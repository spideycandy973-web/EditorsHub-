package com.editorshub.ui.feature.editor

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.editorshub.ui.theme.AppMotion
import com.editorshub.ui.theme.EditorPalette
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.editorshub.core.domain.model.ExportConfig
import com.editorshub.core.domain.model.ExportResolution
import com.editorshub.core.domain.model.VideoCodec
import com.editorshub.core.domain.timeline.TimelineMath
import com.editorshub.ui.components.LabeledSelect
import com.editorshub.ui.components.SliderRow

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun EditorScreen(
    vm: EditorViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val state by vm.ui.collectAsState()
    var showExport by remember { mutableStateOf(false) }
    var fullscreen by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = EditorPalette.Black,
        topBar = {
            var moreExpanded by remember { mutableStateOf(false) }
            TopAppBar(
                title = {
                    Column {
                        Text(state.project?.name ?: "Loading…", fontSize = 16.sp,
                            color = EditorPalette.TextPrimary)
                        state.project?.let {
                            Text("${it.width}×${it.height} · ${it.fps}fps · ${it.layers.size} layers",
                                fontSize = 9.sp, color = EditorPalette.TextSecondary)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = EditorPalette.TextPrimary)
                    }
                },
                actions = {
                    GuidesMenu(state, vm)
                    IconButton(onClick = { moreExpanded = true }) {
                        Icon(Icons.Default.MoreVert, "More", tint = EditorPalette.TextPrimary)
                    }
                    DropdownMenu(expanded = moreExpanded, onDismissRequest = { moreExpanded = false }) {
                        DropdownMenuItem(text = { Text("Undo", fontSize = 11.sp) },
                            enabled = state.undoAvailable,
                            onClick = { vm.undo(); moreExpanded = false })
                        DropdownMenuItem(text = { Text("Redo", fontSize = 11.sp) },
                            enabled = state.redoAvailable,
                            onClick = { vm.redo(); moreExpanded = false })
                        DropdownMenuItem(text = { Text("Zoom to fit", fontSize = 11.sp) },
                            onClick = { vm.zoomToFit(); moreExpanded = false })
                        DropdownMenuItem(text = { Text("Zoom to selection", fontSize = 11.sp) },
                            onClick = { vm.zoomToSelection(); moreExpanded = false })
                        DropdownMenuItem(text = { Text("Save project", fontSize = 11.sp) },
                            onClick = { vm.saveNow(); moreExpanded = false })
                        DropdownMenuItem(text = { Text("Fullscreen preview", fontSize = 11.sp) },
                            onClick = { fullscreen = true; moreExpanded = false })
                        DropdownMenuItem(text = { Text(if (state.gpuPreview) "GPU preview: ON" else "GPU preview: OFF", fontSize = 11.sp) },
                            onClick = { vm.setGpuPreview(!state.gpuPreview); moreExpanded = false })
                        DropdownMenuItem(text = { Text(if (state.snapping) "Snapping: ON" else "Snapping: OFF", fontSize = 11.sp) },
                            onClick = { vm.setSnapping(!state.snapping); moreExpanded = false })
                    }
                    IconButton(onClick = { showExport = true }) {
                        Icon(Icons.Default.Share, "Export", tint = EditorPalette.Purple)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = EditorPalette.Charcoal,
                    titleContentColor = EditorPalette.TextPrimary,
                    navigationIconContentColor = EditorPalette.TextPrimary,
                    actionIconContentColor = EditorPalette.TextPrimary
                )
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().background(EditorPalette.Black)) {
            PreviewSurface(state, Modifier.fillMaxWidth().weight(1f))
            PlayerBar(state, vm)
            Box(Modifier.fillMaxWidth().height(180.dp)) {
                TimelineEditor(state, vm, Modifier.fillMaxSize())
                FloatingActionButton(
                    onClick = { vm.setTab(EditorTab.ADD) },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp).size(48.dp),
                    containerColor = EditorPalette.Purple,
                    contentColor = EditorPalette.Black,
                    shape = RoundedCornerShape(13.dp)
                ) { Icon(Icons.Default.Add, "Add layer") }
            }
            Inspector(state, vm, Modifier.fillMaxWidth().weight(1f))
        }
        state.message?.let { msg ->
            Snackbar(modifier = Modifier.padding(8.dp)) { Text(msg, fontSize = 11.sp) }
        }
    }

    if (fullscreen) {
        Dialog(onDismissRequest = { fullscreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                PreviewSurface(state, Modifier.fillMaxSize())
                IconButton(onClick = { fullscreen = false },
                    modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)) {
                    Icon(Icons.Default.Close, "Close fullscreen", tint = Color.White)
                }
            }
        }
    }

    if (showExport) ExportDialog(state, vm) { showExport = false }
}

/** Alight-Motion-style control bar: transport icons, ruler and timecode. */
@Composable
private fun PlayerBar(state: EditorUiState, vm: EditorViewModel) {
    val p = state.project ?: return
    val pad = { v: Long -> v.toString().padStart(2, '0') }
    val totalSeconds = state.playheadMs / 1000L
    val frames = ((state.playheadMs % 1000L) * p.fps / 1000L).coerceAtLeast(0L)
    val timecode = "${pad(totalSeconds / 60)}:${pad(totalSeconds % 60)}:${pad(frames)}"

    Column(
        Modifier.fillMaxWidth().height(64.dp).background(EditorPalette.Charcoal)
    ) {
        Row(
            Modifier.fillMaxWidth().height(38.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            IconButton(onClick = { vm.undo() }, enabled = state.undoAvailable, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Default.Undo, "Undo", Modifier.size(17.dp), tint = EditorPalette.TextPrimary)
            }
            IconButton(onClick = { vm.redo() }, enabled = state.redoAvailable, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Default.Redo, "Redo", Modifier.size(17.dp), tint = EditorPalette.TextPrimary)
            }
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = { vm.seek(0L) }, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Default.SkipPrevious, "Go to start", Modifier.size(18.dp), tint = EditorPalette.TextPrimary)
            }
            IconButton(onClick = { vm.togglePlay() }, modifier = Modifier.size(36.dp)) {
                AnimatedContent(targetState = state.playing, transitionSpec = {
                    scaleIn(animationSpec = spring()) + fadeIn() togetherWith
                        scaleOut(animationSpec = spring()) + fadeOut()
                }, label = "play-pause") { playing ->
                    Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        "Play / pause", Modifier.size(20.dp),
                        tint = if (playing) EditorPalette.Purple else EditorPalette.TextPrimary)
                }
            }
            IconButton(onClick = { vm.seek(p.durationMs) }, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Default.SkipNext, "Go to end", Modifier.size(18.dp), tint = EditorPalette.TextPrimary)
            }
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = { state.selectedLayerId?.let { vm.duplicateLayer(it) } }, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Default.ContentCopy, "Duplicate layer", Modifier.size(16.dp), tint = EditorPalette.TextPrimary)
            }
            IconButton(onClick = { vm.setPreviewZoom(1f) }, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Default.FitScreen, "Fit preview", Modifier.size(17.dp), tint = EditorPalette.TextPrimary)
            }
        }
        Box(Modifier.fillMaxWidth().height(26.dp).background(EditorPalette.SurfaceRaised)) {
            Canvas(Modifier.fillMaxSize()) {
                val step = 18.dp.toPx()
                var x = 0f
                while (x < size.width) {
                    drawLine(Color(0xFF5A6684), Offset(x, 0f), Offset(x, 10f), strokeWidth = 1f)
                    drawLine(Color(0xFF3D4860), Offset(x, 10f), Offset(x, 15f), strokeWidth = 1f)
                    x += step
                }
            }
            Text(
                timecode,
                fontSize = 11.sp,
                color = EditorPalette.TextPrimary,
                modifier = Modifier.align(Alignment.Center)
                    .background(Color(0xFF303A50), RoundedCornerShape(5.dp))
                    .padding(horizontal = 9.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun ExportDialog(state: EditorUiState, vm: EditorViewModel, onDismiss: () -> Unit) {
    val p = state.project ?: return
    val context = LocalContext.current
    var resolution by remember { mutableStateOf(state.defaultResolution) }
    var fps by remember { mutableIntStateOf(state.defaultFps) }
    var bitrateMbps by remember { mutableFloatStateOf(state.defaultBitrate / 1_000_000f) }
    var codec by remember { mutableStateOf(state.defaultCodec) }
    var audio by remember { mutableStateOf(state.defaultIncludeAudio) }
    var quality by remember { mutableIntStateOf(state.defaultQuality) }
    var saveLocation by remember { mutableStateOf(state.defaultSaveLocation) }

    val shortSide = resolution.shortSide
    val ratio = p.width.toFloat() / p.height.toFloat()
    val (w, h) = if (ratio >= 1f) shortSide to (shortSide / ratio).toInt()
        else (shortSide * ratio).toInt() to shortSide

    val export = state.export
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export video") },
        text = {
            Column {
                LabeledSelect("Resolution", resolution.name,
                    ExportResolution.entries.map { it.name to it.label }) { resolution = ExportResolution.valueOf(it) }
                Text("Output ${w}×${h}", fontSize = 10.sp)
                LabeledSelect("Frame rate", fps.toString(),
                    listOf("24", "30", "60").map { it to "$it fps" }) { fps = it.toInt() }
                SliderRow("Bitrate (Mbps)", bitrateMbps, 1f..30f, { bitrateMbps = it })
                SliderRow("Quality", quality.toFloat(), 10f..100f, { quality = it.toInt() })
                LabeledSelect("Codec", codec.name,
                    VideoCodec.entries.map { it.name to it.name }) { codec = VideoCodec.valueOf(it) }
                LabeledSelect("Save location", saveLocation,
                    listOf("gallery" to "Gallery / MediaStore", "app" to "App storage")) { saveLocation = it }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Include audio", fontSize = 11.sp, modifier = Modifier.weight(1f))
                    Switch(checked = audio, onCheckedChange = { audio = it })
                }
                Text("${TimelineMath.frameCount(p.durationMs, fps)} frames will be rendered.",
                    fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                when (export) {
                    is ExportUiState.Running -> {
                        LinearProgressIndicator(progress = { export.percent / 100f }, modifier = Modifier.fillMaxWidth())
                        Text("${export.percent}% · ${export.label}", fontSize = 10.sp)
                    }
                    is ExportUiState.Success -> {
                        Text("Saved: ${export.file}", fontSize = 10.sp, color = Color(0xFF4ADE80))
                        if (export.uri.isNotBlank()) {
                            Button(onClick = {
                                val share = Intent(Intent.ACTION_SEND).apply {
                                    type = "video/mp4"
                                    putExtra(Intent.EXTRA_STREAM, Uri.parse(export.uri))
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(share, "Share video"))
                            }, modifier = Modifier.fillMaxWidth()) { Text("Share", fontSize = 10.sp) }
                        }
                    }
                    is ExportUiState.Failure -> Text(export.message, fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.error)
                    ExportUiState.Idle -> Unit
                }
            }
        },
        confirmButton = {
            if (export is ExportUiState.Running) {
                TextButton(onClick = { vm.cancelExport() }) { Text("Cancel export") }
            } else {
                TextButton(onClick = {
                    vm.startExport(
                        ExportConfig(
                            width = w, height = h, fps = fps, fpsExact = fps.toDouble(),
                            bitrate = (bitrateMbps * 1_000_000 * quality / 100).toInt(),
                            audioBitrate = 128_000,
                            codec = codec, includeAudio = audio,
                            saveLocation = saveLocation
                        )
                    )
                }) { Text("Start export") }
            }
        },
        dismissButton = { TextButton(onClick = { vm.dismissExport(); onDismiss() }) { Text("Close") } }
    )
}


@Composable
private fun GuidesMenu(state: EditorUiState, vm: EditorViewModel) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) { Icon(Icons.Default.GridOn, "Preview guides") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            GuideItem("Transparency checkerboard", state.checkerboard) { vm.setGuide("checkerboard", it) }
            GuideItem("Safe area", state.showSafeArea) { vm.setGuide("safe", it) }
            GuideItem("Grid", state.showGrid) { vm.setGuide("grid", it) }
            GuideItem("Center guides", state.showCenterGuides) { vm.setGuide("center", it) }
            GuideItem("Aspect guides", state.showAspectGuides) { vm.setGuide("aspect", it) }
        }
    }
}

@Composable
private fun GuideItem(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    DropdownMenuItem(
        text = { Text(label, style = androidx.compose.material3.MaterialTheme.typography.bodySmall) },
        onClick = { onChange(!checked) },
        trailingIcon = {
            Icon(if (checked) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                null, Modifier.size(16.dp))
        }
    )
}
