package com.editorshub.core.domain.timeline

import com.editorshub.core.domain.keyframe.KeyframeEngine
import com.editorshub.core.domain.model.Easing
import com.editorshub.core.domain.model.Keyframe
import com.editorshub.core.domain.model.KeyframeTrack
import com.editorshub.core.domain.model.Layer
import kotlin.math.abs
import kotlin.math.roundToLong

/** Pure timeline geometry and clip operations (no Android types -> JVM unit tests). */
object TimelineMath {

    fun msToX(ms: Long, pxPerSecond: Float): Float = ms / 1000f * pxPerSecond
    fun xToMs(x: Float, pxPerSecond: Float): Long = (x / pxPerSecond * 1000f).roundToLong()

    fun snap(ms: Long, fps: Int, enabled: Boolean): Long {
        if (!enabled || fps <= 0) return ms
        val frame = 1000.0 / fps
        return ((ms / frame).roundToLong() * frame).roundToLong()
    }

    fun frameCount(durationMs: Long, fps: Int): Int =
        frameCount(durationMs, fps.toDouble())

    fun frameCount(durationMs: Long, fpsExact: Double): Int =
        ((durationMs * fpsExact.coerceIn(1.0, 240.0) / 1000.0).toInt()).coerceAtLeast(1)

    fun frameTime(index: Int, fps: Int): Long = frameTimeExact(index, fps.toDouble())

    fun frameTimeExact(index: Int, fpsExact: Double): Long =
        (index * 1000.0 / fpsExact.coerceIn(1.0, 240.0)).roundToLong()

    /** Snap to the exact rational frame rate (23.976 / 29.97 supported). */
    fun snapExact(ms: Long, fpsExact: Double, enabled: Boolean): Long {
        if (!enabled) return ms
        val frame = 1000.0 / fpsExact.coerceIn(1.0, 240.0)
        return ((ms / frame).roundToLong() * frame).roundToLong()
    }

    fun active(layer: Layer, timeMs: Long): Boolean =
        timeMs >= layer.startMs && timeMs < layer.startMs + layer.durationMs

    fun move(layer: Layer, deltaMs: Long, fps: Int, snapping: Boolean = true): Layer =
        move(layer, deltaMs, fps.toDouble(), snapping)

    fun move(layer: Layer, deltaMs: Long, fpsExact: Double, snapping: Boolean = true): Layer {
        val next = snapExact(layer.startMs + deltaMs, fpsExact, snapping).coerceAtLeast(0)
        return layer.copy(startMs = next)
    }

    fun resizeEnd(layer: Layer, deltaMs: Long, fps: Int, snapping: Boolean = true): Layer =
        resizeEnd(layer, deltaMs, fps.toDouble(), snapping)

    fun resizeEnd(layer: Layer, deltaMs: Long, fpsExact: Double, snapping: Boolean = true): Layer {
        val next = snapExact(layer.durationMs + deltaMs, fpsExact, snapping).coerceAtLeast(33L)
        return layer.copy(durationMs = next)
    }

    /** Rebase clip-local animation when the visible in-point changes. */
    fun trimStart(layer: Layer, deltaMs: Long, fps: Int, snapping: Boolean = true): Layer =
        trimStart(layer, deltaMs, fps.toDouble(), snapping)

    fun trimStart(layer: Layer, deltaMs: Long, fpsExact: Double, snapping: Boolean = true): Layer {
        val minDelta = if (layer.type == com.editorshub.core.domain.model.LayerType.VIDEO)
            maxOf(-layer.startMs, -layer.inPointMs) else -layer.startMs
        val d = snapExact(deltaMs, fpsExact, snapping).coerceIn(minDelta, layer.durationMs - 33L)
        if (d == 0L) return layer
        val effects = layer.effects.map { effect ->
            effect.copy(animated = effect.animated.mapValues { (_, track) -> rebaseTrack(track, d) })
        }
        val masks = layer.masks.map { mask ->
            mask.copy(keyframes = mask.keyframes.map { rebaseTrack(it, d) })
        }
        val transitionIn = layer.transitionIn?.let { transition ->
            if (d <= 0L) transition else {
                val remaining = transition.durationMs - d
                if (remaining > 0L) transition.copy(durationMs = remaining) else null
            }
        }
        return layer.copy(
            startMs = layer.startMs + d,
            durationMs = layer.durationMs - d,
            inPointMs = (layer.inPointMs + d).coerceAtLeast(0L),
            keyframes = layer.keyframes.map { rebaseTrack(it, d) },
            effects = effects,
            masks = masks,
            transitionIn = transitionIn
        )
    }

    fun split(layer: Layer, atMs: Long, fps: Int, newId: String): Pair<Layer, Layer>? =
        split(layer, atMs, fps.toDouble(), newId)

    fun split(layer: Layer, atMs: Long, fpsExact: Double, newId: String): Pair<Layer, Layer>? {
        if (atMs <= layer.startMs || atMs >= layer.startMs + layer.durationMs) return null
        val cut = snapExact(atMs, fpsExact, true).coerceIn(layer.startMs + 1L, layer.startMs + layer.durationMs - 1L)
        val localCut = cut - layer.startMs
        val layerTracks = layer.keyframes.map { splitTrack(it, localCut) }
        val effectTracks = layer.effects.map { effect ->
            val left = effect.animated.mapValues { (_, track) -> splitTrack(track, localCut).first }
            val right = effect.animated.mapValues { (_, track) -> splitTrack(track, localCut).second }
            effect.copy(animated = left) to effect.copy(animated = right)
        }
        val maskTracks = layer.masks.map { mask ->
            val pairs = mask.keyframes.map { splitTrack(it, localCut) }
            mask.copy(keyframes = pairs.map { it.first }) to mask.copy(keyframes = pairs.map { it.second })
        }
        val first = layer.copy(
            durationMs = localCut,
            keyframes = layerTracks.map { it.first },
            effects = effectTracks.map { it.first },
            masks = maskTracks.map { it.first },
            transitionOut = null
        )
        val second = layer.copy(
            id = newId,
            name = "${layer.name} (2)",
            startMs = cut,
            durationMs = layer.durationMs - localCut,
            inPointMs = layer.inPointMs + localCut,
            keyframes = layerTracks.map { it.second },
            effects = effectTracks.map { it.second },
            masks = maskTracks.map { it.second },
            transitionIn = null
        )
        return first to second
    }

    /** Insert an evaluated boundary keyframe so neither side jumps when a clip is split. */
    private fun splitTrack(track: KeyframeTrack, cutMs: Long): Pair<KeyframeTrack, KeyframeTrack> {
        val keys = track.keyframes.sortedBy { it.timeMs }
        if (keys.isEmpty()) return track to track
        val boundary = boundaryKeyframe(track, cutMs)
        val left = (keys.filter { it.timeMs < cutMs } + boundary).distinctBy { it.timeMs }.sortedBy { it.timeMs }
        val right = (listOf(boundary.copy(timeMs = 0L, id = "${boundary.id}_right")) +
            keys.filter { it.timeMs > cutMs }.map { it.copy(timeMs = it.timeMs - cutMs) })
            .distinctBy { it.timeMs }.sortedBy { it.timeMs }
        return track.copy(keyframes = left) to track.copy(keyframes = right)
    }

    /** Shift keyframe clocks with the new clip head; positive trims preserve the value at t=0. */
    private fun rebaseTrack(track: KeyframeTrack, deltaMs: Long): KeyframeTrack {
        if (track.keyframes.isEmpty()) return track
        if (deltaMs <= 0L) return track.copy(
            keyframes = track.keyframes.map { it.copy(timeMs = it.timeMs - deltaMs) }.sortedBy { it.timeMs }
        )
        val boundary = boundaryKeyframe(track, deltaMs).copy(
            timeMs = 0L, id = "${track.keyframes.first().id}_trim_$deltaMs"
        )
        val shifted = track.keyframes.filter { it.timeMs > deltaMs }
            .map { it.copy(timeMs = it.timeMs - deltaMs) }
        return track.copy(keyframes = (listOf(boundary) + shifted).distinctBy { it.timeMs }.sortedBy { it.timeMs })
    }

    private fun boundaryKeyframe(track: KeyframeTrack, timeMs: Long): Keyframe {
        val exact = track.keyframes.firstOrNull { kotlin.math.abs(it.timeMs - timeMs) <= 1L }
        if (exact != null) return exact.copy(timeMs = timeMs)
        return if (track.keyframes.any { it.color != null }) {
            val color = KeyframeEngine.color(track, timeMs, 0xFFFFFFFF.toInt())
            Keyframe(
                id = "${track.keyframes.firstOrNull()?.id ?: track.property}_boundary_$timeMs",
                timeMs = timeMs,
                color = String.format("#%08X", color),
                easing = Easing.LINEAR
            )
        } else {
            Keyframe(
                id = "${track.keyframes.firstOrNull()?.id ?: track.property}_boundary_$timeMs",
                timeMs = timeMs,
                value = KeyframeEngine.value(track, timeMs, 0f),
                easing = Easing.LINEAR
            )
        }
    }

    fun overlaps(a: Layer, b: Layer): Boolean =
        a.startMs < b.startMs + b.durationMs && b.startMs < a.startMs + a.durationMs

    fun reordered(layers: List<Layer>, from: Int, to: Int): List<Layer> {
        if (from !in layers.indices || to !in layers.indices || from == to) return layers
        val out = layers.toMutableList()
        val item = out.removeAt(from)
        out.add(to.coerceIn(0, out.size), item)
        return out
    }

    fun nearestKeyframe(times: List<Long>, timeMs: Long, direction: Int): Long? =
        if (direction > 0) times.filter { it > timeMs + 1 }.minOrNull()
        else times.filter { it < timeMs - 1 }.maxOrNull()

    fun distance(a: Long, b: Long): Long = abs(a - b)
}
