package com.example.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.example.engine.ProjectTemplates
import com.example.model.*
import com.example.render.VideoExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class ProjectRepository(
    private val context: Context,
    private val dao: ProjectDao
) {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    val projectsFlow: Flow<List<ProjectDocument>> = dao.observeAllProjects().map { list ->
        list.mapNotNull { entity -> decodeDocument(entity.documentJson) }
    }

    fun decodeDocument(raw: String): ProjectDocument? {
        return try {
            json.decodeFromString<ProjectDocument>(raw)
        } catch (_: Exception) {
            null
        }
    }

    fun encodeDocument(doc: ProjectDocument): String = json.encodeToString(doc)

    suspend fun saveProject(doc: ProjectDocument) {
        val now = System.currentTimeMillis()
        val updated = doc.copy(project = doc.project.copy(updatedAt = now))
        dao.upsertProject(
            ProjectEntity(
                id = updated.project.id,
                name = updated.project.name,
                ratio = updated.project.ratio,
                width = updated.project.width,
                height = updated.project.height,
                fps = updated.project.fps,
                duration = updated.project.duration,
                layerCount = updated.layers.size,
                createdAt = updated.project.createdAt,
                updatedAt = now,
                documentJson = encodeDocument(updated)
            )
        )
    }

    suspend fun loadProject(id: String): ProjectDocument? {
        val entity = dao.getProjectById(id) ?: return null
        return decodeDocument(entity.documentJson)
    }

    suspend fun deleteProject(id: String) {
        dao.deleteProjectById(id)
    }

    suspend fun createFromTemplate(templateId: String): ProjectDocument {
        val doc = ProjectTemplates.createFromTemplate(templateId)
        saveProject(doc)
        return doc
    }

    /**
     * Imports .json, .omp.json, .xml, or .amproj / .zip project packages from device storage.
     */
    suspend fun importProjectFromUri(uri: Uri): Result<ProjectDocument> = withContext(Dispatchers.IO) {
        try {
            val fileName = resolveFileName(uri).orEmpty().lowercase()
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext Result.failure(IllegalArgumentException("Tidak dapat membuka file yang dipilih"))
            val doc = parseProjectBytes(bytes, fileName, sourceLabel = fileName.ifBlank { "Impor File" })
                ?: return@withContext Result.failure(IllegalArgumentException("Format file proyek tidak dikenali (.json, .xml, .amproj/.zip)"))
            val finalized = finalizeImportedDocument(doc)
            saveProject(finalized)
            Result.success(finalized)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Imports a project from a web URL, GitHub/Drive/Dropbox link, or Alight Motion share URL.
     */
    suspend fun importProjectFromLink(rawUrl: String): Result<ProjectDocument> = withContext(Dispatchers.IO) {
        try {
            val normalizedUrl = normalizeImportUrl(rawUrl.trim())
            if (!normalizedUrl.startsWith("http://") && !normalizedUrl.startsWith("https://")) {
                return@withContext Result.failure(IllegalArgumentException("Masukkan tautan URL yang valid (https://...)"))
            }
            val fetched = fetchUrlBytes(normalizedUrl)
                ?: return@withContext Result.failure(IllegalArgumentException("Gagal mengunduh tautan. Periksa koneksi internet Anda."))

            var doc = parseProjectBytes(fetched.bytes, fetched.resolvedName.lowercase(), sourceLabel = normalizedUrl)
            if (doc == null) {
                val text = fetched.bytes.toString(Charsets.UTF_8)
                // Look for embedded package download link inside HTML share page (e.g., alight.link / alightcreative.com)
                val nestedUrl = extractNestedProjectUrlFromHtml(text, normalizedUrl)
                if (nestedUrl != null) {
                    val pkg = fetchUrlBytes(nestedUrl)
                    if (pkg != null) {
                        doc = parseProjectBytes(pkg.bytes, pkg.resolvedName.lowercase(), sourceLabel = nestedUrl)
                    }
                }
                if (doc == null) {
                    // Try finding inline scene XML or ProjectDocument JSON inside script tags / HTML
                    val xmlMatch = Regex("<scene[\\s\\S]*?</scene>", RegexOption.IGNORE_CASE).find(text)?.value
                    if (xmlMatch != null) {
                        doc = XmlInterchange.fromAlightMotionXml(xmlMatch)
                    }
                }
                if (doc == null && text.contains("alight", ignoreCase = true) && text.contains("</html>", ignoreCase = true)) {
                    return@withContext Result.failure(
                        IllegalStateException(
                            "Tautan Alight Motion ini memblokir unduhan paket langsung (memerlukan autentikasi atau privat). Ekspor sebagai .amproj / .xml / .json lalu gunakan Impor File."
                        )
                    )
                }
            }

            if (doc == null) {
                return@withContext Result.failure(IllegalArgumentException("Tautan tidak berisi paket proyek Open Motion / Alight Motion yang valid."))
            }

            val finalized = finalizeImportedDocument(doc)
            saveProject(finalized)
            Result.success(finalized)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseProjectBytes(bytes: ByteArray, lowerName: String, sourceLabel: String): ProjectDocument? {
        if (bytes.isEmpty()) return null
        val isZip = (bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()) ||
            lowerName.endsWith(".zip") || lowerName.endsWith(".amproj")
        if (isZip) {
            return parseAmprojZip(bytes)
        }
        val text = bytes.toString(Charsets.UTF_8).trimStart('\uFEFF', ' ', '\n', '\r', '\t')
        if (text.startsWith("<") || lowerName.endsWith(".xml")) {
            return XmlInterchange.fromAlightMotionXml(text)
        }
        if (text.startsWith("{") || lowerName.endsWith(".json")) {
            return decodeDocument(text)
        }
        return null
    }

    private fun parseAmprojZip(zipBytes: ByteArray): ProjectDocument? {
        val projectId = newUid("project")
        val mediaOutDir = File(context.filesDir, "imported_media/$projectId").apply { mkdirs() }
        val fontsOutDir = File(context.filesDir, "fonts").apply { mkdirs() }
        val extractedMediaMap = mutableMapOf<String, String>()
        var jsonCandidate: String? = null
        var xmlCandidate: String? = null

        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val safeEntryName = entry.name.replace("\\", "/")
                    if (safeEntryName.contains("..")) {
                        zis.closeEntry()
                        entry = zis.nextEntry
                        continue
                    }
                    val baseName = File(safeEntryName).name
                    val lower = baseName.lowercase()
                    val entryBytes = zis.readBytes()

                    when {
                        lower.endsWith(".omp.json") || lower == "project.json" || (lower.endsWith(".json") && jsonCandidate == null) -> {
                            jsonCandidate = entryBytes.toString(Charsets.UTF_8)
                        }
                        lower == "scene.xml" || lower.endsWith(".xml") -> {
                            if (xmlCandidate == null || lower == "scene.xml") {
                                xmlCandidate = entryBytes.toString(Charsets.UTF_8)
                            }
                        }
                        lower.endsWith(".ttf") || lower.endsWith(".otf") -> {
                            val fontFile = File(fontsOutDir, baseName)
                            fontFile.writeBytes(entryBytes)
                        }
                        lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
                            lower.endsWith(".webp") || lower.endsWith(".mp4") || lower.endsWith(".mov") ||
                            lower.endsWith(".mp3") || lower.endsWith(".wav") || lower.endsWith(".m4a") || lower.endsWith(".aac") -> {
                            val outFile = File(mediaOutDir, baseName)
                            outFile.writeBytes(entryBytes)
                            val uriStr = Uri.fromFile(outFile).toString()
                            extractedMediaMap[safeEntryName] = uriStr
                            extractedMediaMap[baseName] = uriStr
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        val parsed = jsonCandidate?.let { decodeDocument(it) }
            ?: xmlCandidate?.let { XmlInterchange.fromAlightMotionXml(it) }
            ?: return null

        val remappedLayers = parsed.layers.map { l ->
            val rawUri = l.mediaUri
            if (!rawUri.isNullOrBlank()) {
                val matched = extractedMediaMap[rawUri]
                    ?: extractedMediaMap[rawUri.substringAfterLast('/')]
                if (matched != null) l.copy(mediaUri = matched) else l
            } else l
        }
        return parsed.copy(
            project = parsed.project.copy(id = projectId),
            layers = remappedLayers
        )
    }

    private fun finalizeImportedDocument(doc: ProjectDocument): ProjectDocument {
        return doc.copy(
            project = doc.project.copy(
                id = newUid("project"),
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    private data class HttpFetchResult(val bytes: ByteArray, val resolvedName: String)

    private fun fetchUrlBytes(urlStr: String): HttpFetchResult? {
        var current = urlStr
        for (redirect in 0..5) {
            val conn = (URL(current).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 15_000
                readTimeout = 25_000
                setRequestProperty("User-Agent", "OpenMotionAndroid/1.0")
            }
            val code = conn.responseCode
            if (code in 300..399) {
                val loc = conn.getHeaderField("Location") ?: break
                current = if (loc.startsWith("http")) loc else URL(URL(current), loc).toString()
                conn.disconnect()
                continue
            }
            if (code !in 200..299) {
                conn.disconnect()
                return null
            }
            val bytes = conn.inputStream.use { it.readBytes() }
            val disp = conn.getHeaderField("Content-Disposition").orEmpty()
            val ctype = conn.contentType.orEmpty().lowercase()
            conn.disconnect()
            val name = Regex("filename=\"?([^\";]+)\"?").find(disp)?.groupValues?.getOrNull(1)
                ?: when {
                    ctype.contains("zip") -> "package.amproj"
                    ctype.contains("xml") -> "scene.xml"
                    ctype.contains("json") -> "project.json"
                    else -> current.substringAfterLast('/').substringBefore('?')
                }
            return HttpFetchResult(bytes, name)
        }
        return null
    }

    private fun normalizeImportUrl(url: String): String {
        // GitHub blob -> raw
        val ghBlob = Regex("^https?://github\\.com/([^/]+)/([^/]+)/blob/(.+)$").find(url)
        if (ghBlob != null) {
            val (user, repo, rest) = ghBlob.destructured
            return "https://raw.githubusercontent.com/$user/$repo/$rest"
        }
        // GitHub Gist -> raw
        val gist = Regex("^https?://gist\\.github\\.com/([^/]+/[0-9a-fA-F]+)/?$").find(url)
        if (gist != null) {
            return "${url.trimEnd('/')}/raw"
        }
        // Pastebin -> raw
        val pastebin = Regex("^https?://pastebin\\.com/([A-Za-z0-9]+)$").find(url)
        if (pastebin != null) {
            return "https://pastebin.com/raw/${pastebin.groupValues[1]}"
        }
        // Google Drive share link -> direct download
        val driveFile = Regex("https?://drive\\.google\\.com/file/d/([^/]+)").find(url)
        if (driveFile != null) {
            val id = driveFile.groupValues[1]
            return "https://drive.google.com/uc?export=download&id=$id"
        }
        // Dropbox dl=0 -> dl=1
        if (url.contains("dropbox.com") && url.contains("dl=0")) {
            return url.replace("dl=0", "dl=1")
        }
        return url
    }

    private fun extractNestedProjectUrlFromHtml(html: String, baseUrl: String): String? {
        val pattern = Regex("""https?://[^\s"'<>]+\.(amproj|omp\.json|json|xml)(\?[^\s"'<>]+)?""", RegexOption.IGNORE_CASE)
        val direct = pattern.find(html)?.value
        if (direct != null) return direct
        val hrefRel = Regex("""href=["']([^"']+\.(amproj|omp\.json|xml|json))["']""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.getOrNull(1)
        if (hrefRel != null) return URL(URL(baseUrl), hrefRel).toString()
        return null
    }

    private fun resolveFileName(uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else uri.lastPathSegment
            } ?: uri.lastPathSegment
        } catch (_: Exception) {
            uri.lastPathSegment
        }
    }

    fun listCustomFonts(): List<String> {
        val dir = File(context.filesDir, "fonts")
        if (!dir.exists()) return emptyList()
        return dir.listFiles()
            ?.filter { it.isFile && (it.name.endsWith(".ttf", true) || it.name.endsWith(".otf", true)) }
            ?.map { it.nameWithoutExtension }
            ?.sorted()
            ?: emptyList()
    }

    suspend fun importCustomFont(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        try {
            val rawName = resolveFileName(uri) ?: "custom_font_${System.currentTimeMillis()}.ttf"
            val ext = if (rawName.endsWith(".otf", true)) ".otf" else ".ttf"
            val base = rawName.substringBeforeLast('.').replace(Regex("[^a-zA-Z0-9 _-]"), "").ifBlank { "CustomFont" }
            val dir = File(context.filesDir, "fonts").apply { mkdirs() }
            val outFile = File(dir, "$base$ext")
            context.contentResolver.openInputStream(uri)?.use { inp ->
                outFile.outputStream().use { out -> inp.copyTo(out) }
            } ?: return@withContext Result.failure(IllegalArgumentException("Gagal membaca file font"))
            Result.success(base)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun exportJsonFile(doc: ProjectDocument): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val safeName = doc.project.name.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "open_motion" }
        val file = File(dir, "$safeName.omp.json")
        file.writeText(encodeDocument(doc))
        file
    }

    suspend fun exportXmlFile(doc: ProjectDocument): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val safeName = doc.project.name.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "open_motion" }
        val file = File(dir, "$safeName.xml")
        file.writeText(XmlInterchange.toAlightMotionXml(doc))
        file
    }

    suspend fun exportAmprojZip(doc: ProjectDocument): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val safeName = doc.project.name.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "open_motion" }
        val zipFile = File(dir, "$safeName.amproj")
        if (zipFile.exists()) zipFile.delete()

        val rewrittenLayers = mutableListOf<LayerData>()
        val mediaPayloads = mutableListOf<Pair<String, InputStream>>()

        doc.layers.forEachIndexed { idx, layer ->
            val rawUri = layer.mediaUri
            if (!rawUri.isNullOrBlank()) {
                val uri = Uri.parse(rawUri)
                val stream = try {
                    if (uri.scheme == "file") File(uri.path!!).inputStream()
                    else context.contentResolver.openInputStream(uri)
                } catch (_: Exception) {
                    null
                }
                if (stream != null) {
                    val ext = when (layer.type) {
                        LayerType.VIDEO -> "mp4"
                        LayerType.AUDIO -> "mp3"
                        else -> "png"
                    }
                    val entryPath = "media/asset_${idx}.$ext"
                    mediaPayloads.add(entryPath to stream)
                    rewrittenLayers.add(layer.copy(mediaUri = entryPath))
                } else {
                    rewrittenLayers.add(layer)
                }
            } else {
                rewrittenLayers.add(layer)
            }
        }

        val bundledDoc = doc.copy(layers = rewrittenLayers)
        try {
            ZipOutputStream(zipFile.outputStream()).use { zos ->
                zos.putNextEntry(ZipEntry("project.json"))
                zos.write(encodeDocument(bundledDoc).toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                zos.putNextEntry(ZipEntry("scene.xml"))
                zos.write(XmlInterchange.toAlightMotionXml(bundledDoc).toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                for ((entryPath, stream) in mediaPayloads) {
                    stream.use { s ->
                        zos.putNextEntry(ZipEntry(entryPath))
                        s.copyTo(zos)
                        zos.closeEntry()
                    }
                }
            }
        } finally {
            mediaPayloads.forEach { (_, s) -> runCatching { s.close() } }
        }
        zipFile
    }

    suspend fun exportPngFile(doc: ProjectDocument, timeSec: Float): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val safeName = doc.project.name.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "open_motion" }
        val file = File(dir, "${safeName}_frame.png")
        return VideoExporter.exportPng(context, doc, timeSec, file)
    }

    suspend fun exportMp4File(
        doc: ProjectDocument,
        outWidth: Int,
        outHeight: Int,
        fps: Int,
        bitrateMbps: Int,
        onProgress: (Float) -> Unit
    ): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val safeName = doc.project.name.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "open_motion" }
        val file = File(dir, "$safeName.mp4")
        return VideoExporter.exportMp4(context, doc, outWidth, outHeight, fps, bitrateMbps, file, onProgress)
    }

    suspend fun saveFileToUserUri(sourceFile: File, targetUri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openOutputStream(targetUri)?.use { out ->
                sourceFile.inputStream().use { inp -> inp.copyTo(out) }
            } ?: return@withContext false
            true
        } catch (_: Exception) {
            false
        }
    }

    fun shareFile(file: File, mimeType: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent(Intent.ACTION_CHOOSER).apply {
            putExtra(Intent.EXTRA_INTENT, intent)
            putExtra(Intent.EXTRA_TITLE, "Bagikan / Simpan Hasil")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}
