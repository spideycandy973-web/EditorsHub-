package com.editorshub

import com.editorshub.core.domain.model.*
import com.editorshub.core.rendering.RenderGraph
import org.junit.Assert.*
import org.junit.Test

class RenderGraphTest {
    private fun project() = Project(
        id = "p", name = "P", width = 1080, height = 1920, fps = 30, durationMs = 5000,
        layers = listOf(
            Layer(id = "top", name = "Top", type = LayerType.SHAPE, startMs = 0, durationMs = 5000,
                transform = Transform(opacity = 0.5f)),
            Layer(id = "group", name = "Group", type = LayerType.GROUP, startMs = 0, durationMs = 5000,
                transform = Transform(x = 100f)),
            Layer(id = "hidden", name = "Hidden", type = LayerType.SHAPE, visible = false,
                startMs = 0, durationMs = 5000)
        )
    )

    @Test fun resolvesOnlyVisibleActiveLayers() {
        val frame = RenderGraph.resolve(project(), 1000L)
        assertEquals(2, frame.layers.size)
        assertFalse(frame.layers.any { it.layer.id == "hidden" })
    }

    @Test fun opacityUsesStaticTransform() {
        val frame = RenderGraph.resolve(project(), 1000L)
        val top = frame.layers.first { it.layer.id == "top" }
        assertEquals(0.5f, RenderGraph.effectiveOpacity(frame, top), 0.001f)
    }

    @Test fun keyframesOverrideTransform() {
        val p = project().copy(layers = listOf(
            Layer(id = "x", name = "X", type = LayerType.SHAPE,
                transform = Transform(x = 10f),
                keyframes = listOf(KeyframeTrack("x", listOf(
                    Keyframe(timeMs = 0, value = 0f, easing = Easing.LINEAR),
                    Keyframe(timeMs = 1000, value = 200f, easing = Easing.LINEAR)
                )))
            )
        ))
        val frame = RenderGraph.resolve(p, 500L)
        assertEquals(100f, frame.layers[0].transform.x, 0.001f)
    }

    @Test fun parentMatrixComposes() {
        val p = project().copy(layers = listOf(
            Layer(id = "parent", name = "P", type = LayerType.GROUP,
                transform = Transform(x = 100f)),
            Layer(id = "child", name = "C", type = LayerType.SHAPE, parentId = "parent",
                transform = Transform(x = 50f))
        ))
        val frame = RenderGraph.resolve(p, 0L)
        val child = frame.layers.first { it.layer.id == "child" }
        // centre(540) + parent(100) + child(50) = 690
        assertEquals(690f, child.transform.matrix.e, 0.001f)
    }

    @Test fun transitionFadesIn() {
        val p = project().copy(layers = listOf(
            Layer(id = "l", name = "L", type = LayerType.SHAPE,
                transitionIn = TransitionSpec(TransitionType.FADE_IN, 1000L))
        ))
        val start = RenderGraph.resolve(p, 0L).layers[0].transition
        val mid = RenderGraph.resolve(p, 500L).layers[0].transition
        val end = RenderGraph.resolve(p, 1000L).layers[0].transition
        assertEquals(0f, start.opacity, 0.001f)
        assertEquals(0.5f, mid.opacity, 0.001f)
        assertEquals(1f, end.opacity, 0.001f)
    }
}
