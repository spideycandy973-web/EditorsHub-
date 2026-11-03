package com.editorshub.core.domain.transitions

import com.editorshub.core.domain.model.TransitionClip
import com.editorshub.core.domain.model.TransitionSpec
import com.editorshub.core.domain.model.TransitionType

/** Resolves transitions into concrete per-frame render state. Pure Kotlin. */
object TransitionResolver {

    data class State(
        val opacity: Float = 1f,
        val scale: Float = 1f,
        val dx: Float = 0f,
        val dy: Float = 0f,
        val rotation: Float = 0f,
        val blur: Float = 0f,
        val wipe: Float = 1f
    )

    val NEUTRAL = State()

    /** [progress] 0 = fully hidden, 1 = fully shown. */
    fun resolve(spec: TransitionSpec?, progress: Float): State {
        if (spec == null) return NEUTRAL
        val p = progress.coerceIn(0f, 1f)
        val params = spec.params
        return when (spec.type) {
            TransitionType.FADE_IN, TransitionType.FADE_OUT, TransitionType.CROSSFADE ->
                State(opacity = p)
            TransitionType.ZOOM ->
                State(opacity = p, scale = 0.35f + 0.65f * p)
            TransitionType.SLIDE ->
                State(opacity = 1f, dx = (1f - p) * (params["distance"] ?: 0.6f) * -1f, dy = (1f - p) * (params["distanceY"] ?: 0f))
            TransitionType.SPIN ->
                State(opacity = p, scale = 0.5f + 0.5f * p, rotation = (1f - p) * (params["turns"] ?: 1f) * 360f)
            TransitionType.BLUR ->
                State(opacity = p, blur = (1f - p) * (params["radius"] ?: 40f))
            TransitionType.WIPE ->
                State(opacity = 1f, wipe = p)
            TransitionType.CUSTOM ->
                State(opacity = p, scale = 0.8f + 0.2f * p, rotation = (1f - p) * (params["spin"] ?: 0.25f) * 360f)
        }
    }

    fun inProgress(spec: TransitionSpec?, localMs: Long): Float {
        if (spec == null || spec.durationMs <= 0L) return 1f
        return (localMs.toFloat() / spec.durationMs.toFloat()).coerceIn(0f, 1f)
    }

    fun outProgress(spec: TransitionSpec?, localMs: Long, durationMs: Long): Float {
        if (spec == null || spec.durationMs <= 0L) return 1f
        val remaining = durationMs - localMs
        return (remaining.toFloat() / spec.durationMs.toFloat()).coerceIn(0f, 1f)
    }

    /** Crossfade between two layers: returns (fromOpacity, toOpacity). */
    fun crossfade(clip: TransitionClip, fromStartMs: Long, fromDurationMs: Long, toStartMs: Long, timeMs: Long): Pair<Float, Float> {
        val overlapStart = maxOf(fromStartMs, toStartMs)
        val overlapEnd = minOf(fromStartMs + fromDurationMs, toStartMs + fromDurationMs)
        if (overlapEnd <= overlapStart || clip.durationMs <= 0L) return 1f to 1f
        val center = (overlapStart + overlapEnd) / 2.0
        val half = clip.durationMs / 2.0
        val t = ((timeMs - center + half) / (2.0 * half)).coerceIn(0.0, 1.0).toFloat()
        return (1f - t) to t
    }
}
