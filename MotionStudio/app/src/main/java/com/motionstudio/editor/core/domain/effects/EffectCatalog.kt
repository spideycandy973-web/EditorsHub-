package com.motionstudio.editor.core.domain.effects

import com.motionstudio.editor.core.domain.keyframe.KeyframeEngine
import com.motionstudio.editor.core.domain.model.Effect
import com.motionstudio.editor.core.domain.model.EffectType

data class ParamSpec(val key: String, val label: String, val min: Float, val max: Float, val default: Float)
data class EffectSpec(val type: EffectType, val label: String, val group: String, val params: List<ParamSpec>)

/** Single source of truth for effect parameters. Used by UI, renderer and GPU shaders. */
object EffectCatalog {

    private fun p(key: String, label: String, min: Float, max: Float, default: Float) =
        ParamSpec(key, label, min, max, default)

    val specs: List<EffectSpec> = listOf(
        EffectSpec(EffectType.GAUSSIAN_BLUR, "Gaussian Blur", "Blur", listOf(p("amount", "Radius", 0f, 100f, 12f))),
        EffectSpec(EffectType.MOTION_BLUR, "Motion Blur", "Blur", listOf(p("amount", "Length", 0f, 80f, 20f), p("angle", "Angle", 0f, 360f, 0f))),
        EffectSpec(EffectType.DIRECTIONAL_BLUR, "Directional Blur", "Blur", listOf(p("amount", "Length", 0f, 80f, 16f), p("angle", "Angle", 0f, 360f, 45f))),
        EffectSpec(EffectType.GLOW, "Glow", "Light", listOf(p("amount", "Radius", 0f, 120f, 32f), p("strength", "Intensity", 0f, 1f, 0.7f), p("hue", "Hue", 0f, 360f, 280f))),
        EffectSpec(EffectType.SHADOW, "Drop Shadow", "Light", listOf(p("amount", "Blur", 0f, 100f, 24f), p("offsetX", "Offset X", -200f, 200f, 12f), p("offsetY", "Offset Y", -200f, 200f, 18f), p("strength", "Opacity", 0f, 1f, 0.65f))),
        EffectSpec(EffectType.BRIGHTNESS, "Brightness", "Color", listOf(p("amount", "Brightness", -1f, 1f, 0f))),
        EffectSpec(EffectType.CONTRAST, "Contrast", "Color", listOf(p("amount", "Contrast", -1f, 2f, 0f))),
        EffectSpec(EffectType.SATURATION, "Saturation", "Color", listOf(p("amount", "Saturation", 0f, 3f, 1f))),
        EffectSpec(EffectType.HUE, "Hue", "Color", listOf(p("amount", "Hue shift", -180f, 180f, 0f))),
        EffectSpec(EffectType.EXPOSURE, "Exposure", "Color", listOf(p("amount", "Exposure", -2f, 2f, 0f))),
        EffectSpec(EffectType.VIGNETTE, "Vignette", "Color", listOf(p("amount", "Strength", 0f, 1f, 0.55f), p("strength", "Size", 0.1f, 1f, 0.65f))),
        EffectSpec(EffectType.SHARPEN, "Sharpen", "Color", listOf(p("amount", "Amount", 0f, 1f, 0.5f))),
        EffectSpec(EffectType.NOISE, "Film Grain", "Stylize", listOf(p("amount", "Amount", 0f, 1f, 0.25f))),
        EffectSpec(EffectType.RGB_SPLIT, "RGB Split", "Stylize", listOf(p("amount", "Offset", 0f, 60f, 12f))),
        EffectSpec(EffectType.CHROMATIC_ABERRATION, "Chromatic Aberration", "Stylize", listOf(p("amount", "Offset", 0f, 60f, 14f))),
        EffectSpec(EffectType.PIXELATION, "Pixelation", "Stylize", listOf(p("amount", "Block size", 2f, 120f, 16f))),
        EffectSpec(EffectType.WAVE_DISTORTION, "Wave Distortion", "Distort", listOf(p("amount", "Amplitude", 0f, 120f, 24f), p("strength", "Frequency", 1f, 30f, 6f))),
        EffectSpec(EffectType.COLOR_GRADING, "Color Grading", "Color", listOf(p("amount", "Temperature", -1f, 1f, 0f), p("strength", "Tint", -1f, 1f, 0f), p("hue", "Lift", 0f, 1f, 0f)))
    )

    private val byType = specs.associateBy { it.type }

    fun spec(type: EffectType): EffectSpec = byType.getValue(type)

    fun defaults(type: EffectType): Map<String, Float> =
        spec(type).params.associate { it.key to it.default }

    fun groups(): List<String> = specs.map { it.group }.distinct()

    /** Resolved parameter: animated keyframes win, then static params, then the default. */
    fun paramValue(effect: Effect, key: String, timeMs: Long): Float {
        val fallback = defaults(effect.type)[key] ?: 0f
        val track = effect.animated[key]
        return if (track != null) KeyframeEngine.value(track, timeMs, effect.params[key] ?: fallback)
        else effect.params[key] ?: fallback
    }

    fun resolvedParams(effect: Effect, timeMs: Long): Map<String, Float> =
        spec(effect.type).params.associate { it.key to paramValue(effect, it.key, timeMs) }
}
