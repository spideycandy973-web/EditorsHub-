package com.editorshub.ui.feature.home

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.editorshub.BuildConfig
import com.editorshub.core.domain.model.ExportResolution
import com.editorshub.core.domain.model.VideoCodec
import com.editorshub.ui.components.LabeledSelect
import com.editorshub.ui.components.SliderRow
import com.editorshub.ui.theme.*

private val SECTIONS = listOf("General", "Performance", "Export", "Storage", "AI", "About")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun SettingsScreen(
    vm: HomeViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onTutorials: () -> Unit,
    onAi: () -> Unit,
    onNodeEditor: () -> Unit
) {
    val s by vm.appSettings.collectAsState()
    var section by remember { mutableStateOf("General") }
    var apiKey by remember { mutableStateOf("") }
    var cache by remember { mutableStateOf(vm.cacheSize()) }

    Scaffold(containerColor = EditorPalette.Black, topBar = {
        TopAppBar(title = { Text("Settings") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = EditorPalette.Charcoal))
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            ScrollableTabRow(selectedTabIndex = SECTIONS.indexOf(section),
                containerColor = EditorPalette.Charcoal, edgePadding = 8.dp) {
                SECTIONS.forEach { name ->
                    Tab(selected = section == name, onClick = { section = name },
                        text = { Text(name, style = MaterialTheme.typography.labelSmall) })
                }
            }
            AnimatedContent(targetState = section, transitionSpec = {
                fadeIn() + slideInHorizontally { if (SECTIONS.indexOf(targetState) > SECTIONS.indexOf(initialState)) it / 8 else -it / 8 } togetherWith
                    fadeOut() + slideOutHorizontally { if (SECTIONS.indexOf(targetState) > SECTIONS.indexOf(initialState)) -it / 8 else it / 8 }
            }, label = "settings-section") { sec ->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(EditorDimens.Space3),
                    verticalArrangement = Arrangement.spacedBy(EditorDimens.Space2)) {
                    when (sec) {
                        "General" -> {
                            SettingsCard("Appearance") {
                                SwitchRow("Dark theme", s.darkTheme) { vm.updateSettings { it.copy(darkTheme = !it.darkTheme) } }
                                SwitchRow("Dynamic colour (Android 12+)", s.dynamicColor) {
                                    vm.updateSettings { it.copy(dynamicColor = !it.dynamicColor) }
                                }
                                SliderRow("UI scale", s.uiScale, 0.8f..1.4f, {
                                    vm.updateSettings { st -> st.copy(uiScale = it) }
                                })
                            }
                            SettingsCard("Editable in-app identity") {
                                OutlinedTextField(value = s.brandName,
                                    onValueChange = { value -> vm.updateSettings { it.copy(brandName = value.take(40)) } },
                                    label = { Text("Brand name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                                OutlinedTextField(value = s.brandSubtitle,
                                    onValueChange = { value -> vm.updateSettings { it.copy(brandSubtitle = value.take(60)) } },
                                    label = { Text("Subtitle / website") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                                Text("This only changes the local wordmark. The Android package remains com.editorshub.",
                                    style = MaterialTheme.typography.labelSmall, color = EditorPalette.TextMuted)
                            }
                            SettingsCard("Language & files") {
                                LabeledSelect("Language", s.language,
                                    listOf("system" to "System", "en" to "English", "bn" to "Bengali",
                                        "es" to "Spanish", "hi" to "Hindi")) {
                                    vm.updateSettings { st -> st.copy(language = it) }
                                }
                                LabeledSelect("Default project location", s.defaultProjectLocation,
                                    listOf("internal" to "Private app storage", "external" to "App external storage")) {
                                    vm.updateSettings { st -> st.copy(defaultProjectLocation = it) }
                                }
                                SliderRow("Autosave interval (ms)", s.autosaveIntervalMs.toFloat(), 200f..5000f, {
                                    vm.updateSettings { st -> st.copy(autosaveIntervalMs = it.toLong()) }
                                })
                                SwitchRow("Confirm before deleting", s.confirmBeforeDelete) {
                                    vm.updateSettings { st -> st.copy(confirmBeforeDelete = !st.confirmBeforeDelete) }
                                }
                            }
                        }
                        "Performance" -> {
                            SettingsCard("Preview & rendering") {
                                SliderRow("Preview quality", s.previewQuality, 0.2f..1f, {
                                    vm.updateSettings { st -> st.copy(previewQuality = it) }
                                })
                                SwitchRow("Hardware acceleration (GPU shader preview)", s.hardwareAcceleration) {
                                    vm.updateSettings { st -> st.copy(hardwareAcceleration = !st.hardwareAcceleration) }
                                }
                                SwitchRow("Background rendering", s.backgroundRendering) {
                                    vm.updateSettings { st -> st.copy(backgroundRendering = !st.backgroundRendering) }
                                }
                                SwitchRow("Proxy media", s.proxyEnabled) {
                                    vm.updateSettings { st -> st.copy(proxyEnabled = !st.proxyEnabled) }
                                }
                                SliderRow("Proxy scale", s.proxyScale, 0.25f..1f, {
                                    vm.updateSettings { st -> st.copy(proxyScale = it) }
                                })
                            }
                            SettingsCard("Guides") {
                                SwitchRow("Transparency checkerboard", s.checkerboard) {
                                    vm.updateSettings { st -> st.copy(checkerboard = !st.checkerboard) }
                                }
                                SwitchRow("Safe area guides", s.showSafeArea) {
                                    vm.updateSettings { st -> st.copy(showSafeArea = !st.showSafeArea) }
                                }
                                SwitchRow("Grid guides", s.showGrid) {
                                    vm.updateSettings { st -> st.copy(showGrid = !st.showGrid) }
                                }
                                SwitchRow("Center guides", s.showCenterGuides) {
                                    vm.updateSettings { st -> st.copy(showCenterGuides = !st.showCenterGuides) }
                                }
                                SwitchRow("Aspect ratio guides", s.showAspectGuides) {
                                    vm.updateSettings { st -> st.copy(showAspectGuides = !st.showAspectGuides) }
                                }
                            }
                            SettingsCard("Timeline") {
                                SwitchRow("Frame snapping", s.snapping) {
                                    vm.updateSettings { st -> st.copy(snapping = !st.snapping) }
                                }
                                SwitchRow("Magnetic snapping to layer edges", s.magneticSnapping) {
                                    vm.updateSettings { st -> st.copy(magneticSnapping = !st.magneticSnapping) }
                                }
                            }
                        }
                        "Export" -> {
                            SettingsCard("Defaults") {
                                LabeledSelect("Resolution", s.defaultResolution.name,
                                    ExportResolution.entries.map { it.name to it.label }) {
                                    vm.updateSettings { st -> st.copy(defaultResolution = ExportResolution.valueOf(it)) }
                                }
                                LabeledSelect("Frame rate", s.defaultFps.toString(),
                                    listOf("24", "30", "60").map { it to "$it fps" }) {
                                    vm.updateSettings { st -> st.copy(defaultFps = it.toInt()) }
                                }
                                LabeledSelect("Codec", s.defaultCodec.name,
                                    VideoCodec.entries.map { it.name to it.name }) {
                                    vm.updateSettings { st -> st.copy(defaultCodec = VideoCodec.valueOf(it)) }
                                }
                                SliderRow("Bitrate (Mbps)", s.defaultBitrate / 1_000_000f, 1f..30f, {
                                    vm.updateSettings { st -> st.copy(defaultBitrate = (it * 1_000_000).toInt()) }
                                })
                                SliderRow("Quality", s.defaultQuality.toFloat(), 10f..100f, {
                                    vm.updateSettings { st -> st.copy(defaultQuality = it.toInt()) }
                                })
                                LabeledSelect("Save location", s.defaultSaveLocation,
                                    listOf("gallery" to "Gallery / MediaStore", "app" to "App storage")) {
                                    vm.updateSettings { st -> st.copy(defaultSaveLocation = it) }
                                }
                                SwitchRow("Include audio", s.defaultIncludeAudio) {
                                    vm.updateSettings { st -> st.copy(defaultIncludeAudio = !st.defaultIncludeAudio) }
                                }
                            }
                        }
                        "Storage" -> {
                            SettingsCard("Cache") {
                                Text("Cache usage: ${vm.formatBytes(cache)}",
                                    style = MaterialTheme.typography.bodySmall)
                                SliderRow("Cache limit (MB)", s.cacheLimitMb.toFloat(), 32f..2048f, {
                                    vm.updateSettings { st -> st.copy(cacheLimitMb = it.toInt()) }
                                })
                                Button(onClick = { vm.clearCache(); cache = vm.cacheSize() }) {
                                    Text("Clear cache")
                                }
                            }
                            SettingsCard("Project files") {
                                Text("Projects are stored as versioned JSON in a Room database. Imported media is copied into private storage so references survive.",
                                    style = MaterialTheme.typography.bodySmall, color = EditorPalette.TextMuted)
                                LabeledSelect("Default project location", s.defaultProjectLocation,
                                    listOf("internal" to "Private app storage", "external" to "App external storage")) {
                                    vm.updateSettings { st -> st.copy(defaultProjectLocation = it) }
                                }
                            }
                        }
                        "AI" -> {
                            SettingsCard("AI service") {
                                Button(onClick = onAi, modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.Default.Bolt, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp)); Text("Open AI Studio")
                                }
                                SwitchRow("Enable AI Studio", s.aiEnabled) {
                                    vm.updateSettings { st -> st.copy(aiEnabled = !st.aiEnabled) }
                                }
                                OutlinedTextField(value = s.aiBaseUrl,
                                    onValueChange = { v -> vm.updateSettings { st -> st.copy(aiBaseUrl = v) } },
                                    label = { Text("Endpoint (OpenAI-compatible)") }, singleLine = true,
                                    modifier = Modifier.fillMaxWidth())
                                OutlinedTextField(value = s.aiModel,
                                    onValueChange = { v -> vm.updateSettings { st -> st.copy(aiModel = v) } },
                                    label = { Text("Model") }, singleLine = true,
                                    modifier = Modifier.fillMaxWidth())
                                OutlinedTextField(value = apiKey, onValueChange = { apiKey = it },
                                    label = { Text("API key${if (vm.aiKeyConfigured()) " (configured)" else ""}") },
                                    singleLine = true, modifier = Modifier.fillMaxWidth())
                                Button(onClick = { if (apiKey.isNotBlank()) { vm.setAiKey(apiKey); apiKey = "" } }) {
                                    Text("Save encrypted key")
                                }
                                SliderRow("Temperature", s.aiTemperature, 0f..1f, {
                                    vm.updateSettings { st -> st.copy(aiTemperature = it) }
                                })
                                SliderRow("Max tokens", s.aiMaxTokens.toFloat(), 128f..4096f, {
                                    vm.updateSettings { st -> st.copy(aiMaxTokens = it.toInt()) }
                                })
                                Text("Keys are encrypted with an Android Keystore AES-GCM key before storage. No AI result is simulated when the service is unavailable.",
                                    style = MaterialTheme.typography.labelSmall, color = EditorPalette.TextMuted)
                            }
                        }
                        "About" -> {
                            SettingsCard("EditorsHub EditorsHub") {
                                Text("Version ${BuildConfig.APP_VERSION_NAME} · build ${BuildConfig.APP_VERSION_CODE} · open source (MIT)",
                                    style = MaterialTheme.typography.bodySmall)
                                Text("Kotlin · Compose · Room · Hilt · MediaCodec · OpenGL ES",
                                    style = MaterialTheme.typography.labelSmall, color = EditorPalette.TextMuted)
                                TextButton(onClick = onTutorials) { Text("Help, tutorials & documentation") }
                                TextButton(onClick = onNodeEditor) { Text("Open Node Editor") }
                                Text("Privacy: projects and media remain on this device unless you explicitly share them. AI prompts are sent only to the endpoint you configure.",
                                    style = MaterialTheme.typography.labelSmall, color = EditorPalette.TextMuted)
                                Text("Licenses: Apache 2.0 (AndroidX, Media3, Hilt, Room), MIT (kotlinx), SIL OFL (system fonts).",
                                    style = MaterialTheme.typography.labelSmall, color = EditorPalette.TextMuted)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = EditorPalette.Surface)) {
        Column(Modifier.padding(EditorDimens.Space3)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = EditorPalette.Purple)
            Spacer(Modifier.height(EditorDimens.Space1))
            content()
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onChange),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = { onChange() })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TutorialsScreen(onBack: () -> Unit) {
    val steps = listOf(
        "Create a project" to "Home → New project. Choose resolution, aspect ratio, exact frame rate (including 23.976/29.97/59.94), background, audio sample rate, colour space and preview quality. Everything is saved in the project config.",
        "Import projects" to "Import project accepts native .json, Alight Motion .xml, Node Video .zip, and After Effects .aepx. Large archives stream without size limits and every conversion produces a compatibility report. Binary .aep is preserved as a recoverable reference instead of being faked.",
        "Add layers & edit the timeline" to "In the editor use Add for text, shapes, solids, vectors, groups and effects; import media through SAF. Drag clips, drag edges to trim, split at the playhead, duplicate, reorder, lock, hide, group, undo/redo.",
        "Animate" to "Move the playhead, enable Auto-key and change a transform value. Open Keys for the curve editor: drag keyframes, pick easing, or edit custom Bezier control points. Presets write real keyframes.",
        "Effects & nodes" to "Effects Library previews each effect with the real compositor. The Node Editor builds a validated DAG whose topological order becomes the actual render stack. AI Studio converts prompts into validated graphs via a configured inference endpoint.",
        "Export" to "Export renders every frame through the compositor and OpenGL ES shader chain into MediaCodec/MediaMuxer. Resolution, FPS, bitrate, codec and save location are honoured; progress and cancellation are real.",
        "Recover work" to "Autosave writes versioned JSON snapshots to Room. Interrupted sessions are flagged on the home screen and can be restored from version history."
    )
    Scaffold(containerColor = EditorPalette.Black, topBar = {
        TopAppBar(title = { Text("Tutorials & documentation") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = EditorPalette.Charcoal))
    }) { padding ->
        androidx.compose.foundation.lazy.LazyColumn(Modifier.padding(padding)
            .padding(EditorDimens.Space3), verticalArrangement = Arrangement.spacedBy(EditorDimens.Space2)) {
            items(steps) { (title, body) ->
                Card(Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = EditorPalette.Surface)) {
                    Column(Modifier.padding(EditorDimens.Space3)) {
                        Text(title, style = MaterialTheme.typography.titleSmall)
                        Text(body, style = MaterialTheme.typography.bodySmall,
                            color = EditorPalette.TextMuted)
                    }
                }
            }
        }
    }
}
