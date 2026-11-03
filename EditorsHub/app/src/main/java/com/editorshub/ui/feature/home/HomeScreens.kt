package com.editorshub.ui.feature.home

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.launch
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.editorshub.core.ai.AiKeyStore
import com.editorshub.core.data.TemplateCatalog
import com.editorshub.core.domain.model.*
import com.editorshub.ui.components.ColorSwatchRow
import com.editorshub.ui.components.LabeledSelect
import com.editorshub.ui.components.SliderRow
import com.editorshub.ui.theme.*
import java.io.File
import kotlin.math.roundToInt

// ------------------------------------------------------------------ HOME
@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun HomeScreen(
    vm: HomeViewModel = hiltViewModel(),
    onOpenProject: (String) -> Unit,
    onProjects: () -> Unit,
    onTemplates: () -> Unit,
    onEffects: () -> Unit,
    onAi: () -> Unit,
    onSettings: () -> Unit,
    onTutorials: () -> Unit,
    onImport: () -> Unit,
    onNodeEditor: () -> Unit
) {
    val context = LocalContext.current
    val recent by vm.projects.collectAsState()
    val settings by vm.appSettings.collectAsState()
    var showCreate by remember { mutableStateOf(false) }
    var actionTarget by remember { mutableStateOf<ProjectSummary?>(null) }
    var infoTarget by remember { mutableStateOf<ProjectSummary?>(null) }
    var deleteTarget by remember { mutableStateOf<ProjectSummary?>(null) }
    var renameTarget by remember { mutableStateOf<ProjectSummary?>(null) }
    var exportTarget by remember { mutableStateOf<ProjectSummary?>(null) }
    val mediaPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) vm.importMedia(uris, uris.map { null })
    }
    LaunchedEffect(Unit) { vm.openProject.collect { onOpenProject(it) } }
    LaunchedEffect(Unit) { vm.info.collect { if (it != null) infoTarget = it } }

    Scaffold(
        containerColor = EditorPalette.Black,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreate = true },
                containerColor = EditorPalette.Purple,
                contentColor = EditorPalette.Black
            ) {
                Icon(Icons.Default.Add, null, Modifier.size(EditorDimens.IconSmall))
                Spacer(Modifier.width(EditorDimens.Space1))
                Text("New project", style = MaterialTheme.typography.labelLarge)
            }
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(170.dp),
            contentPadding = PaddingValues(EditorDimens.Space3),
            horizontalArrangement = Arrangement.spacedBy(EditorDimens.Space2),
            verticalArrangement = Arrangement.spacedBy(EditorDimens.Space2),
            modifier = Modifier.padding(padding).fillMaxSize()
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                HeroCard(onNew = { showCreate = true }, onImport = onImport,
                    onMedia = { mediaPicker.launch(arrayOf("video/*", "image/*", "audio/*")) })
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(horizontalArrangement = Arrangement.spacedBy(EditorDimens.Space2),
                    modifier = Modifier.fillMaxWidth()) {
                    QuickAction("Open", Icons.Default.FolderOpen) { onProjects() }
                    QuickAction("Templates", Icons.Default.Dashboard) { onTemplates() }
                    QuickAction("Effects", Icons.Default.AutoAwesome) { onEffects() }
                    QuickAction("AI Studio", Icons.Default.Bolt) { onAi() }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(horizontalArrangement = Arrangement.spacedBy(EditorDimens.Space2),
                    modifier = Modifier.fillMaxWidth()) {
                    QuickAction("Import", Icons.Default.Upload) { onImport() }
                    QuickAction("Nodes", Icons.Default.DeviceHub) { onNodeEditor() }
                    QuickAction("Docs", Icons.Default.School) { onTutorials() }
                    QuickAction("Settings", Icons.Default.Settings) { onSettings() }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Recent projects (${recent.size})", style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f))
                    TextButton(onClick = onProjects) { Text("See all", style = MaterialTheme.typography.labelMedium) }
                }
            }
            if (recent.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Card(Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = EditorPalette.Surface)) {
                        Column(Modifier.padding(EditorDimens.Space5).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.MovieCreation, null, Modifier.size(36.dp),
                                tint = EditorPalette.Purple)
                            Spacer(Modifier.height(EditorDimens.Space2))
                            Text("No projects yet", style = MaterialTheme.typography.titleMedium)
                            Text("Create a project, pick a template or import a project file.",
                                style = MaterialTheme.typography.bodySmall, color = EditorPalette.TextMuted)
                        }
                    }
                }
            } else {
                items(recent.take(6), key = { it.id }) { p ->
                    ProjectCard(p, vm, onClick = { onOpenProject(p.id) },
                        onAction = { actionTarget = p })
                }
            }
        }
    }

    if (showCreate) ProjectCreationSheet(vm, onDismiss = { showCreate = false },
        onCreated = { showCreate = false })

    actionTarget?.let { p ->
        ProjectActionsDialog(
            summary = p, vm = vm, confirmDelete = settings.confirmBeforeDelete,
            onDismiss = { actionTarget = null },
            onOpen = { onOpenProject(p.id); actionTarget = null },
            onRename = { renameTarget = p; actionTarget = null },
            onDelete = { if (settings.confirmBeforeDelete) deleteTarget = p else { vm.delete(p.id); actionTarget = null } },
            onInfo = { infoTarget = p; actionTarget = null },
            onExport = { exportTarget = p; actionTarget = null }
        )
    }
    deleteTarget?.let { p ->
        ConfirmDialog("Delete “${p.name}”?", "This removes the project and its version history.",
            onConfirm = { vm.delete(p.id); deleteTarget = null }, onDismiss = { deleteTarget = null })
    }
    renameTarget?.let { p ->
        var name by remember { mutableStateOf(p.name) }
        AlertDialog(onDismissRequest = { renameTarget = null }, title = { Text("Rename project") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { vm.rename(p.id, name); renameTarget = null }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancel") } })
    }
    infoTarget?.let { p -> ProjectInfoDialog(p, vm) { infoTarget = null } }
    exportTarget?.let { p -> QuickExportDialog(p, vm) { exportTarget = null } }
}

@Composable
private fun HeroCard(onNew: () -> Unit, onImport: () -> Unit, onMedia: () -> Unit) {
    Card(Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = EditorPalette.Surface)) {
        Box(Modifier.fillMaxWidth().background(EditorPalette.HeroBrush)) {
            Column(Modifier.padding(EditorDimens.Space4)) {
                Text("Create something that moves", style = MaterialTheme.typography.headlineMedium)
                Text("Layers · keyframes · GPU effects · real MP4 export",
                    style = MaterialTheme.typography.bodySmall, color = EditorPalette.TextSecondary)
                Spacer(Modifier.height(EditorDimens.Space3))
                Row(horizontalArrangement = Arrangement.spacedBy(EditorDimens.Space2)) {
                    Button(onClick = onNew) { Text("New project") }
                    OutlinedButton(onClick = onImport) { Text("Import project") }
                    OutlinedButton(onClick = onMedia) { Text("Media") }
                }
            }
        }
    }
}

@Composable
private fun RowScope.QuickAction(label: String,
                                 icon: androidx.compose.ui.graphics.vector.ImageVector,
                                 onClick: () -> Unit) {
    Card(Modifier.weight(1f).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = EditorPalette.Surface)) {
        Column(Modifier.padding(EditorDimens.Space2).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, Modifier.size(EditorDimens.Icon), tint = EditorPalette.Purple)
            Spacer(Modifier.height(EditorDimens.Space1))
            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

@Composable
private fun ProjectCard(summary: ProjectSummary, vm: HomeViewModel,
                        onClick: () -> Unit, onAction: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = EditorPalette.Surface)) {
        Box {
            if (summary.thumbnailPath != null && File(summary.thumbnailPath).exists()) {
                AsyncImage(model = File(summary.thumbnailPath), contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)))
            } else {
                Box(Modifier.fillMaxWidth().height(110.dp)
                    .background(Brush.linearGradient(listOf(Color(0xFF24263A), Color(0xFF1A2C3A)))),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Movie, null, tint = EditorPalette.Purple)
                }
            }
            IconButton(onClick = onAction, modifier = Modifier.align(Alignment.TopEnd).size(30.dp)) {
                Icon(Icons.Default.MoreVert, "Actions", modifier = Modifier.size(16.dp), tint = Color.White)
            }
            if (summary.interrupted) {
                Box(Modifier.align(Alignment.BottomStart).padding(4.dp)
                    .background(EditorPalette.Amber, RoundedCornerShape(4.dp)).padding(3.dp)) {
                    Text("RECOVER", fontSize = 7.sp, color = EditorPalette.Black)
                }
            }
        }
        Column(Modifier.padding(EditorDimens.Space2)) {
            Text(summary.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            Text("${summary.width}×${summary.height} · ${vm.formatFps(summary.fpsExact)}",
                style = MaterialTheme.typography.labelSmall, color = EditorPalette.TextMuted)
            Text("${vm.formatDuration(summary.durationMs)} · ${vm.formatBytes(summary.bytes)}",
                style = MaterialTheme.typography.labelSmall, color = EditorPalette.TextMuted)
        }
    }
}

// --------------------------------------------------------------- PROJECTS
@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun ProjectsScreen(
    vm: HomeViewModel = hiltViewModel(),
    onOpenProject: (String) -> Unit,
    onImport: () -> Unit,
    onNew: () -> Unit
) {
    val visible by vm.visible.collectAsState()
    val query by vm.query.collectAsState()
    val sort by vm.sort.collectAsState()
    val grid by vm.grid.collectAsState()
    val settings by vm.appSettings.collectAsState()
    val context = LocalContext.current
    var actionTarget by remember { mutableStateOf<ProjectSummary?>(null) }
    var deleteTarget by remember { mutableStateOf<ProjectSummary?>(null) }
    var infoTarget by remember { mutableStateOf<ProjectSummary?>(null) }
    var exportTarget by remember { mutableStateOf<ProjectSummary?>(null) }
    var renameTarget by remember { mutableStateOf<ProjectSummary?>(null) }
    var showCreate by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = EditorPalette.Black,
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { showCreate = true },
                containerColor = EditorPalette.Purple, contentColor = EditorPalette.Black) {
                Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text("New")
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(value = query, onValueChange = { vm.setQuery(it) },
                placeholder = { Text("Search projects") },
                leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(EditorDimens.IconSmall)) },
                modifier = Modifier.fillMaxWidth().padding(EditorDimens.Space3), singleLine = true)
            Row(Modifier.padding(horizontal = EditorDimens.Space3),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    LabeledSelect("Sort", sort.name, ProjectSort.entries.map { it.name to it.name }) {
                        vm.setSort(ProjectSort.valueOf(it))
                    }
                }
                Spacer(Modifier.width(EditorDimens.Space2))
                IconButton(onClick = { vm.setGrid(!grid) }) {
                    AnimatedContent(targetState = grid, label = "view-toggle") { g ->
                        Icon(if (g) Icons.Default.GridView else Icons.Default.ViewList, "Toggle view")
                    }
                }
                IconButton(onClick = onImport) { Icon(Icons.Default.Upload, "Import project") }
            }
            AnimatedContent(targetState = grid, transitionSpec = {
                fadeIn() + scaleIn(initialScale = 0.98f) togetherWith fadeOut() + scaleOut(targetScale = 0.98f)
            }, label = "layout") { isGrid ->
                if (visible.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.FolderOpen, null, Modifier.size(40.dp),
                                tint = EditorPalette.TextMuted)
                            Text("No projects found", style = MaterialTheme.typography.titleMedium)
                            TextButton(onClick = onNew) { Text("Create a project") }
                        }
                    }
                } else if (isGrid) {
                    LazyVerticalGrid(GridCells.Adaptive(170.dp), Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(EditorDimens.Space3),
                        horizontalArrangement = Arrangement.spacedBy(EditorDimens.Space2),
                        verticalArrangement = Arrangement.spacedBy(EditorDimens.Space2)) {
                        items(visible, key = { it.id }) { p ->
                            ProjectCard(p, vm, { onOpenProject(p.id) }, { actionTarget = p })
                        }
                    }
                } else {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(EditorDimens.Space3),
                        verticalArrangement = Arrangement.spacedBy(EditorDimens.Space2)) {
                        items(visible, key = { it.id }) { p ->
                            Card(Modifier.fillMaxWidth().clickable { onOpenProject(p.id) }) {
                                Row(Modifier.padding(EditorDimens.Space2),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    if (p.thumbnailPath != null && File(p.thumbnailPath).exists()) {
                                        AsyncImage(File(p.thumbnailPath), null, Modifier.size(56.dp)
                                            .clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                                    } else Box(Modifier.size(56.dp).background(EditorPalette.SurfaceVariant,
                                        RoundedCornerShape(8.dp)))
                                    Spacer(Modifier.width(EditorDimens.Space2))
                                    Column(Modifier.weight(1f)) {
                                        Text(p.name, style = MaterialTheme.typography.titleSmall)
                                        Text("${p.width}×${p.height} · ${vm.formatFps(p.fpsExact)} · ${vm.formatDuration(p.durationMs)}",
                                            style = MaterialTheme.typography.labelSmall, color = EditorPalette.TextMuted)
                                        Text("Modified ${vm.formatDate(p.updatedAt)} · ${vm.formatBytes(p.bytes)}",
                                            style = MaterialTheme.typography.labelSmall, color = EditorPalette.TextMuted)
                                    }
                                    IconButton(onClick = { actionTarget = p }) { Icon(Icons.Default.MoreVert, null) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreate) ProjectCreationSheet(vm, { showCreate = false }, { showCreate = false })
    actionTarget?.let { p ->
        ProjectActionsDialog(p, vm, settings.confirmBeforeDelete,
            { actionTarget = null }, { onOpenProject(p.id); actionTarget = null },
            { renameTarget = p; actionTarget = null },
            { if (settings.confirmBeforeDelete) deleteTarget = p else { vm.delete(p.id); actionTarget = null } },
            { infoTarget = p; actionTarget = null }, { exportTarget = p; actionTarget = null })
    }
    deleteTarget?.let { p -> ConfirmDialog("Delete “${p.name}”?",
        "This removes the project and its version history.",
        { vm.delete(p.id); deleteTarget = null }, { deleteTarget = null }) }
    renameTarget?.let { p ->
        var name by remember { mutableStateOf(p.name) }
        AlertDialog(onDismissRequest = { renameTarget = null }, title = { Text("Rename") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { vm.rename(p.id, name); renameTarget = null }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancel") } })
    }
    infoTarget?.let { ProjectInfoDialog(it, vm) { infoTarget = null } }
    exportTarget?.let { QuickExportDialog(it, vm) { exportTarget = null } }
}

@Composable
private fun ProjectActionsDialog(
    summary: ProjectSummary, vm: HomeViewModel, confirmDelete: Boolean,
    onDismiss: () -> Unit, onOpen: () -> Unit, onRename: () -> Unit,
    onDelete: () -> Unit, onInfo: () -> Unit, onExport: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(summary.name) },
        text = {
            Column {
                ActionRow(Icons.Default.PlayArrow, "Open") { onOpen() }
                ActionRow(Icons.Default.Edit, "Rename") { onRename() }
                ActionRow(Icons.Default.ContentCopy, "Duplicate") { vm.duplicate(summary.id); onDismiss() }
                ActionRow(Icons.Default.Share, "Share project file") {
                    scope.launch {
                        val path = vm.shareProject(summary.id) ?: return@launch
                        val uri = FileProvider.getUriForFile(context,
                            "${context.packageName}.fileprovider", File(path))
                        val share = Intent(Intent.ACTION_SEND).apply {
                            type = "application/json"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(share, "Share project"))
                    }
                    onDismiss()
                }
                ActionRow(Icons.Default.FileDownload, "Export video") { onExport() }
                ActionRow(Icons.Default.Info, "Project information") { onInfo() }
                ActionRow(Icons.Default.Delete, "Delete", tint = EditorPalette.Red) { onDelete() }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun ActionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String,
                      tint: Color = EditorPalette.TextPrimary, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(18.dp), tint = tint)
        Spacer(Modifier.width(EditorDimens.Space3))
        Text(label, style = MaterialTheme.typography.bodySmall, color = tint)
    }
}

@Composable
private fun ConfirmDialog(title: String, body: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(body) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Delete", color = EditorPalette.Red) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun ProjectInfoDialog(p: ProjectSummary, vm: HomeViewModel, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Project information") },
        text = {
            Column {
                InfoLine("Name", p.name)
                InfoLine("Resolution", "${p.width} × ${p.height}")
                InfoLine("Frame rate", vm.formatFps(p.fpsExact))
                InfoLine("Duration", vm.formatDuration(p.durationMs))
                InfoLine("Layers", "${p.layerCount}")
                InfoLine("File size", vm.formatBytes(p.bytes))
                InfoLine("Modified", vm.formatDate(p.updatedAt))
                InfoLine("Version", "${p.version}")
                InfoLine("State", if (p.interrupted) "Autosave recovery available" else "Saved")
            }
        }, confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } })
}

@Composable
private fun InfoLine(k: String, v: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(k, style = MaterialTheme.typography.labelSmall, color = EditorPalette.TextMuted,
            modifier = Modifier.width(96.dp))
        Text(v, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun QuickExportDialog(p: ProjectSummary, vm: HomeViewModel, onDismiss: () -> Unit) {
    val settings by vm.appSettings.collectAsState()
    val short = 1080
    var fps by remember { mutableIntStateOf(30) }
    var bitrate by remember { mutableFloatStateOf(8f) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Export “${p.name}”") },
        text = {
            Column {
                Text("${p.width}×${p.height} source → ${short}p output",
                    style = MaterialTheme.typography.labelSmall)
                LabeledSelect("Frame rate", fps.toString(),
                    listOf("24", "30", "60").map { it to "$it fps" }) { fps = it.toInt() }
                SliderRow("Bitrate (Mbps)", bitrate, 1f..30f, { bitrate = it })
                Text("Rendering uses the real MediaCodec pipeline in the background.",
                    style = MaterialTheme.typography.labelSmall, color = EditorPalette.TextMuted)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val ratio = p.width.toFloat() / p.height.toFloat()
                val (w, h) = if (ratio >= 1f) short to (short / ratio).roundToInt()
                    else (short * ratio).roundToInt() to short
                vm.exportProject(p.id, ExportConfig(width = w / 2 * 2, height = h / 2 * 2,
                    fps = fps, fpsExact = fps.toDouble(), bitrate = (bitrate * 1_000_000).toInt(),
                    saveLocation = settings.defaultSaveLocation), { })
                onDismiss()
            }) { Text("Start export") }
        }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

// ------------------------------------------------------------- CREATION SHEET
private data class ResPreset(val label: String, val shortSide: Int)
private val RES_PRESETS = listOf(
    ResPreset("144p", 144), ResPreset("240p", 240), ResPreset("360p", 360),
    ResPreset("480p", 480), ResPreset("720p", 720), ResPreset("1080p", 1080),
    ResPreset("1440p", 1440), ResPreset("2160p (4K)", 2160), ResPreset("Custom", 0)
)
private val ASPECTS = listOf("1:1", "4:5", "9:16", "16:9", "3:4", "4:3", "21:9", "Custom")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectCreationSheet(vm: HomeViewModel, onDismiss: () -> Unit, onCreated: () -> Unit) {
    var name by remember { mutableStateOf("Untitled project") }
    var res by remember { mutableStateOf(RES_PRESETS[5]) }
    var aspect by remember { mutableStateOf("9:16") }
    var customW by remember { mutableStateOf("1080") }
    var customH by remember { mutableStateOf("1920") }
    var fps by remember { mutableStateOf(FrameRates.ALL[6]) }
    var duration by remember { mutableFloatStateOf(10f) }
    var bgType by remember { mutableStateOf(BackgroundType.SOLID) }
    var bgColor by remember { mutableStateOf("#FF0B0C14") }
    var bgEnd by remember { mutableStateOf("#FF22D3EE") }
    var bgAngle by remember { mutableFloatStateOf(45f) }
    var sampleRate by remember { mutableStateOf("48000") }
    var colorSpace by remember { mutableStateOf(ColorSpace.SRGB) }
    var quality by remember { mutableFloatStateOf(0.75f) }

    val (w, h) = remember(res, aspect, customW, customH) {
        if (res.shortSide == 0 || aspect == "Custom") {
            (customW.toIntOrNull() ?: 1080).coerceIn(16, 4096) to
                (customH.toIntOrNull() ?: 1920).coerceIn(16, 4096)
        } else {
            val parts = aspect.split(":").mapNotNull { it.toFloatOrNull() }
            val rw = parts.getOrNull(0) ?: 9f
            val rh = parts.getOrNull(1) ?: 16f
            if (rw >= rh) {
                val height = res.shortSide
                val width = (height * rw / rh).roundToInt()
                (width / 2 * 2) to (height / 2 * 2)
            } else {
                val width = res.shortSide
                val height = (width * rh / rw).roundToInt()
                (width / 2 * 2) to (height / 2 * 2)
            }
        }
    }
    val timebase = remember(fps) {
        when {
            kotlin.math.abs(fps.exact - 23.976) < 0.01 -> "1001/24000"
            kotlin.math.abs(fps.exact - 29.97) < 0.01 -> "1001/30000"
            kotlin.math.abs(fps.exact - 59.94) < 0.01 -> "1001/60000"
            else -> "1/${fps.exact.roundToInt()}"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New project") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(name, { name = it }, label = { Text("Project name") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(EditorDimens.Space2))
                LabeledSelect("Resolution", res.label, RES_PRESETS.map { it.label to it.label }) {
                    res = RES_PRESETS.first { p -> p.label == it }
                }
                LabeledSelect("Aspect ratio", aspect, ASPECTS.map { it to it }) { aspect = it }
                if (res.shortSide == 0 || aspect == "Custom") {
                    Row(horizontalArrangement = Arrangement.spacedBy(EditorDimens.Space2)) {
                        OutlinedTextField(customW, { customW = it }, label = { Text("Width") },
                            modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(customH, { customH = it }, label = { Text("Height") },
                            modifier = Modifier.weight(1f), singleLine = true)
                    }
                }
                Text("Output: ${w} × ${h}", style = MaterialTheme.typography.labelSmall,
                    color = EditorPalette.Cyan)
                LabeledSelect("Frame rate", fps.label, FrameRates.ALL.map { it.label to it.label }) {
                    fps = FrameRates.ALL.first { p -> p.label == it }
                }
                SliderRow("Default duration (s)", duration, 1f..120f, { duration = it })
                LabeledSelect("Background", bgType.name,
                    BackgroundType.entries.map { it.name to it.name }) { bgType = BackgroundType.valueOf(it) }
                if (bgType != BackgroundType.TRANSPARENT) {
                    ColorSwatchRow("Primary", bgColor) { bgColor = it }
                    if (bgType == BackgroundType.LINEAR_GRADIENT || bgType == BackgroundType.RADIAL_GRADIENT) {
                        ColorSwatchRow("Gradient end", bgEnd) { bgEnd = it }
                        if (bgType == BackgroundType.LINEAR_GRADIENT)
                            SliderRow("Gradient angle", bgAngle, 0f..360f, { bgAngle = it })
                    }
                }
                LabeledSelect("Audio sample rate", sampleRate,
                    listOf("44100", "48000", "96000").map { it to "$it Hz" }) { sampleRate = it }
                LabeledSelect("Color space", colorSpace.name,
                    ColorSpace.entries.map { it.name to it.name }) { colorSpace = ColorSpace.valueOf(it) }
                SliderRow("Preview quality", quality, 0.2f..1f, { quality = it })
                Text("Timebase: $timebase · saved in project configuration",
                    style = MaterialTheme.typography.labelSmall, color = EditorPalette.TextMuted)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                vm.createProject(ProjectSpec(
                    name = name.ifBlank { "Untitled project" },
                    width = w, height = h, aspect = aspect, fpsExact = fps.exact,
                    durationMs = (duration * 1000).toLong(),
                    backgroundType = bgType, background = bgColor,
                    backgroundEnd = bgEnd, backgroundAngle = bgAngle,
                    audioSampleRate = sampleRate.toIntOrNull() ?: 48000,
                    colorSpace = colorSpace, previewQuality = quality, timebase = timebase
                ))
                onCreated()
            }) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// -------------------------------------------------------------- TEMPLATES
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplatesScreen(vm: HomeViewModel = hiltViewModel(), onBack: () -> Unit,
                    onCreated: (String) -> Unit) {
    LaunchedEffect(Unit) { vm.openProject.collect { onCreated(it) } }
    Scaffold(containerColor = EditorPalette.Black, topBar = {
        TopAppBar(title = { Text("Templates") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = EditorPalette.Charcoal))
    }) { padding ->
        LazyColumn(Modifier.padding(padding).padding(EditorDimens.Space3),
            verticalArrangement = Arrangement.spacedBy(EditorDimens.Space2)) {
            items(vm.templates) { t ->
                Card(Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = EditorPalette.Surface)) {
                    Column(Modifier.padding(EditorDimens.Space3)) {
                        Text(t.name, style = MaterialTheme.typography.titleMedium)
                        Text(t.description, style = MaterialTheme.typography.bodySmall,
                            color = EditorPalette.TextMuted)
                        Spacer(Modifier.height(EditorDimens.Space2))
                        Button(onClick = { vm.createFromTemplate(t) }) { Text("Use template") }
                    }
                }
            }
        }
    }
}
