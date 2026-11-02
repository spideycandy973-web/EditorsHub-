package com.motionstudio.editor

import com.motionstudio.editor.core.data.ProjectSerializer
import com.motionstudio.editor.core.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class ProjectSerializationTest {

    private fun sample() = Project(
        id = "p1", name = "Test", width = 1080, height = 1920, fps = 30, durationMs = 8000,
        background = "#FF0B0C14",
        layers = listOf(
            Layer(
                id = "l1", name = "Title", type = LayerType.TEXT,
                startMs = 200, durationMs = 5000,
                text = TextSpec(text = "Hello\nWorld", fontSize = 120f),
                keyframes = listOf(
                    KeyframeTrack("opacity", listOf(
                        Keyframe(id = "k1", timeMs = 0, value = 0f, easing = Easing.EASE_OUT),
                        Keyframe(id = "k2", timeMs = 800, value = 1f, easing = Easing.LINEAR)
                    ))
                ),
                effects = listOf(Effect(id = "e1", type = EffectType.GLOW,
                    params = mapOf("amount" to 40f))),
                masks = listOf(MaskSpec(id = "m1", kind = MaskKind.CIRCLE, featherPx = 12f)),
                transitionIn = TransitionSpec(TransitionType.ZOOM, 600L)
            )
        ),
        audioTracks = listOf(AudioTrack(id = "a1", uri = "file://x", name = "music",
            durationMs = 4000, peaks = listOf(0.1f, 0.9f))),
        transitions = listOf(TransitionClip(id = "t1", fromLayerId = "l1", toLayerId = "l2"))
    )

    @Test fun roundTripPreservesEverything() {
        val json = ProjectSerializer.encode(sample())
        val restored = ProjectSerializer.decode(json)
        assertEquals("Test", restored.name)
        assertEquals(1, restored.layers.size)
        assertEquals("Hello\nWorld", restored.layers[0].text?.text)
        assertEquals(2, restored.layers[0].keyframes[0].keyframes.size)
        assertEquals(40f, restored.layers[0].effects[0].params["amount"]!!, 0.001f)
        assertEquals(MaskKind.CIRCLE, restored.layers[0].masks[0].kind)
        assertEquals(TransitionType.ZOOM, restored.layers[0].transitionIn?.type)
        assertEquals(2, restored.audioTracks[0].peaks.size)
        assertEquals("t1", restored.transitions[0].id)
    }

    @Test fun migrateHealsInvalidValues() {
        val raw = ProjectSerializer.encode(sample().copy(fps = 0, width = 2, version = 0,
            layers = listOf(sample().layers[0].copy(durationMs = 0L))))
        val healed = ProjectSerializer.migrate(raw)
        assertEquals(PROJECT_FORMAT_VERSION, healed.version)
        assertTrue(healed.fps >= 1)
        assertTrue(healed.width >= 16)
        assertTrue(healed.layers[0].durationMs >= 33L)
    }

    @Test fun jsonContainsStructuredFields() {
        val json = ProjectSerializer.encode(sample())
        assertTrue(json.contains("\"keyframes\""))
        assertTrue(json.contains("\"effects\""))
        assertTrue(json.contains("\"masks\""))
    }
}
