package com.example.model

data class EffectParamSpec(
    val id: String,
    val labelId: String,
    val labelEn: String,
    val min: Float,
    val max: Float,
    val defaultValue: Float,
    val step: Float = 0.5f,
    val unit: String = ""
)

data class EffectSpec(
    val id: String,
    val nameId: String,
    val nameEn: String,
    val category: String,
    val descriptionId: String,
    val descriptionEn: String,
    val params: List<EffectParamSpec>,
    val featured: Boolean = false
)

object EffectsCatalog {
    val categories = listOf(
        "Semua", "Populer", "Blur", "Warna", "Distorsi", "Gerak", "Simulasi", "Stilisasi"
    )

    val all: List<EffectSpec> = listOf(
        EffectSpec(
            id = "gaussian_blur",
            nameId = "Gaussian Blur",
            nameEn = "Gaussian Blur",
            category = "Blur",
            descriptionId = "Menghaluskan tepi dan detail layer secara merata.",
            descriptionEn = "Softens edges and layer details smoothly.",
            featured = true,
            params = listOf(
                EffectParamSpec("radius", "Radius Blur", "Blur Radius", 0f, 80f, 14f, 0.5f, "px")
            )
        ),
        EffectSpec(
            id = "directional_motion_blur",
            nameId = "Motion Blur Arah",
            nameEn = "Directional Motion Blur",
            category = "Blur",
            descriptionId = "Efek jejak gerak berarah sesuai sudut dan jarak.",
            descriptionEn = "Directional motion streak along a chosen angle.",
            featured = true,
            params = listOf(
                EffectParamSpec("angle", "Sudut", "Angle", -180f, 180f, 0f, 1f, "°"),
                EffectParamSpec("distance", "Jarak", "Distance", 0f, 90f, 22f, 1f, "px")
            )
        ),
        EffectSpec(
            id = "zoom_motion_blur",
            nameId = "Zoom Motion Blur",
            nameEn = "Zoom Motion Blur",
            category = "Blur",
            descriptionId = "Jejak zoom radial cepat menuju pusat objek.",
            descriptionEn = "Radial speed zoom blur radiating from the center.",
            params = listOf(
                EffectParamSpec("strength", "Kekuatan", "Strength", 0f, 100f, 35f, 1f, "%")
            )
        ),
        EffectSpec(
            id = "spin_motion_blur",
            nameId = "Spin Motion Blur",
            nameEn = "Spin Motion Blur",
            category = "Blur",
            descriptionId = "Blur rotasi melingkar untuk aksi putaran cepat.",
            descriptionEn = "Rotational spin blur trails.",
            params = listOf(
                EffectParamSpec("angle", "Sudut Putar", "Spin Angle", 0f, 45f, 16f, 0.5f, "°")
            )
        ),
        EffectSpec(
            id = "brightness_contrast",
            nameId = "Kecerahan & Kontras",
            nameEn = "Brightness & Contrast",
            category = "Warna",
            descriptionId = "Atur tingkat terang dan kontras warna layer.",
            descriptionEn = "Adjust layer brightness and contrast.",
            featured = true,
            params = listOf(
                EffectParamSpec("brightness", "Kecerahan", "Brightness", -100f, 100f, 12f, 1f, "%"),
                EffectParamSpec("contrast", "Kontras", "Contrast", -100f, 100f, 20f, 1f, "%")
            )
        ),
        EffectSpec(
            id = "hue_saturation",
            nameId = "Hue & Saturasi",
            nameEn = "Hue & Saturation",
            category = "Warna",
            descriptionId = "Geser spektrum warna dan intensitas saturasi.",
            descriptionEn = "Shift hue angle and saturation intensity.",
            params = listOf(
                EffectParamSpec("hue", "Geser Hue", "Hue Shift", -180f, 180f, 0f, 1f, "°"),
                EffectParamSpec("saturation", "Saturasi", "Saturation", 0f, 200f, 125f, 1f, "%")
            )
        ),
        EffectSpec(
            id = "exposure_gamma",
            nameId = "Eksposur & Gamma",
            nameEn = "Exposure & Gamma",
            category = "Warna",
            descriptionId = "Koreksi eksposur cahaya kamera dan midtone.",
            descriptionEn = "Fine-tune exposure and midtone gain.",
            params = listOf(
                EffectParamSpec("exposure", "Eksposur", "Exposure", -2f, 2f, 0.3f, 0.05f, "EV"),
                EffectParamSpec("gamma", "Gamma", "Gamma", 0.3f, 2.5f, 1.0f, 0.05f, "")
            )
        ),
        EffectSpec(
            id = "invert",
            nameId = "Balik Warna (Invert)",
            nameEn = "Invert Colors",
            category = "Warna",
            descriptionId = "Membalikkan warna negatif RGB secara instan.",
            descriptionEn = "Inverts RGB color channels for negative look.",
            params = listOf(
                EffectParamSpec("amount", "Campuran", "Mix", 0f, 100f, 100f, 1f, "%")
            )
        ),
        EffectSpec(
            id = "posterize",
            nameId = "Posterize / Kuantisasi",
            nameEn = "Posterize",
            category = "Warna",
            descriptionId = "Batasi tingkat gradasi warna untuk gaya poster retro.",
            descriptionEn = "Reduce tonal levels for crisp posterized graphics.",
            params = listOf(
                EffectParamSpec("levels", "Tingkat Warna", "Color Levels", 2f, 24f, 6f, 1f, "")
            )
        ),
        EffectSpec(
            id = "glow",
            nameId = "Cahaya Neon (Glow)",
            nameEn = "Neon Glow",
            category = "Stilisasi",
            descriptionId = "Tambahkan pendar cahaya bercahaya di sekitar layer.",
            descriptionEn = "Add luminous outer glow around the layer.",
            featured = true,
            params = listOf(
                EffectParamSpec("radius", "Radius Cahaya", "Glow Radius", 2f, 80f, 24f, 1f, "px"),
                EffectParamSpec("intensity", "Intensitas", "Intensity", 0f, 100f, 75f, 1f, "%")
            )
        ),
        EffectSpec(
            id = "chromatic_aberration",
            nameId = "Chromatic Aberration",
            nameEn = "Chromatic Aberration",
            category = "Distorsi",
            descriptionId = "Pisahkan kanal RGB untuk efek lensa sinematik/glitch.",
            descriptionEn = "Split RGB channels for cinematic lens fringing.",
            featured = true,
            params = listOf(
                EffectParamSpec("offset", "Pergeseran RGB", "RGB Shift", 0f, 48f, 10f, 0.5f, "px"),
                EffectParamSpec("angle", "Sudut", "Angle", -180f, 180f, 15f, 1f, "°")
            )
        ),
        EffectSpec(
            id = "vignette",
            nameId = "Vignette",
            nameEn = "Vignette",
            category = "Stilisasi",
            descriptionId = "Gelapkan pinggiran layer untuk fokus sinematik.",
            descriptionEn = "Darken outer edges for cinematic focus.",
            params = listOf(
                EffectParamSpec("intensity", "Kekuatan", "Strength", 0f, 100f, 55f, 1f, "%"),
                EffectParamSpec("radius", "Radius", "Radius", 10f, 100f, 65f, 1f, "%")
            )
        ),
        EffectSpec(
            id = "pixelate",
            nameId = "Piksel / Mosaik",
            nameEn = "Pixelate / Mosaic",
            category = "Stilisasi",
            descriptionId = "Ubah tampilan layer menjadi blok mosaik/pixel art.",
            descriptionEn = "Create crisp pixel mosaic tiles across the layer.",
            params = listOf(
                EffectParamSpec("size", "Ukuran Blok", "Block Size", 2f, 80f, 16f, 1f, "px")
            )
        ),
        EffectSpec(
            id = "scanlines",
            nameId = "Garis CRT / Scanlines",
            nameEn = "CRT Scanlines",
            category = "Stilisasi",
            descriptionId = "Tambahkan garis monitor CRT retro bergerak.",
            descriptionEn = "Add animated retro CRT scanline overlay.",
            params = listOf(
                EffectParamSpec("density", "Kepadatan", "Density", 4f, 48f, 14f, 1f, "px"),
                EffectParamSpec("opacity", "Opasitas", "Opacity", 5f, 95f, 42f, 1f, "%")
            )
        ),
        EffectSpec(
            id = "mirror",
            nameId = "Cermin (Mirror)",
            nameEn = "Mirror / Kaleidoscope",
            category = "Distorsi",
            descriptionId = "Pantulkan layer secara simetris horizontal/vertikal.",
            descriptionEn = "Reflect layer horizontally or vertically.",
            params = listOf(
                EffectParamSpec("axis", "Mode (0:H 1:V 2:4x)", "Axis (0:H 1:V 2:Quad)", 0f, 2f, 0f, 1f, "")
            )
        ),
        EffectSpec(
            id = "swing",
            nameId = "Ayun / Oscillate",
            nameEn = "Oscillate / Swing",
            category = "Gerak",
            descriptionId = "Ayunan rotasi dan posisi otomatis berirama.",
            descriptionEn = "Automatic rhythmic rotational & positional swing.",
            featured = true,
            params = listOf(
                EffectParamSpec("frequency", "Frekuensi", "Frequency", 0.1f, 8f, 1.2f, 0.1f, "Hz"),
                EffectParamSpec("angle", "Sudut Ayun", "Swing Angle", 0f, 90f, 12f, 0.5f, "°"),
                EffectParamSpec("amplitude", "Amplitudo Y", "Y Amplitude", 0f, 150f, 18f, 1f, "px")
            )
        ),
        EffectSpec(
            id = "pulse_scale",
            nameId = "Denyut Skala",
            nameEn = "Pulse Scale",
            category = "Gerak",
            descriptionId = "Animasi denyut skala ritmis otomatis.",
            descriptionEn = "Rhythmic breathing scale pulse.",
            params = listOf(
                EffectParamSpec("speed", "Kecepatan", "Speed", 0.2f, 8f, 2f, 0.1f, "Hz"),
                EffectParamSpec("amount", "Besar Denyut", "Amount", 0f, 0.8f, 0.14f, 0.01f, "x")
            )
        ),
        EffectSpec(
            id = "auto_shake",
            nameId = "Guncangan Kamera (Shake)",
            nameEn = "Camera Shake / Wiggle",
            category = "Gerak",
            descriptionId = "Guncangan organik bergetar otomatis untuk aksi.",
            descriptionEn = "Procedural organic position & rotation shake.",
            featured = true,
            params = listOf(
                EffectParamSpec("magnitude", "Kekuatan", "Magnitude", 0f, 120f, 24f, 1f, "px"),
                EffectParamSpec("frequency", "Kecepatan", "Speed", 1f, 25f, 8f, 0.5f, "Hz"),
                EffectParamSpec("rotAmount", "Getar Rotasi", "Rot Shake", 0f, 20f, 3f, 0.5f, "°")
            )
        ),
        EffectSpec(
            id = "spin_auto",
            nameId = "Putar Otomatis (Auto Spin)",
            nameEn = "Continuous Spin",
            category = "Gerak",
            descriptionId = "Putar layer terus-menerus sesuai kecepatan RPS.",
            descriptionEn = "Continuous angular rotation over time.",
            params = listOf(
                EffectParamSpec("speed", "Putaran/dtk", "Deg/sec", -720f, 720f, 90f, 5f, "°/s")
            )
        ),
        EffectSpec(
            id = "wave_warp",
            nameId = "Wave Warp",
            nameEn = "Wave Warp",
            category = "Distorsi",
            descriptionId = "Distorsi gelombang dinamis pada permukaan layer.",
            descriptionEn = "Dynamic wave distortion across the layer.",
            params = listOf(
                EffectParamSpec("amplitude", "Amplitudo", "Amplitude", 0f, 60f, 16f, 0.5f, "px"),
                EffectParamSpec("frequency", "Frekuensi", "Frequency", 0.5f, 12f, 3f, 0.25f, ""),
                EffectParamSpec("speed", "Kecepatan", "Speed", 0f, 6f, 2f, 0.1f, "x")
            )
        ),
        EffectSpec(
            id = "particles",
            nameId = "Partikel (Particular)",
            nameEn = "Particle Emitter (Particular)",
            category = "Simulasi",
            descriptionId = "Preset 0:Debu 1:Hujan 2:Salju 3:Api 4:Kembang Api 5:Bokeh 6:Confetti.",
            descriptionEn = "Presets 0:Dust 1:Rain 2:Snow 3:Fire 4:Fireworks 5:Bokeh 6:Confetti.",
            featured = true,
            params = listOf(
                EffectParamSpec("preset", "Preset (0-6)", "Preset (0-6)", 0f, 6f, 0f, 1f, ""),
                EffectParamSpec("count", "Jumlah", "Count", 5f, 160f, 42f, 1f, ""),
                EffectParamSpec("speed", "Kecepatan", "Speed", 0.1f, 5f, 1.4f, 0.1f, "x"),
                EffectParamSpec("size", "Ukuran", "Size", 2f, 36f, 9f, 0.5f, "px")
            )
        ),
        EffectSpec(
            id = "audio_spectrum",
            nameId = "Spektrum Audio (FFT Real)",
            nameEn = "Real Audio Spectrum (FFT)",
            category = "Simulasi",
            descriptionId = "Visualizer frekuensi audio nyata dari trek audio/video proyek.",
            descriptionEn = "Real FFT frequency visualizer reactive to project audio.",
            featured = true,
            params = listOf(
                EffectParamSpec("bars", "Jumlah Batang", "Bar Count", 8f, 64f, 28f, 1f, ""),
                EffectParamSpec("height", "Tinggi Maks", "Max Height", 20f, 360f, 160f, 2f, "px"),
                EffectParamSpec("speed", "Sensitivitas", "Gain / Sensitivity", 0.2f, 5f, 1.8f, 0.1f, "x")
            )
        ),
        EffectSpec(
            id = "drop_shadow",
            nameId = "Bayangan Jatuh",
            nameEn = "Drop Shadow",
            category = "Stilisasi",
            descriptionId = "Tambahkan bayangan lembut di belakang objek.",
            descriptionEn = "Add soft shadow behind the layer.",
            params = listOf(
                EffectParamSpec("offsetX", "Geser X", "Offset X", -80f, 80f, 12f, 1f, "px"),
                EffectParamSpec("offsetY", "Geser Y", "Offset Y", -80f, 80f, 18f, 1f, "px"),
                EffectParamSpec("blur", "Kelembutan", "Softness", 0f, 60f, 20f, 1f, "px")
            )
        )
    )

    fun byId(id: String): EffectSpec? = all.find { it.id == id }

    fun createInstance(spec: EffectSpec): EffectInstance {
        return EffectInstance(
            specId = spec.id,
            name = spec.nameId,
            category = spec.category,
            enabled = true,
            params = spec.params.associate { it.id to it.defaultValue }
        )
    }
}
