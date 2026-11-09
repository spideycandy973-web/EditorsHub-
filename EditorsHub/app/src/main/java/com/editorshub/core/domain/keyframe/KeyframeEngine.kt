package com.editorshub.core.domain.keyframe

import com.editorshub.core.domain.model.ColorUtil
import com.editorshub.core.domain.model.Easing
import com.editorshub.core.domain.model.Keyframe
import com.editorshub.core.domain.model.KeyframeTrack
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

/**
 * The animation engine. All interpolation is pure Kotlin so it can be unit tested on the JVM
 * and reused by both the preview compositor and the export encoder.
 */
object KeyframeEngine {

    fun sorted(track: KeyframeTrack?): List<Keyframe> =
        track?.keyframes?.sortedBy { it.timeMs } ?: emptyList()

    /** Numeric value of a track at [timeMs], or [fallback] when the track is empty. */
    fun value(track: KeyframeTrack?, timeMs: Long, fallback: Float): Float {
        val kfs = sorted(track)
        if (kfs.isEmpty()) return fallback
        if (timeMs <= kfs.first().timeMs) return kfs.first().value
        if (timeMs >= kfs.last().timeMs) return kfs.last().value
        return interpolate(kfs, timeMs) { a, b, t -> a.value + (b.value - a.value) * t }
    }

    /** Color value of a track at [timeMs] (linear ARGB interpolation). */
    fun color(track: KeyframeTrack?, timeMs: Long, fallback: Int): Int {
        val kfs = sorted(track)
        if (kfs.isEmpty()) return fallback
        val first = ColorUtil.parse(kfs.first().color ?: "", fallback)
        val last = ColorUtil.parse(kfs.last().color ?: "", fallback)
        if (timeMs <= kfs.first().timeMs) return first
        if (timeMs >= kfs.last().timeMs) return last
        return interpolate(kfs, timeMs) { a, b, t ->
            val ca = ColorUtil.parse(a.color ?: "", fallback)
            val cb = ColorUtil.parse(b.color ?: "", fallback)
            ColorUtil.lerp(ca, cb, t).toFloat()
        }.toInt()
    }

    private inline fun <T> interpolate(
        kfs: List<Keyframe>,
        timeMs: Long,
        crossinline mix: (Keyframe, Keyframe, Float) -> T
    ): T {
        var i = 0
        while (i < kfs.size - 2 && kfs[i + 1].timeMs <= timeMs) i++
        val a = kfs[i]
        val b = kfs[i + 1]
        if (a.hold || a.easing == Easing.HOLD) return mix(a, b, 0f)
        val span = (b.timeMs - a.timeMs).toFloat()
        if (span <= 0f) return mix(a, b, 1f)
        val linearT = (timeMs - a.timeMs) / span
        val eased = ease(linearT, a.easing, a)
        return mix(a, b, eased)
    }

    fun ease(t: Float, easing: Easing, k: Keyframe = Keyframe()): Float {
        val x = t.coerceIn(0f, 1f)
        return when (easing) {
            Easing.LINEAR -> x
            Easing.HOLD -> 0f
            Easing.EASE_IN -> x * x * x
            Easing.EASE_OUT -> 1f - (1f - x).pow(3)
            Easing.EASE_IN_OUT -> if (x < 0.5f) 4f * x * x * x
                else 1f - (-2f * x + 2f).pow(3) / 2f
            Easing.BEZIER -> cubicBezier(x, k.cp1x, k.cp1y, k.cp2x, k.cp2y)
            Easing.ELASTIC -> {
                if (x == 0f || x == 1f) x
                else (2f.pow(-10f * x) * sin((x * 10f - 0.75f) * (2f * Math.PI.toFloat() / 3f)) + 1f).toFloat()
            }
            Easing.BOUNCE -> bounce(x)
        }
    }

    private fun bounce(x: Float): Float {
        val n1 = 7.5625f
        val d1 = 2.75f
        return when {
            x < 1f / d1 -> n1 * x * x
            x < 2f / d1 -> { val y = x - 1.5f / d1; n1 * y * y + 0.75f }
            x < 2.5f / d1 -> { val y = x - 2.25f / d1; n1 * y * y + 0.9375f }
            else -> { val y = x - 2.625f / d1; n1 * y * y + 0.984375f }
        }
    }

    /** Solve a CSS-style cubic-bezier(x1,y1,x2,y2) for the y value at a given x. */
    fun cubicBezier(x: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
        if (x <= 0f) return 0f
        if (x >= 1f) return 1f
        var t = x
        repeat(8) {
            val current = bezierComponent(t, x1, x2) - x
            val derivative = bezierDerivative(t, x1, x2)
            if (abs(current) < 1e-5f || abs(derivative) < 1e-6f) return@repeat
            t -= current / derivative
            t = t.coerceIn(0f, 1f)
        }
        return bezierComponent(t.coerceIn(0f, 1f), y1, y2)
    }

    private fun bezierComponent(t: Float, p1: Float, p2: Float): Float {
        val u = 1f - t
        return 3f * u * u * t * p1 + 3f * u * t * t * p2 + t * t * t
    }

    private fun bezierDerivative(t: Float, p1: Float, p2: Float): Float {
        val u = 1f - t
        return 3f * u * u * p1 + 6f * u * t * (p2 - p1) + 3f * t * t * (1f - p2)
    }

    fun trackValue(tracks: List<KeyframeTrack>, property: String, timeMs: Long, fallback: Float): Float =
        value(tracks.firstOrNull { it.property == property }, timeMs, fallback)

    fun trackColor(tracks: List<KeyframeTrack>, property: String, timeMs: Long, fallback: Int): Int =
        color(tracks.firstOrNull { it.property == property }, timeMs, fallback)

    /** Nearest existing point within a frame tolerance, used by diamond toggles. */
    fun keyframeAt(track: KeyframeTrack?, timeMs: Long, toleranceMs: Long): Keyframe? =
        sorted(track).firstOrNull { abs(it.timeMs - timeMs) <= toleranceMs.coerceAtLeast(0L) }

    fun upsert(track: KeyframeTrack, timeMs: Long, value: Float, easing: Easing): KeyframeTrack {
        val existing = track.keyframes.firstOrNull { kotlin.math.abs(it.timeMs - timeMs) < 2L }
        val kfs = track.keyframes.toMutableList()
        if (existing != null) {
            val idx = kfs.indexOf(existing)
            kfs[idx] = existing.copy(value = value, easing = easing)
        } else {
            kfs.add(Keyframe(id = "kf_${timeMs}_${kfs.size}", timeMs = timeMs, value = value, easing = easing))
        }
        return track.copy(keyframes = kfs.sortedBy { it.timeMs })
    }

    /** Color tracks carry an ARGB hex string instead of a numeric value. */
    fun upsertColor(track: KeyframeTrack, timeMs: Long, color: String, easing: Easing): KeyframeTrack {
        val existing = keyframeAt(track, timeMs, 2L)
        val kfs = track.keyframes.toMutableList()
        if (existing != null) {
            val idx = kfs.indexOf(existing)
            kfs[idx] = existing.copy(value = 0f, color = color, easing = easing)
        } else {
            kfs.add(Keyframe(
                id = "kf_color_${timeMs}_${kfs.size}",
                timeMs = timeMs,
                value = 0f,
                color = color,
                easing = easing
            ))
        }
        return track.copy(keyframes = kfs.sortedBy { it.timeMs })
    }

    fun remove(track: KeyframeTrack, keyframeId: String): KeyframeTrack =
        track.copy(keyframes = track.keyframes.filterNot { it.id == keyframeId })

    /** Damped spring used by elastic previews; exposed for tests and motion presets. */
    fun spring(x: Float): Float =
        if (x == 0f || x == 1f) x else (1f - cos(x * 5f * Math.PI.toFloat()) * exp(-5f * x)).toFloat()
}
