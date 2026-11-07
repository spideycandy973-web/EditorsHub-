package com.editorshub.core.importer

import android.util.Base64
import android.util.Xml
import com.editorshub.core.data.ProjectSerializer
import com.editorshub.core.domain.model.*
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
        var embeddedData = StringBuilder()
        var inEmbeddedData = false
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
                        "composition", "comp", "project", "settings", "alightmotion", "amproject", "motionproject", "editorshubmotion" -> {
                            inComposition = true
                            anyAttr("name", "projectName")?.let { name = it }
                            anyAttr("width", "w")?.toIntOrNull()?.let { width = it }
                            anyAttr("height", "h")?.toIntOrNull()?.let { height = it }
                            anyAttr("fps", "framerate", "frameRate")?.toDoubleOrNull()?.let { fps = it }
                            anyAttr("durationMs", "duration")?.toDoubleOrNull()?.let { d ->
                                durationMs = if (attr("durationMs") == null && d < 1000)
                                    (d * 1000).roundToLong() else d.roundToLong()
                            }
                            anyAttr("background", "bg", "backgroundColor")?.let { background = normalizeColor(it) }
                        }
                        "layers", "audiotracks" -> inComposition = true
                        "editorshubdata" -> inEmbeddedData = true
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
                                startMs = longAttr("startMs", longAttr("start", 0L)).let {
                                    if (attr("startMs") == null && it < 1000 && durationMs > 1000) it * 1000L else it
                                },
                                durationMs = longAttr("durationMs", longAttr("duration", durationMs)).let {
                                    if (attr("durationMs") == null && it < 1000 && durationMs > 1000) it * 1000L else it
                                },
                                source = source,
                                transform = Transform(
                                    x = floatAttr("x", 0f), y = floatAttr("y", 0f),
                                    scaleX = floatAttr("scaleX", floatAttr("scale", 1f)),
                                    scaleY = floatAttr("scaleY", floatAttr("scale", 1f)),
                                    rotation = floatAttr("rotation", 0f),
                                    opacity = floatAttr("opacity", floatAttr("alpha", 1f))
                                ),
                                blend = runCatching {
                                    BlendMode.valueOf((attr("blend") ?: "NORMAL").uppercase())
                                }.getOrDefault(BlendMode.NORMAL)
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
                                    val key = com.editorshub.core.domain.effects.EffectCatalog.spec(type).params.first().key
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
                            val rawKind = (anyAttr("kind", "type") ?: "rectangle").lowercase()
                                .replace("_", "").replace("-", "").replace(" ", "")
                            val sk = when (rawKind) {
                                "circle", "ellipse" -> ShapeKind.CIRCLE
                                "rounded", "roundedrect", "roundedrectangle" -> ShapeKind.ROUNDED_RECTANGLE
                                "triangle" -> ShapeKind.TRIANGLE
                                "polygon" -> ShapeKind.POLYGON
                                "star" -> ShapeKind.STAR
                                "plus", "cross" -> ShapeKind.CROSS
                                "arc" -> ShapeKind.ARC
                                "callout", "speechbubble" -> ShapeKind.CALLOUT
                                "pie" -> ShapeKind.PIE
                                "hexagon" -> ShapeKind.HEXAGON
                                "flower" -> ShapeKind.FLOWER
                                "arrow" -> ShapeKind.ARROW
                                "trapezoid" -> ShapeKind.TRAPEZOID
                                "crescent" -> ShapeKind.CRESCENT
                                "line" -> ShapeKind.LINE
                                "beam" -> ShapeKind.BEAM
                                "curve", "flag" -> ShapeKind.FLAG
                                "seal", "burstseal" -> ShapeKind.SEAL
                                "diamond" -> ShapeKind.DIAMOND
                                "pentagon" -> ShapeKind.PENTAGON
                                "sparkle" -> ShapeKind.SPARKLE
                                "burst" -> ShapeKind.BURST
                                "ring" -> ShapeKind.RING
                                "halfcircle" -> ShapeKind.HALF_CIRCLE
                                "octagon" -> ShapeKind.OCTAGON
                                "leaf" -> ShapeKind.LEAF
                                "arrowhead" -> ShapeKind.ARROWHEAD
                                "crown" -> ShapeKind.CROWN
                                "hourglass" -> ShapeKind.HOURGLASS
                                "sector" -> ShapeKind.SECTOR
                                "notchedsquare" -> ShapeKind.NOTCHED_SQUARE
                                "wave" -> ShapeKind.WAVE
                                else -> ShapeKind.RECTANGLE
                            }
                            val strokeWidth = floatAttr("strokeWidth", 4f)
                            val enabled = attr("strokeEnabled")?.toBooleanStrictOrNull()
                                ?: (attr("strokeWidth")?.toFloatOrNull()?.let { it > 0f } ?: false)
                            currentLayer?.shape = ShapeSpec(
                                kind = sk,
                                width = floatAttr("width", 500f), height = floatAttr("height", 500f),
                                cornerRadius = floatAttr("cornerRadius", 0f),
                                sides = floatAttr("sides", 6f).toInt().coerceIn(3, 32),
                                starPoints = floatAttr("starPoints", 5f).toInt().coerceIn(3, 20),
                                innerRadius = floatAttr("innerRadius", 0.45f).coerceIn(0.05f, 0.95f),
                                fillColor = normalizeColor(anyAttr("fill", "color") ?: "#FFFFFFFF"),
                                fillColor2 = normalizeColor(anyAttr("fill2", "color2") ?: "#FF8B7BFF"),
                                gradient = runCatching { GradientType.valueOf((attr("gradient") ?: "SOLID").uppercase()) }
                                    .getOrDefault(GradientType.SOLID),
                                gradientAngle = floatAttr("gradientAngle", 45f),
                                strokeColor = normalizeColor(attr("strokeColor") ?: "#FF000000"),
                                strokeWidth = strokeWidth,
                                strokeEnabled = enabled,
                                strokeCap = floatAttr("strokeCap", 1f).toInt().coerceIn(0, 2),
                                strokeJoin = floatAttr("strokeJoin", 0f).toInt().coerceIn(0, 2),
                                strokeStart = floatAttr("strokeStart", 0f).coerceIn(0f, 100f),
                                strokeEnd = floatAttr("strokeEnd", 100f).coerceIn(0f, 100f),
                                shadowEnabled = attr("shadowEnabled")?.toBooleanStrictOrNull() ?: false,
                                shadowSize = floatAttr("shadowSize", 0f).coerceAtLeast(0f),
                                shadowAlpha = floatAttr("shadowAlpha", 1f).coerceIn(0f, 1f),
                                shadowX = floatAttr("shadowX", 0f), shadowY = floatAttr("shadowY", 3f),
                                shadowColor = normalizeColor(attr("shadowColor") ?: "#FF000000")
                            )
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
                    if (inEmbeddedData && embeddedData.length < 64 * 1024 * 1024) {
                        embeddedData.append(parser.text.trim())
                    } else if (currentLayer != null && textBuffer.length < 4096) {
                        textBuffer.append(parser.text.trim())
                    }
                }
                XmlPullParser.END_TAG -> {
                    val tag = parser.name.lowercase()
                    if (skipDepth > 0) { skipDepth--; continue }
                    when (tag) {
                        "keyframes", "animations" -> inKeyframes = false
                        "effects" -> inEffects = false
                        "masks" -> inMasks = false
                        "editorshubdata" -> inEmbeddedData = false
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

        if (embeddedData.isNotEmpty()) {
            val decoded = runCatching {
                val json = String(Base64.decode(embeddedData.toString(), Base64.DEFAULT), Charsets.UTF_8)
                ProjectSerializer.decode(json)
            }.getOrNull()
            if (decoded != null) {
                val now = System.currentTimeMillis()
                val imported = decoded.copy(
                    id = ImportIds.newId("project"),
                    createdAt = now,
                    updatedAt = now
                )
                val mediaUris = imported.layers.mapNotNull { it.sourceUri } + imported.audioTracks.map { it.uri }
                val missingMedia = mediaUris.filter { uri ->
                    when {
                        uri.startsWith("content:") || uri.startsWith("data:") ||
                            uri.startsWith("http:") || uri.startsWith("https:") -> false
                        uri.startsWith("file://") -> !java.io.File(uri.removePrefix("file://")).exists()
                        else -> !java.io.File(uri).exists()
                    }
                }
                val counts = intArrayOf(
                    imported.layers.size + imported.audioTracks.size,
                    imported.layers.sumOf { layer -> layer.keyframes.sumOf { it.keyframes.size } },
                    imported.layers.sumOf { it.effects.size },
                    imported.layers.sumOf { it.masks.size }
                )
                return Draft(imported, missingMedia.distinct(), emptyList(), emptyList(), counts)
            }
            warnings.add("The embedded EditorsHub project data could not be decoded; visible XML data was imported instead.")
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
        var blend: BlendMode = BlendMode.NORMAL,
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
                blendMode = blend,
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
