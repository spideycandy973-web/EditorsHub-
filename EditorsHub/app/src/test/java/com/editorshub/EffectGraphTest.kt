package com.editorshub

import com.editorshub.core.ai.EffectEdge
import com.editorshub.core.ai.EffectGraph
import com.editorshub.core.ai.EffectNode
import com.editorshub.core.ai.NodeEvaluator
import com.editorshub.core.ai.NodeTypes
import com.editorshub.core.domain.model.EffectType
import org.junit.Assert.*
import org.junit.Test

class EffectGraphTest {

    @Test fun defaultGraphIsValidAndRenders() {
        val g = NodeEvaluator.defaultGraph()
        val v = NodeEvaluator.validate(g)
        assertTrue(v.errors.toString(), v.ok)
        val effects = NodeEvaluator.toEffects(g)
        assertTrue(effects.isNotEmpty())
        assertTrue(effects.any { it.type == EffectType.GAUSSIAN_BLUR })
        assertTrue(effects.any { it.type == EffectType.GLOW })
    }

    @Test fun unknownNodeIsRejected() {
        val g = EffectGraph(nodes = listOf(
            EffectNode(type = "INPUT"), EffectNode(type = "EXECUTE_NATIVE_CODE"),
            EffectNode(type = "OUTPUT")))
        assertFalse(NodeEvaluator.validate(g).ok)
    }

    @Test fun cycleIsRejected() {
        val a = EffectNode(type = "INPUT"); val b = EffectNode(type = "BLUR"); val c = EffectNode(type = "OUTPUT")
        val g = EffectGraph(nodes = listOf(a, b, c),
            edges = listOf(EffectEdge(a.id, b.id), EffectEdge(b.id, c.id), EffectEdge(c.id, b.id)))
        val v = NodeEvaluator.validate(g)
        assertFalse(v.ok)
        assertTrue(v.errors.any { it.contains("cycle") })
    }

    @Test fun parametersAreClampedToDeclaredRanges() {
        val g = EffectGraph(nodes = listOf(
            EffectNode(type = "INPUT"),
            EffectNode(type = "BLUR", params = mapOf("amount" to 9999f)),
            EffectNode(type = "OUTPUT")))
        val effects = NodeEvaluator.toEffects(g)
        val blur = effects.first { it.type == EffectType.GAUSSIAN_BLUR }
        assertTrue(blur.params["amount"]!! <= 100f)
    }

    @Test fun animationNodeProducesKeyframes() {
        val tracks = NodeEvaluator.animationTracks(NodeEvaluator.defaultGraph(), 4000L)
        assertTrue(tracks.isNotEmpty())
        assertEquals(3, tracks.first().keyframes.size)
    }

    @Test fun whitelistIsClosed() {
        assertTrue(NodeTypes.valid("GLOW"))
        assertFalse(NodeTypes.valid("RUN_SHELL"))
    }
}
