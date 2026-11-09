package com.editorshub.ui.feature.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import com.editorshub.core.domain.model.AudioTrack
import com.editorshub.core.domain.model.Layer
import com.editorshub.core.domain.model.LayerType
import com.editorshub.core.domain.model.Project
import com.editorshub.core.domain.timeline.TimelineMath
import com.editorshub.ui.theme.EditorPalette
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
    val timelineDurationMs = project.durationMs.coerceAtLeast(1L)
    val contentWidth: Dp = ((timelineDurationMs / 1000f) * zoom + 100f).dp.coerceAtLeast(220.dp)
    val rowHeight = 36.dp
    val totalTracks = project.layers.size + project.audioTracks.size
    val rows = totalTracks.coerceAtLeast(1)
    val latestPlayhead = rememberUpdatedState(state.playheadMs)
    val hScroll = rememberScrollState()
    val vScroll = rememberScrollState()
    var menuExpanded by remember { mutableStateOf(false) }
    val totalSeconds = state.playheadMs / 1000L
    val frames = ((state.playheadMs % 1000L) * project.fps / 1000L).coerceAtLeast(0L)
    val timecode = "%02d:%02d:%02d".format(totalSeconds / 60, totalSeconds % 60, frames)

    Column(modifier = modifier.background(EditorPalette.Charcoal)) {
        Row(Modifier.fillMaxWidth().height(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.width(132.dp).fillMaxHeight().background(EditorPalette.Surface),
                verticalAlignment = Alignment.CenterVertically) {
                Text(" LAYERS · $totalTracks",
                    fontSize = 8.sp, color = EditorPalette.TextMuted, modifier = Modifier.weight(1f))
                IconButton(onClick = { vm.setZoom(zoom - 16f) }, modifier = Modifier.size(25.dp)) {
                    Icon(Icons.Default.Remove, "Zoom out", Modifier.size(14.dp), tint = EditorPalette.TextSecondary)
                }
                IconButton(onClick = { vm.setZoom(zoom + 16f) }, modifier = Modifier.size(25.dp)) {
                    Icon(Icons.Default.Add, "Zoom in", Modifier.size(14.dp), tint = EditorPalette.TextSecondary)
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(25.dp)) {
                        Icon(Icons.Default.MoreHoriz, "Timeline actions", Modifier.size(16.dp), tint = EditorPalette.TextSecondary)
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(text = { Text("Zoom out") }, onClick = { vm.setZoom(zoom - 16f); menuExpanded = false })
                        DropdownMenuItem(text = { Text("Zoom in") }, onClick = { vm.setZoom(zoom + 16f); menuExpanded = false })
                        DropdownMenuItem(text = { Text("Split at playhead") },
                            enabled = state.selectedLayerId != null,
                            onClick = { state.selectedLayerId?.let(vm::splitLayer); menuExpanded = false })
                        DropdownMenuItem(text = { Text("Duplicate layer") },
                            enabled = state.selectedLayerId != null,
                            onClick = { state.selectedLayerId?.let(vm::duplicateLayer); menuExpanded = false })
                        DropdownMenuItem(text = { Text("Delete layer") },
                            enabled = state.selectedLayerId != null,
                            onClick = { state.selectedLayerId?.let(vm::deleteLayer); menuExpanded = false })
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxHeight().horizontalScroll(hScroll)) {
                Box(Modifier.width(contentWidth).fillMaxHeight()) {
                    Ruler(timelineDurationMs, zoom, 24.dp, vm)
                    Box(Modifier.offset(x = (state.playheadMs / 1000f * zoom).dp)
                        .fillMaxHeight().width(1.dp).background(Color(0x99FFFFFF)))
                    Text(timecode, fontSize = 9.sp, color = EditorPalette.TextPrimary,
                        modifier = Modifier.align(Alignment.CenterStart)
                            .offset(x = (state.playheadMs / 1000f * zoom - 32f).coerceAtLeast(0f).dp)
                            .background(Color(0xFF303034), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }
        }
        Row(Modifier.fillMaxWidth().weight(1f).verticalScroll(vScroll)) {
            Column(Modifier.width(132.dp)) {
                if (totalTracks == 0) {
                    Box(Modifier.height(rowHeight).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                        Text("  No tracks", fontSize = 8.sp, color = EditorPalette.TextMuted)
                    }
                } else {
                    project.layers.forEachIndexed { index, layer ->
                        LayerLabel(layer, index, state, vm, rowHeight)
                    }
                    project.audioTracks.forEachIndexed { index, track ->
                        AudioTrackLabel(track, project.layers.size + index, vm, rowHeight)
                    }
                }
            }
            Box(Modifier.weight(1f).horizontalScroll(hScroll)) {
                Box(Modifier.width(contentWidth).height(rowHeight * rows)) {
                    Column {
                        if (totalTracks == 0) {
                            Box(Modifier.fillMaxWidth().height(rowHeight).border(0.5.dp, EditorPalette.OutlineSoft))
                        } else {
                            project.layers.forEach { layer ->
                                ClipBar(layer, project, zoom, rowHeight, density, state, vm)
                            }
                            project.audioTracks.forEach { track ->
                                AudioClipBar(track, zoom, rowHeight, density, vm)
                            }
                        }
                    }
                    Box(
                        Modifier.offset(x = (state.playheadMs / 1000f * zoom - 9f).coerceAtLeast(-9f).dp)
                            .fillMaxHeight().width(18.dp)
                            .pointerInput(zoom, timelineDurationMs) {
                                var currentMs = 0L
                                detectDragGestures(
                                    onDragStart = { currentMs = latestPlayhead.value; vm.pause() },
                                    onDrag = { change, drag ->
                                        change.consume()
                                        currentMs = (currentMs + (drag.x / density / zoom * 1000f).roundToLong())
                                            .coerceIn(0L, timelineDurationMs)
                                        vm.seek(currentMs)
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(Modifier.fillMaxHeight().width(2.dp).background(Color.White))
                    }
                }
            }
        }
    }
}

@Composable
private fun Ruler(durationMs: Long, zoom: Float, height: Dp, vm: EditorViewModel) {
    val stepMs = if (zoom > 140f) 250L else if (zoom > 70f) 500L else 1000L
    val density = LocalDensity.current.density
    Box(Modifier.fillMaxWidth().height(height)
        .background(MaterialTheme.colorScheme.surfaceVariant)
        .pointerInput(durationMs, zoom) {
            detectTapGestures { offset ->
                val timeMs = (offset.x / density / zoom * 1000f).roundToLong()
                vm.seek(timeMs.coerceIn(0L, durationMs))
            }
        }) {
        var t = 0L
        while (t <= durationMs) {
            val x = (t / 1000f * zoom).dp
            Box(Modifier.offset(x = x).width(1.dp).height(height).background(Color(0xFF454A63)))
            Text("${pad2(t / 60000)}:${pad2(t / 1000 % 60)}", fontSize = 8.sp,
                modifier = Modifier.offset(x = x + 2.dp, y = 5.dp), color = EditorPalette.TextMuted)
            t += stepMs
        }
    }
}

@Composable
private fun LayerLabel(layer: Layer, index: Int, state: EditorUiState, vm: EditorViewModel, height: Dp) {
    Row(
        modifier = Modifier
            .height(height).fillMaxWidth()
            .background(if (layer.id == state.selectedLayerId) Color(0xFF243247) else EditorPalette.Surface)
            .border(0.5.dp, EditorPalette.OutlineSoft)
            .clickable { vm.selectLayer(layer.id) },
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
            null, Modifier.size(14.dp), tint = EditorPalette.Purple
        )
        Spacer(Modifier.width(4.dp))
        Text(layer.name, fontSize = 9.sp, maxLines = 1, color = if (layer.id == state.selectedLayerId) Color.White else EditorPalette.TextSecondary, modifier = Modifier.weight(1f))
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
private fun AudioTrackLabel(track: AudioTrack, index: Int, vm: EditorViewModel, height: Dp) {
    Row(
        modifier = Modifier
            .height(height).fillMaxWidth()
            .background(EditorPalette.Surface)
            .border(0.5.dp, EditorPalette.OutlineSoft)
            .clickable { vm.setTab(EditorTab.AUDIO) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("${index + 1}", fontSize = 9.sp, color = Color.Gray, modifier = Modifier.width(14.dp))
        Icon(Icons.Default.Audiotrack, null, Modifier.size(14.dp), tint = Color(0xFFB58CFF))
        Spacer(Modifier.width(4.dp))
        Text(track.name, fontSize = 9.sp, maxLines = 1, modifier = Modifier.weight(1f))
        IconButton(onClick = { vm.toggleMuteAudio(track.id) }, modifier = Modifier.size(24.dp)) {
            Icon(
                if (track.muted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                if (track.muted) "Unmute ${track.name}" else "Mute ${track.name}",
                Modifier.size(13.dp),
                tint = if (track.muted) EditorPalette.TextMuted else EditorPalette.TextSecondary
            )
        }
    }
}

@Composable
private fun AudioClipBar(track: AudioTrack, zoom: Float, height: Dp, density: Float, vm: EditorViewModel) {
    val left = (track.startMs / 1000f * zoom).dp
    val width = (track.durationMs / 1000f * zoom).dp.coerceAtLeast(8.dp)
    Box(Modifier.fillMaxWidth().height(height).border(0.5.dp, EditorPalette.OutlineSoft)) {
        Box(
            Modifier.offset(x = left).width(width).height(height - 8.dp).offset(y = 4.dp)
                .background(Color(0xFF6942A8), RoundedCornerShape(5.dp))
                .border(0.75.dp, Color(0xFFB58CFF), RoundedCornerShape(5.dp))
                .pointerInput(track.id, zoom) {
                    detectDragGestures(
                        onDragStart = { vm.setTab(EditorTab.AUDIO) },
                        onDrag = { _, drag ->
                            val deltaMs = (drag.x / density / zoom * 1000f).roundToLong()
                            vm.moveAudioTrack(track.id, deltaMs)
                        }
                    )
                }
        ) {
            Canvas(Modifier.fillMaxSize().padding(horizontal = 3.dp)) {
                if (track.peaks.isEmpty()) {
                    drawLine(Color(0x99E9D5FF), Offset(0f, size.height / 2),
                        Offset(size.width, size.height / 2), strokeWidth = 1f)
                } else {
                    val step = size.width / track.peaks.size.coerceAtLeast(1)
                    track.peaks.forEachIndexed { i, peak ->
                        val amplitude = peak.coerceIn(0f, 1f) * size.height * 0.8f
                        val x = i * step + step / 2f
                        drawLine(Color(0xCCEEE5FF), Offset(x, (size.height - amplitude) / 2f),
                            Offset(x, (size.height + amplitude) / 2f), strokeWidth = step.coerceAtLeast(1f))
                    }
                }
            }
            Text(track.name, fontSize = 8.sp, color = Color.White,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 6.dp), maxLines = 1)
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
        LayerType.TEXT -> Color(0xFFF5A623)
        LayerType.IMAGE, LayerType.VIDEO -> Color(0xFF3D8BEF)
        LayerType.AUDIO -> Color(0xFF9B6CE0)
        LayerType.SOLID -> Color(0xFFF06A6A)
        LayerType.SHAPE, LayerType.VECTOR -> Color(0xFF10B89A)
        else -> Color(0xFF5A6478)
    }
    Box(Modifier.fillMaxWidth().height(height).border(0.5.dp, EditorPalette.OutlineSoft)) {
        Box(
            Modifier
                .offset(x = left)
                .width(width)
                .height(height - 8.dp)
                .offset(y = 4.dp)
                .background(color, RoundedCornerShape(5.dp))
                .border(if (selected) 1.5.dp else 0.5.dp,
                    if (selected) Color.White else EditorPalette.Outline, RoundedCornerShape(5.dp))
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
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 10.dp, end = 10.dp), maxLines = 1)
            // Trim handles
            TrimHandle(Modifier.align(Alignment.CenterStart), zoom, density) { d -> vm.trimStart(layer.id, d) }
            TrimHandle(Modifier.align(Alignment.CenterEnd), zoom, density) { d -> vm.trimEnd(layer.id, d) }
            if (selected) {
                layer.keyframes.flatMap { it.keyframes }.forEach { kf ->
                    Box(
                        Modifier
                            .offset(x = (kf.timeMs / 1000f * zoom).dp, y = (height - 14.dp))
                            .size(7.dp)
                            .background(EditorPalette.Cyan, RoundedCornerShape(1.dp))
                            .border(0.5.dp, Color.White.copy(alpha = 0.75f), RoundedCornerShape(1.dp))
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

private fun pad2(v: Long): String = v.toString().padStart(2, '0')
