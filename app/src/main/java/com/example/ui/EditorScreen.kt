package com.example.ui

import android.graphics.Paint
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.AnimationEngine
import com.example.model.*
import com.example.render.CompositionCanvasRenderer
import com.example.render.ShapePathBuilder
import com.example.ui.theme.OmsColors
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EditorScreen(
    viewModel: MotionStudioViewModel,
    onBackHome: () -> Unit
) {
    val doc by viewModel.activeDoc.collectAsState()
    val currentTime by viewModel.currentTime.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val isLooping by viewModel.isLooping.collectAsState()
    val selectedLayerId by viewModel.selectedLayerId.collectAsState()
    val drawerMode by viewModel.drawerMode.collectAsState()
    val canUndo by viewModel.canUndo.collectAsState()
    val canRedo by viewModel.canRedo.collectAsState()
    val cameraViewActive by viewModel.cameraViewActive.collectAsState()
    val showGuides by viewModel.showGuides.collectAsState()
    val viewportZoom by viewModel.viewportZoom.collectAsState()
    val vectorPenSession by viewModel.vectorPenSession.collectAsState()
    val eyedropperTarget by viewModel.eyedropperTarget.collectAsState()
    val motionTrackUi by viewModel.motionTrackUi.collectAsState()
    val multiSelectedIds by viewModel.multiSelectedIds.collectAsState()
    val lang by viewModel.language.collectAsState()

    val currentDoc = doc ?: return

    BackHandler {
        when {
            eyedropperTarget != null -> viewModel.cancelEyedropper()
            motionTrackUi.selectingTargetPoint -> viewModel.cancelMotionTrack()
            vectorPenSession != null -> viewModel.cancelVectorPen()
            drawerMode != EditorDrawerMode.NONE -> viewModel.openDrawer(EditorDrawerMode.NONE)
            else -> {
                viewModel.closeEditor()
                onBackHome()
            }
        }
    }

    var showRenameDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = OmsColors.EditorBg,
        topBar = {
            EditorTopBar(
                project = currentDoc.project,
                canUndo = canUndo,
                canRedo = canRedo,
                cameraViewActive = cameraViewActive,
                showGuides = showGuides,
                onBack = {
                    viewModel.closeEditor()
                    onBackHome()
                },
                onRename = { showRenameDialog = true },
                onUndo = { viewModel.undo() },
                onRedo = { viewModel.redo() },
                onToggle3d = { viewModel.toggle3dScene() },
                onToggleCameraView = { viewModel.toggleCameraView() },
                onToggleGuides = { viewModel.toggleGuides() },
                onSettings = { viewModel.openDrawer(EditorDrawerMode.PROJECT_SETTINGS) },
                onExport = { viewModel.openDrawer(EditorDrawerMode.EXPORT_SHEET) }
            )
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
        ) {
            val isWide = maxWidth >= 820.dp

            if (isWide) {
                Row(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.weight(1.15f).fillMaxHeight()) {
                        ViewportPanel(
                            doc = currentDoc,
                            currentTime = currentTime,
                            selectedLayerId = selectedLayerId,
                            cameraViewActive = cameraViewActive,
                            showGuides = showGuides,
                            viewportZoom = viewportZoom,
                            vectorPenSession = vectorPenSession,
                            eyedropperTarget = eyedropperTarget,
                            motionTrackUi = motionTrackUi,
                            viewModel = viewModel,
                            modifier = Modifier.weight(1f).fillMaxWidth()
                        )
                    }
                    Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(OmsColors.Border))
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        TimelineAndToolsPanel(
                            viewModel = viewModel,
                            doc = currentDoc,
                            currentTime = currentTime,
                            isPlaying = isPlaying,
                            isLooping = isLooping,
                            selectedLayerId = selectedLayerId,
                            multiSelectedIds = multiSelectedIds,
                            drawerMode = drawerMode,
                            lang = lang
                        )
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    ViewportPanel(
                        doc = currentDoc,
                        currentTime = currentTime,
                        selectedLayerId = selectedLayerId,
                        cameraViewActive = cameraViewActive,
                        showGuides = showGuides,
                        viewportZoom = viewportZoom,
                        vectorPenSession = vectorPenSession,
                        eyedropperTarget = eyedropperTarget,
                        motionTrackUi = motionTrackUi,
                        viewModel = viewModel,
                        modifier = Modifier.weight(0.50f).fillMaxWidth()
                    )
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(OmsColors.Border))
                    Box(modifier = Modifier.weight(0.50f).fillMaxWidth()) {
                        TimelineAndToolsPanel(
                            viewModel = viewModel,
                            doc = currentDoc,
                            currentTime = currentTime,
                            isPlaying = isPlaying,
                            isLooping = isLooping,
                            selectedLayerId = selectedLayerId,
                            multiSelectedIds = multiSelectedIds,
                            drawerMode = drawerMode,
                            lang = lang
                        )
                    }
                }
            }

            // Active Drawer Sheet Overlay
            if (drawerMode != EditorDrawerMode.NONE && drawerMode != EditorDrawerMode.EXPORT_SHEET) {
                EditorDrawerHost(
                    drawerMode = drawerMode,
                    viewModel = viewModel,
                    doc = currentDoc,
                    selectedLayer = currentDoc.layers.find { it.id == selectedLayerId },
                    currentTime = currentTime,
                    lang = lang,
                    isWide = isWide,
                    modifier = if (isWide) {
                        Modifier
                            .align(Alignment.CenterEnd)
                            .width(420.dp)
                            .fillMaxHeight()
                    } else {
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .fillMaxHeight(0.54f)
                    }
                )
            }

            if (drawerMode == EditorDrawerMode.EXPORT_SHEET) {
                ExportSheetOverlay(
                    viewModel = viewModel,
                    doc = currentDoc,
                    lang = lang,
                    onClose = { viewModel.openDrawer(EditorDrawerMode.NONE) }
                )
            }
        }
    }

    if (showRenameDialog) {
        var nextName by remember(currentDoc.project.id) { mutableStateOf(currentDoc.project.name) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            containerColor = OmsColors.Panel,
            title = { Text(OmsStrings.projectNameLabel(lang)) },
            text = {
                OutlinedTextField(
                    value = nextName,
                    onValueChange = { nextName = it },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateProjectSettings(name = nextName.trim().ifBlank { currentDoc.project.name })
                    showRenameDialog = false
                }) {
                    Text(OmsStrings.tr(lang, "Simpan", "Save"), color = OmsColors.Accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text(OmsStrings.tr(lang, "Batal", "Cancel"), color = OmsColors.Muted)
                }
            }
        )
    }
}

@Composable
private fun EditorTopBar(
    project: ProjectData,
    canUndo: Boolean,
    canRedo: Boolean,
    cameraViewActive: Boolean,
    showGuides: Boolean,
    onBack: () -> Unit,
    onRename: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onToggle3d: () -> Unit,
    onToggleCameraView: () -> Unit,
    onToggleGuides: () -> Unit,
    onSettings: () -> Unit,
    onExport: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(50.dp)
            .background(OmsColors.Panel)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack, modifier = Modifier.testTag("editor_back_home_btn")) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali", tint = OmsColors.Text)
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onRename)
                .padding(horizontal = 4.dp)
        ) {
            Text(
                text = project.name,
                color = OmsColors.Text,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${project.width}×${project.height} • ${project.fps} fps",
                color = OmsColors.Muted,
                fontSize = 10.sp,
                maxLines = 1
            )
        }
        IconButton(onClick = onUndo, enabled = canUndo, modifier = Modifier.testTag("undo_btn")) {
            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo", tint = if (canUndo) OmsColors.Text else OmsColors.Muted.copy(alpha = 0.4f))
        }
        IconButton(onClick = onRedo, enabled = canRedo, modifier = Modifier.testTag("redo_btn")) {
            Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo", tint = if (canRedo) OmsColors.Text else OmsColors.Muted.copy(alpha = 0.4f))
        }
        IconButton(onClick = onToggle3d, modifier = Modifier.testTag("toggle_3d_btn")) {
            Text(
                "3D",
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (project.space3dActive) OmsColors.Accent else OmsColors.Muted
            )
        }
        IconButton(onClick = onToggleCameraView, modifier = Modifier.testTag("toggle_cam_view_btn")) {
            Icon(
                Icons.Filled.Videocam,
                contentDescription = "Camera View",
                tint = if (cameraViewActive) OmsColors.Purple else OmsColors.Muted,
                modifier = Modifier.size(20.dp)
            )
        }
        IconButton(onClick = onToggleGuides) {
            Icon(
                Icons.Filled.GridOn,
                contentDescription = "Guides",
                tint = if (showGuides) OmsColors.Accent else OmsColors.Muted,
                modifier = Modifier.size(19.dp)
            )
        }
        IconButton(onClick = onSettings, modifier = Modifier.testTag("project_settings_btn")) {
            Icon(Icons.Outlined.Settings, contentDescription = "Pengaturan", tint = OmsColors.Text, modifier = Modifier.size(20.dp))
        }
        IconButton(onClick = onExport, modifier = Modifier.testTag("open_export_btn")) {
            Icon(Icons.Filled.IosShare, contentDescription = "Ekspor", tint = OmsColors.Accent, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun ViewportPanel(
    doc: ProjectDocument,
    currentTime: Float,
    selectedLayerId: String?,
    cameraViewActive: Boolean,
    showGuides: Boolean,
    viewportZoom: Float,
    vectorPenSession: VectorPenSession?,
    eyedropperTarget: String?,
    motionTrackUi: MotionTrackUiState,
    viewModel: MotionStudioViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val project = doc.project

    Column(modifier = modifier.background(OmsColors.EditorBg)) {
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clipToBounds()
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            val availW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
            val availH = constraints.maxHeight.toFloat().coerceAtLeast(1f)
            val baseScale = min(availW / project.width.toFloat(), availH / project.height.toFloat())
            val drawW = project.width * baseScale * viewportZoom
            val drawH = project.height * baseScale * viewportZoom

            val density = LocalDensity.current
            val boxW = with(density) { drawW.toDp() }
            val boxH = with(density) { drawH.toDp() }

            Box(
                modifier = Modifier
                    .size(boxW, boxH)
                    .border(1.dp, OmsColors.Border)
                    .pointerInput(doc.layers, currentTime, selectedLayerId, vectorPenSession, eyedropperTarget, motionTrackUi.selectingTargetPoint, drawW, drawH) {
                        detectTapGestures { offset ->
                            val canvasX = (offset.x / drawW) * project.width
                            val canvasY = (offset.y / drawH) * project.height
                            when {
                                eyedropperTarget != null -> viewModel.pickCanvasColorAt(canvasX, canvasY)
                                motionTrackUi.selectingTargetPoint -> viewModel.runMotionTrackAt(canvasX, canvasY)
                                vectorPenSession != null -> viewModel.handleVectorPenTap(canvasX, canvasY)
                                else -> {
                                    val hit = doc.layers.lastOrNull { l ->
                                        if (l.hidden || l.type == LayerType.AUDIO || l.type == LayerType.CAMERA ||
                                            currentTime < l.start || currentTime > l.start + l.duration
                                        ) return@lastOrNull false
                                        val wTr = AnimationEngine.resolveWorldTransform(l, doc.layers, currentTime)
                                        abs(canvasX - wTr.x) <= max(60f, l.w * abs(wTr.scaleX) * 0.55f) &&
                                            abs(canvasY - wTr.y) <= max(60f, l.h * abs(wTr.scaleY) * 0.55f)
                                    }
                                    viewModel.selectLayer(hit?.id)
                                }
                            }
                        }
                    }
                    .pointerInput(selectedLayerId, vectorPenSession, eyedropperTarget, motionTrackUi.selectingTargetPoint, drawW, drawH) {
                        if (eyedropperTarget == null && !motionTrackUi.selectingTargetPoint) {
                            detectDragGestures(
                                onDragStart = { startOffset ->
                                    if (vectorPenSession != null) {
                                        val cx = (startOffset.x / drawW) * project.width
                                        val cy = (startOffset.y / drawH) * project.height
                                        viewModel.beginVectorPenDrag(cx, cy)
                                    } else {
                                        viewModel.beginCanvasDragUndo()
                                    }
                                },
                                onDragEnd = {
                                    if (vectorPenSession != null) viewModel.endVectorPenDrag()
                                },
                                onDragCancel = {
                                    if (vectorPenSession != null) viewModel.endVectorPenDrag()
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val dx = (dragAmount.x / drawW) * project.width
                                    val dy = (dragAmount.y / drawH) * project.height
                                    if (vectorPenSession != null) {
                                        viewModel.dragVectorPenHandle(dx, dy)
                                    } else {
                                        viewModel.dragLayerOnCanvas(dx, dy)
                                    }
                                }
                            )
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val scaleX = size.width / project.width.toFloat()
                    val scaleY = size.height / project.height.toFloat()
                    drawIntoCanvas { canvas ->
                        val nc = canvas.nativeCanvas
                        nc.save()
                        nc.scale(scaleX, scaleY)
                        CompositionCanvasRenderer.render(
                            context = context,
                            canvas = nc,
                            doc = doc,
                            timeSec = currentTime,
                            selectedLayerId = selectedLayerId,
                            showGuides = showGuides,
                            cameraViewActive = cameraViewActive
                        )

                        // Interactive Vector Pen Overlay with Bezier Handles
                        if (vectorPenSession != null && vectorPenSession.nodes.isNotEmpty()) {
                            nc.save()
                            nc.translate(project.width * 0.5f, project.height * 0.5f)
                            val penPath = ShapePathBuilder.buildVectorPath(
                                VectorPathData(closed = vectorPenSession.closed, nodes = vectorPenSession.nodes)
                            )
                            val strokeP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                color = android.graphics.Color.parseColor("#00D7A7")
                                style = Paint.Style.STROKE
                                strokeWidth = 4f
                            }
                            nc.drawPath(penPath, strokeP)

                            val handleLineP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                color = android.graphics.Color.parseColor("#8374FF")
                                style = Paint.Style.STROKE
                                strokeWidth = 2.5f
                            }
                            val handleFillP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                color = android.graphics.Color.parseColor("#43CBE8")
                                style = Paint.Style.FILL
                            }
                            val nodeFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                color = android.graphics.Color.WHITE
                                style = Paint.Style.FILL
                            }
                            val selFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                color = android.graphics.Color.parseColor("#FF6DA9")
                                style = Paint.Style.FILL
                            }

                            vectorPenSession.nodes.forEachIndexed { idx, node ->
                                val isSel = idx == vectorPenSession.selectedNodeIndex
                                if (isSel && node.smooth) {
                                    nc.drawLine(node.x, node.y, node.x + node.inX, node.y + node.inY, handleLineP)
                                    nc.drawLine(node.x, node.y, node.x + node.outX, node.y + node.outY, handleLineP)
                                    nc.drawCircle(node.x + node.inX, node.y + node.inY, 10f, handleFillP)
                                    nc.drawCircle(node.x + node.outX, node.y + node.outY, 10f, handleFillP)
                                }
                                nc.drawCircle(node.x, node.y, if (isSel) 15f else 10f, if (isSel) selFill else nodeFill)
                                nc.drawCircle(node.x, node.y, if (isSel) 15f else 10f, strokeP)
                            }
                            nc.restore()
                        }
                        nc.restore()
                    }
                }
            }

            // Status / Mode Badge
            val selectedLayer = doc.layers.find { it.id == selectedLayerId }
            if (selectedLayer != null && vectorPenSession == null && eyedropperTarget == null && !motionTrackUi.selectingTargetPoint) {
                val tx = AnimationEngine.evaluateProperty(selectedLayer, "x", currentTime).roundToInt()
                val ty = AnimationEngine.evaluateProperty(selectedLayer, "y", currentTime).roundToInt()
                val tz = AnimationEngine.evaluateProperty(selectedLayer, "z", currentTime).roundToInt()
                Surface(
                    color = OmsColors.Panel.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, OmsColors.Border),
                    modifier = Modifier.align(Alignment.TopStart).padding(4.dp)
                ) {
                    Text(
                        text = "${selectedLayer.name}  X:$tx Y:$ty" + if (project.space3dActive || selectedLayer.space3d) " Z:$tz" else "",
                        color = OmsColors.Mint,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Eyedropper HUD
            if (eyedropperTarget != null) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .clip(RoundedCornerShape(12.dp))
                        .background(OmsColors.Panel.copy(alpha = 0.94f))
                        .border(1.dp, OmsColors.Cyan, RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.Colorize, contentDescription = null, tint = OmsColors.Cyan, modifier = Modifier.size(16.dp))
                    Text("Ketuk kanvas untuk ambil warna", fontSize = 11.sp, color = OmsColors.Text)
                    TextButton(onClick = { viewModel.cancelEyedropper() }) { Text("Batal", fontSize = 11.sp, color = OmsColors.Danger) }
                }
            }

            // Motion Tracker HUD
            if (motionTrackUi.selectingTargetPoint || motionTrackUi.isTracking) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .clip(RoundedCornerShape(12.dp))
                        .background(OmsColors.Panel.copy(alpha = 0.95f))
                        .border(1.dp, OmsColors.Orange, RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (motionTrackUi.isTracking) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = OmsColors.Orange, strokeWidth = 2.dp)
                        Text(motionTrackUi.statusMessage ?: "Melacak...", fontSize = 11.sp, color = OmsColors.Orange)
                    } else {
                        Icon(Icons.Filled.GpsFixed, contentDescription = null, tint = OmsColors.Orange, modifier = Modifier.size(16.dp))
                        Text("Ketuk titik objek pada Video untuk track", fontSize = 11.sp, color = OmsColors.Text)
                    }
                    TextButton(onClick = { viewModel.cancelMotionTrack() }) { Text("Batal", fontSize = 11.sp, color = OmsColors.Danger) }
                }
            }

            // Full Interactive Vector Pen HUD
            if (vectorPenSession != null) {
                val selNode = vectorPenSession.nodes.getOrNull(vectorPenSession.selectedNodeIndex)
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(OmsColors.Panel.copy(alpha = 0.95f))
                        .border(1.dp, OmsColors.Accent.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Pen (${vectorPenSession.nodes.size})", fontSize = 11.sp, color = OmsColors.Mint, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp))
                    if (selNode != null) {
                        TextButton(onClick = { viewModel.toggleVectorNodeSmooth() }) {
                            Text(if (selNode.smooth) "Halus" else "Sudut", fontSize = 10.sp, color = OmsColors.Purple)
                        }
                        if (selNode.smooth) {
                            TextButton(onClick = { viewModel.toggleVectorNodeSymmetric() }) {
                                Text(if (selNode.symmetric) "Simetris" else "Bebas", fontSize = 10.sp, color = OmsColors.Cyan)
                            }
                        }
                        TextButton(onClick = { viewModel.insertVectorMidpoint() }) {
                            Text("+Titik", fontSize = 10.sp, color = OmsColors.Orange)
                        }
                        TextButton(onClick = { viewModel.deleteSelectedVectorNode() }) {
                            Text("Hapus", fontSize = 10.sp, color = OmsColors.Danger)
                        }
                    }
                    TextButton(onClick = { viewModel.undoVectorPenNode() }) { Text("Undo", fontSize = 10.sp, color = OmsColors.Text) }
                    TextButton(onClick = { viewModel.toggleVectorPenClosed() }) {
                        Text(if (vectorPenSession.closed) "Tutup" else "Buka", fontSize = 10.sp, color = OmsColors.Cyan)
                    }
                    TextButton(onClick = { viewModel.cancelVectorPen() }) { Text("Batal", fontSize = 10.sp, color = OmsColors.Danger) }
                    Button(
                        onClick = { viewModel.finishVectorPen() },
                        colors = ButtonDefaults.buttonColors(containerColor = OmsColors.Accent, contentColor = Color(0xFF061B15)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                    ) { Text("Selesai", fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }

        // Viewport Zoom Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .background(OmsColors.Bg2)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Zoom ${(viewportZoom * 100).roundToInt()}%",
                fontSize = 10.sp,
                color = OmsColors.Muted,
                modifier = Modifier.width(68.dp)
            )
            Slider(
                value = viewportZoom,
                onValueChange = { viewModel.setViewportZoom(it) },
                valueRange = 0.35f..2.5f,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { viewModel.setViewportZoom(1f) }) {
                Text("Fit", fontSize = 11.sp, color = OmsColors.Accent)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TimelineAndToolsPanel(
    viewModel: MotionStudioViewModel,
    doc: ProjectDocument,
    currentTime: Float,
    isPlaying: Boolean,
    isLooping: Boolean,
    selectedLayerId: String?,
    multiSelectedIds: Set<String>,
    drawerMode: EditorDrawerMode,
    lang: AppLang
) {
    val context = LocalContext.current
    val pxPerSec = doc.project.timelinePxPerSec.coerceIn(28f, 180f)
    val horizontalScroll = rememberScrollState()
    val density = LocalDensity.current

    fun fmtTime(t: Float): String {
        val totalCs = (t.coerceAtLeast(0f) * 100f).roundToInt()
        val s = totalCs / 100
        val cs = totalCs % 100
        val min = s / 60
        val remS = s % 60
        return String.format(Locale.US, "%02d:%02d.%02d", min, remS, cs)
    }

    Box(modifier = Modifier.fillMaxSize().background(OmsColors.Bg2)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Transport Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .background(OmsColors.Panel)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.seekTo(0f) }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = "Awal", tint = OmsColors.Muted, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = { viewModel.jumpToPrevKeyframe() }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.FastRewind, contentDescription = "Keyframe Sebelumnya", tint = OmsColors.Muted, modifier = Modifier.size(18.dp))
                }
                IconButton(
                    onClick = { viewModel.togglePlay() },
                    modifier = Modifier.size(40.dp).testTag("play_pause_btn")
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.PauseCircle else Icons.Filled.PlayCircle,
                        contentDescription = "Putar / Jeda",
                        tint = OmsColors.Accent,
                        modifier = Modifier.size(28.dp)
                    )
                }
                IconButton(onClick = { viewModel.jumpToNextKeyframe() }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.FastForward, contentDescription = "Keyframe Berikutnya", tint = OmsColors.Muted, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = { viewModel.toggleLoop() }, modifier = Modifier.size(34.dp)) {
                    Icon(
                        Icons.Filled.Repeat,
                        contentDescription = "Loop",
                        tint = if (isLooping) OmsColors.Accent else OmsColors.Muted.copy(alpha = 0.45f),
                        modifier = Modifier.size(17.dp)
                    )
                }

                Spacer(Modifier.weight(1f))

                Surface(
                    color = Color(0xFF0C0F18),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, OmsColors.Border)
                ) {
                    Text(
                        text = "${fmtTime(currentTime)} / ${fmtTime(doc.project.duration)}",
                        fontSize = 10.sp,
                        color = OmsColors.Text,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                IconButton(
                    onClick = { viewModel.toggleMarkerAtPlayhead() },
                    modifier = Modifier.size(34.dp).testTag("toggle_marker_btn")
                ) {
                    Icon(Icons.Filled.BookmarkAdd, contentDescription = "Marker", tint = OmsColors.Mint, modifier = Modifier.size(17.dp))
                }
                IconButton(
                    onClick = { viewModel.splitSelectedLayerAtPlayhead() },
                    enabled = selectedLayerId != null,
                    modifier = Modifier.size(34.dp).testTag("split_layer_btn")
                ) {
                    Icon(
                        Icons.Filled.ContentCut,
                        contentDescription = "Pisah Layer",
                        tint = if (selectedLayerId != null) OmsColors.Text else OmsColors.Muted.copy(alpha = 0.4f),
                        modifier = Modifier.size(17.dp)
                    )
                }
            }

            // Ruler + Timeline Tracks
            val laneWidthDp: Dp = (doc.project.duration * pxPerSec + 140f).dp
            val headerWidth = 110.dp
            val playheadX = with(density) { (currentTime * pxPerSec).dp.toPx() }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Time Ruler Row
                    Row(modifier = Modifier.fillMaxWidth().height(24.dp).background(Color(0xFF121521))) {
                        Box(
                            modifier = Modifier
                                .width(headerWidth)
                                .fillMaxHeight()
                                .border(0.5.dp, OmsColors.Border),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    "-",
                                    color = OmsColors.Muted,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable { viewModel.setTimelineZoom(pxPerSec - 14f) }.padding(horizontal = 6.dp)
                                )
                                Text("${doc.layers.size}L", fontSize = 9.sp, color = OmsColors.Muted)
                                Text(
                                    "+",
                                    color = OmsColors.Muted,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable { viewModel.setTimelineZoom(pxPerSec + 14f) }.padding(horizontal = 6.dp)
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .horizontalScroll(horizontalScroll)
                                .pointerInput(doc.project.duration, pxPerSec) {
                                    detectTapGestures { pos ->
                                        val sec = (pos.x / with(density) { pxPerSec.dp.toPx() }).coerceIn(0f, doc.project.duration)
                                        viewModel.seekTo(sec)
                                    }
                                }
                                .pointerInput(doc.project.duration, pxPerSec) {
                                    detectDragGestures { change, _ ->
                                        change.consume()
                                        val sec = (change.position.x / with(density) { pxPerSec.dp.toPx() }).coerceIn(0f, doc.project.duration)
                                        viewModel.seekTo(sec)
                                    }
                                }
                        ) {
                            Canvas(modifier = Modifier.width(laneWidthDp).fillMaxHeight()) {
                                val stepPx = pxPerSec.dp.toPx()
                                val totalSec = doc.project.duration.roundToInt()
                                val tickPaint = Paint().apply {
                                    color = android.graphics.Color.parseColor("#8B95AC")
                                    textSize = 22f
                                }
                                for (s in 0..totalSec) {
                                    val x = s * stepPx
                                    drawLine(Color(0xFF39425D), Offset(x, 0f), Offset(x, size.height), strokeWidth = 2f)
                                    drawIntoCanvas { it.nativeCanvas.drawText("${s}s", x + 5f, size.height - 6f, tickPaint) }
                                    val halfX = x + stepPx * 0.5f
                                    if (s < totalSec) {
                                        drawLine(Color(0xFF282F44), Offset(halfX, size.height * 0.5f), Offset(halfX, size.height), strokeWidth = 1f)
                                    }
                                }
                                // Markers
                                doc.markers.forEach { m ->
                                    val mx = m.time * stepPx
                                    drawCircle(OmsColors.Accent, radius = 6f, center = Offset(mx, size.height - 6f))
                                }
                                // Playhead indicator on ruler
                                drawLine(Color.White, Offset(playheadX, 0f), Offset(playheadX, size.height), strokeWidth = 3f)
                            }
                        }
                    }

                    // Layer Tracks List
                    if (doc.layers.isEmpty()) {
                        Box(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = OmsStrings.tr(lang, "Tekan + untuk menambahkan Shape, Teks, Media, atau Kamera 3D", "Tap + to add Shapes, Text, Media, or 3D Camera"),
                                fontSize = 12.sp,
                                color = OmsColors.Muted
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentPadding = PaddingValues(bottom = 52.dp)
                        ) {
                            items(doc.layers.reversed(), key = { it.id }) { layer ->
                                val isSelected = layer.id == selectedLayerId
                                val clipColor = when (layer.type) {
                                    LayerType.SHAPE -> OmsColors.ClipShape
                                    LayerType.TEXT -> OmsColors.ClipText
                                    LayerType.IMAGE -> OmsColors.ClipImage
                                    LayerType.VIDEO -> OmsColors.ClipVideo
                                    LayerType.AUDIO -> OmsColors.ClipAudio
                                    LayerType.CAMERA -> OmsColors.ClipCamera
                                    LayerType.MESH -> OmsColors.ClipMesh
                                    LayerType.GROUP -> OmsColors.Mint
                                    LayerType.NULL -> OmsColors.Orange
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(34.dp)
                                        .background(if (isSelected) Color(0xFF22283C) else Color.Transparent)
                                ) {
                                    val isMulti = layer.id in multiSelectedIds
                                    val needsMediaRelink = (layer.type == LayerType.IMAGE || layer.type == LayerType.VIDEO || layer.type == LayerType.AUDIO) &&
                                        !CompositionCanvasRenderer.isMediaUriAccessible(context, layer.mediaUri)

                                    // Sticky Layer Header
                                    Row(
                                        modifier = Modifier
                                            .width(headerWidth)
                                            .fillMaxHeight()
                                            .background(if (isSelected) Color(0xFF252D43) else OmsColors.Panel)
                                            .border(0.5.dp, OmsColors.Border)
                                            .combinedClickable(
                                                onClick = { viewModel.selectLayer(layer.id) },
                                                onLongClick = { viewModel.toggleMultiSelect(layer.id) }
                                            )
                                            .padding(horizontal = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (layer.hidden) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                            contentDescription = "Visibility",
                                            tint = if (layer.hidden) OmsColors.Muted.copy(alpha = 0.4f) else OmsColors.Muted,
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clickable {
                                                    viewModel.updateLayerById(layer.id) { it.copy(hidden = !it.hidden) }
                                                }
                                        )
                                        Spacer(Modifier.width(3.dp))
                                        Text(
                                            text = (if (isMulti) "✓ " else if (layer.parentId != null) "↳ " else "") + layer.name,
                                            fontSize = 10.sp,
                                            color = if (isMulti) OmsColors.Accent else if (isSelected) Color.White else OmsColors.Text,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (needsMediaRelink) {
                                            Text(
                                                "!",
                                                color = OmsColors.Danger,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 12.sp,
                                                modifier = Modifier.padding(horizontal = 2.dp)
                                            )
                                        }
                                        Icon(
                                            imageVector = if (layer.locked) Icons.Filled.Lock else Icons.Outlined.LockOpen,
                                            contentDescription = "Lock",
                                            tint = if (layer.locked) OmsColors.Orange else OmsColors.Muted.copy(alpha = 0.5f),
                                            modifier = Modifier
                                                .size(14.dp)
                                                .clickable {
                                                    viewModel.updateLayerById(layer.id) { it.copy(locked = !it.locked) }
                                                }
                                        )
                                    }

                                    // Scrollable Clip Lane
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .horizontalScroll(horizontalScroll)
                                            .border(0.5.dp, OmsColors.Border.copy(alpha = 0.4f))
                                    ) {
                                        Box(modifier = Modifier.width(laneWidthDp).fillMaxHeight()) {
                                            val startDp = (layer.start * pxPerSec).dp
                                            val durDp = (layer.duration * pxPerSec).coerceAtLeast(24f).dp
                                            Box(
                                                modifier = Modifier
                                                    .offset(x = startDp, y = 4.dp)
                                                    .width(durDp)
                                                    .height(26.dp)
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(clipColor.copy(alpha = if (layer.hidden) 0.4f else 0.92f))
                                                    .then(
                                                        if (isSelected) Modifier.border(2.dp, Color.White, RoundedCornerShape(4.dp))
                                                        else Modifier
                                                    )
                                                    .pointerInput(layer.id, pxPerSec) {
                                                        detectTapGestures { viewModel.selectLayer(layer.id) }
                                                    }
                                                    .pointerInput(layer.id, pxPerSec) {
                                                        detectDragGestures { change, dragAmount ->
                                                            change.consume()
                                                            if (!layer.locked) {
                                                                val dt = dragAmount.x / with(density) { pxPerSec.dp.toPx() }
                                                                viewModel.updateLayerById(layer.id, recordHistory = false) { l ->
                                                                    l.copy(start = (l.start + dt).coerceIn(0f, (doc.project.duration - 0.2f).coerceAtLeast(0f)))
                                                                }
                                                            }
                                                        }
                                                    }
                                            ) {
                                                // Real audio waveform bars inside AUDIO clips
                                                if (layer.type == LayerType.AUDIO && !layer.mediaUri.isNullOrBlank()) {
                                                    val peaks = remember(layer.mediaUri, layer.duration) {
                                                        com.example.render.AudioTrackEngine.getWaveformSlice(layer.mediaUri, 0f, layer.duration, 28)
                                                    }
                                                    if (peaks != null) {
                                                        Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp)) {
                                                            val bw = size.width / peaks.size
                                                            peaks.forEachIndexed { idx, pk ->
                                                                val bh = (pk * size.height * 0.85f).coerceAtLeast(2f)
                                                                drawLine(
                                                                    color = Color(0xFF0A141E).copy(alpha = 0.55f),
                                                                    start = Offset(idx * bw + bw * 0.5f, (size.height - bh) * 0.5f),
                                                                    end = Offset(idx * bw + bw * 0.5f, (size.height + bh) * 0.5f),
                                                                    strokeWidth = (bw * 0.65f).coerceAtLeast(1.5f)
                                                                )
                                                            }
                                                        }
                                                    }
                                                }

                                                Text(
                                                    text = layer.name,
                                                    color = Color(0xFF0A141E),
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 10.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.align(Alignment.CenterStart).padding(horizontal = 8.dp)
                                                )

                                                // Pink Diamond Keyframes inside clip
                                                val allKfs = layer.keyframes.values.flatten().map { it.time }.distinct()
                                                allKfs.forEach { kt ->
                                                    val kfX = (kt * pxPerSec).dp
                                                    Box(
                                                        modifier = Modifier
                                                            .offset(x = kfX - 6.dp)
                                                            .align(Alignment.CenterStart)
                                                            .size(8.dp)
                                                            .background(OmsColors.Pink, RoundedCornerShape(2.dp))
                                                            .border(1.dp, Color.White, RoundedCornerShape(2.dp))
                                                    )
                                                }

                                                // Left & Right Trim Handles when selected
                                                if (isSelected && !layer.locked) {
                                                    Box(
                                                        modifier = Modifier
                                                            .align(Alignment.CenterStart)
                                                            .width(10.dp)
                                                            .fillMaxHeight()
                                                            .background(Color.White.copy(alpha = 0.85f))
                                                            .pointerInput(layer.id, pxPerSec) {
                                                                detectDragGestures { change, drag ->
                                                                    change.consume()
                                                                    val dt = drag.x / with(density) { pxPerSec.dp.toPx() }
                                                                    viewModel.dragTrimLayerEdge(layer.id, trimLeft = true, deltaSec = dt)
                                                                }
                                                            }
                                                    )
                                                    Box(
                                                        modifier = Modifier
                                                            .align(Alignment.CenterEnd)
                                                            .width(10.dp)
                                                            .fillMaxHeight()
                                                            .background(Color.White.copy(alpha = 0.85f))
                                                            .pointerInput(layer.id, pxPerSec) {
                                                                detectDragGestures { change, drag ->
                                                                    change.consume()
                                                                    val dt = drag.x / with(density) { pxPerSec.dp.toPx() }
                                                                    viewModel.dragTrimLayerEdge(layer.id, trimLeft = false, deltaSec = dt)
                                                                }
                                                            }
                                                    )
                                                }
                                            }
                                            // Playhead vertical line
                                            Box(
                                                modifier = Modifier
                                                    .offset(x = (currentTime * pxPerSec).dp)
                                                    .width(1.5.dp)
                                                    .fillMaxHeight()
                                                    .background(Color.White.copy(alpha = 0.85f))
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Multi-Selection Batch Toolbar (when 2+ layers selected via long press)
            if (multiSelectedIds.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(OmsColors.Panel2)
                        .border(0.5.dp, OmsColors.Accent)
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${multiSelectedIds.size} dipilih", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = OmsColors.Accent)
                    TextButton(onClick = { viewModel.alignMultiSelected("left") }) { Text("Kiri", fontSize = 10.sp) }
                    TextButton(onClick = { viewModel.alignMultiSelected("centerH") }) { Text("Tengah", fontSize = 10.sp) }
                    TextButton(onClick = { viewModel.alignMultiSelected("right") }) { Text("Kanan", fontSize = 10.sp) }
                    TextButton(onClick = { viewModel.exportSelectedLayersJson() }) { Text("Ekspor JSON", fontSize = 10.sp, color = OmsColors.Cyan) }
                    TextButton(onClick = { viewModel.deleteMultiSelected() }) { Text("Hapus", fontSize = 10.sp, color = OmsColors.Danger) }
                    TextButton(onClick = { viewModel.clearMultiSelect() }) { Text("Tutup", fontSize = 10.sp, color = OmsColors.Muted) }
                }
            }

            // Bottom Action Toolbar
            val selectedLayer = doc.layers.find { it.id == selectedLayerId }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .background(OmsColors.Panel)
                    .border(0.5.dp, OmsColors.Border)
                    .horizontalScroll(rememberScrollState())
                    .padding(end = 68.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (selectedLayer == null) {
                    BottomToolButton(Icons.Filled.Interests, "Bentuk", false) { viewModel.openAddHub(AddHubTab.SHAPES) }
                    BottomToolButton(Icons.Filled.TextFields, "Teks", false) { viewModel.addTextLayer() }
                    BottomToolButton(Icons.Filled.PermMedia, "Media", false) { viewModel.openAddHub(AddHubTab.MEDIA) }
                    BottomToolButton(Icons.Filled.MusicNote, "Audio", false) { viewModel.openAddHub(AddHubTab.AUDIO) }
                    BottomToolButton(Icons.Filled.Videocam, "3D", false) { viewModel.openAddHub(AddHubTab.ELEMENTS) }
                    BottomToolButton(Icons.Filled.Edit, "Vektor", false) { viewModel.startVectorPen() }
                } else {
                    BottomToolButton(Icons.Filled.OpenWith, "Gerak", drawerMode == EditorDrawerMode.MOVE_TRANSFORM) {
                        viewModel.openDrawer(EditorDrawerMode.MOVE_TRANSFORM)
                    }
                    BottomToolButton(Icons.Filled.Palette, "Warna", drawerMode == EditorDrawerMode.COLOR) {
                        viewModel.openDrawer(EditorDrawerMode.COLOR)
                    }
                    BottomToolButton(Icons.Filled.AutoAwesome, "Efek", drawerMode == EditorDrawerMode.EFFECTS) {
                        viewModel.openDrawer(EditorDrawerMode.EFFECTS)
                    }
                    BottomToolButton(Icons.Filled.Layers, "Blend", drawerMode == EditorDrawerMode.BLEND_MASK) {
                        viewModel.openDrawer(EditorDrawerMode.BLEND_MASK)
                    }
                    when (selectedLayer.type) {
                        LayerType.TEXT -> BottomToolButton(Icons.Filled.TextFields, "Teks", drawerMode == EditorDrawerMode.TEXT_TYPOGRAPHY) {
                            viewModel.openDrawer(EditorDrawerMode.TEXT_TYPOGRAPHY)
                        }
                        LayerType.SHAPE -> BottomToolButton(Icons.Filled.Category, "Shape", drawerMode == EditorDrawerMode.SHAPE_STUDIO) {
                            viewModel.openDrawer(EditorDrawerMode.SHAPE_STUDIO)
                        }
                        LayerType.CAMERA, LayerType.MESH -> BottomToolButton(Icons.Filled.ViewInAr, "3D/Cam", drawerMode == EditorDrawerMode.CAMERA_3D) {
                            viewModel.openDrawer(EditorDrawerMode.CAMERA_3D)
                        }
                        else -> Unit
                    }
                    BottomToolButton(Icons.AutoMirrored.Filled.ShowChart, "Kurva", drawerMode == EditorDrawerMode.CURVE_EDITOR) {
                        viewModel.openDrawer(EditorDrawerMode.CURVE_EDITOR)
                    }
                    BottomToolButton(Icons.Filled.AccountTree, "Parent", drawerMode == EditorDrawerMode.HIERARCHY) {
                        viewModel.openDrawer(EditorDrawerMode.HIERARCHY)
                    }
                    BottomToolButton(Icons.Filled.MoreHoriz, "Opsi", drawerMode == EditorDrawerMode.INSPECTOR_ROOT) {
                        viewModel.openDrawer(EditorDrawerMode.INSPECTOR_ROOT)
                    }
                }
            }
        }

        // Floating Mint + Button
        FloatingActionButton(
            onClick = { viewModel.openAddHub() },
            shape = CircleShape,
            containerColor = OmsColors.Panel2,
            contentColor = OmsColors.Accent,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 10.dp, bottom = 60.dp)
                .size(46.dp)
                .border(2.dp, OmsColors.Accent, CircleShape)
                .testTag("editor_add_layer_fab")
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Tambah Layer", modifier = Modifier.size(26.dp))
        }
    }
}

@Composable
private fun BottomToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(64.dp)
            .fillMaxHeight()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (active) OmsColors.Accent else OmsColors.Muted,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            color = if (active) OmsColors.Accent else OmsColors.Muted,
            maxLines = 1
        )
    }
}
