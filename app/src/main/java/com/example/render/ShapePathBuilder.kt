package com.example.render

import android.graphics.Path
import android.graphics.RectF
import com.example.model.LayerData
import com.example.model.ShapeKind
import com.example.model.VectorNode
import com.example.model.VectorPathData
import kotlin.math.*

object ShapePathBuilder {

    fun buildPath(layer: LayerData, w: Float, h: Float, radiusOverride: Float? = null): Path {
        val hw = w * 0.5f
        val hh = h * 0.5f
        val path = Path()

        if (layer.shapeKind == ShapeKind.VECTOR_PATH && layer.vectorPath != null && layer.vectorPath.nodes.isNotEmpty()) {
            return buildVectorPath(layer.vectorPath)
        }

        when (layer.shapeKind) {
            ShapeKind.RECT -> {
                path.addRect(-hw, -hh, hw, hh, Path.Direction.CW)
            }
            ShapeKind.ROUND_RECT -> {
                val r = (radiusOverride ?: layer.radius).coerceIn(0f, min(hw, hh))
                path.addRoundRect(RectF(-hw, -hh, hw, hh), r, r, Path.Direction.CW)
            }
            ShapeKind.ELLIPSE -> {
                path.addOval(RectF(-hw, -hh, hw, hh), Path.Direction.CW)
            }
            ShapeKind.PILL -> {
                val r = min(hw, hh)
                path.addRoundRect(RectF(-hw, -hh, hw, hh), r, r, Path.Direction.CW)
            }
            ShapeKind.LINE -> {
                val thickness = max(4f, h * 0.14f)
                path.addRoundRect(RectF(-hw, -thickness * 0.5f, hw, thickness * 0.5f), thickness * 0.5f, thickness * 0.5f, Path.Direction.CW)
            }
            ShapeKind.TRIANGLE -> {
                path.moveTo(0f, -hh)
                path.lineTo(hw, hh)
                path.lineTo(-hw, hh)
                path.close()
            }
            ShapeKind.DIAMOND -> {
                path.moveTo(0f, -hh)
                path.lineTo(hw, 0f)
                path.lineTo(0f, hh)
                path.lineTo(-hw, 0f)
                path.close()
            }
            ShapeKind.STAR -> {
                addStar(path, hw, hh, layer.shapePoints.coerceIn(3, 16), (layer.shapeInner / 100f).coerceIn(0.15f, 0.9f))
            }
            ShapeKind.STAR4 -> {
                addStar(path, hw, hh, 4, 0.34f)
            }
            ShapeKind.BURST -> {
                addStar(path, hw, hh, max(8, layer.shapePoints), 0.58f)
            }
            ShapeKind.POLYGON -> {
                addPolygon(path, hw, hh, layer.shapeSides.coerceIn(3, 24))
            }
            ShapeKind.PENTA -> addPolygon(path, hw, hh, 5)
            ShapeKind.HEXAGON -> addPolygon(path, hw, hh, 6)
            ShapeKind.OCTAGON -> addPolygon(path, hw, hh, 8)
            ShapeKind.TRAPEZOID -> {
                path.moveTo(-hw * 0.62f, -hh)
                path.lineTo(hw * 0.62f, -hh)
                path.lineTo(hw, hh)
                path.lineTo(-hw, hh)
                path.close()
            }
            ShapeKind.PARALLELOGRAM -> {
                path.moveTo(-hw * 0.58f, -hh)
                path.lineTo(hw, -hh)
                path.lineTo(hw * 0.58f, hh)
                path.lineTo(-hw, hh)
                path.close()
            }
            ShapeKind.ARROW -> {
                val shaftH = hh * 0.42f
                val headStart = hw * 0.18f
                path.moveTo(-hw, -shaftH)
                path.lineTo(headStart, -shaftH)
                path.lineTo(headStart, -hh)
                path.lineTo(hw, 0f)
                path.lineTo(headStart, hh)
                path.lineTo(headStart, shaftH)
                path.lineTo(-hw, shaftH)
                path.close()
            }
            ShapeKind.CHEVRON -> {
                val t = hw * 0.42f
                path.moveTo(-hw, -hh)
                path.lineTo(-hw + t, -hh)
                path.lineTo(hw, 0f)
                path.lineTo(-hw + t, hh)
                path.lineTo(-hw, hh)
                path.lineTo(hw - t, 0f)
                path.close()
            }
            ShapeKind.PLUS -> {
                val armW = hw * 0.36f
                val armH = hh * 0.36f
                path.moveTo(-armW, -hh); path.lineTo(armW, -hh)
                path.lineTo(armW, -armH); path.lineTo(hw, -armH)
                path.lineTo(hw, armH); path.lineTo(armW, armH)
                path.lineTo(armW, hh); path.lineTo(-armW, hh)
                path.lineTo(-armW, armH); path.lineTo(-hw, armH)
                path.lineTo(-hw, -armH); path.lineTo(-armW, -armH)
                path.close()
            }
            ShapeKind.CROSS_ROUND -> {
                val arm = min(hw, hh) * 0.34f
                path.addRoundRect(RectF(-arm, -hh, arm, hh), arm, arm, Path.Direction.CW)
                path.addRoundRect(RectF(-hw, -arm, hw, arm), arm, arm, Path.Direction.CW)
            }
            ShapeKind.CHECKMARK -> {
                path.moveTo(-hw * 0.85f, hh * 0.05f)
                path.lineTo(-hw * 0.58f, -hh * 0.22f)
                path.lineTo(-hw * 0.18f, hh * 0.28f)
                path.lineTo(hw * 0.68f, -hh * 0.72f)
                path.lineTo(hw * 0.92f, -hh * 0.45f)
                path.lineTo(-hw * 0.18f, hh * 0.78f)
                path.close()
            }
            ShapeKind.HEART -> {
                path.moveTo(0f, hh * 0.88f)
                path.cubicTo(-hw * 1.1f, hh * 0.15f, -hw * 1.05f, -hh * 0.9f, 0f, -hh * 0.32f)
                path.cubicTo(hw * 1.05f, -hh * 0.9f, hw * 1.1f, hh * 0.15f, 0f, hh * 0.88f)
                path.close()
            }
            ShapeKind.PIE -> {
                path.moveTo(0f, 0f)
                path.arcTo(RectF(-hw, -hh, hw, hh), -90f, 270f, false)
                path.close()
            }
            ShapeKind.ARC -> {
                val inner = (layer.shapeInner / 100f).coerceIn(0.4f, 0.88f)
                path.arcTo(RectF(-hw, -hh, hw, hh), -160f, 140f, false)
                path.arcTo(RectF(-hw * inner, -hh * inner, hw * inner, hh * inner), -20f, -140f, false)
                path.close()
            }
            ShapeKind.GEAR -> {
                val teeth = layer.shapeSides.coerceIn(6, 16)
                val total = teeth * 4
                for (i in 0 until total) {
                    val isOuter = (i % 4 == 1 || i % 4 == 2)
                    val rFactor = if (isOuter) 1f else 0.74f
                    val a = -PI / 2.0 + (2.0 * PI * i / total)
                    val px = (cos(a) * hw * rFactor).toFloat()
                    val py = (sin(a) * hh * rFactor).toFloat()
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                path.close()
                path.fillType = Path.FillType.EVEN_ODD
                path.addCircle(0f, 0f, min(hw, hh) * 0.34f, Path.Direction.CCW)
            }
            ShapeKind.SPIRAL -> {
                val turns = 2.6f
                val steps = 64
                val outerR = min(hw, hh)
                val innerWidth = max(6f, outerR * 0.14f)
                for (i in 0..steps) {
                    val u = i.toFloat() / steps
                    val ang = u * turns * 2f * PI.toFloat()
                    val r = outerR * (0.12f + 0.88f * u)
                    val x = cos(ang) * r
                    val y = sin(ang) * r
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                for (i in steps downTo 0) {
                    val u = i.toFloat() / steps
                    val ang = u * turns * 2f * PI.toFloat()
                    val r = (outerR * (0.12f + 0.88f * u) - innerWidth).coerceAtLeast(2f)
                    val x = cos(ang) * r
                    val y = sin(ang) * r
                    path.lineTo(x, y)
                }
                path.close()
            }
            ShapeKind.WAVE -> {
                val crests = 3
                val amp = hh * 0.32f
                val bar = hh * 0.28f
                path.moveTo(-hw, -bar)
                for (i in 0..32) {
                    val u = i / 32f
                    val x = -hw + u * w
                    val y = -bar + sin(u * crests * 2f * PI.toFloat()) * amp
                    path.lineTo(x, y)
                }
                for (i in 32 downTo 0) {
                    val u = i / 32f
                    val x = -hw + u * w
                    val y = bar + sin(u * crests * 2f * PI.toFloat()) * amp
                    path.lineTo(x, y)
                }
                path.close()
            }
            ShapeKind.FLOWER -> {
                val petals = layer.shapePoints.coerceIn(4, 12)
                val steps = petals * 12
                for (i in 0..steps) {
                    val u = (i.toFloat() / steps) * 2f * PI.toFloat()
                    val mod = 0.55f + 0.45f * abs(sin(petals * u * 0.5f))
                    val x = cos(u) * hw * mod
                    val y = sin(u) * hh * mod
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
            }
            ShapeKind.CRESCENT -> {
                path.fillType = Path.FillType.EVEN_ODD
                path.addOval(RectF(-hw, -hh, hw, hh), Path.Direction.CW)
                path.addOval(RectF(-hw * 0.35f, -hh * 0.82f, hw * 1.15f, hh * 0.82f), Path.Direction.CCW)
            }
            ShapeKind.RING -> {
                path.fillType = Path.FillType.EVEN_ODD
                path.addOval(RectF(-hw, -hh, hw, hh), Path.Direction.CW)
                val inner = (layer.shapeInner / 100f).coerceIn(0.2f, 0.85f)
                path.addOval(RectF(-hw * inner, -hh * inner, hw * inner, hh * inner), Path.Direction.CCW)
            }
            ShapeKind.FRAME -> {
                path.fillType = Path.FillType.EVEN_ODD
                path.addRect(-hw, -hh, hw, hh, Path.Direction.CW)
                val inner = (layer.shapeInner / 100f).coerceIn(0.3f, 0.9f)
                path.addRect(-hw * inner, -hh * inner, hw * inner, hh * inner, Path.Direction.CCW)
            }
            ShapeKind.DROP -> {
                path.moveTo(0f, -hh)
                path.cubicTo(hw * 0.85f, -hh * 0.05f, hw, hh * 0.95f, 0f, hh)
                path.cubicTo(-hw, hh * 0.95f, -hw * 0.85f, -hh * 0.05f, 0f, -hh)
                path.close()
            }
            ShapeKind.BUBBLE -> {
                val bodyBottom = hh * 0.56f
                val r = min(hw * 0.2f, hh * 0.2f)
                path.addRoundRect(RectF(-hw, -hh, hw, bodyBottom), r, r, Path.Direction.CW)
                path.moveTo(-hw * 0.22f, bodyBottom - 2f)
                path.lineTo(-hw * 0.52f, hh)
                path.lineTo(hw * 0.10f, bodyBottom - 2f)
                path.close()
            }
            ShapeKind.TAG -> {
                path.moveTo(-hw, -hh * 0.65f)
                path.lineTo(hw * 0.35f, -hh * 0.65f)
                path.lineTo(hw, 0f)
                path.lineTo(hw * 0.35f, hh * 0.65f)
                path.lineTo(-hw, hh * 0.65f)
                path.close()
                path.fillType = Path.FillType.EVEN_ODD
                path.addCircle(-hw * 0.58f, 0f, min(hw, hh) * 0.16f, Path.Direction.CCW)
            }
            ShapeKind.BOOKMARK -> {
                path.moveTo(-hw * 0.65f, -hh)
                path.lineTo(hw * 0.65f, -hh)
                path.lineTo(hw * 0.65f, hh)
                path.lineTo(0f, hh * 0.42f)
                path.lineTo(-hw * 0.65f, hh)
                path.close()
            }
            ShapeKind.SHIELD -> {
                path.moveTo(0f, -hh)
                path.lineTo(hw * 0.88f, -hh * 0.65f)
                path.lineTo(hw * 0.88f, hh * 0.05f)
                path.cubicTo(hw * 0.82f, hh * 0.62f, 0f, hh, 0f, hh)
                path.cubicTo(0f, hh, -hw * 0.82f, hh * 0.62f, -hw * 0.88f, hh * 0.05f)
                path.lineTo(-hw * 0.88f, -hh * 0.65f)
                path.close()
            }
            ShapeKind.LIGHTNING -> {
                path.moveTo(hw * 0.14f, -hh)
                path.lineTo(-hw * 0.65f, hh * 0.06f)
                path.lineTo(-hw * 0.06f, hh * 0.06f)
                path.lineTo(-hw * 0.24f, hh)
                path.lineTo(hw * 0.65f, -hh * 0.12f)
                path.lineTo(hw * 0.06f, -hh * 0.12f)
                path.close()
            }
            ShapeKind.CLOUD -> {
                path.addCircle(-hw * 0.36f, hh * 0.18f, min(hw, hh) * 0.38f, Path.Direction.CW)
                path.addCircle(hw * 0.36f, hh * 0.20f, min(hw, hh) * 0.36f, Path.Direction.CW)
                path.addCircle(-hw * 0.05f, -hh * 0.12f, min(hw, hh) * 0.48f, Path.Direction.CW)
                path.addRoundRect(RectF(-hw * 0.65f, hh * 0.05f, hw * 0.65f, hh * 0.56f), 20f, 20f, Path.Direction.CW)
            }
            ShapeKind.CROWN -> {
                path.moveTo(-hw * 0.82f, hh * 0.65f)
                path.lineTo(hw * 0.82f, hh * 0.65f)
                path.lineTo(hw * 0.94f, -hh * 0.36f)
                path.lineTo(hw * 0.42f, hh * 0.14f)
                path.lineTo(0f, -hh * 0.72f)
                path.lineTo(-hw * 0.42f, hh * 0.14f)
                path.lineTo(-hw * 0.94f, -hh * 0.36f)
                path.close()
            }
            ShapeKind.ROCKET -> {
                path.moveTo(0f, -hh)
                path.cubicTo(hw * 0.56f, -hh * 0.45f, hw * 0.58f, hh * 0.32f, hw * 0.35f, hh * 0.58f)
                path.lineTo(hw * 0.72f, hh * 0.86f)
                path.lineTo(hw * 0.24f, hh * 0.68f)
                path.lineTo(0f, hh * 0.94f)
                path.lineTo(-hw * 0.24f, hh * 0.68f)
                path.lineTo(-hw * 0.72f, hh * 0.86f)
                path.lineTo(-hw * 0.35f, hh * 0.58f)
                path.cubicTo(-hw * 0.58f, hh * 0.32f, -hw * 0.56f, -hh * 0.45f, 0f, -hh)
                path.close()
            }
            ShapeKind.VECTOR_PATH -> {
                path.addRect(-hw, -hh, hw, hh, Path.Direction.CW)
            }
        }
        return path
    }

    fun buildVectorPath(data: VectorPathData): Path {
        val path = Path()
        val nodes = data.nodes
        if (nodes.isEmpty()) return path
        path.moveTo(nodes[0].x, nodes[0].y)
        for (i in 1 until nodes.size) {
            val prev = nodes[i - 1]
            val curr = nodes[i]
            if (prev.smooth || curr.smooth) {
                path.cubicTo(
                    prev.x + prev.outX, prev.y + prev.outY,
                    curr.x + curr.inX, curr.y + curr.inY,
                    curr.x, curr.y
                )
            } else {
                path.lineTo(curr.x, curr.y)
            }
        }
        if (data.closed && nodes.size >= 2) {
            val prev = nodes.last()
            val curr = nodes.first()
            if (prev.smooth || curr.smooth) {
                path.cubicTo(
                    prev.x + prev.outX, prev.y + prev.outY,
                    curr.x + curr.inX, curr.y + curr.inY,
                    curr.x, curr.y
                )
            } else {
                path.lineTo(curr.x, curr.y)
            }
            path.close()
        }
        return path
    }

    /**
     * Subdivides segment [segmentIdx -> segmentIdx+1] at midpoint t=0.5 using De Casteljau's algorithm.
     */
    fun insertMidpointNode(nodes: List<VectorNode>, segmentIdx: Int, closed: Boolean): List<VectorNode> {
        if (nodes.size < 2 || segmentIdx !in nodes.indices) return nodes
        val nextIdx = if (segmentIdx == nodes.lastIndex) {
            if (closed) 0 else return nodes
        } else {
            segmentIdx + 1
        }
        val p0 = nodes[segmentIdx]
        val p3 = nodes[nextIdx]
        val p1x = p0.x + p0.outX
        val p1y = p0.y + p0.outY
        val p2x = p3.x + p3.inX
        val p2y = p3.y + p3.inY

        val m01x = (p0.x + p1x) * 0.5f
        val m01y = (p0.y + p1y) * 0.5f
        val m12x = (p1x + p2x) * 0.5f
        val m12y = (p1y + p2y) * 0.5f
        val m23x = (p2x + p3.x) * 0.5f
        val m23y = (p2y + p3.y) * 0.5f

        val q0x = (m01x + m12x) * 0.5f
        val q0y = (m01y + m12y) * 0.5f
        val q1x = (m12x + m23x) * 0.5f
        val q1y = (m12y + m23y) * 0.5f

        val mx = (q0x + q1x) * 0.5f
        val my = (q0y + q1y) * 0.5f

        val midNode = VectorNode(
            x = mx,
            y = my,
            inX = q0x - mx,
            inY = q0y - my,
            outX = q1x - mx,
            outY = q1y - my,
            smooth = true,
            symmetric = false
        )

        val out = nodes.toMutableList()
        out[segmentIdx] = p0.copy(outX = m01x - p0.x, outY = m01y - p0.y)
        out[nextIdx] = p3.copy(inX = m23x - p3.x, inY = m23y - p3.y)
        out.add(segmentIdx + 1, midNode)
        return out
    }

    private fun addPolygon(path: Path, rx: Float, ry: Float, sides: Int) {
        for (i in 0 until sides) {
            val angle = -PI / 2.0 + (2.0 * PI * i / sides)
            val px = (cos(angle) * rx).toFloat()
            val py = (sin(angle) * ry).toFloat()
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
    }

    private fun addStar(path: Path, rx: Float, ry: Float, points: Int, innerRatio: Float) {
        val total = points * 2
        for (i in 0 until total) {
            val rX = if (i % 2 == 0) rx else rx * innerRatio
            val rY = if (i % 2 == 0) ry else ry * innerRatio
            val angle = -PI / 2.0 + (PI * i / points)
            val px = (cos(angle) * rX).toFloat()
            val py = (sin(angle) * rY).toFloat()
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
    }
}
