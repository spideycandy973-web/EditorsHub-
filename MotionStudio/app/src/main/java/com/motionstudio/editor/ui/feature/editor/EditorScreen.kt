package com.motionstudio.editor.ui.feature.editor

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.motionstudio.editor.core.domain.model.ExportConfig
import com.motionstudio.editor.core.domain.model.ExportResolution
import com.motionstudio.editor.core.domain.model.VideoCodec
import com.motionstudio.editor.core.domain.timeline.TimelineMath
import com.motionstudio.editor.ui.components.LabeledSelect
import com.motionstudio.editor.ui.components.SliderRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    vm: EditorViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val state by vm.ui.collectAsState()
    var showExport by remember { mutableStateOf(false) }
    var fullscreen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.project?.name ?: "Loading…", fontSize = 14.sp)
                        state.project?.let {
                            Text("${it.width}×${it.height} · ${it.fps}fps · ${it.layers.size} layers",
                                fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = { vm.saveNow() }) { Icon(Icons.Default.Save, "Save") }
                    IconButton(onClick = { fullscreen = true }) { Icon(Icons.Default.Fullscreen, "Fullscreen") }
                    IconButton(onClick = { showExport = true }) { Icon(Icons.Default.FileDownload, "Export") }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            PreviewSurface(state, Modifier.fillMaxWidth().weight(1f))
            PlayerBar(state, vm)
            TimelineEditor(state, vm, Modifier.fillMaxWidth().height(180.dp))
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

@Composable
private fun PlayerBar(state: EditorUiState, vm: EditorViewModel) {
    val p = state.project ?: return
    Row(
        modifier = Modifier.fillMaxWidth().height(48.dp).background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { vm.seek(0L) }, modifier = Modifier.size(30.dp)) {
            Icon(Icons.Default.SkipPrevious, null, Modifier.size(17.dp))
        }
        IconButton(onClick = { vm.stepFrames(-1) }, modifier = Modifier.size(30.dp)) {
            Icon(Icons.Default.KeyboardArrowLeft, null, Modifier.size(18.dp))
        }
        IconButton(onClick = { vm.togglePlay() }, modifier = Modifier.size(34.dp)) {
            Icon(if (state.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                "Play / pause", Modifier.size(20.dp))
        }
        IconButton(onClick = { vm.stepFrames(1) }, modifier = Modifier.size(30.dp)) {
            Icon(Icons.Default.KeyboardArrowRight, null, Modifier.size(18.dp))
        }
        Slider(
            value = state.playheadMs.toFloat(),
            onValueChange = { vm.seek(it.toLong()) },
            valueRange = 0f..p.durationMs.toFloat(),
            modifier = Modifier.weight(1f).height(24.dp)
        )
        Text("${"%.2f".format(state.playheadMs / 1000f)}s", fontSize = 10.sp)
        IconButton(onClick = { vm.setGpuPreview(!state.gpuPreview) }, modifier = Modifier.size(30.dp)) {
            Text("GPU", fontSize = 8.sp, color = if (state.gpuPreview) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = { vm.setSnapping(!state.snapping) }, modifier = Modifier.size(30.dp)) {
            Icon(Icons.Default.Straighten, "Snapping", Modifier.size(15.dp),
                tint = if (state.snapping) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = { vm.setPreviewZoom(state.previewZoom + 0.1f) }, modifier = Modifier.size(30.dp)) {
            Icon(Icons.Default.ZoomIn, "Zoom", Modifier.size(15.dp))
        }
        IconButton(onClick = { vm.setPreviewZoom(1f) }, modifier = Modifier.size(30.dp)) {
            Icon(Icons.Default.ZoomOut, "Reset zoom", Modifier.size(15.dp))
        }
    }
}

@Composable
private fun ExportDialog(state: EditorUiState, vm: EditorViewModel, onDismiss: () -> Unit) {
    val p = state.project ?: return
    val context = LocalContext.current
    var resolution by remember { mutableStateOf(ExportResolution.P1080) }
    var fps by remember { mutableIntStateOf(30) }
    var bitrateMbps by remember { mutableFloatStateOf(8f) }
    var codec by remember { mutableStateOf(VideoCodec.H264) }
    var audio by remember { mutableStateOf(true) }

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
                LabeledSelect("Codec", codec.name,
                    VideoCodec.entries.map { it.name to it.name }) { codec = VideoCodec.valueOf(it) }
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
                            width = w, height = h, fps = fps,
                            bitrate = (bitrateMbps * 1_000_000).toInt(),
                            codec = codec, includeAudio = audio
                        )
                    )
                }) { Text("Start export") }
            }
        },
        dismissButton = { TextButton(onClick = { vm.dismissExport(); onDismiss() }) { Text("Close") } }
    )
}
