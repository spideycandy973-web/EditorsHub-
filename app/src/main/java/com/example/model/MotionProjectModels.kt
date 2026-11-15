package com.example.model

import kotlinx.serialization.Serializable
import java.util.UUID

fun newUid(prefix: String): String = "${prefix}_${UUID.randomUUID().toString().take(8)}"

@Serializable
enum class LayerType {
    SHAPE, TEXT, IMAGE, VIDEO, AUDIO, CAMERA, MESH, GROUP, NULL
}

@Serializable
enum class ShapeKind(val labelId: String, val labelEn: String) {
    RECT("Kotak", "Rectangle"),
    ROUND_RECT("Sudut Bulat", "Rounded Rect"),
    ELLIPSE("Lingkaran", "Circle"),
    TRIANGLE("Segitiga", "Triangle"),
    STAR("Bintang", "Star"),
    POLYGON("Poligon", "Polygon"),
    ARROW("Panah", "Arrow"),
    DIAMOND("Belah Ketupat", "Diamond"),
    PLUS("Plus", "Plus"),
    HEART("Hati", "Heart"),
    PIE("Pie", "Pie"),
    CRESCENT("Bulan Sabit", "Crescent"),
    PENTA("Segi Lima", "Pentagon"),
    DROP("Tetesan Air", "Droplet"),
    RING("Cincin", "Ring"),
    PILL("Kapsul", "Pill"),
    STAR4("Bintang 4", "4-Point Star"),
    BUBBLE("Balon Kata", "Speech Bubble"),
    LINE("Garis", "Line"),
    CHEVRON("Chevron", "Chevron"),
    HEXAGON("Segi Enam", "Hexagon"),
    OCTAGON("Segi Delapan", "Octagon"),
    SHIELD("Perisai", "Shield"),
    LIGHTNING("Petir", "Lightning"),
    CLOUD("Awan", "Cloud"),
    CROWN("Mahkota", "Crown"),
    ROCKET("Roket", "Rocket"),
    ARC("Busur", "Arc"),
    GEAR("Roda Gigi", "Gear"),
    SPIRAL("Spiral", "Spiral"),
    BURST("Ledakan", "Starburst"),
    TRAPEZOID("Trapesium", "Trapezoid"),
    PARALLELOGRAM("Jajar Genjang", "Parallelogram"),
    CHECKMARK("Centang", "Checkmark"),
    CROSS_ROUND("Silang Bulat", "Rounded Cross"),
    TAG("Label Tag", "Price Tag"),
    BOOKMARK("Penanda", "Bookmark"),
    FRAME("Bingkai", "Frame"),
    WAVE("Gelombang", "Wave"),
    FLOWER("Bunga", "Flower"),
    VECTOR_PATH("Path Vektor", "Vector Path")
}

@Serializable
enum class BlendModeKind(val label: String) {
    NORMAL("Normal"),
    MULTIPLY("Multiply"),
    SCREEN("Screen"),
    OVERLAY("Overlay"),
    DARKEN("Darken"),
    LIGHTEN("Lighten"),
    ADD("Linear Dodge (Add)"),
    DIFFERENCE("Difference")
}

@Serializable
enum class MatteModeKind(val labelId: String, val labelEn: String) {
    NONE("Tidak Ada", "None"),
    MASK("Track Matte (Mask)", "Track Matte (Mask)"),
    EXCLUDE("Exclude Matte (Punch)", "Exclude Matte (Punch)")
}

@Serializable
enum class RepeatModeKind(val labelId: String, val labelEn: String) {
    NONE("Nonaktif", "Off"),
    LINEAR("Linear", "Linear"),
    RADIAL("Radial (Melingkar)", "Radial"),
    GRID("Grid (Kisi)", "Grid")
}

@Serializable
enum class CameraLensMode(val label: String) {
    FREE("Free Lens"),
    PRIME("Prime 50mm"),
    OPTICAL_ZOOM("Optical Zoom"),
    DIGITAL("Digital Zoom")
}

@Serializable
enum class MeshKind(val label: String) {
    CUBE("3D Cube"),
    SPHERE("3D Sphere")
}

@Serializable
enum class TextAlignKind {
    LEFT, CENTER, RIGHT
}

@Serializable
data class EasingSpec(
    val preset: String = "linear",
    val x1: Float = 0.25f,
    val y1: Float = 0.25f,
    val x2: Float = 0.75f,
    val y2: Float = 0.75f
)

@Serializable
data class Keyframe(
    val id: String = newUid("kf"),
    val time: Float,
    val value: Float,
    val easing: EasingSpec = EasingSpec()
)

@Serializable
data class VectorNode(
    val x: Float,
    val y: Float,
    val inX: Float = 0f,
    val inY: Float = 0f,
    val outX: Float = 0f,
    val outY: Float = 0f,
    val smooth: Boolean = true,
    val symmetric: Boolean = true
)

@Serializable
data class VectorPathData(
    val closed: Boolean = true,
    val nodes: List<VectorNode> = emptyList()
)

@Serializable
data class MaskConfig(
    val enabled: Boolean = false,
    val type: String = "rect",
    val feather: Float = 0f,
    val invert: Boolean = false,
    val matteMode: MatteModeKind = MatteModeKind.NONE,
    val matteScope: String = "below",
    val matteTargetId: String? = null
)

@Serializable
data class RepeatConfig(
    val mode: RepeatModeKind = RepeatModeKind.NONE,
    val count: Int = 5,
    val cols: Int = 3,
    val rows: Int = 3,
    val offsetX: Float = 90f,
    val offsetY: Float = 0f,
    val offsetRotation: Float = 0f,
    val offsetScale: Float = 1f,
    val radialRadius: Float = 180f
)

@Serializable
data class LayerStyleConfig(
    val innerGlowEnabled: Boolean = false,
    val innerGlowHex: String = "#FFFFFF",
    val innerGlowRadius: Float = 18f,
    val innerGlowAlpha: Float = 0.65f,
    val dashedStroke: Boolean = false,
    val dashLength: Float = 18f,
    val dashGap: Float = 12f
)

@Serializable
data class EffectInstance(
    val id: String = newUid("fx"),
    val specId: String,
    val name: String,
    val category: String,
    val enabled: Boolean = true,
    val params: Map<String, Float> = emptyMap()
)

@Serializable
data class TimelineMarker(
    val id: String = newUid("mk"),
    val time: Float,
    val comment: String = "Marker",
    val colorHex: String = "#00D7A7"
)

@Serializable
data class LayerData(
    val id: String = newUid("layer"),
    val type: LayerType,
    val name: String,
    val start: Float = 0f,
    val duration: Float = 3f,
    val hidden: Boolean = false,
    val locked: Boolean = false,
    val parentId: String? = null,
    val space3d: Boolean = false,
    val x: Float = 540f,
    val y: Float = 960f,
    val z: Float = 0f,
    val w: Float = 420f,
    val h: Float = 420f,
    val depth: Float = 260f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val scaleZ: Float = 1f,
    val rotation: Float = 0f,
    val rotX: Float = 0f,
    val rotY: Float = 0f,
    val rotZ: Float = 0f,
    val skewX: Float = 0f,
    val skewY: Float = 0f,
    val opacity: Float = 1f,
    val anchorX: Float = 0.5f,
    val anchorY: Float = 0.5f,
    val colorHex: String = "#00D7A7",
    val strokeHex: String = "#FFFFFF",
    val strokeWidth: Float = 0f,
    val radius: Float = 28f,
    val blendMode: BlendModeKind = BlendModeKind.NORMAL,
    val mask: MaskConfig = MaskConfig(),
    val repeat: RepeatConfig = RepeatConfig(),
    val layerStyle: LayerStyleConfig = LayerStyleConfig(),
    val effects: List<EffectInstance> = emptyList(),
    // Shape properties
    val shapeKind: ShapeKind = ShapeKind.RECT,
    val shapePoints: Int = 5,
    val shapeInner: Float = 46f,
    val shapeSides: Int = 6,
    val vectorPath: VectorPathData? = null,
    // Text properties
    val textContent: String = "Open Motion",
    val fontSize: Float = 96f,
    val fontFamily: String = "Space Grotesk",
    val fontWeight: Int = 700,
    val italic: Boolean = false,
    val tracking: Float = 0f,
    val lineHeight: Float = 1.15f,
    val textAlign: TextAlignKind = TextAlignKind.CENTER,
    // Media / Audio properties
    val mediaUri: String? = null,
    val volume: Float = 1f,
    val playbackRate: Float = 1f,
    // Camera properties
    val fov: Float = 55f,
    val focusDistance: Float = 1200f,
    val dofEnabled: Boolean = false,
    val aperture: Float = 2.8f,
    val lensMode: CameraLensMode = CameraLensMode.FREE,
    val orbitEnabled: Boolean = false,
    val orbitTargetId: String? = null,
    val orbitAzimuth: Float = 0f,
    val orbitElevation: Float = 0f,
    val orbitDistance: Float = 1400f,
    // 3D Mesh
    val meshKind: MeshKind = MeshKind.CUBE,
    // Keyframes keyed by property name
    val keyframes: Map<String, List<Keyframe>> = emptyMap()
)

@Serializable
data class ProjectData(
    val id: String = newUid("project"),
    val name: String = "Proyek Baru",
    val width: Int = 1080,
    val height: Int = 1920,
    val fps: Int = 30,
    val duration: Float = 10f,
    val bgHex: String = "#10121D",
    val ratio: String = "9:16",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val gridEnabled: Boolean = true,
    val ambientIntensity: Float = 0.72f,
    val timelinePxPerSec: Float = 64f,
    val space3dActive: Boolean = false
)

@Serializable
data class ProjectDocument(
    val project: ProjectData = ProjectData(),
    val layers: List<LayerData> = emptyList(),
    val markers: List<TimelineMarker> = emptyList()
)

object AspectRatioPresets {
    val ratios = listOf("9:16", "16:9", "4:5", "1:1", "4:3", "Custom")
    val resolutions = listOf(720, 1080, 1440)
    val fpsOptions = listOf(24, 30, 60)

    fun computeDimensions(ratio: String, shortSide: Int, customW: Int = 1080, customH: Int = 1920): Pair<Int, Int> {
        fun even(v: Int) = ((v.coerceIn(64, 4096) + 1) / 2) * 2
        if (ratio == "Custom") return even(customW) to even(customH)
        val parts = ratio.split(":").mapNotNull { it.toIntOrNull() }
        val rw = parts.getOrNull(0)?.takeIf { it > 0 } ?: 9
        val rh = parts.getOrNull(1)?.takeIf { it > 0 } ?: 16
        return if (rw >= rh) {
            even((shortSide.toFloat() * rw / rh).toInt()) to even(shortSide)
        } else {
            even(shortSide) to even((shortSide.toFloat() * rh / rw).toInt())
        }
    }
}
