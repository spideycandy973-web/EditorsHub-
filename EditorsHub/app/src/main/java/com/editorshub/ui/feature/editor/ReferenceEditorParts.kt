package com.editorshub.ui.feature.editor

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.editorshub.core.di.AppEntryPoint
import com.editorshub.core.domain.model.Layer
import com.editorshub.core.domain.model.Project
import com.editorshub.ui.theme.EditorPalette
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private val SheetSurface = Color(0xFF1C1C1E)
private val TileSurface = Color(0xFF252527)

/** Icons for the reference-style Add sheet. They all map to real editing actions. */
enum class ReferenceAddIcon {
    MEDIA, TEXT, SHAPE, AUDIO, FREEHAND, VECTOR, ELEMENT, TEMPLATE, CAMERA, NULL, GROUP
}

private data class AddTileSpec(
    val label: String,
    val description: String,
    val icon: ReferenceAddIcon,
    val chevron: Boolean = false,
    val newBadge: Boolean = false,
    val horizontal: Boolean = false,
    val onClick: () -> Unit
)

@Composable
fun ReferenceAddSheet(
    onDismiss: () -> Unit,
    onMedia: () -> Unit,
    onText: () -> Unit,
    onShape: () -> Unit,
    onAudio: () -> Unit,
    onFreehand: () -> Unit,
    onVector: () -> Unit,
    onImportProject: () -> Unit,
    onTemplate: () -> Unit,
    onCamera: () -> Unit,
    onNull: () -> Unit,
    onGroup: () -> Unit,
    modifier: Modifier = Modifier
) {
    val first = listOf(
        AddTileSpec("Media", "Import media layer", ReferenceAddIcon.MEDIA, chevron = true, onClick = onMedia),
        AddTileSpec("Text", "Add Text layer", ReferenceAddIcon.TEXT, onClick = onText),
        AddTileSpec("Shape", "Choose Shape layer", ReferenceAddIcon.SHAPE, chevron = true, onClick = onShape),
        AddTileSpec("Audio", "Import audio layer", ReferenceAddIcon.AUDIO, chevron = true, onClick = onAudio)
    )
    val second = listOf(
        AddTileSpec("Freehand\nDrawing", "Add freehand drawing layer", ReferenceAddIcon.FREEHAND, onClick = onFreehand),
        AddTileSpec("Vector\nDrawing", "Add vector drawing layer", ReferenceAddIcon.VECTOR, onClick = onVector),
        AddTileSpec("Element /\nProject", "Import project or element", ReferenceAddIcon.ELEMENT, chevron = true, onClick = onImportProject),
        AddTileSpec("Template", "Add template layers", ReferenceAddIcon.TEMPLATE,
            chevron = true, newBadge = true, onClick = onTemplate)
    )
    val third = listOf(
        AddTileSpec("Camera", "Add camera object", ReferenceAddIcon.CAMERA, horizontal = true, onClick = onCamera),
        AddTileSpec("Null", "Add null object", ReferenceAddIcon.NULL, horizontal = true, onClick = onNull),
        AddTileSpec("Empty Group", "Add empty group", ReferenceAddIcon.GROUP, horizontal = true, onClick = onGroup)
    )
    Column(
        modifier
            .fillMaxWidth()
            .height(292.dp)
            .background(SheetSurface, RoundedCornerShape(24.dp))
            .padding(horizontal = 14.dp, vertical = 14.dp)
            .semantics { contentDescription = "Add sheet" }
    ) {
        Text("Add", Modifier.clickable(role = Role.Button, onClick = onDismiss),
            fontSize = 18.sp, fontWeight = FontWeight.Bold,
            color = EditorPalette.TextPrimary)
        Spacer(Modifier.height(10.dp))
        AddTileRow(first, 70.dp)
        Spacer(Modifier.height(8.dp))
        AddTileRow(second, 74.dp)
        Spacer(Modifier.height(8.dp))
        AddTileRow(third, 46.dp)
    }
}

@Composable
private fun AddTileRow(tiles: List<AddTileSpec>, height: androidx.compose.ui.unit.Dp) {
    Row(Modifier.fillMaxWidth().height(height),
        horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        tiles.forEach { tile ->
            AddTile(tile, Modifier.weight(1f).fillMaxHeight())
        }
    }
}

@Composable
private fun AddTile(spec: AddTileSpec, modifier: Modifier) {
    Box(
        modifier
            .background(TileSurface, RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClick = spec.onClick)
            .semantics { contentDescription = spec.description }
            .padding(horizontal = 5.dp, vertical = 5.dp)
    ) {
        if (spec.horizontal) {
            Row(Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center) {
                AddTileIcon(spec.icon, 21.dp)
                Spacer(Modifier.width(7.dp))
                Text(spec.label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                    color = EditorPalette.TextPrimary, maxLines = 1)
            }
        } else {
            Column(Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                AddTileIcon(spec.icon, 25.dp)
                Spacer(Modifier.height(3.dp))
                Text(spec.label.replace("\n", " "), fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold, lineHeight = 11.sp,
                    color = EditorPalette.TextPrimary, maxLines = 2,
                    textAlign = TextAlign.Center)
            }
        }
        if (spec.chevron) {
            Icon(Icons.Default.ChevronRight, null, Modifier.align(Alignment.TopEnd).size(15.dp),
                tint = EditorPalette.TextMuted)
        }
        if (spec.newBadge) {
            Text("NEW", Modifier.align(Alignment.TopStart).offset(x = 2.dp, y = (-1).dp)
                .background(EditorPalette.Amber, RoundedCornerShape(3.dp))
                .padding(horizontal = 3.dp, vertical = 1.dp),
                fontSize = 7.sp, fontWeight = FontWeight.Bold, color = Color.Black)
        }
    }
}

@Composable
private fun AddTileIcon(icon: ReferenceAddIcon, size: androidx.compose.ui.unit.Dp) {
    when (icon) {
        ReferenceAddIcon.TEXT -> Text("Aa", fontSize = (size.value * 0.85f).sp,
            fontWeight = FontWeight.SemiBold, color = EditorPalette.TextPrimary)
        ReferenceAddIcon.MEDIA -> Icon(Icons.Default.Image, null, Modifier.size(size),
            tint = EditorPalette.TextPrimary)
        ReferenceAddIcon.SHAPE -> Icon(Icons.Default.Category, null, Modifier.size(size),
            tint = EditorPalette.TextPrimary)
        ReferenceAddIcon.AUDIO -> Icon(Icons.Default.Audiotrack, null, Modifier.size(size),
            tint = EditorPalette.TextPrimary)
        ReferenceAddIcon.FREEHAND -> Icon(Icons.Default.Brush, null, Modifier.size(size),
            tint = EditorPalette.TextPrimary)
        ReferenceAddIcon.VECTOR -> Icon(Icons.Default.Edit, null, Modifier.size(size),
            tint = EditorPalette.TextPrimary)
        ReferenceAddIcon.ELEMENT -> Icon(Icons.Default.Workspaces, null, Modifier.size(size),
            tint = EditorPalette.TextPrimary)
        ReferenceAddIcon.TEMPLATE -> Icon(Icons.Default.GridView, null, Modifier.size(size),
            tint = EditorPalette.TextPrimary)
        ReferenceAddIcon.CAMERA -> Icon(Icons.Default.Movie, null, Modifier.size(size),
            tint = EditorPalette.TextPrimary)
        ReferenceAddIcon.NULL -> Icon(Icons.Default.CropSquare, null, Modifier.size(size),
            tint = EditorPalette.TextPrimary)
        ReferenceAddIcon.GROUP -> Icon(Icons.Default.SelectAll, null, Modifier.size(size),
            tint = EditorPalette.TextPrimary)
    }
}

/**
 * A timeline clip's filmstrip. The repeated tile is an actual rendered frame from
 * the layer, not a decorative bitmap. Missing/corrupt media falls back to a
 * visible branded poster supplied by ThumbnailGenerator.
 */
@Composable
fun LayerFilmstrip(project: Project, layer: Layer, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val thumbnails = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext, AppEntryPoint::class.java
        ).thumbnailGenerator()
    }
    val bitmap by produceState<Bitmap?>(
        initialValue = null,
        project.id, layer.id, layer.sourceUri, layer.text?.text, layer.shape?.kind
    ) {
        value = withContext(Dispatchers.IO) {
            val singleLayer = layer.copy(visible = true, enabled = true, parentId = null)
            val oneLayerProject = project.copy(
                layers = listOf(singleLayer),
                audioTracks = emptyList(),
                transitions = emptyList()
            )
            val time = layer.startMs + minOf(1000L, (layer.durationMs / 2L).coerceAtLeast(1L))
            thumbnails.renderBitmap(oneLayerProject, time, maxDimension = 96)
        }
    }
    val currentBitmap = bitmap
    DisposableEffect(currentBitmap) {
        onDispose {
            if (currentBitmap != null && !currentBitmap.isRecycled) currentBitmap.recycle()
        }
    }

    Box(modifier.background(Color(0xFF17171A)).clip(RoundedCornerShape(4.dp))) {
        if (currentBitmap != null && !currentBitmap.isRecycled) {
            val image = currentBitmap!!.asImageBitmap()
            Canvas(Modifier.fillMaxSize()) {
                val tileWidth = (size.height * image.width / image.height.toFloat())
                    .coerceAtLeast(8f)
                var x = 0f
                while (x < size.width) {
                    drawImage(
                        image = image,
                        dstOffset = IntOffset(x.roundToInt(), 0),
                        dstSize = IntSize(
                            tileWidth.roundToInt().coerceAtLeast(1),
                            size.height.roundToInt().coerceAtLeast(1)
                        )
                    )
                    x += tileWidth
                }
            }
        } else {
            Column(
                Modifier.fillMaxSize()
                    .background(Brush.linearGradient(listOf(Color(0xFF24263A), Color(0xFF1A2C3A)))),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.Movie, null, Modifier.size(16.dp), tint = EditorPalette.Purple)
                Text(layer.name.take(1).uppercase().ifEmpty { "E" },
                    fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    color = EditorPalette.TextPrimary)
                Text("No frame", fontSize = 7.sp, color = EditorPalette.TextMuted)
            }
        }
    }
}
