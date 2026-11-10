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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
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
import coil.request.CachePolicy
import coil.request.ImageRequest
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
import java.util.Locale
import kotlin.math.roundToInt

enum class ProjectSection { PROJECTS, TEMPLATES, ELEMENTS }
enum class CreateMode { PROJECT, ELEMENT }

private fun gcd(a: Int, b: Int): Int = if (b == 0) kotlin.math.abs(a) else gcd(b, a % b)

private fun aspectLabel(width: Int, height: Int): String {
    if (width <= 0 || height <= 0) return "Custom"
    val divisor = gcd(width, height)
    return "${width / divisor}:${height / divisor}"
}

private fun formatProjectBytes(bytes: Long): String = when {
    bytes >= 1_048_576L -> String.format(Locale.UK, "%.1fMB", bytes / 1_048_576.0)
    bytes >= 1_024L -> String.format(Locale.UK, "%.1fKB", bytes / 1_024.0)
    else -> "${bytes}B"
}

// ------------------------------------------------------------------ HOME
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: HomeViewModel = hiltViewModel(),
    importVm: ImportViewModel = hiltViewModel(),
    onOpenProject: (String) -> Unit,
    onSettings: () -> Unit,
    onProjects: () -> Unit
) {
    val settings by vm.appSettings.collectAsState()
    val projects by vm.projects.collectAsState()
    val importState by importVm.ui.collectAsState()
    val recent = remember(projects) { projects.sortedByDescending { it.updatedAt }.take(4) }
    var showAbout by remember { mutableStateOf(false) }
    var packageUrl by remember { mutableStateOf("") }
    var packageBusy by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.openProject.collect { onOpenProject(it) } }
    LaunchedEffect(Unit) { vm.message.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(projects) { vm.ensureThumbnails(projects) }

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
                .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 24.dp)
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
        modifier = Modifier.fillMaxWidth().height(101.dp),
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
        modifier = Modifier.fillMaxWidth().height(243.dp),
        shape = RoundedCornerShape(20.dp),
        color = ReferencePalette.Card
    ) {
        Column(
            Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 27.dp)
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
        Modifier.fillMaxWidth().height(38.dp).clip(RoundedCornerShape(10.dp))
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
        ProjectThumbnail(
            project = project,
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)),
            iconSize = 20.dp
        )
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

/** Loads a real thumbnail file with a visible fallback for missing/corrupt files. */
@Composable
private fun ProjectThumbnail(
    project: ProjectSummary,
    modifier: Modifier,
    iconSize: Dp = 22.dp,
    showInitial: Boolean = false
) {
    val context = LocalContext.current
    val file = remember(project.thumbnailPath) {
        project.thumbnailPath?.let(::File)?.takeIf { it.isFile && it.length() > 0L }
    }
    var loadFailed by remember(project.thumbnailPath, project.updatedAt) { mutableStateOf(false) }
    val request = remember(file, project.updatedAt) {
        file?.let {
            ImageRequest.Builder(context)
                .data(it)
                // Thumbnails are overwritten in place; never show a cached stale frame.
                .memoryCachePolicy(CachePolicy.DISABLED)
                .diskCachePolicy(CachePolicy.DISABLED)
                .build()
        }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        ProjectThumbnailPlaceholder(project, Modifier.fillMaxSize(), iconSize, showInitial)
        if (request != null && !loadFailed) {
            AsyncImage(
                model = request,
                contentDescription = "${project.name} thumbnail",
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
                onSuccess = { loadFailed = false },
                onError = { loadFailed = true }
            )
        }
    }
}

@Composable
private fun ProjectThumbnailPlaceholder(
    project: ProjectSummary,
    modifier: Modifier,
    iconSize: Dp,
    showInitial: Boolean
) {
    Box(
        modifier.background(Brush.linearGradient(listOf(Color(0xFF24263A), Color(0xFF1A2C3A)))),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Movie, null, Modifier.size(iconSize), tint = EditorPalette.Purple)
            if (showInitial) {
                Spacer(Modifier.height(4.dp))
                Text(project.name.take(1).uppercase(), color = ReferencePalette.Text,
                    fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
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
            ProjectThumbnail(
                project = summary,
                modifier = Modifier.fillMaxWidth().height(110.dp)
                    .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)),
                iconSize = 30.dp,
                showInitial = true
            )
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
        modifier.fillMaxHeight().clip(RoundedCornerShape(18.dp))
            .background(if (selected) Color(0xFF414147) else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 12.sp, maxLines = 1,
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
    onNew: (CreateMode) -> Unit,
    onSettings: () -> Unit
) {
    val visible by vm.visible.collectAsState()
    val query by vm.query.collectAsState()
    val sort by vm.sort.collectAsState()
    val grid by vm.grid.collectAsState()
    val settings by vm.appSettings.collectAsState()
    val elements by vm.elements.collectAsState()
    val templates = vm.templates
    var section by remember { mutableStateOf(ProjectSection.PROJECTS) }
    var sectionQuery by remember { mutableStateOf("") }
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
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        launch { vm.openProject.collect { onOpenProject(it) } }
        launch { vm.message.collect { snackbar.showSnackbar(it) } }
    }
    LaunchedEffect(visible, elements) { vm.ensureThumbnails(visible + elements) }

    val filteredTemplates = remember(templates, sectionQuery) {
        templates.filter {
            sectionQuery.isBlank() || it.name.contains(sectionQuery, true) ||
                it.description.contains(sectionQuery, true)
        }
    }
    val filteredElements = remember(elements, sectionQuery) {
        elements.filter { sectionQuery.isBlank() || it.name.contains(sectionQuery, true) }
    }
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
    val searchValue = if (section == ProjectSection.PROJECTS) query else sectionQuery
    val onSearchChange: (String) -> Unit = if (section == ProjectSection.PROJECTS) {
        vm::setQuery
    } else { { sectionQuery = it } }
    val searchPlaceholder = when (section) {
        ProjectSection.PROJECTS -> "Search projects"
        ProjectSection.TEMPLATES -> "Search templates"
        ProjectSection.ELEMENTS -> "Search elements"
    }
    val showListHeader = section != ProjectSection.ELEMENTS || filteredElements.isNotEmpty()

    Scaffold(
        containerColor = ReferencePalette.Background,
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().background(ReferencePalette.Background)
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 24.dp)) {
                ReferenceHomeHeader(
                    settings.brandName, settings.brandSubtitle,
                    onSettings = onSettings,
                    onAbout = { aboutExpanded = true }
                )
                Spacer(Modifier.height(25.dp))
                Row(
                    Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(20.dp))
                        .background(ReferencePalette.Input).padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ProjectSectionButton("Projects", section == ProjectSection.PROJECTS,
                        Modifier.weight(1f)) { section = ProjectSection.PROJECTS }
                    ProjectSectionButton("Your Templates", section == ProjectSection.TEMPLATES,
                        Modifier.weight(1.2f)) { section = ProjectSection.TEMPLATES }
                    ProjectSectionButton("Elements", section == ProjectSection.ELEMENTS,
                        Modifier.weight(1f)) { section = ProjectSection.ELEMENTS }
                }
                Spacer(Modifier.height(23.dp))
                ReferenceSearchBar(searchValue, onSearchChange, searchPlaceholder)
                if (showListHeader) {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 26.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            when {
                                selectionMode -> "${selectedIds.size} selected"
                                section == ProjectSection.PROJECTS -> "Your projects"
                                section == ProjectSection.TEMPLATES -> "Your templates"
                                else -> "Your elements"
                            },
                            Modifier.weight(1f), fontSize = 18.sp,
                            fontWeight = FontWeight.Bold, color = ReferencePalette.Text
                        )
                        if (selectionMode) {
                            TextButton(onClick = { selectedIds = emptySet() },
                                contentPadding = PaddingValues(horizontal = 4.dp)) {
                                Text("Cancel", fontSize = 12.sp, color = ReferencePalette.Mint)
                            }
                        } else {
                            Box {
                                TextButton(onClick = { sortExpanded = true },
                                    contentPadding = PaddingValues(horizontal = 4.dp)) {
                                    Text("Sort by $sortLabel", fontSize = 12.sp,
                                        color = ReferencePalette.SecondaryText)
                                    Icon(Icons.Default.ExpandMore, null, Modifier.size(16.dp),
                                        tint = ReferencePalette.SecondaryText)
                                }
                                DropdownMenu(expanded = sortExpanded,
                                    onDismissRequest = { sortExpanded = false }) {
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
                                IconButton(onClick = { actionsExpanded = true },
                                    modifier = Modifier.size(30.dp)) {
                                    Icon(Icons.Default.MoreHoriz, "Project list options",
                                        Modifier.size(19.dp), tint = ReferencePalette.SecondaryText)
                                }
                                DropdownMenu(expanded = actionsExpanded,
                                    onDismissRequest = { actionsExpanded = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Import project") },
                                        leadingIcon = { Icon(Icons.Default.FileOpen, null) },
                                        onClick = { actionsExpanded = false; onImport() }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(if (grid) "List view" else "Grid view") },
                                        leadingIcon = {
                                            Icon(if (grid) Icons.Default.ViewList else Icons.Default.GridView, null)
                                        },
                                        onClick = { vm.setGrid(!grid); actionsExpanded = false }
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            AnimatedContent(
                targetState = section,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    fadeIn() + scaleIn(initialScale = 0.985f) togetherWith
                        fadeOut() + scaleOut(targetScale = 0.985f)
                },
                label = "project-section"
            ) { currentSection ->
                when (currentSection) {
                    ProjectSection.PROJECTS -> {
                        if (visible.isEmpty()) {
                            ReferenceEmptyState(
                                icon = Icons.Default.FolderOpen,
                                title = if (query.isBlank()) "No projects… yet!" else "No projects match that search.",
                                body = "Create a project to start editing.",
                                action = if (query.isBlank()) "Create a project" else null,
                                onAction = { onNew(CreateMode.PROJECT) }
                            )
                        } else if (grid) {
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
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                itemsIndexed(visible, key = { _, project -> project.id }) { index, project ->
                                    val date = dateLabels.getOrNull(index)
                                    Column {
                                        if (date != null && (index == 0 || date != dateLabels.getOrNull(index - 1))) {
                                            Text(date,
                                                Modifier.padding(start = 2.dp, top = 10.dp, bottom = 4.dp),
                                                fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                                color = ReferencePalette.SecondaryText)
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
                    ProjectSection.TEMPLATES -> {
                        if (filteredTemplates.isEmpty()) {
                            ReferenceEmptyState(
                                icon = Icons.Default.Movie,
                                title = "No templates match that search.",
                                body = "Try another phrase or clear the search field."
                            )
                        } else {
                            LazyColumn(
                                Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 18.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(filteredTemplates, key = { it.name }) { template ->
                                    ReferenceTemplateRow(template) { vm.createFromTemplate(template) }
                                }
                            }
                        }
                    }
                    ProjectSection.ELEMENTS -> {
                        if (filteredElements.isEmpty()) {
                            ReferenceEmptyState(
                                icon = Icons.Default.FolderOpen,
                                title = if (sectionQuery.isBlank()) "No elements… yet!" else "No elements match that search.",
                                body = if (sectionQuery.isBlank()) "Create an element to reuse a composition." else null,
                                action = if (sectionQuery.isBlank()) "Create an element" else null,
                                onAction = { onNew(CreateMode.ELEMENT) }
                            )
                        } else {
                            LazyColumn(
                                Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 18.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(filteredElements, key = { it.id }) { element ->
                                    ReferenceProjectRow(
                                        project = element,
                                        selectionMode = false,
                                        selected = false,
                                        onClick = { onOpenProject(element.id) },
                                        onSelect = {},
                                        moreIcon = Icons.Default.Close,
                                        moreDescription = "Remove ${element.name} from Elements",
                                        onMore = { vm.removeElement(element.id) }
                                    )
                                }
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
    onMore: () -> Unit,
    moreIcon: ImageVector = Icons.Default.MoreHoriz,
    moreDescription: String = "Project options for ${project.name}"
) {
    val totalSeconds = project.durationMs / 1_000L
    val duration = String.format(
        Locale.UK, "%02d:%02d:%02d",
        totalSeconds / 3_600L, (totalSeconds / 60L) % 60L, totalSeconds % 60L
    )
    Row(
        Modifier.fillMaxWidth().height(92.dp).clip(RoundedCornerShape(14.dp))
            .background(if (selected) ReferencePalette.Input else ReferencePalette.Card)
            .combinedClickable(
                onClick = { if (selectionMode) onSelect() else onClick() },
                onLongClick = onSelect
            )
            .padding(start = 8.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(76.dp).height(80.dp)) {
            ProjectThumbnail(
                project = project,
                modifier = Modifier.matchParentSize().clip(RoundedCornerShape(10.dp)),
                iconSize = 26.dp,
                showInitial = true
            )
            Box(
                Modifier.align(Alignment.BottomStart).padding(5.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(Color.Black.copy(alpha = 0.78f))
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                Text(duration, color = Color.White, fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(project.name, color = ReferencePalette.Text, fontSize = 16.sp,
                fontWeight = FontWeight.Bold, maxLines = 1)
            Row(Modifier.fillMaxWidth()) {
                ReferenceProjectMeta(Icons.Default.OpenWith,
                    aspectLabel(project.width, project.height), Modifier.weight(1f))
                ReferenceProjectMeta(Icons.Default.CropSquare,
                    "${minOf(project.width, project.height)}p", Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth()) {
                ReferenceProjectMeta(Icons.Default.FolderOpen,
                    formatProjectBytes(project.bytes), Modifier.weight(1f))
                ReferenceProjectMeta(Icons.Default.Bolt,
                    "${project.fpsExact.roundToInt()}fps", Modifier.weight(1f))
            }
        }
        if (selectionMode) {
            IconButton(onClick = onSelect, modifier = Modifier.size(32.dp)) {
                Icon(
                    if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    if (selected) "Selected ${project.name}" else "Select ${project.name}",
                    Modifier.size(20.dp),
                    tint = if (selected) ReferencePalette.Mint else ReferencePalette.MutedText
                )
            }
        } else {
            Box(
                Modifier.size(32.dp).clickable(
                    role = androidx.compose.ui.semantics.Role.Button, onClick = onMore
                ),
                contentAlignment = Alignment.Center
            ) {
                Icon(moreIcon, moreDescription, Modifier.size(19.dp),
                    tint = ReferencePalette.MutedText)
            }
        }
    }
}

@Composable
private fun ReferenceProjectMeta(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(14.dp), tint = ReferencePalette.MutedText)
        Spacer(Modifier.width(5.dp))
        Text(text, color = ReferencePalette.SecondaryText, fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
private fun ReferenceTemplateRow(
    template: TemplateCatalog.Template,
    onClick: () -> Unit
) {
    val interaction = remember(template.name) {
        androidx.compose.foundation.interaction.MutableInteractionSource()
    }
    Row(
        Modifier.fillMaxWidth().height(78.dp).clip(RoundedCornerShape(14.dp))
            .background(ReferencePalette.Card)
            .clickable(interactionSource = interaction,
                indication = androidx.compose.foundation.LocalIndication.current,
                onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(58.dp).clip(RoundedCornerShape(10.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF24263A), Color(0xFF1A2C3A)))),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Movie, null, Modifier.size(26.dp),
                tint = EditorPalette.Purple)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(template.name, color = ReferencePalette.Text, fontSize = 14.sp,
                fontWeight = FontWeight.Bold, maxLines = 1)
            Text(template.description, color = ReferencePalette.MutedText, fontSize = 11.sp,
                maxLines = 2)
        }
    }
}

@Composable
private fun ReferenceEmptyState(
    icon: ImageVector,
    title: String,
    body: String? = null,
    action: String? = null,
    onAction: (() -> Unit)? = null
) {
    Box(Modifier.fillMaxSize().padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, Modifier.size(40.dp), tint = ReferencePalette.MutedText)
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                color = ReferencePalette.Text)
            if (body != null) {
                Text(body, fontSize = 12.sp, color = ReferencePalette.MutedText)
            }
            if (action != null && onAction != null) {
                TextButton(onClick = onAction) { Text(action, color = ReferencePalette.Mint) }
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
private val CREATE_ASPECTS = listOf("16:9", "9:16", "4:5", "1:1", "4:3")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectCreationSheet(
    vm: HomeViewModel,
    initialMode: CreateMode = CreateMode.PROJECT,
    onDismiss: () -> Unit,
    onCreated: () -> Unit
) {
    var mode by remember { mutableStateOf(initialMode) }
    var name by remember {
        mutableStateOf(if (initialMode == CreateMode.ELEMENT) "New Element" else "New Project 1")
    }
    var nameEdited by remember { mutableStateOf(false) }
    var aspect by remember { mutableStateOf("16:9") }
    var custom by remember { mutableStateOf(false) }
    var widthText by remember { mutableStateOf("1920") }
    var heightText by remember { mutableStateOf("1080") }
    var fps by remember { mutableStateOf(FrameRates.ALL[10]) }
    var fpsExpanded by remember { mutableStateOf(false) }
    var duration by remember { mutableFloatStateOf(10f) }
    var bgType by remember { mutableStateOf(BackgroundType.SOLID) }
    var bgExpanded by remember { mutableStateOf(false) }
    var bgColor by remember { mutableStateOf("#FF000000") }
    var bgEnd by remember { mutableStateOf("#FF22D3EE") }
    var bgAngle by remember { mutableFloatStateOf(45f) }
    var advanced by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    val sampleRate = 48_000
    val colorSpace = ColorSpace.SRGB
    val quality = 0.75f

    fun selectMode(next: CreateMode) {
        mode = next
        if (!nameEdited) {
            name = if (next == CreateMode.ELEMENT) "New Element" else "New Project 1"
        }
    }

    fun applyAspect(next: String) {
        val parts = next.split(":").mapNotNull { it.toFloatOrNull() }
        val rw = parts.getOrNull(0) ?: 16f
        val rh = parts.getOrNull(1) ?: 9f
        val shortSide = 1080
        if (rw >= rh) {
            widthText = (shortSide * rw / rh).roundToInt().toString()
            heightText = shortSide.toString()
        } else {
            widthText = shortSide.toString()
            heightText = (shortSide * rh / rw).roundToInt().toString()
        }
        aspect = next
        custom = false
    }

    val w = (widthText.toIntOrNull() ?: 1920).coerceIn(16, 4096)
    val h = (heightText.toIntOrNull() ?: 1080).coerceIn(16, 4096)
    val timebase = when {
        kotlin.math.abs(fps.exact - 23.976) < 0.01 -> "1001/24000"
        kotlin.math.abs(fps.exact - 29.97) < 0.01 -> "1001/30000"
        kotlin.math.abs(fps.exact - 59.94) < 0.01 -> "1001/60000"
        else -> "1/${fps.exact.roundToInt()}"
    }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val sheetHeight = if (maxHeight < 466.dp) maxHeight - 6.dp else 460.dp
        Column(
            Modifier.fillMaxWidth().height(sheetHeight)
                .clip(RoundedCornerShape(28.dp))
                .background(ReferencePalette.Card)
                .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 18.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().height(38.dp)
                    .clip(RoundedCornerShape(19.dp))
                    .background(ReferencePalette.Input).padding(3.dp)
            ) {
                ReferenceCreateTab("Project", mode == CreateMode.PROJECT, Modifier.weight(1f)) {
                    selectMode(CreateMode.PROJECT)
                }
                ReferenceCreateTab("Element", mode == CreateMode.ELEMENT, Modifier.weight(1f)) {
                    selectMode(CreateMode.ELEMENT)
                }
            }
            Spacer(Modifier.height(14.dp))
            ReferenceCreateNameField(
                value = name,
                onValueChange = { name = it; nameEdited = true },
                onClear = { name = ""; nameEdited = true }
            )
            Spacer(Modifier.height(12.dp))
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll)) {
                Row(Modifier.fillMaxWidth().height(48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CREATE_ASPECTS.forEach { option ->
                        ReferenceAspectChip(option, !custom && aspect == option) { applyAspect(option) }
                    }
                    Box(
                        Modifier.size(46.dp).clip(RoundedCornerShape(10.dp))
                            .border(1.dp, ReferencePalette.SecondaryText, RoundedCornerShape(10.dp))
                            .clickable(role = androidx.compose.ui.semantics.Role.Button,
                                onClick = { custom = true; aspect = "Custom" }),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Edit, "Custom composition size",
                            Modifier.size(19.dp), tint = ReferencePalette.Text)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth().height(60.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Resolution", color = ReferencePalette.Text, fontSize = 16.sp,
                            fontWeight = FontWeight.Bold)
                        Text("Composition Size", color = ReferencePalette.MutedText, fontSize = 10.sp)
                        Text("$w × $h", color = ReferencePalette.MutedText, fontSize = 10.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ReferenceCreateValueField(widthText, {
                            widthText = it; custom = true; aspect = "Custom"
                        }, Modifier.width(74.dp).height(46.dp))
                        Box(
                            Modifier.padding(horizontal = 5.dp).size(32.dp).clip(CircleShape)
                                .background(Color(0xFF073C2C))
                                .clickable(role = androidx.compose.ui.semantics.Role.Button,
                                    onClick = {
                                        val old = widthText
                                        widthText = heightText
                                        heightText = old
                                        aspect = aspectLabel(w, h)
                                        custom = aspect !in CREATE_ASPECTS
                                    }),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("⇄", color = ReferencePalette.Mint, fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.semantics { contentDescription = "Swap width and height" })
                        }
                        ReferenceCreateValueField(heightText, {
                            heightText = it; custom = true; aspect = "Custom"
                        }, Modifier.width(74.dp).height(46.dp))
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth().height(50.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("Frame Rate", Modifier.weight(1f), color = ReferencePalette.Text,
                        fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    ReferenceCreateDropdown(
                        value = fps.label,
                        expanded = fpsExpanded,
                        onExpandedChange = { fpsExpanded = it },
                        options = FrameRates.ALL.map { it.label to it.label },
                        onSelect = { label ->
                            fps = FrameRates.ALL.first { preset -> preset.label == label }
                        },
                        modifier = Modifier.width(190.dp).height(46.dp)
                    )
                }
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth().height(50.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("Background", Modifier.weight(1f), color = ReferencePalette.Text,
                        fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    ReferenceCreateDropdown(
                        value = when (bgType) {
                            BackgroundType.SOLID -> "Black"
                            BackgroundType.TRANSPARENT -> "Transparent"
                            BackgroundType.LINEAR_GRADIENT -> "Linear gradient"
                            BackgroundType.RADIAL_GRADIENT -> "Radial gradient"
                        },
                        leading = {
                            Box(
                                Modifier.size(18.dp).clip(RoundedCornerShape(4.dp))
                                    .background(Color(ColorUtil.parse(bgColor)))
                                    .border(1.dp, Color(0xFF3A3A40), RoundedCornerShape(4.dp))
                            )
                        },
                        expanded = bgExpanded,
                        onExpandedChange = { bgExpanded = it },
                        options = BackgroundType.entries.map { it.name to it.name },
                        onSelect = { bgType = BackgroundType.valueOf(it) },
                        modifier = Modifier.width(190.dp).height(46.dp)
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth().height(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(role = androidx.compose.ui.semantics.Role.Button,
                            onClick = { advanced = !advanced })
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (advanced) "Hide advanced settings" else "More settings",
                        Modifier.weight(1f), color = ReferencePalette.SecondaryText,
                        fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Icon(if (advanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        null, Modifier.size(18.dp), tint = ReferencePalette.SecondaryText)
                }
                if (advanced) {
                    Spacer(Modifier.height(8.dp))
                    SliderRow("Project duration (s)", duration, 1f..120f, onChange = { duration = it })
                    LabeledSelect("Background type", bgType.name,
                        BackgroundType.entries.map { it.name to it.name },
                        onChange = { bgType = BackgroundType.valueOf(it) })
                    if (bgType != BackgroundType.TRANSPARENT) {
                        ColorSwatchRow("Primary", bgColor) { bgColor = it }
                        if (bgType == BackgroundType.LINEAR_GRADIENT ||
                            bgType == BackgroundType.RADIAL_GRADIENT) {
                            ColorSwatchRow("Gradient end", bgEnd) { bgEnd = it }
                            if (bgType == BackgroundType.LINEAR_GRADIENT) {
                                SliderRow("Gradient angle", bgAngle, 0f..360f, onChange = { bgAngle = it })
                            }
                        }
                    }
                    Text("Timebase: $timebase", Modifier.padding(top = 8.dp),
                        fontSize = 9.sp, color = ReferencePalette.MutedText)
                }
            }
            Spacer(Modifier.height(12.dp))
            ReferenceButton(
                text = if (mode == CreateMode.ELEMENT) "Create Element" else "Create Project",
                modifier = Modifier.fillMaxWidth().height(46.dp),
                background = ReferencePalette.Lavender,
                foreground = Color(0xFF111116),
                onClick = {
                    val spec = ProjectSpec(
                        name = name.ifBlank {
                            if (mode == CreateMode.ELEMENT) "Untitled element" else "Untitled project"
                        },
                        width = w, height = h,
                        aspect = if (custom) aspectLabel(w, h) else aspect,
                        fpsExact = fps.exact,
                        durationMs = (duration * 1000).toLong(),
                        backgroundType = bgType, background = bgColor,
                        backgroundEnd = bgEnd, backgroundAngle = bgAngle,
                        audioSampleRate = sampleRate, colorSpace = colorSpace,
                        previewQuality = quality, timebase = timebase
                    )
                    if (mode == CreateMode.ELEMENT) vm.createElement(spec) else vm.createProject(spec)
                    onCreated()
                }
            )
        }
    }
}

@Composable
private fun ReferenceCreateTab(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.fillMaxHeight().clip(RoundedCornerShape(16.dp))
            .background(if (selected) Color(0xFF414147) else Color.Transparent)
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 15.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) ReferencePalette.Text else ReferencePalette.MutedText)
    }
}

@Composable
private fun ReferenceCreateNameField(value: String, onValueChange: (String) -> Unit, onClear: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(14.dp))
            .background(ReferencePalette.Input).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text("New Project 1", color = ReferencePalette.MutedText, fontSize = 16.sp)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Project name" },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = ReferencePalette.Text, fontSize = 16.sp
                )
            )
        }
        IconButton(onClick = onClear, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.Close, "Clear project name", Modifier.size(18.dp),
                tint = ReferencePalette.SecondaryText)
        }
    }
}

@Composable
private fun ReferenceAspectChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.width(54.dp).height(46.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Color(0xFF2E2E33) else Color.Transparent)
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        ReferenceAspectBrackets(
            Modifier.matchParentSize().padding(3.dp),
            if (selected) ReferencePalette.Text else ReferencePalette.SecondaryText
        )
        Text(label, color = ReferencePalette.Text, fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ReferenceAspectBrackets(modifier: Modifier, tint: Color) {
    androidx.compose.foundation.Canvas(modifier) {
        val arm = 9.dp.toPx()
        val stroke = 1.5.dp.toPx()
        val right = size.width
        val bottom = size.height
        drawLine(tint, androidx.compose.ui.geometry.Offset(0f, 0f),
            androidx.compose.ui.geometry.Offset(arm, 0f), stroke)
        drawLine(tint, androidx.compose.ui.geometry.Offset(0f, 0f),
            androidx.compose.ui.geometry.Offset(0f, arm), stroke)
        drawLine(tint, androidx.compose.ui.geometry.Offset(right, 0f),
            androidx.compose.ui.geometry.Offset(right - arm, 0f), stroke)
        drawLine(tint, androidx.compose.ui.geometry.Offset(right, 0f),
            androidx.compose.ui.geometry.Offset(right, arm), stroke)
        drawLine(tint, androidx.compose.ui.geometry.Offset(0f, bottom),
            androidx.compose.ui.geometry.Offset(arm, bottom), stroke)
        drawLine(tint, androidx.compose.ui.geometry.Offset(0f, bottom),
            androidx.compose.ui.geometry.Offset(0f, bottom - arm), stroke)
        drawLine(tint, androidx.compose.ui.geometry.Offset(right, bottom),
            androidx.compose.ui.geometry.Offset(right - arm, bottom), stroke)
        drawLine(tint, androidx.compose.ui.geometry.Offset(right, bottom),
            androidx.compose.ui.geometry.Offset(right, bottom - arm), stroke)
    }
}

@Composable
private fun ReferenceCreateValueField(value: String, onValueChange: (String) -> Unit, modifier: Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(12.dp)).background(ReferencePalette.Input),
        contentAlignment = Alignment.Center
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(
                color = ReferencePalette.Text, fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            ),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
            )
        )
    }
}

@Composable
private fun ReferenceCreateDropdown(
    value: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null
) {
    Box(modifier) {
        Row(
            Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))
                .background(ReferencePalette.Input)
                .clickable(role = androidx.compose.ui.semantics.Role.Button,
                    onClick = { onExpandedChange(true) })
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(10.dp))
            }
            Text(value, Modifier.weight(1f), color = ReferencePalette.Text,
                fontSize = 14.sp, maxLines = 1)
            Icon(Icons.Default.ExpandMore, null, Modifier.size(19.dp),
                tint = ReferencePalette.SecondaryText)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
            options.forEach { (label, key) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = { onSelect(key); onExpandedChange(false) }
                )
            }
        }
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
