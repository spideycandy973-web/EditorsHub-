package com.editorshub

import com.editorshub.core.domain.keyframe.KeyframeEngine
import com.editorshub.core.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class KeyframeInterpolationTest {

    private fun track(vararg kfs: Keyframe) = KeyframeTrack("x", kfs.toList())

    @Test fun emptyTrackReturnsFallback() {
        assertEquals(7f, KeyframeEngine.value(null, 500L, 7f), 0.001f)
        assertEquals(7f, KeyframeEngine.value(track(), 500L, 7f), 0.001f)
    }

    @Test fun linearMidpoint() {
        val t = track(
            Keyframe(timeMs = 0, value = 0f, easing = Easing.LINEAR),
            Keyframe(timeMs = 1000, value = 100f, easing = Easing.LINEAR)
        )
        assertEquals(50f, KeyframeEngine.value(t, 500L, -1f), 0.001f)
        assertEquals(25f, KeyframeEngine.value(t, 250L, -1f), 0.001f)
    }

    @Test fun clampsOutsideRange() {
        val t = track(
            Keyframe(timeMs = 100, value = 10f),
            Keyframe(timeMs = 900, value = 90f)
        )
        assertEquals(10f, KeyframeEngine.value(t, 0L, -1f), 0.001f)
        assertEquals(90f, KeyframeEngine.value(t, 5000L, -1f), 0.001f)
    }

    @Test fun holdKeepsPreviousValue() {
        val t = track(
            Keyframe(timeMs = 0, value = 10f, easing = Easing.HOLD),
            Keyframe(timeMs = 1000, value = 90f, easing = Easing.LINEAR)
        )
        assertEquals(10f, KeyframeEngine.value(t, 999L, -1f), 0.001f)
    }

    @Test fun easeInOutIsSymmetric() {
        assertEquals(0.5f, KeyframeEngine.ease(0.5f, Easing.EASE_IN_OUT), 0.001f)
        assertTrue(KeyframeEngine.ease(0.25f, Easing.EASE_IN_OUT) < 0.25f)
        assertTrue(KeyframeEngine.ease(0.75f, Easing.EASE_IN_OUT) > 0.75f)
    }

    @Test fun cubicBezierMatchesKnownValues() {
        assertEquals(0.5f, KeyframeEngine.cubicBezier(0.5f, 0.25f, 0.25f, 0.75f, 0.75f), 0.02f)
        assertEquals(0f, KeyframeEngine.cubicBezier(0f, 0.42f, 0f, 0.58f, 1f), 0.001f)
        assertEquals(1f, KeyframeEngine.cubicBezier(1f, 0.42f, 0f, 0.58f, 1f), 0.001f)
    }

    @Test fun multipleKeyframesUseCorrectSegment() {
        val t = track(
            Keyframe(timeMs = 0, value = 0f, easing = Easing.LINEAR),
            Keyframe(timeMs = 1000, value = 100f, easing = Easing.LINEAR),
            Keyframe(timeMs = 2000, value = 0f, easing = Easing.LINEAR)
        )
        assertEquals(50f, KeyframeEngine.value(t, 500L, -1f), 0.001f)
        assertEquals(50f, KeyframeEngine.value(t, 1500L, -1f), 0.001f)
    }

    @Test fun colorInterpolation() {
        val t = KeyframeTrack("color", listOf(
            Keyframe(timeMs = 0, color = "#FF000000"),
            Keyframe(timeMs = 1000, color = "#FFFFFFFF")
        ))
        val mid = KeyframeEngine.color(t, 500L, 0)
        val red = (mid ushr 16) and 0xFF
        val alpha = (mid ushr 24) and 0xFF
        assertTrue(kotlin.math.abs(red - 127) <= 2)
        assertEquals(255, alpha)
    }

    @Test fun colorKeyframesUpsertAndResolve() {
        var t = KeyframeEngine.upsertColor(
            KeyframeTrack("color"), 0L, "#FFFF0000", Easing.LINEAR)
        t = KeyframeEngine.upsertColor(t, 1000L, "#FF0000FF", Easing.LINEAR)
        assertEquals(2, t.keyframes.size)
        val start = KeyframeEngine.color(t, 0L, 0)
        val mid = KeyframeEngine.color(t, 500L, 0)
        assertEquals(255, (start ushr 24) and 0xFF)
        assertTrue(((mid ushr 16) and 0xFF) in 126..129)
        assertTrue((mid and 0xFF) in 126..129)
    }

    @Test fun keyframeAtHonorsFrameTolerance() {
        val t = track(
            Keyframe(id = "a", timeMs = 100L, value = 10f),
            Keyframe(id = "b", timeMs = 500L, value = 50f)
        )
        assertEquals("a", KeyframeEngine.keyframeAt(t, 116L, 20L)?.id)
        assertEquals("b", KeyframeEngine.keyframeAt(t, 490L, 20L)?.id)
        assertEquals(null, KeyframeEngine.keyframeAt(t, 250L, 20L))
    }

    @Test fun upsertAndRemove() {
        var t = KeyframeEngine.upsert(KeyframeTrack("x"), 100L, 5f, Easing.BEZIER)
        assertEquals(1, t.keyframes.size)
        t = KeyframeEngine.upsert(t, 100L, 9f, Easing.BEZIER)
        assertEquals(1, t.keyframes.size)
        assertEquals(9f, t.keyframes[0].value, 0.001f)
        t = KeyframeEngine.remove(t, t.keyframes[0].id)
        assertTrue(t.keyframes.isEmpty())
    }
}
