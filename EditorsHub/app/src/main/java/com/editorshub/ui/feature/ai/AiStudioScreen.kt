package com.editorshub.ui.feature.ai

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.editorshub.core.ai.*
import com.editorshub.core.data.ProjectRepository
import com.editorshub.core.domain.effects.EffectCatalog
import com.editorshub.core.domain.model.*
import com.editorshub.core.rendering.ThumbnailGenerator
import com.editorshub.core.settings.SettingsRepository
import com.editorshub.ui.components.RenderPreview
import com.editorshub.ui.components.SliderRow
import com.editorshub.ui.feature.effects.EmptyState
import com.editorshub.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import javax.inject.Inject

data class AiUiState(
    val prompt: String = "Cinematic blue neon glow with animated RGB separation, subtle flicker and a smooth pulse",
    val referencePath: String? = null,
    val referenceAnalysis: String? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val connection: String = "Not configured",
    val configured: Boolean = false,
    val graph: EffectGraph? = null,
    val effects: List<Effect> = emptyList(),
    val explanation: String = "",
    val history: List<AiResult> = emptyList(),
    val myEffects: List<EffectGraph> = emptyList()
)

@HiltViewModel
class AiViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val service: AiEffectService,
    private val settings: SettingsRepository,
    private val repository: ProjectRepository,
    private val thumbnails: ThumbnailGenerator
) : ViewModel() {
    private val _ui = MutableStateFlow(AiUiState())
    val ui: StateFlow<AiUiState> = _ui.asStateFlow()
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val undoStack = ArrayDeque<AiResult>()

    init {
        viewModelScope.launch {
            settings.settings.collect { s ->
                _ui.value = _ui.value.copy(configured = s.aiConfigured,
                    connection = if (s.aiConfigured) "Configured · ${s.aiModel}" else "Not configured")
            }
        }
        refreshMyEffects()
    }

    fun setPrompt(v: String) { _ui.value = _ui.value.copy(prompt = v) }

    fun checkConnection() = viewModelScope.launch {
        val r = service.status()
        _ui.value = _ui.value.copy(
            connection = r.getOrNull() ?: "Error: ${r.exceptionOrNull()?.message}",
            error = r.exceptionOrNull()?.message
        )
    }

    fun setApiKey(plain: String, url: String, model: String, enabled: Boolean) = viewModelScope.launch {
        settings.update { it.copy(aiApiKey = AiKeyStore.encrypt(plain), aiBaseUrl = url,
            aiModel = model, aiEnabled = enabled) }
    }

    fun attachReference(uri: Uri) = viewModelScope.launch {
        val dir = File(context.cacheDir, "ai_refs").apply { mkdirs() }
        val file = File(dir, "${UUID.randomUUID()}.img")
        context.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { out -> input.copyTo(out) }
        }
        val analysis = withContext(Dispatchers.Default) { service.analyzeReference(file.absolutePath) }
        _ui.value = _ui.value.copy(referencePath = file.absolutePath,
            referenceAnalysis = analysis ?: "Reference image could not be decoded.")
    }

    fun generate() = viewModelScope.launch {
        _ui.value.let { if (it.graph != null) undoStack.addLast(AiResult(it.graph, it.effects, it.explanation, "")) }
        if (undoStack.size > 20) undoStack.removeFirst()
        _ui.value = _ui.value.copy(loading = true, error = null)
        val r = service.generate(_ui.value.prompt, _ui.value.referencePath)
        r.fold(
            onSuccess = { result ->
                _ui.value = _ui.value.copy(loading = false, graph = result.graph,
                    effects = result.effects, explanation = result.explanation,
                    history = (listOf(result) + _ui.value.history).take(20))
            },
            onFailure = { e ->
                _ui.value = _ui.value.copy(loading = false, error = e.message,
                    graph = null, effects = emptyList(), explanation = "")
            }
        )
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        val prev = undoStack.removeLast()
        _ui.value = _ui.value.copy(graph = prev.graph, effects = prev.effects,
            explanation = prev.explanation, error = null)
    }

    fun updateParam(index: Int, key: String, value: Float) {
        val list = _ui.value.effects.toMutableList()
        if (index !in list.indices) return
        list[index] = list[index].copy(params = list[index].params + (key to value))
        _ui.value = _ui.value.copy(effects = list)
    }

    fun saveEffect() {
        val g = _ui.value.graph ?: return
        val dir = File(context.filesDir, "effects").apply { mkdirs() }
        File(dir, "${UUID.randomUUID()}.json").writeText(json.encodeToString(g))
        refreshMyEffects()
    }

    fun exportEffect(): String? {
        val g = _ui.value.graph ?: return null
        val dir = File(context.cacheDir, "effects").apply { mkdirs() }
        val f = File(dir, "${g.name.replace(Regex("[^A-Za-z0-9_-]"), "_")}.json")
        f.writeText(json.encodeToString(g))
        return f.absolutePath
    }

    fun deleteEffect(name: String) {
        File(context.filesDir, "effects").listFiles()?.forEach { f ->
            if (runCatching { json.decodeFromString<EffectGraph>(f.readText()).name }.getOrNull() == name) f.delete()
        }
        refreshMyEffects()
    }

    fun applyToNewProject(onCreated: (String) -> Unit) = viewModelScope.launch {
        val effects = _ui.value.effects
        if (effects.isEmpty()) return@launch
        val name = _ui.value.graph?.name ?: "AI effect"
        val p = repository.create(ProjectSpec(name = name, width = 1080, height = 1080,
            aspect = "1:1", durationMs = 8000L))
        val layer = Layer(id = UUID.randomUUID().toString(), name = name, type = LayerType.SHAPE,
            startMs = 0, durationMs = 8000L,
            shape = ShapeSpec(kind = ShapeKind.CIRCLE, width = 640f, height = 640f,
                gradient = GradientType.RADIAL, fillColor = "#FF8B7BFF", fillColor2 = "#FF22D3EE"),
            effects = effects,
            keyframes = _ui.value.graph?.let { NodeEvaluator.animationTracks(it, 8000L) } ?: emptyList())
        val updated = p.copy(layers = listOf(layer))
        repository.save(updated)
        val t = withContext(Dispatchers.Default) { thumbnails.generate(updated) }
        if (t != null) repository.save(updated, thumbnailPath = t, autosaveOnly = true)
        onCreated(updated.id)
    }

    private fun refreshMyEffects() {
        _ui.value = _ui.value.copy(myEffects = File(context.filesDir, "effects")
            .listFiles { f -> f.extension == "json" }
            ?.mapNotNull { runCatching { json.decodeFromString<EffectGraph>(it.readText()) }.getOrNull() }
            ?: emptyList())
    }

    fun demoProject(): Project {
        val g = _ui.value.graph
        return Project(
            id = "ai_demo", name = "AI preview", width = 480, height = 480,
            fps = 30, durationMs = 4000L, background = "#FF101228",
            layers = listOf(
                Layer(id = "bg", name = "Background", type = LayerType.SOLID,
                    startMs = 0, durationMs = 4000L),
                Layer(id = "subject", name = "Subject", type = LayerType.SHAPE,
                    startMs = 0, durationMs = 4000L,
                    shape = ShapeSpec(kind = ShapeKind.STAR, width = 300f, height = 300f,
                        gradient = GradientType.RADIAL, fillColor = "#FF8B7BFF", fillColor2 = "#FF22D3EE"),
                    effects = _ui.value.effects,
                    keyframes = g?.let { NodeEvaluator.animationTracks(it, 4000L) } ?: emptyList())
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun AiStudioScreen(
    vm: AiViewModel = hiltViewModel(),
    onOpenProject: (String) -> Unit,
    onSettings: () -> Unit
) {
    val state by vm.ui.collectAsState()
    val previewProject = remember(state.graph, state.effects) { vm.demoProject() }
    val context = LocalContext.current
    var showConfig by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf("https://api.openai.com/v1") }
    var model by remember { mutableStateOf("gpt-4o-mini") }
    var key by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.attachReference(uri)
    }

    Scaffold(
        containerColor = EditorPalette.Black,
        floatingActionButton = {
            if (state.graph != null) {
                ExtendedFloatingActionButton(onClick = { vm.applyToNewProject(onOpenProject) },
                    containerColor = EditorPalette.Purple, contentColor = EditorPalette.Black) {
                    Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text("Apply to project")
                }
            }
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())
                .padding(EditorDimens.Space4),
            verticalArrangement = Arrangement.spacedBy(EditorDimens.Space3)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("AI Studio", style = MaterialTheme.typography.headlineMedium)
                    Text("Text → validated effect graph · real inference only",
                        style = MaterialTheme.typography.labelMedium, color = EditorPalette.TextMuted)
                }
                IconButton(onClick = { showConfig = !showConfig }) { Icon(Icons.Default.VpnKey, "AI settings") }
            }

            AnimatedVisibility(visible = showConfig, enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(EditorDimens.Space3)) {
                        Text("AI service configuration", style = MaterialTheme.typography.titleSmall)
                        OutlinedTextField(value = url, onValueChange = { url = it },
                            label = { Text("Endpoint") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = model, onValueChange = { model = it },
                            label = { Text("Model") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = key, onValueChange = { key = it },
                            label = { Text("API key (encrypted with Android Keystore)") },
                            singleLine = true, modifier = Modifier.fillMaxWidth())
                        Row(horizontalArrangement = Arrangement.spacedBy(EditorDimens.Space2)) {
                            Button(onClick = { vm.setApiKey(key, url, model, true); key = ""; showConfig = false }) {
                                Text("Save")
                            }
                            OutlinedButton(onClick = onSettings) { Text("More AI settings") }
                            OutlinedButton(onClick = { vm.checkConnection() }) { Text("Test") }
                        }
                        Text(state.connection, style = MaterialTheme.typography.labelSmall,
                            color = EditorPalette.TextMuted)
                    }
                }
            }

            if (!state.configured) {
                Card(Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2210))) {
                    Column(Modifier.padding(EditorDimens.Space3)) {
                        Icon(Icons.Default.Warning, null, tint = EditorPalette.Amber)
                        Text("AI service not configured", style = MaterialTheme.typography.titleSmall)
                        Text("Generation is disabled until a real inference endpoint and key are configured. Nothing is simulated.",
                            style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { showConfig = true }) { Text("Configure now") }
                    }
                }
            }

            OutlinedTextField(
                value = state.prompt, onValueChange = { vm.setPrompt(it) },
                label = { Text("Describe the effect") }, minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(EditorDimens.Space2)) {
                OutlinedButton(onClick = { picker.launch(arrayOf("image/*")) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Image, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp))
                    Text("Reference image", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(onClick = { vm.undo() }, enabled = state.history.isNotEmpty()) {
                    Icon(Icons.Default.Undo, null, Modifier.size(16.dp))
                }
                Button(onClick = { vm.generate() }, enabled = state.configured && !state.loading,
                    modifier = Modifier.weight(1f)) {
                    Text(if (state.loading) "Generating…" else if (state.graph == null) "Generate" else "Regenerate")
                }
            }
            state.referenceAnalysis?.let {
                Text("Reference: $it", style = MaterialTheme.typography.labelSmall,
                    color = EditorPalette.Cyan)
            }
            AnimatedVisibility(visible = state.loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            AnimatedVisibility(visible = state.error != null) {
                Card(Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2A1620))) {
                    Text(state.error ?: "", style = MaterialTheme.typography.bodySmall,
                        color = EditorPalette.Red, modifier = Modifier.padding(EditorDimens.Space3))
                }
            }

            AnimatedVisibility(visible = state.graph != null,
                enter = fadeIn() + expandVertically(), exit = fadeOut()) {
                Column(verticalArrangement = Arrangement.spacedBy(EditorDimens.Space3)) {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(EditorDimens.Space3)) {
                            Text(state.graph?.name ?: "", style = MaterialTheme.typography.titleMedium)
                            Text(state.explanation, style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(EditorDimens.Space2))
                            RenderPreview(previewProject, 1000L,
                                Modifier.fillMaxWidth().height(190.dp))
                            Spacer(Modifier.height(EditorDimens.Space2))
                            Row(horizontalArrangement = Arrangement.spacedBy(EditorDimens.Space2)) {
                                Button(onClick = { vm.saveEffect() }, modifier = Modifier.weight(1f)) {
                                    Text("Save effect")
                                }
                                OutlinedButton(onClick = {
                                    vm.exportEffect()?.let { path ->
                                        val uri = FileProvider.getUriForFile(context,
                                            "${context.packageName}.fileprovider", File(path))
                                        val share = Intent(Intent.ACTION_SEND).apply {
                                            type = "application/json"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(share, "Export effect"))
                                    }
                                }) { Text("Export") }
                            }
                        }
                    }
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(EditorDimens.Space3)) {
                            Text("Effect parameters (editable)", style = MaterialTheme.typography.titleSmall)
                            state.effects.forEachIndexed { i, effect ->
                                val spec = EffectCatalog.spec(effect.type)
                                Text(spec.label, style = MaterialTheme.typography.labelMedium,
                                    color = EditorPalette.Purple)
                                spec.params.forEach { p ->
                                    SliderRow(p.label, effect.params[p.key] ?: p.default, p.min..p.max,
                                        { v -> vm.updateParam(i, p.key, v) })
                                }
                            }
                        }
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(EditorDimens.Space3)) {
                    Text("My Effects (${state.myEffects.size})", style = MaterialTheme.typography.titleSmall)
                    if (state.myEffects.isEmpty()) Text("No saved effects.",
                        style = MaterialTheme.typography.bodySmall, color = EditorPalette.TextMuted)
                    state.myEffects.forEach { g ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(g.name, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            Text("${g.nodes.size} nodes", style = MaterialTheme.typography.labelSmall)
                            IconButton(onClick = { vm.deleteEffect(g.name) }) {
                                Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp), tint = EditorPalette.Red)
                            }
                        }
                    }
                    if (state.history.isNotEmpty()) {
                        Spacer(Modifier.height(EditorDimens.Space2))
                        Text("History: ${state.history.size} generation(s)",
                            style = MaterialTheme.typography.labelSmall, color = EditorPalette.TextMuted)
                    }
                }
            }
        }
    }
}
