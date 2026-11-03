package com.editorshub.core.ai

import com.editorshub.core.domain.effects.EffectCatalog
import com.editorshub.core.domain.model.Effect
import com.editorshub.core.domain.model.EffectType
import com.editorshub.core.domain.model.Keyframe
import com.editorshub.core.domain.model.KeyframeTrack
import com.editorshub.core.domain.model.Easing
import kotlinx.serialization.Serializable
import java.util.UUID

/** Whitelisted, validated rendering nodes — never arbitrary generated code. */
object NodeTypes {
    val ALL = listOf(
        "INPUT", "OUTPUT", "BLUR", "COLOR_GRADE", "DISTORT", "GLOW", "NOISE",
        "DISPLACE", "TRANSFORM", "BLEND", "MASK", "COMPOSITE", "GRADIENT",
        "TEXTURE", "ANIMATION"
    )
    val RENDERABLE = ALL - setOf("INPUT", "OUTPUT", "COMPOSITE", "BLEND", "MASK", "GRADIENT", "TEXTURE", "TRANSFORM")
    fun valid(type: String) = type.uppercase() in ALL
}

@Serializable data class EffectNode(
    val id: String = UUID.randomUUID().toString(),
    val type: String = "BLUR",
    val label: String = "Blur",
    val params: Map<String, Float> = emptyMap(),
    val x: Float = 60f,
    val y: Float = 60f
)

@Serializable data class EffectEdge(val from: String = "", val to: String = "")

@Serializable data class EffectGraph(
    val name: String = "Custom effect",
    val nodes: List<EffectNode> = emptyList(),
    val edges: List<EffectEdge> = emptyList(),
    val outputId: String = "",
    val sourcePrompt: String = ""
)

data class GraphValidation(val ok: Boolean, val errors: List<String>, val order: List<EffectNode>)

/**
 * Converts a node graph into an ordered, real effect stack. Topologically sorts the DAG,
 * rejects cycles/unknown nodes, clamps parameters to their declared ranges and maps only
 * whitelisted node types to actual renderer effects.
 */
object NodeEvaluator {

    fun validate(graph: EffectGraph): GraphValidation {
        val errors = mutableListOf<String>()
        val ids = graph.nodes.map { it.id }.toSet()
        if (graph.nodes.none { it.type == "INPUT" }) errors.add("Graph needs an INPUT node.")
        if (graph.nodes.none { it.type == "OUTPUT" }) errors.add("Graph needs an OUTPUT node.")
        graph.nodes.forEach { n -> if (!NodeTypes.valid(n.type)) errors.add("Unknown node '${n.type}'.") }
        graph.edges.forEach { e ->
            if (e.from !in ids || e.to !in ids) errors.add("Edge references a missing node.")
        }
        val byId = graph.nodes.associateBy { it.id }
        val indegree = graph.nodes.associate { it.id to 0 }.toMutableMap()
        graph.edges.forEach { e -> if (e.to in indegree) indegree[e.to] = indegree.getValue(e.to) + 1 }
        val queue = ArrayDeque(graph.nodes.filter { indegree.getValue(it.id) == 0 }.map { it.id })
        val order = mutableListOf<EffectNode>()
        val seen = mutableSetOf<String>()
        while (queue.isNotEmpty()) {
            val id = queue.removeFirst()
            if (!seen.add(id)) continue
            byId[id]?.let { order.add(it) }
            graph.edges.filter { it.from == id }.forEach { e ->
                indegree[e.to] = (indegree[e.to] ?: 1) - 1
                if (indegree[e.to] == 0) queue.addLast(e.to)
            }
        }
        if (order.size != graph.nodes.size) errors.add("Graph contains a cycle; remove a connection.")
        return GraphValidation(errors.isEmpty(), errors, order)
    }

    fun toEffects(graph: EffectGraph): List<Effect> {
        val validation = validate(graph)
        if (!validation.ok) return emptyList()
        return validation.order.mapNotNull { node ->
            val type = mapNodeToEffect(node.type) ?: return@mapNotNull null
            val specs = EffectCatalog.spec(type).params
            val params = specs.associate { spec ->
                val raw = node.params[spec.key] ?: spec.default
                spec.key to raw.coerceIn(spec.min, spec.max)
            }
            Effect(id = UUID.randomUUID().toString(), type = type, params = params)
        }
    }

    fun mapNodeToEffect(type: String): EffectType? = when (type.uppercase()) {
        "BLUR" -> EffectType.GAUSSIAN_BLUR
        "COLOR_GRADE" -> EffectType.COLOR_GRADING
        "DISTORT" -> EffectType.WAVE_DISTORTION
        "GLOW" -> EffectType.GLOW
        "NOISE" -> EffectType.NOISE
        "DISPLACE" -> EffectType.CHROMATIC_ABERRATION
        "ANIMATION" -> EffectType.PIXELATION
        else -> null
    }

    fun animationTracks(graph: EffectGraph, durationMs: Long): List<KeyframeTrack> {
        val hasAnim = graph.nodes.any { it.type == "ANIMATION" }
        if (!hasAnim) return emptyList()
        val pulse = (graph.nodes.first { it.type == "ANIMATION" }.params["pulse"] ?: 0.3f)
        return listOf(
            KeyframeTrack("opacity", listOf(
                Keyframe(id = UUID.randomUUID().toString(), timeMs = 0L, value = 1f - pulse, easing = Easing.EASE_IN_OUT),
                Keyframe(id = UUID.randomUUID().toString(), timeMs = durationMs / 2, value = 1f, easing = Easing.EASE_IN_OUT),
                Keyframe(id = UUID.randomUUID().toString(), timeMs = durationMs, value = 1f - pulse, easing = Easing.EASE_IN_OUT)
            ))
        )
    }

    fun defaultGraph(): EffectGraph {
        val input = EffectNode(type = "INPUT", label = "Input", x = 40f, y = 120f)
        val blur = EffectNode(type = "BLUR", label = "Blur", x = 240f, y = 60f)
        val glow = EffectNode(type = "GLOW", label = "Glow", x = 240f, y = 190f)
        val anim = EffectNode(type = "ANIMATION", label = "Pulse", x = 440f, y = 120f)
        val output = EffectNode(type = "OUTPUT", label = "Output", x = 640f, y = 120f)
        return EffectGraph(
            name = "Neon pulse",
            nodes = listOf(input, blur, glow, anim, output),
            edges = listOf(
                EffectEdge(input.id, blur.id), EffectEdge(input.id, glow.id),
                EffectEdge(blur.id, anim.id), EffectEdge(glow.id, anim.id),
                EffectEdge(anim.id, output.id)
            ),
            outputId = output.id
        )
    }
}
