package com.editorshub.core.rendering.canvas

import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.util.Log
import com.editorshub.core.domain.effects.EffectCatalog
import com.editorshub.core.domain.model.BlendMode as ProjectBlendMode
import com.editorshub.core.domain.model.ColorUtil
import com.editorshub.core.domain.model.EffectType
import com.editorshub.core.domain.model.FitMode
import com.editorshub.core.domain.model.GradientType
import com.editorshub.core.domain.model.LayerType
import com.editorshub.core.domain.model.MaskKind
import com.editorshub.core.domain.model.ShapeKind
import com.editorshub.core.domain.model.ShapeSpec
import com.editorshub.core.domain.model.TextAlign
import com.editorshub.core.domain.model.VectorSpec
import com.editorshub.core.media.MediaRepository
import com.editorshub.core.rendering.Affine
import com.editorshub.core.rendering.RenderFrame
import com.editorshub.core.rendering.RenderGraph
import com.editorshub.core.rendering.ResolvedEffect
import com.editorshub.core.rendering.ResolvedLayer
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The real compositor. Draws every layer into an isolated bitmap, applies the resolved
 * effect stack and masks, then composites with the layer's blend mode and animated matrix.
 * Used by the preview (hardware Canvas) and by the export encoder.
 */
@Singleton
class CanvasCompositor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val media: MediaRepository
) {
    private var scratch: Bitmap? = null
    private var scratchCanvas: Canvas? = null

    /** 0.2..1 — scales decoded video frames to control preview cost. */
    @Volatile var previewQuality: Float = 1f

    @Synchronized
    fun draw(canvas: Canvas, frame: RenderFrame, width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        val projectWidth = frame.project.width.coerceIn(1, 4096)
        val projectHeight = frame.project.height.coerceIn(1, 4096)
        canvas.save()
        try {
            // Layer transforms and content are authored in project pixels. Scale the finished
            // composition into the preview/output viewport instead of interpreting project
            // coordinates as device pixels (which clipped almost every portrait composition).
            canvas.scale(width.toFloat() / projectWidth, height.toFloat() / projectHeight)
            drawBackground(canvas, frame.project, projectWidth, projectHeight)
            // Layer 0 is topmost, so paint bottom-to-top.
            for (resolved in frame.layers.asReversed()) {
                drawLayer(canvas, frame, resolved, projectWidth, projectHeight)
            }
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "Out of memory while compositing; frame skipped")
            canvas.drawColor(Color.BLACK)
        } catch (e: Exception) {
            Log.w(TAG, "Compositing failed; frame skipped", e)
            canvas.drawColor(Color.BLACK)
        } finally {
            canvas.restore()
        }
    }

    private fun drawBackground(canvas: Canvas, project: com.editorshub.core.domain.model.Project,
                               width: Int, height: Int) {
        val p = project
        when (p.backgroundType) {
            com.editorshub.core.domain.model.BackgroundType.TRANSPARENT -> {
                // Transparent composition: leave the canvas clear so the checkerboard overlay shows.
                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            }
            com.editorshub.core.domain.model.BackgroundType.LINEAR_GRADIENT -> {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = gradient(p.background, ColorUtil.parse(p.backgroundEnd),
                        com.editorshub.core.domain.model.GradientType.LINEAR,
                        p.backgroundAngle, width.toFloat(), height.toFloat())
                }
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            }
            com.editorshub.core.domain.model.BackgroundType.RADIAL_GRADIENT -> {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = RadialGradient(width / 2f, height / 2f,
                        kotlin.math.max(width, height) / 2f,
                        ColorUtil.parse(p.background), ColorUtil.parse(p.backgroundEnd),
                        Shader.TileMode.CLAMP)
                }
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            }
            else -> canvas.drawColor(ColorUtil.parse(p.background))
        }
    }

    /** Render the whole composition to a bitmap (used by GPU preview and GL export). */
    @Synchronized
    fun renderToBitmap(frame: RenderFrame, width: Int, height: Int, quality: Float? = null): Bitmap {
        val w = width.coerceIn(1, 4096)
        val h = height.coerceIn(1, 4096)
        val previousQuality = previewQuality
        if (quality != null) previewQuality = quality.coerceIn(0.2f, 1f)
        return try {
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            draw(Canvas(bmp), frame, w, h)
            bmp
        } catch (e: OutOfMemoryError) {
            fallbackBitmap()
        } catch (e: Exception) {
            Log.w(TAG, "renderToBitmap failed", e)
            fallbackBitmap()
        } finally {
            if (quality != null) previewQuality = previousQuality
        }
    }

    private fun fallbackBitmap(): Bitmap =
        Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLACK) }

    private fun obtain(width: Int, height: Int): Pair<Bitmap, Canvas> {
        val w = width.coerceIn(1, 4096)
        val h = height.coerceIn(1, 4096)
        var bmp = scratch
        if (bmp == null || bmp.width != w || bmp.height != h || bmp.isRecycled) {
            bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            scratch = bmp
            scratchCanvas = Canvas(bmp)
        }
        val c = scratchCanvas ?: Canvas(bmp!!).also { scratchCanvas = it }
        return bmp!! to c
    }

    private fun drawLayer(canvas: Canvas, frame: RenderFrame, resolved: ResolvedLayer, width: Int, height: Int) {
        val (layerBitmap, layerCanvas) = obtain(width, height)
        layerCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        val t = resolved.transform
        val matrix = Matrix().apply {
            setValues(floatArrayOf(t.matrix.a, t.matrix.c, t.matrix.e, t.matrix.b, t.matrix.d, t.matrix.f, 0f, 0f, 1f))
        }
        // Transition transform around the composition centre.
        val tr = resolved.transition
        if (tr.scale != 1f || tr.rotation != 0f || tr.dx != 0f || tr.dy != 0f) {
            matrix.postScale(tr.scale, tr.scale, width / 2f, height / 2f)
            matrix.postRotate(tr.rotation, width / 2f, height / 2f)
            matrix.postTranslate(tr.dx * width, tr.dy * height)
        }

        layerCanvas.save()
        layerCanvas.concat(matrix)
        drawContent(layerCanvas, frame, resolved)
        layerCanvas.restore()

        applyMasks(layerCanvas, layerBitmap, resolved, width, height)

        var processed = applyEffects(layerBitmap, resolved.effects, width, height, frame.timeMs)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val alpha = RenderGraph.effectiveOpacity(frame, resolved)
        paint.alpha = (alpha * 255).toInt().coerceIn(0, 255)
        applyBlend(paint, resolved.layer.blendMode)

        canvas.save()
        if (tr.wipe < 1f) {
            canvas.clipRect(0f, 0f, width * tr.wipe, height.toFloat())
        }
        if (tr.blur > 0f && processed != layerBitmap) {
            processed = boxBlur(processed, tr.blur, width, height)
        }
        canvas.drawBitmap(processed, 0f, 0f, paint)
        canvas.restore()

        if (processed !== layerBitmap && !processed.isRecycled) processed.recycle()
    }

    // ------------------------------------------------------------------ content
    private fun drawContent(c: Canvas, frame: RenderFrame, resolved: ResolvedLayer) {
        val layer = resolved.layer
        when (layer.type) {
            LayerType.SOLID -> drawSolid(c, frame, resolved)
            LayerType.SHAPE -> drawShape(c, resolved)
            LayerType.VECTOR -> drawVector(c, layer.vector)
            LayerType.TEXT -> drawText(c, layer.text, frame)
            LayerType.IMAGE -> drawImage(c, layer.sourceUri, layer.fit, frame, resolved, false)
            LayerType.VIDEO -> drawImage(c, layer.sourceUri, layer.fit, frame, resolved, true)
            LayerType.AUDIO, LayerType.EFFECT, LayerType.GROUP -> Unit
        }
    }

    private fun drawSolid(c: Canvas, frame: RenderFrame, resolved: ResolvedLayer) {
        val w = frame.project.width.toFloat()
        val h = frame.project.height.toFloat()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = gradient(frame.project.background, resolved.color, GradientType.LINEAR, 135f, w, h)
        c.drawRect(-w / 2, -h / 2, w / 2, h / 2, paint)
    }

    private fun drawShape(c: Canvas, resolved: ResolvedLayer) {
        val spec = resolved.layer.shape ?: return
        val w = spec.width.coerceAtLeast(1f)
        val h = spec.height.coerceAtLeast(1f)
        val path = shapePath(spec, w, h)
        val openPath = spec.kind in setOf(ShapeKind.LINE, ShapeKind.ARC)
        if (!openPath) {
            val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                shader = gradient(spec.fillColor, resolved.color, spec.gradient, spec.gradientAngle, w, h)
                if (spec.shadowEnabled && spec.shadowSize > 0f) {
                    val a = (spec.shadowAlpha.coerceIn(0f, 1f) * 255).toInt()
                    val base = ColorUtil.parse(spec.shadowColor)
                    setShadowLayer(spec.shadowSize, spec.shadowX, spec.shadowY,
                        (base and 0x00FFFFFF) or (a shl 24))
                }
            }
            c.drawPath(path, fill)
            fill.clearShadowLayer()
        }
        if ((spec.strokeEnabled || openPath) && spec.strokeWidth > 0f) {
            val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = spec.strokeWidth.coerceAtLeast(0.1f)
                color = if (openPath && !spec.strokeEnabled) ColorUtil.parse(spec.fillColor)
                    else ColorUtil.parse(spec.strokeColor)
                strokeCap = when (spec.strokeCap.coerceIn(0, 2)) {
                    0 -> Paint.Cap.BUTT
                    2 -> Paint.Cap.SQUARE
                    else -> Paint.Cap.ROUND
                }
                strokeJoin = when (spec.strokeJoin.coerceIn(0, 2)) {
                    1 -> Paint.Join.ROUND
                    2 -> Paint.Join.BEVEL
                    else -> Paint.Join.MITER
                }
            }
            c.drawPath(trimmedShapePath(path, spec.strokeStart, spec.strokeEnd), stroke)
        }
    }

    private fun trimmedShapePath(path: Path, startPercent: Float, endPercent: Float): Path {
        val start = startPercent.coerceIn(0f, 100f) / 100f
        val end = endPercent.coerceIn(0f, 100f) / 100f
        if (start <= 0f && end >= 1f) return path
        val measure = PathMeasure(path, false)
        val length = measure.length
        if (length <= 0f) return path
        val out = Path()
        if (end >= start) {
            measure.getSegment(length * start, length * end, out, true)
        } else {
            measure.getSegment(length * start, length, out, true)
            measure.getSegment(0f, length * end, out, true)
        }
        return out
    }

    private fun drawVector(c: Canvas, spec: VectorSpec?) {
        if (spec == null || spec.commands.isEmpty()) return
        val path = vectorPath(spec)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            shader = gradient(spec.fillColor, ColorUtil.parse(spec.fillColor2), spec.gradient, spec.gradientAngle, 600f, 600f)
        }
        c.drawPath(path, fill)
        if (spec.strokeWidth > 0f) {
            val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = spec.strokeWidth
                color = ColorUtil.parse(spec.strokeColor)
            }
            c.drawPath(path, stroke)
        }
    }

    private fun drawText(c: Canvas, spec: com.editorshub.core.domain.model.TextSpec?, frame: RenderFrame) {
        if (spec == null) return
        val text = if (spec.uppercase) spec.text.uppercase() else spec.text
        val lines = text.split("\n")
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
        paint.typeface = typeface(spec.fontFamily, spec.fontWeight, spec.italic)
        paint.textSize = spec.fontSize
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) paint.letterSpacing = spec.letterSpacing / spec.fontSize
        paint.textAlign = when (spec.align) {
            TextAlign.LEFT -> Paint.Align.LEFT
            TextAlign.CENTER -> Paint.Align.CENTER
            TextAlign.RIGHT -> Paint.Align.RIGHT
        }
        paint.shader = gradient(spec.color, ColorUtil.parse(spec.color2), spec.gradient, spec.gradientAngle, 900f, spec.fontSize * lines.size)
        if (spec.shadowBlur > 0f) {
            paint.setShadowLayer(spec.shadowBlur, spec.shadowDx, spec.shadowDy, ColorUtil.parse(spec.shadowColor))
        }
        val lineHeight = spec.fontSize * spec.lineSpacing
        val total = lines.size * lineHeight
        val fm = paint.fontMetrics
        val baseY = -total / 2f + lineHeight / 2f - (fm.ascent + fm.descent) / 2f
        val x = when (spec.align) {
            TextAlign.LEFT -> -frame.project.width * 0.45f
            TextAlign.RIGHT -> frame.project.width * 0.45f
            TextAlign.CENTER -> 0f
        }
        if (spec.outlineWidth > 0f) {
            val outline = Paint(paint).apply {
                style = Paint.Style.STROKE
                strokeWidth = spec.outlineWidth
                color = ColorUtil.parse(spec.outlineColor)
                shader = null
            }
            lines.forEachIndexed { i, line -> c.drawText(line, x, baseY + i * lineHeight, outline) }
        }
        lines.forEachIndexed { i, line -> c.drawText(line, x, baseY + i * lineHeight, paint) }
        paint.setShadowLayer(0f, 0f, 0f, 0)
    }

    private fun drawImage(
        c: Canvas, uri: String?, fit: FitMode, frame: RenderFrame,
        resolved: ResolvedLayer, video: Boolean
    ) {
        if (uri == null) return
        val path = uri.removePrefix("file://")
        val maxSize = (max(frame.project.width, frame.project.height) * previewQuality.coerceIn(0.2f, 1f)).toInt().coerceAtLeast(256)
        val bmp = if (video) {
            val localMs = (frame.timeMs - resolved.layer.startMs + resolved.layer.inPointMs).coerceAtLeast(0L)
            media.videoFrame(path, localMs, maxSize)
        } else media.image(path, maxSize)
        if (bmp == null) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(90, 120, 120, 160) }
            c.drawRect(-200f, -200f, 200f, 200f, p)
            return
        }
        val W = frame.project.width.toFloat()
        val H = frame.project.height.toFloat()
        val dst = when (fit) {
            FitMode.STRETCH -> RectF(-W / 2, -H / 2, W / 2, H / 2)
            FitMode.NONE -> RectF(-bmp.width / 2f, -bmp.height / 2f, bmp.width / 2f, bmp.height / 2f)
            FitMode.COVER, FitMode.CONTAIN -> {
                val s = if (fit == FitMode.COVER) max(W / bmp.width, H / bmp.height)
                else min(W / bmp.width, H / bmp.height)
                val dw = bmp.width * s; val dh = bmp.height * s
                RectF(-dw / 2, -dh / 2, dw / 2, dh / 2)
            }
        }
        c.drawBitmap(bmp, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
    }

    // ------------------------------------------------------------------- masks
    private fun applyMasks(c: Canvas, bmp: Bitmap, resolved: ResolvedLayer, width: Int, height: Int) {
        for (mask in resolved.masks) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.xfermode = PorterDuffXfermode(
                if (mask.mask.inverted) PorterDuff.Mode.DST_OUT else PorterDuff.Mode.DST_IN
            )
            paint.alpha = (mask.opacity * 255).toInt().coerceIn(0, 255)
            if (mask.feather > 0f) {
                try { paint.maskFilter = android.graphics.BlurMaskFilter(mask.feather, android.graphics.BlurMaskFilter.Blur.NORMAL) }
                catch (e: Exception) { /* unsupported on this GPU */ }
            }
            val path = Path()
            val expand = mask.expansion
            when (mask.mask.kind) {
                MaskKind.RECTANGLE -> path.addRect(
                    -width / 2f + expand, -height / 2f + expand,
                    width / 2f - expand, height / 2f - expand, Path.Direction.CW
                )
                MaskKind.CIRCLE -> path.addCircle(0f, 0f, min(width, height) / 2f - expand, Path.Direction.CW)
                MaskKind.PATH -> mask.mask.path?.let { path.addPath(vectorPath(it)) }
            }
            c.save()
            c.drawPath(path, paint)
            c.restore()
        }
    }

    // ----------------------------------------------------------------- effects
    private fun applyEffects(
        src: Bitmap,
        effects: List<ResolvedEffect>,
        width: Int,
        height: Int,
        timeMs: Long
    ): Bitmap {
        if (effects.isEmpty()) return src
        var current = src
        for (effect in effects) {
            val p = effect.params
            current = when (effect.effect.type) {
                EffectType.BRIGHTNESS, EffectType.CONTRAST, EffectType.SATURATION,
                EffectType.HUE, EffectType.EXPOSURE, EffectType.SHARPEN, EffectType.COLOR_GRADING ->
                    colorGrade(current, effect, width, height)
                EffectType.GAUSSIAN_BLUR -> boxBlur(current, p["amount"] ?: 12f, width, height)
                EffectType.MOTION_BLUR, EffectType.DIRECTIONAL_BLUR ->
                    directionalBlur(current, p["amount"] ?: 20f, p["angle"] ?: 0f, width, height)
                EffectType.GLOW -> glow(current, p["amount"] ?: 32f, p["strength"] ?: 0.7f, p["hue"] ?: 280f, width, height)
                EffectType.SHADOW -> shadow(current, p, width, height)
                EffectType.VIGNETTE -> vignette(current, p["amount"] ?: 0.55f, p["strength"] ?: 0.65f, width, height)
                EffectType.NOISE -> grain(current, p["amount"] ?: 0.25f, timeMs, width, height)
                EffectType.RGB_SPLIT, EffectType.CHROMATIC_ABERRATION ->
                    rgbSplit(current, p["amount"] ?: 12f, width, height)
                EffectType.PIXELATION -> pixelate(current, p["amount"] ?: 16f, width, height)
                EffectType.WAVE_DISTORTION -> wave(current, p["amount"] ?: 24f, p["strength"] ?: 6f, width, height)
            }
        }
        return current
    }

    private fun newBitmap(w: Int, h: Int): Bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)

    private fun colorGrade(src: Bitmap, effect: ResolvedEffect, w: Int, h: Int): Bitmap {
        val out = newBitmap(w, h)
        val c = Canvas(out)
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val cm = ColorMatrix()
        val stack = mutableListOf<ColorMatrix>()
        when (effect.effect.type) {
            EffectType.BRIGHTNESS -> {
                val v = (effect.params["amount"] ?: 0f) * 255f
                stack.add(ColorMatrix(floatArrayOf(1f,0f,0f,0f,v, 0f,1f,0f,0f,v, 0f,0f,1f,0f,v, 0f,0f,0f,1f,0f)))
            }
            EffectType.CONTRAST -> {
                val v = (effect.params["amount"] ?: 0f) + 1f
                val t = (1f - v) * 128f
                stack.add(ColorMatrix(floatArrayOf(v,0f,0f,0f,t, 0f,v,0f,0f,t, 0f,0f,v,0f,t, 0f,0f,0f,1f,0f)))
            }
            EffectType.SATURATION -> {
                val s = ColorMatrix().apply { setSaturation(effect.params["amount"] ?: 1f) }
                stack.add(s)
            }
            EffectType.HUE -> {
                val deg = effect.params["amount"] ?: 0f
                for (axis in 0..2) stack.add(ColorMatrix().apply { setRotate(axis, deg) })
            }
            EffectType.EXPOSURE -> {
                val k = Math.pow(2.0, (effect.params["amount"] ?: 0f).toDouble()).toFloat()
                stack.add(ColorMatrix(floatArrayOf(k,0f,0f,0f,0f, 0f,k,0f,0f,0f, 0f,0f,k,0f,0f, 0f,0f,0f,1f,0f)))
            }
            EffectType.SHARPEN -> {
                val a = (effect.params["amount"] ?: 0.5f)
                stack.add(ColorMatrix(floatArrayOf(
                    1f + a, -a/2, 0f, 0f, 0f,
                    -a/2, 1f + a, -a/2, 0f, 0f,
                    0f, -a/2, 1f + a, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )))
            }
            EffectType.COLOR_GRADING -> {
                val temp = effect.params["amount"] ?: 0f
                val tint = effect.params["strength"] ?: 0f
                val lift = effect.params["hue"] ?: 0f
                stack.add(ColorMatrix(floatArrayOf(
                    1f, 0f, 0f, 0f, temp * 40f + lift * 20f,
                    0f, 1f, 0f, 0f, tint * 20f,
                    0f, 0f, 1f, 0f, -temp * 40f + lift * 20f,
                    0f, 0f, 0f, 1f, 0f
                )))
            }
            else -> Unit
        }
        for (m in stack) cm.postConcat(m)
        p.colorFilter = ColorMatrixColorFilter(cm)
        c.drawBitmap(src, 0f, 0f, p)
        release(src)
        return out
    }

    private fun boxBlur(src: Bitmap, radius: Float, w: Int, h: Int): Bitmap {
        if (radius <= 0.5f) return src
        val scale = (1f / (1f + radius / 6f)).coerceIn(0.02f, 1f)
        val sw = max(1, (w * scale).toInt()); val sh = max(1, (h * scale).toInt())
        val small = Bitmap.createScaledBitmap(src, sw, sh, true)
        val out = newBitmap(w, h)
        val p = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        Canvas(out).drawBitmap(small, null, RectF(0f, 0f, w.toFloat(), h.toFloat()), p)
        small.recycle()
        release(src)
        return out
    }

    private fun directionalBlur(src: Bitmap, length: Float, angle: Float, w: Int, h: Int): Bitmap {
        if (length <= 0.5f) return src
        val out = newBitmap(w, h)
        val c = Canvas(out)
        val rad = Math.toRadians(angle.toDouble())
        val steps = 7
        val p = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        for (i in 0..steps) {
            val k = i / steps.toFloat()
            p.alpha = (110 * (1f - k * 0.5f)).toInt()
            val dx = cos(rad).toFloat() * length * k
            val dy = sin(rad).toFloat() * length * k
            c.drawBitmap(src, dx, dy, p)
        }
        release(src)
        return out
    }

    private fun glow(src: Bitmap, radius: Float, strength: Float, hue: Float, w: Int, h: Int): Bitmap {
        val blurred = boxBlur(clone(src), radius, w, h)
        val out = newBitmap(w, h)
        val c = Canvas(out)
        val p = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        p.alpha = (strength * 220).toInt().coerceIn(0, 255)
        val rgb = android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.85f, 0.7f))
        val cm = ColorMatrix(floatArrayOf(0f,0f,0f,0f, android.graphics.Color.red(rgb).toFloat(),
            0f,0f,0f,0f, android.graphics.Color.green(rgb).toFloat(),
            0f,0f,0f,0f, android.graphics.Color.blue(rgb).toFloat(),
            0f,0f,0f,1f,0f))
        p.colorFilter = ColorMatrixColorFilter(cm)
        c.drawBitmap(blurred, 0f, 0f, p)
        c.drawBitmap(src, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        blurred.recycle()
        release(src)
        return out
    }

    private fun shadow(src: Bitmap, p: Map<String, Float>, w: Int, h: Int): Bitmap {
        val blur = p["amount"] ?: 24f
        val blurred = boxBlur(clone(src), blur, w, h)
        val out = newBitmap(w, h)
        val c = Canvas(out)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        paint.alpha = ((p["strength"] ?: 0.65f) * 200).toInt().coerceIn(0, 255)
        paint.colorFilter = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
            0f,0f,0f,0f,0f, 0f,0f,0f,0f,0f, 0f,0f,0f,0f,0f, 0f,0f,0f,1f,0f)))
        c.drawBitmap(blurred, p["offsetX"] ?: 12f, p["offsetY"] ?: 18f, paint)
        c.drawBitmap(src, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        blurred.recycle()
        release(src)
        return out
    }

    private fun vignette(src: Bitmap, amount: Float, size: Float, w: Int, h: Int): Bitmap {
        val out = newBitmap(w, h)
        val c = Canvas(out)
        c.drawBitmap(src, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        val radius = max(w, h) * (0.35f + size * 0.55f)
        val shader = RadialGradient(
            w / 2f, h / 2f, radius,
            Color.argb(0, 0, 0, 0),
            Color.argb((amount * 255).toInt().coerceIn(0, 255), 0, 0, 0),
            Shader.TileMode.CLAMP
        )
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.shader = shader
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
        }
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        release(src)
        return out
    }

    private fun grain(src: Bitmap, amount: Float, timeMs: Long, w: Int, h: Int): Bitmap {
        if (amount <= 0f) return src
        val out = newBitmap(w, h)
        val c = Canvas(out)
        c.drawBitmap(src, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        val random = java.util.Random(timeMs / 40L)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val dots = (amount * 2600).toInt()
        for (i in 0 until dots) {
            val x = random.nextFloat() * w
            val y = random.nextFloat() * h
            p.color = Color.argb((amount * 70).toInt(), 255, 255, 255)
            c.drawPoint(x, y, p)
        }
        release(src)
        return out
    }

    private fun rgbSplit(src: Bitmap, offset: Float, w: Int, h: Int): Bitmap {
        if (offset <= 0f) return src
        val out = newBitmap(w, h)
        val c = Canvas(out)
        val p = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        p.xfermode = PorterDuffXfermode(PorterDuff.Mode.LIGHTEN)
        val red = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(1f,0f,0f,0f,0f, 0f,0f,0f,0f,0f, 0f,0f,0f,0f,0f, 0f,0f,0f,1f,0f)))
        val blue = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(0f,0f,0f,0f,0f, 0f,0f,0f,0f,0f, 0f,0f,1f,0f,0f, 0f,0f,0f,1f,0f)))
        p.colorFilter = red
        c.drawBitmap(src, -offset, 0f, p)
        p.colorFilter = blue
        c.drawBitmap(src, offset, 0f, p)
        c.drawBitmap(src, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        release(src)
        return out
    }

    private fun pixelate(src: Bitmap, size: Float, w: Int, h: Int): Bitmap {
        val block = size.coerceAtLeast(2f)
        val sw = max(1, (w / block).toInt()); val sh = max(1, (h / block).toInt())
        val small = Bitmap.createScaledBitmap(src, sw, sh, true)
        val out = newBitmap(w, h)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = false }
        Canvas(out).drawBitmap(small, null, RectF(0f, 0f, w.toFloat(), h.toFloat()), p)
        small.recycle()
        release(src)
        return out
    }

    private fun wave(src: Bitmap, amplitude: Float, frequency: Float, w: Int, h: Int): Bitmap {
        val out = newBitmap(w, h)
        val c = Canvas(out)
        val strip = 6
        var y = 0
        while (y < h) {
            val off = (sin(y.toDouble() / h * frequency * Math.PI * 2).toFloat() * amplitude)
            val hh = min(strip, h - y)
            c.drawBitmap(src, android.graphics.Rect(0, y, w, y + hh),
                RectF(off, y.toFloat(), w.toFloat() + off, (y + hh).toFloat()),
                Paint(Paint.FILTER_BITMAP_FLAG))
            y += strip
        }
        release(src)
        return out
    }

    private fun clone(src: Bitmap): Bitmap = Bitmap.createBitmap(src)

    private fun release(bmp: Bitmap) {
        // Never recycle the caller-owned scratch bitmap.
        if (bmp !== scratch && !bmp.isRecycled) bmp.recycle()
    }

    // ------------------------------------------------------------------ helpers
    private fun gradient(a: String, bHex: Int, type: GradientType, angle: Float, w: Float, h: Float): Shader {
        val c1 = ColorUtil.parse(a)
        val c2 = bHex
        if (type == GradientType.SOLID) return android.graphics.LinearGradient(0f, 0f, 1f, 0f, c1, c1, Shader.TileMode.CLAMP)
        if (type == GradientType.RADIAL) return RadialGradient(0f, 0f, max(w, h) / 2f, c1, c2, Shader.TileMode.CLAMP)
        val rad = Math.toRadians(angle.toDouble())
        val dx = cos(rad).toFloat(); val dy = sin(rad).toFloat()
        val len = (abs(w * dx) + abs(h * dy)) / 2f
        return LinearGradient(-dx * len, -dy * len, dx * len, dy * len, c1, c2, Shader.TileMode.CLAMP)
    }

    private fun typeface(family: String, weight: Int, italic: Boolean): Typeface {
        val base = when (family) {
            "serif" -> Typeface.SERIF
            "monospace" -> Typeface.MONOSPACE
            else -> Typeface.DEFAULT
        }
        val style = if (italic) Typeface.ITALIC else if (weight >= 700) Typeface.BOLD else Typeface.NORMAL
        return Typeface.create(base, style)
    }

    private fun applyBlend(paint: Paint, blend: ProjectBlendMode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            paint.blendMode = when (blend) {
                ProjectBlendMode.NORMAL -> BlendMode.SRC_OVER
                ProjectBlendMode.MULTIPLY -> BlendMode.MULTIPLY
                ProjectBlendMode.SCREEN -> BlendMode.SCREEN
                ProjectBlendMode.OVERLAY -> BlendMode.OVERLAY
                ProjectBlendMode.DARKEN -> BlendMode.DARKEN
                ProjectBlendMode.LIGHTEN -> BlendMode.LIGHTEN
                ProjectBlendMode.ADD -> BlendMode.PLUS
                ProjectBlendMode.DIFFERENCE -> BlendMode.DIFFERENCE
            }
        } else {
            paint.xfermode = PorterDuffXfermode(
                when (blend) {
                    ProjectBlendMode.NORMAL -> PorterDuff.Mode.SRC_OVER
                    ProjectBlendMode.MULTIPLY -> PorterDuff.Mode.MULTIPLY
                    ProjectBlendMode.SCREEN -> PorterDuff.Mode.SCREEN
                    ProjectBlendMode.OVERLAY -> PorterDuff.Mode.OVERLAY
                    ProjectBlendMode.DARKEN -> PorterDuff.Mode.DARKEN
                    ProjectBlendMode.LIGHTEN -> PorterDuff.Mode.LIGHTEN
                    ProjectBlendMode.ADD -> PorterDuff.Mode.ADD
                    ProjectBlendMode.DIFFERENCE -> PorterDuff.Mode.LIGHTEN
                }
            )
        }
    }

    private fun shapePath(spec: ShapeSpec, w: Float, h: Float): Path {
        val p = Path()
        val rect = RectF(-w / 2f, -h / 2f, w / 2f, h / 2f)
        fun polygon(points: List<Pair<Float, Float>>) {
            points.forEachIndexed { i, point ->
                if (i == 0) p.moveTo(point.first, point.second) else p.lineTo(point.first, point.second)
            }
            p.close()
        }
        fun radial(points: Int, inner: Float = 0.48f) {
            val n = points.coerceIn(3, 32) * 2
            for (i in 0 until n) {
                val a = -Math.PI / 2.0 + i * Math.PI * 2.0 / n
                val r = if (i % 2 == 0) 1f else inner
                val x = (cos(a) * w * 0.5 * r).toFloat()
                val y = (sin(a) * h * 0.5 * r).toFloat()
                if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
            }
            p.close()
        }
        when (spec.kind) {
            ShapeKind.RECTANGLE -> p.addRoundRect(rect,
                spec.cornerRadius.coerceAtMost(min(w, h) / 2f),
                spec.cornerRadius.coerceAtMost(min(w, h) / 2f), Path.Direction.CW)
            ShapeKind.ROUNDED_RECTANGLE -> p.addRoundRect(rect,
                if (spec.cornerRadius > 0f) spec.cornerRadius.coerceAtMost(min(w, h) / 2f) else min(w, h) * 0.12f,
                if (spec.cornerRadius > 0f) spec.cornerRadius.coerceAtMost(min(w, h) / 2f) else min(w, h) * 0.12f,
                Path.Direction.CW)
            ShapeKind.CIRCLE -> p.addCircle(0f, 0f, min(w, h) / 2f, Path.Direction.CW)
            ShapeKind.TRIANGLE -> polygon(listOf(0f to -h / 2f, w / 2f to h / 2f, -w / 2f to h / 2f))
            ShapeKind.POLYGON, ShapeKind.STAR -> {
                val n = if (spec.kind == ShapeKind.STAR) spec.starPoints.coerceIn(3, 20) * 2
                    else spec.sides.coerceIn(3, 32)
                for (i in 0 until n) {
                    val a = -Math.PI / 2 + i * Math.PI * 2 / n
                    val r = if (spec.kind == ShapeKind.STAR && i % 2 == 1) spec.innerRadius.coerceIn(0.05f, 0.95f) else 1f
                    val x = (cos(a) * w / 2 * r).toFloat(); val y = (sin(a) * h / 2 * r).toFloat()
                    if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                }
                p.close()
            }
            ShapeKind.LINE -> { p.moveTo(-w / 2f, 0f); p.lineTo(w / 2f, 0f) }
            ShapeKind.ARC -> p.arcTo(rect, -55f, 290f, true)
            ShapeKind.CROSS -> {
                p.addRect(-w * 0.17f, -h / 2f, w * 0.17f, h / 2f, Path.Direction.CW)
                p.addRect(-w / 2f, -h * 0.17f, w / 2f, h * 0.17f, Path.Direction.CW)
            }
            ShapeKind.CALLOUT -> {
                p.addRoundRect(RectF(-w * 0.45f, -h * 0.42f, w * 0.45f, h * 0.28f),
                    min(w, h) * 0.08f, min(w, h) * 0.08f, Path.Direction.CW)
                p.moveTo(-w * 0.20f, h * 0.26f); p.lineTo(-w * 0.28f, h * 0.48f)
                p.lineTo(0f, h * 0.27f); p.close()
            }
            ShapeKind.PIE, ShapeKind.SECTOR -> {
                p.moveTo(0f, 0f); p.arcTo(rect, -90f, if (spec.kind == ShapeKind.PIE) 275f else 90f, false); p.close()
            }
            ShapeKind.HEXAGON -> polygon((0 until 6).map { i ->
                val a = -Math.PI / 2 + i * Math.PI / 3
                (cos(a) * w / 2).toFloat() to (sin(a) * h / 2).toFloat()
            })
            ShapeKind.FLOWER -> {
                val r = min(w, h) * 0.25f
                p.addCircle(0f, -h * 0.22f, r, Path.Direction.CW)
                p.addCircle(w * 0.22f, 0f, r, Path.Direction.CW)
                p.addCircle(0f, h * 0.22f, r, Path.Direction.CW)
                p.addCircle(-w * 0.22f, 0f, r, Path.Direction.CW)
                p.addCircle(0f, 0f, r * 0.62f, Path.Direction.CW)
            }
            ShapeKind.ARROW -> polygon(listOf(
                -w / 2f to -h * 0.16f, w * 0.12f to -h * 0.16f, w * 0.12f to -h * 0.40f,
                w / 2f to 0f, w * 0.12f to h * 0.40f, w * 0.12f to h * 0.16f,
                -w / 2f to h * 0.16f))
            ShapeKind.TRAPEZOID -> polygon(listOf(-w * 0.32f to -h / 2f, w * 0.32f to -h / 2f,
                w / 2f to h / 2f, -w / 2f to h / 2f))
            ShapeKind.CRESCENT -> {
                p.addCircle(0f, 0f, min(w, h) * 0.48f, Path.Direction.CW)
                val cut = Path().apply { addCircle(w * 0.20f, -h * 0.08f, min(w, h) * 0.42f, Path.Direction.CW) }
                p.op(cut, Path.Op.DIFFERENCE)
            }
            ShapeKind.BEAM -> polygon(listOf(-w * 0.48f to h * 0.30f, -w * 0.28f to h * 0.48f,
                w * 0.48f to -h * 0.30f, w * 0.28f to -h * 0.48f))
            ShapeKind.FLAG -> polygon(listOf(-w * 0.38f to h * 0.48f, -w * 0.38f to -h * 0.48f,
                w * 0.40f to -h * 0.48f, w * 0.18f to 0f, w * 0.02f to h * 0.16f,
                -w * 0.12f to h * 0.48f))
            ShapeKind.SEAL, ShapeKind.BURST -> radial(if (spec.kind == ShapeKind.SEAL) 16 else 12, 0.82f)
            ShapeKind.HEART -> {
                val s = min(w, h) / 2f
                p.moveTo(0f, s * 0.75f)
                p.cubicTo(-s * 1.4f, -s * 0.1f, -s * 0.6f, -s * 1.1f, 0f, -s * 0.35f)
                p.cubicTo(s * 0.6f, -s * 1.1f, s * 1.4f, -s * 0.1f, 0f, s * 0.75f)
                p.close()
            }
            ShapeKind.RING -> {
                p.addCircle(0f, 0f, min(w, h) / 2f, Path.Direction.CW)
                val cut = Path().apply { addCircle(0f, 0f, min(w, h) * 0.28f, Path.Direction.CW) }
                p.op(cut, Path.Op.DIFFERENCE)
            }
            ShapeKind.HALF_CIRCLE -> {
                p.moveTo(-w / 2f, 0f); p.arcTo(rect, 180f, 180f, false); p.close()
            }
            ShapeKind.DIAMOND -> polygon(listOf(0f to -h / 2f, w / 2f to 0f, 0f to h / 2f, -w / 2f to 0f))
            ShapeKind.PENTAGON -> polygon((0 until 5).map { i ->
                val a = -Math.PI / 2 + i * Math.PI * 2 / 5
                (cos(a) * w / 2).toFloat() to (sin(a) * h / 2).toFloat()
            })
            ShapeKind.SPARKLE -> radial(4, 0.10f)
            ShapeKind.OCTAGON -> polygon((0 until 8).map { i ->
                val a = -Math.PI / 8 + i * Math.PI / 4
                (cos(a) * w / 2).toFloat() to (sin(a) * h / 2).toFloat()
            })
            ShapeKind.LEAF -> {
                p.moveTo(-w / 2f, h / 2f)
                p.cubicTo(-w * 0.45f, -h * 0.45f, w * 0.35f, -h * 0.55f, w / 2f, -h / 2f)
                p.cubicTo(w * 0.45f, h * 0.38f, -w * 0.10f, h * 0.55f, -w / 2f, h / 2f)
                p.close()
            }
            ShapeKind.ARROWHEAD -> polygon(listOf(-w / 2f to -h * 0.23f, w * 0.10f to -h * 0.23f,
                w * 0.10f to -h / 2f, w / 2f to 0f, w * 0.10f to h / 2f,
                w * 0.10f to h * 0.23f, -w / 2f to h * 0.23f))
            ShapeKind.CROWN -> polygon(listOf(-w / 2f to -h * 0.30f, -w * 0.25f to 0f,
                0f to -h / 2f, w * 0.25f to 0f, w / 2f to -h * 0.30f,
                w * 0.42f to h / 2f, -w * 0.42f to h / 2f))
            ShapeKind.HOURGLASS -> polygon(listOf(-w / 2f to -h / 2f, w / 2f to -h / 2f,
                w * 0.12f to 0f, w / 2f to h / 2f, -w / 2f to h / 2f, -w * 0.12f to 0f))
            ShapeKind.NOTCHED_SQUARE -> polygon(listOf(-w / 2f to -h / 2f, w / 2f to -h / 2f,
                w / 2f to h / 2f, w * 0.15f to h / 2f, 0f to h * 0.28f,
                -w * 0.15f to h / 2f, -w / 2f to h / 2f))
            ShapeKind.WAVE -> {
                p.moveTo(-w / 2f, -h * 0.20f)
                p.cubicTo(-w * 0.25f, -h * 0.50f, -w * 0.15f, h * 0.20f, 0f, -h * 0.08f)
                p.cubicTo(w * 0.20f, -h * 0.42f, w * 0.30f, h * 0.14f, w / 2f, -h * 0.14f)
                p.lineTo(w / 2f, h / 2f); p.lineTo(-w / 2f, h / 2f); p.close()
            }
        }
        return p
    }

    private fun vectorPath(spec: VectorSpec): Path {
        val p = Path()
        for (cmd in spec.commands) {
            val a = cmd.args
            when (cmd.op.uppercase()) {
                "M" -> if (a.size >= 2) p.moveTo(a[0], a[1])
                "L" -> if (a.size >= 2) p.lineTo(a[0], a[1])
                "Q" -> if (a.size >= 4) p.quadTo(a[0], a[1], a[2], a[3])
                "C" -> if (a.size >= 6) p.cubicTo(a[0], a[1], a[2], a[3], a[4], a[5])
                "Z" -> p.close()
            }
        }
        if (spec.closed) p.close()
        return p
    }

    private companion object { const val TAG = "EditorsHubCompositor" }

}
