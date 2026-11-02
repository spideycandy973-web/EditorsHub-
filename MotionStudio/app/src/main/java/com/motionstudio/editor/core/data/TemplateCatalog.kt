package com.motionstudio.editor.core.data

import com.motionstudio.editor.core.domain.model.BlendMode
import com.motionstudio.editor.core.domain.model.Effect
import com.motionstudio.editor.core.domain.model.EffectType
import com.motionstudio.editor.core.domain.model.GradientType
import com.motionstudio.editor.core.domain.model.Keyframe
import com.motionstudio.editor.core.domain.model.KeyframeTrack
import com.motionstudio.editor.core.domain.model.Layer
import com.motionstudio.editor.core.domain.model.LayerType
import com.motionstudio.editor.core.domain.model.Project
import com.motionstudio.editor.core.domain.model.ShapeKind
import com.motionstudio.editor.core.domain.model.ShapeSpec
import com.motionstudio.editor.core.domain.model.TextSpec
import com.motionstudio.editor.core.domain.model.TransitionSpec
import com.motionstudio.editor.core.domain.model.TransitionType
import java.util.UUID

/** Ready-to-edit, fully real sample compositions (no placeholder screens). */
object TemplateCatalog {

    data class Template(val name: String, val description: String, val build: () -> Project)

    private fun kf(track: String, vararg points: Pair<Long, Float>) =
        KeyframeTrack(track, points.map { (t, v) -> Keyframe(id = UUID.randomUUID().toString(), timeMs = t, value = v) })

    val templates: List<Template> = listOf(
        Template("Clean Title", "Animated headline over a gradient background") {
            Project(
                id = UUID.randomUUID().toString(), name = "Clean Title",
                width = 1080, height = 1920, fps = 30, durationMs = 6000,
                background = "#FF0E0B1F",
                layers = listOf(
                    Layer(
                        id = UUID.randomUUID().toString(), name = "Headline", type = LayerType.TEXT,
                        startMs = 200, durationMs = 5600,
                        text = TextSpec(text = "MOTION\nSTUDIO", fontSize = 150f, gradient = GradientType.LINEAR),
                        keyframes = listOf(
                            kf("opacity", 0L to 0f, 800L to 1f, 4800L to 1f, 5600L to 0f),
                            kf("y", 0L to 240f, 900L to 0f)
                        ),
                        transitionIn = TransitionSpec(TransitionType.ZOOM, 600)
                    ),
                    Layer(
                        id = UUID.randomUUID().toString(), name = "Glow orb", type = LayerType.SHAPE,
                        startMs = 0, durationMs = 6000,
                        shape = ShapeSpec(kind = ShapeKind.CIRCLE, width = 900f, height = 900f, gradient = GradientType.RADIAL),
                        blendMode = BlendMode.SCREEN,
                        effects = listOf(Effect(type = EffectType.GAUSSIAN_BLUR, params = mapOf("amount" to 70f))),
                        keyframes = listOf(kf("scaleX", 0L to 0.7f, 3000L to 1.15f, 6000L to 0.7f))
                    ),
                    Layer(
                        id = UUID.randomUUID().toString(), name = "Background", type = LayerType.SOLID,
                        startMs = 0, durationMs = 6000
                    )
                )
            )
        },
        Template("Kinetic Shapes", "Rotating vector shapes with glow and color grading") {
            Project(
                id = UUID.randomUUID().toString(), name = "Kinetic Shapes",
                width = 1080, height = 1080, fps = 30, durationMs = 5000,
                layers = listOf(
                    Layer(
                        id = UUID.randomUUID().toString(), name = "Star", type = LayerType.SHAPE,
                        shape = ShapeSpec(kind = ShapeKind.STAR, width = 620f, height = 620f, gradient = GradientType.LINEAR),
                        effects = listOf(Effect(type = EffectType.GLOW, params = mapOf("amount" to 45f))),
                        keyframes = listOf(kf("rotation", 0L to 0f, 5000L to 360f))
                    ),
                    Layer(
                        id = UUID.randomUUID().toString(), name = "Ring", type = LayerType.SHAPE,
                        shape = ShapeSpec(kind = ShapeKind.CIRCLE, width = 800f, height = 800f, strokeWidth = 12f),
                        keyframes = listOf(kf("scaleX", 0L to 0.4f, 2500L to 1.1f, 5000L to 0.4f))
                    )
                )
            )
        },
        Template("Lower Third", "Slide-in caption bar for interviews") {
            Project(
                id = UUID.randomUUID().toString(), name = "Lower Third",
                width = 1920, height = 1080, fps = 30, durationMs = 6000,
                layers = listOf(
                    Layer(
                        id = UUID.randomUUID().toString(), name = "Bar", type = LayerType.SHAPE,
                        startMs = 400, durationMs = 5000,
                        shape = ShapeSpec(kind = ShapeKind.RECTANGLE, width = 900f, height = 120f, cornerRadius = 28f),
                        transitionIn = TransitionSpec(TransitionType.SLIDE, 500),
                        transitionOut = TransitionSpec(TransitionType.FADE_OUT, 400)
                    ),
                    Layer(
                        id = UUID.randomUUID().toString(), name = "Name", type = LayerType.TEXT,
                        startMs = 600, durationMs = 4800,
                        text = TextSpec(text = "YOUR NAME", fontSize = 64f),
                        transitionIn = TransitionSpec(TransitionType.FADE_IN, 500)
                    )
                )
            )
        }
    )
}
