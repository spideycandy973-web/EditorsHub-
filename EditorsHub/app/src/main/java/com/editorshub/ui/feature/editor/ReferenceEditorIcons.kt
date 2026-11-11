package com.editorshub.ui.feature.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.editorshub.ui.theme.EditorPalette

/** Thin, single-stroke icons drawn from the reference's editor chrome. */
enum class ReferenceEditorIcon {
    BACK, EXPORT, ZOOM, UNDO, REDO, START, PLAY, PAUSE, END, DUPLICATE, FULLSCREEN, DIAMOND
}

@Composable
fun ReferenceEditorIcon(
    icon: ReferenceEditorIcon,
    modifier: Modifier = Modifier,
    tint: Color = EditorPalette.TextPrimary
) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(
            width = (w * 0.065f).coerceIn(1.2f, 2.3f),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
        fun line(a: Offset, b: Offset) = drawLine(tint, a, b, stroke.width, stroke.cap)
        fun path(filled: Boolean = false, build: Path.() -> Unit) {
            val p = Path().apply(build)
            if (filled) drawPath(p, tint) else drawPath(p, tint, style = stroke)
        }
        when (icon) {
            ReferenceEditorIcon.BACK -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.08f, h * 0.12f),
                    size = Size(w * 0.82f, h * 0.76f),
                    cornerRadius = CornerRadius(w * 0.08f, w * 0.08f),
                    style = stroke
                )
                line(Offset(w * 0.76f, h * 0.5f), Offset(w * 0.28f, h * 0.5f))
                path {
                    moveTo(w * 0.43f, h * 0.33f)
                    lineTo(w * 0.26f, h * 0.5f)
                    lineTo(w * 0.43f, h * 0.67f)
                }
            }
            ReferenceEditorIcon.EXPORT -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.14f, h * 0.16f),
                    size = Size(w * 0.72f, h * 0.72f),
                    cornerRadius = CornerRadius(w * 0.06f, w * 0.06f),
                    style = stroke
                )
                line(Offset(w * 0.44f, h * 0.58f), Offset(w * 0.84f, h * 0.18f))
                path {
                    moveTo(w * 0.58f, h * 0.18f)
                    lineTo(w * 0.85f, h * 0.18f)
                    lineTo(w * 0.85f, h * 0.45f)
                }
            }
            ReferenceEditorIcon.ZOOM -> {
                drawCircle(tint, w * 0.25f, Offset(w * 0.43f, h * 0.43f), style = stroke)
                line(Offset(w * 0.61f, h * 0.61f), Offset(w * 0.84f, h * 0.84f))
                line(Offset(w * 0.33f, h * 0.43f), Offset(w * 0.53f, h * 0.43f))
                line(Offset(w * 0.43f, h * 0.33f), Offset(w * 0.43f, h * 0.53f))
            }
            ReferenceEditorIcon.UNDO -> {
                path {
                    moveTo(w * 0.88f, h * 0.70f)
                    quadraticBezierTo(w * 0.72f, h * 0.12f, w * 0.25f, h * 0.36f)
                }
                path {
                    moveTo(w * 0.12f, h * 0.26f)
                    lineTo(w * 0.25f, h * 0.36f)
                    lineTo(w * 0.14f, h * 0.52f)
                }
            }
            ReferenceEditorIcon.REDO -> {
                path {
                    moveTo(w * 0.12f, h * 0.70f)
                    quadraticBezierTo(w * 0.28f, h * 0.12f, w * 0.75f, h * 0.36f)
                }
                path {
                    moveTo(w * 0.88f, h * 0.26f)
                    lineTo(w * 0.75f, h * 0.36f)
                    lineTo(w * 0.86f, h * 0.52f)
                }
            }
            ReferenceEditorIcon.START -> {
                line(Offset(w * 0.16f, h * 0.18f), Offset(w * 0.16f, h * 0.82f))
                line(Offset(w * 0.86f, h * 0.5f), Offset(w * 0.34f, h * 0.5f))
                path {
                    moveTo(w * 0.52f, h * 0.30f)
                    lineTo(w * 0.32f, h * 0.5f)
                    lineTo(w * 0.52f, h * 0.70f)
                }
            }
            ReferenceEditorIcon.PLAY -> path(filled = true) {
                moveTo(w * 0.28f, h * 0.16f)
                lineTo(w * 0.84f, h * 0.5f)
                lineTo(w * 0.28f, h * 0.84f)
                close()
            }
            ReferenceEditorIcon.PAUSE -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.26f, h * 0.18f),
                    size = Size(w * 0.16f, h * 0.64f),
                    cornerRadius = CornerRadius(w * 0.03f, w * 0.03f)
                )
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.58f, h * 0.18f),
                    size = Size(w * 0.16f, h * 0.64f),
                    cornerRadius = CornerRadius(w * 0.03f, w * 0.03f)
                )
            }
            ReferenceEditorIcon.END -> {
                line(Offset(w * 0.84f, h * 0.18f), Offset(w * 0.84f, h * 0.82f))
                line(Offset(w * 0.14f, h * 0.5f), Offset(w * 0.66f, h * 0.5f))
                path {
                    moveTo(w * 0.48f, h * 0.30f)
                    lineTo(w * 0.68f, h * 0.5f)
                    lineTo(w * 0.48f, h * 0.70f)
                }
            }
            ReferenceEditorIcon.DUPLICATE -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.12f, h * 0.12f),
                    size = Size(w * 0.62f, w * 0.62f),
                    cornerRadius = CornerRadius(w * 0.08f, w * 0.08f),
                    style = stroke
                )
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.32f, h * 0.32f),
                    size = Size(w * 0.56f, w * 0.56f),
                    cornerRadius = CornerRadius(w * 0.08f, w * 0.08f),
                    style = stroke
                )
                line(Offset(w * 0.60f, h * 0.50f), Offset(w * 0.60f, h * 0.70f))
                line(Offset(w * 0.50f, h * 0.60f), Offset(w * 0.70f, h * 0.60f))
            }
            ReferenceEditorIcon.FULLSCREEN -> {
                // Four corner brackets with small inset markers like the reference.
                line(Offset(w * 0.10f, h * 0.30f), Offset(w * 0.10f, h * 0.12f))
                line(Offset(w * 0.10f, h * 0.12f), Offset(w * 0.34f, h * 0.12f))
                line(Offset(w * 0.66f, h * 0.12f), Offset(w * 0.90f, h * 0.12f))
                line(Offset(w * 0.90f, h * 0.12f), Offset(w * 0.90f, h * 0.30f))
                line(Offset(w * 0.10f, h * 0.70f), Offset(w * 0.10f, h * 0.88f))
                line(Offset(w * 0.10f, h * 0.88f), Offset(w * 0.34f, h * 0.88f))
                line(Offset(w * 0.66f, h * 0.88f), Offset(w * 0.90f, h * 0.88f))
                line(Offset(w * 0.90f, h * 0.88f), Offset(w * 0.90f, h * 0.70f))
                path(filled = true) {
                    moveTo(w * 0.42f, h * 0.06f)
                    lineTo(w * 0.50f, h * 0.16f)
                    lineTo(w * 0.58f, h * 0.06f)
                    close()
                }
                path(filled = true) {
                    moveTo(w * 0.42f, h * 0.94f)
                    lineTo(w * 0.50f, h * 0.84f)
                    lineTo(w * 0.58f, h * 0.94f)
                    close()
                }
            }
            ReferenceEditorIcon.DIAMOND -> path {
                moveTo(w * 0.5f, h * 0.08f)
                lineTo(w * 0.92f, h * 0.5f)
                lineTo(w * 0.5f, h * 0.92f)
                lineTo(w * 0.08f, h * 0.5f)
                close()
            }
        }
    }
}

/** A small square with a magnifier-and-plus glyph for the preview's top-left button. */
@Composable
fun ReferenceZoomButton(
    zoomed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) = androidx.compose.material3.IconButton(
    onClick = onClick,
    modifier = modifier.size(38.dp)
) {
    ReferenceEditorIcon(
        ReferenceEditorIcon.ZOOM,
        Modifier.size(23.dp),
        tint = if (zoomed) EditorPalette.Green else EditorPalette.TextPrimary
    )
}
