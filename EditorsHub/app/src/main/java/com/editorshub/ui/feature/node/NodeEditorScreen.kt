package com.editorshub.ui.feature.node

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.editorshub.core.ai.*
import com.editorshub.core.data.ProjectRepository
import com.editorshub.core.domain.effects.EffectCatalog
import com.editorshub.core.domain.model.*
import com.editorshub.core.rendering.ThumbnailGenerator
import com.editorshub.ui.components.RenderPreview
import com.editorshub.ui.components.SliderRow
import com.editorshub.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import javax.inject.Inject

data class NodeUiState(
    val graph: EffectGraph = NodeEvaluator.defaultGraph(),
    val selected: String? = null,
    val connectFrom: String? = null,
    val validation: GraphValidation = NodeEvaluator.validate(NodeEvaluator.defaultGraph()),
    val saved: List<EffectGraph> = emptyList(),
    val message: String? = null
)

@HiltViewModel
class NodeEditorViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: ProjectRepository,
    private val thumbnails: ThumbnailGenerator
) : ViewModel() {
    private val _ui = MutableStateFlow(NodeUiState())
    val ui: StateFlow<NodeUiState> = _ui.asStateFlow()
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    init { refresh() }

    private fun update(graph: EffectGraph, message: String? = null) {
        _ui.value = _ui.value.copy(graph = graph, validation = NodeEvaluator.validate(graph), message = message)
    }

    fun addNode(type: String) {
        val g = _ui.value.graph
        val n = EffectNode(type = type, label = type.lowercase().replaceFirstChar { it.uppercase() },
            x = 80f + (g.nodes.size % 4) * 190f, y = 340f + (g.nodes.size / 4) * 120f)
        update(g.copy(nodes = g.nodes + n), "$type node added")
    }

    fun deleteNode(id: String) {
        val g = _ui.value.graph
        update(g.copy(nodes = g.nodes.filterNot { it.id == id },
            edges = g.edges.filterNot { it.from == id || it.to == id },
            outputId = if (g.outputId == id) "" else g.outputId))
    }

    fun moveNode(id: String, dxDp: Float, dyDp: Float) {
        val g = _ui.value.graph
        update(g.copy(nodes = g.nodes.map {
            if (it.id == id) it.copy(x = (it.x + dxDp).coerceIn(0f, 900f), y = (it.y + dyDp).coerceIn(0f, 900f)) else it
        }))
    }

    fun select(id: String?) { _ui.value = _ui.value.copy(selected = id) }

    fun tapPort(id: String) {
        val from = _ui.value.connectFrom
        if (from == null) { _ui.value = _ui.value.copy(connectFrom = id); return }
        if (from == id) { _ui.value = _ui.value.copy(connectFrom = null); return }
        connect(from, id)
        _ui.value = _ui.value.copy(connectFrom = null)
    }

    fun connect(from: String, to: String) {
        val g = _ui.value.graph
        if (g.edges.any { it.from == from && it.to == to }) return
        update(g.copy(edges = g.edges + EffectEdge(from, to)), "Connected")
    }

    fun disconnect(edge: EffectEdge) {
        val g = _ui.value.graph
        update(g.copy(edges = g.edges.filterNot { it == edge }), "Disconnected")
    }

    fun setParam(id: String, key: String, value: Float) {
        val g = _ui.value.graph
        update(g.copy(nodes = g.nodes.map { n ->
            if (n.id == id) n.copy(params = n.params + (key to value)) else n
        }))
    }

    fun reset() = update(NodeEvaluator.defaultGraph(), "Graph reset")

    fun duplicate() {
        val original = _ui.value.graph
        val newNodes = original.nodes.map { it.copy(id = UUID.randomUUID().toString(), x = it.x + 30f, y = it.y + 30f) }
        val map = original.nodes.zip(newNodes).associate { (a, b) -> a.id to b.id }
        update(original.copy(name = "${original.name} copy", nodes = newNodes,
            edges = original.edges.map { EffectEdge(map[it.from] ?: it.from, map[it.to] ?: it.to) },
            outputId = map[original.outputId] ?: ""), "Graph duplicated")
    }

    fun save() {
        val g = _ui.value.graph
        if (!_ui.value.validation.ok) { _ui.value = _ui.value.copy(message = "Fix validation errors first"); return }
        val dir = File(context.filesDir, "effects").apply { mkdirs() }
        File(dir, "${UUID.randomUUID()}.json").writeText(json.encodeToString(g))
        refresh()
        _ui.value = _ui.value.copy(message = "Graph saved to My Effects")
    }

    fun export(): String? {
        val g = _ui.value.graph
        val dir = File(context.cacheDir, "effects").apply { mkdirs() }
        val f = File(dir, "${g.name.replace(Regex("[^A-Za-z0-9_-]"), "_")}.json")
        f.writeText(json.encodeToString(g))
        return f.absolutePath
    }

    fun load(uri: Uri) = viewModelScope.launch {
        val text = context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
        val g = text?.let { runCatching { json.decodeFromString<EffectGraph>(it) }.getOrNull() }
        if (g == null) _ui.value = _ui.value.copy(message = "Invalid effect graph file")
        else update(g, "Graph imported")
    }

    fun load(graph: EffectGraph) = update(graph, "Graph loaded")

    fun applyToNewProject(onCreated: (String) -> Unit) = viewModelScope.launch {
        val effects = NodeEvaluator.toEffects(_ui.value.graph)
        if (effects.isEmpty()) { _ui.value = _ui.value.copy(message = "No renderable effects in this graph"); return@launch }
        val p = repository.create(ProjectSpec(name = _ui.value.graph.name, width = 1080,
            height = 1080, aspect = "1:1", durationMs = 8000L))
        val layer = Layer(id = UUID.randomUUID().toString(), name = _ui.value.graph.name,
            type = LayerType.SHAPE, startMs = 0, durationMs = 8000L,
            shape = ShapeSpec(kind = ShapeKind.STAR, width = 640f, height = 640f,
                gradient = GradientType.RADIAL, fillColor = "#FF8B7BFF", fillColor2 = "#FF22D3EE"),
            effects = effects,
            keyframes = NodeEvaluator.animationTracks(_ui.value.graph, 8000L))
        val updated = p.copy(layers = listOf(layer))
        repository.save(updated)
        val t = withContext(Dispatchers.Default) { thumbnails.generate(updated) }
        if (t != null) repository.save(updated, thumbnailPath = t, autosaveOnly = true)
        onCreated(updated.id)
    }

    private fun refresh() {
        _ui.value = _ui.value.copy(saved = File(context.filesDir, "effects")
            .listFiles { f -> f.extension == "json" }
            ?.mapNotNull { runCatching { json.decodeFromString<EffectGraph>(it.readText()) }.getOrNull() }
            ?: emptyList())
    }

    fun demoProject() = Project(
        id = "node_demo", name = _ui.value.graph.name, width = 480, height = 480,
        fps = 30, durationMs = 4000L, background = "#FF101228",
        layers = listOf(
            Layer(id = "bg", name = "Background", type = LayerType.SOLID,
                startMs = 0, durationMs = 4000L),
            Layer(id = "subject", name = "Subject", type = LayerType.SHAPE,
                startMs = 0, durationMs = 4000L,
                shape = ShapeSpec(kind = ShapeKind.CIRCLE, width = 300f, height = 300f,
                    gradient = GradientType.RADIAL, fillColor = "#FFF472B6", fillColor2 = "#FF22D3EE"),
                effects = NodeEvaluator.toEffects(_ui.value.graph),
                keyframes = NodeEvaluator.animationTracks(_ui.value.graph, 4000L))
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodeEditorScreen(
    vm: NodeEditorViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onOpenProject: (String) -> Unit
) {
    val state by vm.ui.collectAsState()
    val context = LocalContext.current
    val density = LocalDensity.current.density
    var showAdd by remember { mutableStateOf(false) }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.load(uri)
    }

    Scaffold(
        containerColor = EditorPalette.Black,
        topBar = {
            TopAppBar(
                title = { Text("Custom effect builder") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { showAdd = true }) { Icon(Icons.Default.Add, "Add node") }
                    IconButton(onClick = { vm.reset() }) { Icon(Icons.Default.RestartAlt, "Reset") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = EditorPalette.Charcoal)
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(Modifier.padding(horizontal = EditorDimens.Space3, vertical = EditorDimens.Space1),
                horizontalArrangement = Arrangement.spacedBy(EditorDimens.Space1)) {
                Button(onClick = { vm.save() }) { Text("Save", style = MaterialTheme.typography.labelSmall) }
                OutlinedButton(onClick = { vm.duplicate() }) { Text("Copy", style = MaterialTheme.typography.labelSmall) }
                OutlinedButton(onClick = { importPicker.launch(arrayOf("application/json", "*/*")) }) {
                    Text("Import", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(onClick = {
                    vm.export()?.let { path ->
                        val uri = FileProvider.getUriForFile(context,
                            "${context.packageName}.fileprovider", File(path))
                        val share = Intent(Intent.ACTION_SEND).apply {
                            type = "application/json"; putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(share, "Export graph"))
                    }
                }) { Text("Export", style = MaterialTheme.typography.labelSmall) }
            }

            if (showAdd) {
                Row(Modifier.padding(horizontal = EditorDimens.Space3),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    NodeTypes.ALL.chunked(5).forEach { group ->
                        Column(Modifier.weight(1f)) {
                            group.forEach { t ->
                                OutlinedButton(onClick = { vm.addNode(t); showAdd = false },
                                    modifier = Modifier.fillMaxWidth(),
                                    contentPadding = PaddingValues(2.dp)) {
                                    Text(t, fontSize = 8.sp)
                                }
                            }
                        }
                    }
                }
            }

            Box(
                Modifier.weight(1f).fillMaxWidth().background(EditorPalette.Charcoal)
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    state.graph.edges.forEach { e ->
                        val a = state.graph.nodes.firstOrNull { it.id == e.from }
                        val b = state.graph.nodes.firstOrNull { it.id == e.to }
                        if (a != null && b != null) {
                            val s = Offset((a.x + 150f) * density, (a.y + 30f) * density)
                            val t = Offset((b.x) * density, (b.y + 30f) * density)
                            drawLine(EditorPalette.Purple, s, t, strokeWidth = 3f * density)
                            drawCircle(EditorPalette.Cyan, radius = 4f * density, center = t)
                        }
                    }
                }
                state.graph.nodes.forEach { node ->
                    NodeCard(node, state, density, vm, Modifier)
                }
                if (state.connectFrom != null) {
                    Text("Tap a target node's input port to connect",
                        style = MaterialTheme.typography.labelSmall, color = EditorPalette.Amber,
                        modifier = Modifier.align(Alignment.TopCenter).padding(8.dp))
                }
            }

            Column(Modifier.height(230.dp).verticalScroll(rememberScrollState())
                .padding(EditorDimens.Space3)) {
                RenderPreview(vm.demoProject(), 1000L, Modifier.fillMaxWidth().height(110.dp))
                Spacer(Modifier.height(EditorDimens.Space2))
                if (!state.validation.ok) {
                    state.validation.errors.forEach {
                        Text("• $it", style = MaterialTheme.typography.labelSmall, color = EditorPalette.Red)
                    }
                } else {
                    Text("Valid DAG · ${NodeEvaluator.toEffects(state.graph).size} renderable effects",
                        style = MaterialTheme.typography.labelSmall, color = EditorPalette.Green)
                }
                state.message?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = EditorPalette.Cyan) }
                val selected = state.graph.nodes.firstOrNull { it.id == state.selected }
                if (selected != null) {
                    Text("${selected.label} (${selected.type})", style = MaterialTheme.typography.titleSmall)
                    val type = NodeEvaluator.mapNodeToEffect(selected.type)
                    if (type != null) {
                        EffectCatalog.spec(type).params.forEach { p ->
                            SliderRow(p.label, selected.params[p.key] ?: p.default, p.min..p.max,
                                { v -> vm.setParam(selected.id, p.key, v) })
                        }
                    } else {
                        Text("Structural node — no parameters.", style = MaterialTheme.typography.labelSmall)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Edges: ${state.graph.edges.size}", style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.width(EditorDimens.Space2))
                    state.graph.edges.take(6).forEach { e ->
                        IconButton(onClick = { vm.disconnect(e) }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.LinkOff, "Disconnect", Modifier.size(12.dp))
                        }
                    }
                }
                Button(onClick = { vm.applyToNewProject(onOpenProject) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Apply graph to a new project")
                }
            }
        }
    }
}

@Composable
private fun NodeCard(
    node: EffectNode, state: NodeUiState, density: Float,
    vm: NodeEditorViewModel, modifier: Modifier
) {
    val selected = node.id == state.selected
    Card(
        modifier
            .offset(x = node.x.dp, y = node.y.dp)
            .width(150.dp)
            .pointerInput(node.id) {
                detectDragGestures(onDragEnd = { }) { _, drag ->
                    vm.moveNode(node.id, drag.x / density, drag.y / density)
                }
            }
            .border(1.dp, if (selected) EditorPalette.Purple else EditorPalette.Outline,
                RoundedCornerShape(10.dp)),
        colors = CardDefaults.cardColors(containerColor = EditorPalette.SurfaceRaised)
    ) {
        Row(Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).background(EditorPalette.Cyan, CircleShape)
                .pointerInput(node.id) {
                    detectTapGestures(onTap = { vm.tapPort(node.id) })
                })
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) {
                Text(node.label, fontSize = 10.sp, maxLines = 1)
                Text(node.type, fontSize = 8.sp, color = EditorPalette.TextMuted)
            }
            IconButton(onClick = { vm.select(node.id) }, modifier = Modifier.size(22.dp)) {
                Icon(Icons.Default.Tune, "Edit", Modifier.size(12.dp))
            }
            IconButton(onClick = { vm.deleteNode(node.id) }, modifier = Modifier.size(22.dp)) {
                Icon(Icons.Default.Close, "Delete", Modifier.size(12.dp), tint = EditorPalette.Red)
            }
        }
    }
}
