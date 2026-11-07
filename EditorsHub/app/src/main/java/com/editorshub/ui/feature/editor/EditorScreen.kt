package com.editorshub.ui.feature.editor

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.editorshub.core.importer.NativeXmlExporter
import com.editorshub.ui.components.LabeledSelect
import com.editorshub.ui.components.SliderRow

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun EditorScreen(
    vm: EditorViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val state by vm.ui.collectAsState()
    val context = LocalContext.current
    var showExport by remember { mutableStateOf(false) }
    var fullscreen by remember { mutableStateOf(false) }
    var showShapePicker by remember { mutableStateOf(false) }
    val xmlExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/xml")
    ) { uri ->
        if (uri != null) {
            val result = runCatching {
                val project = state.project ?: error("Project is not ready")
                val output = context.contentResolver.openOutputStream(uri) ?: error("Cannot open destination")
                output.use { it.write(NativeXmlExporter.encode(project).toByteArray(Charsets.UTF_8)) }
            }
            Toast.makeText(context,
                if (result.isSuccess) "Project XML exported" else "XML export failed: ${result.exceptionOrNull()?.message}",
                Toast.LENGTH_LONG).show()
        }
    }

    Box(Modifier.fillMaxSize().background(EditorPalette.Black)) {
        Column(Modifier.fillMaxSize().background(EditorPalette.Black)) {
            // The reference has an intentional dark breathing strip above its short toolbar.
            Spacer(Modifier.height(34.dp))
            EditorTopBar(
                state = state,
                vm = vm,
                onBack = {
                    if (state.tab in setOf(EditorTab.BORDER, EditorTab.BLEND, EditorTab.COLOR,
                            EditorTab.TRANSFORM, EditorTab.SHAPE, EditorTab.KEYFRAMES,
                            EditorTab.EFFECTS, EditorTab.TEXT, EditorTab.VECTOR, EditorTab.MASKS)) {
                        vm.setTab(EditorTab.PROPERTIES)
                    } else onBack()
                },
                onExport = { showExport = true },
                onXmlExport = {
                    val name = (state.project?.name ?: "Project").replace(Regex("[^A-Za-z0-9_-]"), "_")
                    xmlExportLauncher.launch("$name.xml")
                },
                onFullscreen = { fullscreen = true }
            )
            Box(Modifier.weight(1f).fillMaxWidth().background(EditorPalette.Black)) {
                PreviewSurface(state, Modifier.fillMaxSize())
                IconButton(
                    onClick = {
                        vm.setPreviewZoom(if (state.previewZoom > 1.25f) 1f else state.previewZoom + 0.25f)
                    },
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                        .size(38.dp).background(EditorPalette.SurfaceRaised, RoundedCornerShape(6.dp))
                ) { Icon(Icons.Default.ZoomIn, "Zoom preview", tint = EditorPalette.TextPrimary) }
            }
            PlayerBar(state, vm)
            TimelineEditor(state, vm, Modifier.fillMaxWidth().height(86.dp))
            Inspector(state, vm, Modifier.fillMaxWidth().height(228.dp),
                onShapePicker = { showShapePicker = true })
        }
        state.message?.let { msg ->
            Snackbar(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 236.dp, start = 10.dp, end = 10.dp)) {
                Text(msg, fontSize = 11.sp)
            }
        }
    }

    if (showShapePicker) {
        ShapePickerDialog(
            onDismiss = { showShapePicker = false },
            onPick = { choice ->
                vm.addLayer(com.editorshub.core.domain.model.LayerType.SHAPE, choice.kind)
                if (choice.sides != null || choice.starPoints != null) {
                    vm.updateShape { shape -> shape.copy(
                        sides = choice.sides ?: shape.sides,
                        starPoints = choice.starPoints ?: shape.starPoints
                    ) }
                }
                showShapePicker = false
            }
        )
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
private fun EditorTopBar(
    state: EditorUiState,
    vm: EditorViewModel,
    onBack: () -> Unit,
    onExport: () -> Unit,
    onXmlExport: () -> Unit,
    onFullscreen: () -> Unit
) {
    var moreExpanded by remember { mutableStateOf(false) }
    val title = when (state.tab) {
        EditorTab.BORDER -> "Border & Shadow"
        EditorTab.BLEND -> "Blending & Opacity"
        EditorTab.TRANSFORM -> "Move & Transform"
        EditorTab.SHAPE -> "Edit Shape"
        EditorTab.COLOR -> "Color & Fill"
        EditorTab.KEYFRAMES -> "Keyframes"
        EditorTab.EFFECTS -> "Effects"
        EditorTab.TEXT -> "Text"
        EditorTab.VECTOR -> "Vector Path"
        EditorTab.MASKS -> "Masks"
        EditorTab.AUDIO -> "Audio"
        EditorTab.TRANSITIONS -> "Transitions"
        EditorTab.PROJECT -> "Project Settings"
        else -> state.project?.name ?: "Loading…"
    }
    Row(
        Modifier.fillMaxWidth().height(42.dp).background(EditorPalette.SurfaceRaised),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
            Icon(Icons.Default.ArrowBack, "Back", tint = EditorPalette.TextPrimary)
        }
        Text(title, modifier = Modifier.weight(1f), fontSize = 13.sp,
            color = EditorPalette.TextPrimary, maxLines = 1)
        GuidesMenu(state, vm)
        IconButton(onClick = { vm.setTab(EditorTab.PROJECT) }, modifier = Modifier.size(38.dp)) {
            Icon(Icons.Default.Settings, "Project settings", Modifier.size(19.dp), tint = EditorPalette.TextSecondary)
        }
        Box {
            IconButton(onClick = { moreExpanded = true }, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Default.MoreVert, "More", Modifier.size(19.dp), tint = EditorPalette.TextSecondary)
            }
            DropdownMenu(expanded = moreExpanded, onDismissRequest = { moreExpanded = false }) {
                DropdownMenuItem(text = { Text("Undo", fontSize = 11.sp) }, enabled = state.undoAvailable,
                    onClick = { vm.undo(); moreExpanded = false })
                DropdownMenuItem(text = { Text("Redo", fontSize = 11.sp) }, enabled = state.redoAvailable,
                    onClick = { vm.redo(); moreExpanded = false })
                DropdownMenuItem(text = { Text("Zoom to fit", fontSize = 11.sp) },
                    onClick = { vm.zoomToFit(); moreExpanded = false })
                DropdownMenuItem(text = { Text("Zoom to selection", fontSize = 11.sp) },
                    onClick = { vm.zoomToSelection(); moreExpanded = false })
                DropdownMenuItem(text = { Text("Save project", fontSize = 11.sp) },
                    onClick = { vm.saveNow(); moreExpanded = false })
                DropdownMenuItem(text = { Text("Export project XML", fontSize = 11.sp) },
                    onClick = { onXmlExport(); moreExpanded = false })
                DropdownMenuItem(text = { Text("Fullscreen preview", fontSize = 11.sp) },
                    onClick = { onFullscreen(); moreExpanded = false })
                DropdownMenuItem(text = { Text(if (state.gpuPreview) "GPU preview: ON" else "GPU preview: OFF", fontSize = 11.sp) },
                    onClick = { vm.setGpuPreview(!state.gpuPreview); moreExpanded = false })
                DropdownMenuItem(text = { Text(if (state.snapping) "Snapping: ON" else "Snapping: OFF", fontSize = 11.sp) },
                    onClick = { vm.setSnapping(!state.snapping); moreExpanded = false })
            }
        }
        IconButton(onClick = onExport, modifier = Modifier.size(42.dp)) {
            Icon(Icons.Default.Share, "Export video", tint = EditorPalette.Purple)
        }
    }
}

/** Compact transport row matching the supplied editor. */
@Composable
private fun PlayerBar(state: EditorUiState, vm: EditorViewModel) {
    val p = state.project ?: return
    Row(
        Modifier.fillMaxWidth().height(48.dp).background(EditorPalette.Charcoal),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        IconButton(onClick = { vm.undo() }, enabled = state.undoAvailable, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Default.Undo, "Undo", Modifier.size(21.dp), tint = EditorPalette.TextPrimary)
        }
        IconButton(onClick = { vm.redo() }, enabled = state.redoAvailable, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Default.Redo, "Redo", Modifier.size(21.dp), tint = EditorPalette.TextMuted)
        }
        IconButton(onClick = { vm.seek(0L) }, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Default.SkipPrevious, "Go to start", Modifier.size(23.dp), tint = EditorPalette.TextPrimary)
        }
        IconButton(onClick = { vm.togglePlay() }, modifier = Modifier.size(40.dp)) {
            AnimatedContent(targetState = state.playing, transitionSpec = {
                scaleIn(animationSpec = spring()) + fadeIn() togetherWith
                    scaleOut(animationSpec = spring()) + fadeOut()
            }, label = "play-pause") { playing ->
                Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    "Play / pause", Modifier.size(25.dp),
                    tint = if (playing) EditorPalette.Purple else EditorPalette.TextPrimary)
            }
        }
        IconButton(onClick = { vm.seek(p.durationMs) }, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Default.SkipNext, "Go to end", Modifier.size(23.dp), tint = EditorPalette.TextPrimary)
        }
        IconButton(onClick = { state.selectedLayerId?.let { vm.duplicateLayer(it) } }, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Default.LibraryAdd, "Duplicate layer", Modifier.size(22.dp), tint = EditorPalette.TextPrimary)
        }
        IconButton(onClick = { vm.setTab(EditorTab.ADD) }, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Default.AddBox, "Add layer", Modifier.size(22.dp), tint = EditorPalette.TextPrimary)
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
