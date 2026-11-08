package com.editorshub.ui.feature.home

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
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
import androidx.compose.ui.text.font.FontWeight
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
import com.editorshub.ui.components.pressScale
import com.editorshub.ui.theme.*
import java.io.File
import kotlin.math.roundToInt

// ------------------------------------------------------------------ HOME
@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun HomeScreen(
    vm: HomeViewModel = hiltViewModel(),
    onOpenProject: (String) -> Unit,
    onSettings: () -> Unit,
    onImport: () -> Unit,
    onProjects: () -> Unit,
    onTemplates: () -> Unit,
    createRequest: Int = 0
) {
    val settings by vm.appSettings.collectAsState()
    val projects by vm.projects.collectAsState()
    var showCreate by remember { mutableStateOf(false) }
    val recent = remember(projects) { projects.sortedByDescending { it.updatedAt }.take(4) }

    val context = LocalContext.current
    val mediaPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            val names = uris.map { uri ->
                runCatching {
                    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                        ?.use { cursor ->
                            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (cursor.moveToFirst() && index >= 0) cursor.getString(index) else null
                        }
                }.getOrNull()
            }
            vm.importMedia(uris, names)
        }
    }
    LaunchedEffect(Unit) { vm.openProject.collect { onOpenProject(it) } }
    LaunchedEffect(createRequest) { if (createRequest > 0) showCreate = true }

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.message.collect { snackbar.showSnackbar(it) } }

    Scaffold(containerColor = EditorPalette.Black, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize().background(EditorPalette.Black),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item(key = "brand") { HomeBrandHeader(settings.brandName, settings.brandSubtitle, onSettings) }
            item(key = "hero") {
                HomeHeroCard(
                    onNew = { showCreate = true },
                    onMedia = { mediaPicker.launch(arrayOf("video/*", "image/*", "audio/*")) }
                )
            }
            item(key = "quick-actions") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DashboardAction(
                        title = "Import project",
                        subtitle = "XML · ZIP · project",
                        icon = Icons.Default.FileOpen,
                        modifier = Modifier.weight(1f),
                        onClick = onImport
                    )
                    DashboardAction(
                        title = "Templates",
                        subtitle = "Start from a design",
                        icon = Icons.Default.Dashboard,
                        modifier = Modifier.weight(1f),
                        onClick = onTemplates
                    )
                }
            }
            item(key = "recent-heading") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Recent projects", style = MaterialTheme.typography.titleLarge,
                            color = EditorPalette.TextPrimary)
                        Text(if (recent.isEmpty()) "Your edits will appear here" else "Pick up where you left off",
                            style = MaterialTheme.typography.bodySmall, color = EditorPalette.TextMuted)
                    }
                    TextButton(onClick = onProjects) {
                        Text("View all", color = EditorPalette.Purple, fontSize = 12.sp)
                        Icon(Icons.Default.ChevronRight, null, Modifier.size(16.dp), tint = EditorPalette.Purple)
                    }
                }
            }
            if (recent.isEmpty()) {
                item(key = "recent-empty") {
                    EmptyRecentProjects(onCreate = { showCreate = true }, onImportMedia = {
                        mediaPicker.launch(arrayOf("video/*", "image/*", "audio/*"))
                    })
                }
            } else {
                items(recent, key = { "recent-${it.id}" }) { project ->
                    RecentProjectCard(project, vm) { onOpenProject(project.id) }
                }
            }
        }
    }

    if (showCreate) ProjectCreationSheet(vm, onDismiss = { showCreate = false },
        onCreated = { showCreate = false })
}

@Composable
private fun HomeHeroCard(onNew: () -> Unit, onMedia: () -> Unit) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = EditorPalette.Surface)
    ) {
        Box(Modifier.fillMaxWidth().background(EditorPalette.HeroBrush)) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(32.dp).clip(CircleShape).background(EditorPalette.Purple.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.AutoAwesome, null, Modifier.size(17.dp), tint = EditorPalette.Purple)
                    }
                    Spacer(Modifier.width(9.dp))
                    Text("MOTION STUDIO", fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp, color = EditorPalette.Purple)
                }
                Text("Create your next edit", style = MaterialTheme.typography.headlineMedium,
                    color = EditorPalette.TextPrimary)
                Text("Build with video, images, text, shapes and audio on a layered timeline.",
                    style = MaterialTheme.typography.bodySmall, color = EditorPalette.TextSecondary)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val createInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                    Box(
                        Modifier.weight(1.2f).height(48.dp).pressScale(createInteraction, 0.96f)
                            .clip(RoundedCornerShape(14.dp)).background(EditorPalette.Purple)
                            .clickable(interactionSource = createInteraction, indication = androidx.compose.foundation.LocalIndication.current,
                                role = androidx.compose.ui.semantics.Role.Button, onClick = onNew),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Add, null, Modifier.size(20.dp), tint = EditorPalette.Black)
                            Spacer(Modifier.width(6.dp))
                            Text("New project", fontWeight = FontWeight.Bold, color = EditorPalette.Black)
                        }
                    }
                    val mediaInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                    Box(
                        Modifier.weight(1f).height(48.dp).pressScale(mediaInteraction, 0.96f)
                            .clip(RoundedCornerShape(14.dp)).background(EditorPalette.SurfaceRaised)
                            .clickable(interactionSource = mediaInteraction, indication = androidx.compose.foundation.LocalIndication.current,
                                role = androidx.compose.ui.semantics.Role.Button, onClick = onMedia),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.VideoLibrary, null, Modifier.size(18.dp), tint = EditorPalette.TextPrimary)
                            Spacer(Modifier.width(6.dp))
                            Text("Add media", fontWeight = FontWeight.SemiBold, color = EditorPalette.TextPrimary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardAction(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Card(
        modifier = modifier.pressScale(interaction, 0.97f)
            .clickable(interactionSource = interaction, indication = androidx.compose.foundation.LocalIndication.current,
                onClick = onClick),
        shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(containerColor = EditorPalette.Surface)
    ) {
        Row(Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(EditorPalette.SurfaceRaised),
                contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(19.dp), tint = EditorPalette.Purple)
            }
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.labelLarge, color = EditorPalette.TextPrimary,
                    maxLines = 1)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = EditorPalette.TextMuted,
                    maxLines = 1)
            }
        }
    }
}

@Composable
private fun EmptyRecentProjects(onCreate: () -> Unit, onImportMedia: () -> Unit) {
    Card(shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = EditorPalette.Surface)) {
        Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(54.dp).clip(CircleShape).background(EditorPalette.SurfaceRaised),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Default.MovieCreation, null, Modifier.size(25.dp), tint = EditorPalette.Purple)
            }
            Text("Your studio is ready", style = MaterialTheme.typography.titleMedium,
                color = EditorPalette.TextPrimary)
            Text("Create a project or bring in clips from your device to start editing.",
                style = MaterialTheme.typography.bodySmall, color = EditorPalette.TextMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onCreate) { Text("Create project", color = EditorPalette.Purple) }
                TextButton(onClick = onImportMedia) { Text("Choose media", color = EditorPalette.TextPrimary) }
            }
        }
    }
}

@Composable
private fun RecentProjectCard(project: ProjectSummary, vm: HomeViewModel, onClick: () -> Unit) {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Card(
        modifier = Modifier.fillMaxWidth().pressScale(interaction, 0.98f)
            .clickable(interactionSource = interaction, indication = androidx.compose.foundation.LocalIndication.current,
                onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = EditorPalette.Surface)
    ) {
        Row(Modifier.fillMaxWidth().height(94.dp).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(94.dp).fillMaxHeight().clip(RoundedCornerShape(11.dp))) {
                val thumb = project.thumbnailPath?.let(::File)?.takeIf { it.exists() }
                if (thumb != null) {
                    AsyncImage(model = thumb, contentDescription = "${project.name} preview",
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Box(Modifier.fillMaxSize().background(Brush.linearGradient(
                        listOf(EditorPalette.SurfaceRaised, EditorPalette.SurfaceVariant))),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Movie, null, Modifier.size(26.dp), tint = EditorPalette.Purple)
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(project.name, style = MaterialTheme.typography.titleSmall, color = EditorPalette.TextPrimary,
                    maxLines = 1)
                Text("${project.width} × ${project.height} · ${vm.formatFps(project.fpsExact)} fps",
                    style = MaterialTheme.typography.labelSmall, color = EditorPalette.TextSecondary, maxLines = 1)
                Text("${vm.formatDuration(project.durationMs)} · ${project.layerCount} layers",
                    style = MaterialTheme.typography.labelSmall, color = EditorPalette.TextMuted, maxLines = 1)
            }
            Icon(Icons.Default.ChevronRight, null, Modifier.size(21.dp), tint = EditorPalette.TextMuted)
        }
    }
}

@Composable
private fun HomeBrandHeader(brand: String, subtitle: String, onSettings: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onSettings, modifier = Modifier.size(44.dp)) {
            Icon(Icons.Default.Settings, "Settings", Modifier.size(24.dp), tint = EditorPalette.TextPrimary)
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(brand.ifBlank { "EditorsHub" }, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold,
                color = EditorPalette.TextPrimary, letterSpacing = 0.8.sp, maxLines = 1)
            if (subtitle.isNotBlank()) Text(subtitle, fontSize = 8.sp, fontWeight = FontWeight.SemiBold,
                color = EditorPalette.TextPrimary, maxLines = 1)
        }
        IconButton(onClick = onSettings, modifier = Modifier.size(42.dp)
            .clip(CircleShape).background(Color(0xFF60616B))) {
            Icon(Icons.Default.Person, "Identity and account settings", Modifier.size(23.dp), tint = Color.White)
        }
    }
}

@Composable
private fun ProjectCard(summary: ProjectSummary, vm: HomeViewModel,
                        onClick: () -> Unit, onAction: () -> Unit) {
    val interaction = remember(summary.id) { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Card(Modifier.fillMaxWidth().pressScale(interaction, 0.98f)
        .clickable(interactionSource = interaction, indication = androidx.compose.foundation.LocalIndication.current,
            onClick = onClick),
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

@Composable
private fun ProjectSectionButton(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.fillMaxHeight().clip(CircleShape)
            .background(if (selected) EditorPalette.SurfaceVariant else Color.Transparent)
            .then(if (selected) Modifier else Modifier.clickable(onClick = onClick)),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 11.sp, maxLines = 1,
            color = if (selected) EditorPalette.TextPrimary else EditorPalette.TextMuted)
    }
}

// --------------------------------------------------------------- PROJECTS
@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun ProjectsScreen(
    vm: HomeViewModel = hiltViewModel(),
    onOpenProject: (String) -> Unit,
    onImport: () -> Unit,
    onNew: () -> Unit,
    onTemplates: () -> Unit,
    onEffects: () -> Unit,
    onSettings: () -> Unit
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
    Scaffold(containerColor = EditorPalette.Black) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Spacer(Modifier.height(42.dp))
            HomeBrandHeader(settings.brandName, settings.brandSubtitle, onSettings)
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
                .height(44.dp).clip(CircleShape).background(EditorPalette.Surface),
                verticalAlignment = Alignment.CenterVertically) {
                ProjectSectionButton("Projects", true, Modifier.weight(1f)) { }
                ProjectSectionButton("Your Templates", false, Modifier.weight(1.2f), onTemplates)
                ProjectSectionButton("Elements", false, Modifier.weight(1f), onEffects)
            }
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
    val interaction = remember(label) { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Row(Modifier.fillMaxWidth().pressScale(interaction, 0.97f)
        .clickable(interactionSource = interaction, indication = androidx.compose.foundation.LocalIndication.current,
            onClick = onClick).padding(vertical = 8.dp),
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
    var name by remember { mutableStateOf("New Project 1") }
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

/** In-app wordmark: the same "EH" monogram used by the launcher icon. */
@Composable
private fun EditorsHubBrand() {
    Row(verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EditorDimens.Space2)) {
        Box(
            modifier = Modifier.size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF1B1E3C), Color(0xFF0B0C14))))
                .border(1.dp, Color(0x338B7BFF), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Row {
                Text("E", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold,
                    color = EditorPalette.Purple, letterSpacing = (-1).sp)
                Text("H", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold,
                    color = EditorPalette.Cyan, letterSpacing = (-1).sp)
            }
        }
        Column {
            Text("EditorsHub", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold, color = EditorPalette.TextPrimary)
            Text("VIDEO & MOTION EDITOR", fontSize = 8.sp,
                color = EditorPalette.TextMuted, letterSpacing = 1.2.sp)
        }
    }
}
