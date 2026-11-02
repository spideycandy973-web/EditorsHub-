package com.motionstudio.editor.ui.feature.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.opengl.GLSurfaceView
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.motionstudio.editor.core.di.AppEntryPoint
import com.motionstudio.editor.core.rendering.RenderFrame
import com.motionstudio.editor.core.rendering.RenderGraph
import com.motionstudio.editor.core.rendering.canvas.CanvasCompositor
import com.motionstudio.editor.core.rendering.gl.EffectUniformBuilder
import com.motionstudio.editor.core.rendering.gl.GlPreviewRenderer
import dagger.hilt.android.EntryPointAccessors
import java.util.concurrent.atomic.AtomicReference

/** Hardware-accelerated Canvas view: draws the real composition on every invalidation. */
private class RenderView(
    context: Context,
    private val compositor: CanvasCompositor
) : View(context) {
    private var frame: RenderFrame? = null
    init { setLayerType(LAYER_TYPE_HARDWARE, null) }

    fun update(frame: RenderFrame) {
        this.frame = frame
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val f = frame
        if (f == null) { canvas.drawColor(Color.BLACK); return }
        compositor.draw(canvas, f, width, height)
    }
}

/**
 * Preview with two real backends:
 *  - GLSurfaceView + OpenGL ES shader chain (GPU effects)
 *  - hardware Canvas (fast compositing fallback)
 */
@Composable
fun PreviewSurface(state: EditorUiState, modifier: Modifier = Modifier) {
    val project = state.project ?: return
    val context = LocalContext.current
    val entry = remember {
        EntryPointAccessors.fromApplication(context.applicationContext, AppEntryPoint::class.java)
    }
    val compositor = remember { entry.compositor() }
    val density = LocalDensity.current
    val ratio = project.width.toFloat() / project.height.toFloat()
    LaunchedEffect(state.previewQuality) { compositor.previewQuality = state.previewQuality }

    BoxWithConstraints(modifier = modifier.background(MaterialTheme.colorScheme.background)
        .clipToBounds(), contentAlignment = Alignment.Center) {
        val w = minOf(maxWidth, maxHeight * ratio) * state.previewZoom
        val h = w / ratio

        if (state.gpuPreview) {
            val holder = remember { AtomicReference<Pair<com.motionstudio.editor.core.domain.model.Project, Long>?>(null) }
            val renderer = remember {
                GlPreviewRenderer(
                    bitmapProvider = {
                        val pair = holder.get() ?: return@GlPreviewRenderer null
                        val frame = RenderGraph.resolve(pair.first, pair.second)
                        compositor.renderToBitmap(frame, pair.first.width, pair.first.height)
                    },
                    uniformsProvider = {
                        val pair = holder.get()
                        if (pair == null) com.motionstudio.editor.core.rendering.gl.GlUniforms()
                        else {
                            val frame = RenderGraph.resolve(pair.first, pair.second)
                            EffectUniformBuilder.from(frame.layers.flatMap { it.effects }, pair.second)
                        }
                    }
                )
            }
            AndroidView(
                modifier = Modifier.size(w, h),
                factory = { ctx ->
                    GLSurfaceView(ctx).apply {
                        setEGLContextClientVersion(2)
                        setRenderer(renderer)
                        renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
                    }
                },
                update = { view ->
                    holder.set(project to state.playheadMs)
                    view.requestRender()
                }
            )
        } else {
            val frame = remember(project, state.playheadMs) { RenderGraph.resolve(project, state.playheadMs) }
            AndroidView(
                modifier = Modifier.size(w, h),
                factory = { ctx -> RenderView(ctx, compositor) },
                update = { view -> view.update(frame) }
            )
        }
    }
}
