package com.editorshub.ui.feature.effects

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.editorshub.core.ai.EffectGraph
import com.editorshub.core.ai.NodeEvaluator
import com.editorshub.core.data.ProjectRepository
import com.editorshub.core.domain.effects.EffectCatalog
import com.editorshub.core.domain.model.*
import com.editorshub.core.rendering.ThumbnailGenerator
import com.editorshub.ui.components.RenderPreview
import com.editorshub.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@HiltViewModel
class EffectsLibraryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: ProjectRepository,
    private val thumbnails: ThumbnailGenerator
) : ViewModel() {
    private val _created = MutableSharedFlow<String>()
    val created = _created.asSharedFlow()
    private val _presets = MutableStateFlow<List<EffectGraph>>(emptyList())
    val presets: StateFlow<List<EffectGraph>> = _presets.asStateFlow()

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    init { refreshPresets() }

    fun demo(type: EffectType): Project {
        val effect = Effect(id = UUID.randomUUID().toString(), type = type,
            params = EffectCatalog.defaults(type))
        return Project(
            id = "demo_$type", name = EffectCatalog.spec(type).label,
            width = 480, height = 480, fps = 30, durationMs = 2000L,
            background = "#FF12142A",
            layers = listOf(
                Layer(id = "bg", name = "Background", type = LayerType.SOLID,
                    startMs = 0, durationMs = 2000L),
                Layer(id = "shape", name = "Preview shape", type = LayerType.SHAPE,
                    startMs = 0, durationMs = 2000L,
                    shape = ShapeSpec(kind = ShapeKind.STAR, width = 260f, height = 260f,
                        gradient = GradientType.RADIAL, fillColor = "#FFFFFFFF", fillColor2 = "#FF22D3EE"),
                    effects = listOf(effect)),
                Layer(id = "label", name = "Label", type = LayerType.TEXT,
                    startMs = 0, durationMs = 2000L,
                    text = TextSpec(text = EffectCatalog.spec(type).label, fontSize = 42f),
                    effects = listOf(effect.copy(id = UUID.randomUUID().toString())))
            )
        )
    }

    fun createWithEffect(type: EffectType) = viewModelScope.launch {
        val p = repository.create(ProjectSpec(name = EffectCatalog.spec(type).label,
            width = 1080, height = 1080, aspect = "1:1", durationMs = 8000L))
        val demo = demo(type)
        val withLayers = p.copy(layers = demo.layers.map { it.copy(id = UUID.randomUUID().toString()) })
        repository.save(withLayers)
        val t = withContext(Dispatchers.Default) { thumbnails.generate(withLayers) }
        if (t != null) repository.save(withLayers, thumbnailPath = t, autosaveOnly = true)
        _created.emit(withLayers.id)
    }

    fun createFromPreset(graph: EffectGraph) = viewModelScope.launch {
        val effects = NodeEvaluator.toEffects(graph)
        if (effects.isEmpty()) return@launch
        val p = repository.create(ProjectSpec(name = graph.name, width = 1080, height = 1080,
            aspect = "1:1", durationMs = 8000L))
        val layer = Layer(id = UUID.randomUUID().toString(), name = graph.name,
            type = LayerType.SHAPE, startMs = 0, durationMs = 8000L,
            shape = ShapeSpec(kind = ShapeKind.CIRCLE, width = 600f, height = 600f,
                gradient = GradientType.RADIAL),
            effects = effects,
            keyframes = NodeEvaluator.animationTracks(graph, 8000L))
        val updated = p.copy(layers = listOf(layer))
        repository.save(updated)
        val t = withContext(Dispatchers.Default) { thumbnails.generate(updated) }
        if (t != null) repository.save(updated, thumbnailPath = t, autosaveOnly = true)
        _created.emit(updated.id)
    }

    fun refreshPresets() {
        _presets.value = effectsDir().listFiles { f -> f.extension == "json" }
            ?.mapNotNull { f -> runCatching { json.decodeFromString<EffectGraph>(f.readText()) }.getOrNull() }
            ?: emptyList()
    }

    fun deletePreset(name: String) {
        effectsDir().listFiles { f -> f.extension == "json" }?.forEach { f ->
            val g = runCatching { json.decodeFromString<EffectGraph>(f.readText()) }.getOrNull()
            if (g?.name == name) f.delete()
        }
        refreshPresets()
    }

    fun exportPreset(graph: EffectGraph): String {
        val dir = File(context.cacheDir, "effects").apply { mkdirs() }
        val f = File(dir, "${graph.name.replace(Regex("[^A-Za-z0-9_-]"), "_")}.json")
        f.writeText(json.encodeToString(graph))
        return f.absolutePath
    }

    private fun effectsDir() = File(context.filesDir, "effects").apply { mkdirs() }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun EffectsLibraryScreen(
    vm: EffectsLibraryViewModel = hiltViewModel(),
    onOpenProject: (String) -> Unit,
    onNodeEditor: () -> Unit
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var showPresets by remember { mutableStateOf(false) }
    val presets by vm.presets.collectAsState()
    val specs = remember(query) {
        EffectCatalog.specs.filter { s ->
            query.isBlank() || s.label.contains(query, true) || s.group.contains(query, true)
        }
    }
    LaunchedEffect(Unit) { vm.created.collect { onOpenProject(it) } }
    LaunchedEffect(showPresets) { if (showPresets) vm.refreshPresets() }

    Scaffold(
        containerColor = EditorPalette.Black,
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onNodeEditor,
                containerColor = EditorPalette.Purple, contentColor = EditorPalette.Black) {
                Icon(Icons.Default.DeviceHub, null, Modifier.size(EditorDimens.IconSmall))
                Spacer(Modifier.width(EditorDimens.Space1))
                Text("Node editor", style = MaterialTheme.typography.labelMedium)
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                placeholder = { Text("Search effects", style = MaterialTheme.typography.bodySmall) },
                leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(EditorDimens.IconSmall)) },
                modifier = Modifier.fillMaxWidth().padding(EditorDimens.Space3),
                singleLine = true
            )
            Row(Modifier.padding(horizontal = EditorDimens.Space3)) {
                FilterChip(selected = !showPresets, onClick = { showPresets = false },
                    label = { Text("Catalog (${EffectCatalog.specs.size})") })
                Spacer(Modifier.width(EditorDimens.Space2))
                FilterChip(selected = showPresets, onClick = { showPresets = true },
                    label = { Text("My Presets (${presets.size})") })
            }
            AnimatedContent(
                targetState = showPresets,
                transitionSpec = { fadeIn() + slideInHorizontally { if (targetState) it else -it } togetherWith
                    fadeOut() + slideOutHorizontally { if (targetState) -it else it } },
                label = "effects-tab"
            ) { presetsTab ->
                if (presetsTab) {
                    if (presets.isEmpty()) {
                        EmptyState("No saved effects yet", "Generate one in AI Studio or build one in the Node Editor.")
                    } else {
                        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(EditorDimens.Space3),
                            verticalArrangement = Arrangement.spacedBy(EditorDimens.Space2)) {
                            items(presets, key = { it.name }) { g ->
                                Card(Modifier.fillMaxWidth()) {
                                    Row(Modifier.padding(EditorDimens.Space3),
                                        verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(g.name, style = MaterialTheme.typography.titleSmall)
                                            Text("${g.nodes.size} nodes · ${NodeEvaluator.toEffects(g).size} renderable effects",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = EditorPalette.TextMuted)
                                        }
                                        IconButton(onClick = {
                                            val path = vm.exportPreset(g)
                                            val uri = androidx.core.content.FileProvider.getUriForFile(
                                                context, "${context.packageName}.fileprovider", File(path))
                                            val share = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                                type = "application/json"; putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(android.content.Intent.createChooser(share, "Export effect"))
                                        }) { Icon(Icons.Default.Share, "Export") }
                                        IconButton(onClick = { vm.deletePreset(g.name); vm.refreshPresets() }) {
                                            Icon(Icons.Default.Delete, "Delete", tint = EditorPalette.Red)
                                        }
                                        Button(onClick = { vm.createFromPreset(g) }) { Text("Apply") }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(EditorDimens.Space3),
                        verticalArrangement = Arrangement.spacedBy(EditorDimens.Space3)) {
                        EffectCatalog.groups().forEach { group ->
                            val inGroup = specs.filter { it.group == group }
                            if (inGroup.isEmpty()) return@forEach
                            item {
                                Text(group, style = MaterialTheme.typography.titleSmall,
                                    color = EditorPalette.Purple,
                                    modifier = Modifier.padding(vertical = EditorDimens.Space1))
                            }
                            items(inGroup.chunked(2)) { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(EditorDimens.Space2),
                                    modifier = Modifier.fillMaxWidth()) {
                                    row.forEach { spec ->
                                        EffectCard(spec, vm, Modifier.weight(1f))
                                    }
                                    if (row.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EffectCard(spec: com.editorshub.core.domain.effects.EffectSpec,
                       vm: EffectsLibraryViewModel, modifier: Modifier) {
    val project = remember(spec.type) { vm.demo(spec.type) }
    Card(modifier, colors = CardDefaults.cardColors(containerColor = EditorPalette.Surface)) {
        RenderPreview(project, 900L, Modifier.fillMaxWidth().height(110.dp))
        Column(Modifier.padding(EditorDimens.Space2)) {
            Text(spec.label, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            Text("${spec.params.size} parameters · real render",
                style = MaterialTheme.typography.labelSmall, color = EditorPalette.TextMuted)
            Spacer(Modifier.height(EditorDimens.Space1))
            Button(onClick = { vm.createWithEffect(spec.type) }, modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(EditorDimens.Space1)) {
                Text("Use in project", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
fun EmptyState(title: String, body: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.background(EditorPalette.Surface, androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                .padding(EditorDimens.Space5)) {
            Icon(Icons.Default.Inbox, null, Modifier.size(40.dp), tint = EditorPalette.TextMuted)
            Spacer(Modifier.height(EditorDimens.Space2))
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodySmall, color = EditorPalette.TextMuted)
        }
    }
}
