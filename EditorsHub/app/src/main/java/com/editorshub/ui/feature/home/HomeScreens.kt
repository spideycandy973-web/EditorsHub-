package com.editorshub.ui.feature.home

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.editorshub.ui.feature.importer.ImportResultDialog
import com.editorshub.ui.feature.importer.ImportViewModel
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: HomeViewModel = hiltViewModel(),
    importVm: ImportViewModel = hiltViewModel(),
    onOpenProject: (String) -> Unit,
    onSettings: () -> Unit,
    onProjects: () -> Unit,
    createRequest: Int = 0
) {
    val settings by vm.appSettings.collectAsState()
    val projects by vm.projects.collectAsState()
    val importState by importVm.ui.collectAsState()
    val recent = remember(projects) { projects.sortedByDescending { it.updatedAt }.take(4) }
    var showCreate by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var packageUrl by remember { mutableStateOf("") }
    var packageBusy by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.openProject.collect { onOpenProject(it) } }
    LaunchedEffect(Unit) { vm.message.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(createRequest) { if (createRequest > 0) showCreate = true }

    val xmlPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importVm.start(uri, displayName(context, uri))
    }
    val qrPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            packageBusy = true
            try {
                val link = decodeQrImage(context, uri)
                packageUrl = link
                vm.importPackageLink(link).join()
            } catch (error: Exception) {
                snackbar.showSnackbar("Could not read project QR image: ${error.message ?: "invalid QR code"}")
            } finally {
                packageBusy = false
            }
        }
    }

    val importPackage: () -> Unit = {
        val link = packageUrl.trim()
        if (link.isBlank()) {
            scope.launch { snackbar.showSnackbar("Paste a project package link first") }
        } else {
            scope.launch {
                packageBusy = true
                try {
                    vm.importPackageLink(link).join()
                } finally {
                    packageBusy = false
                }
            }
        }
    }

    Scaffold(
        containerColor = ReferencePalette.Background,
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize()
                .background(ReferencePalette.Background)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 24.dp)
        ) {
            ReferenceHomeHeader(
                brand = settings.brandName,
                subtitle = settings.brandSubtitle,
                onSettings = onSettings,
                onAbout = { showAbout = true }
            )
            Spacer(Modifier.height(8.dp))
            ReferenceUploadCard(
                busy = importState.running,
                onClick = { xmlPicker.launch(arrayOf("application/xml", "text/xml", "*/*")) }
            )
            Spacer(Modifier.height(14.dp))
            ReferencePackageCard(
                value = packageUrl,
                onValueChange = { packageUrl = it },
                busy = packageBusy,
                onImport = importPackage,
                onScan = { qrPicker.launch(arrayOf("image/*")) }
            )

            if (recent.isNotEmpty()) {
                Spacer(Modifier.height(25.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Recent projects", Modifier.weight(1f), fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold, color = ReferencePalette.Text)
                    TextButton(onClick = onProjects) {
                        Text("View all", color = ReferencePalette.Mint, fontSize = 12.sp)
                        Icon(Icons.Default.ChevronRight, null, Modifier.size(16.dp), tint = ReferencePalette.Mint)
                    }
                }
                recent.forEach { project ->
                    ReferenceRecentProject(project, vm) { onOpenProject(project.id) }
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }

    if (showCreate) {
        ProjectCreationSheet(vm, onDismiss = { showCreate = false }, onCreated = { showCreate = false })
    }
    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            containerColor = ReferencePalette.Card,
            title = { Text(settings.brandName.ifBlank { "ULTRA MOTION" }, color = ReferencePalette.Text) },
            text = {
                Text(
                    "${settings.brandSubtitle}\n\nNative motion-editing studio · EditorsHub",
                    color = ReferencePalette.SecondaryText
                )
            },
            confirmButton = {
                TextButton(onClick = { showAbout = false; onSettings() }) {
                    Text("Settings", color = ReferencePalette.Mint)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAbout = false }) {
                    Text("Close", color = ReferencePalette.SecondaryText)
                }
            }
        )
    }
    importState.result?.let { result ->
        ImportResultDialog(
            result = result,
            onOpenProject = { id -> importVm.clear(); onOpenProject(id) },
            onDismiss = { importVm.clear() }
        )
    }
}

@Composable
private fun ReferenceHomeHeader(
    brand: String,
    subtitle: String,
    onSettings: () -> Unit,
    onAbout: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().height(52.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(44.dp).clickable(
                role = androidx.compose.ui.semantics.Role.Button,
                onClick = onSettings
            ),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.Settings, "Settings", Modifier.size(23.dp), tint = ReferencePalette.Text)
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            Text(
                brand.ifBlank { "ULTRA MOTION" }, fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold, letterSpacing = 1.8.sp,
                color = ReferencePalette.Text, maxLines = 1
            )
            Text(
                subtitle.ifBlank { "ultramotionofficial.com" }, fontSize = 8.sp,
                fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp,
                color = ReferencePalette.Text, maxLines = 1
            )
        }
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(ReferencePalette.Avatar)
                .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onAbout),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.PersonOutline, "About", Modifier.size(21.dp), tint = Color(0xFFC9C9D2))
        }
    }
}

@Composable
private fun ReferenceUploadCard(busy: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(96.dp),
        shape = RoundedCornerShape(20.dp),
        color = ReferencePalette.Card
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ReferenceXmlGlyph(Modifier.size(34.dp))
            Spacer(Modifier.width(16.dp))
            Text(
                "Upload XML file", Modifier.weight(1f), fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold, color = ReferencePalette.Text, maxLines = 1
            )
            ReferenceButton(
                text = if (busy) "Importing…" else "Upload",
                modifier = Modifier.width(83.dp).height(41.dp),
                background = ReferencePalette.Lavender,
                foreground = Color(0xFF111116),
                enabled = !busy,
                onClick = onClick
            )
        }
    }
}

@Composable
private fun ReferencePackageCard(
    value: String,
    onValueChange: (String) -> Unit,
    busy: Boolean,
    onImport: () -> Unit,
    onScan: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(235.dp),
        shape = RoundedCornerShape(20.dp),
        color = ReferencePalette.Card
    ) {
        Column(
            Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 25.dp)
        ) {
            Row(Modifier.fillMaxWidth().height(36.dp), verticalAlignment = Alignment.CenterVertically) {
                ReferencePackageGlyph(Modifier.size(30.dp))
                Spacer(Modifier.width(18.dp))
                Column(verticalArrangement = Arrangement.Center) {
                    Text("Import Project Package", fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold, color = ReferencePalette.Text, maxLines = 1)
                    Text("Paste a link or scan the QR code.", fontSize = 10.sp,
                        color = ReferencePalette.MutedText, maxLines = 1)
                }
            }
            Spacer(Modifier.height(12.dp))
            ReferencePackageUrl(value, onValueChange)
            Spacer(Modifier.height(18.dp))
            ReferenceButton(
                text = if (busy) "Importing…" else "Import",
                modifier = Modifier.fillMaxWidth().height(41.dp),
                background = ReferencePalette.Lavender,
                foreground = Color(0xFF111116),
                enabled = !busy,
                onClick = onImport
            )
            Spacer(Modifier.height(1.dp))
            ReferenceButton(
                text = "Scan QR image",
                icon = { Icon(Icons.Outlined.QrCodeScanner, null, Modifier.size(17.dp)) },
                modifier = Modifier.fillMaxWidth().height(42.dp),
                background = ReferencePalette.Input,
                foreground = ReferencePalette.Mint,
                enabled = !busy,
                onClick = onScan
            )
        }
    }
}

@Composable
private fun ReferencePackageUrl(value: String, onValueChange: (String) -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(14.dp))
            .background(ReferencePalette.Input).padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (value.isEmpty()) {
            Text("https://alight...", fontSize = 13.sp, color = ReferencePalette.MutedText)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(
                color = ReferencePalette.Text,
                fontSize = 13.sp
            ),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri,
                imeAction = androidx.compose.ui.text.input.ImeAction.Done
            )
        )
    }
}

@Composable
private fun ReferenceSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String
) {
    Row(
        Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(10.dp))
            .background(ReferencePalette.Input).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Search, null, Modifier.size(16.dp), tint = ReferencePalette.MutedText)
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Text(placeholder, fontSize = 12.sp, color = ReferencePalette.MutedText)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = ReferencePalette.Text, fontSize = 12.sp
                )
            )
        }
        if (value.isNotEmpty()) {
            Box(
                Modifier.size(28.dp).clickable(onClick = { onValueChange("") }),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Close, "Clear search", Modifier.size(16.dp), tint = ReferencePalette.SecondaryText)
            }
        }
    }
}

@Composable
private fun ReferenceButton(
    text: String,
    modifier: Modifier = Modifier,
    background: Color,
    foreground: Color,
    enabled: Boolean = true,
    icon: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Row(
        modifier.pressScale(interaction, 0.97f)
            .clip(RoundedCornerShape(13.dp))
            .background(if (enabled) background else background.copy(alpha = 0.58f))
            .clickable(
                interactionSource = interaction,
                indication = androidx.compose.foundation.LocalIndication.current,
                enabled = enabled,
                role = androidx.compose.ui.semantics.Role.Button,
                onClick = onClick
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            icon()
            Spacer(Modifier.width(8.dp))
        }
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = foreground, maxLines = 1)
    }
}

@Composable
private fun ReferenceXmlGlyph(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val scale = size.width / 34f
        val stroke = 1.7f * scale
        val white = ReferencePalette.Text
        val back = androidx.compose.ui.geometry.Offset(4f * scale, 9f * scale)
        val rect = androidx.compose.ui.geometry.Size(22f * scale, 18f * scale)
        drawRoundRect(white, topLeft = back, size = rect,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(1f * scale), style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
        drawRoundRect(white, topLeft = androidx.compose.ui.geometry.Offset(9f * scale, 2.5f * scale),
            size = androidx.compose.ui.geometry.Size(21f * scale, 17f * scale),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(1f * scale), style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
        val mountain = Path().apply {
            moveTo(11f * scale, 17f * scale)
            lineTo(16f * scale, 12f * scale)
            lineTo(19.5f * scale, 15.5f * scale)
            lineTo(23.5f * scale, 11.5f * scale)
            lineTo(29f * scale, 17f * scale)
        }
        drawPath(mountain, white, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        drawCircle(white, radius = 1.6f * scale, center = androidx.compose.ui.geometry.Offset(24.5f * scale, 8f * scale))
        val arrow = Path().apply {
            moveTo(16f * scale, 29f * scale)
            lineTo(16f * scale, 15f * scale)
            moveTo(16f * scale, 15f * scale)
            lineTo(11f * scale, 20f * scale)
            moveTo(16f * scale, 15f * scale)
            lineTo(21f * scale, 20f * scale)
        }
        drawPath(arrow, Color(0xFFFFCE5C), style = androidx.compose.ui.graphics.drawscope.Stroke(
            width = 4f * scale, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round
        ))
    }
}

@Composable
private fun ReferencePackageGlyph(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val scale = size.width / 30f
        val white = ReferencePalette.Text
        val stroke = 1.65f * scale
        val hex = Path().apply {
            moveTo(15f * scale, 1.6f * scale)
            lineTo(26.5f * scale, 8.3f * scale)
            lineTo(26.5f * scale, 21.7f * scale)
            lineTo(15f * scale, 28.4f * scale)
            lineTo(3.5f * scale, 21.7f * scale)
            lineTo(3.5f * scale, 8.3f * scale)
            close()
        }
        drawPath(hex, white, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        val cube = Path().apply {
            moveTo(15f * scale, 8.2f * scale)
            lineTo(21f * scale, 11.6f * scale)
            lineTo(21f * scale, 18.4f * scale)
            lineTo(15f * scale, 21.8f * scale)
            lineTo(9f * scale, 18.4f * scale)
            lineTo(9f * scale, 11.6f * scale)
            close()
        }
        drawPath(cube, white.copy(alpha = 0.2f))
        drawPath(cube, white, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke * 0.9f, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        drawLine(white, androidx.compose.ui.geometry.Offset(15f * scale, 8.2f * scale),
            androidx.compose.ui.geometry.Offset(15f * scale, 15f * scale), strokeWidth = stroke * 0.9f)
        drawLine(white, androidx.compose.ui.geometry.Offset(15f * scale, 15f * scale),
            androidx.compose.ui.geometry.Offset(21f * scale, 11.6f * scale), strokeWidth = stroke * 0.9f)
        drawLine(white, androidx.compose.ui.geometry.Offset(15f * scale, 15f * scale),
            androidx.compose.ui.geometry.Offset(9f * scale, 11.6f * scale), strokeWidth = stroke * 0.9f)
    }
}

@Composable
private fun ReferenceRecentProject(project: ProjectSummary, vm: HomeViewModel, onClick: () -> Unit) {
    val interaction = remember(project.id) { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Row(
        Modifier.fillMaxWidth().height(70.dp).clip(RoundedCornerShape(14.dp))
            .background(ReferencePalette.Card)
            .clickable(interactionSource = interaction, indication = androidx.compose.foundation.LocalIndication.current,
                onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(ReferencePalette.Input),
            contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Movie, null, Modifier.size(21.dp), tint = ReferencePalette.Lavender)
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(project.name, color = ReferencePalette.Text, fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text("${project.width} × ${project.height} · ${vm.formatDuration(project.durationMs)}",
                color = ReferencePalette.MutedText, fontSize = 10.sp, maxLines = 1)
        }
        Icon(Icons.Default.ChevronRight, null, Modifier.size(18.dp), tint = ReferencePalette.MutedText)
    }
}

private fun displayName(context: android.content.Context, uri: Uri): String = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (cursor.moveToFirst() && column >= 0) cursor.getString(column) else null
    }
}.getOrNull()?.takeIf { it.isNotBlank() } ?: (uri.lastPathSegment ?: "project.xml")

private suspend fun decodeQrImage(context: android.content.Context, uri: Uri): String = withContext(Dispatchers.IO) {
    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
        ?: error("The selected image could not be opened")
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) error("The selected file is not a readable image")
    val options = android.graphics.BitmapFactory.Options()
    val maxDimension = 2048
    var sample = 1
    while (bounds.outWidth / sample > maxDimension || bounds.outHeight / sample > maxDimension) sample *= 2
    options.inSampleSize = sample
    val bitmap = context.contentResolver.openInputStream(uri)?.use {
        android.graphics.BitmapFactory.decodeStream(it, null, options)
    } ?: error("The selected image could not be decoded")
    try {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val source = com.google.zxing.RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
        val hints = hashMapOf<com.google.zxing.DecodeHintType, Any>(
            com.google.zxing.DecodeHintType.POSSIBLE_FORMATS to listOf(com.google.zxing.BarcodeFormat.QR_CODE),
            com.google.zxing.DecodeHintType.TRY_HARDER to true
        )
        val link = com.google.zxing.MultiFormatReader().decode(
            com.google.zxing.BinaryBitmap(com.google.zxing.common.HybridBinarizer(source)), hints
        ).text.trim()
        val parsed = runCatching { java.net.URI(link) }.getOrNull()
        if (parsed?.scheme?.equals("https", ignoreCase = true) != true || parsed.host.isNullOrBlank()) {
            error("QR code must contain an HTTPS project link")
        }
        link
    } finally {
        bitmap.recycle()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProjectCard(
    summary: ProjectSummary,
    vm: HomeViewModel,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onSelect: () -> Unit,
    onAction: () -> Unit
) {
    val interaction = remember(summary.id) { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Card(Modifier.fillMaxWidth().pressScale(interaction, 0.98f)
        .combinedClickable(
            interactionSource = interaction,
            indication = androidx.compose.foundation.LocalIndication.current,
            onClick = { if (selectionMode) onSelect() else onClick() },
            onLongClick = onSelect
        ),
        colors = CardDefaults.cardColors(containerColor = ReferencePalette.Card)) {
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
            if (selectionMode) {
                IconButton(onClick = onSelect, modifier = Modifier.align(Alignment.TopEnd).size(34.dp)) {
                    Icon(
                        if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        if (selected) "Selected ${summary.name}" else "Select ${summary.name}",
                        modifier = Modifier.size(20.dp),
                        tint = if (selected) ReferencePalette.Mint else Color.White
                    )
                }
            } else {
                IconButton(onClick = onAction, modifier = Modifier.align(Alignment.TopEnd).size(30.dp)) {
                    Icon(Icons.Default.MoreVert, "Actions", modifier = Modifier.size(16.dp), tint = Color.White)
                }
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
        modifier.fillMaxHeight().clip(RoundedCornerShape(7.dp))
            .background(if (selected) Color(0xFF414147) else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 11.sp, maxLines = 1,
            color = if (selected) ReferencePalette.Text else ReferencePalette.MutedText,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium)
    }
}

// --------------------------------------------------------------- PROJECTS
@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class, ExperimentalFoundationApi::class)
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
    var actionTarget by remember { mutableStateOf<ProjectSummary?>(null) }
    var deleteTarget by remember { mutableStateOf<ProjectSummary?>(null) }
    var infoTarget by remember { mutableStateOf<ProjectSummary?>(null) }
    var exportTarget by remember { mutableStateOf<ProjectSummary?>(null) }
    var renameTarget by remember { mutableStateOf<ProjectSummary?>(null) }
    var selectedIds by remember { mutableStateOf(emptySet<String>()) }
    var confirmDeleteSelection by remember { mutableStateOf(false) }
    val selectionMode = selectedIds.isNotEmpty()
    var sortExpanded by remember { mutableStateOf(false) }
    var actionsExpanded by remember { mutableStateOf(false) }
    var aboutExpanded by remember { mutableStateOf(false) }

    val dateLabels = remember(visible, sort) {
        visible.map { project ->
            if (sort == ProjectSort.DATE) {
                java.text.SimpleDateFormat("d MMMM yyyy", java.util.Locale.UK)
                    .format(java.util.Date(project.updatedAt))
            } else null
        }
    }
    val sortLabel = when (sort) {
        ProjectSort.DATE -> "Date"
        ProjectSort.NAME -> "Name"
        ProjectSort.DURATION -> "Duration"
        ProjectSort.SIZE -> "Size"
    }

    Scaffold(containerColor = ReferencePalette.Background) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().background(ReferencePalette.Background)
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 22.dp)) {
                ReferenceHomeHeader(
                    settings.brandName, settings.brandSubtitle,
                    onSettings = onSettings,
                    onAbout = { aboutExpanded = true }
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(10.dp))
                        .background(ReferencePalette.Input).padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ProjectSectionButton("Projects", true, Modifier.weight(1f)) { }
                    ProjectSectionButton("Your Templates", false, Modifier.weight(1.2f), onTemplates)
                    ProjectSectionButton("Effects", false, Modifier.weight(1f), onEffects)
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 11.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (selectionMode) "${selectedIds.size} selected" else "Your projects",
                        Modifier.weight(1f), fontSize = 13.sp,
                        fontWeight = FontWeight.Bold, color = ReferencePalette.Text
                    )
                    if (selectionMode) {
                        TextButton(onClick = { selectedIds = emptySet() },
                            contentPadding = PaddingValues(horizontal = 4.dp)) {
                            Text("Cancel", fontSize = 11.sp, color = ReferencePalette.Mint)
                        }
                    } else {
                        Box {
                            TextButton(onClick = { sortExpanded = true },
                                contentPadding = PaddingValues(horizontal = 4.dp)) {
                                Text("Sort by $sortLabel", fontSize = 11.sp, color = ReferencePalette.SecondaryText)
                                Icon(Icons.Default.ExpandMore, null, Modifier.size(16.dp),
                                    tint = ReferencePalette.SecondaryText)
                            }
                            DropdownMenu(expanded = sortExpanded, onDismissRequest = { sortExpanded = false }) {
                                ProjectSort.entries.forEach { option ->
                                    val label = when (option) {
                                        ProjectSort.DATE -> "Sort by Date"
                                        ProjectSort.NAME -> "Sort by Name"
                                        ProjectSort.DURATION -> "Sort by Duration"
                                        ProjectSort.SIZE -> "Sort by Size"
                                    }
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = { vm.setSort(option); sortExpanded = false }
                                    )
                                }
                            }
                        }
                        Box {
                            IconButton(onClick = { actionsExpanded = true }, modifier = Modifier.size(30.dp)) {
                                Icon(Icons.Default.MoreHoriz, "Project list options", Modifier.size(19.dp),
                                    tint = ReferencePalette.SecondaryText)
                            }
                            DropdownMenu(expanded = actionsExpanded, onDismissRequest = { actionsExpanded = false }) {
                                DropdownMenuItem(
                                    text = { Text("Import project") },
                                    leadingIcon = { Icon(Icons.Default.FileOpen, null) },
                                    onClick = { actionsExpanded = false; onImport() }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (grid) "List view" else "Grid view") },
                                    leadingIcon = { Icon(if (grid) Icons.Default.ViewList else Icons.Default.GridView, null) },
                                    onClick = { vm.setGrid(!grid); actionsExpanded = false }
                                )
                            }
                        }
                    }
                }
                ReferenceSearchBar(query, vm::setQuery, "Search projects")
                Spacer(Modifier.height(10.dp))
            }

            AnimatedContent(
                targetState = grid,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    fadeIn() + scaleIn(initialScale = 0.98f) togetherWith fadeOut() + scaleOut(targetScale = 0.98f)
                },
                label = "project-layout"
            ) { isGrid ->
                if (visible.isEmpty()) {
                    Box(Modifier.fillMaxSize().padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.FolderOpen, null, Modifier.size(38.dp), tint = ReferencePalette.MutedText)
                            Text(if (query.isBlank()) "No projects… yet!" else "No projects match that search.",
                                fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = ReferencePalette.Text)
                            if (query.isBlank()) {
                                Text("Create a project to start editing.", fontSize = 12.sp,
                                    color = ReferencePalette.MutedText)
                                TextButton(onClick = onNew) { Text("Create a project", color = ReferencePalette.Mint) }
                            }
                        }
                    }
                } else if (isGrid) {
                    LazyVerticalGrid(
                        GridCells.Adaptive(170.dp), Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(visible, key = { it.id }) { project ->
                            ProjectCard(
                                summary = project,
                                vm = vm,
                                selectionMode = selectionMode,
                                selected = project.id in selectedIds,
                                onClick = { onOpenProject(project.id) },
                                onSelect = {
                                    selectedIds = if (project.id in selectedIds) selectedIds - project.id
                                        else selectedIds + project.id
                                },
                                onAction = { actionTarget = project }
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        itemsIndexed(visible, key = { _, project -> project.id }) { index, project ->
                            val date = dateLabels.getOrNull(index)
                            Column {
                                if (date != null && (index == 0 || date != dateLabels.getOrNull(index - 1))) {
                                    Text(date, Modifier.padding(start = 2.dp, top = 8.dp, bottom = 2.dp),
                                        fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                        color = ReferencePalette.MutedText, letterSpacing = 0.3.sp)
                                }
                                ReferenceProjectRow(
                                    project = project,
                                    selectionMode = selectionMode,
                                    selected = project.id in selectedIds,
                                    onClick = { onOpenProject(project.id) },
                                    onSelect = {
                                        selectedIds = if (project.id in selectedIds) selectedIds - project.id
                                            else selectedIds + project.id
                                    },
                                    onMore = { actionTarget = project }
                                )
                            }
                        }
                    }
                }
            }
            if (selectionMode) {
                Row(
                    Modifier.fillMaxWidth().background(ReferencePalette.Card)
                        .padding(horizontal = 4.dp, vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { selectedIds = visible.map { it.id }.toSet() }) {
                        Icon(Icons.Default.SelectAll, null, Modifier.size(17.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Select All", fontSize = 10.sp)
                    }
                    TextButton(onClick = {
                        selectedIds.toList().forEach { vm.duplicate(it) }
                        selectedIds = emptySet()
                    }) {
                        Icon(Icons.Default.ContentCopy, null, Modifier.size(17.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Duplicate", fontSize = 10.sp)
                    }
                    TextButton(onClick = { confirmDeleteSelection = true }) {
                        Icon(Icons.Default.Delete, null, Modifier.size(17.dp), tint = EditorPalette.Red)
                        Spacer(Modifier.width(4.dp))
                        Text("Delete", fontSize = 10.sp, color = EditorPalette.Red)
                    }
                }
            }
        }
    }

    if (aboutExpanded) {
        AlertDialog(
            onDismissRequest = { aboutExpanded = false },
            containerColor = ReferencePalette.Card,
            title = { Text(settings.brandName.ifBlank { "ULTRA MOTION" }, color = ReferencePalette.Text) },
            text = { Text("${settings.brandSubtitle}\n\nNative motion-editing studio · EditorsHub",
                color = ReferencePalette.SecondaryText) },
            confirmButton = { TextButton(onClick = { aboutExpanded = false; onSettings() }) {
                Text("Settings", color = ReferencePalette.Mint)
            } },
            dismissButton = { TextButton(onClick = { aboutExpanded = false }) {
                Text("Close", color = ReferencePalette.SecondaryText)
            } }
        )
    }
    actionTarget?.let { project ->
        ProjectActionsDialog(project, vm,
            { actionTarget = null },
            { onOpenProject(project.id); actionTarget = null },
            { selectedIds = selectedIds + project.id; actionTarget = null },
            { renameTarget = project; actionTarget = null },
            { if (settings.confirmBeforeDelete) deleteTarget = project else { vm.delete(project.id); actionTarget = null } },
            { infoTarget = project; actionTarget = null }, { exportTarget = project; actionTarget = null })
    }
    if (confirmDeleteSelection) {
        ConfirmDialog(
            "Delete ${selectedIds.size} projects?",
            "This removes the selected projects and their version histories.",
            {
                selectedIds.toList().forEach { vm.delete(it) }
                selectedIds = emptySet()
                confirmDeleteSelection = false
            },
            { confirmDeleteSelection = false }
        )
    }
    deleteTarget?.let { project -> ConfirmDialog("Delete “${project.name}”?",
        "This removes the project and its version history.",
        { vm.delete(project.id); deleteTarget = null }, { deleteTarget = null }) }
    renameTarget?.let { project ->
        var name by remember { mutableStateOf(project.name) }
        AlertDialog(onDismissRequest = { renameTarget = null }, title = { Text("Rename") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { vm.rename(project.id, name); renameTarget = null }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancel") } })
    }
    infoTarget?.let { ProjectInfoDialog(it, vm) { infoTarget = null } }
    exportTarget?.let { QuickExportDialog(it, vm) { exportTarget = null } }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReferenceProjectRow(
    project: ProjectSummary,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onSelect: () -> Unit,
    onMore: () -> Unit
) {
    val totalSeconds = project.durationMs / 1_000L
    val duration = "%02d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
    Row(
        Modifier.fillMaxWidth().height(58.dp).clip(RoundedCornerShape(12.dp))
            .background(if (selected) ReferencePalette.Input else ReferencePalette.Card)
            .combinedClickable(
                onClick = { if (selectionMode) onSelect() else onClick() },
                onLongClick = onSelect
            )
            .padding(start = 10.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val thumbnail = project.thumbnailPath?.let(::File)?.takeIf { it.exists() }
        if (thumbnail != null) {
            AsyncImage(thumbnail, "${project.name} thumbnail", Modifier.size(38.dp)
                .clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
        } else {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(8.dp)).background(ReferencePalette.Input),
                contentAlignment = Alignment.Center) {
                Text(project.name.take(1).uppercase(), color = ReferencePalette.Text,
                    fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(project.name, color = ReferencePalette.Text, fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text("$duration · ${project.width}×${project.height} · ${project.layerCount} layers",
                color = ReferencePalette.MutedText, fontSize = 10.sp, maxLines = 1)
        }
        if (selectionMode) {
            IconButton(onClick = onSelect, modifier = Modifier.size(32.dp)) {
                Icon(
                    if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    if (selected) "Selected ${project.name}" else "Select ${project.name}",
                    Modifier.size(19.dp),
                    tint = if (selected) ReferencePalette.Mint else ReferencePalette.MutedText
                )
            }
        } else {
            Box(
                Modifier.size(32.dp).clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onMore),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.MoreHoriz, "Project options for ${project.name}", Modifier.size(18.dp),
                    tint = ReferencePalette.MutedText)
            }
        }
    }
}

@Composable
private fun ProjectActionsDialog(
    summary: ProjectSummary, vm: HomeViewModel,
    onDismiss: () -> Unit, onOpen: () -> Unit, onSelect: () -> Unit, onRename: () -> Unit,
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
                ActionRow(Icons.Default.CheckCircle, "Select") { onSelect() }
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
    val sampleRate = "48000"
    val colorSpace = ColorSpace.SRGB
    val quality = 0.75f

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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Column(
                Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = 16.dp)
                    .heightIn(max = 700.dp).clip(RoundedCornerShape(22.dp))
                    .background(ReferencePalette.Card).padding(16.dp)
            ) {
                Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Create project", fontSize = 17.sp, fontWeight = FontWeight.Bold,
                            color = ReferencePalette.Text)
                        Text("PROJECT", fontSize = 9.sp, fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp, color = ReferencePalette.Mint)
                    }
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).background(ReferencePalette.Input)
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Close, "Close create project", Modifier.size(18.dp),
                            tint = ReferencePalette.SecondaryText)
                    }
                }
                HorizontalDivider(color = Color(0xFF333338))
                Column(
                    Modifier.weight(1f, fill = false).fillMaxWidth()
                        .verticalScroll(rememberScrollState()).padding(top = 12.dp, bottom = 12.dp)
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Project name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ReferencePalette.Mint,
                            focusedLabelColor = ReferencePalette.Mint,
                            cursorColor = ReferencePalette.Mint
                        )
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Aspect ratio", fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        color = ReferencePalette.Text)
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        ASPECTS.forEach { option ->
                            ReferenceRatioChip(option, aspect == option) { aspect = option }
                        }
                    }
                    if (res.shortSide == 0 || aspect == "Custom") {
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(customW, { customW = it }, label = { Text("Width") },
                                modifier = Modifier.weight(1f), singleLine = true)
                            OutlinedTextField(customH, { customH = it }, label = { Text("Height") },
                                modifier = Modifier.weight(1f), singleLine = true)
                        }
                    }
                    Text("Output: ${w} × ${h}", Modifier.padding(top = 6.dp),
                        fontSize = 10.sp, color = ReferencePalette.MutedText)
                    Spacer(Modifier.height(8.dp))
                    LabeledSelect("Resolution", res.label, RES_PRESETS.map { it.label to it.label }) {
                        res = RES_PRESETS.first { preset -> preset.label == it }
                    }
                    LabeledSelect("Frame rate", fps.label, FrameRates.ALL.map { it.label to it.label }) {
                        fps = FrameRates.ALL.first { preset -> preset.label == it }
                    }
                    SliderRow("Project duration (s)", duration, 1f..120f, { duration = it })
                    LabeledSelect("Background", bgType.name,
                        BackgroundType.entries.map { it.name to it.name }) { bgType = BackgroundType.valueOf(it) }
                    if (bgType != BackgroundType.TRANSPARENT) {
                        ColorSwatchRow("Primary", bgColor) { bgColor = it }
                        if (bgType == BackgroundType.LINEAR_GRADIENT || bgType == BackgroundType.RADIAL_GRADIENT) {
                            ColorSwatchRow("Gradient end", bgEnd) { bgEnd = it }
                            if (bgType == BackgroundType.LINEAR_GRADIENT) {
                                SliderRow("Gradient angle", bgAngle, 0f..360f, { bgAngle = it })
                            }
                        }
                    }
                    Text("Timebase: $timebase", Modifier.padding(top = 8.dp),
                        fontSize = 9.sp, color = ReferencePalette.MutedText)
                }
                ReferenceButton(
                    text = "Create Project",
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    background = ReferencePalette.Lavender,
                    foreground = Color(0xFF111116),
                    onClick = {
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
                    }
                )
            }
        }
    }
}

@Composable
private fun ReferenceRatioChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember(label) { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Box(
        Modifier.padding(end = 6.dp).height(32.dp).clip(RoundedCornerShape(9.dp))
            .background(if (selected) ReferencePalette.Lavender else ReferencePalette.Input)
            .clickable(interactionSource = interaction, indication = androidx.compose.foundation.LocalIndication.current,
                role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .padding(horizontal = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
            color = if (selected) Color(0xFF111116) else ReferencePalette.SecondaryText)
    }
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
