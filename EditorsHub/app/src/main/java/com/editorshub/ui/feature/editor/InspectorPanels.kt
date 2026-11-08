package com.editorshub.ui.feature.editor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.editorshub.core.domain.effects.EffectCatalog
import com.editorshub.core.domain.keyframe.KeyframeEngine
import com.editorshub.core.domain.model.*
import com.editorshub.core.rendering.RenderGraph
import com.editorshub.ui.components.*
import com.editorshub.ui.theme.AppMotion
import com.editorshub.ui.theme.EditorPalette
import kotlin.math.abs

private val TRANSFORM_PROPS = listOf(
    Triple("x", "Position X", -2000f..2000f),
    Triple("y", "Position Y", -2000f..2000f),
    Triple("scaleX", "Scale X", 0f..3f),
    Triple("scaleY", "Scale Y", 0f..3f),
    Triple("rotation", "Rotation", -720f..720f),
    Triple("opacity", "Opacity", 0f..1f),
    Triple("anchorX", "Anchor X", -1f..1f),
    Triple("anchorY", "Anchor Y", -1f..1f),
    Triple("skewX", "Skew X", -80f..80f)
)

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun Inspector(
    state: EditorUiState,
    vm: EditorViewModel,
    modifier: Modifier = Modifier,
    onShapePicker: () -> Unit = {}
) {
    val project = state.project ?: return
    val layer = project.layers.firstOrNull { it.id == state.selectedLayerId }
    Row(modifier = modifier.background(EditorPalette.Surface)) {
        Column(
            Modifier.width(54.dp).fillMaxHeight().background(EditorPalette.SurfaceRaised),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            IconButton(onClick = {
                if (state.tab in setOf(EditorTab.BORDER, EditorTab.BLEND, EditorTab.COLOR,
                        EditorTab.SHAPE, EditorTab.TRANSFORM, EditorTab.EFFECTS, EditorTab.TEXT,
                        EditorTab.VECTOR, EditorTab.MASKS, EditorTab.KEYFRAMES)) {
                    vm.setTab(EditorTab.PROPERTIES)
                } else vm.setTab(EditorTab.ADD)
            }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.ArrowBack, "Back to properties", tint = EditorPalette.TextPrimary)
            }
            IconButton(onClick = { vm.setTab(EditorTab.KEYFRAMES) }, modifier = Modifier.size(42.dp)) {
                Icon(Icons.Default.Add, "Add keyframe", tint = EditorPalette.TextSecondary)
            }
            IconButton(onClick = { vm.setTab(EditorTab.TRANSFORM) }, modifier = Modifier.size(42.dp)) {
                Icon(Icons.Default.ShowChart, "Transform and easing", tint = EditorPalette.TextSecondary)
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { vm.undo() }, enabled = state.undoAvailable,
                modifier = Modifier.size(42.dp)) {
                Icon(Icons.Default.Undo, "Undo", tint = EditorPalette.TextMuted)
            }
        }
        Box(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 6.dp, vertical = 4.dp)) {
            AnimatedContent(
                targetState = state.tab,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    val direction = if (targetState.ordinal >= initialState.ordinal) 1 else -1
                    (fadeIn(tween(AppMotion.Fast, easing = AppMotion.Standard)) +
                        slideInHorizontally(
                            initialOffsetX = { width -> direction * width / 14 },
                            animationSpec = tween(AppMotion.Normal, easing = AppMotion.Emphasized)
                        )) togetherWith
                        (fadeOut(tween(AppMotion.Fast)) +
                            slideOutHorizontally(
                                targetOffsetX = { width -> -direction * width / 18 },
                                animationSpec = tween(AppMotion.Fast, easing = AppMotion.Standard)
                            ))
                },
                label = "inspector-panel-transition"
            ) { panelTab ->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    when (panelTab) {
                        EditorTab.ADD -> AddPanel(state, vm, onShapePicker)
                        EditorTab.PROPERTIES -> PropertiesPanel(vm, layer)
                        EditorTab.BORDER -> BorderPanel(state, vm, layer)
                        EditorTab.BLEND -> BlendPanel(vm, layer)
                        EditorTab.COLOR -> ColorPanel(vm, layer)
                        EditorTab.TRANSFORM -> TransformPanel(state, vm, layer, project)
                        EditorTab.KEYFRAMES -> KeyframesPanel(state, vm, layer)
                        EditorTab.EFFECTS -> EffectsPanel(state, vm, layer)
                        EditorTab.TEXT -> TextPanel(state, vm, layer)
                        EditorTab.SHAPE -> ShapePanel(state, vm, layer)
                        EditorTab.VECTOR -> VectorPanel(state, vm, layer)
                        EditorTab.MASKS -> MasksPanel(state, vm, layer)
                        EditorTab.AUDIO -> AudioPanel(state, vm, project)
                        EditorTab.TRANSITIONS -> TransitionsPanel(state, vm, layer, project)
                        EditorTab.PROJECT -> ProjectPanel(state, vm, project)
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = state.tab == EditorTab.BORDER,
            enter = fadeIn(tween(AppMotion.Fast)) + slideInHorizontally(
                initialOffsetX = { it / 3 }, animationSpec = tween(AppMotion.Normal, easing = AppMotion.Emphasized)
            ),
            exit = fadeOut(tween(AppMotion.Fast)) + slideOutHorizontally(
                targetOffsetX = { it / 3 }, animationSpec = tween(AppMotion.Fast, easing = AppMotion.Standard)
            )
        ) {
            Column(
                Modifier.width(62.dp).fillMaxHeight().background(EditorPalette.Charcoal),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                BorderModeButton("stroke", "Stroke", Icons.Default.Brush, state, vm)
                BorderModeButton("border", "Border", Icons.Default.CropSquare, state, vm)
                BorderModeButton("shadow", "Shadow", Icons.Default.Layers, state, vm)
            }
        }
    }
}

@Composable
private fun PropertiesPanel(vm: EditorViewModel, layer: Layer?) {
    val entries = listOf(
        Triple("Color & Fill", Icons.Default.Palette, EditorTab.COLOR),
        Triple("Border & Shadow", Icons.Default.BorderColor, EditorTab.BORDER),
        Triple("Blending & Opacity", Icons.Default.BlurOn, EditorTab.BLEND),
        Triple("Move & Transform", Icons.Default.OpenWith, EditorTab.TRANSFORM),
        Triple("Edit Shape", Icons.Default.Edit, EditorTab.SHAPE),
        Triple("Effects", Icons.Default.AutoAwesome, EditorTab.EFFECTS),
        Triple("Keyframes", Icons.Default.Add, EditorTab.KEYFRAMES),
        Triple("Masks", Icons.Default.Crop, EditorTab.MASKS)
    )
    if (layer == null) { Hint("Select a layer to edit its properties"); return }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        entries.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth().height(62.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { (title, icon, tab) ->
                    val interaction = remember(title) { androidx.compose.foundation.interaction.MutableInteractionSource() }
                    Card(
                        Modifier.weight(1f).fillMaxHeight().pressScale(interaction, 0.95f)
                            .clickable(interactionSource = interaction,
                                indication = androidx.compose.foundation.LocalIndication.current) { vm.setTab(tab) },
                        shape = RoundedCornerShape(9.dp),
                        colors = CardDefaults.cardColors(containerColor = EditorPalette.SurfaceRaised)
                    ) {
                        Column(Modifier.fillMaxSize().padding(5.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center) {
                            Icon(icon, null, Modifier.size(20.dp), tint = EditorPalette.TextPrimary)
                            Spacer(Modifier.height(3.dp))
                            Text(title, fontSize = 8.sp, color = EditorPalette.TextPrimary, maxLines = 2)
                        }
                    }
                }
                repeat((3 - row.size).coerceAtLeast(0)) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun BorderModeButton(mode: String, label: String,
                             icon: androidx.compose.ui.graphics.vector.ImageVector,
                             state: EditorUiState, vm: EditorViewModel) {
    val selected = state.selectedProperty == mode
    val interaction = remember(mode) { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Column(
        Modifier.width(52.dp).height(58.dp).pressScale(interaction, 0.94f).background(
            if (selected) EditorPalette.SurfaceRaised else Color.Transparent, RoundedCornerShape(8.dp)
        ).clickable(interactionSource = interaction,
            indication = androidx.compose.foundation.LocalIndication.current) { vm.setSelectedProperty(mode) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, label, Modifier.size(21.dp), tint = if (selected) EditorPalette.Purple else EditorPalette.TextSecondary)
        Text(label, fontSize = 7.sp, color = if (selected) EditorPalette.Purple else EditorPalette.TextMuted)
    }
}

@Composable
private fun BorderPanel(state: EditorUiState, vm: EditorViewModel, layer: Layer?) {
    val spec = layer?.shape
    if (spec == null) { Hint("Border and shadow are available for shape layers"); return }
    when (state.selectedProperty) {
        "border" -> {
            if (!spec.strokeEnabled) {
                Button(onClick = { vm.updateShape { it.copy(strokeEnabled = true) } },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EditorPalette.SurfaceRaised)) {
                    Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                    Text("Add Border", color = EditorPalette.TextPrimary, fontSize = 12.sp)
                }
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Border", Modifier.weight(1f), fontSize = 12.sp)
                    Switch(checked = spec.strokeEnabled,
                        onCheckedChange = { enabled -> vm.updateShape { it.copy(strokeEnabled = enabled) } })
                }
                SliderRow("Width", spec.strokeWidth, 0f..100f, onChange = { value ->
                    vm.updateShape { shape -> shape.copy(strokeWidth = value) }
                })
            }
        }
        "shadow" -> {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Shadow", Modifier.weight(1f), fontSize = 12.sp)
                Switch(checked = spec.shadowEnabled,
                    onCheckedChange = { enabled -> vm.updateShape { it.copy(shadowEnabled = enabled) } })
            }
            SliderRow("Size", spec.shadowSize, 0f..80f, onChange = { value ->
                vm.updateShape { shape -> shape.copy(shadowSize = value) }
            })
            SliderRow("Alpha", spec.shadowAlpha, 0f..1f, onChange = { value ->
                vm.updateShape { shape -> shape.copy(shadowAlpha = value) }
            })
            SliderRow("Position X", spec.shadowX, -100f..100f, onChange = { value ->
                vm.updateShape { shape -> shape.copy(shadowX = value) }
            })
            SliderRow("Position Y", spec.shadowY, -100f..100f, onChange = { value ->
                vm.updateShape { shape -> shape.copy(shadowY = value) }
            })
            ColorSwatchRow("Shadow color", spec.shadowColor) { value ->
                vm.updateShape { shape -> shape.copy(shadowColor = value) }
            }
        }
        else -> {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CropSquare, null, Modifier.size(20.dp), tint = EditorPalette.TextPrimary)
                Text("Stroke", Modifier.weight(1f).padding(start = 8.dp), fontSize = 12.sp)
                Switch(checked = spec.strokeEnabled,
                    onCheckedChange = { enabled -> vm.updateShape { it.copy(strokeEnabled = enabled) } })
            }
            SliderRow("Width", spec.strokeWidth, 0f..100f, onChange = { value ->
                vm.updateShape { shape -> shape.copy(strokeWidth = value) }
            })
            Text("Line cap", fontSize = 9.sp, color = EditorPalette.TextMuted)
            ChoiceStrip(listOf("Butt", "Round", "Square"), spec.strokeCap) { value ->
                vm.updateShape { it.copy(strokeCap = value) }
            }
            Text("Line join", fontSize = 9.sp, color = EditorPalette.TextMuted)
            ChoiceStrip(listOf("Miter", "Round", "Bevel"), spec.strokeJoin) { value ->
                vm.updateShape { it.copy(strokeJoin = value) }
            }
            SliderRow("Start", spec.strokeStart, 0f..100f, onChange = { value ->
                vm.updateShape { shape -> shape.copy(strokeStart = value) }
            })
            SliderRow("End", spec.strokeEnd, 0f..100f, onChange = { value ->
                vm.updateShape { shape -> shape.copy(strokeEnd = value) }
            })
        }
    }
}

@Composable
private fun ChoiceStrip(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        labels.forEachIndexed { index, label ->
            TextButton(onClick = { onSelect(index) }, modifier = Modifier.weight(1f).height(32.dp),
                contentPadding = PaddingValues(0.dp)) {
                Text(label, fontSize = 8.sp,
                    color = if (index == selected) EditorPalette.Purple else EditorPalette.TextSecondary)
            }
        }
    }
}

@Composable
private fun BlendPanel(vm: EditorViewModel, layer: Layer?) {
    if (layer == null) { Hint("Select a layer"); return }
    val modes = BlendMode.entries
    SliderRow("Opacity", layer.transform.opacity, 0f..1f,
        { vm.setTransform("opacity", it) })
    Text("Blending mode", fontSize = 10.sp, color = EditorPalette.TextMuted,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp))
    modes.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            row.forEach { mode ->
                val selected = layer.blendMode == mode
                TextButton(onClick = { vm.setBlend(layer.id, mode) }, modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColors(contentColor = if (selected) EditorPalette.Purple else EditorPalette.TextPrimary)) {
                    Text(mode.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() },
                        fontSize = 9.sp, maxLines = 1)
                }
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun ColorPanel(vm: EditorViewModel, layer: Layer?) {
    val spec = layer?.shape
    if (spec == null) { Hint("Select a shape layer to edit its fill"); return }
    LabeledSelect("Fill", spec.gradient.name,
        GradientType.entries.map { it.name to it.name }) { value ->
        vm.updateShape { it.copy(gradient = GradientType.valueOf(value)) }
    }
    ColorSwatchRow("Primary", spec.fillColor) { value -> vm.updateShape { it.copy(fillColor = value) } }
    ColorSwatchRow("Gradient end", spec.fillColor2) { value -> vm.updateShape { it.copy(fillColor2 = value) } }
    SliderRow("Gradient angle", spec.gradientAngle, 0f..360f, onChange = { value ->
        vm.updateShape { shape -> shape.copy(gradientAngle = value) }
    })
}

@Composable
private fun AddPanel(state: EditorUiState, vm: EditorViewModel, onShapePicker: () -> Unit) {
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { uri -> vm.importMedia(uri, null) }
    }
    PanelSection("Add layer") {
        val types = listOf(
            LayerType.TEXT to Icons.Default.TextFields,
            LayerType.SHAPE to Icons.Default.Category,
            LayerType.SOLID to Icons.Default.CropSquare,
            LayerType.VECTOR to Icons.Default.Edit,
            LayerType.GROUP to Icons.Default.Layers,
            LayerType.EFFECT to Icons.Default.Star
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            types.chunked(3).forEach { group ->
                Column(Modifier.weight(1f)) {
                    group.forEach { (type, icon) ->
                        Button(onClick = {
                            if (type == LayerType.SHAPE) onShapePicker() else vm.addLayer(type)
                        }, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Icon(icon, null, Modifier.size(15.dp)); Spacer(Modifier.width(4.dp))
                            Text(type.name.lowercase().replaceFirstChar { it.uppercase() }, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
        Button(onClick = { importLauncher.launch(arrayOf("video/*", "image/*", "audio/*")) },
            modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Upload, null, Modifier.size(15.dp)); Spacer(Modifier.width(5.dp))
            Text("Import media (SAF)", fontSize = 11.sp)
        }
        Text("Supported: MP4, MOV, WebM, PNG, JPG, WebP, GIF, MP3, WAV, AAC",
            fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    PanelSection("Motion presets") {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.fillMaxWidth()) {
            listOf("Fade in", "Fade out", "Slide up", "Pop", "Spin", "Pulse").chunked(3).forEach { group ->
                Column(Modifier.weight(1f)) {
                    group.forEach { p ->
                        OutlinedButton(onClick = { vm.applyPreset(p) }, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Text(p, fontSize = 9.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TransformPanel(state: EditorUiState, vm: EditorViewModel, layer: Layer?, project: Project) {
    if (layer == null) { Hint("Select a layer"); return }
    val local = state.playheadMs - layer.startMs
    val rt = RenderGraph.resolvedTransform(layer, state.playheadMs)
    PanelSection("Layer: ${layer.name}") {
        OutlinedTextField(value = layer.name, onValueChange = { vm.renameLayer(layer.id, it) },
            modifier = Modifier.fillMaxWidth(), singleLine = true, textStyle = LocalTextStyle.current.copy(fontSize = 12.sp))
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            IconButton(onClick = { vm.toggleVisible(layer.id) }) {
                Icon(if (layer.visible) Icons.Default.Visibility else Icons.Default.VisibilityOff, null)
            }
            IconButton(onClick = { vm.toggleLock(layer.id) }) {
                Icon(if (layer.locked) Icons.Default.Lock else Icons.Default.LockOpen, null)
            }
            IconButton(onClick = { vm.duplicateLayer(layer.id) }) { Icon(Icons.Default.ContentCopy, null) }
            IconButton(onClick = { vm.splitLayer(layer.id) }) { Icon(Icons.Default.ContentCut, null) }
            IconButton(onClick = { vm.deleteLayer(layer.id) }) {
                Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Auto-key", fontSize = 11.sp, modifier = Modifier.weight(1f))
            Switch(checked = state.autoKey, onCheckedChange = { vm.setAutoKey(it) })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Multi-select", fontSize = 11.sp, modifier = Modifier.weight(1f))
            Switch(checked = state.multiSelect, onCheckedChange = { vm.toggleMultiSelect() })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(onClick = { vm.copyLayers() }) { Icon(Icons.Default.ContentCopy, "Copy", Modifier.size(16.dp)) }
            IconButton(onClick = { vm.pasteLayers() }) { Icon(Icons.Default.ContentPaste, "Paste", Modifier.size(16.dp)) }
            IconButton(onClick = { vm.groupSelected() }) { Icon(Icons.Default.Workspaces, "Group", Modifier.size(16.dp)) }
            IconButton(onClick = { vm.ungroupSelected() }) { Icon(Icons.Default.LayersClear, "Ungroup", Modifier.size(16.dp)) }
            IconButton(onClick = { vm.toggleEnabled(layer.id) }) { Icon(Icons.Default.ToggleOn, "Enable", Modifier.size(16.dp)) }
            IconButton(onClick = { vm.toggleMuteLayer(layer.id) }) { Icon(Icons.Default.VolumeOff, "Mute", Modifier.size(16.dp)) }
        }
    }
    PanelSection("Transform") {
        TRANSFORM_PROPS.forEach { (key, label, range) ->
            val value = when (key) {
                "x" -> rt.x; "y" -> rt.y; "scaleX" -> rt.scaleX; "scaleY" -> rt.scaleY
                "rotation" -> rt.rotation; "opacity" -> rt.opacity
                "anchorX" -> rt.anchorX; "anchorY" -> rt.anchorY; "skewX" -> rt.skewX
                else -> 0f
            }
            val track = layer.keyframes.firstOrNull { it.property == key }
            val keyed = track?.keyframes?.any { abs(it.timeMs - local) < 40L } ?: false
            SliderRow(label, value, range, { vm.setTransform(key, it) },
                onKeyframe = {
                    val existing = track?.keyframes?.firstOrNull { abs(it.timeMs - local) < 40L }
                    if (existing != null) vm.removeKeyframe(key, existing.id) else vm.addKeyframe(key)
                }, keyed = keyed)
        }
    }
    PanelSection("Blend & hierarchy") {
        LabeledSelect("Blend mode", layer.blendMode.name,
            BlendMode.entries.map { it.name to it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }) { v ->
            vm.setBlend(layer.id, BlendMode.valueOf(v))
        }
        LabeledSelect("Parent", layer.parentId ?: "",
            listOf("" to "None") + project.layers.filter { it.id != layer.id }.map { it.id to it.name }) { v ->
            vm.setParent(layer.id, v.ifBlank { null })
        }
        LabeledSelect("Fit", layer.fit.name,
            FitMode.entries.map { it.name to it.name }) { v -> vm.setFit(layer.id, FitMode.valueOf(v)) }
    }
    PanelSection("Timing") {
        Text("Start ${layer.startMs}ms · Duration ${layer.durationMs}ms", fontSize = 10.sp)
        SliderRow("Timeline zoom", state.zoomPxPerSecond, 12f..320f, { vm.setZoom(it) })
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Button(onClick = { vm.reorderLayer(project.layers.indexOfFirst { it.id == layer.id },
                (project.layers.indexOfFirst { it.id == layer.id } - 1).coerceAtLeast(0)) },
                modifier = Modifier.weight(1f)) { Text("Up", fontSize = 10.sp) }
            Button(onClick = { vm.reorderLayer(project.layers.indexOfFirst { it.id == layer.id },
                (project.layers.indexOfFirst { it.id == layer.id } + 1).coerceAtMost(project.layers.size - 1)) },
                modifier = Modifier.weight(1f)) { Text("Down", fontSize = 10.sp) }
        }
    }
}

@Composable
private fun KeyframesPanel(state: EditorUiState, vm: EditorViewModel, layer: Layer?) {
    if (layer == null) { Hint("Select a layer"); return }
    val properties = TRANSFORM_PROPS.map { it.first } + listOf("color", "blur", "feather", "volume")
    val track = layer.keyframes.firstOrNull { it.property == state.selectedProperty } ?: KeyframeTrack(state.selectedProperty)
    val local = state.playheadMs - layer.startMs
    PanelSection("Keyframe engine") {
        LabeledSelect("Property", state.selectedProperty, properties.map { it to it }) { vm.setSelectedProperty(it) }
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Button(onClick = { vm.addKeyframe(state.selectedProperty) }, modifier = Modifier.weight(1f)) {
                Text("◆ Add", fontSize = 10.sp)
            }
            Button(onClick = { vm.copyKeyframes(state.selectedProperty) }, modifier = Modifier.weight(1f)) {
                Text("Copy", fontSize = 10.sp)
            }
            Button(onClick = { vm.pasteKeyframes(state.selectedProperty) }, modifier = Modifier.weight(1f)) {
                Text("Paste", fontSize = 10.sp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            OutlinedButton(onClick = { vm.clearKeyframes(state.selectedProperty) }, modifier = Modifier.weight(1f)) {
                Text("Clear", fontSize = 10.sp)
            }
            OutlinedButton(onClick = { vm.stepFrames(-1) }, modifier = Modifier.weight(1f)) { Text("◀", fontSize = 10.sp) }
            OutlinedButton(onClick = { vm.stepFrames(1) }, modifier = Modifier.weight(1f)) { Text("▶", fontSize = 10.sp) }
        }
        Text("${track.keyframes.size} keyframes · local playhead ${local}ms", fontSize = 10.sp)
    }
    PanelSection("Graph editor") {
        KeyframeGraph(track, state.selectedProperty, vm, Modifier.fillMaxWidth().height(150.dp))
        Text("Drag a diamond to change its time and value.", fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    PanelSection("Keyframes") {
        track.keyframes.sortedBy { it.timeMs }.forEach { kf ->
            Card(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Column(Modifier.padding(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("@ ${kf.timeMs}ms = ${"%.2f".format(kf.value)}", fontSize = 10.sp,
                            modifier = Modifier.weight(1f))
                        IconButton(onClick = { vm.removeKeyframe(state.selectedProperty, kf.id) },
                            modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Close, null, Modifier.size(13.dp)) }
                    }
                    LabeledSelect("Easing", kf.easing.name,
                        Easing.entries.map { it.name to it.name }) { v ->
                        vm.setKeyframeEasing(state.selectedProperty, kf.id, Easing.valueOf(v))
                    }
                    if (kf.easing == Easing.BEZIER) {
                        SliderRow("Control 1 X", kf.cp1x, 0f..1f, {
                            vm.setKeyframeBezier(state.selectedProperty, kf.id, it, kf.cp1y, kf.cp2x, kf.cp2y)
                        })
                        SliderRow("Control 1 Y", kf.cp1y, -0.5f..1.5f, {
                            vm.setKeyframeBezier(state.selectedProperty, kf.id, kf.cp1x, it, kf.cp2x, kf.cp2y)
                        })
                        SliderRow("Control 2 X", kf.cp2x, 0f..1f, {
                            vm.setKeyframeBezier(state.selectedProperty, kf.id, kf.cp1x, kf.cp1y, it, kf.cp2y)
                        })
                        SliderRow("Control 2 Y", kf.cp2y, -0.5f..1.5f, {
                            vm.setKeyframeBezier(state.selectedProperty, kf.id, kf.cp1x, kf.cp1y, kf.cp2x, it)
                        })
                    }
                    SliderRow("Time", kf.timeMs.toFloat(), 0f..layer.durationMs.toFloat(),
                        { vm.moveKeyframe(state.selectedProperty, kf.id, it.toLong()) })
                    SliderRow("Value", kf.value, -2000f..2000f,
                        { vm.setKeyframeValue(state.selectedProperty, kf.id, it) })
                }
            }
        }
    }
}

@Composable
private fun KeyframeGraph(track: KeyframeTrack, property: String, vm: EditorViewModel, modifier: Modifier) {
    val kfs = track.keyframes.sortedBy { it.timeMs }
    val maxT = (kfs.maxOfOrNull { it.timeMs } ?: 1000L).coerceAtLeast(1000L).toFloat()
    val minV = (kfs.minOfOrNull { it.value } ?: 0f) - 0.5f
    val maxV = (kfs.maxOfOrNull { it.value } ?: 1f) + 0.5f
    var dragging by remember { mutableStateOf<String?>(null) }
    Canvas(modifier.background(Color(0xFF10121B)).border(1.dp, Color(0xFF2A2E40))
        .pointerInput(track) {
            detectDragGestures(
                onDragStart = { off ->
                    val nearest = kfs.minByOrNull {
                        val x = it.timeMs / maxT * size.width
                        val y = size.height - (it.value - minV) / (maxV - minV) * size.height
                        (x - off.x) * (x - off.x) + (y - off.y) * (y - off.y)
                    }
                    dragging = nearest?.id
                },
                onDragEnd = { dragging = null },
                onDrag = { _, drag ->
                    val id = dragging ?: return@detectDragGestures
                    val kf = kfs.firstOrNull { it.id == id } ?: return@detectDragGestures
                    val newT = ((kf.timeMs / maxT * size.width + drag.x) / size.width * maxT)
                        .toLong().coerceAtLeast(0L)
                    val newV = kf.value - drag.y / size.height * (maxV - minV)
                    vm.moveKeyframe(property, id, newT)
                    vm.setKeyframeValue(property, id, newV)
                }
            )
        }) {
        val w = size.width; val h = size.height
        drawLine(Color(0xFF303449), Offset(0f, h / 2), Offset(w, h / 2))
        drawLine(Color(0xFF303449), Offset(w / 2, 0f), Offset(w / 2, h))
        if (kfs.size >= 2) {
            val path = Path()
            var first = true
            for (i in 0 until kfs.size - 1) {
                val a = kfs[i]; val b = kfs[i + 1]
                val span = (b.timeMs - a.timeMs).coerceAtLeast(1L)
                for (s in 0..30) {
                    val t = s / 30f
                    val time = a.timeMs + span * t
                    val eased = KeyframeEngine.ease(t, a.easing, a)
                    val v = a.value + (b.value - a.value) * eased
                    val x = time / maxT * w
                    val y = h - (v - minV) / (maxV - minV) * h
                    if (first) { path.moveTo(x, y); first = false } else path.lineTo(x, y)
                }
            }
            drawPath(path, Color(0xFF8B7BFF), style = Stroke(width = 3f))
        }
        kfs.forEach { kf ->
            val x = kf.timeMs / maxT * w
            val y = h - (kf.value - minV) / (maxV - minV) * h
            drawCircle(Color(0xFFFBBF24), radius = 9f, center = Offset(x, y))
        }
    }
}

@Composable
private fun EffectsPanel(state: EditorUiState, vm: EditorViewModel, layer: Layer?) {
    if (layer == null) { Hint("Select a layer"); return }
    val local = state.playheadMs - layer.startMs
    PanelSection("Applied (${layer.effects.size})") {
        layer.effects.forEachIndexed { i, e ->
            val spec = EffectCatalog.spec(e.type)
            Card(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Column(Modifier.padding(7.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(spec.label, fontSize = 11.sp, modifier = Modifier.weight(1f))
                        IconButton(onClick = { vm.toggleEffect(e.id) }, modifier = Modifier.size(26.dp)) {
                            Icon(if (e.enabled) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                null, Modifier.size(14.dp))
                        }
                        IconButton(onClick = { vm.reorderEffect(e.id, -1) }, modifier = Modifier.size(26.dp)) {
                            Icon(Icons.Default.ArrowUpward, null, Modifier.size(13.dp))
                        }
                        IconButton(onClick = { vm.reorderEffect(e.id, 1) }, modifier = Modifier.size(26.dp)) {
                            Icon(Icons.Default.ArrowDownward, null, Modifier.size(13.dp))
                        }
                        IconButton(onClick = { vm.removeEffect(e.id) }, modifier = Modifier.size(26.dp)) {
                            Icon(Icons.Default.Close, null, Modifier.size(13.dp))
                        }
                    }
                    spec.params.forEach { p ->
                        val value = EffectCatalog.resolvedParams(e, local)[p.key] ?: p.default
                        val keyed = e.animated[p.key]?.keyframes?.any { abs(it.timeMs - local) < 40L } ?: false
                        SliderRow(p.label, value, p.min..p.max,
                            { vm.setEffectParam(e.id, p.key, it) },
                            onKeyframe = { /* animated params are keyframed via Auto-key */ },
                            keyed = keyed)
                    }
                }
            }
        }
    }
    PanelSection("Effect catalog") {
        EffectCatalog.groups().forEach { group ->
            Text(group, fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
            EffectCatalog.specs.filter { it.group == group }.forEach { s ->
                OutlinedButton(onClick = { vm.addEffect(s.type) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                    Text("+ ${s.label}", fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun TextPanel(state: EditorUiState, vm: EditorViewModel, layer: Layer?) {
    val spec = layer?.text
    if (spec == null) { Hint("Select a text layer"); return }
    PanelSection("Text") {
        OutlinedTextField(
            value = spec.text,
            onValueChange = { newText -> vm.updateText { t -> t.copy(text = newText) } },
            modifier = Modifier.fillMaxWidth(), minLines = 2,
            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
        )
    }
    PanelSection("Typography") {
        SliderRow("Font size", spec.fontSize, 12f..300f, { vm.updateText { t -> t.copy(fontSize = it) } })
        SliderRow("Weight", spec.fontWeight.toFloat(), 100f..900f,
            { vm.updateText { t -> t.copy(fontWeight = it.toInt()) } })
        LabeledSelect("Alignment", spec.align.name,
            TextAlign.entries.map { it.name to it.name }) { v -> vm.updateText { t -> t.copy(align = TextAlign.valueOf(v)) } }
        LabeledSelect("Font", spec.fontFamily,
            listOf("sans" to "Sans", "serif" to "Serif", "monospace" to "Monospace")) { v ->
            vm.updateText { t -> t.copy(fontFamily = v) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Italic", fontSize = 11.sp, modifier = Modifier.weight(1f))
            Switch(checked = spec.italic, onCheckedChange = { vm.updateText { t -> t.copy(italic = it) } })
            Text("Caps", fontSize = 11.sp, modifier = Modifier.weight(1f))
            Switch(checked = spec.uppercase, onCheckedChange = { vm.updateText { t -> t.copy(uppercase = it) } })
        }
        SliderRow("Letter spacing", spec.letterSpacing, -10f..60f,
            { vm.updateText { t -> t.copy(letterSpacing = it) } })
        SliderRow("Line spacing", spec.lineSpacing, 0.7f..2.5f,
            { vm.updateText { t -> t.copy(lineSpacing = it) } })
    }
    PanelSection("Color & decoration") {
        LabeledSelect("Fill", spec.gradient.name,
            GradientType.entries.map { it.name to it.name }) { v ->
            vm.updateText { t -> t.copy(gradient = GradientType.valueOf(v)) }
        }
        ColorSwatchRow("Primary", spec.color) { v -> vm.updateText { t -> t.copy(color = v) } }
        ColorSwatchRow("Gradient end", spec.color2) { v -> vm.updateText { t -> t.copy(color2 = v) } }
        SliderRow("Gradient angle", spec.gradientAngle, 0f..360f,
            { vm.updateText { t -> t.copy(gradientAngle = it) } })
        SliderRow("Shadow blur", spec.shadowBlur, 0f..60f,
            { vm.updateText { t -> t.copy(shadowBlur = it) } })
        SliderRow("Outline width", spec.outlineWidth, 0f..30f,
            { vm.updateText { t -> t.copy(outlineWidth = it) } })
        ColorSwatchRow("Outline", spec.outlineColor) { v -> vm.updateText { t -> t.copy(outlineColor = v) } }
    }
    PanelSection("Text animation") {
        listOf("Fade in", "Slide up", "Pop", "Spin", "Pulse").forEach { p ->
            OutlinedButton(onClick = { vm.applyPreset(p) }, modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                Text(p, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun ShapePanel(state: EditorUiState, vm: EditorViewModel, layer: Layer?) {
    val spec = layer?.shape
    if (spec == null) { Hint("Select a shape or solid layer"); return }
    PanelSection("Shape") {
        ChipRow(ShapeKind.entries.map { it.name }, spec.kind.name) { v ->
            vm.updateShape { s -> s.copy(kind = ShapeKind.valueOf(v)) }
        }
        SliderRow("Width", spec.width, 20f..2000f, { vm.updateShape { s -> s.copy(width = it) } })
        SliderRow("Height", spec.height, 20f..2000f, { vm.updateShape { s -> s.copy(height = it) } })
        SliderRow("Corner radius", spec.cornerRadius, 0f..400f,
            { vm.updateShape { s -> s.copy(cornerRadius = it) } })
        SliderRow("Sides", spec.sides.toFloat(), 3f..20f,
            { vm.updateShape { s -> s.copy(sides = it.toInt()) } })
        SliderRow("Star points", spec.starPoints.toFloat(), 3f..20f,
            { vm.updateShape { s -> s.copy(starPoints = it.toInt()) } })
        SliderRow("Inner radius", spec.innerRadius, 0.05f..0.95f,
            { vm.updateShape { s -> s.copy(innerRadius = it) } })
    }
    PanelSection("Fill & stroke") {
        LabeledSelect("Fill", spec.gradient.name,
            GradientType.entries.map { it.name to it.name }) { v ->
            vm.updateShape { s -> s.copy(gradient = GradientType.valueOf(v)) }
        }
        ColorSwatchRow("Primary", spec.fillColor) { v -> vm.updateShape { s -> s.copy(fillColor = v) } }
        ColorSwatchRow("Secondary", spec.fillColor2) { v -> vm.updateShape { s -> s.copy(fillColor2 = v) } }
        SliderRow("Stroke width", spec.strokeWidth, 0f..100f,
            { vm.updateShape { s -> s.copy(strokeWidth = it) } })
        ColorSwatchRow("Stroke", spec.strokeColor) { v -> vm.updateShape { s -> s.copy(strokeColor = v) } }
    }
}

@Composable
private fun VectorPanel(state: EditorUiState, vm: EditorViewModel, layer: Layer?) {
    val spec = layer?.vector
    if (spec == null) { Hint("Select a vector layer"); return }
    PanelSection("Bezier path") {
        spec.commands.forEachIndexed { i, cmd ->
            Card(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Row(Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}. ${cmd.op}", fontSize = 10.sp, modifier = Modifier.width(48.dp))
                    cmd.args.chunked(2).forEachIndexed { idx, pair ->
                        Text("(${pair.getOrNull(0)?.toInt() ?: 0},${pair.getOrNull(1)?.toInt() ?: 0})",
                            fontSize = 9.sp, modifier = Modifier.padding(horizontal = 3.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = {
                        vm.updateVector { v -> v.copy(commands = v.commands.filterIndexed { j, _ -> j != i }) }
                    }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Close, null, Modifier.size(12.dp)) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Button(onClick = {
                vm.updateVector { v -> v.copy(commands = v.commands + VectorCommand("L", listOf(0f, 0f))) }
            }, modifier = Modifier.weight(1f)) { Text("+ Line", fontSize = 10.sp) }
            Button(onClick = {
                vm.updateVector { v -> v.copy(commands = v.commands + VectorCommand("C", listOf(50f, -50f, 100f, 50f, 150f, 0f))) }
            }, modifier = Modifier.weight(1f)) { Text("+ Curve", fontSize = 10.sp) }
        }
        SliderRow("Stroke width", spec.strokeWidth, 0f..60f,
            { vm.updateVector { v -> v.copy(strokeWidth = it) } })
        ColorSwatchRow("Fill", spec.fillColor) { v -> vm.updateVector { s -> s.copy(fillColor = v) } }
    }
}

@Composable
private fun MasksPanel(state: EditorUiState, vm: EditorViewModel, layer: Layer?) {
    if (layer == null) { Hint("Select a layer"); return }
    PanelSection("Masks") {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            listOf(MaskKind.RECTANGLE, MaskKind.CIRCLE, MaskKind.PATH).forEach { k ->
                OutlinedButton(onClick = { vm.addMask(k) }, modifier = Modifier.weight(1f)) {
                    Text("+ ${k.name.lowercase()}", fontSize = 9.sp)
                }
            }
        }
        layer.masks.forEach { mask ->
            Card(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Column(Modifier.padding(7.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(mask.kind.name, fontSize = 11.sp, modifier = Modifier.weight(1f))
                        IconButton(onClick = { vm.removeMask(mask.id) }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, null, Modifier.size(13.dp))
                        }
                    }
                    SliderRow("Feather", mask.featherPx, 0f..200f,
                        { vm.updateMask(mask.id) { m -> m.copy(featherPx = it) } })
                    SliderRow("Opacity", mask.opacity, 0f..1f,
                        { vm.updateMask(mask.id) { m -> m.copy(opacity = it) } })
                    SliderRow("Expansion", mask.expansionPx, -200f..200f,
                        { vm.updateMask(mask.id) { m -> m.copy(expansionPx = it) } })
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Inverted", fontSize = 11.sp, modifier = Modifier.weight(1f))
                        Switch(checked = mask.inverted,
                            onCheckedChange = { vm.updateMask(mask.id) { m -> m.copy(inverted = it) } })
                    }
                }
            }
        }
        Text("Masks are composited with real DST_IN / DST_OUT Porter-Duff paths.",
            fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AudioPanel(state: EditorUiState, vm: EditorViewModel, project: Project) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { vm.importMedia(it, null) }
    }
    PanelSection("Audio tracks (${project.audioTracks.size})") {
        Button(onClick = { launcher.launch(arrayOf("audio/*")) }, modifier = Modifier.fillMaxWidth()) {
            Text("+ Import audio", fontSize = 11.sp)
        }
        project.audioTracks.forEach { track ->
            Card(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Column(Modifier.padding(7.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(track.name, fontSize = 11.sp, modifier = Modifier.weight(1f), maxLines = 1)
                        IconButton(onClick = { vm.removeAudioTrack(track.id) }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, null, Modifier.size(13.dp))
                        }
                    }
                    Waveform(track.peaks, Modifier.fillMaxWidth().height(38.dp))
                    SliderRow("Volume", track.volume, 0f..2f,
                        { vm.setAudioTrack(track.id) { t -> t.copy(volume = it) } })
                    SliderRow("Fade in (ms)", track.fadeInMs.toFloat(), 0f..5000f,
                        { vm.setAudioTrack(track.id) { t -> t.copy(fadeInMs = it.toLong()) } })
                    SliderRow("Fade out (ms)", track.fadeOutMs.toFloat(), 0f..5000f,
                        { vm.setAudioTrack(track.id) { t -> t.copy(fadeOutMs = it.toLong()) } })
                }
            }
        }
    }
}

@Composable
private fun Waveform(peaks: List<Float>, modifier: Modifier) {
    Canvas(modifier.background(Color(0xFF10121B))) {
        if (peaks.isEmpty()) {
            drawLine(Color(0xFF454A63), Offset(0f, size.height / 2), Offset(size.width, size.height / 2))
            return@Canvas
        }
        val barW = size.width / peaks.size
        peaks.forEachIndexed { i, v ->
            val h = v * size.height * 0.9f
            drawLine(Color(0xFF22D3EE),
                Offset(i * barW + barW / 2, size.height / 2 - h / 2),
                Offset(i * barW + barW / 2, size.height / 2 + h / 2),
                strokeWidth = barW.coerceAtLeast(1f))
        }
    }
}

@Composable
private fun TransitionsPanel(state: EditorUiState, vm: EditorViewModel, layer: Layer?, project: Project) {
    if (layer == null) { Hint("Select a layer"); return }
    val types = TransitionType.entries.map { it.name to it.name }
    PanelSection("Transitions") {
        Text("In", fontSize = 11.sp)
        LabeledSelect("Type", layer.transitionIn?.type?.name ?: "",
            listOf("" to "None") + types) { v ->
            vm.setTransitionIn(if (v.isBlank()) null else TransitionType.valueOf(v),
                layer.transitionIn?.durationMs ?: 600L)
        }
        SliderRow("In duration", (layer.transitionIn?.durationMs ?: 600L).toFloat(), 100f..3000f,
            { vm.setTransitionIn(layer.transitionIn?.type ?: TransitionType.FADE_IN, it.toLong()) })
        Text("Out", fontSize = 11.sp)
        LabeledSelect("Type", layer.transitionOut?.type?.name ?: "",
            listOf("" to "None") + types) { v ->
            vm.setTransitionOut(if (v.isBlank()) null else TransitionType.valueOf(v),
                layer.transitionOut?.durationMs ?: 600L)
        }
        SliderRow("Out duration", (layer.transitionOut?.durationMs ?: 600L).toFloat(), 100f..3000f,
            { vm.setTransitionOut(layer.transitionOut?.type ?: TransitionType.FADE_OUT, it.toLong()) })
    }
    PanelSection("Crossfade between layers") {
        if (project.layers.size < 2) { Text("Add at least two layers.", fontSize = 10.sp) }
        else {
            var from by remember { mutableStateOf(project.layers[0].id) }
            var to by remember { mutableStateOf(project.layers[1].id) }
            LabeledSelect("From", from, project.layers.map { it.id to it.name }) { from = it }
            LabeledSelect("To", to, project.layers.map { it.id to it.name }) { to = it }
            Button(onClick = { vm.addCrossfade(from, to, 600L) }, modifier = Modifier.fillMaxWidth()) {
                Text("Add crossfade", fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun ProjectPanel(state: EditorUiState, vm: EditorViewModel, project: Project) {
    PanelSection("Composition") {
        OutlinedTextField(value = project.name, onValueChange = { vm.renameProject(it) },
            modifier = Modifier.fillMaxWidth(), singleLine = true, textStyle = LocalTextStyle.current.copy(fontSize = 12.sp))
        SliderRow("Width", project.width.toFloat(), 240f..2160f,
            { vm.setProjectSettings(it.toInt(), project.height, project.fps, project.durationMs, project.background) })
        SliderRow("Height", project.height.toFloat(), 240f..3840f,
            { vm.setProjectSettings(project.width, it.toInt(), project.fps, project.durationMs, project.background) })
        LabeledSelect("Frame rate", project.fps.toString(),
            listOf("24", "30", "60").map { it to "$it fps" }) {
            vm.setProjectSettings(project.width, project.height, it.toInt(), project.durationMs, project.background)
        }
        SliderRow("Duration (s)", project.durationMs / 1000f, 1f..120f,
            { vm.setProjectSettings(project.width, project.height, project.fps, (it * 1000).toLong(), project.background) })
        ColorSwatchRow("Background", project.background) { v ->
            vm.setProjectSettings(project.width, project.height, project.fps, project.durationMs, v)
        }
    }
    PanelSection("Project files") {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = { vm.saveNow() }, modifier = Modifier.weight(1f)) { Text("Save", fontSize = 10.sp) }
            Button(onClick = { vm.recover() }, modifier = Modifier.weight(1f)) { Text("Recover", fontSize = 10.sp) }
        }
        Text("Autosave writes a versioned JSON snapshot to Room after every edit.",
            fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Hint(text: String) {
    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
