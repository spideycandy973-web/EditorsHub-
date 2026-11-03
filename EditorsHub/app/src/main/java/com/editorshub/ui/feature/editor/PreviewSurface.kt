package com.editorshub.ui.feature.editor

import android.graphics.Bitmap
import android.opengl.GLSurfaceView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.editorshub.core.di.AppEntryPoint
import com.editorshub.core.rendering.RenderFrame
import com.editorshub.core.rendering.canvas.RenderView
import com.editorshub.core.rendering.RenderGraph
import com.editorshub.core.rendering.canvas.CanvasCompositor
import com.editorshub.core.rendering.gl.EffectUniformBuilder
import com.editorshub.core.rendering.gl.GlPreviewRenderer
import dagger.hilt.android.EntryPointAccessors
import java.util.concurrent.atomic.AtomicReference

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
    LaunchedEffect(state.previewQuality, state.proxyEnabled, state.proxyScale) {
        compositor.previewQuality = (state.previewQuality *
            if (state.proxyEnabled) state.proxyScale else 1f).coerceIn(0.15f, 1f)
    }

    BoxWithConstraints(modifier = modifier.background(MaterialTheme.colorScheme.background)
        .clipToBounds(), contentAlignment = Alignment.Center) {
        val w = minOf(maxWidth, maxHeight * ratio) * state.previewZoom
        val h = w / ratio

        if (state.gpuPreview) {
            val holder = remember { AtomicReference<Pair<com.editorshub.core.domain.model.Project, Long>?>(null) }
            val renderer = remember {
                GlPreviewRenderer(
                    bitmapProvider = {
                        val pair = holder.get() ?: return@GlPreviewRenderer null
                        val frame = RenderGraph.resolve(pair.first, pair.second)
                        compositor.renderToBitmap(frame, pair.first.width, pair.first.height)
                    },
                    uniformsProvider = {
                        val pair = holder.get()
                        if (pair == null) com.editorshub.core.rendering.gl.GlUniforms()
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
        PreviewGuides(state, Modifier.size(w, h))
    }
}

/** Real overlay: transparency checkerboard, safe areas, grid, centre and aspect guides. */
@Composable
private fun PreviewGuides(state: EditorUiState, modifier: Modifier) {
    val transparent = state.project?.backgroundType ==
        com.editorshub.core.domain.model.BackgroundType.TRANSPARENT
    if (!transparent && !state.showGrid && !state.showSafeArea &&
        !state.showCenterGuides && !state.showAspectGuides) return
    Canvas(modifier) {
        val w = size.width; val h = size.height
        if (transparent && state.checkerboard) {
            val cell = 16f * density
            var y = 0f
            var row = 0
            while (y < h) {
                var x = 0f
                var col = row
                while (x < w) {
                    drawRect(
                        if ((col % 2) == 0) Color(0xFF2A2C36) else Color(0xFF171922),
                        topLeft = Offset(x, y), size = androidx.compose.ui.geometry.Size(cell, cell)
                    )
                    x += cell; col++
                }
                y += cell; row++
            }
        }
        val guide = Color(0x66FFFFFF)
        if (state.showGrid) {
            for (i in 1..2) {
                drawLine(guide, Offset(w * i / 3f, 0f), Offset(w * i / 3f, h), strokeWidth = 1f)
                drawLine(guide, Offset(0f, h * i / 3f), Offset(w, h * i / 3f), strokeWidth = 1f)
            }
        }
        if (state.showSafeArea) {
            drawRect(guide, topLeft = Offset(w * 0.1f, h * 0.1f),
                size = androidx.compose.ui.geometry.Size(w * 0.8f, h * 0.8f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
        }
        if (state.showAspectGuides) {
            drawRect(Color(0x448B7BFF), topLeft = Offset(w * 0.05f, h * 0.05f),
                size = androidx.compose.ui.geometry.Size(w * 0.9f, h * 0.9f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
        }
        if (state.showCenterGuides) {
            drawLine(Color(0x88FBBF24), Offset(w / 2f, 0f), Offset(w / 2f, h), strokeWidth = 1f)
            drawLine(Color(0x88FBBF24), Offset(0f, h / 2f), Offset(w, h / 2f), strokeWidth = 1f)
        }
    }
}
