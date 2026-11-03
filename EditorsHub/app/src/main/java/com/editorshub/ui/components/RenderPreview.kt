package com.editorshub.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.editorshub.core.di.AppEntryPoint
import com.editorshub.core.domain.model.Project
import com.editorshub.core.rendering.RenderGraph
import com.editorshub.core.rendering.canvas.RenderView
import com.editorshub.ui.theme.EditorPalette
import dagger.hilt.android.EntryPointAccessors

/** Real rendered preview of any project, reused by the Effects Library, AI Studio and Node Editor. */
@Composable
fun RenderPreview(project: Project, timeMs: Long = 0L, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val compositor = remember {
        EntryPointAccessors.fromApplication(context.applicationContext, AppEntryPoint::class.java).compositor()
    }
    val frame = remember(project, timeMs) { RenderGraph.resolve(project, timeMs) }
    val ratio = project.width.toFloat() / project.height.toFloat()
    BoxWithConstraints(
        modifier = modifier.background(EditorPalette.Black).clipToBounds(),
        contentAlignment = Alignment.Center
    ) {
        val w = minOf(maxWidth, maxHeight * ratio)
        val h = w / ratio
        AndroidView(
            modifier = Modifier.size(w, h),
            factory = { ctx -> RenderView(ctx, compositor) },
            update = { view -> view.update(frame) }
        )
    }
}
