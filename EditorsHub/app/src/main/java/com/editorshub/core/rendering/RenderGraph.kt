package com.editorshub.core.rendering

import com.editorshub.core.domain.effects.EffectCatalog
import com.editorshub.core.domain.keyframe.KeyframeEngine
import com.editorshub.core.domain.model.ColorUtil
import com.editorshub.core.domain.model.Effect
import com.editorshub.core.domain.model.Layer
import com.editorshub.core.domain.model.LayerType
import com.editorshub.core.domain.model.MaskSpec
import com.editorshub.core.domain.model.Project
import com.editorshub.core.domain.model.Transform
import com.editorshub.core.domain.timeline.TimelineMath
import com.editorshub.core.domain.transitions.TransitionResolver
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/** 2D affine transform (a,b,c,d,e,f) as used by android.graphics.Matrix. */
data class Affine(val a: Float, val b: Float, val c: Float, val d: Float, val e: Float, val f: Float) {
    companion object {
        val IDENTITY = Affine(1f, 0f, 0f, 1f, 0f, 0f)
        fun translate(x: Float, y: Float) = Affine(1f, 0f, 0f, 1f, x, y)
        fun scale(x: Float, y: Float) = Affine(x, 0f, 0f, y, 0f, 0f)
        fun rotate(deg: Float): Affine {
            val r = Math.toRadians(deg.toDouble())
            val cs = cos(r).toFloat(); val sn = sin(r).toFloat()
            return Affine(cs, sn, -sn, cs, 0f, 0f)
        }
        fun skewX(deg: Float) = Affine(1f, 0f, tan(Math.toRadians(deg.toDouble())).toFloat(), 1f, 0f, 0f)
    }
}

fun multiply(m: Affine, n: Affine): Affine = Affine(
    m.a * n.a + m.c * n.b,
    m.b * n.a + m.d * n.b,
    m.a * n.c + m.c * n.d,
    m.b * n.c + m.d * n.d,
    m.a * n.e + m.c * n.f + m.e,
    m.b * n.e + m.d * n.f + m.f
)

data class ResolvedTransform(
    val x: Float, val y: Float, val scaleX: Float, val scaleY: Float,
    val rotation: Float, val opacity: Float, val anchorX: Float, val anchorY: Float,
    val skewX: Float, val matrix: Affine
)

data class ResolvedEffect(val effect: Effect, val params: Map<String, Float>)
data class ResolvedMask(val mask: MaskSpec, val opacity: Float, val feather: Float, val expansion: Float)

data class ResolvedLayer(
    val layer: Layer,
    val transform: ResolvedTransform,
    val effects: List<ResolvedEffect>,
    val masks: List<ResolvedMask>,
    val transition: TransitionResolver.State,
    val color: Int
)

data class RenderFrame(
    val project: Project,
    val timeMs: Long,
    val layers: List<ResolvedLayer>,
    val crossfades: Map<String, Float>
)

/**
 * Turns a [Project] + time into a flat list of drawable layers with every animated value
 * resolved. Deliberately free of Android types so rendering math is unit testable.
 */
object RenderGraph {

    fun resolve(project: Project, timeMs: Long): RenderFrame {
        val transforms = project.layers.associate { it.id to resolvedTransform(it, timeMs) }
        val crossfades = resolveCrossfades(project, timeMs)
        val layers = mutableListOf<ResolvedLayer>()
        for (layer in project.layers) {
            if (!layer.visible || !layer.enabled || !TimelineMath.active(layer, timeMs)) continue
            val local = timeMs - layer.startMs
            val base = transforms.getValue(layer.id)
            val world = worldMatrix(project, layer, transforms)
            val transform = base.copy(matrix = world)

            val transition = run {
                val inSpec = layer.transitionIn
                val outSpec = layer.transitionOut
                when {
                    inSpec != null && local < inSpec.durationMs ->
                        TransitionResolver.resolve(inSpec, TransitionResolver.inProgress(inSpec, local))
                    outSpec != null && layer.durationMs - local < outSpec.durationMs ->
                        TransitionResolver.resolve(outSpec, TransitionResolver.outProgress(outSpec, local, layer.durationMs))
                    else -> TransitionResolver.NEUTRAL
                }
            }

            val effects = layer.effects.filter { it.enabled }
                .map { ResolvedEffect(it, EffectCatalog.resolvedParams(it, local)) }

            val masks = layer.masks.map { mask ->
                ResolvedMask(
                    mask = mask,
                    opacity = KeyframeEngine.trackValue(mask.keyframes, "opacity", local, mask.opacity),
                    feather = KeyframeEngine.trackValue(mask.keyframes, "feather", local, mask.featherPx),
                    expansion = KeyframeEngine.trackValue(mask.keyframes, "expansion", local, mask.expansionPx)
                )
            }

            val fallbackColor = when (layer.type) {
                LayerType.SHAPE -> ColorUtil.parse(layer.shape?.fillColor ?: "#FFFFFFFF")
                LayerType.VECTOR -> ColorUtil.parse(layer.vector?.fillColor ?: "#FFFFFFFF")
                LayerType.TEXT -> ColorUtil.parse(layer.text?.color ?: "#FFFFFFFF")
                LayerType.SOLID -> ColorUtil.parse(project.background)
                else -> 0xFFFFFFFF.toInt()
            }
            val color = KeyframeEngine.trackColor(layer.keyframes, "color", local, fallbackColor)
            layers.add(ResolvedLayer(layer, transform, effects, masks, transition, color))
        }
        return RenderFrame(project, timeMs, layers, crossfades)
    }

    private fun resolveCrossfades(project: Project, timeMs: Long): Map<String, Float> {
        val out = mutableMapOf<String, Float>()
        for (clip in project.transitions) {
            val from = project.layers.firstOrNull { it.id == clip.fromLayerId } ?: continue
            val to = project.layers.firstOrNull { it.id == clip.toLayerId } ?: continue
            val (a, b) = TransitionResolver.crossfade(clip, from.startMs, from.durationMs, to.startMs, timeMs)
            out[from.id] = (out[from.id] ?: 1f) * a
            out[to.id] = (out[to.id] ?: 1f) * b
        }
        return out
    }

    fun resolvedTransform(layer: Layer, timeMs: Long): ResolvedTransform {
        val t = layer.transform
        val k = layer.keyframes
        val local = timeMs - layer.startMs
        return ResolvedTransform(
            x = KeyframeEngine.trackValue(k, "x", local, t.x),
            y = KeyframeEngine.trackValue(k, "y", local, t.y),
            scaleX = KeyframeEngine.trackValue(k, "scaleX", local, t.scaleX),
            scaleY = KeyframeEngine.trackValue(k, "scaleY", local, t.scaleY),
            rotation = KeyframeEngine.trackValue(k, "rotation", local, t.rotation),
            opacity = KeyframeEngine.trackValue(k, "opacity", local, t.opacity),
            anchorX = KeyframeEngine.trackValue(k, "anchorX", local, t.anchorX),
            anchorY = KeyframeEngine.trackValue(k, "anchorY", local, t.anchorY),
            skewX = KeyframeEngine.trackValue(k, "skewX", local, t.skewX),
            matrix = Affine.IDENTITY
        )
    }

    private fun contentSize(layer: Layer, project: Project): Pair<Float, Float> = when (layer.type) {
        LayerType.SHAPE -> (layer.shape?.width ?: project.width * 0.5f) to (layer.shape?.height ?: project.width * 0.5f)
        LayerType.VECTOR -> (project.width * 0.5f) to (project.width * 0.5f)
        LayerType.TEXT -> (project.width * 0.8f) to ((layer.text?.fontSize ?: 96f) * 2f)
        else -> project.width.toFloat() to project.height.toFloat()
    }

    private fun localMatrix(layer: Layer, rt: ResolvedTransform, project: Project): Affine {
        val (w, h) = contentSize(layer, project)
        var m = Affine.translate(rt.x, rt.y)
        m = multiply(m, Affine.rotate(rt.rotation))
        m = multiply(m, Affine.scale(rt.scaleX, rt.scaleY))
        if (rt.skewX != 0f) m = multiply(m, Affine.skewX(rt.skewX))
        m = multiply(m, Affine.translate(-rt.anchorX * w, -rt.anchorY * h))
        return m
    }

    private fun chain(layer: Layer, project: Project, transforms: Map<String, ResolvedTransform>, depth: Int): Affine {
        val own = localMatrix(layer, transforms.getValue(layer.id), project)
        val parentId = layer.parentId
        if (parentId == null || depth > 12) return own
        val parent = project.layers.firstOrNull { it.id == parentId } ?: return own
        return multiply(chain(parent, project, transforms, depth + 1), own)
    }

    fun worldMatrix(project: Project, layer: Layer, transforms: Map<String, ResolvedTransform>): Affine =
        multiply(
            Affine.translate(project.width / 2f, project.height / 2f),
            chain(layer, project, transforms, 0)
        )

    /** Effective opacity including parenting, transitions and crossfades. */
    fun effectiveOpacity(frame: RenderFrame, resolved: ResolvedLayer): Float {
        var alpha = resolved.transform.opacity * resolved.transition.opacity
        var parentId = resolved.layer.parentId
        var guard = 0
        while (parentId != null && guard < 12) {
            val parent = frame.project.layers.firstOrNull { it.id == parentId } ?: break
            alpha *= resolvedTransform(parent, frame.timeMs).opacity
            parentId = parent.parentId
            guard++
        }
        alpha *= frame.crossfades[resolved.layer.id] ?: 1f
        return alpha.coerceIn(0f, 1f)
    }
}
