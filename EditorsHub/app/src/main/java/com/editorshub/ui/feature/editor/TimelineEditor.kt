package com.editorshub.ui.feature.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.editorshub.core.domain.model.AudioTrack
import com.editorshub.core.domain.model.Layer
import com.editorshub.core.domain.model.LayerType
import com.editorshub.core.domain.model.Project
import com.editorshub.ui.theme.EditorPalette
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToLong

/**
 * Reference-style mobile timeline: the playhead stays at the centre, the ruler
 * is a dense tick strip, and each track has a compact eye/thumbnail chip on the
 * left. Every gesture still edits the real project: scrub/zoom, move, trim,
 * visibility, keyframes and audio placement.
 */
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
    val totalTracks = project.layers.size + project.audioTracks.size
    val rowHeight = 28.dp
    val rows = totalTracks.coerceAtLeast(1)
    val latestPlayhead = rememberUpdatedState(state.playheadMs)
    val latestZoom = rememberUpdatedState(zoom)
    val vScroll = rememberScrollState()
    val totalSeconds = state.playheadMs / 1000L
    val frames = ((state.playheadMs % 1000L) * project.fps / 1000L).coerceAtLeast(0L)
    val timecode = "%02d:%02d:%02d".format(totalSeconds / 60, totalSeconds % 60, frames)

    BoxWithConstraints(
        modifier
            .background(EditorPalette.Black)
            .clipToBounds()
    ) {
        val playheadX = maxWidth / 2
        val originDp = playheadX - (state.playheadMs / 1000f * zoom).dp

        // Pan scrubs the playhead; pinch changes the real timeline zoom.
        Box(
            Modifier.fillMaxSize().pointerInput(timelineDurationMs) {
                detectTransformGestures(panZoomLock = false) { _, pan, gestureZoom, _ ->
                    if (gestureZoom != 1f) {
                        vm.setZoom(latestZoom.value * gestureZoom)
                    }
                    if (pan.x != 0f) {
                        val deltaMs = (pan.x / density / latestZoom.value * 1000f).roundToLong()
                        vm.seek((latestPlayhead.value - deltaMs).coerceIn(0L, timelineDurationMs))
                    }
                }
            }
        )

        ReferenceRuler(
            zoom = zoom,
            playheadMs = state.playheadMs,
            durationMs = timelineDurationMs,
            fps = project.fps,
            vm = vm,
            modifier = Modifier.fillMaxWidth().height(22.dp)
        )

        Column(
            Modifier.fillMaxWidth().padding(top = 28.dp).verticalScroll(vScroll).clipToBounds()
        ) {
            if (totalTracks == 0) {
                Box(Modifier.fillMaxWidth().height(rowHeight), contentAlignment = Alignment.CenterStart) {
                    Text("  No tracks", fontSize = 9.sp, color = EditorPalette.TextMuted)
                }
            } else {
                project.layers.forEach { layer ->
                    TrackRow(
                        layer = layer,
                        project = project,
                        state = state,
                        vm = vm,
                        originDp = originDp,
                        zoom = zoom,
                        rowHeight = rowHeight,
                        density = density
                    )
                }
                project.audioTracks.forEach { track ->
                    AudioTrackRow(
                        track = track,
                        originDp = originDp,
                        zoom = zoom,
                        rowHeight = rowHeight,
                        density = density,
                        vm = vm
                    )
                }
            }
        }

        // Compact per-project actions are hidden behind the layer chip's menu.
        LayerChip(
            layer = project.layers.firstOrNull { it.id == state.selectedLayerId }
                ?: project.layers.firstOrNull(),
            project = project,
            state = state,
            vm = vm,
            modifier = Modifier.align(Alignment.TopStart).offset(x = 2.dp, y = 34.dp)
        )

        // Timecode pill sits on the ruler/track boundary like the reference.
        Text(
            timecode,
            Modifier.offset(x = playheadX - 38.dp, y = 22.dp)
                .width(76.dp).height(21.dp)
                .background(Color(0xFF303034), RoundedCornerShape(5.dp))
                .wrapContentHeight(Alignment.CenterVertically),
            fontSize = 10.sp,
            color = EditorPalette.TextPrimary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        // The fixed white playhead crosses the ruler, tracks and empty area.
        Box(
            Modifier.offset(x = playheadX - 0.75.dp).fillMaxHeight().width(1.5.dp)
                .background(Color.White)
                .semantics { contentDescription = "Timeline playhead" }
        )
    }
}

@Composable
private fun ReferenceRuler(
    zoom: Float,
    playheadMs: Long,
    durationMs: Long,
    fps: Int,
    vm: EditorViewModel,
    modifier: Modifier
) {
    val density = LocalDensity.current.density
    val latestPlayhead = rememberUpdatedState(playheadMs)
    Canvas(
        modifier.background(Color(0xFF161618))
            .pointerInput(zoom, durationMs) {
                detectTapGestures { offset ->
                    val deltaMs = ((offset.x - size.width / 2f) / density / zoom * 1000f).roundToLong()
                    vm.seek((latestPlayhead.value + deltaMs).coerceIn(0L, durationMs))
                }
            }
    ) {
        val w = size.width
        val h = size.height
        val secondPx = (zoom * density).coerceAtLeast(1f)
        val playheadSeconds = playheadMs / 1000f
        val subdivisions = fps.coerceIn(10, 60)
        val first = floor((playheadSeconds - w / 2f / secondPx) * subdivisions).toInt()
        val last = ceil((playheadSeconds + w / 2f / secondPx) * subdivisions).toInt()
        for (i in first..last) {
            val major = i % subdivisions == 0
            val x = w / 2f + (i.toFloat() / subdivisions - playheadSeconds) * secondPx
            val tickHeight = if (major) h * 0.75f else h * 0.34f
            drawLine(
                color = if (major) Color(0xFF6A6A70) else Color(0xFF45454A),
                start = Offset(x, 0f),
                end = Offset(x, tickHeight),
                strokeWidth = if (major) 1.4f else 0.8f
            )
        }
        drawLine(Color(0xFF303034), Offset(0f, h - 1f), Offset(w, h - 1f), strokeWidth = 1f)
    }
}

@Composable
private fun TrackRow(
    layer: Layer,
    project: Project,
    state: EditorUiState,
    vm: EditorViewModel,
    originDp: Dp,
    zoom: Float,
    rowHeight: Dp,
    density: Float
) {
    Box(Modifier.fillMaxWidth().height(rowHeight)) {
        ClipBar(
            layer = layer,
            project = project,
            left = originDp + (layer.startMs / 1000f * zoom).dp,
            zoom = zoom,
            height = rowHeight,
            density = density,
            state = state,
            vm = vm
        )
    }
}

@Composable
private fun AudioTrackRow(
    track: AudioTrack,
    originDp: Dp,
    zoom: Float,
    rowHeight: Dp,
    density: Float,
    vm: EditorViewModel
) {
    val left = originDp + (track.startMs / 1000f * zoom).dp
    val width = (track.durationMs / 1000f * zoom).dp.coerceAtLeast(18.dp)
    Box(Modifier.fillMaxWidth().height(rowHeight)) {
        Box(
            Modifier.offset(x = left).width(width).height(rowHeight - 6.dp).offset(y = 3.dp)
                .background(Color(0xFF6942A8), RoundedCornerShape(5.dp))
                .border(0.75.dp, Color(0xFFB58CFF), RoundedCornerShape(5.dp))
                .pointerInput(track.id, zoom) {
                    detectDragGestures(onDrag = { _, drag ->
                        val deltaMs = (drag.x / density / zoom * 1000f).roundToLong()
                        vm.moveAudioTrack(track.id, deltaMs)
                    })
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
private fun LayerChip(
    layer: Layer?,
    project: Project,
    state: EditorUiState,
    vm: EditorViewModel,
    modifier: Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier
            .width(if (menuExpanded) 78.dp else 62.dp)
            .height(28.dp)
            .background(Color(0xFF1C1C1E), RoundedCornerShape(14.dp))
            .border(0.5.dp, Color(0xFF303034), RoundedCornerShape(14.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = { layer?.let { vm.toggleVisible(it.id) } },
            modifier = Modifier.size(28.dp).semantics { contentDescription = "Toggle layer visibility" }
        ) {
            Icon(
                if (layer?.visible != false) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                null, Modifier.size(14.dp), tint = EditorPalette.TextSecondary
            )
        }
        if (layer != null) {
            LayerFilmstrip(project, layer, Modifier.size(25.dp, 21.dp))
        } else {
            Box(Modifier.size(25.dp, 21.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Layers, null, Modifier.size(13.dp), tint = EditorPalette.TextMuted)
            }
        }
        Box {
            IconButton(onClick = { menuExpanded = true },
                modifier = Modifier.size(22.dp).semantics { contentDescription = "Timeline actions" }) {
                Icon(Icons.Default.MoreHoriz, null, Modifier.size(13.dp), tint = EditorPalette.TextMuted)
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(text = { Text("Zoom out", fontSize = 11.sp) },
                    onClick = { vm.setZoom(state.zoomPxPerSecond - 8f); menuExpanded = false })
                DropdownMenuItem(text = { Text("Zoom in", fontSize = 11.sp) },
                    onClick = { vm.setZoom(state.zoomPxPerSecond + 8f); menuExpanded = false })
                DropdownMenuItem(text = { Text("Zoom to fit", fontSize = 11.sp) },
                    onClick = { vm.zoomToFit(); menuExpanded = false })
                DropdownMenuItem(text = { Text(if (state.autoKey) "Auto key: ON" else "Auto key: OFF", fontSize = 11.sp) },
                    onClick = { vm.setAutoKey(!state.autoKey); menuExpanded = false })
                DropdownMenuItem(text = { Text(if (state.snapping) "Frame snapping: ON" else "Frame snapping: OFF", fontSize = 11.sp) },
                    onClick = { vm.setSnapping(!state.snapping); menuExpanded = false })
                DropdownMenuItem(text = { Text("Toggle keyframe at playhead", fontSize = 11.sp) },
                    enabled = state.selectedLayerId != null,
                    onClick = { vm.toggleKeyframe(state.selectedProperty); menuExpanded = false })
                DropdownMenuItem(text = { Text("Split at playhead", fontSize = 11.sp) },
                    enabled = state.selectedLayerId != null,
                    onClick = { state.selectedLayerId?.let(vm::splitLayer); menuExpanded = false })
                DropdownMenuItem(text = { Text("Duplicate layer", fontSize = 11.sp) },
                    enabled = state.selectedLayerId != null,
                    onClick = { state.selectedLayerId?.let(vm::duplicateLayer); menuExpanded = false })
                DropdownMenuItem(text = { Text("Delete layer", fontSize = 11.sp) },
                    enabled = state.selectedLayerId != null,
                    onClick = { state.selectedLayerId?.let(vm::deleteLayer); menuExpanded = false })
            }
        }
    }
}

@Composable
private fun ClipBar(
    layer: Layer,
    project: Project,
    left: Dp,
    zoom: Float,
    height: Dp,
    density: Float,
    state: EditorUiState,
    vm: EditorViewModel
) {
    val width = (layer.durationMs / 1000f * zoom).dp.coerceAtLeast(18.dp)
    val selected = layer.id == state.selectedLayerId
    val frameTolerance = (1000.0 / project.fpsExact.coerceIn(1.0, 240.0)).toLong().coerceAtLeast(8L)
    val localPlayhead = state.playheadMs - layer.startMs
    val hasCurrentKeyframe = layer.keyframes
        .firstOrNull { it.property == state.selectedProperty }
        ?.keyframes
        ?.any { abs(it.timeMs - localPlayhead) <= frameTolerance } ?: false
    val markers = layer.keyframes.flatMap { track ->
        track.keyframes.map { keyframe -> track.property to keyframe }
    }
    val color = when {
        layer.name.startsWith("Camera", ignoreCase = true) -> Color(0xFFD6247A)
        layer.type == LayerType.TEXT -> Color(0xFFF5C542)
        layer.type == LayerType.IMAGE || layer.type == LayerType.VIDEO -> Color(0xFF3D8BEF)
        layer.type == LayerType.AUDIO -> Color(0xFF9B6CE0)
        layer.type == LayerType.SOLID -> Color(0xFFF06A6A)
        layer.type == LayerType.SHAPE || layer.type == LayerType.VECTOR -> Color(0xFF20C6B2)
        else -> Color(0xFF5A6478)
    }
    Box(
        Modifier.offset(x = left).width(width).height(height - 6.dp).offset(y = 3.dp)
            .background(color, RoundedCornerShape(5.dp))
            .border(if (selected) 1.5.dp else 0.5.dp,
                if (selected) Color.White else Color(0xFF5C6470), RoundedCornerShape(5.dp))
    ) {
        LayerFilmstrip(project, layer, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(color.copy(alpha = 0.46f)))
        if (!layer.locked) {
            Box(Modifier.fillMaxSize().padding(horizontal = 9.dp)
                .pointerInput(layer.id, zoom) {
                    detectDragGestures(
                        onDragStart = { vm.selectLayer(layer.id) },
                        onDrag = { _, drag ->
                            val deltaMs = (drag.x / density / zoom * 1000f).roundToLong()
                            vm.moveClip(layer.id, deltaMs)
                        }
                    )
                })
        }
        Text(layer.name, fontSize = 9.sp, color = Color.White,
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 11.dp, end = 10.dp),
            maxLines = 1)
        TrimHandle(Modifier.align(Alignment.CenterStart), zoom, density, enabled = !layer.locked) { d ->
            vm.trimStart(layer.id, d)
        }
        TrimHandle(Modifier.align(Alignment.CenterEnd), zoom, density, enabled = !layer.locked) { d ->
            vm.trimEnd(layer.id, d)
        }
        if (selected && !layer.locked) {
            KeyframeDiamond(
                active = hasCurrentKeyframe,
                modifier = Modifier.align(Alignment.CenterStart).offset(x = (-18).dp),
                contentDescription = if (hasCurrentKeyframe) "Remove keyframe" else "Toggle keyframe",
                onTap = { vm.toggleKeyframe(state.selectedProperty, layer.id) }
            )
        }
        if (selected) {
            markers.forEach { (property, keyframe) ->
                val removeMarker: (() -> Unit)? = if (!layer.locked) {
                    { vm.removeKeyframe(property, keyframe.id) }
                } else null
                KeyframeDiamond(
                    active = true,
                    modifier = Modifier.align(Alignment.CenterStart)
                        .offset(x = (keyframe.timeMs / 1000f * zoom + 10f).dp),
                    contentDescription = "Keyframe at ${layer.startMs + keyframe.timeMs} ms",
                    onTap = {
                        vm.selectLayer(layer.id)
                        vm.setSelectedProperty(property)
                        vm.setTab(EditorTab.KEYFRAMES)
                        vm.seek(layer.startMs + keyframe.timeMs)
                    },
                    onLongPress = removeMarker
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun KeyframeDiamond(
    active: Boolean,
    modifier: Modifier,
    contentDescription: String,
    onTap: () -> Unit,
    onLongPress: (() -> Unit)? = null
) {
    val label = contentDescription
    val tap by rememberUpdatedState(onTap)
    val longPress by rememberUpdatedState(onLongPress)
    Box(
        modifier
            .size(16.dp)
            .combinedClickable(
                onLongClick = longPress,
                onClick = { tap() }
            )
            .semantics { this.contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier.size(11.dp)
                .rotate(45f)
                .background(
                    if (active) EditorPalette.Green else Color(0xFF35353A),
                    RoundedCornerShape(2.dp)
                )
                .border(
                    1.dp,
                    if (active) Color.White else EditorPalette.TextMuted,
                    RoundedCornerShape(2.dp)
                )
        )
    }
}

@Composable
private fun TrimHandle(
    modifier: Modifier,
    zoom: Float,
    density: Float,
    enabled: Boolean = true,
    onDrag: (Long) -> Unit
) {
    val gesture = if (enabled) Modifier.pointerInput(zoom) {
        detectDragGestures(onDrag = { _, drag ->
            onDrag((drag.x / density / zoom * 1000f).roundToLong())
        })
    } else Modifier
    Box(
        modifier.width(8.dp).height(22.dp)
            .background(Color(0x66FFFFFF), RoundedCornerShape(3.dp))
            .then(gesture)
    )
}
