package com.example.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.ProjectTemplates
import com.example.model.*
import com.example.ui.theme.OmsColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class TutorialItem(
    val id: String,
    val icon: String,
    val titleId: String,
    val titleEn: String,
    val descId: String,
    val descEn: String,
    val tag: String,
    val stepsId: List<String>,
    val stepsEn: List<String>
)

private val TUTORIAL_ITEMS = listOf(
    TutorialItem(
        id = "keyframe",
        icon = "◆",
        titleId = "Animasi Keyframe & Kurva Easing",
        titleEn = "Keyframe Animation & Easing Curves",
        descId = "Tambahkan titik keyframe pada posisi, skala, dan rotasi serta atur kurva Bezier.",
        descEn = "Add keyframes on position, scale, and rotation with custom cubic Bezier easing.",
        tag = "DASAR ANIMASI",
        stepsId = listOf(
            "1. Pilih layer di kanvas atau timeline.",
            "2. Buka menu Gerak & Transformasi lalu tekan ikon ◆ di samping X/Y/Skala/Rotasi.",
            "3. Geser playhead ke waktu baru dan ubah nilai properti (keyframe kedua dibuat otomatis).",
            "4. Buka Curve Editor untuk memilih preset Overshoot/Elastic atau seret titik Bezier."
        ),
        stepsEn = listOf(
            "1. Select a layer on the canvas or timeline.",
            "2. Open Move & Transform and tap ◆ next to X/Y/Scale/Rotation.",
            "3. Scrub the playhead and adjust the property (a second keyframe is added automatically).",
            "4. Open Curve Editor to pick Overshoot/Elastic presets or drag Bezier handles."
        )
    ),
    TutorialItem(
        id = "3d",
        icon = "◇",
        titleId = "Ruang Komposisi 3D & Kamera",
        titleEn = "3D Composition Space & Camera",
        descId = "Aktifkan ruang 3D, geser layer di sumbu Z, dan animasikan kamera perspektif.",
        descEn = "Enable 3D space, offset layers along Z depth, and animate a perspective camera.",
        tag = "3D COMPOSITING",
        stepsId = listOf(
            "1. Ketuk tombol + > Elemen > Kamera 3D atau aktifkan toggle 3D di bilah atas.",
            "2. Atur posisi Z tiap layer agar memiliki kedalaman berbeda (parallax).",
            "3. Tambahkan keyframe pada posisi X/Z atau rotZ kamera untuk gerakan sinematik."
        ),
        stepsEn = listOf(
            "1. Tap + > Elements > 3D Camera or enable the 3D scene toggle in the top bar.",
            "2. Adjust each layer's Z coordinate to create depth parallax.",
            "3. Keyframe the camera's X/Z position or Z-roll for cinematic motion."
        )
    ),
    TutorialItem(
        id = "vector",
        icon = "✒",
        titleId = "Vector Pen & Shape Studio",
        titleEn = "Vector Pen & Shape Studio",
        descId = "Gambar path kustom titik-demi-titik atau sesuaikan bentuk bintang, poligon, dan garis.",
        descEn = "Draw custom Bezier paths point-by-point or tune parametric primitive shapes.",
        tag = "VEKTOR & SHAPE",
        stepsId = listOf(
            "1. Buka tombol + lalu pilih tab Bentuk atau tombol Vektor.",
            "2. Ketuk kanvas untuk menambahkan node kurva Bezier, lalu tekan Selesai.",
            "3. Buka Shape Studio untuk mengatur jumlah sudut, radius dalam, dan lebar garis tepi."
        ),
        stepsEn = listOf(
            "1. Tap + and choose Shapes or the Vector Pen quick button.",
            "2. Tap the canvas to place Bezier nodes, then tap Done.",
            "3. Use Shape Studio to customize star points, inner radius, corner radius, and stroke."
        )
    ),
    TutorialItem(
        id = "effects",
        icon = "✦",
        titleId = "Katalog Efek & Spektrum Audio",
        titleEn = "Visual Effects & Audio Spectrum",
        descId = "Terapkan Chromatic Aberration, Glow Neon, Partikel, Blur, dan Spektrum Visualizer.",
        descEn = "Stack Chromatic Aberration, Neon Glow, Particles, Blur, and Audio Spectrum.",
        tag = "VISUAL EFFECTS",
        stepsId = listOf(
            "1. Pilih layer lalu buka tab Efek.",
            "2. Ketuk Tambah Efek dan pilih kategori Blur, Warna, Distorsi, Gerak, atau Simulasi.",
            "3. Nyalakan/matikan efek kapan saja atau animasikan parameternya dengan keyframe."
        ),
        stepsEn = listOf(
            "1. Select a layer and open Effects.",
            "2. Tap Add Effect and browse Blur, Color, Distortion, Motion, or Simulation.",
            "3. Toggle effects on/off or animate individual parameters with keyframes."
        )
    ),
    TutorialItem(
        id = "export",
        icon = "↗",
        titleId = "Ekspor Video MP4, PNG, JSON & XML",
        titleEn = "Export MP4 Video, PNG, JSON & XML",
        descId = "Render video MP4 H.264 native hingga 60 FPS atau ekspor proyek JSON/XML.",
        descEn = "Render native H.264 MP4 up to 60 FPS, PNG frame, or shareable JSON/XML packages.",
        tag = "RENDER & EKSPOR",
        stepsId = listOf(
            "1. Tekan ikon Ekspor di pojok kanan atas editor.",
            "2. Pilih format Video MP4, Resolusi (720p/1080p), FPS, dan Bitrate.",
            "3. Atau pilih PNG untuk frame aktif, JSON untuk cadangan penuh, dan XML Interchange."
        ),
        stepsEn = listOf(
            "1. Tap the Export icon in the top-right corner of the editor.",
            "2. Choose MP4 Video, Resolution (720p/1080p), FPS, and Bitrate.",
            "3. Or export the active frame as PNG, full project JSON, or XML Interchange."
        )
    )
)

@Composable
fun HomeScreen(
    viewModel: MotionStudioViewModel,
    onOpenEditor: () -> Unit
) {
    val lang by viewModel.language.collectAsState()
    val activeTab by viewModel.homeTab.collectAsState()
    val projects by viewModel.projects.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()

    val importUi by viewModel.importUi.collectAsState()

    var showNewProjectSheet by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var selectedTutorial by remember { mutableStateOf<TutorialItem?>(null) }
    var menuProject by remember { mutableStateOf<ProjectDocument?>(null) }
    var renameTarget by remember { mutableStateOf<ProjectDocument?>(null) }

    val projectFileImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importProjectFile(uri) {
                showImportDialog = false
                onOpenEditor()
            }
        }
    }

    Scaffold(
        containerColor = OmsColors.Bg,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(58.dp)
                    .background(OmsColors.Panel)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(
                            Brush.linearGradient(listOf(OmsColors.Accent, OmsColors.Purple))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Open Motion",
                        tint = Color(0xFF071B15),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Open Motion",
                        style = MaterialTheme.typography.titleMedium,
                        color = OmsColors.Text
                    )
                    Text(
                        text = "Studio Animasi & Komposisi",
                        fontSize = 10.sp,
                        color = OmsColors.Muted
                    )
                }

                TextButton(
                    onClick = {
                        viewModel.setLanguage(if (lang == AppLang.ID) AppLang.EN else AppLang.ID)
                    },
                    modifier = Modifier.testTag("language_toggle_button")
                ) {
                    Text(
                        text = lang.code.uppercase(),
                        color = OmsColors.Mint,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(
                    onClick = { showAboutDialog = true },
                    modifier = Modifier.testTag("about_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = "Tentang Open Motion",
                        tint = OmsColors.Muted
                    )
                }
            }
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(OmsColors.Panel)
                    .navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .border(1.dp, OmsColors.Border.copy(alpha = 0.5f)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HomeBottomNavItem(
                        icon = Icons.Outlined.Home,
                        activeIcon = Icons.Filled.Home,
                        label = OmsStrings.navHome(lang),
                        selected = activeTab == HomeTab.BERANDA,
                        modifier = Modifier.weight(1f).testTag("nav_home_tab"),
                        onClick = { viewModel.selectHomeTab(HomeTab.BERANDA) }
                    )
                    HomeBottomNavItem(
                        icon = Icons.Outlined.School,
                        activeIcon = Icons.Filled.School,
                        label = OmsStrings.navTutorial(lang),
                        selected = activeTab == HomeTab.TUTORIAL,
                        modifier = Modifier.weight(1f).testTag("nav_tutorial_tab"),
                        onClick = { viewModel.selectHomeTab(HomeTab.TUTORIAL) }
                    )
                    Spacer(Modifier.width(66.dp))
                    HomeBottomNavItem(
                        icon = Icons.Outlined.Folder,
                        activeIcon = Icons.Filled.Folder,
                        label = OmsStrings.navProjects(lang),
                        selected = activeTab == HomeTab.PROYEK,
                        modifier = Modifier.weight(1f).testTag("nav_projects_tab"),
                        onClick = { viewModel.selectHomeTab(HomeTab.PROYEK) }
                    )
                    HomeBottomNavItem(
                        icon = Icons.Outlined.DashboardCustomize,
                        activeIcon = Icons.Filled.DashboardCustomize,
                        label = OmsStrings.navTemplates(lang),
                        selected = activeTab == HomeTab.TEMPLATE,
                        modifier = Modifier.weight(1f).testTag("nav_templates_tab"),
                        onClick = { viewModel.selectHomeTab(HomeTab.TEMPLATE) }
                    )
                }

                FloatingActionButton(
                    onClick = { showNewProjectSheet = true },
                    shape = CircleShape,
                    containerColor = OmsColors.Accent,
                    contentColor = Color(0xFF071B17),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = (-18).dp)
                        .size(58.dp)
                        .border(4.dp, OmsColors.Bg, CircleShape)
                        .testTag("home_fab_new_project")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = OmsStrings.quickNew(lang),
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (activeTab) {
                HomeTab.BERANDA -> BerandaTabContent(
                    lang = lang,
                    recentProjects = projects.take(4),
                    onNewProject = { showNewProjectSheet = true },
                    onOpenTutorials = { viewModel.selectHomeTab(HomeTab.TUTORIAL) },
                    onOpenTemplates = { viewModel.selectHomeTab(HomeTab.TEMPLATE) },
                    onImportProject = {
                        viewModel.clearImportError()
                        showImportDialog = true
                    },
                    onSeeAllProjects = { viewModel.selectHomeTab(HomeTab.PROYEK) },
                    onOpenProject = { doc -> viewModel.openProject(doc.project.id, onOpenEditor) },
                    onMoreProject = { menuProject = it }
                )

                HomeTab.TUTORIAL -> TutorialTabContent(
                    lang = lang,
                    onSelectTutorial = { selectedTutorial = it }
                )

                HomeTab.PROYEK -> ProjectsTabContent(
                    lang = lang,
                    projects = projects,
                    sortOrder = sortOrder,
                    onChangeSort = { viewModel.setSortOrder(it) },
                    onNewProject = { showNewProjectSheet = true },
                    onImportProject = {
                        viewModel.clearImportError()
                        showImportDialog = true
                    },
                    onOpenProject = { doc -> viewModel.openProject(doc.project.id, onOpenEditor) },
                    onMoreProject = { menuProject = it }
                )

                HomeTab.TEMPLATE -> TemplatesTabContent(
                    lang = lang,
                    onUseTemplate = { id ->
                        viewModel.createProjectFromTemplate(id, onOpenEditor)
                    }
                )
            }
        }
    }

    if (showNewProjectSheet) {
        NewProjectModal(
            lang = lang,
            onDismiss = { showNewProjectSheet = false },
            onCreate = { name, ratio, res, fps, dur, bg, cw, ch ->
                showNewProjectSheet = false
                viewModel.createNewProject(name, ratio, res, fps, dur, bg, cw, ch, onOpenEditor)
            }
        )
    }

    if (showImportDialog) {
        ImportProjectDialog(
            lang = lang,
            importUi = importUi,
            onDismiss = {
                if (!importUi.isImporting) {
                    viewModel.clearImportError()
                    showImportDialog = false
                }
            },
            onPickFile = {
                projectFileImportLauncher.launch(arrayOf("application/json", "text/xml", "application/xml", "application/zip", "application/octet-stream", "*/*"))
            },
            onImportLink = { url ->
                viewModel.importProjectLink(url) {
                    showImportDialog = false
                    onOpenEditor()
                }
            }
        )
    }

    selectedTutorial?.let { tut ->
        AlertDialog(
            onDismissRequest = { selectedTutorial = null },
            containerColor = OmsColors.Panel,
            title = {
                Text(
                    text = if (lang == AppLang.ID) tut.titleId else tut.titleEn,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val steps = if (lang == AppLang.ID) tut.stepsId else tut.stepsEn
                    steps.forEach { step ->
                        Text(step, style = MaterialTheme.typography.bodyMedium, color = OmsColors.Text)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedTutorial = null }) {
                    Text(OmsStrings.tr(lang, "Mengerti", "Got it"), color = OmsColors.Accent)
                }
            }
        )
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            containerColor = OmsColors.Panel,
            title = { Text("Open Motion Studio", style = MaterialTheme.typography.titleMedium) },
            text = {
                Text(
                    text = OmsStrings.tr(
                        lang,
                        "Open Motion adalah studio animasi keyframe, path vektor Bezier, efek visual, dan komposisi 3D native di Android (MIT Open Source).",
                        "Open Motion is a native Android keyframe animation, Bezier vector path, visual effects, and 3D compositing studio (MIT Open Source)."
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = OmsColors.Muted
                )
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("OK", color = OmsColors.Accent)
                }
            }
        )
    }

    menuProject?.let { doc ->
        AlertDialog(
            onDismissRequest = { menuProject = null },
            containerColor = OmsColors.Panel,
            title = { Text(doc.project.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(
                        onClick = {
                            menuProject = null
                            viewModel.openProject(doc.project.id, onOpenEditor)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(OmsStrings.tr(lang, "Buka di Editor", "Open in Editor"), color = OmsColors.Accent)
                    }
                    TextButton(
                        onClick = {
                            renameTarget = doc
                            menuProject = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(OmsStrings.tr(lang, "Ubah Nama", "Rename"), color = OmsColors.Text)
                    }
                    TextButton(
                        onClick = {
                            viewModel.duplicateProject(doc)
                            menuProject = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(OmsStrings.tr(lang, "Duplikat Proyek", "Duplicate Project"), color = OmsColors.Text)
                    }
                    TextButton(
                        onClick = {
                            viewModel.deleteProject(doc.project.id)
                            menuProject = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(OmsStrings.tr(lang, "Hapus Proyek", "Delete Project"), color = OmsColors.Danger)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { menuProject = null }) {
                    Text(OmsStrings.tr(lang, "Tutup", "Close"), color = OmsColors.Muted)
                }
            }
        )
    }

    renameTarget?.let { doc ->
        var newName by remember(doc.project.id) { mutableStateOf(doc.project.name) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            containerColor = OmsColors.Panel,
            title = { Text(OmsStrings.tr(lang, "Ubah Nama Proyek", "Rename Project")) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    label = { Text(OmsStrings.projectNameLabel(lang)) }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.renameProject(doc, newName)
                    renameTarget = null
                }) {
                    Text(OmsStrings.tr(lang, "Simpan", "Save"), color = OmsColors.Accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text(OmsStrings.tr(lang, "Batal", "Cancel"), color = OmsColors.Muted)
                }
            }
        )
    }
}

@Composable
private fun HomeBottomNavItem(
    icon: ImageVector,
    activeIcon: ImageVector,
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = if (selected) activeIcon else icon,
            contentDescription = label,
            tint = if (selected) OmsColors.Accent else OmsColors.Muted,
            modifier = Modifier.size(21.dp)
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) OmsColors.Accent else OmsColors.Muted
        )
    }
}

@Composable
private fun BerandaTabContent(
    lang: AppLang,
    recentProjects: List<ProjectDocument>,
    onNewProject: () -> Unit,
    onOpenTutorials: () -> Unit,
    onOpenTemplates: () -> Unit,
    onImportProject: () -> Unit,
    onSeeAllProjects: () -> Unit,
    onOpenProject: (ProjectDocument) -> Unit,
    onMoreProject: (ProjectDocument) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                OmsColors.Mint.copy(alpha = 0.16f),
                                OmsColors.Panel.copy(alpha = 0.95f)
                            )
                        )
                    )
                    .border(1.dp, OmsColors.Mint.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                    .padding(18.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = OmsStrings.heroTitle(lang),
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White
                    )
                    Text(
                        text = OmsStrings.heroSubtitle(lang),
                        style = MaterialTheme.typography.bodyMedium,
                        color = OmsColors.Muted
                    )
                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = onNewProject,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = OmsColors.Accent,
                            contentColor = Color(0xFF071B15)
                        ),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.testTag("hero_new_project_btn")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(OmsStrings.startNewProject(lang), fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    QuickActionCard(
                        icon = Icons.Filled.Add,
                        iconTint = OmsColors.Mint,
                        title = OmsStrings.quickNew(lang),
                        sub = OmsStrings.quickNewSub(lang),
                        modifier = Modifier.weight(1f).testTag("quick_new_btn"),
                        onClick = onNewProject
                    )
                    QuickActionCard(
                        icon = Icons.Filled.PlayArrow,
                        iconTint = OmsColors.Purple,
                        title = OmsStrings.quickTut(lang),
                        sub = OmsStrings.quickTutSub(lang),
                        modifier = Modifier.weight(1f).testTag("quick_tut_btn"),
                        onClick = onOpenTutorials
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    QuickActionCard(
                        icon = Icons.Filled.AutoAwesomeMosaic,
                        iconTint = Color(0xFFFF907C),
                        title = OmsStrings.quickTmpl(lang),
                        sub = OmsStrings.quickTmplSub(lang),
                        modifier = Modifier.weight(1f).testTag("quick_tmpl_btn"),
                        onClick = onOpenTemplates
                    )
                    QuickActionCard(
                        icon = Icons.Filled.FileOpen,
                        iconTint = OmsColors.Orange,
                        title = OmsStrings.quickImport(lang),
                        sub = OmsStrings.quickImportSub(lang),
                        modifier = Modifier.weight(1f).testTag("quick_import_btn"),
                        onClick = onImportProject
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(OmsStrings.recentProjects(lang), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onSeeAllProjects) {
                    Text(OmsStrings.seeAll(lang), color = OmsColors.Mint, fontSize = 12.sp)
                }
            }
        }

        if (recentProjects.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(OmsColors.Card)
                        .border(1.dp, OmsColors.Border, RoundedCornerShape(12.dp))
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(OmsStrings.noProjectsHint(lang), color = OmsColors.Muted, style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else {
            items(recentProjects, key = { it.project.id }) { doc ->
                ProjectCardRow(
                    doc = doc,
                    onClick = { onOpenProject(doc) },
                    onMore = { onMoreProject(doc) }
                )
            }
        }
        item { Spacer(Modifier.height(36.dp)) }
    }
}

@Composable
private fun QuickActionCard(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    sub: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(OmsColors.Card)
            .border(1.dp, OmsColors.Border.copy(alpha = 0.65f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconTint.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = title, tint = iconTint, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = OmsColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, fontSize = 10.sp, color = OmsColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ProjectCardRow(
    doc: ProjectDocument,
    onClick: () -> Unit,
    onMore: () -> Unit
) {
    val dateFmt = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(OmsColors.Card)
            .border(1.dp, OmsColors.Border.copy(alpha = 0.65f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(width = 74.dp, height = 52.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF0F1320), Color(0xFF242940)))),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .border(2.dp, OmsColors.Accent, RoundedCornerShape(4.dp))
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = doc.project.name,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = OmsColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${doc.project.ratio} • ${doc.project.width}×${doc.project.height} • ${doc.project.fps}fps • ${doc.layers.size} layer",
                fontSize = 11.sp,
                color = OmsColors.Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = dateFmt.format(Date(doc.project.updatedAt)),
                fontSize = 10.sp,
                color = OmsColors.Muted.copy(alpha = 0.7f)
            )
        }
        IconButton(onClick = onMore) {
            Icon(Icons.Filled.MoreVert, contentDescription = "Opsi Proyek", tint = OmsColors.Muted)
        }
    }
}

@Composable
private fun TutorialTabContent(
    lang: AppLang,
    onSelectTutorial: (TutorialItem) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                text = OmsStrings.tr(lang, "Panduan & Tutorial Editor", "Editor Guides & Tutorials"),
                style = MaterialTheme.typography.titleMedium
            )
        }
        items(TUTORIAL_ITEMS, key = { it.id }) { tut ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(OmsColors.Card)
                    .border(1.dp, OmsColors.Border.copy(alpha = 0.65f), RoundedCornerShape(14.dp))
                    .clickable { onSelectTutorial(tut) }
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(OmsColors.Accent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(tut.icon, fontSize = 22.sp, color = OmsColors.Accent)
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = if (lang == AppLang.ID) tut.titleId else tut.titleEn,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = OmsColors.Text
                    )
                    Text(
                        text = if (lang == AppLang.ID) tut.descId else tut.descEn,
                        fontSize = 11.sp,
                        color = OmsColors.Muted
                    )
                    Text(
                        text = tut.tag,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = OmsColors.Purple
                    )
                }
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = OmsColors.Muted)
            }
        }
        item { Spacer(Modifier.height(36.dp)) }
    }
}

@Composable
private fun ProjectsTabContent(
    lang: AppLang,
    projects: List<ProjectDocument>,
    sortOrder: ProjectSortOrder,
    onChangeSort: (ProjectSortOrder) -> Unit,
    onNewProject: () -> Unit,
    onImportProject: () -> Unit,
    onOpenProject: (ProjectDocument) -> Unit,
    onMoreProject: (ProjectDocument) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "WORKSPACE LOKAL",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = OmsColors.Accent
                )
                Text(
                    text = "${OmsStrings.yourProjects(lang)} (${projects.size})",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            OutlinedButton(
                onClick = onImportProject,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.testTag("projects_tab_import_btn")
            ) {
                Icon(Icons.Filled.FileOpen, contentDescription = null, modifier = Modifier.size(16.dp), tint = OmsColors.Orange)
                Spacer(Modifier.width(4.dp))
                Text(OmsStrings.tr(lang, "Impor File / Link", "Import File / Link"), fontSize = 11.sp, color = OmsColors.Text)
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ProjectSortOrder.entries.forEach { order ->
                    val label = when (order) {
                        ProjectSortOrder.NEWEST -> OmsStrings.tr(lang, "Terbaru", "Newest")
                        ProjectSortOrder.OLDEST -> OmsStrings.tr(lang, "Terlama", "Oldest")
                        ProjectSortOrder.NAME -> OmsStrings.tr(lang, "Nama", "Name")
                    }
                FilterChip(
                    selected = sortOrder == order,
                    onClick = { onChangeSort(order) },
                    label = { Text(label, fontSize = 11.sp) }
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        if (projects.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(OmsStrings.noProjectsHint(lang), color = OmsColors.Muted)
                    Button(
                        onClick = onNewProject,
                        colors = ButtonDefaults.buttonColors(containerColor = OmsColors.Accent, contentColor = Color(0xFF071B15))
                    ) {
                        Text(OmsStrings.quickNew(lang), fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 42.dp)
            ) {
                items(projects, key = { it.project.id }) { doc ->
                    ProjectCardRow(
                        doc = doc,
                        onClick = { onOpenProject(doc) },
                        onMore = { onMoreProject(doc) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TemplatesTabContent(
    lang: AppLang,
    onUseTemplate: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = OmsStrings.tr(lang, "Templat Desain & Motion", "Motion & Design Templates"),
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(12.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 42.dp)
        ) {
            items(ProjectTemplates.catalog, key = { it.id }) { item ->
                val accent = Color(android.graphics.Color.parseColor(item.accentHex))
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(OmsColors.Card)
                        .border(1.dp, OmsColors.Border.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(84.dp)
                            .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.28f), OmsColors.Panel2))),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = item.badge,
                            color = accent,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp
                        )
                    }
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (lang == AppLang.ID) item.titleId else item.titleEn,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = OmsColors.Text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (lang == AppLang.ID) item.descId else item.descEn,
                            fontSize = 10.sp,
                            color = OmsColors.Muted,
                            minLines = 2,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        Button(
                            onClick = { onUseTemplate(item.id) },
                            modifier = Modifier.fillMaxWidth().testTag("use_template_${item.id}"),
                            colors = ButtonDefaults.buttonColors(containerColor = OmsColors.Accent, contentColor = Color(0xFF071B15)),
                            contentPadding = PaddingValues(vertical = 6.dp)
                        ) {
                            Text(
                                text = OmsStrings.tr(lang, "Gunakan Templat", "Use Template"),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewProjectModal(
    lang: AppLang,
    onDismiss: () -> Unit,
    onCreate: (String, String, Int, Int, Float, String, Int, Int) -> Unit
) {
    var name by remember { mutableStateOf(if (lang == AppLang.ID) "Proyek Baru" else "New Project") }
    var ratio by remember { mutableStateOf("9:16") }
    var resolution by remember { mutableStateOf(1080) }
    var fps by remember { mutableStateOf(30) }
    var duration by remember { mutableStateOf("10") }
    var bgHex by remember { mutableStateOf("#10121D") }
    var customW by remember { mutableStateOf("1080") }
    var customH by remember { mutableStateOf("1920") }

    val bgChoices = listOf("#10121D", "#000000", "#FFFFFF", "#1B1330", "#0A2220", "#1F2937")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = OmsColors.Panel,
        contentColor = OmsColors.Text
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 10.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = OmsStrings.createProjectTitle(lang),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(OmsStrings.projectNameLabel(lang)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("new_project_name_input")
            )

            Text(OmsStrings.aspectRatioLabel(lang), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = OmsColors.Muted)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AspectRatioPresets.ratios.forEach { r ->
                    val selected = ratio == r
                    Column(
                        modifier = Modifier
                            .width(68.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) OmsColors.Accent.copy(alpha = 0.16f) else OmsColors.Card)
                            .border(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) OmsColors.Accent else OmsColors.Border,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { ratio = r }
                            .padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val (bw, bh) = when (r) {
                            "9:16" -> 14.dp to 24.dp
                            "16:9" -> 26.dp to 15.dp
                            "4:5" -> 18.dp to 22.dp
                            "1:1" -> 20.dp to 20.dp
                            "4:3" -> 24.dp to 18.dp
                            else -> 22.dp to 22.dp
                        }
                        Box(
                            modifier = Modifier
                                .height(26.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(bw, bh)
                                    .border(1.5.dp, if (selected) OmsColors.Accent else OmsColors.Text, RoundedCornerShape(2.dp))
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(r, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (selected) OmsColors.Accent else OmsColors.Text)
                    }
                }
            }

            if (ratio == "Custom") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = customW,
                        onValueChange = { customW = it.filter(Char::isDigit) },
                        label = { Text("Width") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = customH,
                        onValueChange = { customH = it.filter(Char::isDigit) },
                        label = { Text("Height") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(OmsStrings.resolutionLabel(lang), fontSize = 11.sp, color = OmsColors.Muted)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AspectRatioPresets.resolutions.forEach { res ->
                            FilterChip(
                                selected = resolution == res,
                                onClick = { resolution = res },
                                label = { Text("${res}p", fontSize = 11.sp) }
                            )
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(OmsStrings.fpsLabel(lang), fontSize = 11.sp, color = OmsColors.Muted)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AspectRatioPresets.fpsOptions.forEach { f ->
                            FilterChip(
                                selected = fps == f,
                                onClick = { fps = f },
                                label = { Text("$f", fontSize = 11.sp) }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = duration,
                    onValueChange = { duration = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text(OmsStrings.durationLabel(lang)) },
                    singleLine = true,
                    modifier = Modifier.width(120.dp)
                )
            }

            Text(OmsStrings.backgroundLabel(lang), fontSize = 11.sp, color = OmsColors.Muted)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                bgChoices.forEach { hex ->
                    val c = Color(android.graphics.Color.parseColor(hex))
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(c)
                            .border(
                                width = if (bgHex == hex) 2.5.dp else 1.dp,
                                color = if (bgHex == hex) OmsColors.Accent else OmsColors.Border,
                                shape = CircleShape
                            )
                            .clickable { bgHex = hex }
                    )
                }
            }

            Button(
                onClick = {
                    val d = duration.toFloatOrNull() ?: 10f
                    val cw = customW.toIntOrNull() ?: 1080
                    val ch = customH.toIntOrNull() ?: 1920
                    onCreate(name, ratio, resolution, fps, d, bgHex, cw, ch)
                },
                colors = ButtonDefaults.buttonColors(containerColor = OmsColors.Accent, contentColor = Color(0xFF061B15)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("create_project_submit_btn")
            ) {
                Text(OmsStrings.createBtn(lang), fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun ImportProjectDialog(
    lang: AppLang,
    importUi: ImportUiState,
    onDismiss: () -> Unit,
    onPickFile: () -> Unit,
    onImportLink: (String) -> Unit
) {
    var linkText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = OmsColors.Panel,
        title = {
            Text(OmsStrings.importTitle(lang), style = MaterialTheme.typography.titleMedium, color = OmsColors.Text)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = OmsStrings.tr(
                        lang,
                        "Pilih berkas proyek dari perangkat (.omp.json, .json, .xml, .amproj, .zip) atau tempel tautan proyek.",
                        "Pick a project file (.omp.json, .json, .xml, .amproj, .zip) or paste a project URL."
                    ),
                    fontSize = 12.sp,
                    color = OmsColors.Muted
                )

                Button(
                    onClick = onPickFile,
                    enabled = !importUi.isImporting,
                    colors = ButtonDefaults.buttonColors(containerColor = OmsColors.Accent, contentColor = Color(0xFF061B15)),
                    modifier = Modifier.fillMaxWidth().testTag("import_pick_file_btn")
                ) {
                    Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        OmsStrings.tr(lang, "Pilih File (.json / .xml / .amproj)", "Pick Project File (.json / .xml / .amproj)"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }

                HorizontalDivider(color = OmsColors.Border)

                Text(
                    text = OmsStrings.tr(lang, "ATAU IMPOR VIA LINK / URL PROYEK", "OR IMPORT VIA PROJECT LINK / URL"),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = OmsColors.Cyan
                )

                OutlinedTextField(
                    value = linkText,
                    onValueChange = { linkText = it },
                    enabled = !importUi.isImporting,
                    placeholder = { Text("https://... (.amproj, .xml, .json, Alight / Drive)", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("import_link_input")
                )

                Button(
                    onClick = { onImportLink(linkText) },
                    enabled = !importUi.isImporting && linkText.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = OmsColors.Purple, contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth().testTag("import_link_submit_btn")
                ) {
                    Icon(Icons.Filled.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        OmsStrings.tr(lang, "Impor dari Link Proyek", "Import from Project Link"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }

                if (importUi.isImporting) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = OmsColors.Accent, strokeWidth = 2.dp)
                        Text(OmsStrings.tr(lang, "Mengunduh & memproses proyek...", "Downloading & parsing project..."), fontSize = 12.sp, color = OmsColors.Mint)
                    }
                }

                importUi.errorMessage?.let { err ->
                    Text(
                        text = err,
                        color = OmsColors.Danger,
                        fontSize = 11.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, enabled = !importUi.isImporting) {
                Text(OmsStrings.tr(lang, "Tutup", "Close"), color = OmsColors.Muted)
            }
        }
    )
}
