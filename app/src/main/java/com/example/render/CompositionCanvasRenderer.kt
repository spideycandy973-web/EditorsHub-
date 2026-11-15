package com.example.render

import android.content.Context
import android.graphics.*
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import com.example.R
import com.example.engine.AnimationEngine
import com.example.engine.EvaluatedCamera
import com.example.model.*
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.*
import kotlin.random.Random

object CompositionCanvasRenderer {

    private val imageCache = ConcurrentHashMap<String, Bitmap>()
    private val videoThumbCache = ConcurrentHashMap<String, Bitmap>()
    private val customFontCache = ConcurrentHashMap<String, Typeface>()

    fun clearCustomFontCache() {
        customFontCache.clear()
    }

    fun parseColorSafe(hex: String, fallback: Int = Color.WHITE): Int {
        return try {
            Color.parseColor(if (hex.startsWith("#")) hex else "#$hex")
        } catch (_: Exception) {
            fallback
        }
    }

    fun getTypeface(context: Context, family: String, weight: Int, italic: Boolean): Typeface {
        val style = when {
            weight >= 650 && italic -> Typeface.BOLD_ITALIC
            weight >= 650 -> Typeface.BOLD
            italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        val customDir = File(context.filesDir, "fonts")
        if (customDir.exists()) {
            val cached = customFontCache[family.lowercase()]
            if (cached != null) return Typeface.create(cached, style)
            val matchedFile = customDir.listFiles()?.firstOrNull { f ->
                f.nameWithoutExtension.equals(family, ignoreCase = true)
            }
            if (matchedFile != null) {
                try {
                    val tf = Typeface.createFromFile(matchedFile)
                    customFontCache[family.lowercase()] = tf
                    return Typeface.create(tf, style)
                } catch (_: Exception) {
                }
            }
        }

        val resId = when (family.lowercase()) {
            "space grotesk" -> R.font.space_grotesk
            "roboto mono" -> R.font.roboto_mono
            "playfair display" -> R.font.playfair_display
            "montserrat" -> R.font.montserrat
            "pacifico" -> R.font.pacifico
            "oswald" -> R.font.oswald
            "inter" -> R.font.inter
            else -> R.font.inter
        }
        val base = try {
            ResourcesCompat.getFont(context, resId) ?: Typeface.SANS_SERIF
        } catch (_: Exception) {
            Typeface.SANS_SERIF
        }
        return Typeface.create(base, style)
    }

    fun isMediaUriAccessible(context: Context, uriStr: String?): Boolean {
        if (uriStr.isNullOrBlank()) return false
        return try {
            val uri = Uri.parse(uriStr)
            if (uri.scheme == "file") {
                val f = File(uri.path ?: "")
                f.exists() && f.length() > 0
            } else {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false
            }
        } catch (_: Exception) {
            false
        }
    }

    fun loadBitmapForLayer(context: Context, uriStr: String?, isVideo: Boolean, localTimeSec: Float): Bitmap? {
        if (uriStr.isNullOrBlank()) return null
        if (!isVideo) {
            imageCache[uriStr]?.let { return it }
            return try {
                val uri = Uri.parse(uriStr)
                val stream = if (uri.scheme == "file") File(uri.path!!).inputStream()
                else context.contentResolver.openInputStream(uri)
                stream?.use { s ->
                    val bmp = BitmapFactory.decodeStream(s)
                    if (bmp != null) {
                        if (imageCache.size > 32) imageCache.clear()
                        imageCache[uriStr] = bmp
                    }
                    bmp
                }
            } catch (_: Exception) {
                null
            }
        } else {
            val bucketMs = ((localTimeSec * 4f).roundToInt() * 250L).coerceAtLeast(0L)
            val key = "$uriStr@$bucketMs"
            videoThumbCache[key]?.let { return it }
            return try {
                val retriever = MediaMetadataRetriever()
                val uri = Uri.parse(uriStr)
                if (uri.scheme == "file") retriever.setDataSource(uri.path)
                else retriever.setDataSource(context, uri)
                val bmp = retriever.getFrameAtTime(bucketMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                retriever.release()
                if (bmp != null) {
                    if (videoThumbCache.size > 24) videoThumbCache.clear()
                    videoThumbCache[key] = bmp
                }
                bmp
            } catch (_: Exception) {
                null
            }
        }
    }

    fun sampleColorAt(context: Context, doc: ProjectDocument, timeSec: Float, canvasX: Float, canvasY: Float): Int {
        val sampleW = 270
        val sampleH = ((doc.project.height.toFloat() / doc.project.width.coerceAtLeast(1)) * sampleW).roundToInt().coerceIn(64, 480)
        val bmp = Bitmap.createBitmap(sampleW, sampleH, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.scale(sampleW.toFloat() / doc.project.width.coerceAtLeast(1), sampleH.toFloat() / doc.project.height.coerceAtLeast(1))
        render(context, c, doc, timeSec, selectedLayerId = null, showGuides = false, cameraViewActive = true)
        val px = ((canvasX / doc.project.width.coerceAtLeast(1)) * sampleW).roundToInt().coerceIn(0, sampleW - 1)
        val py = ((canvasY / doc.project.height.coerceAtLeast(1)) * sampleH).roundToInt().coerceIn(0, sampleH - 1)
        val pixel = bmp.getPixel(px, py)
        bmp.recycle()
        return pixel
    }

    fun render(
        context: Context,
        canvas: Canvas,
        doc: ProjectDocument,
        timeSec: Float,
        selectedLayerId: String? = null,
        showGuides: Boolean = true,
        cameraViewActive: Boolean = true
    ) {
        val project = doc.project
        val w = project.width.toFloat().coerceAtLeast(1f)
        val h = project.height.toFloat().coerceAtLeast(1f)

        // 1. Background
        val bgPaint = Paint().apply {
            color = parseColorSafe(project.bgHex, Color.parseColor("#10121D"))
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        // Optional grid
        if (showGuides && project.gridEnabled) {
            drawGrid(canvas, w, h)
        }

        // 2. Active Camera (with Orbit, LensMode, DoF)
        val evalCam: EvaluatedCamera? = if (cameraViewActive) {
            AnimationEngine.evaluateActiveCamera(doc.layers, w, h, timeSec)
        } else null
        val defaultCamZ = h / (2f * tan(Math.toRadians(27.5)).toFloat())
        val camFov = evalCam?.fov ?: 55f
        val focal = (h / (2f * tan(Math.toRadians((camFov * 0.5f).toDouble())).toFloat()).coerceAtLeast(0.05f)) *
            (evalCam?.focalMultiplier ?: 1f)

        // Visible layers at timeSec, sorted by Z if 3D active
        val visibleLayers = doc.layers.filter { layer ->
            !layer.hidden &&
                layer.type != LayerType.AUDIO &&
                timeSec >= layer.start &&
                timeSec <= layer.start + layer.duration
        }

        val orderedLayers = if (project.space3dActive || evalCam != null) {
            visibleLayers.sortedBy { AnimationEngine.resolveWorldTransform(it, doc.layers, timeSec).z }
        } else {
            visibleLayers
        }

        // Track Matte source identification: layers acting as matte providers won't draw standalone if assigned as matteTarget
        val consumedAsMatteIds = orderedLayers
            .mapNotNull { it.mask.takeIf { m -> m.matteMode != MatteModeKind.NONE }?.matteTargetId }
            .toSet()

        orderedLayers.forEachIndexed { idx, layer ->
            if (layer.type == LayerType.CAMERA) {
                if (showGuides && layer.id == selectedLayerId) {
                    drawCameraIndicator(canvas, w, h)
                }
                return@forEachIndexed
            }
            if (layer.id in consumedAsMatteIds && layer.id != selectedLayerId) {
                return@forEachIndexed
            }

            val tr = AnimationEngine.resolveWorldTransform(layer, doc.layers, timeSec)
            if (tr.opacity <= 0.005f) return@forEachIndexed

            // Compute DoF blur factor
            var dofBlurRadius = 0f
            if (evalCam != null && evalCam.dofEnabled) {
                val layerDepthFromCam = abs((evalCam.z - tr.z) - evalCam.focusDistance)
                dofBlurRadius = ((layerDepthFromCam / 160f) * (5.6f / evalCam.aperture.coerceAtLeast(1.2f))).coerceIn(0f, 36f)
            }

            val matteActive = layer.mask.matteMode != MatteModeKind.NONE
            val saveLayerId = if (matteActive) {
                canvas.saveLayer(0f, 0f, w, h, null)
            } else -1

            // Repeat instances (linear / radial / grid)
            val instances = buildRepeatOffsets(layer.repeat)
            for (rep in instances) {
                val rx = tr.x + rep.dx
                val ry = tr.y + rep.dy
                val rsx = tr.scaleX * rep.scale
                val rsy = tr.scaleY * rep.scale
                val rrot = tr.rotation + rep.dRot

                val projected = projectToCamera(rx, ry, tr.z, w, h, evalCam, defaultCamZ, focal, layer.space3d || project.space3dActive)

                canvas.save()
                canvas.translate(projected.first, projected.second)
                canvas.rotate(rrot + tr.rotZ)
                val finalSx = rsx * projected.third * cos(Math.toRadians(tr.rotY.toDouble())).toFloat().let {
                    if (abs(it) < 0.08f) 0.08f * sign(it.takeIf { s -> s != 0f } ?: 1f) else it
                }
                val finalSy = rsy * projected.third * cos(Math.toRadians(tr.rotX.toDouble())).toFloat().let {
                    if (abs(it) < 0.08f) 0.08f * sign(it.takeIf { s -> s != 0f } ?: 1f) else it
                }
                canvas.scale(finalSx, finalSy)
                if (layer.skewX != 0f || layer.skewY != 0f) {
                    canvas.skew(tan(Math.toRadians(layer.skewX.toDouble())).toFloat(), tan(Math.toRadians(layer.skewY.toDouble())).toFloat())
                }

                drawLayerContent(
                    context = context,
                    canvas = canvas,
                    doc = doc,
                    layer = layer,
                    timeSec = timeSec,
                    opacity = tr.opacity,
                    isSelected = showGuides && layer.id == selectedLayerId,
                    extraBlur = dofBlurRadius
                )
                canvas.restore()
            }

            if (matteActive && saveLayerId >= 0) {
                val matteSource = if (layer.mask.matteTargetId != null) {
                    doc.layers.find { it.id == layer.mask.matteTargetId }
                } else {
                    orderedLayers.getOrNull(idx - 1)
                }
                if (matteSource != null && matteSource.type != LayerType.AUDIO && matteSource.type != LayerType.CAMERA) {
                    val mattePaint = Paint().apply {
                        xfermode = PorterDuffXfermode(
                            if (layer.mask.matteMode == MatteModeKind.EXCLUDE) PorterDuff.Mode.DST_OUT else PorterDuff.Mode.DST_IN
                        )
                    }
                    canvas.saveLayer(0f, 0f, w, h, mattePaint)
                    val mtr = AnimationEngine.resolveWorldTransform(matteSource, doc.layers, timeSec)
                    val mProj = projectToCamera(mtr.x, mtr.y, mtr.z, w, h, evalCam, defaultCamZ, focal, matteSource.space3d || project.space3dActive)
                    canvas.save()
                    canvas.translate(mProj.first, mProj.second)
                    canvas.rotate(mtr.rotation + mtr.rotZ)
                    canvas.scale(mtr.scaleX * mProj.third, mtr.scaleY * mProj.third)
                    drawLayerContent(context, canvas, doc, matteSource, timeSec, 1f, false, 0f)
                    canvas.restore()
                    canvas.restore()
                }
                canvas.restoreToCount(saveLayerId)
            }

            // Selection bounding box in screen coordinates
            if (showGuides && layer.id == selectedLayerId) {
                val proj = projectToCamera(tr.x, tr.y, tr.z, w, h, evalCam, defaultCamZ, focal, layer.space3d || project.space3dActive)
                drawSelectionOverlay(canvas, layer, proj.first, proj.second, tr.scaleX * proj.third, tr.scaleY * proj.third, tr.rotation + tr.rotZ)
            }
        }
    }

    private data class RepeatOffset(val dx: Float, val dy: Float, val dRot: Float, val scale: Float)

    private fun buildRepeatOffsets(repeat: RepeatConfig): List<RepeatOffset> {
        return when (repeat.mode) {
            RepeatModeKind.NONE -> listOf(RepeatOffset(0f, 0f, 0f, 1f))
            RepeatModeKind.LINEAR -> {
                val c = repeat.count.coerceIn(1, 24)
                (0 until c).map { i ->
                    RepeatOffset(
                        dx = i * repeat.offsetX,
                        dy = i * repeat.offsetY,
                        dRot = i * repeat.offsetRotation,
                        scale = repeat.offsetScale.pow(i).coerceIn(0.15f, 3f)
                    )
                }
            }
            RepeatModeKind.RADIAL -> {
                val c = repeat.count.coerceIn(2, 24)
                (0 until c).map { i ->
                    val ang = (2f * PI.toFloat() * i) / c
                    RepeatOffset(
                        dx = cos(ang) * repeat.radialRadius,
                        dy = sin(ang) * repeat.radialRadius,
                        dRot = (360f * i) / c + repeat.offsetRotation,
                        scale = 1f
                    )
                }
            }
            RepeatModeKind.GRID -> {
                val cols = repeat.cols.coerceIn(1, 6)
                val rows = repeat.rows.coerceIn(1, 6)
                val list = mutableListOf<RepeatOffset>()
                val ox = repeat.offsetX.coerceAtLeast(30f)
                val oy = repeat.offsetY.takeIf { abs(it) > 5f } ?: ox
                for (r in 0 until rows) {
                    for (c in 0 until cols) {
                        list.add(
                            RepeatOffset(
                                dx = (c - (cols - 1) * 0.5f) * ox,
                                dy = (r - (rows - 1) * 0.5f) * oy,
                                dRot = repeat.offsetRotation,
                                scale = 1f
                            )
                        )
                    }
                }
                list
            }
        }
    }

    private fun projectToCamera(
        trX: Float,
        trY: Float,
        trZ: Float,
        w: Float,
        h: Float,
        evalCam: EvaluatedCamera?,
        defaultCamZ: Float,
        focal: Float,
        is3d: Boolean
    ): Triple<Float, Float, Float> {
        if (evalCam == null && !is3d) return Triple(trX, trY, 1f)
        val camX = evalCam?.x ?: 0f
        val camY = evalCam?.y ?: 0f
        val camZ = evalCam?.z ?: defaultCamZ
        val camRotZ = evalCam?.rotZ ?: 0f
        val depthDist = (camZ - trZ).coerceAtLeast(60f)
        val perspScale = (focal / depthDist).coerceIn(0.05f, 12f)
        val dx = trX - w * 0.5f - camX
        val dy = trY - h * 0.5f - camY
        val rad = Math.toRadians((-camRotZ).toDouble())
        val rx = (dx * cos(rad) - dy * sin(rad)).toFloat()
        val ry = (dx * sin(rad) + dy * cos(rad)).toFloat()
        return Triple(w * 0.5f + rx * perspScale, h * 0.5f + ry * perspScale, perspScale)
    }

    private fun drawGrid(canvas: Canvas, w: Float, h: Float) {
        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(26, 255, 255, 255)
            strokeWidth = 1.5f
        }
        val step = (min(w, h) / 8f).coerceAtLeast(48f)
        var x = w * 0.5f
        while (x < w) { canvas.drawLine(x, 0f, x, h, gridPaint); x += step }
        x = w * 0.5f - step
        while (x > 0) { canvas.drawLine(x, 0f, x, h, gridPaint); x -= step }
        var y = h * 0.5f
        while (y < h) { canvas.drawLine(0f, y, w, y, gridPaint); y += step }
        y = h * 0.5f - step
        while (y > 0) { canvas.drawLine(0f, y, w, y, gridPaint); y -= step }
        val centerPaint = Paint().apply {
            color = Color.argb(46, 0, 215, 167)
            strokeWidth = 2f
        }
        canvas.drawLine(w * 0.5f, 0f, w * 0.5f, h, centerPaint)
        canvas.drawLine(0f, h * 0.5f, w, h * 0.5f, centerPaint)
    }

    private fun drawCameraIndicator(canvas: Canvas, w: Float, h: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#94A2FF")
            style = Paint.Style.STROKE
            strokeWidth = 4f
            pathEffect = DashPathEffect(floatArrayOf(18f, 12f), 0f)
        }
        canvas.drawRect(24f, 24f, w - 24f, h - 24f, p)
    }

    private fun applyBlendMode(paint: Paint, blend: BlendModeKind) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            paint.blendMode = when (blend) {
                BlendModeKind.NORMAL -> BlendMode.SRC_OVER
                BlendModeKind.MULTIPLY -> BlendMode.MULTIPLY
                BlendModeKind.SCREEN -> BlendMode.SCREEN
                BlendModeKind.OVERLAY -> BlendMode.OVERLAY
                BlendModeKind.DARKEN -> BlendMode.DARKEN
                BlendModeKind.LIGHTEN -> BlendMode.LIGHTEN
                BlendModeKind.ADD -> BlendMode.PLUS
                BlendModeKind.DIFFERENCE -> BlendMode.DIFFERENCE
            }
        } else {
            @Suppress("DEPRECATION")
            paint.xfermode = when (blend) {
                BlendModeKind.NORMAL -> null
                BlendModeKind.MULTIPLY -> PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
                BlendModeKind.SCREEN -> PorterDuffXfermode(PorterDuff.Mode.SCREEN)
                BlendModeKind.OVERLAY -> PorterDuffXfermode(PorterDuff.Mode.OVERLAY)
                BlendModeKind.DARKEN -> PorterDuffXfermode(PorterDuff.Mode.DARKEN)
                BlendModeKind.LIGHTEN -> PorterDuffXfermode(PorterDuff.Mode.LIGHTEN)
                BlendModeKind.ADD -> PorterDuffXfermode(PorterDuff.Mode.ADD)
                BlendModeKind.DIFFERENCE -> PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)
            }
        }
    }

    private fun buildColorFilter(layer: LayerData): ColorFilter? {
        var brightness = 0f
        var contrast = 1f
        var hue = 0f
        var sat = 1f
        var invertAmt = 0f
        var posterizeLevels = 0f
        var exposure = 0f
        var hasAdjust = false

        for (fx in layer.effects) {
            if (!fx.enabled) continue
            when (fx.specId) {
                "brightness_contrast" -> {
                    brightness += (fx.params["brightness"] ?: 0f) * 2.55f
                    contrast *= (1f + (fx.params["contrast"] ?: 0f) / 100f).coerceIn(0f, 3f)
                    hasAdjust = true
                }
                "hue_saturation" -> {
                    hue += fx.params["hue"] ?: 0f
                    sat *= ((fx.params["saturation"] ?: 100f) / 100f).coerceIn(0f, 3f)
                    hasAdjust = true
                }
                "exposure_gamma" -> {
                    exposure = fx.params["exposure"] ?: 0f
                    val gain = 2f.pow(exposure)
                    contrast *= gain
                    hasAdjust = true
                }
                "invert" -> {
                    invertAmt = ((fx.params["amount"] ?: 100f) / 100f).coerceIn(0f, 1f)
                    hasAdjust = true
                }
                "posterize" -> {
                    posterizeLevels = fx.params["levels"] ?: 6f
                    hasAdjust = true
                }
            }
        }
        if (!hasAdjust) return null
        val cm = ColorMatrix()
        if (sat != 1f) cm.postConcat(ColorMatrix().apply { setSaturation(sat) })
        if (contrast != 1f || brightness != 0f) {
            val t = (1f - contrast) * 128f + brightness
            cm.postConcat(
                ColorMatrix(
                    floatArrayOf(
                        contrast, 0f, 0f, 0f, t,
                        0f, contrast, 0f, 0f, t,
                        0f, 0f, contrast, 0f, t,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
        }
        if (posterizeLevels >= 2f) {
            val boost = (1f + (24f - posterizeLevels) * 0.08f).coerceIn(1f, 2.4f)
            val off = (1f - boost) * 128f
            cm.postConcat(
                ColorMatrix(
                    floatArrayOf(
                        boost, 0f, 0f, 0f, off,
                        0f, boost, 0f, 0f, off,
                        0f, 0f, boost, 0f, off,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
        }
        if (hue != 0f) {
            val angle = Math.toRadians(hue.toDouble())
            val cosA = cos(angle).toFloat()
            val sinA = sin(angle).toFloat()
            cm.postConcat(
                ColorMatrix(
                    floatArrayOf(
                        0.213f + cosA * 0.787f - sinA * 0.213f, 0.715f - cosA * 0.715f - sinA * 0.715f, 0.072f - cosA * 0.072f + sinA * 0.928f, 0f, 0f,
                        0.213f - cosA * 0.213f + sinA * 0.143f, 0.715f + cosA * 0.285f + sinA * 0.140f, 0.072f - cosA * 0.072f - sinA * 0.283f, 0f, 0f,
                        0.213f - cosA * 0.213f - sinA * 0.787f, 0.715f - cosA * 0.715f + sinA * 0.715f, 0.072f + cosA * 0.928f + sinA * 0.072f, 0f, 0f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
        }
        if (invertAmt > 0.01f) {
            val s = 1f - 2f * invertAmt
            val o = 255f * invertAmt
            cm.postConcat(
                ColorMatrix(
                    floatArrayOf(
                        s, 0f, 0f, 0f, o,
                        0f, s, 0f, 0f, o,
                        0f, 0f, s, 0f, o,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
        }
        return ColorMatrixColorFilter(cm)
    }

    private fun drawLayerContent(
        context: Context,
        canvas: Canvas,
        doc: ProjectDocument,
        layer: LayerData,
        timeSec: Float,
        opacity: Float,
        isSelected: Boolean,
        extraBlur: Float
    ) {
        val alphaInt = (opacity * 255).roundToInt().coerceIn(0, 255)
        val w = AnimationEngine.evaluateProperty(layer, "w", timeSec)
        val h = AnimationEngine.evaluateProperty(layer, "h", timeSec)
        val radius = AnimationEngine.evaluateProperty(layer, "radius", timeSec)
        val strokeW = AnimationEngine.evaluateProperty(layer, "strokeWidth", timeSec)
        val localTime = (timeSec - layer.start).coerceAtLeast(0f)

        val blurFx = layer.effects.firstOrNull { it.enabled && it.specId == "gaussian_blur" }
        val motionBlurFx = layer.effects.firstOrNull { it.enabled && it.specId == "directional_motion_blur" }
        val zoomBlurFx = layer.effects.firstOrNull { it.enabled && it.specId == "zoom_motion_blur" }
        val spinBlurFx = layer.effects.firstOrNull { it.enabled && it.specId == "spin_motion_blur" }
        val glowFx = layer.effects.firstOrNull { it.enabled && it.specId == "glow" }
        val chromaFx = layer.effects.firstOrNull { it.enabled && it.specId == "chromatic_aberration" }
        val shadowFx = layer.effects.firstOrNull { it.enabled && it.specId == "drop_shadow" }
        val vignetteFx = layer.effects.firstOrNull { it.enabled && it.specId == "vignette" }
        val waveFx = layer.effects.firstOrNull { it.enabled && it.specId == "wave_warp" }
        val mirrorFx = layer.effects.firstOrNull { it.enabled && it.specId == "mirror" }
        val pixelFx = layer.effects.firstOrNull { it.enabled && it.specId == "pixelate" }
        val scanFx = layer.effects.firstOrNull { it.enabled && it.specId == "scanlines" }
        val particlesFx = layer.effects.firstOrNull { it.enabled && it.specId == "particles" }
        val spectrumFx = layer.effects.firstOrNull { it.enabled && it.specId == "audio_spectrum" }

        if (waveFx != null) {
            val amp = (waveFx.params["amplitude"] ?: 16f) * 0.004f
            val freq = waveFx.params["frequency"] ?: 3f
            val spd = waveFx.params["speed"] ?: 2f
            canvas.skew(sin(localTime * spd * 3f + freq) * amp, 0f)
        }

        if (particlesFx != null) {
            drawParticlesEffect(canvas, w, h, localTime, particlesFx, layer.colorHex, alphaInt)
        }
        if (spectrumFx != null) {
            drawAudioSpectrumEffect(canvas, doc, w, h, timeSec, localTime, spectrumFx, layer.colorHex, alphaInt)
            if (layer.type == LayerType.SHAPE && layer.colorHex.equals("#000000", true)) return
        }

        val colorFilter = buildColorFilter(layer)
        val combinedBlur = ((blurFx?.params?.get("radius") ?: 0f) + extraBlur).coerceAtLeast(0f)

        val mirrorDraws = when ((mirrorFx?.params?.get("axis") ?: -1f).roundToInt()) {
            0 -> listOf(1f to 1f, -1f to 1f)
            1 -> listOf(1f to 1f, 1f to -1f)
            2 -> listOf(1f to 1f, -1f to 1f, 1f to -1f, -1f to -1f)
            else -> listOf(1f to 1f)
        }

        for ((mx, my) in mirrorDraws) {
            canvas.save()
            if (mx != 1f || my != 1f) canvas.scale(mx, my)

            when (layer.type) {
                LayerType.SHAPE -> {
                    if (particlesFx != null && layer.colorHex.equals("#000000", true)) {
                        canvas.restore()
                        continue
                    }
                    val path = ShapePathBuilder.buildPath(layer, w, h, radius)
                    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = parseColorSafe(layer.colorHex)
                        alpha = alphaInt
                        style = Paint.Style.FILL
                        this.colorFilter = colorFilter
                        applyBlendMode(this, layer.blendMode)
                    }

                    if (combinedBlur > 0.5f) {
                        fillPaint.maskFilter = BlurMaskFilter(combinedBlur, BlurMaskFilter.Blur.NORMAL)
                    }

                    if (shadowFx != null) {
                        val ox = shadowFx.params["offsetX"] ?: 12f
                        val oy = shadowFx.params["offsetY"] ?: 18f
                        val sb = (shadowFx.params["blur"] ?: 20f).coerceAtLeast(1f)
                        val shPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = Color.argb((alphaInt * 0.55f).roundToInt(), 0, 0, 0)
                            maskFilter = BlurMaskFilter(sb, BlurMaskFilter.Blur.NORMAL)
                        }
                        canvas.save(); canvas.translate(ox, oy); canvas.drawPath(path, shPaint); canvas.restore()
                    }

                    if (glowFx != null) {
                        val gr = (glowFx.params["radius"] ?: 24f).coerceAtLeast(1f)
                        val gi = ((glowFx.params["intensity"] ?: 75f) / 100f).coerceIn(0f, 1f)
                        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = parseColorSafe(layer.colorHex)
                            alpha = (alphaInt * gi).roundToInt()
                            maskFilter = BlurMaskFilter(gr, BlurMaskFilter.Blur.OUTER)
                        }
                        canvas.drawPath(path, glowPaint)
                    }

                    if (chromaFx != null) {
                        val off = chromaFx.params["offset"] ?: 10f
                        val ang = Math.toRadians((chromaFx.params["angle"] ?: 15f).toDouble())
                        val dx = (cos(ang) * off).toFloat()
                        val dy = (sin(ang) * off).toFloat()
                        val redPaint = Paint(fillPaint).apply { color = Color.argb((alphaInt * 0.65f).roundToInt(), 255, 60, 95) }
                        val cyanPaint = Paint(fillPaint).apply { color = Color.argb((alphaInt * 0.65f).roundToInt(), 60, 235, 255) }
                        canvas.save(); canvas.translate(-dx, -dy); canvas.drawPath(path, redPaint); canvas.restore()
                        canvas.save(); canvas.translate(dx, dy); canvas.drawPath(path, cyanPaint); canvas.restore()
                    }

                    if (motionBlurFx != null) {
                        val dist = motionBlurFx.params["distance"] ?: 22f
                        val ang = Math.toRadians((motionBlurFx.params["angle"] ?: 0f).toDouble())
                        val steps = 5
                        for (s in 1..steps) {
                            val frac = s.toFloat() / steps
                            val trailPaint = Paint(fillPaint).apply {
                                alpha = (alphaInt * 0.22f * (1f - frac * 0.7f)).roundToInt()
                            }
                            canvas.save()
                            canvas.translate((cos(ang) * dist * frac).toFloat(), (sin(ang) * dist * frac).toFloat())
                            canvas.drawPath(path, trailPaint)
                            canvas.restore()
                        }
                    }

                    if (zoomBlurFx != null) {
                        val strength = ((zoomBlurFx.params["strength"] ?: 35f) / 100f).coerceIn(0f, 1f)
                        for (s in 1..4) {
                            val sc = 1f + s * 0.06f * strength
                            val zp = Paint(fillPaint).apply { alpha = (alphaInt * 0.18f).roundToInt() }
                            canvas.save(); canvas.scale(sc, sc); canvas.drawPath(path, zp); canvas.restore()
                        }
                    }

                    if (spinBlurFx != null) {
                        val ang = spinBlurFx.params["angle"] ?: 16f
                        for (s in -2..2) {
                            if (s == 0) continue
                            val sp = Paint(fillPaint).apply { alpha = (alphaInt * 0.18f).roundToInt() }
                            canvas.save(); canvas.rotate(s * (ang * 0.35f)); canvas.drawPath(path, sp); canvas.restore()
                        }
                    }

                    canvas.drawPath(path, fillPaint)

                    // Layer Style: Inner Glow
                    if (layer.layerStyle.innerGlowEnabled) {
                        canvas.save()
                        canvas.clipPath(path)
                        val igPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = parseColorSafe(layer.layerStyle.innerGlowHex, Color.WHITE)
                            alpha = (alphaInt * layer.layerStyle.innerGlowAlpha.coerceIn(0f, 1f)).roundToInt()
                            style = Paint.Style.STROKE
                            strokeWidth = layer.layerStyle.innerGlowRadius.coerceAtLeast(2f)
                            maskFilter = BlurMaskFilter(layer.layerStyle.innerGlowRadius.coerceAtLeast(2f) * 0.65f, BlurMaskFilter.Blur.NORMAL)
                        }
                        canvas.drawPath(path, igPaint)
                        canvas.restore()
                    }

                    if (strokeW > 0.5f) {
                        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = parseColorSafe(layer.strokeHex, Color.WHITE)
                            alpha = alphaInt
                            style = Paint.Style.STROKE
                            strokeWidth = strokeW
                            strokeJoin = Paint.Join.ROUND
                            strokeCap = Paint.Cap.ROUND
                            if (layer.layerStyle.dashedStroke) {
                                pathEffect = DashPathEffect(
                                    floatArrayOf(layer.layerStyle.dashLength.coerceAtLeast(2f), layer.layerStyle.dashGap.coerceAtLeast(2f)),
                                    0f
                                )
                            }
                        }
                        canvas.drawPath(path, strokePaint)
                    }
                }

                LayerType.TEXT -> {
                    val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = parseColorSafe(layer.colorHex, Color.WHITE)
                        alpha = alphaInt
                        textSize = layer.fontSize.coerceAtLeast(10f)
                        typeface = getTypeface(context, layer.fontFamily, layer.fontWeight, layer.italic)
                        letterSpacing = (layer.tracking / 100f).coerceIn(-0.4f, 1.5f)
                        this.colorFilter = colorFilter
                        applyBlendMode(this, layer.blendMode)
                    }
                    if (combinedBlur > 0.5f) {
                        tp.maskFilter = BlurMaskFilter(combinedBlur, BlurMaskFilter.Blur.NORMAL)
                    }
                    if (glowFx != null) {
                        tp.setShadowLayer((glowFx.params["radius"] ?: 24f), 0f, 0f, parseColorSafe(layer.colorHex))
                    } else if (shadowFx != null) {
                        tp.setShadowLayer(
                            (shadowFx.params["blur"] ?: 18f),
                            shadowFx.params["offsetX"] ?: 8f,
                            shadowFx.params["offsetY"] ?: 12f,
                            Color.argb(170, 0, 0, 0)
                        )
                    }

                    val align = when (layer.textAlign) {
                        TextAlignKind.LEFT -> Layout.Alignment.ALIGN_NORMAL
                        TextAlignKind.CENTER -> Layout.Alignment.ALIGN_CENTER
                        TextAlignKind.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
                    }
                    val layoutWidth = w.roundToInt().coerceAtLeast(60)
                    val staticLayout = StaticLayout.Builder
                        .obtain(layer.textContent.ifEmpty { " " }, 0, layer.textContent.length.coerceAtLeast(1), tp, layoutWidth)
                        .setAlignment(align)
                        .setLineSpacing(0f, layer.lineHeight.coerceIn(0.7f, 3f))
                        .build()

                    if (chromaFx != null) {
                        val off = chromaFx.params["offset"] ?: 10f
                        val rp = TextPaint(tp).apply { color = Color.argb((alphaInt * 0.65f).roundToInt(), 255, 65, 95) }
                        val cp = TextPaint(tp).apply { color = Color.argb((alphaInt * 0.65f).roundToInt(), 65, 235, 255) }
                        val rl = StaticLayout.Builder.obtain(layer.textContent.ifEmpty { " " }, 0, layer.textContent.length.coerceAtLeast(1), rp, layoutWidth).setAlignment(align).build()
                        val cl = StaticLayout.Builder.obtain(layer.textContent.ifEmpty { " " }, 0, layer.textContent.length.coerceAtLeast(1), cp, layoutWidth).setAlignment(align).build()
                        canvas.save(); canvas.translate(-layoutWidth * 0.5f - off, -rl.height * 0.5f); rl.draw(canvas); canvas.restore()
                        canvas.save(); canvas.translate(-layoutWidth * 0.5f + off, -cl.height * 0.5f); cl.draw(canvas); canvas.restore()
                    }

                    canvas.save()
                    canvas.translate(-layoutWidth * 0.5f, -staticLayout.height * 0.5f)
                    staticLayout.draw(canvas)
                    canvas.restore()
                }

                LayerType.IMAGE, LayerType.VIDEO -> {
                    val bmp = loadBitmapForLayer(context, layer.mediaUri, layer.type == LayerType.VIDEO, localTime * layer.playbackRate)
                    val dst = RectF(-w * 0.5f, -h * 0.5f, w * 0.5f, h * 0.5f)
                    val imgPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                        alpha = alphaInt
                        this.colorFilter = colorFilter
                        applyBlendMode(this, layer.blendMode)
                    }
                    if (bmp != null) {
                        val drawTarget: () -> Unit = {
                            if (pixelFx != null) {
                                val blk = (pixelFx.params["size"] ?: 16f).coerceIn(2f, 80f)
                                val pw = (w / blk).roundToInt().coerceIn(4, 160)
                                val ph = (h / blk).roundToInt().coerceIn(4, 160)
                                val small = Bitmap.createScaledBitmap(bmp, pw, ph, false)
                                val crispPaint = Paint(imgPaint).apply { isFilterBitmap = false }
                                canvas.drawBitmap(small, null, dst, crispPaint)
                                if (small != bmp) small.recycle()
                            } else {
                                canvas.drawBitmap(bmp, null, dst, imgPaint)
                            }
                        }

                        if (layer.mask.enabled) {
                            val maskPath = Path().apply {
                                if (layer.mask.type == "ellipse") addOval(dst, Path.Direction.CW)
                                else addRoundRect(dst, radius, radius, Path.Direction.CW)
                            }
                            canvas.save()
                            if (layer.mask.invert) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) canvas.clipOutPath(maskPath)
                            } else {
                                canvas.clipPath(maskPath)
                            }
                            drawTarget()
                            canvas.restore()
                        } else {
                            drawTarget()
                        }
                    } else {
                        val ph = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = Color.parseColor("#2E1D24")
                            alpha = alphaInt
                        }
                        canvas.drawRoundRect(dst, 16f, 16f, ph)
                        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = Color.parseColor("#FF8DA1")
                            textSize = 26f
                            textAlign = Paint.Align.CENTER
                        }
                        canvas.drawText(
                            if (layer.mediaUri.isNullOrBlank()) (if (layer.type == LayerType.VIDEO) "VIDEO" else "IMAGE")
                            else "MEDIA HILANG (TAUTKAN ULANG)",
                            0f,
                            10f,
                            tp
                        )
                    }
                }

                LayerType.MESH -> draw3DMesh(canvas, layer, w, h, alphaInt, timeSec)
                LayerType.NULL -> if (isSelected) {
                    val np = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.parseColor("#FFD65C")
                        style = Paint.Style.STROKE
                        strokeWidth = 3f
                        pathEffect = DashPathEffect(floatArrayOf(10f, 8f), 0f)
                    }
                    canvas.drawRect(-w * 0.5f, -h * 0.5f, w * 0.5f, h * 0.5f, np)
                    canvas.drawLine(-w * 0.5f, -h * 0.5f, w * 0.5f, h * 0.5f, np)
                    canvas.drawLine(w * 0.5f, -h * 0.5f, -w * 0.5f, h * 0.5f, np)
                }
                LayerType.GROUP -> if (isSelected) {
                    val gp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.parseColor("#65D4C9")
                        style = Paint.Style.STROKE
                        strokeWidth = 3f
                        pathEffect = DashPathEffect(floatArrayOf(14f, 10f), 0f)
                    }
                    canvas.drawRect(-w * 0.5f, -h * 0.5f, w * 0.5f, h * 0.5f, gp)
                }
                LayerType.AUDIO, LayerType.CAMERA -> Unit
            }

            canvas.restore()
        }

        if (scanFx != null) {
            val density = (scanFx.params["density"] ?: 14f).coerceIn(4f, 48f)
            val op = ((scanFx.params["opacity"] ?: 42f) / 100f).coerceIn(0f, 1f)
            val sp = Paint().apply {
                color = Color.argb((190 * op).roundToInt(), 0, 0, 0)
                strokeWidth = density * 0.42f
            }
            var sy = -h * 0.5f
            while (sy <= h * 0.5f) {
                canvas.drawLine(-w * 0.5f, sy, w * 0.5f, sy, sp)
                sy += density
            }
        }

        if (vignetteFx != null) {
            val strength = ((vignetteFx.params["intensity"] ?: 55f) / 100f).coerceIn(0f, 1f)
            val radFrac = ((vignetteFx.params["radius"] ?: 65f) / 100f).coerceIn(0.15f, 1f)
            val r = max(w, h) * 0.7f
            val vp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    0f, 0f, r,
                    intArrayOf(Color.TRANSPARENT, Color.argb((210 * strength).roundToInt(), 0, 0, 0)),
                    floatArrayOf(radFrac * 0.5f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(-w * 0.5f, -h * 0.5f, w * 0.5f, h * 0.5f, vp)
        }
    }

    private fun drawParticlesEffect(
        canvas: Canvas,
        w: Float,
        h: Float,
        localTime: Float,
        fx: EffectInstance,
        colorHex: String,
        alphaInt: Int
    ) {
        val preset = (fx.params["preset"] ?: 0f).roundToInt().coerceIn(0, 6)
        val count = (fx.params["count"] ?: 42f).roundToInt().coerceIn(5, 160)
        val speed = fx.params["speed"] ?: 1.4f
        val size = fx.params["size"] ?: 9f
        val baseColor = parseColorSafe(colorHex, Color.parseColor("#00D7A7"))
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val rng = Random(42)

        for (i in 0 until count) {
            val sx = (rng.nextFloat() - 0.5f) * w
            val sy = rng.nextFloat()
            val vx = (rng.nextFloat() - 0.5f) * 80f
            val fallSpeed = 0.14f + rng.nextFloat() * 0.35f
            val seedHue = rng.nextFloat() * 360f

            when (preset) {
                1 -> { // Rain
                    val py = (((sy + localTime * speed * 1.4f) % 1f) - 0.5f) * h
                    val px = sx - (py * 0.15f)
                    p.color = Color.argb((alphaInt * 0.65f).roundToInt(), 145, 215, 255)
                    p.strokeWidth = (size * 0.25f).coerceIn(1.5f, 5f)
                    canvas.drawLine(px, py, px - size * 0.6f, py + size * 3.2f, p)
                }
                2 -> { // Snow
                    val py = (((sy + localTime * speed * 0.28f * fallSpeed * 2f) % 1f) - 0.5f) * h
                    val px = sx + sin(localTime * 1.6f + i) * 32f
                    p.color = Color.argb((alphaInt * (0.4f + rng.nextFloat() * 0.6f)).roundToInt(), 245, 250, 255)
                    canvas.drawCircle(px, py, size * (0.4f + rng.nextFloat() * 0.7f), p)
                }
                3 -> { // Fire / Embers (rise up)
                    val prog = ((sy + localTime * speed * fallSpeed * 1.3f) % 1f)
                    val py = h * 0.5f - prog * h
                    val px = sx * (1f - prog * 0.45f) + sin(localTime * 3.5f + i) * 18f
                    p.color = Color.argb((alphaInt * (1f - prog * 0.85f)).roundToInt(), 255, (180 - prog * 130).roundToInt().coerceIn(30, 255), 35)
                    canvas.drawCircle(px, py, size * (1f - prog * 0.6f).coerceAtLeast(0.2f), p)
                }
                4 -> { // Fireworks burst
                    val cycle = (localTime * speed * 0.6f + (i % 3) * 0.33f) % 1f
                    val ang = rng.nextFloat() * 2f * PI.toFloat()
                    val dist = cycle * min(w, h) * 0.46f * (0.4f + rng.nextFloat() * 0.6f)
                    val px = cos(ang) * dist
                    val py = sin(ang) * dist + cycle * cycle * 90f
                    p.color = Color.HSVToColor((alphaInt * (1f - cycle)).roundToInt().coerceIn(0, 255), floatArrayOf(seedHue, 0.85f, 1f))
                    canvas.drawCircle(px, py, size * (1f - cycle * 0.5f), p)
                }
                5 -> { // Bokeh
                    val py = (((sy + localTime * speed * 0.15f) % 1f) - 0.5f) * h
                    val px = sx + cos(localTime * 0.8f + i) * 24f
                    p.color = Color.argb((alphaInt * 0.25f).roundToInt(), Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))
                    canvas.drawCircle(px, py, size * (1.5f + rng.nextFloat() * 1.8f), p)
                }
                6 -> { // Confetti
                    val py = (((sy + localTime * speed * 0.5f) % 1f) - 0.5f) * h
                    val px = sx + sin(localTime * 2.5f + i) * 28f
                    p.color = Color.HSVToColor(alphaInt, floatArrayOf(seedHue, 0.9f, 1f))
                    canvas.save()
                    canvas.translate(px, py)
                    canvas.rotate(localTime * 180f + i * 27f)
                    canvas.drawRect(-size * 0.6f, -size * 0.35f, size * 0.6f, size * 0.35f, p)
                    canvas.restore()
                }
                else -> { // Dust / Sparks
                    val py = (((sy + localTime * speed * fallSpeed) % 1f) - 0.5f) * h
                    val px = sx + sin(localTime * 2f + i) * 26f + vx * (localTime % 3f) * 0.2f
                    val r = size * (0.4f + rng.nextFloat() * 0.85f)
                    p.color = if (baseColor == Color.BLACK) Color.parseColor("#73E2BD") else baseColor
                    p.alpha = (alphaInt * (0.35f + rng.nextFloat() * 0.65f)).roundToInt()
                    canvas.drawCircle(px, py, r, p)
                }
            }
        }
    }

    private fun drawAudioSpectrumEffect(
        canvas: Canvas,
        doc: ProjectDocument,
        w: Float,
        h: Float,
        globalTime: Float,
        localTime: Float,
        fx: EffectInstance,
        colorHex: String,
        alphaInt: Int
    ) {
        val bars = (fx.params["bars"] ?: 28f).roundToInt().coerceIn(6, 64)
        val maxH = (fx.params["height"] ?: 160f).coerceAtMost(h)
        val sens = fx.params["speed"] ?: 1.8f
        val barW = (w / bars) * 0.68f
        val gap = w / bars
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = parseColorSafe(colorHex, Color.parseColor("#00D7A7"))
            alpha = alphaInt
        }
        val realBins = AudioTrackEngine.sampleSpectrumAt(doc, globalTime, bars)
        for (i in 0 until bars) {
            val energy = if (realBins != null) {
                (realBins[i] * sens * 0.85f).coerceIn(0.06f, 1f)
            } else {
                val norm = i.toFloat() / bars
                0.2f + 0.7f * abs(sin(localTime * sens * 3.8f + i * 0.45f) * cos(localTime * sens * 1.7f - norm * 4.5f))
            }
            val bh = (maxH * energy).coerceAtLeast(8f)
            val left = -w * 0.5f + i * gap + (gap - barW) * 0.5f
            canvas.drawRoundRect(RectF(left, -bh * 0.5f, left + barW, bh * 0.5f), barW * 0.5f, barW * 0.5f, p)
        }
    }

    private fun draw3DMesh(
        canvas: Canvas,
        layer: LayerData,
        w: Float,
        h: Float,
        alphaInt: Int,
        timeSec: Float
    ) {
        val rx = Math.toRadians(AnimationEngine.evaluateProperty(layer, "rotX", timeSec).toDouble())
        val ry = Math.toRadians(AnimationEngine.evaluateProperty(layer, "rotY", timeSec).toDouble())
        val rz = Math.toRadians(AnimationEngine.evaluateProperty(layer, "rotZ", timeSec).toDouble())
        val baseColor = parseColorSafe(layer.colorHex, Color.parseColor("#FF907C"))
        val r = Color.red(baseColor)
        val g = Color.green(baseColor)
        val b = Color.blue(baseColor)

        fun rotatePoint(px: Float, py: Float, pz: Float): FloatArray {
            var x = px
            var y = py
            var z = pz
            val y1 = (y * cos(rx) - z * sin(rx)).toFloat()
            val z1 = (y * sin(rx) + z * cos(rx)).toFloat()
            y = y1; z = z1
            val x2 = (x * cos(ry) + z * sin(ry)).toFloat()
            val z2 = (-x * sin(ry) + z * cos(ry)).toFloat()
            x = x2; z = z2
            val x3 = (x * cos(rz) - y * sin(rz)).toFloat()
            val y3 = (x * sin(rz) + y * cos(rz)).toFloat()
            return floatArrayOf(x3, y3, z)
        }

        if (layer.meshKind == MeshKind.SPHERE) {
            val rad = min(w, h) * 0.48f
            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    -rad * 0.3f, -rad * 0.3f, rad * 1.4f,
                    intArrayOf(
                        Color.argb(alphaInt, min(255, r + 70), min(255, g + 70), min(255, b + 70)),
                        Color.argb(alphaInt, r, g, b),
                        Color.argb(alphaInt, (r * 0.28f).toInt(), (g * 0.28f).toInt(), (b * 0.28f).toInt())
                    ),
                    floatArrayOf(0f, 0.55f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawCircle(0f, 0f, rad, fillPaint)
            val wirePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb((alphaInt * 0.45f).roundToInt(), 255, 255, 255)
                style = Paint.Style.STROKE
                strokeWidth = 2f
            }
            for (lat in listOf(-0.5f, 0f, 0.5f)) {
                val p0 = rotatePoint(-rad, lat * rad, 0f)
                val p1 = rotatePoint(rad, lat * rad, 0f)
                canvas.drawLine(p0[0], p0[1], p1[0], p1[1], wirePaint)
            }
            canvas.drawCircle(0f, 0f, rad, wirePaint)
        } else {
            val hx = w * 0.5f
            val hy = h * 0.5f
            val hz = layer.depth * 0.5f
            val verts = arrayOf(
                rotatePoint(-hx, -hy, -hz), rotatePoint(hx, -hy, -hz),
                rotatePoint(hx, hy, -hz), rotatePoint(-hx, hy, -hz),
                rotatePoint(-hx, -hy, hz), rotatePoint(hx, -hy, hz),
                rotatePoint(hx, hy, hz), rotatePoint(-hx, hy, hz)
            )
            val faces = listOf(
                intArrayOf(0, 1, 2, 3) to 0.65f,
                intArrayOf(4, 5, 6, 7) to 1.00f,
                intArrayOf(0, 1, 5, 4) to 0.85f,
                intArrayOf(2, 3, 7, 6) to 0.55f,
                intArrayOf(0, 3, 7, 4) to 0.72f,
                intArrayOf(1, 2, 6, 5) to 0.90f
            ).sortedBy { (idx, _) -> idx.sumOf { verts[it][2].toDouble() } / 4.0 }

            val fp = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
            val ep = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2.5f
                color = Color.argb((alphaInt * 0.75f).roundToInt(), 255, 255, 255)
            }
            for ((idx, shade) in faces) {
                val path = Path().apply {
                    moveTo(verts[idx[0]][0], verts[idx[0]][1])
                    lineTo(verts[idx[1]][0], verts[idx[1]][1])
                    lineTo(verts[idx[2]][0], verts[idx[2]][1])
                    lineTo(verts[idx[3]][0], verts[idx[3]][1])
                    close()
                }
                fp.color = Color.argb(
                    alphaInt,
                    (r * shade).roundToInt().coerceIn(0, 255),
                    (g * shade).roundToInt().coerceIn(0, 255),
                    (b * shade).roundToInt().coerceIn(0, 255)
                )
                canvas.drawPath(path, fp)
                canvas.drawPath(path, ep)
            }
        }
    }

    private fun drawSelectionOverlay(
        canvas: Canvas,
        layer: LayerData,
        cx: Float,
        cy: Float,
        sx: Float,
        sy: Float,
        rot: Float
    ) {
        val hw = layer.w * 0.5f * abs(sx)
        val hh = layer.h * 0.5f * abs(sy)
        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(rot)
        val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#00D7A7")
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        canvas.drawRect(-hw, -hh, hw, hh, boxPaint)

        val handleFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val handleStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#00D7A7")
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        val corners = listOf(-hw to -hh, hw to -hh, hw to hh, -hw to hh)
        for ((x, y) in corners) {
            canvas.drawCircle(x, y, 10f, handleFill)
            canvas.drawCircle(x, y, 10f, handleStroke)
        }
        canvas.drawCircle(0f, 0f, 8f, handleStroke)
        canvas.restore()
    }
}
