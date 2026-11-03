package com.editorshub.core.importer

import android.content.Context
import android.net.Uri
import android.os.StatFs
import androidx.documentfile.provider.DocumentFile
import com.editorshub.core.data.ProjectRepository
import com.editorshub.core.domain.model.AudioTrack
import com.editorshub.core.domain.model.Layer
import com.editorshub.core.domain.model.Project
import com.editorshub.core.rendering.ThumbnailGenerator
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Universal project import manager. All reads are streamed; there is no arbitrary size limit.
 * The original document is always preserved. Every conversion produces an honest report.
 */
@Singleton
class ProjectImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: ProjectRepository,
    private val thumbnails: ThumbnailGenerator
) {
    suspend fun import(
        uri: Uri,
        onProgress: (percent: Int, label: String) -> Unit,
        cancelled: () -> Boolean
    ): ImportResult = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val name = try { DocumentFile.fromSingleUri(context, uri)?.name ?: "import" }
            catch (e: Exception) { "import" }
        val bytes = try {
            resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
        } catch (e: Exception) { -1L }
        val ext = name.substringAfterLast('.', "").lowercase()

        // Storage-space validation before touching the file.
        val free = try { StatFs(context.filesDir.absolutePath).availableBytes } catch (e: Exception) { 0L }
        if (bytes > 0 && free < bytes * 2 + 32L * 1024 * 1024) {
            return@withContext ImportResult.Failure(
                "Not enough free storage (${free / 1024 / 1024} MB available).", null)
        }

        val uid = UUID.randomUUID().toString()
        val importDir = File(context.filesDir, "imports").apply { mkdirs() }
        val original = File(importDir, "original_$uid.$ext")
        onProgress(0, "Reading $name")
        try {
            resolver.openInputStream(uri)?.use { raw ->
                val counting = CountingInputStream(raw, bytes) { read ->
                    if (bytes > 0) onProgress((read * 20 / bytes).toInt().coerceIn(0, 20), "Copying ${read / 1024} KB")
                }
                FileOutputStream(original).use { out -> counting.copyToStreaming(out) }
            } ?: return@withContext ImportResult.Failure("Cannot open the selected document.", null)
        } catch (e: Exception) {
            return@withContext ImportResult.Failure("Read failed: ${e.message}", null)
        }
        if (cancelled()) return@withContext ImportResult.Cancelled

        val kind = detectKind(original, ext)
        val tempDir = File(importDir, "extract_$uid").apply { mkdirs() }
        val mediaDir = File(context.filesDir, "media").apply { mkdirs() }
        onProgress(25, "Converting ${kind.name}")

        val draft = try {
            when (kind) {
                ImportKind.NATIVE_JSON -> {
                    val d = FileInputStream(original).use { NativeProjectImporter.parseJson(it, name) }
                    Draft(d.project, emptyList(), d.missingMedia, emptyList(), d.warnings,
                        intArrayOf(d.project.layers.size, d.project.layers.sumOf { it.keyframes.sumOf { t -> t.keyframes.size } },
                            d.project.layers.sumOf { it.effects.size }, d.project.layers.sumOf { it.masks.size }), 0, 0)
                }
                ImportKind.NATIVE_XML, ImportKind.ALIGHT_XML -> {
                    val d = if (kind == ImportKind.NATIVE_XML)
                        FileInputStream(original).use { NativeProjectImporter.parseXml(it, name) }
                    else null
                    val x = if (d != null) null else FileInputStream(original).use {
                        AlightMotionXmlImporter.parse(it, name, original.length())
                    }
                    val p = d?.project ?: x!!.project
                    val missing = d?.missingMedia ?: x!!.missingMedia
                    val unsupported = d?.warnings?.filter { it.startsWith("Unsupported:") }
                        ?: x!!.unsupported
                    val warnings = (d?.warnings ?: x!!.warnings)
                    Draft(p, emptyList(), missing, unsupported, warnings, x?.counts
                        ?: intArrayOf(p.layers.size, 0, 0, 0), 0, 0)
                }
                ImportKind.NODE_VIDEO_ZIP -> {
                    val d = NodeVideoZipImporter.parse(original, tempDir, mediaDir) { count ->
                        if (count % 5 == 0) onProgress((25 + count).coerceAtMost(70), "Extracting $count entries")
                    }
                    Draft(d.project, d.assets.map { it.path }, d.missingMedia, d.unsupported,
                        d.warnings, d.counts, d.counts[0], d.duplicatedAssets)
                }
                ImportKind.AFTER_EFFECTS_AEPX -> {
                    val d = FileInputStream(original).use { AfterEffectsImporter.parseAepx(it, name) }
                    val p = d.project ?: Project(name = name)
                    Draft(p, emptyList(), d.missingMedia, d.unsupported, d.warnings, d.counts, 0, 0)
                }
                ImportKind.AFTER_EFFECTS_AEP -> {
                    val report = AfterEffectsImporter.binaryAepReport(name, original.length(), original.absolutePath)
                        .copy(logPath = writeLog(uid, listOf("Binary AEP preserved at ${original.absolutePath}")))
                    return@withContext ImportResult.ReportOnly(report)
                }
                ImportKind.UNKNOWN -> {
                    return@withContext ImportResult.Failure(
                        "Unrecognised project format. Supported: .json, .xml, .zip/.amproj, .aepx, .aep (reference only).", null)
                }
            }
        } catch (e: Exception) {
            return@withContext ImportResult.Failure("Conversion failed: ${e.message}", null)
        }
        if (cancelled()) return@withContext ImportResult.Cancelled
        onProgress(75, "Relinking media")

        val (relinkedProject, relinked) = relink(draft.project, mediaDir, draft.extractedPaths)
        val missing = (draft.missingMedia + collectMissing(relinkedProject)).distinct()
        onProgress(88, "Saving project")

        val saved = relinkedProject.copy(
            id = UUID.randomUUID().toString(),
            name = relinkedProject.name.ifBlank { name.substringBeforeLast('.') },
            updatedAt = System.currentTimeMillis(),
            createdAt = System.currentTimeMillis()
        )
        repository.save(saved)
        val thumb = thumbnails.generate(saved)
        if (thumb != null) repository.save(saved, thumbnailPath = thumb, autosaveOnly = true)

        val status = when {
            draft.unsupported.isNotEmpty() || missing.isNotEmpty() -> ImportStatus.PARTIAL
            draft.warnings.isNotEmpty() || draft.unsupported.isNotEmpty() -> ImportStatus.CONVERTED_WITH_MODIFICATIONS
            else -> ImportStatus.IMPORTED
        }
        val report = ImportReport(
            kind = kind, fileName = name, bytes = original.length(), status = status,
            layersImported = draft.counts[0], keyframesImported = draft.counts[1],
            effectsImported = draft.counts[2], masksImported = draft.counts[3],
            assetsFound = draft.assetsFound, assetsRelinked = relinked,
            assetsDuplicated = draft.duplicated,
            missingMedia = missing, unsupported = draft.unsupported,
            warnings = draft.warnings + listOf("Original preserved at ${original.absolutePath}"),
            originalPath = original.absolutePath,
            logPath = writeLog(uid, buildLog(kind, status, draft, missing, relinked)),
            projectId = saved.id
        )
        onProgress(100, "Done")
        ImportResult.Success(report, saved)
    }

    private data class Draft(
        val project: Project,
        val extractedPaths: List<String>,
        val missingMedia: List<String>,
        val unsupported: List<String>,
        val warnings: List<String>,
        val counts: IntArray,
        val assetsFound: Int,
        val duplicated: Int
    )

    private fun detectKind(file: File, ext: String): ImportKind {
        val head = ByteArray(4096)
        val n = FileInputStream(file).use { it.read(head) }.coerceAtLeast(0)
        val text = String(head, 0, n, Charsets.UTF_8)
        return when {
            ext == "aep" -> ImportKind.AFTER_EFFECTS_AEP
            ext == "aepx" -> ImportKind.AFTER_EFFECTS_AEPX
            ext in setOf("zip", "amproj", "node", "nvp") -> ImportKind.NODE_VIDEO_ZIP
            text.trimStart().startsWith("{") -> ImportKind.NATIVE_JSON
            text.contains("<EditorsHubMotion", true) -> ImportKind.NATIVE_XML
            text.contains("<", true) && ext in setOf("xml", "amxml", "txt") -> ImportKind.ALIGHT_XML
            ext == "json" || ext == "ehp" -> ImportKind.NATIVE_JSON
            else -> ImportKind.UNKNOWN
        }
    }

    private fun relink(project: Project, mediaDir: File, extracted: List<String>): Pair<Project, Int> {
        val candidates = (mediaDir.listFiles()?.toList() ?: emptyList()) +
            extracted.map { File(it) }.filter { it.exists() }
        var count = 0
        fun fix(uri: String?): String? {
            if (uri == null) return null
            val path = uri.removePrefix("file://")
            if (File(path).exists()) return uri
            val wanted = path.substringAfterLast('/')
            val match = candidates.firstOrNull { it.name == wanted || it.name.substringBeforeLast('.') == wanted.substringBeforeLast('.') }
            return if (match != null) { count++; "file://${match.absolutePath}" } else uri
        }
        val layers: List<Layer> = project.layers.map { it.copy(sourceUri = fix(it.sourceUri)) }
        val audio: List<AudioTrack> = project.audioTracks.map { it.copy(uri = fix(it.uri) ?: "") }
        return project.copy(layers = layers, audioTracks = audio) to count
    }

    private fun collectMissing(p: Project): List<String> {
        val out = mutableListOf<String>()
        p.layers.forEach { l -> l.sourceUri?.let {
            if (!it.startsWith("data:") && !File(it.removePrefix("file://")).exists()) out.add(it)
        } }
        p.audioTracks.forEach { a -> if (a.uri.isNotBlank() && !a.uri.startsWith("data:") &&
            !File(a.uri.removePrefix("file://")).exists()) out.add(a.uri) }
        return out
    }

    private fun writeLog(uid: String, lines: List<String>): String? = try {
        val dir = File(context.filesDir, "imports").apply { mkdirs() }
        val f = File(dir, "$uid.log")
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        f.writeText(lines.joinToString("\n") { "[$stamp] $it" } + "\n")
        f.absolutePath
    } catch (e: Exception) { null }

    private fun buildLog(
        kind: ImportKind, status: ImportStatus, draft: Draft,
        missing: List<String>, relinked: Int
    ): List<String> = buildList {
        add("Import kind: $kind, status: $status")
        add("Layers: ${draft.counts[0]}, keyframes: ${draft.counts[1]}, effects: ${draft.counts[2]}, masks: ${draft.counts[3]}")
        add("Assets found: ${draft.assetsFound}, relinked: $relinked, duplicates skipped: ${draft.duplicated}")
        draft.warnings.forEach { add("WARN: $it") }
        draft.unsupported.forEach { add("UNSUPPORTED: $it") }
        missing.forEach { add("MISSING MEDIA: $it") }
    }
}
