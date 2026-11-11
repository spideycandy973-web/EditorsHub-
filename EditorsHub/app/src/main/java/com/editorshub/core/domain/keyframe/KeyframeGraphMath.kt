package com.editorshub.core.domain.keyframe

/**
 * Pure mapping used by the graph editor's drag gesture. Keeping it out of the
 * composable makes continuous keyframe dragging unit-testable on the JVM.
 */
object KeyframeGraphMath {
    data class Point(val timeMs: Long, val value: Float)

    fun dragged(
        origin: Point,
        horizontalRatio: Float,
        verticalRatio: Float,
        maxTimeMs: Float,
        minValue: Float,
        maxValue: Float
    ): Point {
        val limit = maxTimeMs.toLong().coerceAtLeast(1L)
        val timeMs = (origin.timeMs + horizontalRatio * maxTimeMs)
            .toLong().coerceIn(0L, limit)
        val span = (maxValue - minValue).coerceAtLeast(0.0001f)
        return Point(timeMs, origin.value - verticalRatio * span)
    }
}
