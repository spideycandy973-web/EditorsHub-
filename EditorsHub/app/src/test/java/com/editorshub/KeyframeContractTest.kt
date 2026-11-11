package com.editorshub

import com.editorshub.core.domain.effects.EffectCatalog
import com.editorshub.core.domain.keyframe.KeyframeGraphMath
import com.editorshub.core.domain.model.Effect
import com.editorshub.core.domain.model.EffectType
import com.editorshub.core.domain.model.Keyframe
import com.editorshub.core.domain.model.KeyframeTrack
import com.editorshub.core.domain.model.Layer
import com.editorshub.core.domain.model.LayerType
import com.editorshub.core.rendering.RenderGraph
import org.junit.Assert.assertEquals
import org.junit.Test

/** Regression contract for the four keyframe paths used by the redesigned editor. */
class KeyframeContractTest {

    @Test
    fun transformDiamondTrackChangesTheRenderedTransform() {
        val layer = Layer(
            id = "animated", name = "Animated", type = LayerType.SHAPE,
            durationMs = 2000L,
            keyframes = listOf(
                KeyframeTrack("x", listOf(
                    Keyframe(timeMs = 0L, value = 0f),
                    Keyframe(timeMs = 1000L, value = 200f)
                ))
            )
        )
        assertEquals(100f, RenderGraph.resolvedTransform(layer, 500L).x, 0.001f)
        assertEquals(200f, RenderGraph.resolvedTransform(layer, 1500L).x, 0.001f)
    }

    @Test
    fun effectParameterDiamondTrackChangesTheResolvedEffect() {
        val effect = Effect(
            id = "blur", type = EffectType.GAUSSIAN_BLUR,
            params = mapOf("amount" to 0f),
            animated = mapOf(
                "amount" to KeyframeTrack("amount", listOf(
                    Keyframe(timeMs = 0L, value = 0f),
                    Keyframe(timeMs = 1000L, value = 100f)
                ))
            )
        )
        assertEquals(50f, EffectCatalog.paramValue(effect, "amount", 500L), 0.001f)
        assertEquals(100f, EffectCatalog.resolvedParams(effect, 1500L)["amount"]!!, 0.001f)
    }

    @Test
    fun graphDragMovesTimeAndValueContinuouslyAndClampsToTimeline() {
        val moved = KeyframeGraphMath.dragged(
            origin = KeyframeGraphMath.Point(500L, 0.5f),
            horizontalRatio = 0.25f,
            verticalRatio = -0.1f,
            maxTimeMs = 1000f,
            minValue = 0f,
            maxValue = 1f
        )
        assertEquals(750L, moved.timeMs)
        assertEquals(0.6f, moved.value, 0.001f)

        val clamped = KeyframeGraphMath.dragged(
            origin = KeyframeGraphMath.Point(900L, 0.5f),
            horizontalRatio = 0.5f,
            verticalRatio = 0.5f,
            maxTimeMs = 1000f,
            minValue = 0f,
            maxValue = 1f
        )
        assertEquals(1000L, clamped.timeMs)
        assertEquals(0f, clamped.value, 0.001f)
    }
}
