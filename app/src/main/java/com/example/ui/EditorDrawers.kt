package com.example.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.AnimationEngine
import com.example.engine.EasingPresets
import com.example.engine.ProjectTemplates
import com.example.model.*
import com.example.render.CompositionCanvasRenderer
import com.example.render.ShapePathBuilder
import com.example.ui.theme.OmsColors
import kotlin.math.roundToInt

@Composable
fun EditorDrawerHost(
    drawerMode: EditorDrawerMode,
    viewModel: MotionStudioViewModel,
    doc: ProjectDocument,
    selectedLayer: LayerData?,
    currentTime: Float,
    lang: AppLang,
    isWide: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = OmsColors.Panel,
        shape = if (isWide) RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp)
        else RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        border = BorderStroke(1.dp, OmsColors.Border),
        shadowElevation = 12.dp
    ) {
        when (drawerMode) {
            EditorDrawerMode.ADD_HUB -> AddHubDrawer(viewModel, lang)
            EditorDrawerMode.INSPECTOR_ROOT -> if (selectedLayer != null) InspectorRootDrawer(viewModel, selectedLayer, lang)
            EditorDrawerMode.MOVE_TRANSFORM -> if (selectedLayer != null) TransformDrawer(viewModel, selectedLayer, currentTime, lang)
            EditorDrawerMode.CURVE_EDITOR -> if (selectedLayer != null) CurveEditorDrawer(viewModel, selectedLayer, currentTime, lang)
            EditorDrawerMode.COLOR -> if (selectedLayer != null) ColorStudioDrawer(viewModel, selectedLayer, lang)
            EditorDrawerMode.BLEND_MASK -> if (selectedLayer != null) BlendMaskDrawer(viewModel, doc, selectedLayer, currentTime, lang)
            EditorDrawerMode.EFFECTS -> if (selectedLayer != null) EffectsDrawer(viewModel, selectedLayer, currentTime, lang)
            EditorDrawerMode.EFFECT_BROWSER -> if (selectedLayer != null) EffectBrowserDrawer(viewModel, lang)
            EditorDrawerMode.SHAPE_STUDIO -> if (selectedLayer != null) ShapeStudioDrawer(viewModel, selectedLayer, currentTime, lang)
            EditorDrawerMode.TEXT_TYPOGRAPHY -> if (selectedLayer != null) TextTypographyDrawer(viewModel, selectedLayer, lang)
            EditorDrawerMode.CAMERA_3D -> if (selectedLayer != null) Camera3DDrawer(viewModel, doc, selectedLayer, currentTime, lang)
            EditorDrawerMode.HIERARCHY -> if (selectedLayer != null) HierarchyDrawer(viewModel, doc, selectedLayer, lang)
            EditorDrawerMode.PROJECT_SETTINGS -> ProjectSettingsDrawer(viewModel, doc.project, lang)
            EditorDrawerMode.NONE, EditorDrawerMode.EXPORT_SHEET -> Unit
        }
    }
}

@Composable
fun DrawerHeader(
    title: String,
    showBackToInspector: Boolean = false,
    onBack: (() -> Unit)? = null,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(OmsColors.Panel2)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showBackToInspector && onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali", tint = OmsColors.Text, modifier = Modifier.size(18.dp))
            }
        }
        Text(
            text = title,
            color = OmsColors.Text,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
        )
        IconButton(onClick = onClose, modifier = Modifier.size(36.dp).testTag("close_drawer_btn")) {
            Icon(Icons.Filled.Close, contentDescription = "Tutup", tint = OmsColors.Muted, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun AddHubDrawer(
    viewModel: MotionStudioViewModel,
    lang: AppLang
) {
    val activeTab by viewModel.addHubTab.collectAsState()

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) viewModel.addMediaLayer(uri, isVideo = false)
    }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) viewModel.addMediaLayer(uri, isVideo = true)
    }

    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.addAudioLayer(uri)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top Tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(OmsColors.Panel2)
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val tabs = listOf(
                AddHubTab.SHAPES to OmsStrings.tr(lang, "Bentuk", "Shapes"),
                AddHubTab.MEDIA to "Media",
                AddHubTab.AUDIO to "Audio",
                AddHubTab.ELEMENTS to OmsStrings.tr(lang, "Elemen", "Elements"),
                AddHubTab.TEMPLATES to OmsStrings.tr(lang, "Templat", "Templates")
            )
            tabs.forEach { (tab, label) ->
                val sel = activeTab == tab
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (sel) OmsColors.Accent.copy(alpha = 0.14f) else Color.Transparent)
                        .clickable { viewModel.setAddHubTab(tab) }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                        color = if (sel) OmsColors.Accent else OmsColors.Muted,
                        maxLines = 1
                    )
                }
            }
            IconButton(onClick = { viewModel.openDrawer(EditorDrawerMode.NONE) }, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Tutup", tint = OmsColors.Muted, modifier = Modifier.size(18.dp))
            }
        }

        // Quick Add Bar (Vector Pen + Text)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(OmsColors.Bg2)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { viewModel.startVectorPen() },
                modifier = Modifier.weight(1f).testTag("quick_add_vector_btn"),
                contentPadding = PaddingValues(vertical = 6.dp)
            ) {
                Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp), tint = OmsColors.Accent)
                Spacer(Modifier.width(6.dp))
                Text(OmsStrings.tr(lang, "Gambar Vektor", "Vector Pen"), fontSize = 11.sp, color = OmsColors.Text)
            }
            OutlinedButton(
                onClick = { viewModel.addTextLayer() },
                modifier = Modifier.weight(1f).testTag("quick_add_text_btn"),
                contentPadding = PaddingValues(vertical = 6.dp)
            ) {
                Icon(Icons.Filled.TextFields, contentDescription = null, modifier = Modifier.size(16.dp), tint = OmsColors.Orange)
                Spacer(Modifier.width(6.dp))
                Text(OmsStrings.tr(lang, "Tambah Teks", "Add Text"), fontSize = 11.sp, color = OmsColors.Text)
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(10.dp)) {
            when (activeTab) {
                AddHubTab.SHAPES -> {
                    val shapes = remember { ShapeKind.entries.filter { it != ShapeKind.VECTOR_PATH } }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(5),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(shapes, key = { it.name }) { kind ->
                            val previewLayer = remember(kind) { LayerData(type = LayerType.SHAPE, name = kind.name, shapeKind = kind) }
                            val composePath = remember(kind) { ShapePathBuilder.buildPath(previewLayer, 64f, 64f).asComposePath() }
                            Column(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(OmsColors.Card)
                                    .border(1.dp, OmsColors.Border, RoundedCornerShape(10.dp))
                                    .clickable { viewModel.addShapeLayer(kind) }
                                    .padding(6.dp)
                                    .testTag("add_shape_${kind.name.lowercase()}"),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Canvas(modifier = Modifier.size(30.dp)) {
                                    translate(size.width * 0.5f, size.height * 0.5f) {
                                        drawPath(
                                            path = composePath,
                                            color = OmsColors.Mint
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                AddHubTab.MEDIA -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .border(1.dp, OmsColors.Border, RoundedCornerShape(14.dp))
                            .background(OmsColors.Card)
                            .padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Filled.PermMedia, contentDescription = null, tint = OmsColors.Cyan, modifier = Modifier.size(32.dp))
                        Text(OmsStrings.tr(lang, "Impor Gambar atau Video", "Import Image or Video"), fontWeight = FontWeight.Bold, color = OmsColors.Text)
                        Text(
                            OmsStrings.tr(lang, "Menggunakan Android Photo Picker tanpa izin penyimpanan luas.", "Uses zero-permission Android Photo Picker."),
                            fontSize = 11.sp,
                            color = OmsColors.Muted
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                colors = ButtonDefaults.buttonColors(containerColor = OmsColors.Accent, contentColor = Color(0xFF061B15))
                            ) {
                                Text(OmsStrings.tr(lang, "Pilih Gambar", "Pick Image"), fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = { videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) }
                            ) {
                                Text(OmsStrings.tr(lang, "Pilih Video", "Pick Video"), color = OmsColors.Text)
                            }
                        }
                    }
                }

                AddHubTab.AUDIO -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .border(1.dp, OmsColors.Border, RoundedCornerShape(14.dp))
                            .background(OmsColors.Card)
                            .padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Filled.MusicNote, contentDescription = null, tint = OmsColors.ClipAudio, modifier = Modifier.size(32.dp))
                        Text(OmsStrings.tr(lang, "Impor Trek Audio", "Import Audio Track"), fontWeight = FontWeight.Bold, color = OmsColors.Text)
                        Button(
                            onClick = { audioPicker.launch(arrayOf("audio/*")) },
                            colors = ButtonDefaults.buttonColors(containerColor = OmsColors.Purple, contentColor = Color.White)
                        ) {
                            Text(OmsStrings.tr(lang, "Pilih Audio", "Pick Audio"))
                        }
                    }
                }

                AddHubTab.ELEMENTS -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            ElementAddCard(Icons.Filled.Videocam, "Kamera 3D", OmsColors.ClipCamera) {
                                viewModel.addElementLayer(LayerType.CAMERA)
                            }
                        }
                        item {
                            ElementAddCard(Icons.Filled.ViewInAr, "3D Cube", OmsColors.ClipMesh) {
                                viewModel.addElementLayer(LayerType.MESH, MeshKind.CUBE)
                            }
                        }
                        item {
                            ElementAddCard(Icons.Filled.Public, "3D Sphere", OmsColors.Cyan) {
                                viewModel.addElementLayer(LayerType.MESH, MeshKind.SPHERE)
                            }
                        }
                        item {
                            ElementAddCard(Icons.Filled.CenterFocusStrong, "Objek Null", OmsColors.Orange) {
                                viewModel.addElementLayer(LayerType.NULL)
                            }
                        }
                        item {
                            ElementAddCard(Icons.Filled.FolderOpen, "Grup Kosong", OmsColors.Mint) {
                                viewModel.addElementLayer(LayerType.GROUP)
                            }
                        }
                    }
                }

                AddHubTab.TEMPLATES -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                        ProjectTemplates.catalog.forEach { t ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(OmsColors.Card)
                                    .border(1.dp, OmsColors.Border, RoundedCornerShape(10.dp))
                                    .clickable { viewModel.appendTemplateIntoCurrentProject(t.id) }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(if (lang == AppLang.ID) t.titleId else t.titleEn, fontWeight = FontWeight.Bold, color = OmsColors.Text)
                                    Text(if (lang == AppLang.ID) t.descId else t.descEn, fontSize = 11.sp, color = OmsColors.Muted)
                                }
                                Text(t.badge, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = OmsColors.Accent)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ElementAddCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(OmsColors.Card)
            .border(1.dp, OmsColors.Border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(icon, contentDescription = label, tint = tint)
        Text(label, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = OmsColors.Text)
    }
}

@Composable
private fun InspectorRootDrawer(
    viewModel: MotionStudioViewModel,
    layer: LayerData,
    lang: AppLang
) {
    val context = LocalContext.current
    val isMediaLayer = layer.type == LayerType.IMAGE || layer.type == LayerType.VIDEO || layer.type == LayerType.AUDIO
    val mediaMissing = isMediaLayer && !CompositionCanvasRenderer.isMediaUriAccessible(context, layer.mediaUri)

    val relinkImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) viewModel.relinkSelectedLayerMedia(uri) }

    val relinkVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) viewModel.relinkSelectedLayerMedia(uri) }

    val relinkAudioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) viewModel.relinkSelectedLayerMedia(uri) }

    Column(modifier = Modifier.fillMaxSize()) {
        DrawerHeader(
            title = layer.name,
            onClose = { viewModel.openDrawer(EditorDrawerMode.NONE) }
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Media Relink (highlighted when missing)
            if (isMediaLayer) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (mediaMissing) OmsColors.Danger.copy(alpha = 0.14f) else OmsColors.Card)
                        .border(1.dp, if (mediaMissing) OmsColors.Danger else OmsColors.Border, RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (mediaMissing) Icons.Filled.LinkOff else Icons.Filled.Link,
                        contentDescription = null,
                        tint = if (mediaMissing) OmsColors.Danger else OmsColors.Accent,
                        modifier = Modifier.size(18.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (mediaMissing) OmsStrings.tr(lang, "File Media Hilang / Perlu Relink", "Media Missing / Relink Required")
                            else OmsStrings.tr(lang, "Sumber Media Terhubung", "Media Source Connected"),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (mediaMissing) OmsColors.Danger else OmsColors.Text
                        )
                        Text(
                            text = layer.mediaUri?.takeLast(32) ?: "-",
                            fontSize = 9.sp,
                            color = OmsColors.Muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            when (layer.type) {
                                LayerType.IMAGE -> relinkImageLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                LayerType.VIDEO -> relinkVideoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                                LayerType.AUDIO -> relinkAudioLauncher.launch(arrayOf("audio/*"))
                                else -> Unit
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("relink_media_btn")
                    ) {
                        Text(OmsStrings.tr(lang, "Tautkan Ulang", "Relink"), fontSize = 10.sp)
                    }
                }
            }

            // Quick Trim & Timeline Actions
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { viewModel.trimSelectedLayerEdge(trimLeft = true) }, modifier = Modifier.weight(1f)) {
                    Text("Trim Awal", fontSize = 10.sp)
                }
                OutlinedButton(onClick = { viewModel.splitSelectedLayerAtPlayhead() }, modifier = Modifier.weight(1f)) {
                    Text("Pisah", fontSize = 10.sp)
                }
                OutlinedButton(onClick = { viewModel.trimSelectedLayerEdge(trimLeft = false) }, modifier = Modifier.weight(1f)) {
                    Text("Trim Akhir", fontSize = 10.sp)
                }
            }

            // Audio / Video Volume & Playback Speed Controls
            if (layer.type == LayerType.AUDIO || layer.type == LayerType.VIDEO) {
                KeyframeSliderRow(
                    label = OmsStrings.tr(lang, "Volume Audio", "Audio Volume"),
                    value = layer.volume,
                    range = 0f..1.5f,
                    hasKey = false,
                    onChange = { v -> viewModel.updateSelectedLayer(recordHistory = false) { it.copy(volume = v) } },
                    onToggleKey = {}
                )
                KeyframeSliderRow(
                    label = OmsStrings.tr(lang, "Kecepatan Putar (Speed)", "Playback Speed"),
                    value = layer.playbackRate,
                    range = 0.25f..2.5f,
                    hasKey = false,
                    onChange = { r -> viewModel.updateSelectedLayer(recordHistory = false) { it.copy(playbackRate = r) } },
                    onToggleKey = {}
                )
            }

            // Optical Flow Motion Tracker for Video Layers
            if (layer.type == LayerType.VIDEO) {
                Button(
                    onClick = { viewModel.startMotionTrackTargetSelection() },
                    colors = ButtonDefaults.buttonColors(containerColor = OmsColors.Purple, contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth().testTag("motion_tracker_btn")
                ) {
                    Icon(Icons.Filled.TrackChanges, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(OmsStrings.tr(lang, "Lacak Gerak Video (Motion Tracker)", "Track Video Motion"), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }

            // Grid of Inspector Modules
            val tiles = buildList {
                add(Triple("Gerak & Transform", Icons.Filled.OpenWith, EditorDrawerMode.MOVE_TRANSFORM))
                add(Triple("Warna & Isi", Icons.Filled.Palette, EditorDrawerMode.COLOR))
                add(Triple("Blending & Mask", Icons.Filled.Layers, EditorDrawerMode.BLEND_MASK))
                add(Triple("Efek (${layer.effects.size})", Icons.Filled.AutoAwesome, EditorDrawerMode.EFFECTS))
                if (layer.type == LayerType.SHAPE) add(Triple("Shape Studio", Icons.Filled.Category, EditorDrawerMode.SHAPE_STUDIO))
                if (layer.type == LayerType.TEXT) add(Triple("Tipografi Teks", Icons.Filled.TextFields, EditorDrawerMode.TEXT_TYPOGRAPHY))
                if (layer.type == LayerType.CAMERA || layer.type == LayerType.MESH) add(Triple("Kamera & 3D", Icons.Filled.ViewInAr, EditorDrawerMode.CAMERA_3D))
                add(Triple("Kurva Easing", Icons.AutoMirrored.Filled.ShowChart, EditorDrawerMode.CURVE_EDITOR))
                add(Triple("Parent / Hierarki", Icons.Filled.AccountTree, EditorDrawerMode.HIERARCHY))
            }

            tiles.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { (label, icon, mode) ->
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(OmsColors.Card)
                                .border(1.dp, OmsColors.Border, RoundedCornerShape(10.dp))
                                .clickable { viewModel.openDrawer(mode) }
                                .padding(vertical = 12.dp, horizontal = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(icon, contentDescription = label, tint = OmsColors.Accent, modifier = Modifier.size(22.dp))
                            Text(label, fontSize = 10.sp, color = OmsColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }

            // Copy / Paste Layer & Style + Export Layer JSON
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { viewModel.copySelectedLayer() },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                ) {
                    Text(OmsStrings.tr(lang, "Salin Layer", "Copy Layer"), fontSize = 10.sp, maxLines = 1)
                }
                OutlinedButton(
                    onClick = { viewModel.pasteClipboardLayer() },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                ) {
                    Text(OmsStrings.tr(lang, "Tempel Layer", "Paste Layer"), fontSize = 10.sp, maxLines = 1)
                }
                OutlinedButton(
                    onClick = { viewModel.copyStyleFromSelected() },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                ) {
                    Text(OmsStrings.tr(lang, "Salin Gaya", "Copy Style"), fontSize = 10.sp, maxLines = 1)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { viewModel.pasteStyleToSelected() },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                ) {
                    Text(OmsStrings.tr(lang, "Tempel Gaya", "Paste Style"), fontSize = 10.sp, maxLines = 1)
                }
                OutlinedButton(
                    onClick = { viewModel.exportSelectedLayersJson() },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                ) {
                    Text(OmsStrings.tr(lang, "Ekspor JSON Layer", "Export Layer JSON"), fontSize = 10.sp, maxLines = 1)
                }
            }

            // Reorder, Duplicate, Delete
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { viewModel.moveLayerOrder(layer.id, 1) }, modifier = Modifier.weight(1f)) {
                    Text("Naik", fontSize = 11.sp)
                }
                OutlinedButton(onClick = { viewModel.moveLayerOrder(layer.id, -1) }, modifier = Modifier.weight(1f)) {
                    Text("Turun", fontSize = 11.sp)
                }
                OutlinedButton(onClick = { viewModel.duplicateSelectedLayer() }, modifier = Modifier.weight(1f)) {
                    Text("Duplikat", fontSize = 11.sp)
                }
                OutlinedButton(
                    onClick = { viewModel.deleteSelectedLayer() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = OmsColors.Danger)
                ) {
                    Text("Hapus", fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
fun TransformDrawer(
    viewModel: MotionStudioViewModel,
    layer: LayerData,
    currentTime: Float,
    lang: AppLang
) {
    val xVal = AnimationEngine.evaluateProperty(layer, "x", currentTime)
    val yVal = AnimationEngine.evaluateProperty(layer, "y", currentTime)
    val zVal = AnimationEngine.evaluateProperty(layer, "z", currentTime)
    val sxVal = AnimationEngine.evaluateProperty(layer, "scaleX", currentTime)
    val syVal = AnimationEngine.evaluateProperty(layer, "scaleY", currentTime)
    val rotVal = AnimationEngine.evaluateProperty(layer, "rotation", currentTime)
    val opVal = AnimationEngine.evaluateProperty(layer, "opacity", currentTime)

    Column(modifier = Modifier.fillMaxSize()) {
        DrawerHeader(
            title = OmsStrings.tr(lang, "Gerak & Transformasi", "Move & Transform"),
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
            // Interactive XY Touch Pad
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(92.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF121623))
                    .border(1.dp, OmsColors.Border, RoundedCornerShape(12.dp))
                    .pointerInput(layer.id) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            viewModel.dragLayerOnCanvas(drag.x * 1.4f, drag.y * 1.4f)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    OmsStrings.tr(lang, "Geser di sini untuk Posisi X/Y presisi", "Drag here for fine X/Y position"),
                    fontSize = 11.sp,
                    color = OmsColors.Muted
                )
            }

            KeyframeSliderRow(
                label = "Posisi X",
                value = xVal,
                range = -600f..1800f,
                hasKey = AnimationEngine.hasKeyframeAt(layer, "x", currentTime),
                onChange = { viewModel.setLayerAnimatedProperty("x", it, recordHistory = false) },
                onToggleKey = { viewModel.toggleKeyframe("x") },
                onOpenCurve = { viewModel.openCurveEditorFor("x") }
            )
            KeyframeSliderRow(
                label = "Posisi Y",
                value = yVal,
                range = -600f..2600f,
                hasKey = AnimationEngine.hasKeyframeAt(layer, "y", currentTime),
                onChange = { viewModel.setLayerAnimatedProperty("y", it, recordHistory = false) },
                onToggleKey = { viewModel.toggleKeyframe("y") },
                onOpenCurve = { viewModel.openCurveEditorFor("y") }
            )
            KeyframeSliderRow(
                label = "Kedalaman Z",
                value = zVal,
                range = -1200f..1200f,
                hasKey = AnimationEngine.hasKeyframeAt(layer, "z", currentTime),
                onChange = { viewModel.setLayerAnimatedProperty("z", it, recordHistory = false) },
                onToggleKey = { viewModel.toggleKeyframe("z") },
                onOpenCurve = { viewModel.openCurveEditorFor("z") }
            )
            KeyframeSliderRow(
                label = "Skala X",
                value = sxVal,
                range = 0.05f..4f,
                hasKey = AnimationEngine.hasKeyframeAt(layer, "scaleX", currentTime),
                onChange = { viewModel.setLayerAnimatedProperty("scaleX", it, recordHistory = false) },
                onToggleKey = { viewModel.toggleKeyframe("scaleX") },
                onOpenCurve = { viewModel.openCurveEditorFor("scaleX") }
            )
            KeyframeSliderRow(
                label = "Skala Y",
                value = syVal,
                range = 0.05f..4f,
                hasKey = AnimationEngine.hasKeyframeAt(layer, "scaleY", currentTime),
                onChange = { viewModel.setLayerAnimatedProperty("scaleY", it, recordHistory = false) },
                onToggleKey = { viewModel.toggleKeyframe("scaleY") },
                onOpenCurve = { viewModel.openCurveEditorFor("scaleY") }
            )
            KeyframeSliderRow(
                label = "Rotasi (°)",
                value = rotVal,
                range = -720f..720f,
                hasKey = AnimationEngine.hasKeyframeAt(layer, "rotation", currentTime),
                onChange = { viewModel.setLayerAnimatedProperty("rotation", it, recordHistory = false) },
                onToggleKey = { viewModel.toggleKeyframe("rotation") },
                onOpenCurve = { viewModel.openCurveEditorFor("rotation") }
            )
            KeyframeSliderRow(
                label = "Opasitas",
                value = opVal,
                range = 0f..1f,
                hasKey = AnimationEngine.hasKeyframeAt(layer, "opacity", currentTime),
                onChange = { viewModel.setLayerAnimatedProperty("opacity", it, recordHistory = false) },
                onToggleKey = { viewModel.toggleKeyframe("opacity") },
                onOpenCurve = { viewModel.openCurveEditorFor("opacity") }
            )
        }
    }
}

@Composable
fun KeyframeSliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    hasKey: Boolean,
    onChange: (Float) -> Unit,
    onToggleKey: () -> Unit,
    onOpenCurve: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(OmsColors.Card)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 11.sp, color = OmsColors.Muted, modifier = Modifier.weight(1f))
            Text(
                text = if (range.endInclusive <= 2f) String.format("%.2f", value) else "${value.roundToInt()}",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = OmsColors.Text
            )
            if (onOpenCurve != null) {
                IconButton(onClick = onOpenCurve, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ShowChart, contentDescription = "Kurva", tint = OmsColors.Cyan, modifier = Modifier.size(16.dp))
                }
            }
            IconButton(onClick = onToggleKey, modifier = Modifier.size(30.dp)) {
                Text(
                    "◆",
                    fontSize = 16.sp,
                    color = if (hasKey) OmsColors.Pink else OmsColors.Muted
                )
            }
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.height(28.dp)
        )
    }
}

@Composable
fun CurveEditorDrawer(
    viewModel: MotionStudioViewModel,
    layer: LayerData,
    currentTime: Float,
    lang: AppLang
) {
    val activeProp by viewModel.curveProp.collectAsState()
    val props = listOf("x", "y", "z", "scaleX", "scaleY", "rotation", "opacity")

    val localT = (currentTime - layer.start).coerceAtLeast(0f)
    val keys = layer.keyframes[activeProp]?.sortedBy { it.time }.orEmpty()
    val activeSegIdx = if (keys.size >= 2) keys.indexOfLast { it.time <= localT + 0.04f }.coerceIn(0, keys.size - 2) else -1
    val currentEasing = if (activeSegIdx >= 0) keys[activeSegIdx].easing else EasingPresets.all.first().second

    var p1x by remember(activeProp, activeSegIdx, currentEasing) { mutableStateOf(currentEasing.x1) }
    var p1y by remember(activeProp, activeSegIdx, currentEasing) { mutableStateOf(currentEasing.y1) }
    var p2x by remember(activeProp, activeSegIdx, currentEasing) { mutableStateOf(currentEasing.x2) }
    var p2y by remember(activeProp, activeSegIdx, currentEasing) { mutableStateOf(currentEasing.y2) }

    Column(modifier = Modifier.fillMaxSize()) {
        DrawerHeader(
            title = "Curve Editor ($activeProp)",
            showBackToInspector = true,
            onBack = { viewModel.openDrawer(EditorDrawerMode.MOVE_TRANSFORM) },
            onClose = { viewModel.openDrawer(EditorDrawerMode.NONE) }
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                props.forEach { p ->
                    val count = layer.keyframes[p]?.size ?: 0
                    FilterChip(
                        selected = p == activeProp,
                        onClick = { viewModel.openCurveEditorFor(p) },
                        label = { Text("$p ($count)", fontSize = 11.sp) }
                    )
                }
            }

            if (keys.size < 2) {
                Text(
                    text = OmsStrings.tr(
                        lang,
                        "Tambahkan minimal 2 keyframe (◆) pada properti '$activeProp' untuk mengedit kurva easing.",
                        "Add at least 2 keyframes (◆) on '$activeProp' to edit its easing curve."
                    ),
                    color = OmsColors.Orange,
                    fontSize = 12.sp
                )
            }

            // Interactive Cubic Bezier Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0E121D))
                    .border(1.dp, OmsColors.Border, RoundedCornerShape(12.dp))
                    .pointerInput(activeProp, keys.size) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            val w = size.width.toFloat().coerceAtLeast(1f)
                            val h = size.height.toFloat().coerceAtLeast(1f)
                            val nx = (change.position.x / w).coerceIn(0f, 1f)
                            val ny = (1f - change.position.y / h).coerceIn(-0.4f, 1.4f)
                            val d1 = kotlin.math.hypot(nx - p1x, ny - p1y)
                            val d2 = kotlin.math.hypot(nx - p2x, ny - p2y)
                            if (d1 <= d2) {
                                p1x = nx; p1y = ny
                            } else {
                                p2x = nx; p2y = ny
                            }
                            viewModel.applyEasingPresetToSelectedProp(
                                activeProp,
                                EasingSpec("custom", p1x, p1y, p2x, p2y)
                            )
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize().padding(18.dp)) {
                    val w = size.width
                    val h = size.height
                    // Baseline grid
                    drawLine(OmsColors.Border, Offset(0f, h), Offset(w, 0f), strokeWidth = 1.5f)
                    drawRect(OmsColors.Border.copy(alpha = 0.5f), style = Stroke(1.5f))

                    val start = Offset(0f, h)
                    val end = Offset(w, 0f)
                    val cp1 = Offset(p1x * w, (1f - p1y) * h)
                    val cp2 = Offset(p2x * w, (1f - p2y) * h)

                    // Handle lines
                    drawLine(OmsColors.Purple, start, cp1, strokeWidth = 3f)
                    drawLine(OmsColors.Cyan, end, cp2, strokeWidth = 3f)

                    // Bezier curve
                    val path = Path().apply {
                        moveTo(start.x, start.y)
                        cubicTo(cp1.x, cp1.y, cp2.x, cp2.y, end.x, end.y)
                    }
                    drawPath(path, OmsColors.Accent, style = Stroke(width = 5f, cap = StrokeCap.Round))

                    // Control dots
                    drawCircle(OmsColors.Purple, radius = 12f, center = cp1)
                    drawCircle(Color.White, radius = 6f, center = cp1)
                    drawCircle(OmsColors.Cyan, radius = 12f, center = cp2)
                    drawCircle(Color.White, radius = 6f, center = cp2)
                }
            }

            // Preset Chips
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                EasingPresets.all.forEach { (name, spec) ->
                    FilterChip(
                        selected = currentEasing.preset == spec.preset,
                        onClick = {
                            p1x = spec.x1; p1y = spec.y1; p2x = spec.x2; p2y = spec.y2
                            viewModel.applyEasingPresetToSelectedProp(activeProp, spec)
                        },
                        label = { Text(name, fontSize = 11.sp) }
                    )
                }
            }
        }
    }
}
