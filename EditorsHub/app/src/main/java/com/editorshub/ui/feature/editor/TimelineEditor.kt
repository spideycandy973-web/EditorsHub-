package com.editorshub.ui.feature.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.editorshub.core.domain.model.Layer
import com.editorshub.core.domain.model.LayerType
import com.editorshub.core.domain.model.Project
import com.editorshub.core.domain.timeline.TimelineMath
import kotlin.math.roundToLong

@Composable
fun TimelineEditor(
    state: EditorUiState,
    vm: EditorViewModel,
    modifier: Modifier = Modifier
) {
    val project = state.project ?: return
    val density = LocalDensity.current.density
    val zoom = state.zoomPxPerSecond
    val contentWidth: Dp = ((project.durationMs / 1000f) * zoom + 120f).dp
    val rowHeight = 40.dp
    val totalHeight = 26.dp + rowHeight * (project.layers.size.coerceAtLeast(1))
    val hScroll = rememberScrollState()
    val vScroll = rememberScrollState()

    Column(modifier = modifier.background(MaterialTheme.colorScheme.surface)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Timeline", style = MaterialTheme.typography.titleMedium, fontSize = 12.sp)
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = { vm.setZoom(zoom - 16) }, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Remove, "Zoom out", Modifier.size(16.dp))
            }
            Slider(value = zoom, onValueChange = { vm.setZoom(it) }, valueRange = 12f..320f,
                modifier = Modifier.weight(1f).height(24.dp))
            IconButton(onClick = { vm.setZoom(zoom + 16) }, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Add, "Zoom in", Modifier.size(16.dp))
            }
            IconButton(onClick = { vm.splitLayer(state.selectedLayerId ?: "") }, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.ContentCut, "Split", Modifier.size(15.dp))
            }
            IconButton(onClick = { vm.duplicateLayer(state.selectedLayerId ?: "") }, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.ContentCopy, "Duplicate", Modifier.size(15.dp))
            }
            IconButton(onClick = { vm.deleteLayer(state.selectedLayerId ?: "") }, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Delete, "Delete", Modifier.size(15.dp), tint = MaterialTheme.colorScheme.error)
            }
        }
        Divider()
        Column(Modifier.fillMaxSize().verticalScroll(vScroll)) {
            Row(Modifier.fillMaxWidth()) {
                // Fixed layer labels
                Column(Modifier.width(112.dp)) {
                    Box(Modifier.height(26.dp).fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.CenterStart) {
                        Text(" ${project.layers.size} layers", fontSize = 9.sp, modifier = Modifier.padding(start = 4.dp))
                    }
                    project.layers.forEachIndexed { index, layer ->
                        LayerLabel(layer, index, state, vm, rowHeight)
                    }
                }
                // Horizontally scrolling tracks
                Box(Modifier.weight(1f).horizontalScroll(hScroll)) {
                    Box(Modifier.width(contentWidth)) {
                        Column {
                            Ruler(project, zoom, 26.dp)
                            project.layers.forEach { layer ->
                                ClipBar(layer, project, zoom, rowHeight, density, state, vm)
                            }
                        }
                        Box(
                            Modifier
                                .offset(x = (state.playheadMs / 1000f * zoom).dp)
                                .width(2.dp)
                                .height(totalHeight)
                                .background(Color(0xFFFF5A7A))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Ruler(project: Project, zoom: Float, height: Dp) {
    val stepMs = if (zoom > 140f) 250L else if (zoom > 70f) 500L else 1000L
    Box(Modifier.fillMaxWidth().height(height).background(MaterialTheme.colorScheme.surfaceVariant)) {
        var t = 0L
        while (t <= project.durationMs) {
            val x = (t / 1000f * zoom).dp
            Box(Modifier.offset(x = x).width(1.dp).height(height).background(Color(0xFF454A63)))
            Text("${t / 1000f}s", fontSize = 8.sp,
                modifier = Modifier.offset(x = x + 2.dp, y = 5.dp), color = Color(0xFF9AA0BC))
            t += stepMs
        }
    }
}

@Composable
private fun LayerLabel(layer: Layer, index: Int, state: EditorUiState, vm: EditorViewModel, height: Dp) {
    Row(
        modifier = Modifier
            .height(height).fillMaxWidth()
            .background(if (layer.id == state.selectedLayerId) Color(0xFF262945) else MaterialTheme.colorScheme.surface)
            .border(0.5.dp, Color(0xFF2A2E40))
            .pointerInput(Unit) { },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("${index + 1}", fontSize = 9.sp, color = Color.Gray, modifier = Modifier.width(14.dp))
        Icon(
            when (layer.type) {
                LayerType.TEXT -> Icons.Default.TextFields
                LayerType.SHAPE, LayerType.VECTOR -> Icons.Default.Category
                LayerType.IMAGE -> Icons.Default.Image
                LayerType.VIDEO -> Icons.Default.Movie
                LayerType.AUDIO -> Icons.Default.Audiotrack
                else -> Icons.Default.Layers
            },
            null, Modifier.size(14.dp), tint = Color(0xFFB9AEFF)
        )
        Spacer(Modifier.width(4.dp))
        Text(layer.name, fontSize = 10.sp, maxLines = 1, modifier = Modifier.weight(1f)
            .pointerInput(Unit) { detectDragGestures { _, _ -> } })
        IconButton(onClick = { vm.toggleVisible(layer.id) }, modifier = Modifier.size(24.dp)) {
            Icon(if (layer.visible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                null, Modifier.size(13.dp))
        }
        IconButton(onClick = { vm.toggleLock(layer.id) }, modifier = Modifier.size(24.dp)) {
            Icon(if (layer.locked) Icons.Default.Lock else Icons.Default.LockOpen,
                null, Modifier.size(13.dp))
        }
    }
}

@Composable
private fun ClipBar(
    layer: Layer,
    project: Project,
    zoom: Float,
    height: Dp,
    density: Float,
    state: EditorUiState,
    vm: EditorViewModel
) {
    val left = (layer.startMs / 1000f * zoom).dp
    val width = (layer.durationMs / 1000f * zoom).dp.coerceAtLeast(8.dp)
    val selected = layer.id == state.selectedLayerId
    val color = when (layer.type) {
        LayerType.TEXT -> Color(0xFF8B3F70)
        LayerType.IMAGE -> Color(0xFF2C6A48)
        LayerType.VIDEO -> Color(0xFF1D6A80)
        LayerType.AUDIO -> Color(0xFF7A6324)
        LayerType.SOLID -> Color(0xFF393D5C)
        else -> Color(0xFF4D4483)
    }
    Box(Modifier.fillMaxWidth().height(height).border(0.5.dp, Color(0xFF232737))) {
        Box(
            Modifier
                .offset(x = left)
                .width(width)
                .height(height - 8.dp)
                .offset(y = 4.dp)
                .background(color, RoundedCornerShape(5.dp))
                .border(if (selected) 1.5.dp else 0.5.dp,
                    if (selected) Color(0xFFC4B5FD) else Color(0xFF6F61B8), RoundedCornerShape(5.dp))
                .pointerInput(layer.id, zoom) {
                    detectDragGestures(
                        onDragStart = { vm.selectLayer(layer.id) },
                        onDragEnd = { },
                        onDrag = { _, drag ->
                            val deltaMs = (drag.x / density / zoom * 1000f).roundToLong()
                            vm.moveClip(layer.id, deltaMs)
                        }
                    )
                }
        ) {
            Text(layer.name, fontSize = 9.sp, color = Color.White,
                modifier = Modifier.padding(start = 10.dp, top = 7.dp), maxLines = 1)
            // Trim handles
            TrimHandle(Modifier.align(Alignment.CenterStart), zoom, density) { d -> vm.trimStart(layer.id, d) }
            TrimHandle(Modifier.align(Alignment.CenterEnd), zoom, density) { d -> vm.trimEnd(layer.id, d) }
            if (selected) {
                layer.keyframes.flatMap { it.keyframes }.forEach { kf ->
                    Box(
                        Modifier
                            .offset(x = (kf.timeMs / 1000f * zoom).dp, y = (height - 14.dp))
                            .size(6.dp)
                            .background(Color.Yellow, RoundedCornerShape(1.dp))
                    )
                }
            }
        }
    }
}

@Composable
private fun TrimHandle(modifier: Modifier, zoom: Float, density: Float, onDrag: (Long) -> Unit) {
    Box(
        modifier
            .width(9.dp)
            .height(24.dp)
            .background(Color(0x66FFFFFF), RoundedCornerShape(3.dp))
            .pointerInput(zoom) {
                detectDragGestures(onDrag = { _, drag ->
                    onDrag((drag.x / density / zoom * 1000f).roundToLong())
                })
            }
    )
}
