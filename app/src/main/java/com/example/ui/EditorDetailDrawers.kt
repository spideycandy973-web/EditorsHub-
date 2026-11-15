package com.example.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.AnimationEngine
import com.example.model.*
import com.example.render.CompositionCanvasRenderer
import com.example.ui.theme.OmsColors
import kotlin.math.roundToInt

@Composable
fun EffectsDrawer(
    viewModel: MotionStudioViewModel,
    layer: LayerData,
    currentTime: Float,
    lang: AppLang
) {
    Column(modifier = Modifier.fillMaxSize()) {
        DrawerHeader(
            title = OmsStrings.tr(lang, "Efek Layer (${layer.effects.size})", "Layer Effects (${layer.effects.size})"),
            showBackToInspector = true,
            onBack = { viewModel.openDrawer(EditorDrawerMode.INSPECTOR_ROOT) },
            onClose = { viewModel.openDrawer(EditorDrawerMode.NONE) }
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = { viewModel.openDrawer(EditorDrawerMode.EFFECT_BROWSER) },
                colors = ButtonDefaults.buttonColors(containerColor = OmsColors.Accent, contentColor = Color(0xFF061B15)),
                modifier = Modifier.fillMaxWidth().testTag("add_effect_btn")
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(OmsStrings.tr(lang, "Tambah Efek", "Add Effect"), fontWeight = FontWeight.Bold)
            }

            if (layer.effects.isEmpty()) {
                Text(
                    text = OmsStrings.tr(lang, "Belum ada efek. Ketuk Tambah Efek untuk mulai.", "No effects applied yet."),
                    color = OmsColors.Muted,
                    fontSize = 12.sp
                )
            } else {
                layer.effects.forEach { fx ->
                    val spec = EffectsCatalog.byId(fx.specId)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(OmsColors.Card)
                            .border(1.dp, OmsColors.Border, RoundedCornerShape(12.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(
                                checked = fx.enabled,
                                onCheckedChange = { viewModel.toggleEffectEnabled(fx.id) }
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (lang == AppLang.ID) (spec?.nameId ?: fx.name) else (spec?.nameEn ?: fx.name),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = OmsColors.Text
                                )
                                Text(fx.category, fontSize = 10.sp, color = OmsColors.Accent)
                            }
                            IconButton(onClick = { viewModel.removeEffect(fx.id) }) {
                                Icon(Icons.Filled.DeleteOutline, contentDescription = "Hapus Efek", tint = OmsColors.Danger)
                            }
                        }
                        if (fx.enabled && spec != null) {
                            spec.params.forEach { param ->
                                val propKey = "effect.${fx.id}.${param.id}"
                                val curVal = AnimationEngine.evaluateProperty(layer, propKey, currentTime)
                                KeyframeSliderRow(
                                    label = if (lang == AppLang.ID) param.labelId else param.labelEn,
                                    value = curVal,
                                    range = param.min..param.max,
                                    hasKey = AnimationEngine.hasKeyframeAt(layer, propKey, currentTime),
                                    onChange = { viewModel.updateEffectParam(fx.id, param.id, it) },
                                    onToggleKey = { viewModel.toggleKeyframe(propKey) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EffectBrowserDrawer(
    viewModel: MotionStudioViewModel,
    lang: AppLang
) {
    var selectedCategory by remember { mutableStateOf("Semua") }
    var search by remember { mutableStateOf("") }

    val filtered = remember(selectedCategory, search) {
        EffectsCatalog.all.filter { spec ->
            val catOk = when (selectedCategory) {
                "Semua" -> true
                "Populer" -> spec.featured
                else -> spec.category == selectedCategory
            }
            val qOk = search.isBlank() || spec.nameId.contains(search, true) || spec.nameEn.contains(search, true)
            catOk && qOk
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        DrawerHeader(
            title = OmsStrings.tr(lang, "Katalog Efek (${EffectsCatalog.all.size})", "Effects Browser (${EffectsCatalog.all.size})"),
            showBackToInspector = true,
            onBack = { viewModel.openDrawer(EditorDrawerMode.EFFECTS) },
            onClose = { viewModel.openDrawer(EditorDrawerMode.NONE) }
        )
        Column(modifier = Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text(OmsStrings.tr(lang, "Cari efek...", "Search effects..."), fontSize = 12.sp) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                EffectsCatalog.categories.forEach { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat, fontSize = 11.sp) }
                    )
                }
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filtered, key = { it.id }) { spec ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(OmsColors.Card)
                            .border(1.dp, OmsColors.Border, RoundedCornerShape(10.dp))
                            .clickable { viewModel.addEffectToSelected(spec) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (lang == AppLang.ID) spec.nameId else spec.nameEn,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = OmsColors.Text
                            )
                            Text(
                                text = if (lang == AppLang.ID) spec.descriptionId else spec.descriptionEn,
                                fontSize = 11.sp,
                                color = OmsColors.Muted
                            )
                        }
                        Text(spec.category, fontSize = 10.sp, color = OmsColors.Accent, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun TextTypographyDrawer(
    viewModel: MotionStudioViewModel,
    layer: LayerData,
    lang: AppLang
) {
    val bundledFonts = listOf(
        "Space Grotesk", "Inter", "Montserrat", "Oswald", "Playfair Display", "Roboto Mono", "Pacifico"
    )
    val customFonts by viewModel.customFonts.collectAsState()
    val fontPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.importCustomFont(uri)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        DrawerHeader(
            title = OmsStrings.tr(lang, "Tipografi Teks", "Text Typography"),
            showBackToInspector = true,
            onBack = { viewModel.openDrawer(EditorDrawerMode.INSPECTOR_ROOT) },
            onClose = { viewModel.openDrawer(EditorDrawerMode.NONE) }
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = layer.textContent,
                onValueChange = { next ->
                    viewModel.updateSelectedLayer(recordHistory = false) {
                        it.copy(textContent = next, name = next.lineSequence().firstOrNull()?.take(18)?.ifBlank { "Teks" } ?: "Teks")
                    }
                },
                label = { Text(OmsStrings.tr(lang, "Isi Teks", "Text Content")) },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth().testTag("text_content_input")
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(OmsStrings.tr(lang, "Jenis Font", "Font Family"), fontSize = 11.sp, color = OmsColors.Muted, modifier = Modifier.weight(1f))
                OutlinedButton(
                    onClick = { fontPicker.launch(arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/octet-stream", "*/*")) },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.testTag("import_font_btn")
                ) {
                    Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.size(14.dp), tint = OmsColors.Accent)
                    Spacer(Modifier.width(4.dp))
                    Text(OmsStrings.tr(lang, "Impor .TTF/.OTF", "Import Font"), fontSize = 10.sp)
                }
            }

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                customFonts.forEach { customFam ->
                    FilterChip(
                        selected = layer.fontFamily.equals(customFam, ignoreCase = true),
                        onClick = { viewModel.updateSelectedLayer { it.copy(fontFamily = customFam) } },
                        leadingIcon = { Icon(Icons.Filled.Star, contentDescription = null, modifier = Modifier.size(12.dp), tint = OmsColors.Orange) },
                        label = { Text(customFam, fontSize = 11.sp) }
                    )
                }
                bundledFonts.forEach { family ->
                    FilterChip(
                        selected = layer.fontFamily.equals(family, ignoreCase = true),
                        onClick = { viewModel.updateSelectedLayer { it.copy(fontFamily = family) } },
                        label = { Text(family, fontSize = 11.sp) }
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = layer.fontWeight >= 650,
                    onClick = {
                        viewModel.updateSelectedLayer { it.copy(fontWeight = if (it.fontWeight >= 650) 400 else 700) }
                    },
                    label = { Text("Bold", fontWeight = FontWeight.Bold) }
                )
                FilterChip(
                    selected = layer.italic,
                    onClick = { viewModel.updateSelectedLayer { it.copy(italic = !it.italic) } },
                    label = { Text("Italic") }
                )
                Spacer(Modifier.weight(1f))
                TextAlignKind.entries.forEach { align ->
                    FilterChip(
                        selected = layer.textAlign == align,
                        onClick = { viewModel.updateSelectedLayer { it.copy(textAlign = align) } },
                        label = { Text(align.name.take(1), fontSize = 11.sp) }
                    )
                }
            }

            KeyframeSliderRow(
                label = OmsStrings.tr(lang, "Ukuran Font", "Font Size"),
                value = layer.fontSize,
                range = 14f..260f,
                hasKey = false,
                onChange = { v -> viewModel.updateSelectedLayer(recordHistory = false) { it.copy(fontSize = v) } },
                onToggleKey = {}
            )
            KeyframeSliderRow(
                label = "Tracking",
                value = layer.tracking,
                range = -10f..45f,
                hasKey = false,
                onChange = { v -> viewModel.updateSelectedLayer(recordHistory = false) { it.copy(tracking = v) } },
                onToggleKey = {}
            )
            KeyframeSliderRow(
                label = "Line Height",
                value = layer.lineHeight,
                range = 0.8f..2.4f,
                hasKey = false,
                onChange = { v -> viewModel.updateSelectedLayer(recordHistory = false) { it.copy(lineHeight = v) } },
                onToggleKey = {}
            )
        }
    }
}

@Composable
fun ShapeStudioDrawer(
    viewModel: MotionStudioViewModel,
    layer: LayerData,
    currentTime: Float,
    lang: AppLang
) {
    val radiusVal = AnimationEngine.evaluateProperty(layer, "radius", currentTime)
    val strokeVal = AnimationEngine.evaluateProperty(layer, "strokeWidth", currentTime)
    val wVal = AnimationEngine.evaluateProperty(layer, "w", currentTime)
    val hVal = AnimationEngine.evaluateProperty(layer, "h", currentTime)

    Column(modifier = Modifier.fillMaxSize()) {
        DrawerHeader(
            title = "Shape Studio",
            showBackToInspector = true,
            onBack = { viewModel.openDrawer(EditorDrawerMode.INSPECTOR_ROOT) },
            onClose = { viewModel.openDrawer(EditorDrawerMode.NONE) }
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (layer.shapeKind == ShapeKind.VECTOR_PATH) {
                Button(
                    onClick = { viewModel.startVectorPen(layer) },
                    colors = ButtonDefaults.buttonColors(containerColor = OmsColors.Accent, contentColor = Color(0xFF061B15)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(OmsStrings.tr(lang, "Edit Node Path Vektor", "Edit Vector Path Nodes"), fontWeight = FontWeight.Bold)
                }
            }

            KeyframeSliderRow(
                label = OmsStrings.tr(lang, "Lebar (W)", "Width"),
                value = wVal,
                range = 20f..1400f,
                hasKey = AnimationEngine.hasKeyframeAt(layer, "w", currentTime),
                onChange = { viewModel.setLayerAnimatedProperty("w", it, recordHistory = false) },
                onToggleKey = { viewModel.toggleKeyframe("w") }
            )
            KeyframeSliderRow(
                label = OmsStrings.tr(lang, "Tinggi (H)", "Height"),
                value = hVal,
                range = 10f..1400f,
                hasKey = AnimationEngine.hasKeyframeAt(layer, "h", currentTime),
                onChange = { viewModel.setLayerAnimatedProperty("h", it, recordHistory = false) },
                onToggleKey = { viewModel.toggleKeyframe("h") }
            )
            if (layer.shapeKind == ShapeKind.ROUND_RECT) {
                KeyframeSliderRow(
                    label = OmsStrings.tr(lang, "Radius Sudut", "Corner Radius"),
                    value = radiusVal,
                    range = 0f..220f,
                    hasKey = AnimationEngine.hasKeyframeAt(layer, "radius", currentTime),
                    onChange = { viewModel.setLayerAnimatedProperty("radius", it, recordHistory = false) },
                    onToggleKey = { viewModel.toggleKeyframe("radius") }
                )
            }
            if (layer.shapeKind == ShapeKind.STAR || layer.shapeKind == ShapeKind.BURST || layer.shapeKind == ShapeKind.GEAR || layer.shapeKind == ShapeKind.FLOWER) {
                KeyframeSliderRow(
                    label = OmsStrings.tr(lang, "Jumlah Ujung / Kelopak", "Points / Petals"),
                    value = layer.shapePoints.toFloat(),
                    range = 3f..20f,
                    hasKey = false,
                    onChange = { v -> viewModel.updateSelectedLayer(recordHistory = false) { it.copy(shapePoints = v.roundToInt()) } },
                    onToggleKey = {}
                )
                KeyframeSliderRow(
                    label = OmsStrings.tr(lang, "Radius Dalam (%)", "Inner Radius (%)"),
                    value = layer.shapeInner,
                    range = 15f..88f,
                    hasKey = false,
                    onChange = { v -> viewModel.updateSelectedLayer(recordHistory = false) { it.copy(shapeInner = v) } },
                    onToggleKey = {}
                )
            }
            if (layer.shapeKind == ShapeKind.POLYGON) {
                KeyframeSliderRow(
                    label = OmsStrings.tr(lang, "Jumlah Sisi", "Polygon Sides"),
                    value = layer.shapeSides.toFloat(),
                    range = 3f..16f,
                    hasKey = false,
                    onChange = { v -> viewModel.updateSelectedLayer(recordHistory = false) { it.copy(shapeSides = v.roundToInt()) } },
                    onToggleKey = {}
                )
            }
            KeyframeSliderRow(
                label = OmsStrings.tr(lang, "Lebar Garis Tepi (Stroke)", "Stroke Width"),
                value = strokeVal,
                range = 0f..40f,
                hasKey = AnimationEngine.hasKeyframeAt(layer, "strokeWidth", currentTime),
                onChange = { viewModel.setLayerAnimatedProperty("strokeWidth", it, recordHistory = false) },
                onToggleKey = { viewModel.toggleKeyframe("strokeWidth") }
            )

            // Dashed Stroke & Inner Glow (Layer Style)
            HorizontalDivider(color = OmsColors.Border)
            Text(OmsStrings.tr(lang, "Gaya Layer & Stroke Putus-Putus", "Layer Style & Dashed Stroke"), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = OmsColors.Accent)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(OmsColors.Card)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(OmsStrings.tr(lang, "Garis Putus-Putus (Dashed)", "Dashed Stroke"), modifier = Modifier.weight(1f), color = OmsColors.Text, fontSize = 12.sp)
                Switch(
                    checked = layer.layerStyle.dashedStroke,
                    onCheckedChange = { en -> viewModel.updateSelectedLayer { it.copy(layerStyle = it.layerStyle.copy(dashedStroke = en)) } }
                )
            }
            if (layer.layerStyle.dashedStroke) {
                KeyframeSliderRow(
                    label = OmsStrings.tr(lang, "Panjang Garis (Dash)", "Dash Length"),
                    value = layer.layerStyle.dashLength,
                    range = 4f..80f,
                    hasKey = false,
                    onChange = { v -> viewModel.updateSelectedLayer(recordHistory = false) { it.copy(layerStyle = it.layerStyle.copy(dashLength = v)) } },
                    onToggleKey = {}
                )
                KeyframeSliderRow(
                    label = OmsStrings.tr(lang, "Jarak Jeda (Gap)", "Dash Gap"),
                    value = layer.layerStyle.dashGap,
                    range = 2f..80f,
                    hasKey = false,
                    onChange = { v -> viewModel.updateSelectedLayer(recordHistory = false) { it.copy(layerStyle = it.layerStyle.copy(dashGap = v)) } },
                    onToggleKey = {}
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(OmsColors.Card)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(OmsStrings.tr(lang, "Cahaya Dalam (Inner Glow)", "Inner Glow"), modifier = Modifier.weight(1f), color = OmsColors.Text, fontSize = 12.sp)
                Switch(
                    checked = layer.layerStyle.innerGlowEnabled,
                    onCheckedChange = { en -> viewModel.updateSelectedLayer { it.copy(layerStyle = it.layerStyle.copy(innerGlowEnabled = en)) } }
                )
            }
            if (layer.layerStyle.innerGlowEnabled) {
                KeyframeSliderRow(
                    label = OmsStrings.tr(lang, "Radius Inner Glow", "Inner Glow Radius"),
                    value = layer.layerStyle.innerGlowRadius,
                    range = 2f..70f,
                    hasKey = false,
                    onChange = { v -> viewModel.updateSelectedLayer(recordHistory = false) { it.copy(layerStyle = it.layerStyle.copy(innerGlowRadius = v)) } },
                    onToggleKey = {}
                )
                KeyframeSliderRow(
                    label = OmsStrings.tr(lang, "Intensitas Inner Glow", "Inner Glow Alpha"),
                    value = layer.layerStyle.innerGlowAlpha,
                    range = 0.05f..1f,
                    hasKey = false,
                    onChange = { v -> viewModel.updateSelectedLayer(recordHistory = false) { it.copy(layerStyle = it.layerStyle.copy(innerGlowAlpha = v)) } },
                    onToggleKey = {}
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    listOf("#FFFFFF", "#00D7A7", "#43CBE8", "#FFC04A", "#FF6DA9", "#8374FF").forEach { hex ->
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(android.graphics.Color.parseColor(hex)))
                                .border(
                                    width = if (layer.layerStyle.innerGlowHex.equals(hex, true)) 2.dp else 1.dp,
                                    color = if (layer.layerStyle.innerGlowHex.equals(hex, true)) OmsColors.Accent else OmsColors.Border,
                                    shape = CircleShape
                                )
                                .clickable {
                                    viewModel.updateSelectedLayer { it.copy(layerStyle = it.layerStyle.copy(innerGlowHex = hex)) }
                                }
                        )
                    }
                }
            }

            // Repeat / Array Duplicator Section
            HorizontalDivider(color = OmsColors.Border)
            Text(OmsStrings.tr(lang, "Pengulangan (Repeat / Clone)", "Repeat / Array"), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = OmsColors.Accent)
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                RepeatModeKind.entries.forEach { rm ->
                    FilterChip(
                        selected = layer.repeat.mode == rm,
                        onClick = { viewModel.updateSelectedLayer { it.copy(repeat = it.repeat.copy(mode = rm)) } },
                        label = { Text(if (lang == AppLang.ID) rm.labelId else rm.labelEn, fontSize = 11.sp) }
                    )
                }
            }
            if (layer.repeat.mode != RepeatModeKind.NONE) {
                if (layer.repeat.mode == RepeatModeKind.GRID) {
                    KeyframeSliderRow(
                        label = OmsStrings.tr(lang, "Kolom (Cols)", "Columns"),
                        value = layer.repeat.cols.toFloat(),
                        range = 1f..8f,
                        hasKey = false,
                        onChange = { v -> viewModel.updateSelectedLayer(recordHistory = false) { it.copy(repeat = it.repeat.copy(cols = v.roundToInt())) } },
                        onToggleKey = {}
                    )
                    KeyframeSliderRow(
                        label = OmsStrings.tr(lang, "Baris (Rows)", "Rows"),
                        value = layer.repeat.rows.toFloat(),
                        range = 1f..8f,
                        hasKey = false,
                        onChange = { v -> viewModel.updateSelectedLayer(recordHistory = false) { it.copy(repeat = it.repeat.copy(rows = v.roundToInt())) } },
                        onToggleKey = {}
                    )
                } else {
                    KeyframeSliderRow(
                        label = OmsStrings.tr(lang, "Jumlah Duplikat (Count)", "Repeat Count"),
                        value = layer.repeat.count.toFloat(),
                        range = 1f..24f,
                        hasKey = false,
                        onChange = { v -> viewModel.updateSelectedLayer(recordHistory = false) { it.copy(repeat = it.repeat.copy(count = v.roundToInt())) } },
                        onToggleKey = {}
                    )
                }
                if (layer.repeat.mode == RepeatModeKind.RADIAL) {
                    KeyframeSliderRow(
                        label = OmsStrings.tr(lang, "Radius Melingkar", "Radial Radius"),
                        value = layer.repeat.radialRadius,
                        range = 20f..500f,
                        hasKey = false,
                        onChange = { v -> viewModel.updateSelectedLayer(recordHistory = false) { it.copy(repeat = it.repeat.copy(radialRadius = v)) } },
                        onToggleKey = {}
                    )
                } else {
                    KeyframeSliderRow(
                        label = "Offset X",
                        value = layer.repeat.offsetX,
                        range = -300f..300f,
                        hasKey = false,
                        onChange = { v -> viewModel.updateSelectedLayer(recordHistory = false) { it.copy(repeat = it.repeat.copy(offsetX = v)) } },
                        onToggleKey = {}
                    )
                    KeyframeSliderRow(
                        label = "Offset Y",
                        value = layer.repeat.offsetY,
                        range = -300f..300f,
                        hasKey = false,
                        onChange = { v -> viewModel.updateSelectedLayer(recordHistory = false) { it.copy(repeat = it.repeat.copy(offsetY = v)) } },
                        onToggleKey = {}
                    )
                }
                KeyframeSliderRow(
                    label = OmsStrings.tr(lang, "Rotasi per Salinan (°)", "Step Rotation (°)"),
                    value = layer.repeat.offsetRotation,
                    range = -180f..180f,
                    hasKey = false,
                    onChange = { v -> viewModel.updateSelectedLayer(recordHistory = false) { it.copy(repeat = it.repeat.copy(offsetRotation = v)) } },
                    onToggleKey = {}
                )
                KeyframeSliderRow(
                    label = OmsStrings.tr(lang, "Skala per Salinan", "Step Scale"),
                    value = layer.repeat.offsetScale,
                    range = 0.5f..1.5f,
                    hasKey = false,
                    onChange = { v -> viewModel.updateSelectedLayer(recordHistory = false) { it.copy(repeat = it.repeat.copy(offsetScale = v)) } },
                    onToggleKey = {}
                )
            }
        }
    }
}

@Composable
fun ColorStudioDrawer(
    viewModel: MotionStudioViewModel,
    layer: LayerData,
    lang: AppLang
) {
    var editingStroke by remember { mutableStateOf(false) }
    val activeHex = if (editingStroke) layer.strokeHex else layer.colorHex
    val initialInt = CompositionCanvasRenderer.parseColorSafe(activeHex)
    val hsv = remember(activeHex, editingStroke) {
        FloatArray(3).also { android.graphics.Color.colorToHSV(initialInt, it) }
    }
    var hue by remember(activeHex, editingStroke) { mutableStateOf(hsv[0]) }
    var sat by remember(activeHex, editingStroke) { mutableStateOf(hsv[1]) }
    var valV by remember(activeHex, editingStroke) { mutableStateOf(hsv[2]) }
    var hexInput by remember(activeHex, editingStroke) { mutableStateOf(activeHex.uppercase()) }

    fun applyHsv(h: Float, s: Float, v: Float) {
        val c = android.graphics.Color.HSVToColor(floatArrayOf(h, s, v))
        val hex = String.format("#%06X", 0xFFFFFF and c)
        hexInput = hex
        viewModel.updateSelectedLayer(recordHistory = false) { l ->
            if (editingStroke) l.copy(strokeHex = hex) else l.copy(colorHex = hex)
        }
    }

    val swatches = listOf(
        "#00D7A7", "#73E2BD", "#8374FF", "#43CBE8", "#FFC04A",
        "#FF6DA9", "#FF667C", "#FFFFFF", "#9BA3B8", "#10121D"
    )

    Column(modifier = Modifier.fillMaxSize()) {
        DrawerHeader(
            title = OmsStrings.tr(lang, "Warna & Isi", "Color & Fill"),
            showBackToInspector = true,
            onBack = { viewModel.openDrawer(EditorDrawerMode.INSPECTOR_ROOT) },
            onClose = { viewModel.openDrawer(EditorDrawerMode.NONE) }
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !editingStroke,
                    onClick = { editingStroke = false },
                    label = { Text(OmsStrings.tr(lang, "Warna Isi (Fill)", "Fill Color")) }
                )
                FilterChip(
                    selected = editingStroke,
                    onClick = { editingStroke = true },
                    label = { Text(OmsStrings.tr(lang, "Garis Tepi (Stroke)", "Stroke Color")) }
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, valV))))
                        .border(1.dp, OmsColors.Border, RoundedCornerShape(10.dp))
                )
                OutlinedTextField(
                    value = hexInput,
                    onValueChange = { raw ->
                        hexInput = raw
                        val cleaned = raw.trim().let { if (it.startsWith("#")) it else "#$it" }
                        if (Regex("^#[0-9A-Fa-f]{6}$").matches(cleaned)) {
                            viewModel.updateSelectedLayer(recordHistory = false) { l ->
                                if (editingStroke) l.copy(strokeHex = cleaned.uppercase()) else l.copy(colorHex = cleaned.uppercase())
                            }
                        }
                    },
                    label = { Text("Hex") },
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("hex_color_input")
                )
                OutlinedButton(
                    onClick = { viewModel.startEyedropper(if (editingStroke) "stroke" else "fill") },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                    modifier = Modifier.testTag("eyedropper_btn")
                ) {
                    Icon(Icons.Filled.Colorize, contentDescription = "Pipet", modifier = Modifier.size(16.dp), tint = OmsColors.Cyan)
                    Spacer(Modifier.width(4.dp))
                    Text(OmsStrings.tr(lang, "Pipet", "Pick"), fontSize = 11.sp)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                swatches.forEach { hex ->
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(android.graphics.Color.parseColor(hex)))
                            .border(
                                width = if (activeHex.equals(hex, true)) 2.5.dp else 1.dp,
                                color = if (activeHex.equals(hex, true)) OmsColors.Accent else OmsColors.Border,
                                shape = CircleShape
                            )
                            .clickable {
                                hexInput = hex
                                viewModel.updateSelectedLayer { l ->
                                    if (editingStroke) l.copy(strokeHex = hex) else l.copy(colorHex = hex)
                                }
                            }
                    )
                }
            }

            KeyframeSliderRow("Hue (H)", hue, 0f..360f, false, { hue = it; applyHsv(hue, sat, valV) }, {})
            KeyframeSliderRow("Saturation (S)", sat, 0f..1f, false, { sat = it; applyHsv(hue, sat, valV) }, {})
            KeyframeSliderRow("Value (V)", valV, 0f..1f, false, { valV = it; applyHsv(hue, sat, valV) }, {})
        }
    }
}

@Composable
fun BlendMaskDrawer(
    viewModel: MotionStudioViewModel,
    doc: ProjectDocument,
    layer: LayerData,
    currentTime: Float,
    lang: AppLang
) {
    val opVal = AnimationEngine.evaluateProperty(layer, "opacity", currentTime)
    val matteTargets = doc.layers.filter { it.id != layer.id && it.type != LayerType.AUDIO && it.type != LayerType.CAMERA }

    Column(modifier = Modifier.fillMaxSize()) {
        DrawerHeader(
            title = OmsStrings.tr(lang, "Blending & Masking", "Blending & Masking"),
            showBackToInspector = true,
            onBack = { viewModel.openDrawer(EditorDrawerMode.INSPECTOR_ROOT) },
            onClose = { viewModel.openDrawer(EditorDrawerMode.NONE) }
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            KeyframeSliderRow(
                label = OmsStrings.tr(lang, "Opasitas", "Opacity"),
                value = opVal,
                range = 0f..1f,
                hasKey = AnimationEngine.hasKeyframeAt(layer, "opacity", currentTime),
                onChange = { viewModel.setLayerAnimatedProperty("opacity", it, recordHistory = false) },
                onToggleKey = { viewModel.toggleKeyframe("opacity") }
            )

            Text("Blend Mode", fontSize = 11.sp, color = OmsColors.Muted)
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BlendModeKind.entries.forEach { bm ->
                    FilterChip(
                        selected = layer.blendMode == bm,
                        onClick = { viewModel.updateSelectedLayer { it.copy(blendMode = bm) } },
                        label = { Text(bm.label, fontSize = 11.sp) }
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(OmsColors.Card)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(OmsStrings.tr(lang, "Aktifkan Clipping Mask", "Enable Clipping Mask"), modifier = Modifier.weight(1f), color = OmsColors.Text)
                Switch(
                    checked = layer.mask.enabled,
                    onCheckedChange = { en -> viewModel.updateSelectedLayer { it.copy(mask = it.mask.copy(enabled = en)) } }
                )
            }

            if (layer.mask.enabled) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("rect" to "Kotak", "ellipse" to "Lingkaran").forEach { (id, lbl) ->
                        FilterChip(
                            selected = layer.mask.type == id,
                            onClick = { viewModel.updateSelectedLayer { it.copy(mask = it.mask.copy(type = id)) } },
                            label = { Text(lbl) }
                        )
                    }
                    FilterChip(
                        selected = layer.mask.invert,
                        onClick = { viewModel.updateSelectedLayer { it.copy(mask = it.mask.copy(invert = !it.mask.invert)) } },
                        label = { Text(OmsStrings.tr(lang, "Balik Mask (Invert)", "Invert Mask")) }
                    )
                }
            }

            HorizontalDivider(color = OmsColors.Border)
            Text("Track Matte (Mask / Exclude)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = OmsColors.Accent)
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MatteModeKind.entries.forEach { mm ->
                    FilterChip(
                        selected = layer.mask.matteMode == mm,
                        onClick = { viewModel.updateSelectedLayer { it.copy(mask = it.mask.copy(matteMode = mm)) } },
                        label = { Text(if (lang == AppLang.ID) mm.labelId else mm.labelEn, fontSize = 11.sp) }
                    )
                }
            }
            if (layer.mask.matteMode != MatteModeKind.NONE) {
                Text(OmsStrings.tr(lang, "Pilih Layer Sumber Matte:", "Choose Matte Source Layer:"), fontSize = 11.sp, color = OmsColors.Muted)
                if (matteTargets.isEmpty()) {
                    Text(OmsStrings.tr(lang, "Tambahkan layer lain sebagai sumber matte.", "Add another layer as matte target."), fontSize = 11.sp, color = OmsColors.Orange)
                } else {
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        matteTargets.forEach { t ->
                            FilterChip(
                                selected = layer.mask.matteTargetId == t.id,
                                onClick = { viewModel.updateSelectedLayer { it.copy(mask = it.mask.copy(matteTargetId = t.id)) } },
                                label = { Text(t.name, fontSize = 11.sp) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Camera3DDrawer(
    viewModel: MotionStudioViewModel,
    doc: ProjectDocument,
    layer: LayerData,
    currentTime: Float,
    lang: AppLang
) {
    val zVal = AnimationEngine.evaluateProperty(layer, "z", currentTime)
    val rxVal = AnimationEngine.evaluateProperty(layer, "rotX", currentTime)
    val ryVal = AnimationEngine.evaluateProperty(layer, "rotY", currentTime)
    val rzVal = AnimationEngine.evaluateProperty(layer, "rotZ", currentTime)
    val fovVal = AnimationEngine.evaluateProperty(layer, "fov", currentTime)
    val focusDistVal = AnimationEngine.evaluateProperty(layer, "focusDistance", currentTime)
    val apertureVal = AnimationEngine.evaluateProperty(layer, "aperture", currentTime)
    val orbitAzimuthVal = AnimationEngine.evaluateProperty(layer, "orbitAzimuth", currentTime)
    val orbitElevationVal = AnimationEngine.evaluateProperty(layer, "orbitElevation", currentTime)
    val orbitDistanceVal = AnimationEngine.evaluateProperty(layer, "orbitDistance", currentTime)

    val orbitTargets = doc.layers.filter { it.id != layer.id && it.type != LayerType.AUDIO && it.type != LayerType.CAMERA }

    Column(modifier = Modifier.fillMaxSize()) {
        DrawerHeader(
            title = if (layer.type == LayerType.CAMERA) "Kamera 3D" else "3D Transform & Mesh",
            showBackToInspector = true,
            onBack = { viewModel.openDrawer(EditorDrawerMode.INSPECTOR_ROOT) },
            onClose = { viewModel.openDrawer(EditorDrawerMode.NONE) }
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (layer.type == LayerType.MESH) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MeshKind.entries.forEach { mk ->
                        FilterChip(
                            selected = layer.meshKind == mk,
                            onClick = { viewModel.updateSelectedLayer { it.copy(meshKind = mk, name = mk.label) } },
                            label = { Text(mk.label) }
                        )
                    }
                }
            }

            if (layer.type == LayerType.CAMERA) {
                Text(OmsStrings.tr(lang, "Mode Lensa Kamera", "Camera Lens Mode"), fontSize = 11.sp, color = OmsColors.Muted)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CameraLensMode.entries.forEach { lm ->
                        FilterChip(
                            selected = layer.lensMode == lm,
                            onClick = { viewModel.updateSelectedLayer { it.copy(lensMode = lm) } },
                            label = { Text(lm.label, fontSize = 11.sp) }
                        )
                    }
                }

                KeyframeSliderRow(
                    label = "Field of View (FOV)",
                    value = fovVal,
                    range = 20f..130f,
                    hasKey = AnimationEngine.hasKeyframeAt(layer, "fov", currentTime),
                    onChange = { viewModel.setLayerAnimatedProperty("fov", it, recordHistory = false) },
                    onToggleKey = { viewModel.toggleKeyframe("fov") }
                )

                // Depth of Field (DoF)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(OmsColors.Card)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Depth of Field (Bokeh DoF)", modifier = Modifier.weight(1f), color = OmsColors.Text, fontSize = 12.sp)
                    Switch(
                        checked = layer.dofEnabled,
                        onCheckedChange = { en -> viewModel.updateSelectedLayer { it.copy(dofEnabled = en) } }
                    )
                }
                if (layer.dofEnabled) {
                    KeyframeSliderRow(
                        label = OmsStrings.tr(lang, "Jarak Fokus (Focus Distance)", "Focus Distance"),
                        value = focusDistVal,
                        range = 200f..3500f,
                        hasKey = AnimationEngine.hasKeyframeAt(layer, "focusDistance", currentTime),
                        onChange = { viewModel.setLayerAnimatedProperty("focusDistance", it, recordHistory = false) },
                        onToggleKey = { viewModel.toggleKeyframe("focusDistance") }
                    )
                    KeyframeSliderRow(
                        label = "Aperture (f-stop)",
                        value = apertureVal,
                        range = 1.2f..16f,
                        hasKey = AnimationEngine.hasKeyframeAt(layer, "aperture", currentTime),
                        onChange = { viewModel.setLayerAnimatedProperty("aperture", it, recordHistory = false) },
                        onToggleKey = { viewModel.toggleKeyframe("aperture") }
                    )
                }

                // Camera Orbit Rig
                HorizontalDivider(color = OmsColors.Border)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(OmsColors.Card)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(OmsStrings.tr(lang, "Kamera Orbit Target", "Camera Orbit Rig"), modifier = Modifier.weight(1f), color = OmsColors.Text, fontSize = 12.sp)
                    Switch(
                        checked = layer.orbitEnabled,
                        onCheckedChange = { en -> viewModel.updateSelectedLayer { it.copy(orbitEnabled = en) } }
                    )
                }
                if (layer.orbitEnabled) {
                    Text(OmsStrings.tr(lang, "Target Orbit:", "Orbit Target:"), fontSize = 11.sp, color = OmsColors.Muted)
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = layer.orbitTargetId == null,
                            onClick = { viewModel.updateSelectedLayer { it.copy(orbitTargetId = null) } },
                            label = { Text(OmsStrings.tr(lang, "Tengah Kanvas", "Canvas Center"), fontSize = 11.sp) }
                        )
                        orbitTargets.forEach { t ->
                            FilterChip(
                                selected = layer.orbitTargetId == t.id,
                                onClick = { viewModel.updateSelectedLayer { it.copy(orbitTargetId = t.id) } },
                                label = { Text(t.name, fontSize = 11.sp) }
                            )
                        }
                    }
                    KeyframeSliderRow(
                        label = "Orbit Azimuth (°)",
                        value = orbitAzimuthVal,
                        range = -180f..180f,
                        hasKey = AnimationEngine.hasKeyframeAt(layer, "orbitAzimuth", currentTime),
                        onChange = { viewModel.setLayerAnimatedProperty("orbitAzimuth", it, recordHistory = false) },
                        onToggleKey = { viewModel.toggleKeyframe("orbitAzimuth") }
                    )
                    KeyframeSliderRow(
                        label = "Orbit Elevation (°)",
                        value = orbitElevationVal,
                        range = -85f..85f,
                        hasKey = AnimationEngine.hasKeyframeAt(layer, "orbitElevation", currentTime),
                        onChange = { viewModel.setLayerAnimatedProperty("orbitElevation", it, recordHistory = false) },
                        onToggleKey = { viewModel.toggleKeyframe("orbitElevation") }
                    )
                    KeyframeSliderRow(
                        label = OmsStrings.tr(lang, "Jarak Orbit (Distance)", "Orbit Distance"),
                        value = orbitDistanceVal,
                        range = 200f..3500f,
                        hasKey = AnimationEngine.hasKeyframeAt(layer, "orbitDistance", currentTime),
                        onChange = { viewModel.setLayerAnimatedProperty("orbitDistance", it, recordHistory = false) },
                        onToggleKey = { viewModel.toggleKeyframe("orbitDistance") }
                    )
                }
            }

            KeyframeSliderRow(
                label = "Posisi Z (Depth)",
                value = zVal,
                range = if (layer.type == LayerType.CAMERA) 200f..3500f else -1200f..1200f,
                hasKey = AnimationEngine.hasKeyframeAt(layer, "z", currentTime),
                onChange = { viewModel.setLayerAnimatedProperty("z", it, recordHistory = false) },
                onToggleKey = { viewModel.toggleKeyframe("z") }
            )
            KeyframeSliderRow(
                label = "Rotasi X (°)",
                value = rxVal,
                range = -360f..360f,
                hasKey = AnimationEngine.hasKeyframeAt(layer, "rotX", currentTime),
                onChange = { viewModel.setLayerAnimatedProperty("rotX", it, recordHistory = false) },
                onToggleKey = { viewModel.toggleKeyframe("rotX") }
            )
            KeyframeSliderRow(
                label = "Rotasi Y (°)",
                value = ryVal,
                range = -360f..360f,
                hasKey = AnimationEngine.hasKeyframeAt(layer, "rotY", currentTime),
                onChange = { viewModel.setLayerAnimatedProperty("rotY", it, recordHistory = false) },
                onToggleKey = { viewModel.toggleKeyframe("rotY") }
            )
            KeyframeSliderRow(
                label = "Rotasi Z (°)",
                value = rzVal,
                range = -360f..360f,
                hasKey = AnimationEngine.hasKeyframeAt(layer, "rotZ", currentTime),
                onChange = { viewModel.setLayerAnimatedProperty("rotZ", it, recordHistory = false) },
                onToggleKey = { viewModel.toggleKeyframe("rotZ") }
            )
        }
    }
}

@Composable
fun HierarchyDrawer(
    viewModel: MotionStudioViewModel,
    doc: ProjectDocument,
    layer: LayerData,
    lang: AppLang
) {
    val candidates = doc.layers.filter { it.id != layer.id && it.type != LayerType.AUDIO && it.type != LayerType.CAMERA }
    Column(modifier = Modifier.fillMaxSize()) {
        DrawerHeader(
            title = OmsStrings.tr(lang, "Hierarki & Parenting", "Parent & Hierarchy"),
            showBackToInspector = true,
            onBack = { viewModel.openDrawer(EditorDrawerMode.INSPECTOR_ROOT) },
            onClose = { viewModel.openDrawer(EditorDrawerMode.NONE) }
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                OmsStrings.tr(lang, "Pilih layer induk (parent) agar posisi, skala, dan rotasi mengikuti induk:", "Choose a parent layer so transforms inherit from it:"),
                fontSize = 11.sp,
                color = OmsColors.Muted
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (layer.parentId == null) OmsColors.Accent.copy(alpha = 0.16f) else OmsColors.Card)
                    .border(1.dp, if (layer.parentId == null) OmsColors.Accent else OmsColors.Border, RoundedCornerShape(10.dp))
                    .clickable { viewModel.updateSelectedLayer { it.copy(parentId = null) } }
                    .padding(12.dp)
            ) {
                Text(OmsStrings.tr(lang, "Tanpa Induk (World Space)", "None (World Space)"), color = OmsColors.Text, fontWeight = FontWeight.Bold)
            }
            candidates.forEach { cand ->
                val sel = layer.parentId == cand.id
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (sel) OmsColors.Accent.copy(alpha = 0.16f) else OmsColors.Card)
                        .border(1.dp, if (sel) OmsColors.Accent else OmsColors.Border, RoundedCornerShape(10.dp))
                        .clickable { viewModel.updateSelectedLayer { it.copy(parentId = cand.id) } }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(cand.name, color = OmsColors.Text, modifier = Modifier.weight(1f))
                    Text(cand.type.name, fontSize = 10.sp, color = OmsColors.Muted)
                }
            }
        }
    }
}

@Composable
fun ProjectSettingsDrawer(
    viewModel: MotionStudioViewModel,
    project: ProjectData,
    lang: AppLang
) {
    var durText by remember(project.duration) { mutableStateOf("${project.duration.roundToInt()}") }
    Column(modifier = Modifier.fillMaxSize()) {
        DrawerHeader(
            title = OmsStrings.tr(lang, "Pengaturan Proyek", "Project Settings"),
            onClose = { viewModel.openDrawer(EditorDrawerMode.NONE) }
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(OmsStrings.tr(lang, "Grid Kanvas", "Canvas Grid"), modifier = Modifier.weight(1f), color = OmsColors.Text)
                Switch(
                    checked = project.gridEnabled,
                    onCheckedChange = { viewModel.updateProjectSettings(gridEnabled = it) }
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = durText,
                    onValueChange = { durText = it.filter(Char::isDigit) },
                    label = { Text(OmsStrings.durationLabel(lang)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = {
                    val d = durText.toFloatOrNull() ?: project.duration
                    viewModel.updateProjectSettings(duration = d)
                }) {
                    Text("Terapkan")
                }
            }
            Text("FPS", fontSize = 11.sp, color = OmsColors.Muted)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AspectRatioPresets.fpsOptions.forEach { f ->
                    FilterChip(
                        selected = project.fps == f,
                        onClick = { viewModel.updateProjectSettings(fps = f) },
                        label = { Text("$f fps") }
                    )
                }
            }
            Text(OmsStrings.backgroundLabel(lang), fontSize = 11.sp, color = OmsColors.Muted)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("#10121D", "#000000", "#FFFFFF", "#1B1330", "#0A2220").forEach { hex ->
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(android.graphics.Color.parseColor(hex)))
                            .border(
                                width = if (project.bgHex.equals(hex, true)) 2.5.dp else 1.dp,
                                color = if (project.bgHex.equals(hex, true)) OmsColors.Accent else OmsColors.Border,
                                shape = CircleShape
                            )
                            .clickable { viewModel.updateProjectSettings(bgHex = hex) }
                    )
                }
            }
        }
    }
}

@Composable
fun ExportSheetOverlay(
    viewModel: MotionStudioViewModel,
    doc: ProjectDocument,
    lang: AppLang,
    onClose: () -> Unit
) {
    val exportUi by viewModel.exportUi.collectAsState()
    var selectedFormat by remember { mutableStateOf("mp4") }
    var resShort by remember { mutableStateOf(720) }
    var fps by remember { mutableStateOf(doc.project.fps) }
    var bitrateMbps by remember { mutableStateOf(8f) }

    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(exportUi.lastMimeType ?: "*/*")
    ) { uri ->
        if (uri != null) viewModel.saveLastExportToUri(uri)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = OmsColors.Bg
    ) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .background(OmsColors.Panel)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose, enabled = !exportUi.isExporting) {
                    Icon(Icons.Filled.Close, contentDescription = "Tutup", tint = OmsColors.Text)
                }
                Text(
                    text = OmsStrings.exportTitle(lang),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ExportFormatOptionCard(
                    selected = selectedFormat == "mp4",
                    title = "Video MP4 (H.264 + AAC Audio)",
                    subtitle = OmsStrings.tr(lang, "Animasi penuh & audio campuran dengan MediaCodec hardware encoder", "Full animation + mixed AAC audio via native MediaCodec"),
                    onClick = { selectedFormat = "mp4" }
                ) {
                    Text(OmsStrings.resolutionLabel(lang), fontSize = 11.sp, color = OmsColors.Muted)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(480, 720, 1080).forEach { r ->
                            FilterChip(selected = resShort == r, onClick = { resShort = r }, label = { Text("${r}p") })
                        }
                    }
                    Text(OmsStrings.fpsLabel(lang), fontSize = 11.sp, color = OmsColors.Muted)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(24, 30, 60).forEach { f ->
                            FilterChip(selected = fps == f, onClick = { fps = f }, label = { Text("$f fps") })
                        }
                    }
                    Text("Bitrate: ${bitrateMbps.roundToInt()} Mbps", fontSize = 11.sp, color = OmsColors.Muted)
                    Slider(value = bitrateMbps, onValueChange = { bitrateMbps = it }, valueRange = 2f..16f)
                }

                ExportFormatOptionCard(
                    selected = selectedFormat == "png",
                    title = OmsStrings.tr(lang, "Frame Saat Ini (PNG)", "Current Frame (PNG)"),
                    subtitle = OmsStrings.tr(lang, "Simpan frame aktif pada resolusi penuh", "Save active playhead frame at full resolution"),
                    onClick = { selectedFormat = "png" }
                )

                ExportFormatOptionCard(
                    selected = selectedFormat == "amproj",
                    title = OmsStrings.tr(lang, "Paket Proyek Lengkap (.amproj ZIP)", "Full Project Bundle (.amproj ZIP)"),
                    subtitle = OmsStrings.tr(lang, "Berisi project.json, scene.xml, dan media tertanam", "Bundles project.json, scene.xml, and linked media assets"),
                    onClick = { selectedFormat = "amproj" }
                )

                ExportFormatOptionCard(
                    selected = selectedFormat == "json",
                    title = OmsStrings.tr(lang, "Paket Proyek (JSON)", "Project Package (JSON)"),
                    subtitle = OmsStrings.tr(lang, "Cadangan proyek lengkap untuk dibuka lagi", "Complete portable Open Motion project file"),
                    onClick = { selectedFormat = "json" }
                )

                ExportFormatOptionCard(
                    selected = selectedFormat == "xml",
                    title = "XML Interchange",
                    subtitle = OmsStrings.tr(lang, "Kompatibilitas struktur Alight Motion + ekstensi 3D", "Alight Motion XML structure with 3D extensions"),
                    onClick = { selectedFormat = "xml" }
                )

                if (exportUi.isExporting) {
                    LinearProgressIndicator(
                        progress = { exportUi.progress },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = OmsColors.Accent
                    )
                }
                exportUi.statusMessage?.let { msg ->
                    Text(msg, color = OmsColors.Mint, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }

                if (!exportUi.isExporting && exportUi.lastFile != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(OmsColors.Panel)
                            .border(1.dp, OmsColors.Accent.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val suggested = exportUi.lastFile?.name ?: "openmotion_export"
                                saveLauncher.launch(suggested)
                            },
                            modifier = Modifier.weight(1f).testTag("save_export_device_btn")
                        ) {
                            Icon(Icons.Filled.SaveAlt, contentDescription = null, modifier = Modifier.size(16.dp), tint = OmsColors.Accent)
                            Spacer(Modifier.width(6.dp))
                            Text(OmsStrings.tr(lang, "Simpan ke Perangkat", "Save to Device"), fontSize = 11.sp)
                        }
                        Button(
                            onClick = { viewModel.shareLastExport() },
                            colors = ButtonDefaults.buttonColors(containerColor = OmsColors.Purple, contentColor = Color.White),
                            modifier = Modifier.weight(1f).testTag("share_export_btn")
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(OmsStrings.tr(lang, "Bagikan (Share)", "Share"), fontSize = 11.sp)
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(OmsColors.Panel)
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (exportUi.isExporting) {
                    OutlinedButton(
                        onClick = { viewModel.cancelExport() },
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text(OmsStrings.tr(lang, "Batalkan Ekspor", "Cancel Export"), color = OmsColors.Danger)
                    }
                } else {
                    Button(
                        onClick = {
                            when (selectedFormat) {
                                "mp4" -> viewModel.exportMp4(resShort, fps, bitrateMbps.roundToInt())
                                "png" -> viewModel.exportPng()
                                "amproj" -> viewModel.exportAmproj()
                                "json" -> viewModel.exportJson()
                                "xml" -> viewModel.exportXml()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = OmsColors.Accent, contentColor = Color(0xFF061B15)),
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("execute_export_btn")
                    ) {
                        Text(
                            text = when (selectedFormat) {
                                "mp4" -> OmsStrings.tr(lang, "Ekspor Video MP4", "Export MP4 Video")
                                "png" -> OmsStrings.tr(lang, "Ekspor PNG", "Export PNG")
                                "amproj" -> OmsStrings.tr(lang, "Ekspor Paket .amproj (ZIP)", "Export .amproj (ZIP)")
                                "json" -> OmsStrings.tr(lang, "Ekspor Paket JSON", "Export JSON Package")
                                else -> OmsStrings.tr(lang, "Ekspor XML", "Export XML")
                            },
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExportFormatOptionCard(
    selected: Boolean,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    extraContent: (@Composable ColumnScope.() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(OmsColors.Card)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) OmsColors.Accent else OmsColors.Border,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RadioButton(selected = selected, onClick = onClick)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, color = OmsColors.Text, fontSize = 14.sp)
                Text(subtitle, color = OmsColors.Muted, fontSize = 11.sp)
            }
        }
        if (selected && extraContent != null) {
            extraContent()
        }
    }
}
