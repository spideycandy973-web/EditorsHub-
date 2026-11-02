package com.motionstudio.editor

import com.motionstudio.editor.core.domain.model.FrameRates
import com.motionstudio.editor.core.domain.timeline.TimelineMath
import org.junit.Assert.*
import org.junit.Test

class FrameRateTest {
    @Test fun fractionalRatesArePreserved() {
        assertEquals(23.976, FrameRates.ALL.first { it.label.startsWith("23.976") }.exact, 0.0001)
        assertEquals(29.97, FrameRates.ALL.first { it.label.startsWith("29.97") }.exact, 0.0001)
        assertEquals(59.94, FrameRates.ALL.first { it.label.startsWith("59.94") }.exact, 0.0001)
    }

    @Test fun exactFrameCount() {
        assertEquals(2997, TimelineMath.frameCount(100_000L, 29.97))
        assertEquals(300, TimelineMath.frameCount(10_000L, 30.0))
    }

    @Test fun exactFrameTime() {
        assertEquals(0L, TimelineMath.frameTimeExact(0, 29.97))
        // frame 30 at 29.97fps is at ~1001ms
        assertTrue(TimelineMath.frameTimeExact(30, 29.97) in 999L..1003L)
    }

    @Test fun exactSnapping() {
        val snapped = TimelineMath.snapExact(40L, 29.97, true)
        assertEquals(33L, snapped)
        assertEquals(40L, TimelineMath.snapExact(40L, 29.97, false))
    }
}
