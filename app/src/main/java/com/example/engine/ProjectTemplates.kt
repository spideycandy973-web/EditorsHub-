package com.example.engine

import com.example.model.*

data class MotionTemplateInfo(
    val id: String,
    val titleId: String,
    val titleEn: String,
    val badge: String,
    val descId: String,
    val descEn: String,
    val accentHex: String
)

object ProjectTemplates {

    val catalog = listOf(
        MotionTemplateInfo(
            id = "title",
            titleId = "Judul Animasi Teks",
            titleEn = "Title Motion",
            badge = "TEKS",
            descId = "Overshoot scale bounce dengan kurva elastis modern dan aksen neon.",
            descEn = "Elastic overshoot title reveal with glowing accent bar.",
            accentHex = "#00D7A7"
        ),
        MotionTemplateInfo(
            id = "parallax",
            titleId = "Efek Parallax 3D",
            titleEn = "3D Parallax Scene",
            badge = "3D PARALLAX",
            descId = "Tiga lapisan shape pada bidang kedalaman Z berbeda dengan kamera aktif.",
            descEn = "Multi-depth Z-space geometric layers with perspective camera push.",
            accentHex = "#8374FF"
        ),
        MotionTemplateInfo(
            id = "camera",
            titleId = "Kamera Sinematik",
            titleEn = "Cinematic Camera",
            badge = "KAMERA",
            descId = "Gerakan kamera dinamis mengelilingi objek 3D Cube dan partikel cahaya.",
            descEn = "Dynamic 3D camera push around an orbiting 3D Cube and particles.",
            accentHex = "#FF907C"
        ),
        MotionTemplateInfo(
            id = "pulse",
            titleId = "Transisi Neon Pulse",
            titleEn = "Neon Pulse Intro",
            badge = "EFEK",
            descId = "Bentuk bintang & cincin dengan efek Chromatic Aberration, Glow, dan Spektrum.",
            descEn = "Rhythmic star & ring composition with RGB split, Glow, and Spectrum.",
            accentHex = "#FFC04A"
        )
    )

    fun createFromTemplate(templateId: String): ProjectDocument {
        val project = ProjectData(
            name = catalog.find { it.id == templateId }?.titleId ?: "Templat Open Motion",
            width = 1080,
            height = 1920,
            fps = 30,
            duration = 6f,
            bgHex = "#10121D",
            ratio = "9:16",
            space3dActive = templateId == "parallax" || templateId == "camera"
        )

        val overshoot = EasingSpec("overshoot", 0.34f, 1.56f, 0.64f, 1f)
        val easeOut = EasingSpec("easeOut", 0.16f, 1f, 0.30f, 1f)
        val inOut = EasingSpec("easeInOut", 0.42f, 0f, 0.58f, 1f)

        val layers = when (templateId) {
            "parallax" -> listOf(
                LayerData(
                    type = LayerType.CAMERA,
                    name = "Kamera Utama",
                    duration = 6f,
                    space3d = true,
                    x = 0f, y = 0f, z = 1400f,
                    fov = 55f,
                    keyframes = mapOf(
                        "x" to listOf(Keyframe(time = 0f, value = -160f, easing = inOut), Keyframe(time = 5f, value = 180f)),
                        "z" to listOf(Keyframe(time = 0f, value = 1600f, easing = inOut), Keyframe(time = 5f, value = 1050f))
                    )
                ),
                LayerData(
                    type = LayerType.SHAPE,
                    name = "Lingkaran Latar Z-",
                    shapeKind = ShapeKind.ELLIPSE,
                    duration = 6f,
                    space3d = true,
                    x = 540f, y = 780f, z = -340f,
                    w = 620f, h = 620f,
                    colorHex = "#8374FF",
                    opacity = 0.65f,
                    effects = listOf(EffectsCatalog.createInstance(EffectsCatalog.byId("glow")!!))
                ),
                LayerData(
                    type = LayerType.SHAPE,
                    name = "Cincin Tengah",
                    shapeKind = ShapeKind.RING,
                    duration = 6f,
                    space3d = true,
                    x = 540f, y = 960f, z = 0f,
                    w = 500f, h = 500f,
                    colorHex = "#00D7A7",
                    keyframes = mapOf(
                        "rotation" to listOf(Keyframe(time = 0f, value = 0f, easing = easeOut), Keyframe(time = 5.5f, value = 180f))
                    )
                ),
                LayerData(
                    type = LayerType.TEXT,
                    name = "Judul Parallax",
                    textContent = "DEPTH 3D",
                    fontSize = 116f,
                    fontFamily = "Space Grotesk",
                    duration = 6f,
                    space3d = true,
                    x = 540f, y = 960f, z = 240f,
                    w = 880f, h = 240f,
                    colorHex = "#F2F4FB"
                )
            )
            "camera" -> listOf(
                LayerData(
                    type = LayerType.CAMERA,
                    name = "Kamera Orbit",
                    duration = 6f,
                    space3d = true,
                    x = 0f, y = 0f, z = 1400f,
                    fov = 58f,
                    keyframes = mapOf(
                        "rotZ" to listOf(Keyframe(time = 0f, value = -10f, easing = inOut), Keyframe(time = 5f, value = 12f)),
                        "z" to listOf(Keyframe(time = 0f, value = 1750f, easing = easeOut), Keyframe(time = 5f, value = 1050f))
                    )
                ),
                LayerData(
                    type = LayerType.MESH,
                    name = "3D Hero Cube",
                    meshKind = MeshKind.CUBE,
                    duration = 6f,
                    space3d = true,
                    x = 540f, y = 940f, z = 0f,
                    w = 340f, h = 340f, depth = 340f,
                    rotX = -22f, rotY = 30f,
                    colorHex = "#FF907C",
                    keyframes = mapOf(
                        "rotY" to listOf(Keyframe(time = 0f, value = 0f), Keyframe(time = 6f, value = 360f)),
                        "rotX" to listOf(Keyframe(time = 0f, value = -20f, easing = inOut), Keyframe(time = 3f, value = 25f, easing = inOut), Keyframe(time = 6f, value = -20f))
                    )
                ),
                LayerData(
                    type = LayerType.SHAPE,
                    name = "Partikel Ambient",
                    shapeKind = ShapeKind.RECT,
                    duration = 6f,
                    x = 540f, y = 960f,
                    w = 960f, h = 1500f,
                    colorHex = "#000000",
                    opacity = 0.85f,
                    effects = listOf(EffectsCatalog.createInstance(EffectsCatalog.byId("particles")!!))
                )
            )
            "pulse" -> listOf(
                LayerData(
                    type = LayerType.SHAPE,
                    name = "Bintang Neon",
                    shapeKind = ShapeKind.STAR,
                    duration = 6f,
                    x = 540f, y = 840f,
                    w = 460f, h = 460f,
                    colorHex = "#FFC04A",
                    effects = listOf(
                        EffectsCatalog.createInstance(EffectsCatalog.byId("glow")!!),
                        EffectsCatalog.createInstance(EffectsCatalog.byId("chromatic_aberration")!!),
                        EffectsCatalog.createInstance(EffectsCatalog.byId("pulse_scale")!!)
                    ),
                    keyframes = mapOf(
                        "rotation" to listOf(Keyframe(time = 0f, value = 0f), Keyframe(time = 6f, value = 240f))
                    )
                ),
                LayerData(
                    type = LayerType.SHAPE,
                    name = "Visualizer Audio",
                    shapeKind = ShapeKind.RECT,
                    duration = 6f,
                    x = 540f, y = 1360f,
                    w = 880f, h = 260f,
                    colorHex = "#00D7A7",
                    effects = listOf(EffectsCatalog.createInstance(EffectsCatalog.byId("audio_spectrum")!!))
                )
            )
            else -> listOf(
                LayerData(
                    type = LayerType.SHAPE,
                    name = "Kartu Aksentuasi",
                    shapeKind = ShapeKind.ROUND_RECT,
                    duration = 6f,
                    x = 540f, y = 960f,
                    w = 760f, h = 250f,
                    radius = 48f,
                    colorHex = "#1A2236",
                    strokeHex = "#00D7A7",
                    strokeWidth = 6f,
                    keyframes = mapOf(
                        "scaleX" to listOf(Keyframe(time = 0f, value = 0.2f, easing = overshoot), Keyframe(time = 1.1f, value = 1f)),
                        "scaleY" to listOf(Keyframe(time = 0f, value = 0.2f, easing = overshoot), Keyframe(time = 1.1f, value = 1f))
                    )
                ),
                LayerData(
                    type = LayerType.TEXT,
                    name = "OPEN MOTION",
                    textContent = "OPEN MOTION",
                    fontSize = 96f,
                    fontFamily = "Space Grotesk",
                    duration = 6f,
                    x = 540f, y = 960f,
                    w = 740f, h = 180f,
                    colorHex = "#F2F4FB",
                    keyframes = mapOf(
                        "opacity" to listOf(Keyframe(time = 0.15f, value = 0f, easing = easeOut), Keyframe(time = 0.9f, value = 1f)),
                        "y" to listOf(Keyframe(time = 0.15f, value = 1040f, easing = overshoot), Keyframe(time = 1.1f, value = 960f))
                    )
                )
            )
        }

        return ProjectDocument(
            project = project,
            layers = layers,
            markers = listOf(TimelineMarker(time = 1.1f, comment = "Beat Hit", colorHex = "#00D7A7"))
        )
    }
}
