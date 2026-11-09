package com.editorshub.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.editorshub.core.di.AppEntryPoint
import com.editorshub.core.domain.model.Project
import com.editorshub.ui.theme.EditorPalette
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Real rendered preview of any project, reused by the Effects Library, AI Studio
 * and Node Editor. The frame is rendered off the main thread into a small bitmap
 * and shown as a normal Compose Image; this is more reliable in a lazy list than
 * a hardware-accelerated AndroidView whose first draw can arrive with zero size.
 */
@Composable
fun RenderPreview(project: Project, timeMs: Long = 0L, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val thumbnails = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            AppEntryPoint::class.java
        ).thumbnailGenerator()
    }
    val bitmap by produceState<Bitmap?>(initialValue = null, project, timeMs) {
        value = withContext(Dispatchers.IO) {
            thumbnails.renderBitmap(project, timeMs, maxDimension = 480)
        }
    }
    DisposableEffect(bitmap) {
        val current = bitmap
        onDispose {
            if (current != null && !current.isRecycled) current.recycle()
        }
    }

    Box(
        modifier = modifier.background(EditorPalette.Black).clipToBounds(),
        contentAlignment = Alignment.Center
    ) {
        val frame = bitmap
        if (frame != null && !frame.isRecycled) {
            Image(
                bitmap = frame.asImageBitmap(),
                contentDescription = "Rendered project preview",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.linearGradient(listOf(Color(0xFF24263A), Color(0xFF1A2C3A)))
                ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = EditorPalette.Purple
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        "Rendering preview",
                        color = EditorPalette.TextMuted,
                        fontSize = 9.sp
                    )
                }
            }
        }
    }
}
