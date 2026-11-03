package com.editorshub.core.rendering

import android.content.Context
import android.graphics.Bitmap
import com.editorshub.core.domain.model.Project
import com.editorshub.core.rendering.canvas.CanvasCompositor
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/** Real project thumbnails: renders an actual frame with the compositor. */
@Singleton
class ThumbnailGenerator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val compositor: CanvasCompositor
) {
    fun generate(project: Project, timeMs: Long = 1000L): String? = try {
        val dir = File(context.filesDir, "thumbnails").apply { mkdirs() }
        val file = File(dir, "${project.id}.png")
        val t = timeMs.coerceIn(0L, max(0L, project.durationMs - 1))
        val frame = RenderGraph.resolve(project, t)
        val full = compositor.renderToBitmap(frame, project.width, project.height)
        val scale = 360f / project.width
        val w = (project.width * scale).toInt().coerceAtLeast(1)
        val h = (project.height * scale).toInt().coerceAtLeast(1)
        val thumb = Bitmap.createScaledBitmap(full, w, h, true)
        FileOutputStream(file).use { thumb.compress(Bitmap.CompressFormat.PNG, 90, it) }
        if (thumb !== full) thumb.recycle()
        full.recycle()
        file.absolutePath
    } catch (e: Exception) { null }
}
