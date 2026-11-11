package com.editorshub.core.rendering

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.util.Log
import com.editorshub.core.domain.model.LayerType
import com.editorshub.core.domain.model.Project
import com.editorshub.core.rendering.canvas.CanvasCompositor
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/** Real project thumbnails: renders an actual frame with the compositor. */
@Singleton
class ThumbnailGenerator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val compositor: CanvasCompositor
) {
    /**
     * Never throws. A 4K composition or a depleted heap must not crash project
     * creation/autosave. The image is written through a temporary file so an
     * interrupted render can never leave a zero-byte PNG that looks like a
     * missing thumbnail in Coil.
     */
    fun generate(project: Project, timeMs: Long = 1000L): String? = try {
        val dir = File(context.filesDir, "thumbnails").apply { mkdirs() }
        val file = File(dir, "${project.id}.png")
        val temp = File(dir, "${project.id}.png.tmp")
        if (file.exists() && file.length() == 0L) file.delete()
        val bitmap = renderBitmap(project, timeMs) ?: return null
        try {
            FileOutputStream(temp).use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    "Thumbnail PNG encoding failed"
                }
                output.flush()
            }
            file.delete()
            if (!temp.renameTo(file)) error("Could not replace thumbnail ${file.name}")
            file.absolutePath
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
            if (temp.exists()) temp.delete()
        }
    } catch (e: OutOfMemoryError) {
        Log.w(TAG, "Not enough memory for a thumbnail; skipping")
        null
    } catch (e: Exception) {
        Log.w(TAG, "Thumbnail generation failed", e)
        null
    }

    /**
     * Renders a fresh, caller-owned bitmap at thumbnail resolution. Effects previews
     * use this directly; project thumbnails use [generate] to persist the result.
     */
    fun renderBitmap(
        project: Project,
        timeMs: Long = 1000L,
        maxDimension: Int = 360
    ): Bitmap? = try {
        val w = project.width.coerceAtLeast(1)
        val h = project.height.coerceAtLeast(1)
        val maxSize = maxDimension.coerceAtLeast(1)
        val scale = minOf(maxSize.toFloat() / w.toFloat(), maxSize.toFloat() / h.toFloat())
        val tw = (w * scale).toInt().coerceIn(1, maxSize)
        val th = (h * scale).toInt().coerceIn(1, maxSize)
        val t = bestFrameTime(project, timeMs)
        val frame = RenderGraph.resolve(project, t)
        var bitmap = compositor.renderToBitmap(frame, tw, th, quality = 1f)
        if (bitmap.isRecycled || bitmap.width != tw || bitmap.height != th) {
            if (!bitmap.isRecycled) bitmap.recycle()
            bitmap = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888)
            Canvas(bitmap).drawColor(Color.parseColor("#FF121214"))
        }
        // Empty compositions and layers whose media is missing/corrupt still get
        // a recognizable, branded poster instead of a near-black rectangle that
        // reads as a missing thumbnail.
        if (!hasUsableContent(frame)) drawPoster(Canvas(bitmap), project, tw, th)
        bitmap
    } catch (e: OutOfMemoryError) {
        Log.w(TAG, "Not enough memory for a rendered preview; skipping")
        null
    } catch (e: Exception) {
        Log.w(TAG, "Preview render failed", e)
        null
    }

    /** Prefer a frame where the first visible layer is actually on screen. */
    private fun bestFrameTime(project: Project, requestedMs: Long): Long {
        val end = (project.durationMs - 1L).coerceAtLeast(0L)
        val requested = requestedMs.coerceIn(0L, end)
        val visibleLayers = project.layers.filter { it.visible && it.enabled && it.durationMs > 0L }
        val activeAtRequested = visibleLayers.any {
            requested >= it.startMs && requested < it.startMs + it.durationMs
        }
        if (activeAtRequested) return requested
        val first = visibleLayers.minByOrNull { it.startMs } ?: return requested
        val layerEnd = (first.startMs + first.durationMs - 1L).coerceAtMost(end)
        if (layerEnd < first.startMs) return requested
        return (first.startMs + minOf(1000L, (first.durationMs / 2L).coerceAtLeast(1L)))
            .coerceIn(first.startMs, layerEnd)
            .coerceIn(0L, end)
    }

    /**
     * A frame is useful when at least one active layer can actually draw pixels.
     * A media layer pointing at a deleted/zero-byte file is treated as corrupt;
     * content URIs are left to the compositor because their validity can only be
     * checked by the content resolver.
     */
    private fun hasUsableContent(frame: RenderFrame): Boolean {
        if (frame.layers.isEmpty()) return false
        return frame.layers.any { resolved ->
            val layer = resolved.layer
            when (layer.type) {
                LayerType.IMAGE, LayerType.VIDEO -> usableMedia(layer.sourceUri)
                LayerType.AUDIO, LayerType.GROUP, LayerType.EFFECT -> false
                else -> true
            }
        }
    }

    private fun usableMedia(sourceUri: String?): Boolean {
        if (sourceUri.isNullOrBlank()) return false
        val uri = Uri.parse(sourceUri)
        return when (uri.scheme?.lowercase()) {
            "content", "android.resource", "http", "https" -> true
            "file" -> uri.path?.let { File(it).let { f -> f.isFile && f.length() > 0L } } ?: false
            else -> File(sourceUri).let { f -> f.isFile && f.length() > 0L }
        }
    }

    private fun drawPoster(canvas: Canvas, project: Project, width: Int, height: Int) {
        val w = width.toFloat()
        val h = height.toFloat()
        val background = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, w, h,
                Color.parseColor("#FF24263A"), Color.parseColor("#FF121218"),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, w, h, background)
        val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = (min(w, h) * 0.012f).coerceAtLeast(1f)
            color = Color.parseColor("#6601FFA7")
        }
        val inset = min(w, h) * 0.08f
        canvas.drawRoundRect(
            inset, inset, w - inset, h - inset,
            min(w, h) * 0.09f, min(w, h) * 0.09f, outline
        )
        val initial = project.name.trim().take(1).uppercase().ifEmpty { "E" }
        val initialPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FFDFE3FF")
            textAlign = Paint.Align.CENTER
            textSize = min(w, h) * 0.34f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val metrics = initialPaint.fontMetrics
        canvas.drawText(initial, w / 2f, h / 2f - (metrics.ascent + metrics.descent) / 2f, initialPaint)
        val detailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FF01FFA7")
            textAlign = Paint.Align.CENTER
            textSize = min(w, h) * 0.055f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        canvas.drawText("${project.width} × ${project.height}", w / 2f, h * 0.82f, detailPaint)
    }

    private companion object {
        const val TAG = "EditorsHubThumb"
    }
}
