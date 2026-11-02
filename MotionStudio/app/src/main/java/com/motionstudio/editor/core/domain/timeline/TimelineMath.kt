package com.motionstudio.editor.core.domain.timeline

import com.motionstudio.editor.core.domain.model.Layer
import kotlin.math.abs
import kotlin.math.roundToLong

/** Pure timeline geometry and clip operations (no Android types -> JVM unit tests). */
object TimelineMath {

    fun msToX(ms: Long, pxPerSecond: Float): Float = ms / 1000f * pxPerSecond
    fun xToMs(x: Float, pxPerSecond: Float): Long = (x / pxPerSecond * 1000f).roundToLong()

    fun snap(ms: Long, fps: Int, enabled: Boolean): Long {
        if (!enabled || fps <= 0) return ms
        val frame = 1000.0 / fps
        return ((ms / frame).toLong() * frame).toLong()
    }

    fun frameCount(durationMs: Long, fps: Int): Int =
        ((durationMs.toDouble() * fps / 1000.0).toInt()).coerceAtLeast(1)

    fun frameTime(index: Int, fps: Int): Long = (index.toDouble() * 1000.0 / fps).toLong()

    fun active(layer: Layer, timeMs: Long): Boolean =
        timeMs >= layer.startMs && timeMs < layer.startMs + layer.durationMs

    fun move(layer: Layer, deltaMs: Long, fps: Int, snapping: Boolean = true): Layer {
        val next = snap(layer.startMs + deltaMs, fps, snapping).coerceAtLeast(0)
        return layer.copy(startMs = next)
    }

    fun resizeEnd(layer: Layer, deltaMs: Long, fps: Int, snapping: Boolean = true): Layer {
        val next = snap(layer.durationMs + deltaMs, fps, snapping).coerceAtLeast(33L)
        return layer.copy(durationMs = next)
    }

    fun trimStart(layer: Layer, deltaMs: Long, fps: Int, snapping: Boolean = true): Layer {
        val d = snap(deltaMs, fps, snapping).coerceIn(-layer.startMs, layer.durationMs - 33L)
        return layer.copy(
            startMs = layer.startMs + d,
            durationMs = layer.durationMs - d,
            inPointMs = (layer.inPointMs + d).coerceAtLeast(0)
        )
    }

    fun split(layer: Layer, atMs: Long, fps: Int, newId: String): Pair<Layer, Layer>? {
        if (atMs <= layer.startMs || atMs >= layer.startMs + layer.durationMs) return null
        val cut = snap(atMs, fps, true)
        val first = layer.copy(durationMs = cut - layer.startMs)
        val second = layer.copy(
            id = newId,
            name = "${layer.name} (2)",
            startMs = cut,
            durationMs = layer.startMs + layer.durationMs - cut,
            inPointMs = layer.inPointMs + (cut - layer.startMs),
            keyframes = layer.keyframes.map { track ->
                val shifted = track.keyframes.filter { it.timeMs >= cut - layer.startMs }
                    .map { it.copy(timeMs = it.timeMs - (cut - layer.startMs)) }
                track.copy(keyframes = shifted)
            }
        )
        return first to second
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
