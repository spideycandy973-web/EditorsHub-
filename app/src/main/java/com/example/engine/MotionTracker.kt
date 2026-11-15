package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.example.model.EasingSpec
import com.example.model.Keyframe
import com.example.model.LayerData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.math.*

data class TrackResult(
    val xKeyframes: List<Keyframe>,
    val yKeyframes: List<Keyframe>,
    val trackedPointsCount: Int,
    val confidenceAvg: Float
)

/**
 * Native optical-flow / NCC corner feature tracker operating on video frames
 * via MediaMetadataRetriever (mirrors Open Motion's motion/camera tracker workers).
 */
object MotionTracker {

    private const val ANALYSIS_WIDTH = 160
    private const val PATCH_RADIUS = 7
    private const val SEARCH_RADIUS = 18

    suspend fun trackVideoFeature(
        context: Context,
        videoLayer: LayerData,
        normTargetX: Float,
        normTargetY: Float,
        sampleFps: Int = 10,
        onProgress: (Float) -> Unit = {}
    ): TrackResult = withContext(Dispatchers.Default) {
        val uriStr = videoLayer.mediaUri ?: error("Layer video tidak memiliki sumber media")
        val uri = Uri.parse(uriStr)

        val retriever = MediaMetadataRetriever()
        try {
            if (uri.scheme == "file" && !uri.path.isNullOrBlank()) {
                retriever.setDataSource(uri.path)
            } else {
                retriever.setDataSource(context, uri)
            }
            val durMsStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val mediaDurSec = ((durMsStr?.toLongOrNull() ?: 3000L) / 1000f).coerceAtLeast(0.2f)
            val trackDuration = min(videoLayer.duration, mediaDurSec).coerceAtLeast(0.2f)
            val steps = (trackDuration * sampleFps.coerceIn(4, 24)).roundToInt().coerceIn(3, 180)
            val dt = trackDuration / (steps - 1).coerceAtLeast(1)

            // Decode first frame to measure scaled luma dimensions & refine corner point
            val firstRaw = retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: error("Tidak bisa membaca frame awal video")
            val scaledH = ((firstRaw.height.toFloat() / firstRaw.width.coerceAtLeast(1)) * ANALYSIS_WIDTH)
                .roundToInt().coerceIn(64, 320)
            var prevLuma = toGrayscaleArray(firstRaw, ANALYSIS_WIDTH, scaledH)
            firstRaw.recycle()

            var curPx = (normTargetX.coerceIn(0.05f, 0.95f) * (ANALYSIS_WIDTH - 1))
            var curPy = (normTargetY.coerceIn(0.05f, 0.95f) * (scaledH - 1))
            // Snap to strongest local gradient corner in a 7px radius
            val lockedCorner = refineBestCorner(prevLuma, ANALYSIS_WIDTH, scaledH, curPx.roundToInt(), curPy.roundToInt())
            curPx = lockedCorner.first.toFloat()
            curPy = lockedCorner.second.toFloat()

            val startCompX = AnimationEngine.evaluateProperty(videoLayer, "x", videoLayer.start)
            val startCompY = AnimationEngine.evaluateProperty(videoLayer, "y", videoLayer.start)
            val initPx = curPx
            val initPy = curPy

            val xKeys = mutableListOf(Keyframe(time = 0f, value = startCompX, easing = EasingSpec("linear")))
            val yKeys = mutableListOf(Keyframe(time = 0f, value = startCompY, easing = EasingSpec("linear")))
            var confSum = 1f

            for (i in 1 until steps) {
                coroutineContext.ensureActive()
                val localSec = i * dt
                val mediaUs = (localSec * videoLayer.playbackRate.coerceAtLeast(0.1f) * 1_000_000f).toLong()
                val frame = retriever.getFrameAtTime(mediaUs, MediaMetadataRetriever.OPTION_CLOSEST)
                if (frame != null) {
                    val curLuma = toGrayscaleArray(frame, ANALYSIS_WIDTH, scaledH)
                    frame.recycle()

                    val match = trackNccPatch(
                        prev = prevLuma,
                        curr = curLuma,
                        w = ANALYSIS_WIDTH,
                        h = scaledH,
                        px = curPx,
                        py = curPy
                    )
                    curPx = match.first.coerceIn(PATCH_RADIUS + 2f, ANALYSIS_WIDTH - PATCH_RADIUS - 3f)
                    curPy = match.second.coerceIn(PATCH_RADIUS + 2f, scaledH - PATCH_RADIUS - 3f)
                    confSum += match.third

                    // Invert the tracked scene motion for stabilization or map delta directly
                    val deltaLayerX = ((curPx - initPx) / ANALYSIS_WIDTH.toFloat()) * videoLayer.w
                    val deltaLayerY = ((curPy - initPy) / scaledH.toFloat()) * videoLayer.h
                    val tRounded = (localSec * 100f).roundToInt() / 100f
                    xKeys.add(Keyframe(time = tRounded, value = startCompX + deltaLayerX, easing = EasingSpec("linear")))
                    yKeys.add(Keyframe(time = tRounded, value = startCompY + deltaLayerY, easing = EasingSpec("linear")))

                    prevLuma = curLuma
                }
                onProgress((i + 1).toFloat() / steps.toFloat())
            }

            TrackResult(
                xKeyframes = xKeys,
                yKeyframes = yKeys,
                trackedPointsCount = xKeys.size,
                confidenceAvg = (confSum / steps.coerceAtLeast(1)).coerceIn(0f, 1f)
            )
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun toGrayscaleArray(src: Bitmap, tw: Int, th: Int): FloatArray {
        val scaled = Bitmap.createScaledBitmap(src, tw, th, true)
        val pixels = IntArray(tw * th)
        scaled.getPixels(pixels, 0, tw, 0, 0, tw, th)
        if (scaled != src) scaled.recycle()
        val luma = FloatArray(tw * th)
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            luma[i] = 0.299f * r + 0.587f * g + 0.114f * b
        }
        return luma
    }

    private fun refineBestCorner(luma: FloatArray, w: Int, h: Int, cx: Int, cy: Int): Pair<Int, Int> {
        var bestX = cx.coerceIn(PATCH_RADIUS + 2, w - PATCH_RADIUS - 3)
        var bestY = cy.coerceIn(PATCH_RADIUS + 2, h - PATCH_RADIUS - 3)
        var bestScore = -1f
        for (y in (bestY - 5).coerceAtLeast(PATCH_RADIUS + 2)..(bestY + 5).coerceAtMost(h - PATCH_RADIUS - 3)) {
            for (x in (bestX - 5).coerceAtLeast(PATCH_RADIUS + 2)..(bestX + 5).coerceAtMost(w - PATCH_RADIUS - 3)) {
                val gx = luma[y * w + (x + 1)] - luma[y * w + (x - 1)]
                val gy = luma[(y + 1) * w + x] - luma[(y - 1) * w + x]
                val score = gx * gx + gy * gy
                if (score > bestScore) {
                    bestScore = score
                    bestX = x
                    bestY = y
                }
            }
        }
        return bestX to bestY
    }

    private fun trackNccPatch(
        prev: FloatArray,
        curr: FloatArray,
        w: Int,
        h: Int,
        px: Float,
        py: Float
    ): Triple<Float, Float, Float> {
        val cx0 = px.roundToInt().coerceIn(PATCH_RADIUS + 1, w - PATCH_RADIUS - 2)
        val cy0 = py.roundToInt().coerceIn(PATCH_RADIUS + 1, h - PATCH_RADIUS - 2)

        // Precompute template mean and variance
        var meanP = 0f
        var count = 0
        for (dy in -PATCH_RADIUS..PATCH_RADIUS) {
            for (dx in -PATCH_RADIUS..PATCH_RADIUS) {
                meanP += prev[(cy0 + dy) * w + (cx0 + dx)]
                count++
            }
        }
        meanP /= count

        var varP = 0f
        for (dy in -PATCH_RADIUS..PATCH_RADIUS) {
            for (dx in -PATCH_RADIUS..PATCH_RADIUS) {
                val d = prev[(cy0 + dy) * w + (cx0 + dx)] - meanP
                varP += d * d
            }
        }
        if (varP < 1f) return Triple(px, py, 0.3f)

        var bestDx = 0
        var bestDy = 0
        var bestNcc = -2f

        val minX = (cx0 - SEARCH_RADIUS).coerceAtLeast(PATCH_RADIUS + 1)
        val maxX = (cx0 + SEARCH_RADIUS).coerceAtMost(w - PATCH_RADIUS - 2)
        val minY = (cy0 - SEARCH_RADIUS).coerceAtLeast(PATCH_RADIUS + 1)
        val maxY = (cy0 + SEARCH_RADIUS).coerceAtMost(h - PATCH_RADIUS - 2)

        for (sy in minY..maxY) {
            for (sx in minX..maxX) {
                var meanC = 0f
                for (dy in -PATCH_RADIUS..PATCH_RADIUS) {
                    val rowOff = (sy + dy) * w + sx
                    for (dx in -PATCH_RADIUS..PATCH_RADIUS) {
                        meanC += curr[rowOff + dx]
                    }
                }
                meanC /= count

                var num = 0f
                var varC = 0f
                for (dy in -PATCH_RADIUS..PATCH_RADIUS) {
                    val pOff = (cy0 + dy) * w + cx0
                    val cOff = (sy + dy) * w + sx
                    for (dx in -PATCH_RADIUS..PATCH_RADIUS) {
                        val dp = prev[pOff + dx] - meanP
                        val dc = curr[cOff + dx] - meanC
                        num += dp * dc
                        varC += dc * dc
                    }
                }
                val denom = sqrt(varP * varC).coerceAtLeast(1e-4f)
                val ncc = num / denom
                if (ncc > bestNcc) {
                    bestNcc = ncc
                    bestDx = sx - cx0
                    bestDy = sy - cy0
                }
            }
        }

        return Triple(cx0 + bestDx.toFloat(), cy0 + bestDy.toFloat(), bestNcc.coerceIn(0f, 1f))
    }
}
