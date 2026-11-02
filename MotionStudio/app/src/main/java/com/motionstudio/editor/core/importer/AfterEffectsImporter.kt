package com.motionstudio.editor.core.importer

import android.util.Xml
import com.motionstudio.editor.core.domain.model.*
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import kotlin.math.roundToLong

/**
 * Honest After Effects interchange importer.
 * AEPX (XML) is parsed for compositions, solids, footage, text, transforms and keyframes.
 * Binary AEP is NOT parsed: the original is preserved as a recoverable reference and the
 * report explains the required interchange workflow. No unsupported claims are made.
 */
object AfterEffectsImporter {

    data class Draft(
        val project: Project?,
        val missingMedia: List<String>,
        val unsupported: List<String>,
        val warnings: List<String>,
        val counts: IntArray
    )

    fun parseAepx(input: InputStream, fileName: String): Draft {
        val parser = Xml.newPullParser()
        parser.setInput(InputStreamReader(input, Charsets.UTF_8))
        var width = 1920; var height = 1080; var fps = 30.0; var durationMs = 10_000L
        var name = fileName.substringBeforeLast('.')
        val layers = mutableListOf<Layer>()
        val missing = mutableListOf<String>()
        val unsupported = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        var keyframes = 0; var masks = 0; var effects = 0
        var current: MutableLayer? = null
        var inTransform = false; var inKeyframes = false; var inMasks = false; var inEffects = false
        val parentLinks = mutableMapOf<String, String>()
        var textBuffer = StringBuilder()
        var compDepth = 0

        fun attr(n: String) = parser.getAttributeValue(null, n)
        fun anyAttr(vararg names: String) = names.firstNotNullOfOrNull { attr(it) }

        while (true) {
            when (parser.next()) {
                XmlPullParser.END_DOCUMENT -> break
                XmlPullParser.START_TAG -> {
                    when (parser.name.lowercase()) {
                        "composition", "comp" -> {
                            compDepth++
                            anyAttr("name", "comment")?.let { name = it }
                            anyAttr("width", "w")?.toIntOrNull()?.let { width = it }
                            anyAttr("height", "h")?.toIntOrNull()?.let { height = it }
                            anyAttr("framerate", "frameRate", "fps")?.toDoubleOrNull()?.let { fps = it }
                            anyAttr("duration", "durationFrames")?.toDoubleOrNull()?.let { d ->
                                durationMs = if (d < 1000) (d * 1000).roundToLong() else d.roundToLong()
                            }
                        }
                        "layer", "layr", "item" -> {
                            val id = ImportIds.newId("layer")
                            val typeName = (anyAttr("type", "kind") ?: "solid").lowercase()
                            val type = when {
                                typeName.contains("text") -> LayerType.TEXT
                                typeName.contains("solid") -> LayerType.SOLID
                                typeName.contains("footage") || typeName.contains("image") -> LayerType.IMAGE
                                typeName.contains("video") || typeName.contains("movie") -> LayerType.VIDEO
                                typeName.contains("shape") -> LayerType.SHAPE
                                else -> { unsupported.add("AE layer type '$typeName'"); LayerType.SOLID }
                            }
                            val src = anyAttr("source", "file", "path", "footage")
                            if (src != null && !File(src).exists()) missing.add(src)
                            current = MutableLayer(
                                id = id,
                                name = anyAttr("name", "label") ?: "AE Layer ${layers.size + 1}",
                                type = type,
                                startMs = (anyAttr("startTime", "inPoint")?.toDoubleOrNull() ?: 0.0)
                                    .let { (it * 1000).roundToLong() },
                                durationMs = (anyAttr("duration", "outPoint")?.toDoubleOrNull()
                                    ?: durationMs / 1000.0).let { (it * 1000).roundToLong() },
                                source = src,
                                transform = Transform()
                            )
                            anyAttr("parent", "parentLayer")?.let { parentLinks[id] = it }
                            textBuffer.setLength(0)
                        }
                        "transform", "transformgroup" -> inTransform = true
                        "position" -> if (inTransform) {
                            current?.transform = current!!.transform.copy(
                                x = anyAttr("x")?.toFloatOrNull() ?: 0f,
                                y = anyAttr("y")?.toFloatOrNull() ?: 0f)
                        }
                        "scale" -> if (inTransform) {
                            val sx = anyAttr("x")?.toFloatOrNull() ?: 100f
                            val sy = anyAttr("y")?.toFloatOrNull() ?: sx
                            current?.transform = current!!.transform.copy(scaleX = sx / 100f, scaleY = sy / 100f)
                        }
                        "rotation", "rotationz" -> if (inTransform) {
                            current?.transform = current!!.transform.copy(
                                rotation = anyAttr("value", "rotation")?.toFloatOrNull() ?: 0f)
                        }
                        "opacity" -> if (inTransform) {
                            current?.transform = current!!.transform.copy(
                                opacity = (anyAttr("value", "opacity")?.toFloatOrNull() ?: 100f) / 100f)
                        }
                        "anchorpoint", "anchor" -> if (inTransform) {
                            current?.transform = current!!.transform.copy(
                                anchorX = anyAttr("x")?.toFloatOrNull() ?: 0f,
                                anchorY = anyAttr("y")?.toFloatOrNull() ?: 0f)
                        }
                        "keyframes", "keyframegroup" -> inKeyframes = true
                        "keyframe", "key" -> if (inKeyframes) {
                            val prop = (anyAttr("property", "name") ?: "opacity").lowercase()
                            val mapped = when {
                                prop.contains("position.x") || prop == "x" -> "x"
                                prop.contains("position.y") || prop == "y" -> "y"
                                prop.contains("scale") -> "scaleX"
                                prop.contains("rotation") -> "rotation"
                                prop.contains("opacity") -> "opacity"
                                else -> null
                            }
                            if (mapped == null) unsupported.add("AE keyframe property '$prop'")
                            else {
                                val t = ((anyAttr("time", "frame")?.toDoubleOrNull() ?: 0.0) * 1000).roundToLong()
                                val v = (anyAttr("value", "v")?.toFloatOrNull() ?: 0f).let {
                                    if (prop.contains("opacity")) it / 100f else it
                                }
                                current?.keyframe(mapped, Keyframe(
                                    id = ImportIds.newId("kf"), timeMs = t, value = v,
                                    easing = Easing.BEZIER))
                                keyframes++
                                unsupported.add("AE Bezier/easing handles for '$prop' approximated to default curve")
                            }
                        }
                        "masks", "maskgroup" -> inMasks = true
                        "mask" -> if (inMasks) {
                            current?.masks?.add(MaskSpec(id = ImportIds.newId("mask"),
                                kind = if ((anyAttr("mode") ?: "").contains("ellipse")) MaskKind.CIRCLE
                                    else MaskKind.RECTANGLE,
                                featherPx = anyAttr("feather")?.toFloatOrNull() ?: 0f,
                                inverted = (anyAttr("mode") ?: "").contains("subtract", true)))
                            masks++
                        }
                        "effects", "effectgroup" -> inEffects = true
                        "effect" -> if (inEffects) {
                            unsupported.add("AE effect '${anyAttr("name", "matchName") ?: "unknown"}' requires replacement")
                            effects++
                        }
                        "expression" -> unsupported.add("AE expressions are not reproducible")
                        "solid" -> current?.type = LayerType.SOLID
                        else -> Unit
                    }
                }
                XmlPullParser.TEXT -> if (current != null && textBuffer.length < 4096) textBuffer.append(parser.text.trim())
                XmlPullParser.END_TAG -> when (parser.name.lowercase()) {
                    "transform", "transformgroup" -> inTransform = false
                    "keyframes", "keyframegroup" -> inKeyframes = false
                    "masks", "maskgroup" -> inMasks = false
                    "effects", "effectgroup" -> inEffects = false
                    "layer", "layr", "item" -> {
                        current?.let { m ->
                            val txt = textBuffer.toString().trim()
                            layers.add(m.build(if (m.type == LayerType.TEXT) txt else null))
                        }
                        current = null
                    }
                    "composition", "comp" -> compDepth--
                }
            }
        }
        if (compDepth > 0) warnings.add("Nested/pre-composition structure was flattened.")
        if (layers.isEmpty()) warnings.add("No AE layers were recognised; a blank composition was created.")
        warnings.add("3D layers, cameras, expressions, plugins and proprietary effects are not reproduced.")
        val project = Project(
            id = ImportIds.newId("project"), name = name,
            width = width.coerceIn(16, 4096), height = height.coerceIn(16, 4096),
            fps = FrameRates.rounded(fps), fpsExact = fps,
            durationMs = durationMs.coerceAtLeast(500L),
            layers = layers,
            updatedAt = System.currentTimeMillis(), createdAt = System.currentTimeMillis()
        )
        return Draft(project, missing.distinct(), unsupported.distinct(), warnings,
            intArrayOf(layers.size, keyframes, effects, masks))
    }

    fun binaryAepReport(fileName: String, bytes: Long, originalPath: String) = ImportReport(
        kind = ImportKind.AFTER_EFFECTS_AEP,
        fileName = fileName, bytes = bytes, status = ImportStatus.UNSUPPORTED,
        unsupported = listOf(
            "Binary .aep requires a licensed parser or an AEPX / interchange export from After Effects.",
            "3D layers, expressions, plugins and proprietary effects cannot be reproduced."
        ),
        warnings = listOf("The original .aep was preserved as a recoverable reference.",
            "Export the project as .aepx from After Effects and import that file instead."),
        originalPath = originalPath
    )

    private class MutableLayer(
        val id: String, var name: String, var type: LayerType, var startMs: Long,
        var durationMs: Long, var source: String?, var transform: Transform,
        val tracks: MutableList<KeyframeTrack> = mutableListOf(),
        val masks: MutableList<MaskSpec> = mutableListOf()
    ) {
        fun keyframe(property: String, kf: Keyframe) {
            val i = tracks.indexOfFirst { it.property == property }
            if (i >= 0) tracks[i] = tracks[i].copy(
                keyframes = (tracks[i].keyframes + kf).sortedBy { it.timeMs })
            else tracks.add(KeyframeTrack(property, listOf(kf)))
        }
        fun build(text: String?) = Layer(
            id = id, name = name, type = type, startMs = startMs, durationMs = durationMs,
            sourceUri = source, transform = transform, keyframes = tracks, masks = masks,
            text = if (type == LayerType.TEXT) TextSpec(text = text?.takeIf { it.isNotBlank() } ?: "Text") else null,
            shape = if (type == LayerType.SHAPE) ShapeSpec() else null
        )
    }
}
