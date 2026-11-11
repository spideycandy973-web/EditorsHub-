package com.editorshub.ui.feature.editor

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.editorshub.core.domain.model.ExportConfig
import com.editorshub.core.domain.model.ExportResolution
import com.editorshub.core.domain.model.LayerType
import com.editorshub.core.domain.model.TextSpec
import com.editorshub.core.domain.model.VideoCodec
import com.editorshub.core.domain.timeline.TimelineMath
import com.editorshub.core.importer.NativeXmlExporter
import com.editorshub.ui.components.LabeledSelect
import com.editorshub.ui.components.SliderRow
import com.editorshub.ui.theme.AppMotion
import com.editorshub.ui.theme.EditorPalette
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun EditorScreen(
    vm: EditorViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onOpenImporter: () -> Unit
) {
    val state by vm.ui.collectAsState()
    val context = LocalContext.current
    var showExport by remember { mutableStateOf(false) }
    var fullscreen by remember { mutableStateOf(false) }
    var showShapePicker by remember { mutableStateOf(false) }
    var addSheetVisible by remember { mutableStateOf(false) }
    var panelVisible by remember { mutableStateOf(false) }
    LaunchedEffect(state.tab, state.selectedLayerId) {
        if (state.tab != EditorTab.ADD || state.selectedLayerId != null) panelVisible = true
    }
    val inspectorVisible = panelVisible && state.tab != EditorTab.ADD && !addSheetVisible
    val fabRotation by animateFloatAsState(
        targetValue = if (addSheetVisible) 45f else 0f,
        animationSpec = spring(dampingRatio = 0.82f),
        label = "add-fab-rotation"
    )

    val xmlExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/xml")
    ) { uri ->
        if (uri != null) {
            val result = runCatching {
                val project = state.project ?: error("Project is not ready")
                val output = context.contentResolver.openOutputStream(uri) ?: error("Cannot open destination")
                output.use { it.write(NativeXmlExporter.encode(project).toByteArray(Charsets.UTF_8)) }
            }
            Toast.makeText(
                context,
                if (result.isSuccess) "Project XML exported" else "XML export failed: ${result.exceptionOrNull()?.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }
    val mediaLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        uris.forEach { uri -> vm.importMedia(uri, null) }
    }
    val audioLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        uris.forEach { uri -> vm.importMedia(uri, null) }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF0E0E10))) {
        Column(Modifier.fillMaxSize()) {
            // Reference has a dark breathing/status strip above its short toolbar.
            Spacer(Modifier.height(34.dp))
            EditorTopBar(
                state = state,
                vm = vm,
                onBack = {
                    if (state.tab in setOf(
                            EditorTab.BORDER, EditorTab.BLEND, EditorTab.COLOR,
                            EditorTab.TRANSFORM, EditorTab.SHAPE, EditorTab.KEYFRAMES,
                            EditorTab.EFFECTS, EditorTab.TEXT, EditorTab.VECTOR,
                            EditorTab.MASKS, EditorTab.AUDIO, EditorTab.TRANSITIONS,
                            EditorTab.PROJECT
                        )) {
                        vm.setTab(EditorTab.PROPERTIES)
                    } else onBack()
                },
                onExport = { showExport = true },
                onShowInspector = { panelVisible = true },
                onXmlExport = {
                    val name = (state.project?.name ?: "Project").replace(Regex("[^A-Za-z0-9_-]"), "_")
                    xmlExportLauncher.launch("$name.xml")
                },
                onFullscreen = { fullscreen = true }
            )
            Box(Modifier.weight(1f).fillMaxWidth().background(EditorPalette.Black)) {
                PreviewSurface(state, Modifier.fillMaxSize())
                ReferenceZoomButton(
                    zoomed = state.previewZoom > 1.05f,
                    onClick = {
                        vm.setPreviewZoom(if (state.previewZoom > 1.25f) 1f else state.previewZoom + 0.25f)
                    },
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                        .background(EditorPalette.SurfaceRaised, RoundedCornerShape(7.dp))
                        .semantics { contentDescription = "Zoom preview" }
                )
            }
            PlayerBar(state, vm, onFullscreen = { fullscreen = true })
            TimelineEditor(
                state, vm,
                Modifier.fillMaxWidth().height(if (inspectorVisible) 86.dp else 210.dp)
            )
            if (inspectorVisible) {
                Inspector(
                    state = state,
                    vm = vm,
                    modifier = Modifier.fillMaxWidth().height(210.dp),
                    onShapePicker = { showShapePicker = true },
                    onClosePanel = {
                        panelVisible = false
                        vm.setTab(EditorTab.ADD)
                        vm.selectLayer(null)
                    }
                )
            }
        }

        // The Add sheet is a dimmed bottom overlay; the mint FAB turns into X.
        if (addSheetVisible) {
            Box(
                Modifier.fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.58f))
                    .clickable(onClick = { addSheetVisible = false })
            )
            Box(
                Modifier.align(Alignment.BottomCenter)
                    .padding(start = 10.dp, end = 10.dp, bottom = 64.dp)
            ) {
                ReferenceAddSheet(
                    onDismiss = { addSheetVisible = false },
                    onMedia = {
                        addSheetVisible = false
                        mediaLauncher.launch(arrayOf("image/*", "video/*"))
                    },
                    onText = {
                        vm.addLayer(LayerType.TEXT)
                        addSheetVisible = false
                        panelVisible = true
                    },
                    onShape = {
                        addSheetVisible = false
                        showShapePicker = true
                    },
                    onAudio = {
                        addSheetVisible = false
                        audioLauncher.launch(arrayOf("audio/*"))
                    },
                    onFreehand = {
                        vm.addDrawingLayer(freehand = true)
                        addSheetVisible = false
                        panelVisible = true
                    },
                    onVector = {
                        vm.addDrawingLayer(freehand = false)
                        addSheetVisible = false
                        panelVisible = true
                    },
                    onImportProject = {
                        addSheetVisible = false
                        onOpenImporter()
                    },
                    onTemplate = {
                        vm.addTemplateLayers()
                        addSheetVisible = false
                        panelVisible = true
                    },
                    onCamera = {
                        vm.addCameraObject()
                        addSheetVisible = false
                        panelVisible = true
                    },
                    onNull = {
                        vm.addNullObject()
                        addSheetVisible = false
                        panelVisible = true
                    },
                    onGroup = {
                        vm.addLayer(LayerType.GROUP)
                        addSheetVisible = false
                        panelVisible = true
                    }
                )
            }
        }

        Box(
            Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 16.dp)
                .size(50.dp).clip(CircleShape).background(EditorPalette.Green)
                .clickable(role = Role.Button) {
                    addSheetVisible = !addSheetVisible
                    if (!addSheetVisible && (state.tab != EditorTab.ADD ||
                            state.project?.layers?.isNotEmpty() == true)) {
                        panelVisible = true
                    }
                }
                .semantics {
                    contentDescription = if (addSheetVisible) "Close add layer" else "Add layer"
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Add,
                null,
                Modifier.size(27.dp).rotate(fabRotation),
                tint = EditorPalette.Black
            )
        }

        state.message?.let { msg ->
            Snackbar(
                modifier = Modifier.align(Alignment.BottomCenter)
                    .padding(bottom = if (inspectorVisible) 226.dp else 72.dp, start = 10.dp, end = 10.dp)
            ) { Text(msg, fontSize = 11.sp) }
        }
    }

    if (showShapePicker) {
        ShapePickerDialog(
            onDismiss = { showShapePicker = false },
            onPick = { choice ->
                vm.addLayer(LayerType.SHAPE, choice.kind)
                if (choice.sides != null || choice.starPoints != null) {
                    vm.updateShape { shape ->
                        shape.copy(
                            sides = choice.sides ?: shape.sides,
                            starPoints = choice.starPoints ?: shape.starPoints
                        )
                    }
                }
                showShapePicker = false
            }
        )
    }

    if (fullscreen) {
        Dialog(
            onDismissRequest = { fullscreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                PreviewSurface(state, Modifier.fillMaxSize())
                IconButton(
                    onClick = { fullscreen = false },
                    modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
                        .semantics { contentDescription = "Close fullscreen" }
                ) {
                    Icon(Icons.Default.Close, null, tint = Color.White)
                }
            }
        }
    }

    if (showExport) ExportDialog(state, vm) { showExport = false }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun EditorTopBar(
    state: EditorUiState,
    vm: EditorViewModel,
    onBack: () -> Unit,
    onExport: () -> Unit,
    onShowInspector: () -> Unit,
    onXmlExport: () -> Unit,
    onFullscreen: () -> Unit
) {
    if (state.tab == EditorTab.TEXT) {
        ReferenceTextToolbar(state, vm)
        return
    }
    val title = when (state.tab) {
        EditorTab.BORDER -> "Border & Shadow"
        EditorTab.BLEND -> "Blending & Opacity"
        EditorTab.TRANSFORM -> "Move & Transform"
        EditorTab.SHAPE -> "Edit Shape"
        EditorTab.COLOR -> "Color & Fill"
        EditorTab.KEYFRAMES -> "Keyframes"
        EditorTab.EFFECTS -> "Effects"
        EditorTab.VECTOR -> "Vector Path"
        EditorTab.MASKS -> "Masks"
        EditorTab.AUDIO -> "Audio"
        EditorTab.TRANSITIONS -> "Transitions"
        EditorTab.PROJECT -> "Project Settings"
        else -> state.project?.name ?: "Loading…"
    }
    var toolsExpanded by remember { mutableStateOf(false) }
    var guidesExpanded by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().height(42.dp).background(EditorPalette.SurfaceRaised),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.size(44.dp).semantics { contentDescription = "Back" }
        ) {
            ReferenceEditorIcon(ReferenceEditorIcon.BACK, Modifier.size(22.dp))
        }
        AnimatedContent(
            targetState = title,
            modifier = Modifier.weight(1f),
            transitionSpec = {
                (fadeIn(tween(AppMotion.Fast)) + slideInVertically(tween(AppMotion.Fast)) { it / 4 }) togetherWith
                    (fadeOut(tween(AppMotion.Fast)) + slideOutVertically(tween(AppMotion.Fast)) { -it / 4 })
            },
            label = "editor-toolbar-title"
        ) { animatedTitle ->
            Text(animatedTitle, fontSize = 14.sp, color = EditorPalette.TextPrimary, maxLines = 1)
        }
        Box {
            IconButton(
                onClick = { toolsExpanded = true },
                modifier = Modifier.size(40.dp).semantics { contentDescription = "Editor tools" }
            ) {
                Icon(Icons.Outlined.Settings, null, Modifier.size(21.dp),
                    tint = EditorPalette.TextSecondary)
            }
            DropdownMenu(expanded = toolsExpanded, onDismissRequest = { toolsExpanded = false }) {
                DropdownMenuItem(text = { Text("Project settings", fontSize = 11.sp) },
                    onClick = { vm.setTab(EditorTab.PROJECT); onShowInspector(); toolsExpanded = false })
                DropdownMenuItem(text = { Text("Preview guides", fontSize = 11.sp) },
                    onClick = { guidesExpanded = true; toolsExpanded = false })
                DropdownMenuItem(text = { Text("Undo", fontSize = 11.sp) }, enabled = state.undoAvailable,
                    onClick = { vm.undo(); toolsExpanded = false })
                DropdownMenuItem(text = { Text("Redo", fontSize = 11.sp) }, enabled = state.redoAvailable,
                    onClick = { vm.redo(); toolsExpanded = false })
                DropdownMenuItem(text = { Text("Zoom to fit", fontSize = 11.sp) },
                    onClick = { vm.zoomToFit(); toolsExpanded = false })
                DropdownMenuItem(text = { Text("Zoom to selection", fontSize = 11.sp) },
                    onClick = { vm.zoomToSelection(); toolsExpanded = false })
                DropdownMenuItem(text = { Text("Save project", fontSize = 11.sp) },
                    onClick = { vm.saveNow(); toolsExpanded = false })
                DropdownMenuItem(text = { Text("Export project XML", fontSize = 11.sp) },
                    onClick = { onXmlExport(); toolsExpanded = false })
                DropdownMenuItem(text = { Text("Fullscreen preview", fontSize = 11.sp) },
                    onClick = { onFullscreen(); toolsExpanded = false })
                DropdownMenuItem(
                    text = { Text(if (state.gpuPreview) "GPU preview: ON" else "GPU preview: OFF", fontSize = 11.sp) },
                    onClick = { vm.setGpuPreview(!state.gpuPreview); toolsExpanded = false }
                )
                DropdownMenuItem(
                    text = { Text(if (state.snapping) "Frame snapping: ON" else "Frame snapping: OFF", fontSize = 11.sp) },
                    onClick = { vm.setSnapping(!state.snapping); toolsExpanded = false }
                )
            }
            DropdownMenu(expanded = guidesExpanded, onDismissRequest = { guidesExpanded = false }) {
                GuideItem("Transparency checkerboard", state.checkerboard) { vm.setGuide("checkerboard", it) }
                GuideItem("Safe area", state.showSafeArea) { vm.setGuide("safe", it) }
                GuideItem("Grid", state.showGrid) { vm.setGuide("grid", it) }
                GuideItem("Center guides", state.showCenterGuides) { vm.setGuide("center", it) }
                GuideItem("Aspect guides", state.showAspectGuides) { vm.setGuide("aspect", it) }
            }
        }
        IconButton(
            onClick = onExport,
            modifier = Modifier.size(42.dp).semantics { contentDescription = "Export video" }
        ) {
            ReferenceEditorIcon(ReferenceEditorIcon.EXPORT, Modifier.size(21.dp))
        }
    }
}

/** The reference's text-editing toolbar: every dropdown writes a real TextSpec. */
@Composable
private fun ReferenceTextToolbar(state: EditorUiState, vm: EditorViewModel) {
    val spec = state.project?.layers
        ?.firstOrNull { it.id == state.selectedLayerId }?.text ?: TextSpec()
    val fonts = listOf(
        "Roboto Regular" to Triple("sans", 400, false),
        "Roboto Bold" to Triple("sans", 700, false),
        "Roboto Italic" to Triple("sans", 400, true),
        "Serif" to Triple("serif", 400, false),
        "Monospace" to Triple("monospace", 400, false)
    )
    val fontLabel = fonts.firstOrNull {
        it.second.first == spec.fontFamily && it.second.second == spec.fontWeight &&
            it.second.third == spec.italic
    }?.first ?: "Roboto Regular"
    val colors = listOf("#FFFFFFFF", "#FFFF3B47", "#FF01FFA7", "#FF8B7BFF",
        "#FFFFCE5C", "#FF22D3EE", "#FF111116")
    var fontExpanded by remember { mutableStateOf(false) }
    var sizeExpanded by remember { mutableStateOf(false) }
    var colorExpanded by remember { mutableStateOf(false) }
    var optionsExpanded by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().height(42.dp).background(EditorPalette.SurfaceRaised),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            IconButton(onClick = { optionsExpanded = true },
                modifier = Modifier.size(42.dp).semantics { contentDescription = "Text options" }) {
                Icon(Icons.Default.Menu, null, Modifier.size(20.dp), tint = EditorPalette.TextPrimary)
            }
            DropdownMenu(expanded = optionsExpanded, onDismissRequest = { optionsExpanded = false }) {
                DropdownMenuItem(text = { Text("Bold", fontSize = 11.sp) },
                    onClick = { vm.updateText { it.copy(fontWeight = 700) }; optionsExpanded = false })
                DropdownMenuItem(text = { Text("Regular", fontSize = 11.sp) },
                    onClick = { vm.updateText { it.copy(fontWeight = 400) }; optionsExpanded = false })
                DropdownMenuItem(text = { Text("Italic", fontSize = 11.sp) },
                    onClick = { vm.updateText { it.copy(italic = !it.italic) }; optionsExpanded = false })
                DropdownMenuItem(text = { Text("Uppercase", fontSize = 11.sp) },
                    onClick = { vm.updateText { it.copy(uppercase = !it.uppercase) }; optionsExpanded = false })
            }
        }
        Box(Modifier.weight(1f).padding(end = 5.dp)) {
            Row(
                Modifier.fillMaxWidth().height(32.dp)
                    .background(Color(0xFF303034), RoundedCornerShape(3.dp))
                    .clickable(role = Role.Button) { fontExpanded = true }
                    .padding(horizontal = 10.dp)
                    .semantics { contentDescription = "Text font" },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(fontLabel, Modifier.weight(1f), fontSize = 12.sp,
                    color = EditorPalette.TextPrimary, maxLines = 1)
                Icon(Icons.Default.ExpandMore, null, Modifier.size(17.dp),
                    tint = EditorPalette.TextSecondary)
            }
            DropdownMenu(expanded = fontExpanded, onDismissRequest = { fontExpanded = false }) {
                fonts.forEach { (label, values) ->
                    DropdownMenuItem(text = { Text(label, fontSize = 11.sp) }, onClick = {
                        vm.updateText { it.copy(fontFamily = values.first, fontWeight = values.second, italic = values.third) }
                        fontExpanded = false
                    })
                }
            }
        }
        Box(Modifier.width(82.dp).padding(end = 5.dp)) {
            Row(
                Modifier.fillMaxWidth().height(32.dp)
                    .background(Color(0xFF303034), RoundedCornerShape(3.dp))
                    .clickable(role = Role.Button) { sizeExpanded = true }
                    .padding(horizontal = 8.dp)
                    .semantics { contentDescription = "Text size" },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${spec.fontSize.roundToInt()}pt", Modifier.weight(1f), fontSize = 12.sp,
                    color = EditorPalette.TextPrimary, maxLines = 1)
                Icon(Icons.Default.ExpandMore, null, Modifier.size(16.dp),
                    tint = EditorPalette.TextSecondary)
            }
            DropdownMenu(expanded = sizeExpanded, onDismissRequest = { sizeExpanded = false }) {
                listOf(18, 36, 72, 144, 220).forEach { size ->
                    DropdownMenuItem(text = { Text("${size}pt", fontSize = 11.sp) }, onClick = {
                        vm.updateText { it.copy(fontSize = size.toFloat()) }
                        sizeExpanded = false
                    })
                }
            }
        }
        Box(Modifier.size(38.dp).padding(end = 5.dp)) {
            Box(
                Modifier.fillMaxSize().background(Color(android.graphics.Color.parseColor(spec.color)),
                    RoundedCornerShape(3.dp))
                    .border(1.dp, EditorPalette.TextMuted, RoundedCornerShape(3.dp))
                    .clickable(role = Role.Button) { colorExpanded = true }
                    .semantics { contentDescription = "Text color" }
            )
            DropdownMenu(expanded = colorExpanded, onDismissRequest = { colorExpanded = false }) {
                colors.forEach { color ->
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(16.dp)
                                    .background(Color(android.graphics.Color.parseColor(color)),
                                        RoundedCornerShape(3.dp)))
                                Spacer(Modifier.width(8.dp))
                                Text(color, fontSize = 10.sp)
                            }
                        },
                        onClick = {
                            vm.updateText { it.copy(color = color) }
                            colorExpanded = false
                        }
                    )
                }
            }
        }
        IconButton(onClick = { vm.setTab(EditorTab.PROPERTIES) },
            modifier = Modifier.size(38.dp).semantics { contentDescription = "Apply text" }) {
            Icon(Icons.Default.Check, null, Modifier.size(20.dp), tint = EditorPalette.TextPrimary)
        }
    }
}

/** Compact transport row with the reference's thin seven-icon chrome. */
@Composable
private fun PlayerBar(state: EditorUiState, vm: EditorViewModel, onFullscreen: () -> Unit) {
    val p = state.project ?: return
    Row(
        Modifier.fillMaxWidth().height(48.dp).background(EditorPalette.Black),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        PlayerIconButton("Undo", state.undoAvailable, ReferenceEditorIcon.UNDO) { vm.undo() }
        PlayerIconButton("Redo", state.redoAvailable, ReferenceEditorIcon.REDO) { vm.redo() }
        PlayerIconButton("Go to start", true, ReferenceEditorIcon.START) { vm.seek(0L) }
        IconButton(
            onClick = { vm.togglePlay() },
            modifier = Modifier.size(40.dp).semantics { contentDescription = "Play / pause" }
        ) {
            AnimatedContent(
                targetState = state.playing,
                transitionSpec = {
                    scaleIn(animationSpec = spring()) + fadeIn() togetherWith
                        scaleOut(animationSpec = spring()) + fadeOut()
                },
                label = "play-pause"
            ) { playing ->
                ReferenceEditorIcon(
                    if (playing) ReferenceEditorIcon.PAUSE else ReferenceEditorIcon.PLAY,
                    Modifier.size(23.dp),
                    tint = if (playing) EditorPalette.Green else EditorPalette.TextPrimary
                )
            }
        }
        PlayerIconButton("Go to end", true, ReferenceEditorIcon.END) { vm.seek(p.durationMs) }
        PlayerIconButton("Duplicate layer", state.selectedLayerId != null,
            ReferenceEditorIcon.DUPLICATE) { state.selectedLayerId?.let { vm.duplicateLayer(it) } }
        PlayerIconButton("Fullscreen preview", true, ReferenceEditorIcon.FULLSCREEN, onFullscreen)
    }
}

@Composable
private fun PlayerIconButton(
    label: String,
    enabled: Boolean,
    icon: ReferenceEditorIcon,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(40.dp).semantics { contentDescription = label }
    ) {
        ReferenceEditorIcon(
            icon,
            Modifier.size(22.dp),
            tint = if (enabled) EditorPalette.TextPrimary else EditorPalette.TextMuted
        )
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
                    ExportResolution.entries.map { it.name to it.label },
                    onChange = { resolution = ExportResolution.valueOf(it) })
                Text("Output ${w}×${h}", fontSize = 10.sp)
                LabeledSelect("Frame rate", fps.toString(),
                    listOf("24", "30", "60").map { it to "$it fps" },
                    onChange = { fps = it.toInt() })
                SliderRow("Bitrate (Mbps)", bitrateMbps, 1f..30f,
                    onChange = { bitrateMbps = it })
                SliderRow("Quality", quality.toFloat(), 10f..100f,
                    onChange = { quality = it.toInt() })
                LabeledSelect("Codec", codec.name,
                    VideoCodec.entries.map { it.name to it.name },
                    onChange = { codec = VideoCodec.valueOf(it) })
                LabeledSelect("Save location", saveLocation,
                    listOf("gallery" to "Gallery / MediaStore", "app" to "App storage"),
                    onChange = { saveLocation = it })
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
private fun GuideItem(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    DropdownMenuItem(
        text = { Text(label, style = MaterialTheme.typography.bodySmall) },
        onClick = { onChange(!checked) },
        trailingIcon = {
            Icon(
                if (checked) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                null, Modifier.size(16.dp)
            )
        }
    )
}
