package com.editorshub.core.domain.model

import kotlinx.serialization.Serializable

/** Bumped whenever the on-disk project JSON shape changes. */
const val PROJECT_FORMAT_VERSION = 2

@Serializable enum class LayerType { VIDEO, IMAGE, AUDIO, TEXT, SHAPE, VECTOR, SOLID, EFFECT, GROUP }
@Serializable enum class BlendMode { NORMAL, MULTIPLY, SCREEN, OVERLAY, DARKEN, LIGHTEN, ADD, DIFFERENCE }
@Serializable enum class Easing { LINEAR, HOLD, EASE_IN, EASE_OUT, EASE_IN_OUT, BEZIER, ELASTIC, BOUNCE }
@Serializable enum class EffectType {
    GAUSSIAN_BLUR, MOTION_BLUR, DIRECTIONAL_BLUR, GLOW, SHADOW,
    BRIGHTNESS, CONTRAST, SATURATION, HUE, EXPOSURE, VIGNETTE, SHARPEN,
    NOISE, RGB_SPLIT, CHROMATIC_ABERRATION, PIXELATION, WAVE_DISTORTION, COLOR_GRADING
}
@Serializable enum class TransitionType { FADE_IN, FADE_OUT, CROSSFADE, ZOOM, SLIDE, SPIN, BLUR, WIPE, CUSTOM }
@Serializable enum class ShapeKind { RECTANGLE, CIRCLE, TRIANGLE, POLYGON, STAR, LINE, HEART, ARROW }
@Serializable enum class GradientType { SOLID, LINEAR, RADIAL }
@Serializable enum class MaskKind { RECTANGLE, CIRCLE, PATH }
@Serializable enum class FitMode { COVER, CONTAIN, STRETCH, NONE }
@Serializable enum class TextAlign { LEFT, CENTER, RIGHT }
@Serializable enum class AudioEffectType { NONE, ECHO, BASS_BOOST, TREBLE }
@Serializable enum class VideoCodec { H264, H265 }
@Serializable enum class BackgroundType { SOLID, TRANSPARENT, LINEAR_GRADIENT, RADIAL_GRADIENT }
@Serializable enum class ColorSpace { SRGB, DISPLAY_P3, BT709 }

/** Exact rational frame rates used by the timeline, playback and encoder. */
object FrameRates {
    data class Preset(val label: String, val exact: Double)
    val ALL = listOf(
        Preset("12 fps", 12.0), Preset("15 fps", 15.0),
        Preset("23.976 fps", 23.976), Preset("24 fps", 24.0), Preset("25 fps", 25.0),
        Preset("29.97 fps", 29.97), Preset("30 fps", 30.0), Preset("48 fps", 48.0),
        Preset("50 fps", 50.0), Preset("59.94 fps", 59.94), Preset("60 fps", 60.0),
        Preset("90 fps", 90.0), Preset("120 fps", 120.0)
    )
    fun rounded(exact: Double): Int = kotlin.math.round(exact).toInt().coerceIn(1, 240)
    fun frameDurationMs(exact: Double): Double = 1000.0 / exact.coerceIn(1.0, 240.0)
}

/** Fully persisted project-creation configuration. */
@Serializable
data class ProjectSpec(
    val name: String = "Untitled",
    val width: Int = 1080,
    val height: Int = 1920,
    val aspect: String = "9:16",
    val fpsExact: Double = 30.0,
    val durationMs: Long = 10_000L,
    val backgroundType: BackgroundType = BackgroundType.SOLID,
    val background: String = "#FF0B0C14",
    val backgroundEnd: String = "#FF22D3EE",
    val backgroundAngle: Float = 45f,
    val audioSampleRate: Int = 48_000,
    val colorSpace: ColorSpace = ColorSpace.SRGB,
    val previewQuality: Float = 0.75f,
    val timebase: String = "1/30"
)

@Serializable
data class Keyframe(
    val id: String = "",
    val timeMs: Long = 0L,
    val value: Float = 0f,
    /** "#AARRGGBB" for color tracks; null for numeric tracks. */
    val color: String? = null,
    val easing: Easing = Easing.LINEAR,
    val hold: Boolean = false,
    val cp1x: Float = 0.25f,
    val cp1y: Float = 0.1f,
    val cp2x: Float = 0.25f,
    val cp2y: Float = 1f
)

@Serializable
data class KeyframeTrack(val property: String = "", val keyframes: List<Keyframe> = emptyList())

@Serializable
data class Transform(
    val x: Float = 0f,
    val y: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val rotation: Float = 0f,
    val opacity: Float = 1f,
    val anchorX: Float = 0f,
    val anchorY: Float = 0f,
    val skewX: Float = 0f
)

@Serializable
data class Effect(
    val id: String = "",
    val type: EffectType,
    val enabled: Boolean = true,
    val params: Map<String, Float> = emptyMap(),
    val animated: Map<String, KeyframeTrack> = emptyMap()
)

@Serializable
data class TextSpec(
    val text: String = "Your text",
    val fontFamily: String = "sans",
    val fontSize: Float = 96f,
    val fontWeight: Int = 700,
    val italic: Boolean = false,
    val align: TextAlign = TextAlign.CENTER,
    val color: String = "#FFFFFFFF",
    val color2: String = "#FF8B7BFF",
    val gradient: GradientType = GradientType.SOLID,
    val gradientAngle: Float = 45f,
    val letterSpacing: Float = 0f,
    val lineSpacing: Float = 1.2f,
    val shadowBlur: Float = 0f,
    val shadowColor: String = "#00000000",
    val shadowDx: Float = 0f,
    val shadowDy: Float = 0f,
    val outlineWidth: Float = 0f,
    val outlineColor: String = "#FF000000",
    val uppercase: Boolean = false,
    val preset: String = "none"
)

@Serializable
data class ShapeSpec(
    val kind: ShapeKind = ShapeKind.RECTANGLE,
    val width: Float = 500f,
    val height: Float = 500f,
    val cornerRadius: Float = 0f,
    val sides: Int = 6,
    val starPoints: Int = 5,
    val innerRadius: Float = 0.45f,
    val fillColor: String = "#FFFFFFFF",
    val fillColor2: String = "#FF8B7BFF",
    val gradient: GradientType = GradientType.SOLID,
    val gradientAngle: Float = 45f,
    val strokeColor: String = "#FF000000",
    val strokeWidth: Float = 0f
)

/** A vector path as an ordered list of drawing commands. args are in layer-local px. */
@Serializable
data class VectorCommand(val op: String = "M", val args: List<Float> = emptyList())

@Serializable
data class VectorSpec(
    val commands: List<VectorCommand> = emptyList(),
    val fillColor: String = "#FFFFFFFF",
    val fillColor2: String = "#FF22D3EE",
    val gradient: GradientType = GradientType.SOLID,
    val gradientAngle: Float = 45f,
    val strokeColor: String = "#FF000000",
    val strokeWidth: Float = 0f,
    val closed: Boolean = true
)

@Serializable
data class MaskSpec(
    val id: String = "",
    val kind: MaskKind = MaskKind.RECTANGLE,
    val featherPx: Float = 0f,
    val opacity: Float = 1f,
    val expansionPx: Float = 0f,
    val inverted: Boolean = false,
    val path: VectorSpec? = null,
    val keyframes: List<KeyframeTrack> = emptyList()
)

@Serializable
data class TransitionSpec(
    val type: TransitionType = TransitionType.FADE_IN,
    val durationMs: Long = 600L,
    val params: Map<String, Float> = emptyMap()
)

/** A crossfade / wipe between two adjacent layers. */
@Serializable
data class TransitionClip(
    val id: String = "",
    val type: TransitionType = TransitionType.CROSSFADE,
    val fromLayerId: String = "",
    val toLayerId: String = "",
    val durationMs: Long = 600L
)

@Serializable
data class Layer(
    val id: String = "",
    val name: String = "Layer",
    val type: LayerType,
    val startMs: Long = 0L,
    val durationMs: Long = 5_000L,
    val inPointMs: Long = 0L,
    val visible: Boolean = true,
    val locked: Boolean = false,
    val enabled: Boolean = true,
    val muted: Boolean = false,
    val blendMode: BlendMode = BlendMode.NORMAL,
    val parentId: String? = null,
    val sourceUri: String? = null,
    val fit: FitMode = FitMode.COVER,
    val transform: Transform = Transform(),
    val keyframes: List<KeyframeTrack> = emptyList(),
    val effects: List<Effect> = emptyList(),
    val text: TextSpec? = null,
    val shape: ShapeSpec? = null,
    val vector: VectorSpec? = null,
    val masks: List<MaskSpec> = emptyList(),
    val transitionIn: TransitionSpec? = null,
    val transitionOut: TransitionSpec? = null
)

@Serializable
data class AudioTrack(
    val id: String = "",
    val uri: String = "",
    val name: String = "Audio",
    val startMs: Long = 0L,
    val durationMs: Long = 0L,
    val inPointMs: Long = 0L,
    val volume: Float = 1f,
    val muted: Boolean = false,
    val fadeInMs: Long = 0L,
    val fadeOutMs: Long = 0L,
    val keyframes: List<KeyframeTrack> = emptyList(),
    val peaks: List<Float> = emptyList(),
    val effect: AudioEffectType = AudioEffectType.NONE
)

@Serializable
data class Project(
    val id: String = "",
    val name: String = "Untitled",
    val width: Int = 1080,
    val height: Int = 1920,
    val fps: Int = 30,
    /** Exact frame rate (23.976 / 29.97 / 59.94 supported). */
    val fpsExact: Double = 30.0,
    val durationMs: Long = 10_000L,
    val background: String = "#FF0B0C14",
    val backgroundType: BackgroundType = BackgroundType.SOLID,
    val backgroundEnd: String = "#FF22D3EE",
    val backgroundAngle: Float = 45f,
    val audioSampleRate: Int = 48_000,
    val colorSpace: ColorSpace = ColorSpace.SRGB,
    val previewQuality: Float = 0.75f,
    val timebase: String = "1/30",
    val layers: List<Layer> = emptyList(),
    val audioTracks: List<AudioTrack> = emptyList(),
    val transitions: List<TransitionClip> = emptyList(),
    val version: Int = PROJECT_FORMAT_VERSION,
    val updatedAt: Long = 0L,
    val createdAt: Long = 0L
)

@Serializable
data class ExportConfig(
    val width: Int = 1080,
    val height: Int = 1920,
    val fps: Int = 30,
    val fpsExact: Double = 30.0,
    val bitrate: Int = 8_000_000,
    val audioBitrate: Int = 128_000,
    val codec: VideoCodec = VideoCodec.H264,
    val includeAudio: Boolean = true,
    val hardwarePreferred: Boolean = true,
    val saveLocation: String = "gallery"
)

/** Plain, non-serialized enum for settings UI. */
enum class ExportResolution(val label: String, val shortSide: Int) {
    P480("480p", 480), P720("720p", 720), P1080("1080p", 1080),
    P1440("1440p", 1440), P2160("4K", 2160)
}

data class ProjectSummary(
    val id: String,
    val name: String,
    val updatedAt: Long,
    val createdAt: Long,
    val layerCount: Int,
    val version: Int,
    val thumbnailPath: String?,
    val interrupted: Boolean,
    val width: Int,
    val height: Int,
    val fpsExact: Double,
    val durationMs: Long,
    val bytes: Long
)

/** Pure color helpers: "#RRGGBB" or "#AARRGGBB" -> packed ARGB int. */
object ColorUtil {
    fun parse(value: String, fallback: Int = 0xFFFFFFFF.toInt()): Int {
        val s = value.trim().removePrefix("#")
        return try {
            when (s.length) {
                6 -> (0xFF000000.toInt() or s.toInt(16))
                8 -> s.toLong(16).toInt()
                else -> fallback
            }
        } catch (e: Exception) { fallback }
    }

    fun toHex(argb: Int): String = "#%08X".format(argb)

    fun lerp(a: Int, b: Int, t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        val ar = (a ushr 24) and 0xFF
        val ag = (a ushr 16) and 0xFF
        val ab = (a ushr 8) and 0xFF
        val aa = a and 0xFF
        val br = (b ushr 24) and 0xFF
        val bg = (b ushr 16) and 0xFF
        val bb = (b ushr 8) and 0xFF
        val ba = b and 0xFF
        val r = (ar + (br - ar) * k).toInt() and 0xFF
        val g = (ag + (bg - ag) * k).toInt() and 0xFF
        val bl = (ab + (bb - ab) * k).toInt() and 0xFF
        val al = (aa + (ba - aa) * k).toInt() and 0xFF
        return (r shl 24) or (g shl 16) or (bl shl 8) or al
    }
}
