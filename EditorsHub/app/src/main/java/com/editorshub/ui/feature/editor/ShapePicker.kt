package com.editorshub.ui.feature.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.editorshub.core.domain.model.ShapeKind
import com.editorshub.ui.theme.EditorPalette
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

internal data class NativeShapeChoice(
    val kind: ShapeKind,
    val label: String,
    val sides: Int? = null,
    val starPoints: Int? = null
)

private val referenceShapes = listOf(
    NativeShapeChoice(ShapeKind.CIRCLE, "Circle"),
    NativeShapeChoice(ShapeKind.ROUNDED_RECTANGLE, "Rounded rectangle"),
    NativeShapeChoice(ShapeKind.CROSS, "Plus"),
    NativeShapeChoice(ShapeKind.ARC, "Arc"),
    NativeShapeChoice(ShapeKind.TRIANGLE, "Triangle"),
    NativeShapeChoice(ShapeKind.CALLOUT, "Callout"),
    NativeShapeChoice(ShapeKind.PIE, "Pie"),
    NativeShapeChoice(ShapeKind.HEXAGON, "Hexagon"),
    NativeShapeChoice(ShapeKind.FLOWER, "Flower"),
    NativeShapeChoice(ShapeKind.ARROW, "Arrow"),
    NativeShapeChoice(ShapeKind.TRAPEZOID, "Trapezoid"),
    NativeShapeChoice(ShapeKind.CRESCENT, "Crescent"),
    NativeShapeChoice(ShapeKind.RECTANGLE, "Rectangle"),
    NativeShapeChoice(ShapeKind.STAR, "Star"),
    NativeShapeChoice(ShapeKind.LINE, "Line"),
    NativeShapeChoice(ShapeKind.BEAM, "Beam"),
    NativeShapeChoice(ShapeKind.FLAG, "Curve"),
    NativeShapeChoice(ShapeKind.SEAL, "Burst seal")
)

private val extraShapes = listOf(
    NativeShapeChoice(ShapeKind.DIAMOND, "Diamond"),
    NativeShapeChoice(ShapeKind.PENTAGON, "Pentagon"),
    NativeShapeChoice(ShapeKind.SPARKLE, "Sparkle"),
    NativeShapeChoice(ShapeKind.BURST, "Burst"),
    NativeShapeChoice(ShapeKind.HEART, "Heart"),
    NativeShapeChoice(ShapeKind.RING, "Ring"),
    NativeShapeChoice(ShapeKind.HALF_CIRCLE, "Half circle"),
    NativeShapeChoice(ShapeKind.POLYGON, "Polygon", sides = 6),
    NativeShapeChoice(ShapeKind.POLYGON, "3-sided polygon", sides = 3),
    NativeShapeChoice(ShapeKind.POLYGON, "4-sided polygon", sides = 4),
    NativeShapeChoice(ShapeKind.POLYGON, "5-sided polygon", sides = 5),
    NativeShapeChoice(ShapeKind.POLYGON, "6-sided polygon", sides = 6),
    NativeShapeChoice(ShapeKind.POLYGON, "7-sided polygon", sides = 7),
    NativeShapeChoice(ShapeKind.POLYGON, "8-sided polygon", sides = 8),
    NativeShapeChoice(ShapeKind.POLYGON, "9-sided polygon", sides = 9),
    NativeShapeChoice(ShapeKind.POLYGON, "10-sided polygon", sides = 10),
    NativeShapeChoice(ShapeKind.POLYGON, "11-sided polygon", sides = 11),
    NativeShapeChoice(ShapeKind.POLYGON, "12-sided polygon", sides = 12)
)

private val precisionShapes = (3..10).map {
    NativeShapeChoice(ShapeKind.STAR, "$it-point star", starPoints = it)
} + listOf(
        NativeShapeChoice(ShapeKind.LEAF, "Leaf"),
        NativeShapeChoice(ShapeKind.OCTAGON, "Octagon"),
        NativeShapeChoice(ShapeKind.ARROWHEAD, "Arrowhead"),
        NativeShapeChoice(ShapeKind.CROWN, "Crown"),
        NativeShapeChoice(ShapeKind.HOURGLASS, "Hourglass"),
        NativeShapeChoice(ShapeKind.SECTOR, "Sector"),
        NativeShapeChoice(ShapeKind.NOTCHED_SQUARE, "Notched square"),
        NativeShapeChoice(ShapeKind.WAVE, "Wave"),
        NativeShapeChoice(ShapeKind.DIAMOND, "Diamond outline"),
        NativeShapeChoice(ShapeKind.PENTAGON, "Pentagon variant")
    )

@Composable
internal fun ShapePickerDialog(onDismiss: () -> Unit, onPick: (NativeShapeChoice) -> Unit) {
    var page by remember { mutableIntStateOf(0) }
    val pages = listOf(referenceShapes, extraShapes, precisionShapes)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color(0x99000000))) {
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 12.dp, vertical = 24.dp)
                    .heightIn(min = 286.dp, max = 360.dp)
                    .clip(RoundedCornerShape(24.dp)).background(EditorPalette.Surface)
                    .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 8.dp)
            ) {
                Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = EditorPalette.TextPrimary)
                    }
                    Text("Shape", fontSize = 18.sp, color = EditorPalette.TextPrimary)
                    Spacer(Modifier.weight(1f))
                    Text("${page + 1} / 3", fontSize = 9.sp, color = EditorPalette.TextMuted)
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(6),
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(vertical = 2.dp)
                ) {
                    items(pages[page], key = { "${it.label}-${it.sides}-${it.starPoints}" }) { choice ->
                        Box(
                            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(7.dp))
                                .clickable { onPick(choice) },
                            contentAlignment = Alignment.Center
                        ) {
                            ShapeGlyph(choice.kind, Modifier.size(36.dp), choice.sides, choice.starPoints)
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().height(36.dp)) {
                    Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
                        repeat(3) { index ->
                            Box(
                                Modifier.padding(horizontal = 4.dp).size(if (page == index) 7.dp else 6.dp)
                                    .clip(CircleShape)
                                    .background(if (page == index) Color.White else Color(0xFF77777D))
                                    .clickable { page = index }
                            )
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterEnd).size(34.dp)
                        .clip(CircleShape).background(EditorPalette.Purple)) {
                        Icon(Icons.Default.Close, "Close shape picker", tint = EditorPalette.Black)
                    }
                }
            }
        }
    }
}

@Composable
private fun ShapeGlyph(
    kind: ShapeKind,
    modifier: Modifier = Modifier,
    polygonSides: Int? = null,
    starPoints: Int? = null
) {
    Canvas(modifier) {
        val c = Color(0xFFB9B9BE)
        val w = size.width * 0.78f
        val h = size.height * 0.78f
        val l = (size.width - w) / 2f
        val t = (size.height - h) / 2f
        val r = l + w
        val b = t + h
        val stroke = Stroke(width = size.minDimension * 0.055f)
        fun poly(points: List<Offset>) {
            if (points.isEmpty()) return
            val path = Path().apply {
                moveTo(points.first().x, points.first().y)
                points.drop(1).forEach { lineTo(it.x, it.y) }
                close()
            }
            drawPath(path, c)
        }
        fun radial(count: Int, inner: Float = 0.45f) {
            val points = (0 until count * 2).map { i ->
                val angle = -Math.PI / 2 + i * Math.PI / count
                val radius = if (i % 2 == 0) 0.5f else inner * 0.5f
                Offset(size.width / 2f + (cos(angle) * w * radius).toFloat(),
                    size.height / 2f + (sin(angle) * h * radius).toFloat())
            }
            poly(points)
        }
        when (kind) {
            ShapeKind.CIRCLE -> drawCircle(c, min(w, h) / 2f)
            ShapeKind.ROUNDED_RECTANGLE -> drawRoundRect(c, Offset(l, t), androidx.compose.ui.geometry.Size(w, h),
                CornerRadius(min(w, h) * 0.12f))
            ShapeKind.RECTANGLE -> drawRect(c, Offset(l, t), androidx.compose.ui.geometry.Size(w, h))
            ShapeKind.CROSS -> {
                drawRect(c, Offset(size.width * 0.42f, t), androidx.compose.ui.geometry.Size(w * 0.16f, h))
                drawRect(c, Offset(l, size.height * 0.42f), androidx.compose.ui.geometry.Size(w, h * 0.16f))
            }
            ShapeKind.ARC -> drawArc(c, 215f, 290f, false, Offset(l, t), androidx.compose.ui.geometry.Size(w, h), style = stroke)
            ShapeKind.TRIANGLE -> poly(listOf(Offset(size.width / 2f, t), Offset(r, b), Offset(l, b)))
            ShapeKind.CALLOUT -> {
                drawRoundRect(c, Offset(l, t), androidx.compose.ui.geometry.Size(w, h * 0.72f), CornerRadius(4.dp.toPx()))
                poly(listOf(Offset(l + w * 0.22f, t + h * 0.68f), Offset(l + w * 0.18f, b), Offset(l + w * 0.52f, t + h * 0.68f)))
            }
            ShapeKind.PIE, ShapeKind.SECTOR -> {
                val path = Path().apply {
                    moveTo(size.width / 2f, size.height / 2f)
                    lineTo(size.width / 2f, t)
                    arcTo(androidx.compose.ui.geometry.Rect(l, t, r, b), -90f,
                        if (kind == ShapeKind.PIE) 275f else 90f, false)
                    close()
                }
                drawPath(path, c)
            }
            ShapeKind.HEXAGON, ShapeKind.PENTAGON, ShapeKind.OCTAGON, ShapeKind.POLYGON -> {
                val n = polygonSides ?: when (kind) {
                    ShapeKind.HEXAGON -> 6; ShapeKind.PENTAGON -> 5; ShapeKind.OCTAGON -> 8; else -> 6
                }
                val points = (0 until n).map { i ->
                    val a = -Math.PI / 2 + i * Math.PI * 2 / n
                    Offset(size.width / 2f + (cos(a) * w / 2f).toFloat(),
                        size.height / 2f + (sin(a) * h / 2f).toFloat())
                }
                poly(points)
            }
            ShapeKind.FLOWER -> {
                val rr = min(w, h) * 0.23f
                drawCircle(c, rr, Offset(size.width / 2f, t + h * 0.23f))
                drawCircle(c, rr, Offset(r - w * 0.23f, size.height / 2f))
                drawCircle(c, rr, Offset(size.width / 2f, b - h * 0.23f))
                drawCircle(c, rr, Offset(l + w * 0.23f, size.height / 2f))
                drawCircle(c, rr * 0.6f)
            }
            ShapeKind.ARROW, ShapeKind.ARROWHEAD -> poly(listOf(
                Offset(l, t + h * 0.35f), Offset(l + w * 0.62f, t + h * 0.35f),
                Offset(l + w * 0.62f, t), Offset(r, size.height / 2f),
                Offset(l + w * 0.62f, b), Offset(l + w * 0.62f, t + h * 0.65f), Offset(l, t + h * 0.65f)
            ))
            ShapeKind.TRAPEZOID -> poly(listOf(Offset(l + w * 0.2f, t), Offset(r - w * 0.2f, t), Offset(r, b), Offset(l, b)))
            ShapeKind.CRESCENT -> {
                drawCircle(c, min(w, h) / 2f)
                drawCircle(EditorPalette.Surface, min(w, h) * 0.42f, Offset(size.width * 0.60f, size.height * 0.43f))
            }
            ShapeKind.STAR, ShapeKind.SPARKLE, ShapeKind.SEAL, ShapeKind.BURST ->
                radial(if (kind == ShapeKind.STAR) (starPoints ?: 5) else when (kind) {
                    ShapeKind.SPARKLE -> 4; ShapeKind.SEAL -> 16; ShapeKind.BURST -> 12; else -> 5
                }, if (kind == ShapeKind.SPARKLE) 0.1f else 0.45f)
            ShapeKind.LINE -> drawLine(c, Offset(l, b), Offset(r, t), strokeWidth = size.minDimension * 0.065f)
            ShapeKind.BEAM -> poly(listOf(Offset(l, t + h * 0.73f), Offset(l + w * 0.18f, b), Offset(r, t + h * 0.27f), Offset(r - w * 0.18f, t)))
            ShapeKind.FLAG -> poly(listOf(Offset(l + w * 0.16f, b), Offset(l + w * 0.16f, t), Offset(r, t), Offset(l + w * 0.72f, t + h * 0.48f), Offset(l + w * 0.45f, b)))
            ShapeKind.HEART -> {
                val path = Path().apply {
                    moveTo(size.width / 2f, b)
                    cubicTo(l - w * 0.1f, t + h * 0.45f, l + w * 0.1f, t - h * 0.1f, size.width / 2f, t + h * 0.25f)
                    cubicTo(r - w * 0.1f, t - h * 0.1f, r + w * 0.1f, t + h * 0.45f, size.width / 2f, b)
                    close()
                }
                drawPath(path, c)
            }
            ShapeKind.RING -> {
                drawCircle(c, min(w, h) / 2f)
                drawCircle(EditorPalette.Surface, min(w, h) * 0.25f)
            }
            ShapeKind.HALF_CIRCLE -> drawArc(c, 180f, 180f, true, Offset(l, t), androidx.compose.ui.geometry.Size(w, h))
            ShapeKind.DIAMOND -> poly(listOf(Offset(size.width / 2f, t), Offset(r, size.height / 2f), Offset(size.width / 2f, b), Offset(l, size.height / 2f)))
            ShapeKind.LEAF, ShapeKind.WAVE -> {
                val path = Path().apply {
                    moveTo(l, b); cubicTo(l, t, r, t, r, b); cubicTo(r, t + h * 0.7f, l, t + h * 0.7f, l, b); close()
                }
                drawPath(path, c)
            }
            ShapeKind.CROWN -> poly(listOf(Offset(l, t + h * 0.35f), Offset(l + w * 0.2f, t + h * 0.55f),
                Offset(size.width / 2f, t), Offset(r - w * 0.2f, t + h * 0.55f), Offset(r, t + h * 0.35f), Offset(r - w * 0.08f, b), Offset(l + w * 0.08f, b)))
            ShapeKind.HOURGLASS -> poly(listOf(Offset(l, t), Offset(r, t), Offset(l + w * 0.58f, size.height / 2f), Offset(r, b), Offset(l, b), Offset(l + w * 0.42f, size.height / 2f)))
            ShapeKind.NOTCHED_SQUARE -> poly(listOf(Offset(l, t), Offset(r, t), Offset(r, b), Offset(size.width / 2f + w * 0.15f, b), Offset(size.width / 2f, b - h * 0.2f), Offset(size.width / 2f - w * 0.15f, b), Offset(l, b)))
        }
    }
}
