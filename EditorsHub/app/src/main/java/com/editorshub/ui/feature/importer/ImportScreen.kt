package com.editorshub.ui.feature.importer

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.editorshub.core.importer.*
import com.editorshub.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ImportUiState(
    val running: Boolean = false,
    val percent: Int = 0,
    val label: String = "",
    val result: ImportResult? = null,
    val fileName: String = ""
)

@HiltViewModel
class ImportViewModel @Inject constructor(
    private val importer: ProjectImporter
) : ViewModel() {
    private val _ui = MutableStateFlow(ImportUiState())
    val ui: StateFlow<ImportUiState> = _ui.asStateFlow()
    private var job: Job? = null

    fun start(uri: Uri, name: String) {
        job?.cancel()
        _ui.value = ImportUiState(running = true, label = "Starting…", fileName = name)
        job = viewModelScope.launch {
            val r = runCatching {
                importer.import(uri, { p, l ->
                    _ui.value = _ui.value.copy(percent = p, label = l)
                }, { false })
            }.getOrElse { e ->
                ImportResult.Failure("Import failed: ${e.message ?: "unknown error"}", null)
            }
            _ui.value = _ui.value.copy(running = false, result = r, percent = 100)
        }
    }

    fun cancel() {
        job?.cancel()
        _ui.value = _ui.value.copy(running = false, result = ImportResult.Cancelled)
    }

    fun clear() { _ui.value = ImportUiState() }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun ImportScreen(
    vm: ImportViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onOpenProject: (String) -> Unit
) {
    val state by vm.ui.collectAsState()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.start(uri, uri.lastPathSegment ?: "project")
    }

    Scaffold(
        containerColor = EditorPalette.Black,
        topBar = {
            TopAppBar(
                title = { Text("Import project") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = EditorPalette.Charcoal)
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())
                .padding(EditorDimens.Space4),
            verticalArrangement = Arrangement.spacedBy(EditorDimens.Space3)
        ) {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = EditorPalette.Surface)
            ) {
                Column(Modifier.padding(EditorDimens.Space4)) {
                    Text("Universal project import", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(EditorDimens.Space2))
                    Text("Streams large archives without size limits. Originals are preserved and every conversion produces an honest compatibility report.",
                        style = MaterialTheme.typography.bodySmall, color = EditorPalette.TextSecondary)
                    Spacer(Modifier.height(EditorDimens.Space3))
                    Button(
                        onClick = { picker.launch(arrayOf("*/*")) },
                        enabled = !state.running,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Upload, null, Modifier.size(EditorDimens.IconSmall))
                        Spacer(Modifier.width(EditorDimens.Space2))
                        Text("Choose project file")
                    }
                    Text("Supports .json, .xml (Alight Motion), .zip / .amproj (Node Video), .aepx (After Effects XML). Binary .aep is preserved as a recoverable reference.",
                        style = MaterialTheme.typography.labelSmall, color = EditorPalette.TextMuted,
                        modifier = Modifier.padding(top = EditorDimens.Space2))
                }
            }

            AnimatedVisibility(
                visible = state.running,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Card(Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = EditorPalette.SurfaceRaised)) {
                    Column(Modifier.padding(EditorDimens.Space4)) {
                        Text("Importing ${state.fileName}", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(EditorDimens.Space2))
                        LinearProgressIndicator(progress = { state.percent / 100f },
                            modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(EditorDimens.Space1))
                        Text("${state.percent}% · ${state.label}", style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.height(EditorDimens.Space2))
                        OutlinedButton(onClick = { vm.cancel() }, modifier = Modifier.fillMaxWidth()) {
                            Text("Cancel")
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = state.result != null,
                enter = fadeIn() + slideInVertically { it / 6 },
                exit = fadeOut()
            ) {
                state.result?.let { ResultCard(it, onOpenProject) { vm.clear() } }
            }
        }
    }
}

@Composable
private fun ResultCard(result: ImportResult, onOpenProject: (String) -> Unit, onDismiss: () -> Unit) {
    when (result) {
        is ImportResult.Success -> ReportCard(result.report, onOpenProject, onDismiss)
        is ImportResult.ReportOnly -> ReportCard(result.report, onOpenProject, onDismiss)
        is ImportResult.Failure -> Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2A1620))
        ) {
            Column(Modifier.padding(EditorDimens.Space4)) {
                Text("Import failed", style = MaterialTheme.typography.titleMedium, color = EditorPalette.Red)
                Text(result.message, style = MaterialTheme.typography.bodySmall)
                result.report?.let { ReportBody(it) }
                TextButton(onClick = onDismiss) { Text("Dismiss") }
            }
        }
        ImportResult.Cancelled -> Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(EditorDimens.Space4)) {
                Text("Import cancelled", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onDismiss) { Text("Dismiss") }
            }
        }
    }
}

@Composable
private fun ReportCard(report: ImportReport, onOpenProject: (String) -> Unit, onDismiss: () -> Unit) {
    Card(Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = EditorPalette.Surface)) {
        Column(Modifier.padding(EditorDimens.Space4)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (report.status == ImportStatus.IMPORTED) Icons.Default.CheckCircle
                    else Icons.Default.Warning,
                    null, tint = if (report.status == ImportStatus.IMPORTED) EditorPalette.Green else EditorPalette.Amber
                )
                Spacer(Modifier.width(EditorDimens.Space2))
                Text(report.headline, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(EditorDimens.Space2))
            ReportBody(report)
            Spacer(Modifier.height(EditorDimens.Space3))
            Row(horizontalArrangement = Arrangement.spacedBy(EditorDimens.Space2)) {
                report.projectId?.let { id ->
                    Button(onClick = { onOpenProject(id) }, modifier = Modifier.weight(1f)) {
                        Text("Open project")
                    }
                }
                OutlinedButton(onClick = onDismiss) { Text("Close") }
            }
        }
    }
}

@Composable
private fun ReportBody(report: ImportReport) {
    val fmt = { b: Long -> "%.1f MB".format(b / 1024.0 / 1024.0) }
    Text("${report.fileName} · ${fmt(report.bytes)} · ${report.kind}",
        style = MaterialTheme.typography.labelSmall, color = EditorPalette.TextMuted)
    Spacer(Modifier.height(EditorDimens.Space2))
    val stats = listOf(
        "Layers" to report.layersImported, "Keyframes" to report.keyframesImported,
        "Effects" to report.effectsImported, "Masks" to report.masksImported,
        "Assets" to report.assetsFound, "Relinked" to report.assetsRelinked,
        "Duplicates" to report.assetsDuplicated
    )
    Row(horizontalArrangement = Arrangement.spacedBy(EditorDimens.Space1),
        modifier = Modifier.fillMaxWidth()) {
        stats.chunked(4).forEach { group ->
            Column(Modifier.weight(1f)) {
                group.forEach { (k, v) ->
                    Text("$k: $v", fontSize = 10.sp, color = EditorPalette.TextSecondary)
                }
            }
        }
    }
    ReportList("Missing media", report.missingMedia, EditorPalette.Red)
    ReportList("Unsupported / needs replacement", report.unsupported, EditorPalette.Amber)
    ReportList("Notes", report.warnings, EditorPalette.TextMuted)
    report.logPath?.let { Text("Import log: $it", fontSize = 9.sp, color = EditorPalette.TextMuted) }
}

@Composable
private fun ReportList(title: String, items: List<String>, color: Color) {
    if (items.isEmpty()) return
    Spacer(Modifier.height(EditorDimens.Space2))
    Text(title, style = MaterialTheme.typography.labelMedium, color = color)
    items.take(12).forEach { Text("• $it", fontSize = 10.sp, color = EditorPalette.TextSecondary) }
    if (items.size > 12) Text("… and ${items.size - 12} more", fontSize = 9.sp, color = EditorPalette.TextMuted)
}
