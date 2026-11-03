package com.editorshub

import com.editorshub.core.domain.effects.EffectCatalog
import com.editorshub.core.domain.model.*
import com.editorshub.core.domain.transitions.TransitionResolver
import org.junit.Assert.*
import org.junit.Test

class EffectsAndTransitionsTest {

    @Test fun everyEffectHasDefaultsForAllParams() {
        EffectType.entries.forEach { type ->
            val spec = EffectCatalog.spec(type)
            val defaults = EffectCatalog.defaults(type)
            assertEquals(spec.params.size, defaults.size)
            spec.params.forEach { p -> assertTrue(defaults.containsKey(p.key)) }
        }
    }

    @Test fun animatedEffectParamWins() {
        val e = Effect(id = "e", type = EffectType.GAUSSIAN_BLUR,
            params = mapOf("amount" to 10f),
            animated = mapOf("amount" to KeyframeTrack("amount", listOf(
                Keyframe(timeMs = 0, value = 0f, easing = Easing.LINEAR),
                Keyframe(timeMs = 1000, value = 50f, easing = Easing.LINEAR)
            ))))
        assertEquals(25f, EffectCatalog.paramValue(e, "amount", 500L), 0.001f)
    }

    @Test fun transitionTypesProduceRealStates() {
        assertEquals(0f, TransitionResolver.resolve(TransitionSpec(TransitionType.FADE_IN, 500), 0f).opacity, 0.001f)
        assertTrue(TransitionResolver.resolve(TransitionSpec(TransitionType.ZOOM, 500), 0f).scale < 1f)
        assertTrue(TransitionResolver.resolve(TransitionSpec(TransitionType.SPIN, 500), 0f).rotation > 0f)
        assertEquals(0f, TransitionResolver.resolve(TransitionSpec(TransitionType.WIPE, 500), 0f).wipe, 0.001f)
        assertTrue(TransitionResolver.resolve(TransitionSpec(TransitionType.BLUR, 500), 0f).blur > 0f)
    }

    @Test fun crossfadeReturnsComplementaryOpacity() {
        val clip = TransitionClip(id = "c", durationMs = 1000L)
        val (a, b) = TransitionResolver.crossfade(clip, 0L, 3000L, 2000L, 2500L)
        assertEquals(1f, a + b, 0.001f)
    }
}
