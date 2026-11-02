package com.motionstudio.editor

import com.motionstudio.editor.core.domain.model.Layer
import com.motionstudio.editor.core.domain.model.LayerType
import com.motionstudio.editor.core.domain.timeline.TimelineMath
import org.junit.Assert.*
import org.junit.Test

class TimelineMathTest {
    private val layer = Layer(id = "a", name = "A", type = LayerType.SHAPE,
        startMs = 1000, durationMs = 4000, inPointMs = 500)

    @Test fun msToXRoundTrip() {
        assertEquals(140f, TimelineMath.msToX(2000L, 70f), 0.001f)
        assertEquals(2000L, TimelineMath.xToMs(140f, 70f))
    }

    @Test fun snappingToFrames() {
        assertEquals(1000L, TimelineMath.snap(1009L, 30, true))
        assertEquals(1009L, TimelineMath.snap(1009L, 30, false))
    }

    @Test fun splitProducesTwoContiguousLayers() {
        val (a, b) = TimelineMath.split(layer, 3000L, 30, "b")!!
        assertEquals(1000L, a.startMs)
        assertEquals(2000L, a.durationMs)
        assertEquals(3000L, b.startMs)
        assertEquals(2000L, b.durationMs)
        assertEquals(2500L, b.inPointMs)
        assertEquals(a.startMs + a.durationMs, b.startMs)
    }

    @Test fun splitOutsideReturnsNull() {
        assertNull(TimelineMath.split(layer, 0L, 30, "b"))
        assertNull(TimelineMath.split(layer, 9000L, 30, "b"))
    }

    @Test fun trimStartKeepsEnd() {
        val t = TimelineMath.trimStart(layer, 1000L, 30, false)
        assertEquals(2000L, t.startMs)
        assertEquals(3000L, t.durationMs)
        assertEquals(5000L, t.startMs + t.durationMs)
    }

    @Test fun frameCountAndReorder() {
        assertEquals(300, TimelineMath.frameCount(10_000L, 30))
        assertEquals(0L, TimelineMath.frameTime(0, 30))
        val list = listOf(layer, layer.copy(id = "b"), layer.copy(id = "c"))
        val reordered = TimelineMath.reordered(list, 2, 0)
        assertEquals("c", reordered[0].id)
    }

    @Test fun overlapDetection() {
        assertTrue(TimelineMath.overlaps(layer, layer.copy(startMs = 2000)))
        assertFalse(TimelineMath.overlaps(layer, layer.copy(startMs = 9000)))
    }
}
