package com.motionstudio.editor.core.importer

import android.util.Xml
import com.motionstudio.editor.core.domain.model.*
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.io.InputStreamReader
import kotlin.math.roundToLong

/**
 * Incremental XmlPullParser importer for Alight Motion-style XML project files.
 * Unknown elements are collected in the report rather than silently dropped.
 */
object AlightMotionXmlImporter {

    private val effectAliases = mapOf(
        "blur" to EffectType.GAUSSIAN_BLUR, "gaussianblur" to EffectType.GAUSSIAN_BLUR,
        "gaussian_blur" to EffectType.GAUSSIAN_BLUR, "motionblur" to EffectType.MOTION_BLUR,
        "motion_blur" to EffectType.MOTION_BLUR, "directionalblur" to EffectType.DIRECTIONAL_BLUR,
        "glow" to EffectType.GLOW, "shadow" to EffectType.SHADOW, "dropshadow" to EffectType.SHADOW,
        "brightness" to EffectType.BRIGHTNESS, "contrast" to EffectType.CONTRAST,
        "saturation" to EffectType.SATURATION, "hue" to EffectType.HUE,
        "exposure" to EffectType.EXPOSURE, "vignette" to EffectType.VIGNETTE,
        "sharpen" to EffectType.SHARPEN, "noise" to EffectType.NOISE, "grain" to EffectType.NOISE,
        "rgbsplit" to EffectType.RGB_SPLIT, "chromaticaberration" to EffectType.CHROMATIC_ABERRATION,
        "pixelate" to EffectType.PIXELATION, "pixelation" to EffectType.PIXELATION,
        "wave" to EffectType.WAVE_DISTORTION, "wavedistortion" to EffectType.WAVE_DISTORTION,
        "colorgrading" to EffectType.COLOR_GRADING, "colorcorrection" to EffectType.COLOR_GRADING
    )

    private val layerAliases = mapOf(
        "text" to LayerType.TEXT, "shape" to LayerType.SHAPE, "image" to LayerType.IMAGE,
        "photo" to LayerType.IMAGE, "video" to LayerType.VIDEO, "audio" to LayerType.AUDIO,
        "solid" to LayerType.SOLID, "vector" to LayerType.VECTOR, "group" to LayerType.GROUP,
        "effect" to LayerType.EFFECT, "null" to LayerType.GROUP
    )

    data class Draft(
        val project: Project,
        val missingMedia: List<String>,
        val unsupported: List<String>,
        val warnings: List<String>,
        val counts: IntArray // layers, keyframes, effects, masks
    )

    fun parse(input: InputStream, fileName: String, bytes: Long): Draft {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(InputStreamReader(input, Charsets.UTF_8))

        var width = 1080; var height = 1920; var fps = 30.0; var durationMs = 10_000L
        var background = "#FF0B0C14"; var name = fileName.substringBeforeLast('.')
        var layers = 0; var keyframes = 0; var effects = 0; var masks = 0
        val missing = mutableListOf<String>()
        val unsupported = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val outLayers = mutableListOf<Layer>()
        val audio = mutableListOf<AudioTrack>()

        var currentLayer: MutableLayer? = null
        var inKeyframes = false; var inEffects = false; var inMasks = false; var inComposition = false
        var textBuffer = StringBuilder()
        var skipDepth = 0

        fun attr(n: String): String? = parser.getAttributeValue(null, n)
        fun floatAttr(n: String, default: Float): Float = attr(n)?.toFloatOrNull() ?: default
        fun longAttr(n: String, default: Long): Long = attr(n)?.toDoubleOrNull()?.roundToLong() ?: default
        fun anyAttr(vararg names: String): String? = names.firstNotNullOfOrNull { attr(it) }

        while (true) {
            when (parser.next()) {
                XmlPullParser.END_DOCUMENT -> break
                XmlPullParser.START_TAG -> {
                    val tag = parser.name.lowercase()
                    if (skipDepth > 0) { skipDepth++; continue }
                    when (tag) {
                        "composition", "comp", "project", "settings", "alightmotion", "amproject", "motionproject" -> {
                            inComposition = true
                            anyAttr("name", "projectName")?.let { name = it }
                            anyAttr("width", "w")?.toIntOrNull()?.let { width = it }
                            anyAttr("height", "h")?.toIntOrNull()?.let { height = it }
                            anyAttr("fps", "framerate", "frameRate")?.toDoubleOrNull()?.let { fps = it }
                            anyAttr("duration", "durationMs")?.toDoubleOrNull()?.let { d ->
                                durationMs = if (d < 1000) (d * 1000).roundToLong() else d.roundToLong()
                            }
                            anyAttr("background", "bg", "backgroundColor")?.let { background = normalizeColor(it) }
                        }
                        "layers" -> inComposition = true
                        "layer", "item", "track" -> {
                            val typeName = (anyAttr("type", "kind") ?: "shape").lowercase()
                            val type = layerAliases[typeName] ?: run {
                                unsupported.add("Layer type '$typeName'"); LayerType.SHAPE
                            }
                            val source = anyAttr("source", "src", "uri", "path", "file")
                            currentLayer = MutableLayer(
                                id = ImportIds.newId("layer"),
                                name = anyAttr("name", "label") ?: "Layer ${outLayers.size + 1}",
                                type = type,
                                startMs = longAttr("start", 0L).let { if (it < 1000 && durationMs > 1000) it * 1000L else it },
                                durationMs = longAttr("duration", durationMs).let { if (it < 1000 && durationMs > 1000) it * 1000L else it },
                                source = source,
                                transform = Transform(
                                    x = floatAttr("x", 0f), y = floatAttr("y", 0f),
                                    scaleX = floatAttr("scaleX", floatAttr("scale", 1f)),
                                    scaleY = floatAttr("scaleY", floatAttr("scale", 1f)),
                                    rotation = floatAttr("rotation", 0f),
                                    opacity = floatAttr("opacity", floatAttr("alpha", 1f))
                                )
                            )
                            textBuffer.setLength(0)
                        }
                        "keyframes", "animations" -> inKeyframes = true
                        "keyframe", "kf" -> if (inKeyframes) {
                            val property = (anyAttr("property", "prop", "name") ?: "opacity").lowercase()
                            val mapped = mapProperty(property)
                            if (mapped == null) unsupported.add("Keyframe property '$property'")
                            else {
                                val t = longAttr("time", 0L)
                                val v = floatAttr("value", 0f)
                                val easingName = (anyAttr("easing", "interpolation") ?: "linear").lowercase()
                                val easing = mapEasing(easingName)
                                if (easingName.contains("bezier") && easing != Easing.BEZIER)
                                    unsupported.add("Bezier curve data for '$property'")
                                currentLayer?.keyframe(mapped, Keyframe(
                                    id = ImportIds.newId("kf"), timeMs = t, value = v, easing = easing
                                ))
                                keyframes++
                            }
                        }
                        "effects" -> inEffects = true
                        "effect" -> if (inEffects) {
                            val raw = (anyAttr("type", "name", "id") ?: "").lowercase()
                                .replace("_", "").replace("-", "").replace(" ", "")
                            val type = effectAliases[raw]
                            if (type == null) unsupported.add("Effect '$raw'")
                            else {
                                val params = mutableMapOf<String, Float>()
                                anyAttr("amount", "value", "strength", "radius")?.toFloatOrNull()?.let {
                                    val key = com.motionstudio.editor.core.domain.effects.EffectCatalog.spec(type).params.first().key
                                    params[key] = it
                                }
                                currentLayer?.effects?.add(Effect(id = ImportIds.newId("fx"), type = type,
                                    enabled = attr("enabled")?.toBooleanStrictOrNull() ?: true, params = params))
                                effects++
                            }
                        }
                        "masks" -> inMasks = true
                        "mask" -> if (inMasks) {
                            val kind = when ((anyAttr("type", "kind") ?: "rectangle").lowercase()) {
                                "ellipse", "circle" -> MaskKind.CIRCLE
                                "path", "pen", "bezier" -> MaskKind.PATH
                                else -> MaskKind.RECTANGLE
                            }
                            currentLayer?.masks?.add(MaskSpec(
                                id = ImportIds.newId("mask"), kind = kind,
                                featherPx = floatAttr("feather", 0f),
                                opacity = floatAttr("opacity", 1f),
                                inverted = attr("inverted")?.toBooleanStrictOrNull() ?: false
                            ))
                            masks++
                        }
                        "text", "content", "value" -> { /* captured via TEXT event */ }
                        "shape" -> {
                            val sk = when ((anyAttr("kind", "type") ?: "rectangle").lowercase()) {
                                "circle", "ellipse" -> ShapeKind.CIRCLE
                                "triangle" -> ShapeKind.TRIANGLE
                                "polygon" -> ShapeKind.POLYGON
                                "star" -> ShapeKind.STAR
                                else -> ShapeKind.RECTANGLE
                            }
                            currentLayer?.shape = ShapeSpec(kind = sk,
                                width = floatAttr("width", 500f), height = floatAttr("height", 500f),
                                fillColor = normalizeColor(anyAttr("fill", "color") ?: "#FFFFFFFF"))
                        }
                        "media", "asset" -> {
                            val p = anyAttr("path", "src", "source", "uri")
                            if (p != null && !p.startsWith("data:") && !java.io.File(p).exists())
                                missing.add(p)
                        }
                        else -> {
                            if (tag !in setOf("root", "xml", "keyframes", "effects", "masks", "layers",
                                    "transform", "position", "properties", "property", "description", "metadata",
                                    "version", "author", "size", "points", "path", "data")) {
                                unsupported.add("Element <$tag>")
                            }
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    if (currentLayer != null && textBuffer.length < 4096) textBuffer.append(parser.text.trim())
                }
                XmlPullParser.END_TAG -> {
                    val tag = parser.name.lowercase()
                    if (skipDepth > 0) { skipDepth--; continue }
                    when (tag) {
                        "keyframes", "animations" -> inKeyframes = false
                        "effects" -> inEffects = false
                        "masks" -> inMasks = false
                        "layer", "item", "track" -> {
                            currentLayer?.let { m ->
                                val text = textBuffer.toString().trim()
                                val l = m.build(text)
                                if (l.sourceUri != null && !l.sourceUri.startsWith("data:") &&
                                    !java.io.File(l.sourceUri.removePrefix("file://")).exists())
                                    missing.add(l.sourceUri)
                                if (l.sourceUri != null && l.type == LayerType.AUDIO) {
                                    audio.add(AudioTrack(id = ImportIds.newId("audio"), uri = l.sourceUri,
                                        name = l.name, durationMs = l.durationMs))
                                } else outLayers.add(l)
                                layers++
                            }
                            currentLayer = null
                        }
                        "composition", "comp", "project" -> inComposition = false
                    }
                }
            }
        }

        if (layers == 0) warnings.add("No layers were found in the XML file.")
        if (outLayers.isEmpty()) warnings.add("A blank composition was created so the project is still usable.")
        val project = Project(
            id = ImportIds.newId("project"),
            name = name,
            width = width.coerceIn(16, 4096), height = height.coerceIn(16, 4096),
            fps = FrameRates.rounded(fps), fpsExact = fps,
            durationMs = durationMs.coerceAtLeast(500L),
            background = background,
            layers = outLayers,
            audioTracks = audio,
            updatedAt = System.currentTimeMillis(),
            createdAt = System.currentTimeMillis()
        )
        return Draft(project, missing.distinct(), unsupported.distinct(), warnings,
            intArrayOf(layers, keyframes, effects, masks))
    }

    private fun normalizeColor(v: String): String {
        val s = v.trim()
        return when {
            s.startsWith("#") && s.length == 7 -> "#FF${s.drop(1)}"
            s.startsWith("#") && s.length == 9 -> s
            s.startsWith("0x") -> "#FF${s.drop(2)}"
            else -> "#FF0B0C14"
        }
    }

    private fun mapProperty(p: String): String? = when (p) {
        "x", "positionx", "position_x", "translationx" -> "x"
        "y", "positiony", "position_y", "translationy" -> "y"
        "scalex", "scale_x" -> "scaleX"; "scaley", "scale_y" -> "scaleY"
        "scale" -> "scaleX"; "rotation", "angle" -> "rotation"
        "opacity", "alpha" -> "opacity"; "anchorx" -> "anchorX"; "anchory" -> "anchorY"
        "skewx" -> "skewX"; "blur" -> "blur"; "brightness" -> "brightness"
        "color", "fill" -> "color"; "feather" -> "feather"; "volume" -> "volume"
        else -> null
    }

    private fun mapEasing(e: String): Easing = when {
        e.contains("hold") || e.contains("step") -> Easing.HOLD
        e.contains("bezier") || e.contains("custom") -> Easing.BEZIER
        e.contains("easeinout") || e.contains("smooth") -> Easing.EASE_IN_OUT
        e.contains("easein") -> Easing.EASE_IN
        e.contains("easeout") -> Easing.EASE_OUT
        e.contains("elastic") -> Easing.ELASTIC
        e.contains("bounce") -> Easing.BOUNCE
        else -> Easing.LINEAR
    }

    private class MutableLayer(
        val id: String, var name: String, var type: LayerType,
        var startMs: Long, var durationMs: Long, var source: String?,
        var transform: Transform,
        val keyframes: MutableList<KeyframeTrack> = mutableListOf(),
        val effects: MutableList<Effect> = mutableListOf(),
        val masks: MutableList<MaskSpec> = mutableListOf(),
        var shape: ShapeSpec? = null,
        var text: String? = null
    ) {
        fun keyframe(property: String, kf: Keyframe) {
            val idx = keyframes.indexOfFirst { it.property == property }
            if (idx >= 0) keyframes[idx] = keyframes[idx].copy(
                keyframes = (keyframes[idx].keyframes + kf).sortedBy { it.timeMs })
            else keyframes.add(KeyframeTrack(property, listOf(kf)))
        }
        fun build(capturedText: String): Layer {
            val t = text ?: capturedText.takeIf { it.isNotBlank() && type == LayerType.TEXT }
            return Layer(
                id = id, name = name, type = type,
                startMs = startMs, durationMs = durationMs,
                sourceUri = source,
                transform = transform,
                keyframes = keyframes,
                effects = effects,
                masks = masks,
                shape = shape ?: if (type == LayerType.SHAPE) ShapeSpec() else null,
                text = if (type == LayerType.TEXT) TextSpec(text = t ?: "Text") else null,
                vector = if (type == LayerType.VECTOR) VectorSpec() else null
            )
        }
    }
}
