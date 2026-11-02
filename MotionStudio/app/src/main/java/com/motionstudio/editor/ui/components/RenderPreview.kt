package com.motionstudio.editor.ui.components

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
import com.motionstudio.editor.core.di.AppEntryPoint
import com.motionstudio.editor.core.domain.model.Project
import com.motionstudio.editor.core.rendering.RenderGraph
import com.motionstudio.editor.core.rendering.canvas.RenderView
import com.motionstudio.editor.ui.theme.EditorPalette
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
