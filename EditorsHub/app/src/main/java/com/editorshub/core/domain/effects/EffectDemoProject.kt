package com.editorshub.core.domain.effects

import com.editorshub.core.domain.model.Effect
import com.editorshub.core.domain.model.EffectType
import com.editorshub.core.domain.model.GradientType
import com.editorshub.core.domain.model.Layer
import com.editorshub.core.domain.model.LayerType
import com.editorshub.core.domain.model.Project
import com.editorshub.core.domain.model.ShapeKind
import com.editorshub.core.domain.model.ShapeSpec
import com.editorshub.core.domain.model.TextSpec
import java.util.UUID

/**
 * One shared, real composition for effect thumbnails. Layer 0 is topmost in the
 * compositor, so the subject layers stay above the solid background and the
 * effect is actually visible in the rendered frame.
 */
object EffectDemoProject {
    fun forType(type: EffectType): Project {
        val effect = Effect(
            id = UUID.randomUUID().toString(),
            type = type,
            params = EffectCatalog.defaults(type)
        )
        return Project(
            id = "demo_$type",
            name = EffectCatalog.spec(type).label,
            width = 480,
            height = 480,
            fps = 30,
            durationMs = 2000L,
            background = "#FF12142A",
            layers = listOf(
                Layer(
                    id = "label", name = "Label", type = LayerType.TEXT,
                    startMs = 0, durationMs = 2000L,
                    text = TextSpec(text = EffectCatalog.spec(type).label, fontSize = 42f),
                    effects = listOf(effect.copy(id = UUID.randomUUID().toString()))
                ),
                Layer(
                    id = "shape", name = "Preview shape", type = LayerType.SHAPE,
                    startMs = 0, durationMs = 2000L,
                    shape = ShapeSpec(
                        kind = ShapeKind.STAR, width = 260f, height = 260f,
                        gradient = GradientType.RADIAL,
                        fillColor = "#FFFFFFFF", fillColor2 = "#FF22D3EE"
                    ),
                    effects = listOf(effect)
                ),
                Layer(
                    id = "bg", name = "Background", type = LayerType.SOLID,
                    startMs = 0, durationMs = 2000L
                )
            )
        )
    }
}
