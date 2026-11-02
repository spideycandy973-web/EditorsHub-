package com.motionstudio.editor.core.importer

import android.util.Xml
import com.motionstudio.editor.core.domain.model.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.util.zip.ZipInputStream
import kotlin.math.roundToLong

/** Streams a Node Video / generic project ZIP. The archive is never loaded into RAM. */
object NodeVideoZipImporter {

    private val mediaExt = setOf("mp4", "mov", "mkv", "webm", "png", "jpg", "jpeg", "webp", "gif",
        "mp3", "wav", "aac", "m4a", "flac", "ogg", "svg")
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.num(key: String): Double? = (this[key] as? JsonPrimitive)?.doubleOrNull
    private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull
    private fun JsonObject.array(key: String) = this[key] as? kotlinx.serialization.json.JsonArray

    data class Draft(
        val project: Project,
        val assets: List<ImportedAsset>,
        val missingMedia: List<String>,
        val unsupported: List<String>,
        val warnings: List<String>,
        val counts: IntArray,
        val duplicatedAssets: Int
    )

    fun parse(archive: File, tempDir: File, mediaDir: File, onProgress: (Int) -> Unit): Draft {
        require(tempDir.exists() || tempDir.mkdirs())
        val unsupported = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val assets = mutableListOf<ImportedAsset>()
        val manifests = mutableListOf<File>()
        var duplicated = 0
        var entries = 0
        val seenHashes = mutableSetOf<String>()

        ZipInputStream(FileInputStream(archive)).use { zis ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val entry = zis.nextEntry ?: break
                entries++
                val safeName = entry.name.replace('\\', '/').substringAfterLast('/')
                    .ifBlank { "asset_$entries" }
                if (entry.name.contains("..") || entry.isDirectory) { zis.closeEntry(); continue }
                val target = File(tempDir, safeName)
                FileOutputStream(target).use { out ->
                    while (true) {
                        val n = zis.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                    }
                }
                zis.closeEntry()
                val ext = safeName.substringAfterLast('.', "").lowercase()
                if (ext in mediaExt) {
                    val hash = "${target.length()}_${safeName}"
                    if (!seenHashes.add(hash)) { duplicated++; target.delete(); continue }
                    val copy = File(mediaDir, "${ImportIds.newId("asset")}.$ext")
                    target.copyTo(copy, overwrite = true)
                    assets.add(ImportedAsset(safeName, copy.absolutePath,
                        mimeFor(ext)))
                }
                if (ext == "json" || ext == "xml" || safeName.contains("manifest", true) ||
                    safeName.contains("project", true)) manifests.add(target)
                onProgress(entries)
            }
        }

        val manifest = manifests.maxByOrNull { it.length() }
        var width = 1080; var height = 1920; var fps = 30.0; var durationMs = 10_000L
        var name = archive.nameWithoutExtension
        val layers = mutableListOf<Layer>()
        val audio = mutableListOf<AudioTrack>()
        val missing = mutableListOf<String>()
        var keyframes = 0; var effects = 0

        if (manifest == null) {
            warnings.add("No manifest was found. Imported assets only; layers were not reconstructed.")
        } else {
            when (manifest.extension.lowercase()) {
                "json" -> {
                    val root = runCatching { json.parseToJsonElement(manifest.readText()).jsonObject }
                        .getOrNull()
                    if (root == null) warnings.add("Manifest JSON could not be parsed.")
                    else {
                        root.str("name")?.let { name = it }
                        root.int("width")?.let { width = it }
                        root.int("height")?.let { height = it }
                        root.num("fps")?.let { fps = it }
                        root.num("duration")?.let { d ->
                            durationMs = if (d < 1000) (d * 1000).roundToLong() else d.roundToLong()
                        }
                        val layerArray = root.array("layers")
                        if (layerArray == null) warnings.add("Manifest has no layer array.")
                        else layerArray.forEachIndexed { i, element ->
                            val o = element as? JsonObject ?: return@forEachIndexed
                            val srcName = listOf("source", "src", "file", "path", "asset")
                                .firstNotNullOfOrNull { o.str(it) }
                            val asset = srcName?.let { n -> assets.firstOrNull { it.name == n || it.name.substringAfterLast('/') == n.substringAfterLast('/') } }
                            if (srcName != null && asset == null) missing.add(srcName)
                            val typeName = (o.str("type") ?: "image").lowercase()
                            val type = when {
                                typeName.contains("text") -> LayerType.TEXT
                                typeName.contains("shape") || typeName.contains("vector") -> LayerType.SHAPE
                                typeName.contains("audio") -> LayerType.AUDIO
                                typeName.contains("video") -> LayerType.VIDEO
                                else -> LayerType.IMAGE
                            }
                            val start = (o.num("start") ?: 0.0).let {
                                if (it < 1000) (it * 1000).roundToLong() else it.roundToLong()
                            }
                            val dur = (o.num("duration") ?: durationMs.toDouble()).let {
                                if (it < 1000) (it * 1000).roundToLong() else it.roundToLong()
                            }
                            if (type == LayerType.AUDIO) {
                                audio.add(AudioTrack(id = ImportIds.newId("audio"),
                                    uri = asset?.let { "file://${it.path}" } ?: srcName ?: "",
                                    name = srcName ?: "Audio", startMs = start, durationMs = dur))
                            } else {
                                layers.add(Layer(
                                    id = ImportIds.newId("layer"),
                                    name = o.str("name") ?: "Layer ${i + 1}",
                                    type = type, startMs = start, durationMs = dur,
                                    sourceUri = asset?.let { "file://${it.path}" } ?: srcName,
                                    text = if (type == LayerType.TEXT) TextSpec(
                                        text = o.str("text") ?: "Text") else null,
                                    shape = if (type == LayerType.SHAPE) ShapeSpec() else null
                                ))
                            }
                            // Preserve unknown keys as honest conversion warnings.
                            o.keys.filter { it !in setOf("name", "type", "source", "src", "file", "path",
                                "asset", "start", "duration", "text") }.forEach { unsupported.add("Layer field '$it'") }
                        }
                    }
                }
                "xml" -> {
                    val draft = runCatching {
                        FileInputStream(manifest).use { AlightMotionXmlImporter.parse(it, manifest.name, manifest.length()) }
                    }.getOrNull()
                    if (draft != null) {
                        width = draft.project.width; height = draft.project.height
                        fps = draft.project.fpsExact; durationMs = draft.project.durationMs
                        layers.addAll(draft.project.layers); audio.addAll(draft.project.audioTracks)
                        missing.addAll(draft.missingMedia); unsupported.addAll(draft.unsupported)
                        warnings.addAll(draft.warnings); keyframes = draft.counts[1]; effects = draft.counts[2]
                    } else warnings.add("XML manifest could not be parsed.")
                }
                else -> warnings.add("Manifest type '${manifest.extension}' is not a recognised interchange format.")
            }
        }

        // Relink any remaining relative media references to extracted assets.
        var relinked = 0
        for (i in layers.indices) {
            val src = layers[i].sourceUri ?: continue
            val match = assets.firstOrNull { it.name == src.substringAfterLast('/') }
            if (match != null) { layers[i] = layers[i].copy(sourceUri = "file://${match.path}"); relinked++ }
        }
        val stillMissing = layers.mapNotNull { l ->
            l.sourceUri?.takeIf { it !in assets.map { a -> "file://${a.path}" } &&
                !File(it.removePrefix("file://")).exists() }
        } + missing

        if (relinked > 0) warnings.add("Relinked $relinked media reference(s) to extracted assets.")
        if (assets.isNotEmpty()) warnings.add("${assets.size} asset(s) extracted and stored privately.")
        if (layers.isEmpty() && audio.isEmpty()) {
            warnings.add("A single placeholder image layer was created so the project can be opened.")
            layers.add(Layer(id = ImportIds.newId("layer"), name = "Imported assets", type = LayerType.SOLID,
                startMs = 0, durationMs = durationMs))
        }

        val project = Project(
            id = ImportIds.newId("project"), name = name,
            width = width.coerceIn(16, 4096), height = height.coerceIn(16, 4096),
            fps = FrameRates.rounded(fps), fpsExact = fps,
            durationMs = durationMs.coerceAtLeast(500L),
            layers = layers, audioTracks = audio,
            updatedAt = System.currentTimeMillis(), createdAt = System.currentTimeMillis()
        )
        return Draft(project, assets, stillMissing.distinct(), unsupported.distinct(), warnings,
            intArrayOf(layers.size, keyframes, effects, 0), duplicated)
    }

    private fun mimeFor(ext: String) = when (ext) {
        "mp4", "mov", "mkv", "webm" -> "video/$ext"
        "mp3" -> "audio/mpeg"; "wav" -> "audio/wav"; "aac", "m4a" -> "audio/mp4a-latm"
        "flac" -> "audio/flac"; "ogg" -> "audio/ogg"
        "png" -> "image/png"; "jpg", "jpeg" -> "image/jpeg"; "webp" -> "image/webp"
        "gif" -> "image/gif"; "svg" -> "image/svg+xml"
        else -> "application/octet-stream"
    }
}
