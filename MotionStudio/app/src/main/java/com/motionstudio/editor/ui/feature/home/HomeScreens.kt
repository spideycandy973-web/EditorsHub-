package com.motionstudio.editor.ui.feature.home

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.motionstudio.editor.core.data.TemplateCatalog
import com.motionstudio.editor.core.domain.model.ExportResolution
import com.motionstudio.editor.core.domain.model.ProjectSummary
import com.motionstudio.editor.ui.components.LabeledSelect
import com.motionstudio.editor.ui.components.SliderRow
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: HomeViewModel = hiltViewModel(),
    onOpenProject: (String) -> Unit,
    onTemplates: () -> Unit,
    onSettings: () -> Unit,
    onTutorials: () -> Unit
) {
    val projects by vm.projects.collectAsState()
    val context = LocalContext.current
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> if (uris.isNotEmpty()) vm.importMedia(uris, uris.map { null }) }
    var showNew by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<ProjectSummary?>(null) }
    var menuTarget by remember { mutableStateOf<ProjectSummary?>(null) }
    var newName by remember { mutableStateOf("Untitled project") }
    var chosenTemplate by remember { mutableStateOf<TemplateCatalog.Template?>(null) }

    LaunchedEffect(Unit) { vm.openProject.collect { onOpenProject(it) } }
    LaunchedEffect(Unit) { vm.message.collect { /* surfaced via Snackbar below */ } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(30.dp).background(
                                Brush.linearGradient(listOf(Color(0xFF8B7BFF), Color(0xFFF472B6))),
                                RoundedCornerShape(9.dp)
                            ),
                            contentAlignment = Alignment.Center
                        ) { Text("M", color = Color.White, fontWeight = FontWeight.Bold) }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Motion Studio", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("Professional motion editor", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Settings") }
                    IconButton(onClick = onTutorials) { Icon(Icons.Default.School, "Tutorials") }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { showNew = true }) {
                Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("New project")
            }
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(170.dp),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(padding).fillMaxSize()
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                HeroCard(
                    onNew = { showNew = true },
                    onImport = { importLauncher.launch(arrayOf("video/*", "image/*", "audio/*")) },
                    onTemplates = onTemplates
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    QuickAction("Import media", Icons.Default.Upload) {
                        importLauncher.launch(arrayOf("video/*", "image/*", "audio/*"))
                    }
                    QuickAction("Templates", Icons.Default.Dashboard) { onTemplates() }
                    QuickAction("Settings", Icons.Default.Tune) { onSettings() }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text("Recent projects (${projects.size})", style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(vertical = 6.dp))
            }
            if (projects.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Movie, null, Modifier.size(38.dp),
                                tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(8.dp))
                            Text("No projects yet", fontWeight = FontWeight.SemiBold)
                            Text("Create a blank project, pick a template, or import media.",
                                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                items(projects, key = { it.id }) { p ->
                    ProjectCard(
                        summary = p,
                        onClick = { onOpenProject(p.id) },
                        onMenu = { menuTarget = p }
                    )
                }
            }
        }
    }

    if (menuTarget != null) {
        val target = menuTarget!!
        AlertDialog(
            onDismissRequest = { menuTarget = null },
            title = { Text(target.name) },
            text = { Text("${target.layerCount} layers · version ${target.version}\n${if (target.interrupted) "Unsaved session can be recovered" else "Saved"}") },
            confirmButton = {
                TextButton(onClick = { renameTarget = target; menuTarget = null }) { Text("Rename") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { vm.duplicate(target.id); menuTarget = null }) { Text("Duplicate") }
                    TextButton(onClick = { vm.delete(target.id); menuTarget = null }) { Text("Delete") }
                }
            }
        )
    }

    if (renameTarget != null) {
        var name by remember { mutableStateOf(renameTarget!!.name) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename project") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = { vm.rename(renameTarget!!.id, name); renameTarget = null }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancel") } }
        )
    }

    if (showNew) {
        AlertDialog(
            onDismissRequest = { showNew = false },
            title = { Text("New project") },
            text = {
                Column {
                    OutlinedTextField(value = newName, onValueChange = { newName = it },
                        label = { Text("Project name") }, singleLine = true)
                    Spacer(Modifier.height(10.dp))
                    Text("Start from", fontSize = 11.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = chosenTemplate == null, onClick = { chosenTemplate = null },
                            label = { Text("Blank", fontSize = 10.sp) })
                        vm.templates.forEach { t ->
                            FilterChip(selected = chosenTemplate === t, onClick = { chosenTemplate = t },
                                label = { Text(t.name, fontSize = 10.sp) })
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.createProject(newName.ifBlank { "Untitled project" }, chosenTemplate)
                    showNew = false
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showNew = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun HeroCard(onNew: () -> Unit, onImport: () -> Unit, onTemplates: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1D31))
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Create something that moves", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("Layers · keyframes · effects · real MP4 export", fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onNew) { Text("New project") }
                OutlinedButton(onClick = onImport) { Text("Import") }
                OutlinedButton(onClick = onTemplates) { Text("Templates") }
            }
        }
    }
}

@Composable
private fun RowScope.QuickAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Card(modifier = Modifier.weight(1f).clickable(onClick = onClick)) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(4.dp))
            Text(label, fontSize = 10.sp)
        }
    }
}

@Composable
private fun ProjectCard(summary: ProjectSummary, onClick: () -> Unit, onMenu: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Box {
            if (summary.thumbnailPath != null && File(summary.thumbnailPath).exists()) {
                AsyncImage(
                    model = File(summary.thumbnailPath), contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(120.dp)
                )
            } else {
                Box(Modifier.fillMaxWidth().height(120.dp).background(Color(0xFF24263A)),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Movie, null, tint = Color(0xFF8B7BFF))
                }
            }
            IconButton(onClick = onMenu, modifier = Modifier.align(Alignment.TopEnd)) {
                Icon(Icons.Default.MoreVert, "Project menu", tint = Color.White)
            }
        }
        Column(Modifier.padding(8.dp)) {
            Text(summary.name, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1)
            Text("${summary.layerCount} layers · ${if (summary.interrupted) "recoverable" else "saved"}",
                fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplatesScreen(
    vm: HomeViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onCreated: (String) -> Unit
) {
    LaunchedEffect(Unit) { vm.openProject.collect { onCreated(it) } }
    Scaffold(topBar = {
        TopAppBar(title = { Text("Templates") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
        })
    }) { padding ->
        LazyColumn(Modifier.padding(padding).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(vm.templates) { t ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text(t.name, fontWeight = FontWeight.Bold)
                        Text(t.description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { vm.createProject(t.name, t) }) { Text("Use template") }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: HomeViewModel = hiltViewModel(), onBack: () -> Unit) {
    val s by vm.appSettings.collectAsState()
    var cache by remember { mutableStateOf(vm.cacheSize()) }
    Scaffold(topBar = {
        TopAppBar(title = { Text("Settings") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
        })
    }) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(12.dp)) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Appearance", fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Dark theme", modifier = Modifier.weight(1f), fontSize = 12.sp)
                        Switch(checked = s.darkTheme, onCheckedChange = { v -> vm.updateSettings { it.copy(darkTheme = v) } })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Dynamic color (Android 12+)", modifier = Modifier.weight(1f), fontSize = 12.sp)
                        Switch(checked = s.dynamicColor, onCheckedChange = { v -> vm.updateSettings { it.copy(dynamicColor = v) } })
                    }
                    SliderRow("Preview quality", s.previewQuality, 0.2f..1f,
                        { v -> vm.updateSettings { it.copy(previewQuality = v) } })
                }
            }
            Spacer(Modifier.height(10.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Default export", fontWeight = FontWeight.Bold)
                    LabeledSelect("Resolution", s.defaultResolution.name,
                        ExportResolution.entries.map { it.name to it.label }) { v ->
                        vm.updateSettings { it.copy(defaultResolution = ExportResolution.valueOf(v)) }
                    }
                    LabeledSelect("Frame rate", s.defaultFps.toString(),
                        listOf("24", "30", "60").map { it to "$it fps" }) { v ->
                        vm.updateSettings { it.copy(defaultFps = v.toInt()) }
                    }
                    SliderRow("Bitrate (Mbps)", s.defaultBitrate / 1_000_000f, 1f..30f,
                        { v -> vm.updateSettings { it.copy(defaultBitrate = (v * 1_000_000).toInt()) } })
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Include audio", modifier = Modifier.weight(1f), fontSize = 12.sp)
                        Switch(checked = s.defaultIncludeAudio,
                            onCheckedChange = { v -> vm.updateSettings { it.copy(defaultIncludeAudio = v) } })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Timeline snapping", modifier = Modifier.weight(1f), fontSize = 12.sp)
                        Switch(checked = s.snapping,
                            onCheckedChange = { v -> vm.updateSettings { it.copy(snapping = v) } })
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Storage", fontWeight = FontWeight.Bold)
                    Text("Cache: ${cache / 1024} KB", fontSize = 11.sp)
                    Button(onClick = { vm.clearCache(); cache = 0L }) { Text("Clear cache") }
                    LabeledSelect("Language", s.language,
                        listOf("system" to "System", "en" to "English", "bn" to "Bengali", "es" to "Spanish")) { v ->
                        vm.updateSettings { it.copy(language = v) }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("About", fontWeight = FontWeight.Bold)
                    Text("Motion Studio 1.0.0 · open source (MIT)", fontSize = 11.sp)
                    Text("Built with Jetpack Compose, Room, Hilt, Media3, OpenGL ES and MediaCodec.",
                        fontSize = 11.sp)
                    Text("Open-source licenses: Apache 2.0 (AndroidX, Media3, Hilt, Room), MIT (kotlinx).",
                        fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Privacy: all projects and media stay on this device. No network upload.",
                        fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TutorialsScreen(onBack: () -> Unit) {
    val steps = listOf(
        "Create a project" to "Tap New project, choose a resolution preset or start from a template.",
        "Add layers" to "In the editor open the Add panel: text, shapes, vectors, solids, images, video or audio.",
        "Arrange the timeline" to "Drag a clip to move it, drag its edges to trim, use Split at the playhead, and pinch/zoom the timeline.",
        "Animate" to "Move the playhead, turn on Auto-key, change a transform value — a keyframe is created. Open the Keys tab for the curve editor.",
        "Add effects" to "Open Effects, add from the catalog, stack them, reorder them and keyframe their parameters.",
        "Masks & text" to "Use the Masks tab for rectangle/circle/path masks and the Text tab for gradients, outlines and shadows.",
        "Export" to "Tap Export, choose resolution, FPS, bitrate and codec. Rendering is real: every frame is composited and encoded."
    )
    Scaffold(topBar = {
        TopAppBar(title = { Text("Tutorials") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
        })
    }) { padding ->
        LazyColumn(Modifier.padding(padding).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(steps) { (title, body) ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(body, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
