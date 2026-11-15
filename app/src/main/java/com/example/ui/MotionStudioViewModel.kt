package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.ProjectRepository
import com.example.engine.AnimationEngine
import com.example.engine.MotionTracker
import com.example.engine.ProjectTemplates
import com.example.model.*
import com.example.render.AudioTrackEngine
import com.example.render.CompositionCanvasRenderer
import com.example.render.ShapePathBuilder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

enum class HomeTab {
    BERANDA, TUTORIAL, PROYEK, TEMPLATE
}

enum class ProjectSortOrder {
    NEWEST, OLDEST, NAME
}

enum class AddHubTab {
    SHAPES, MEDIA, AUDIO, ELEMENTS, TEMPLATES
}

enum class EditorDrawerMode {
    NONE,
    ADD_HUB,
    INSPECTOR_ROOT,
    MOVE_TRANSFORM,
    COLOR,
    BLEND_MASK,
    EFFECTS,
    EFFECT_BROWSER,
    SHAPE_STUDIO,
    TEXT_TYPOGRAPHY,
    CAMERA_3D,
    HIERARCHY,
    CURVE_EDITOR,
    PROJECT_SETTINGS,
    EXPORT_SHEET
}

enum class VectorHandleKind {
    ANCHOR, IN_HANDLE, OUT_HANDLE
}

data class VectorPenSession(
    val layerId: String?,
    val nodes: List<VectorNode>,
    val closed: Boolean,
    val selectedNodeIndex: Int = -1,
    val activeDragKind: VectorHandleKind? = null
)

data class ExportUiState(
    val isExporting: Boolean = false,
    val progress: Float = 0f,
    val statusMessage: String? = null,
    val lastFile: File? = null,
    val lastMimeType: String? = null
)

data class ImportUiState(
    val isImporting: Boolean = false,
    val errorMessage: String? = null
)

data class MotionTrackUiState(
    val selectingTargetPoint: Boolean = false,
    val isTracking: Boolean = false,
    val progress: Float = 0f,
    val statusMessage: String? = null
)

private data class CopiedLayerStyle(
    val colorHex: String,
    val strokeHex: String,
    val strokeWidth: Float,
    val blendMode: BlendModeKind,
    val effects: List<EffectInstance>,
    val repeat: RepeatConfig,
    val layerStyle: LayerStyleConfig
)

class MotionStudioViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext

    private val repository = ProjectRepository(
        context = appContext,
        dao = AppDatabase.getInstance(application).projectDao()
    )

    private val _language = MutableStateFlow(AppLang.ID)
    val language: StateFlow<AppLang> = _language.asStateFlow()

    private val _homeTab = MutableStateFlow(HomeTab.BERANDA)
    val homeTab: StateFlow<HomeTab> = _homeTab.asStateFlow()

    private val _sortOrder = MutableStateFlow(ProjectSortOrder.NEWEST)
    val sortOrder: StateFlow<ProjectSortOrder> = _sortOrder.asStateFlow()

    val projects: StateFlow<List<ProjectDocument>> = combine(
        repository.projectsFlow,
        _sortOrder
    ) { list, sort ->
        when (sort) {
            ProjectSortOrder.NEWEST -> list.sortedByDescending { it.project.updatedAt }
            ProjectSortOrder.OLDEST -> list.sortedBy { it.project.createdAt }
            ProjectSortOrder.NAME -> list.sortedBy { it.project.name.lowercase() }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _activeDoc = MutableStateFlow<ProjectDocument?>(null)
    val activeDoc: StateFlow<ProjectDocument?> = _activeDoc.asStateFlow()

    private val _currentTime = MutableStateFlow(0f)
    val currentTime: StateFlow<Float> = _currentTime.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isLooping = MutableStateFlow(true)
    val isLooping: StateFlow<Boolean> = _isLooping.asStateFlow()

    private val _selectedLayerId = MutableStateFlow<String?>(null)
    val selectedLayerId: StateFlow<String?> = _selectedLayerId.asStateFlow()

    private val _multiSelectedIds = MutableStateFlow<Set<String>>(emptySet())
    val multiSelectedIds: StateFlow<Set<String>> = _multiSelectedIds.asStateFlow()

    private val _drawerMode = MutableStateFlow(EditorDrawerMode.NONE)
    val drawerMode: StateFlow<EditorDrawerMode> = _drawerMode.asStateFlow()

    private val _addHubTab = MutableStateFlow(AddHubTab.SHAPES)
    val addHubTab: StateFlow<AddHubTab> = _addHubTab.asStateFlow()

    private val _curveProp = MutableStateFlow("x")
    val curveProp: StateFlow<String> = _curveProp.asStateFlow()

    private val _cameraViewActive = MutableStateFlow(true)
    val cameraViewActive: StateFlow<Boolean> = _cameraViewActive.asStateFlow()

    private val _showGuides = MutableStateFlow(true)
    val showGuides: StateFlow<Boolean> = _showGuides.asStateFlow()

    private val _viewportZoom = MutableStateFlow(1f)
    val viewportZoom: StateFlow<Float> = _viewportZoom.asStateFlow()

    private val _vectorPenSession = MutableStateFlow<VectorPenSession?>(null)
    val vectorPenSession: StateFlow<VectorPenSession?> = _vectorPenSession.asStateFlow()

    private val _eyedropperTarget = MutableStateFlow<String?>(null) // "fill" | "stroke" | null
    val eyedropperTarget: StateFlow<String?> = _eyedropperTarget.asStateFlow()

    private val _motionTrackUi = MutableStateFlow(MotionTrackUiState())
    val motionTrackUi: StateFlow<MotionTrackUiState> = _motionTrackUi.asStateFlow()

    private val _customFonts = MutableStateFlow<List<String>>(repository.listCustomFonts())
    val customFonts: StateFlow<List<String>> = _customFonts.asStateFlow()

    private val _importUi = MutableStateFlow(ImportUiState())
    val importUi: StateFlow<ImportUiState> = _importUi.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    private val _exportUi = MutableStateFlow(ExportUiState())
    val exportUi: StateFlow<ExportUiState> = _exportUi.asStateFlow()

    private val undoStack = ArrayDeque<ProjectDocument>()
    private val redoStack = ArrayDeque<ProjectDocument>()
    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()
    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private var playbackJob: Job? = null
    private var autoSaveJob: Job? = null
    private var exportJob: Job? = null
    private var trackingJob: Job? = null
    private var clipboardLayer: LayerData? = null
    private var copiedStyle: CopiedLayerStyle? = null

    fun setLanguage(lang: AppLang) {
        _language.value = lang
    }

    fun selectHomeTab(tab: HomeTab) {
        _homeTab.value = tab
    }

    fun setSortOrder(order: ProjectSortOrder) {
        _sortOrder.value = order
    }

    fun showToast(msg: String) {
        _toastMessage.value = msg
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    fun clearImportError() {
        _importUi.value = ImportUiState()
    }

    fun createNewProject(
        name: String,
        ratio: String,
        resolutionShort: Int,
        fps: Int,
        duration: Float,
        bgHex: String,
        customW: Int = 1080,
        customH: Int = 1920,
        onReady: () -> Unit
    ) {
        val (w, h) = AspectRatioPresets.computeDimensions(ratio, resolutionShort, customW, customH)
        val project = ProjectData(
            name = name.ifBlank { if (_language.value == AppLang.ID) "Proyek Baru" else "Untitled Project" },
            width = w,
            height = h,
            fps = fps.coerceIn(12, 120),
            duration = duration.coerceIn(1f, 300f),
            bgHex = bgHex,
            ratio = ratio
        )
        val doc = ProjectDocument(project = project)
        viewModelScope.launch {
            repository.saveProject(doc)
            openDocumentInEditor(doc)
            onReady()
        }
    }

    fun openProject(id: String, onReady: () -> Unit) {
        viewModelScope.launch {
            val doc = repository.loadProject(id) ?: return@launch
            openDocumentInEditor(doc)
            onReady()
        }
    }

    fun createProjectFromTemplate(templateId: String, onReady: () -> Unit) {
        viewModelScope.launch {
            val doc = repository.createFromTemplate(templateId)
            openDocumentInEditor(doc)
            onReady()
        }
    }

    fun duplicateProject(doc: ProjectDocument) {
        viewModelScope.launch {
            val copy = doc.copy(
                project = doc.project.copy(
                    id = newUid("project"),
                    name = "${doc.project.name} (Salinan)",
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
            )
            repository.saveProject(copy)
            showToast(OmsStrings.tr(_language.value, "Proyek diduplikasi", "Project duplicated"))
        }
    }

    fun renameProject(doc: ProjectDocument, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            repository.saveProject(doc.copy(project = doc.project.copy(name = newName.trim())))
        }
    }

    fun deleteProject(id: String) {
        viewModelScope.launch {
            repository.deleteProject(id)
            showToast(OmsStrings.tr(_language.value, "Proyek dihapus", "Project deleted"))
        }
    }

    fun importProjectFile(uri: Uri, onReady: () -> Unit) {
        viewModelScope.launch {
            _importUi.value = ImportUiState(isImporting = true)
            val res = repository.importProjectFromUri(uri)
            res.fold(
                onSuccess = { imported ->
                    _customFonts.value = repository.listCustomFonts()
                    _importUi.value = ImportUiState()
                    openDocumentInEditor(imported)
                    onReady()
                    showToast(OmsStrings.tr(_language.value, "Proyek berhasil diimpor (${imported.layers.size} layer)", "Project imported (${imported.layers.size} layers)"))
                },
                onFailure = { err ->
                    _importUi.value = ImportUiState(isImporting = false, errorMessage = err.message ?: "Gagal impor file")
                }
            )
        }
    }

    fun importProjectLink(url: String, onReady: () -> Unit) {
        viewModelScope.launch {
            _importUi.value = ImportUiState(isImporting = true)
            val res = repository.importProjectFromLink(url)
            res.fold(
                onSuccess = { imported ->
                    _customFonts.value = repository.listCustomFonts()
                    _importUi.value = ImportUiState()
                    openDocumentInEditor(imported)
                    onReady()
                    showToast(OmsStrings.tr(_language.value, "Proyek dari link berhasil diimpor", "Project imported from link"))
                },
                onFailure = { err ->
                    _importUi.value = ImportUiState(isImporting = false, errorMessage = err.message ?: "Gagal mengimpor dari link")
                }
            )
        }
    }

    fun importCustomFont(uri: Uri) {
        viewModelScope.launch {
            val res = repository.importCustomFont(uri)
            res.fold(
                onSuccess = { family ->
                    CompositionCanvasRenderer.clearCustomFontCache()
                    _customFonts.value = repository.listCustomFonts()
                    updateSelectedLayer { it.copy(fontFamily = family) }
                    showToast(OmsStrings.tr(_language.value, "Font '$family' diimpor", "Font '$family' imported"))
                },
                onFailure = { err ->
                    showToast(err.message ?: "Gagal impor font")
                }
            )
        }
    }

    private fun openDocumentInEditor(doc: ProjectDocument) {
        stopPlayback()
        undoStack.clear()
        redoStack.clear()
        _canUndo.value = false
        _canRedo.value = false
        _activeDoc.value = doc
        _currentTime.value = 0f
        _selectedLayerId.value = doc.layers.firstOrNull()?.id
        _multiSelectedIds.value = emptySet()
        _drawerMode.value = EditorDrawerMode.NONE
        _viewportZoom.value = 1f
        _vectorPenSession.value = null
        _eyedropperTarget.value = null
        _motionTrackUi.value = MotionTrackUiState()
        analyzeProjectAudioAsync(doc)
    }

    private fun analyzeProjectAudioAsync(doc: ProjectDocument) {
        viewModelScope.launch {
            doc.layers
                .filter { (it.type == LayerType.AUDIO || it.type == LayerType.VIDEO) && !it.mediaUri.isNullOrBlank() }
                .forEach { layer ->
                    AudioTrackEngine.ensureAnalyzed(appContext, layer.mediaUri)
                }
        }
    }

    fun closeEditor() {
        stopPlayback()
        AudioTrackEngine.releaseAllPlayback()
        val current = _activeDoc.value
        if (current != null) {
            viewModelScope.launch { repository.saveProject(current) }
        }
        _activeDoc.value = null
    }

    private fun pushHistory(snapshot: ProjectDocument) {
        undoStack.addLast(snapshot)
        if (undoStack.size > 40) undoStack.removeFirst()
        redoStack.clear()
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = false
    }

    private fun updateDoc(recordHistory: Boolean = true, transform: (ProjectDocument) -> ProjectDocument) {
        val current = _activeDoc.value ?: return
        if (recordHistory) pushHistory(current)
        val next = transform(current)
        _activeDoc.value = next
        scheduleAutoSave()
    }

    private fun scheduleAutoSave() {
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            delay(450)
            _activeDoc.value?.let { repository.saveProject(it) }
        }
    }

    fun undo() {
        val current = _activeDoc.value ?: return
        if (undoStack.isEmpty()) return
        redoStack.addLast(current)
        _activeDoc.value = undoStack.removeLast()
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
        scheduleAutoSave()
    }

    fun redo() {
        val current = _activeDoc.value ?: return
        if (redoStack.isEmpty()) return
        undoStack.addLast(current)
        _activeDoc.value = redoStack.removeLast()
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
        scheduleAutoSave()
    }

    // Playback & Scrubbing
    fun togglePlay() {
        if (_isPlaying.value) stopPlayback() else startPlayback()
    }

    fun toggleLoop() {
        _isLooping.value = !_isLooping.value
    }

    private fun startPlayback() {
        val doc = _activeDoc.value ?: return
        if (_currentTime.value >= doc.project.duration) _currentTime.value = 0f
        _isPlaying.value = true
        playbackJob?.cancel()
        playbackJob = viewModelScope.launch {
            var lastNs = System.nanoTime()
            while (isActive && _isPlaying.value) {
                val nowNs = System.nanoTime()
                val dt = ((nowNs - lastNs) / 1_000_000_000f).coerceIn(0f, 0.1f)
                lastNs = nowNs
                val curDoc = _activeDoc.value ?: break
                val maxDur = curDoc.project.duration
                val nextT = _currentTime.value + dt
                if (nextT >= maxDur) {
                    if (_isLooping.value) {
                        _currentTime.value = 0f
                    } else {
                        _currentTime.value = maxDur
                        _isPlaying.value = false
                        AudioTrackEngine.syncPlayback(appContext, curDoc, maxDur, false)
                        break
                    }
                } else {
                    _currentTime.value = nextT
                }
                AudioTrackEngine.syncPlayback(appContext, curDoc, _currentTime.value, true)
                delay(16)
            }
        }
    }

    fun stopPlayback() {
        _isPlaying.value = false
        playbackJob?.cancel()
        playbackJob = null
        _activeDoc.value?.let { AudioTrackEngine.syncPlayback(appContext, it, _currentTime.value, false) }
    }

    fun seekTo(timeSec: Float) {
        val maxDur = _activeDoc.value?.project?.duration ?: 10f
        _currentTime.value = timeSec.coerceIn(0f, maxDur)
        if (_isPlaying.value) {
            _activeDoc.value?.let { AudioTrackEngine.syncPlayback(appContext, it, _currentTime.value, true) }
        }
    }

    fun jumpToPrevKeyframe() {
        val t = _currentTime.value
        val doc = _activeDoc.value ?: return
        val layer = doc.layers.find { it.id == _selectedLayerId.value }
        val allTimes = if (layer != null) {
            layer.keyframes.values.flatten().map { layer.start + it.time }
        } else {
            doc.markers.map { it.time }
        }
        val prev = allTimes.filter { it < t - 0.03f }.maxOrNull() ?: 0f
        seekTo(prev)
    }

    fun jumpToNextKeyframe() {
        val t = _currentTime.value
        val doc = _activeDoc.value ?: return
        val layer = doc.layers.find { it.id == _selectedLayerId.value }
        val allTimes = if (layer != null) {
            layer.keyframes.values.flatten().map { layer.start + it.time }
        } else {
            doc.markers.map { it.time }
        }
        val next = allTimes.filter { it > t + 0.03f }.minOrNull() ?: doc.project.duration
        seekTo(next)
    }

    // Drawer & Selection
    fun selectLayer(id: String?) {
        _selectedLayerId.value = id
        if (id == null && _drawerMode.value !in listOf(EditorDrawerMode.ADD_HUB, EditorDrawerMode.PROJECT_SETTINGS, EditorDrawerMode.EXPORT_SHEET, EditorDrawerMode.NONE)) {
            _drawerMode.value = EditorDrawerMode.NONE
        } else if (id != null && _drawerMode.value == EditorDrawerMode.NONE) {
            _drawerMode.value = EditorDrawerMode.INSPECTOR_ROOT
        }
    }

    fun toggleMultiSelect(id: String) {
        val cur = _multiSelectedIds.value.toMutableSet()
        if (id in cur) cur.remove(id) else cur.add(id)
        _multiSelectedIds.value = cur
    }

    fun clearMultiSelect() {
        _multiSelectedIds.value = emptySet()
    }

    fun alignMultiSelected(align: String) {
        val ids = _multiSelectedIds.value
        val doc = _activeDoc.value ?: return
        if (ids.size < 2) return
        val pw = doc.project.width.toFloat()
        val ph = doc.project.height.toFloat()
        updateDoc { d ->
            d.copy(layers = d.layers.map { l ->
                if (l.id !in ids) l else when (align) {
                    "left" -> l.copy(x = l.w * 0.5f + 24f)
                    "centerH" -> l.copy(x = pw * 0.5f)
                    "right" -> l.copy(x = pw - l.w * 0.5f - 24f)
                    "top" -> l.copy(y = l.h * 0.5f + 24f)
                    "centerV" -> l.copy(y = ph * 0.5f)
                    "bottom" -> l.copy(y = ph - l.h * 0.5f - 24f)
                    else -> l
                }
            })
        }
    }

    fun deleteMultiSelected() {
        val ids = _multiSelectedIds.value
        if (ids.isEmpty()) return
        updateDoc { doc ->
            doc.copy(
                layers = doc.layers.filterNot { it.id in ids }
                    .map { if (it.parentId in ids) it.copy(parentId = null) else it }
            )
        }
        _multiSelectedIds.value = emptySet()
        _selectedLayerId.value = _activeDoc.value?.layers?.lastOrNull()?.id
    }

    fun exportSelectedLayersJson() {
        val doc = _activeDoc.value ?: return
        val ids = if (_multiSelectedIds.value.isNotEmpty()) _multiSelectedIds.value else setOfNotNull(_selectedLayerId.value)
        val subset = doc.copy(
            project = doc.project.copy(name = "${doc.project.name}_layers"),
            layers = doc.layers.filter { it.id in ids }
        )
        viewModelScope.launch {
            val file = repository.exportJsonFile(subset)
            repository.shareFile(file, "application/json")
        }
    }

    fun openDrawer(mode: EditorDrawerMode) {
        _drawerMode.value = mode
    }

    fun openAddHub(tab: AddHubTab = AddHubTab.SHAPES) {
        _addHubTab.value = tab
        _drawerMode.value = EditorDrawerMode.ADD_HUB
    }

    fun setAddHubTab(tab: AddHubTab) {
        _addHubTab.value = tab
    }

    fun toggleCameraView() {
        _cameraViewActive.value = !_cameraViewActive.value
    }

    fun toggleGuides() {
        _showGuides.value = !_showGuides.value
    }

    fun toggle3dScene() {
        updateDoc { doc ->
            doc.copy(project = doc.project.copy(space3dActive = !doc.project.space3dActive))
        }
    }

    fun setViewportZoom(zoom: Float) {
        _viewportZoom.value = zoom.coerceIn(0.25f, 3.5f)
    }

    fun setTimelineZoom(pxPerSec: Float) {
        updateDoc(recordHistory = false) { doc ->
            doc.copy(project = doc.project.copy(timelinePxPerSec = pxPerSec.coerceIn(24f, 180f)))
        }
    }

    fun updateProjectSettings(
        name: String? = null,
        duration: Float? = null,
        fps: Int? = null,
        bgHex: String? = null,
        gridEnabled: Boolean? = null
    ) {
        updateDoc { doc ->
            val p = doc.project
            doc.copy(
                project = p.copy(
                    name = name ?: p.name,
                    duration = duration?.coerceIn(1f, 300f) ?: p.duration,
                    fps = fps?.coerceIn(12, 120) ?: p.fps,
                    bgHex = bgHex ?: p.bgHex,
                    gridEnabled = gridEnabled ?: p.gridEnabled
                )
            )
        }
    }

    // Adding Layers
    fun addShapeLayer(kind: ShapeKind) {
        val doc = _activeDoc.value ?: return
        val p = doc.project
        val shortEdge = min(p.width, p.height).toFloat()
        val size = shortEdge * 0.45f
        val start = _currentTime.value.coerceIn(0f, (p.duration - 0.2f).coerceAtLeast(0f))
        val dur = min(3f, p.duration - start).coerceAtLeast(0.5f)
        val label = if (_language.value == AppLang.ID) kind.labelId else kind.labelEn
        val layer = LayerData(
            type = LayerType.SHAPE,
            name = label,
            start = start,
            duration = dur,
            x = p.width * 0.5f,
            y = p.height * 0.5f,
            w = if (kind == ShapeKind.LINE || kind == ShapeKind.PILL) size * 1.25f else size,
            h = when (kind) {
                ShapeKind.LINE -> (size * 0.14f).coerceAtLeast(18f)
                ShapeKind.PILL -> size * 0.52f
                ShapeKind.ARROW -> size * 0.68f
                else -> size
            },
            shapeKind = kind,
            colorHex = "#00D7A7"
        )
        updateDoc { it.copy(layers = it.layers + layer) }
        _selectedLayerId.value = layer.id
        _drawerMode.value = EditorDrawerMode.INSPECTOR_ROOT
    }

    fun addTextLayer(text: String = "Open Motion") {
        val doc = _activeDoc.value ?: return
        val p = doc.project
        val start = _currentTime.value.coerceIn(0f, (p.duration - 0.2f).coerceAtLeast(0f))
        val dur = min(3f, p.duration - start).coerceAtLeast(0.5f)
        val layer = LayerData(
            type = LayerType.TEXT,
            name = text.take(18).ifBlank { "Teks" },
            start = start,
            duration = dur,
            x = p.width * 0.5f,
            y = p.height * 0.5f,
            w = p.width * 0.78f,
            h = p.height * 0.2f,
            textContent = text,
            fontSize = (p.height * 0.065f).coerceIn(36f, 160f),
            colorHex = "#F2F4FB"
        )
        updateDoc { it.copy(layers = it.layers + layer) }
        _selectedLayerId.value = layer.id
        _drawerMode.value = EditorDrawerMode.TEXT_TYPOGRAPHY
    }

    fun addMediaLayer(uri: Uri, isVideo: Boolean) {
        val doc = _activeDoc.value ?: return
        val p = doc.project
        val start = _currentTime.value.coerceIn(0f, (p.duration - 0.2f).coerceAtLeast(0f))
        val dur = min(4f, p.duration - start).coerceAtLeast(0.5f)
        val layer = LayerData(
            type = if (isVideo) LayerType.VIDEO else LayerType.IMAGE,
            name = if (isVideo) "Video" else "Gambar",
            start = start,
            duration = dur,
            x = p.width * 0.5f,
            y = p.height * 0.5f,
            w = p.width * 0.72f,
            h = p.height * 0.45f,
            mediaUri = uri.toString()
        )
        updateDoc { it.copy(layers = it.layers + layer) }
        _selectedLayerId.value = layer.id
        _drawerMode.value = EditorDrawerMode.INSPECTOR_ROOT
        if (isVideo) viewModelScope.launch { AudioTrackEngine.ensureAnalyzed(appContext, uri.toString()) }
    }

    fun addAudioLayer(uri: Uri) {
        val doc = _activeDoc.value ?: return
        val p = doc.project
        val start = _currentTime.value.coerceIn(0f, (p.duration - 0.2f).coerceAtLeast(0f))
        val dur = min(5f, p.duration - start).coerceAtLeast(0.5f)
        val layer = LayerData(
            type = LayerType.AUDIO,
            name = "Audio Track",
            start = start,
            duration = dur,
            w = 0f,
            h = 0f,
            mediaUri = uri.toString()
        )
        updateDoc { it.copy(layers = it.layers + layer) }
        _selectedLayerId.value = layer.id
        _drawerMode.value = EditorDrawerMode.INSPECTOR_ROOT
        viewModelScope.launch { AudioTrackEngine.ensureAnalyzed(appContext, uri.toString()) }
    }

    fun relinkSelectedLayerMedia(uri: Uri) {
        val id = _selectedLayerId.value ?: return
        updateLayerById(id) { it.copy(mediaUri = uri.toString()) }
        viewModelScope.launch { AudioTrackEngine.ensureAnalyzed(appContext, uri.toString()) }
        showToast(OmsStrings.tr(_language.value, "Media berhasil ditautkan ulang", "Media relinked successfully"))
    }

    fun addElementLayer(type: LayerType, meshKind: MeshKind = MeshKind.CUBE) {
        val doc = _activeDoc.value ?: return
        val p = doc.project
        val start = _currentTime.value.coerceIn(0f, (p.duration - 0.2f).coerceAtLeast(0f))
        val dur = min(4f, p.duration - start).coerceAtLeast(0.5f)
        val layer = when (type) {
            LayerType.CAMERA -> LayerData(
                type = LayerType.CAMERA,
                name = "Kamera 3D",
                start = start,
                duration = dur,
                space3d = true,
                x = 0f,
                y = 0f,
                z = 1400f,
                fov = 55f
            )
            LayerType.MESH -> LayerData(
                type = LayerType.MESH,
                name = meshKind.label,
                meshKind = meshKind,
                start = start,
                duration = dur,
                space3d = true,
                x = p.width * 0.5f,
                y = p.height * 0.5f,
                w = min(p.width, p.height) * 0.34f,
                h = min(p.width, p.height) * 0.34f,
                depth = min(p.width, p.height) * 0.34f,
                rotX = -18f,
                rotY = 28f,
                colorHex = if (meshKind == MeshKind.SPHERE) "#6CA8FF" else "#FF907C"
            )
            LayerType.NULL -> LayerData(
                type = LayerType.NULL,
                name = "Null / Controller",
                start = start,
                duration = dur,
                x = p.width * 0.5f,
                y = p.height * 0.5f,
                w = 140f,
                h = 140f,
                colorHex = "#FFD65C"
            )
            LayerType.GROUP -> LayerData(
                type = LayerType.GROUP,
                name = "Grup Kosong",
                start = start,
                duration = dur,
                x = p.width * 0.5f,
                y = p.height * 0.5f,
                w = p.width * 0.8f,
                h = p.height * 0.6f,
                colorHex = "#65D4C9"
            )
            else -> return
        }
        updateDoc {
            it.copy(
                project = if (type == LayerType.CAMERA || type == LayerType.MESH) it.project.copy(space3dActive = true) else it.project,
                layers = it.layers + layer
            )
        }
        _selectedLayerId.value = layer.id
        _drawerMode.value = if (type == LayerType.CAMERA || type == LayerType.MESH) EditorDrawerMode.CAMERA_3D else EditorDrawerMode.INSPECTOR_ROOT
    }

    fun appendTemplateIntoCurrentProject(templateId: String) {
        val tmpl = ProjectTemplates.createFromTemplate(templateId)
        val offsetLayers = tmpl.layers.map { it.copy(id = newUid("layer")) }
        updateDoc { doc ->
            doc.copy(
                project = doc.project.copy(space3dActive = doc.project.space3dActive || tmpl.project.space3dActive),
                layers = doc.layers + offsetLayers
            )
        }
        _selectedLayerId.value = offsetLayers.lastOrNull()?.id
        _drawerMode.value = EditorDrawerMode.NONE
        showToast(OmsStrings.tr(_language.value, "Templat ditambahkan ke proyek", "Template added to composition"))
    }

    // Layer Mutators
    fun updateSelectedLayer(recordHistory: Boolean = true, transform: (LayerData) -> LayerData) {
        val id = _selectedLayerId.value ?: return
        updateLayerById(id, recordHistory, transform)
    }

    fun updateLayerById(id: String, recordHistory: Boolean = true, transform: (LayerData) -> LayerData) {
        updateDoc(recordHistory) { doc ->
            doc.copy(layers = doc.layers.map { if (it.id == id) transform(it) else it })
        }
    }

    fun setLayerAnimatedProperty(prop: String, value: Float, recordHistory: Boolean = true) {
        val t = _currentTime.value
        updateSelectedLayer(recordHistory) { layer ->
            AnimationEngine.setOrUpdateKeyframeIfAnimated(layer, prop, t, value)
        }
    }

    fun toggleKeyframe(prop: String) {
        val t = _currentTime.value
        updateSelectedLayer { layer ->
            val currentVal = AnimationEngine.evaluateProperty(layer, prop, t)
            AnimationEngine.toggleKeyframe(layer, prop, t, currentVal)
        }
    }

    fun openCurveEditorFor(prop: String) {
        _curveProp.value = prop
        _drawerMode.value = EditorDrawerMode.CURVE_EDITOR
    }

    fun applyEasingPresetToSelectedProp(prop: String, easing: EasingSpec) {
        val t = _currentTime.value
        updateSelectedLayer { layer ->
            val localT = (t - layer.start).coerceAtLeast(0f)
            val keys = layer.keyframes[prop]?.sortedBy { it.time } ?: return@updateSelectedLayer layer
            if (keys.size < 2) return@updateSelectedLayer layer
            val targetIdx = keys.indexOfLast { it.time <= localT + 0.04f }.coerceIn(0, keys.size - 2)
            val updated = keys.toMutableList()
            updated[targetIdx] = updated[targetIdx].copy(easing = easing)
            layer.copy(keyframes = layer.keyframes + (prop to updated))
        }
    }

    fun beginCanvasDragUndo() {
        val current = _activeDoc.value ?: return
        val id = _selectedLayerId.value ?: return
        val layer = current.layers.find { it.id == id } ?: return
        if (!layer.locked) pushHistory(current)
    }

    fun dragLayerOnCanvas(dx: Float, dy: Float) {
        val id = _selectedLayerId.value ?: return
        val doc = _activeDoc.value ?: return
        val layer = doc.layers.find { it.id == id } ?: return
        if (layer.locked) return
        val t = _currentTime.value
        val curX = AnimationEngine.evaluateProperty(layer, "x", t)
        val curY = AnimationEngine.evaluateProperty(layer, "y", t)
        updateLayerById(id, recordHistory = false) { l ->
            val updatedX = AnimationEngine.setOrUpdateKeyframeIfAnimated(l, "x", t, curX + dx)
            AnimationEngine.setOrUpdateKeyframeIfAnimated(updatedX, "y", t, curY + dy)
        }
    }

    fun duplicateSelectedLayer() {
        val id = _selectedLayerId.value ?: return
        val doc = _activeDoc.value ?: return
        val orig = doc.layers.find { it.id == id } ?: return
        val copy = orig.copy(
            id = newUid("layer"),
            name = "${orig.name} (Salinan)",
            x = orig.x + 36f,
            y = orig.y + 36f
        )
        updateDoc { it.copy(layers = it.layers + copy) }
        _selectedLayerId.value = copy.id
    }

    fun copySelectedLayer() {
        val id = _selectedLayerId.value ?: return
        clipboardLayer = _activeDoc.value?.layers?.find { it.id == id }
        if (clipboardLayer != null) {
            showToast(OmsStrings.tr(_language.value, "Layer disalin", "Layer copied"))
        }
    }

    fun pasteClipboardLayer() {
        val clip = clipboardLayer ?: return
        val copy = clip.copy(id = newUid("layer"), start = _currentTime.value)
        updateDoc { it.copy(layers = it.layers + copy) }
        _selectedLayerId.value = copy.id
    }

    fun copyStyleFromSelected() {
        val id = _selectedLayerId.value ?: return
        val l = _activeDoc.value?.layers?.find { it.id == id } ?: return
        copiedStyle = CopiedLayerStyle(
            colorHex = l.colorHex,
            strokeHex = l.strokeHex,
            strokeWidth = l.strokeWidth,
            blendMode = l.blendMode,
            effects = l.effects.map { it.copy(id = newUid("fx")) },
            repeat = l.repeat,
            layerStyle = l.layerStyle
        )
        showToast(OmsStrings.tr(_language.value, "Gaya & Efek layer disalin", "Layer style & effects copied"))
    }

    fun pasteStyleToSelected() {
        val style = copiedStyle ?: return
        updateSelectedLayer { l ->
            l.copy(
                colorHex = style.colorHex,
                strokeHex = style.strokeHex,
                strokeWidth = style.strokeWidth,
                blendMode = style.blendMode,
                effects = style.effects.map { it.copy(id = newUid("fx")) },
                repeat = style.repeat,
                layerStyle = style.layerStyle
            )
        }
        showToast(OmsStrings.tr(_language.value, "Gaya diterapkan", "Style pasted"))
    }

    fun deleteSelectedLayer() {
        val id = _selectedLayerId.value ?: return
        updateDoc { doc ->
            doc.copy(
                layers = doc.layers
                    .filterNot { it.id == id }
                    .map { if (it.parentId == id) it.copy(parentId = null) else it }
            )
        }
        _selectedLayerId.value = _activeDoc.value?.layers?.lastOrNull()?.id
        _drawerMode.value = EditorDrawerMode.NONE
    }

    fun moveLayerOrder(id: String, delta: Int) {
        updateDoc { doc ->
            val list = doc.layers.toMutableList()
            val idx = list.indexOfFirst { it.id == id }
            if (idx < 0) return@updateDoc doc
            val target = (idx + delta).coerceIn(0, list.lastIndex)
            if (target == idx) return@updateDoc doc
            val item = list.removeAt(idx)
            list.add(target, item)
            doc.copy(layers = list)
        }
    }

    fun splitSelectedLayerAtPlayhead() {
        val id = _selectedLayerId.value ?: return
        val doc = _activeDoc.value ?: return
        val l = doc.layers.find { it.id == id } ?: return
        val t = _currentTime.value
        if (t <= l.start + 0.1f || t >= l.start + l.duration - 0.1f) return
        val firstDur = t - l.start
        val secondDur = l.duration - firstDur
        val first = l.copy(duration = firstDur)
        val second = l.copy(id = newUid("layer"), name = "${l.name} (2)", start = t, duration = secondDur)
        updateDoc { d ->
            val idx = d.layers.indexOfFirst { it.id == id }
            val next = d.layers.toMutableList()
            if (idx >= 0) {
                next[idx] = first
                next.add(idx + 1, second)
            }
            d.copy(layers = next)
        }
        _selectedLayerId.value = second.id
    }

    fun trimSelectedLayerEdge(trimLeft: Boolean) {
        val t = _currentTime.value
        updateSelectedLayer { l ->
            if (trimLeft) {
                val end = l.start + l.duration
                val newStart = t.coerceIn(0f, end - 0.2f)
                l.copy(start = newStart, duration = (end - newStart).coerceAtLeast(0.2f))
            } else {
                val newDur = (t - l.start).coerceAtLeast(0.2f)
                l.copy(duration = newDur)
            }
        }
    }

    fun dragTrimLayerEdge(id: String, trimLeft: Boolean, deltaSec: Float) {
        val maxDur = _activeDoc.value?.project?.duration ?: 10f
        updateLayerById(id, recordHistory = false) { l ->
            if (l.locked) return@updateLayerById l
            if (trimLeft) {
                val end = l.start + l.duration
                val nextStart = (l.start + deltaSec).coerceIn(0f, end - 0.2f)
                l.copy(start = nextStart, duration = (end - nextStart).coerceAtLeast(0.2f))
            } else {
                val nextDur = (l.duration + deltaSec).coerceIn(0.2f, maxDur - l.start)
                l.copy(duration = nextDur)
            }
        }
    }

    // Eyedropper Color Picker on Canvas
    fun startEyedropper(target: String) {
        _eyedropperTarget.value = target
        _drawerMode.value = EditorDrawerMode.NONE
        showToast(OmsStrings.tr(_language.value, "Ketuk warna pada kanvas", "Tap any color on the canvas"))
    }

    fun pickCanvasColorAt(canvasX: Float, canvasY: Float) {
        val target = _eyedropperTarget.value ?: return
        val doc = _activeDoc.value ?: return
        val pixel = CompositionCanvasRenderer.sampleColorAt(appContext, doc, _currentTime.value, canvasX, canvasY)
        val hex = String.format("#%06X", 0xFFFFFF and pixel)
        updateSelectedLayer { l ->
            if (target == "stroke") l.copy(strokeHex = hex) else l.copy(colorHex = hex)
        }
        _eyedropperTarget.value = null
        _drawerMode.value = EditorDrawerMode.COLOR
        showToast("Warna diambil: $hex")
    }

    fun cancelEyedropper() {
        _eyedropperTarget.value = null
    }

    // Motion & Camera Optical Flow Tracker
    fun startMotionTrackTargetSelection() {
        val doc = _activeDoc.value ?: return
        val layer = doc.layers.find { it.id == _selectedLayerId.value } ?: return
        if (layer.type != LayerType.VIDEO) {
            showToast(OmsStrings.tr(_language.value, "Pilih layer Video untuk Motion Tracker", "Select a Video layer for Motion Tracking"))
            return
        }
        _drawerMode.value = EditorDrawerMode.NONE
        _motionTrackUi.value = MotionTrackUiState(selectingTargetPoint = true)
        showToast(OmsStrings.tr(_language.value, "Ketuk titik objek pada video untuk dilacak", "Tap a feature point on the video to track"))
    }

    fun runMotionTrackAt(canvasX: Float, canvasY: Float) {
        val doc = _activeDoc.value ?: return
        val layer = doc.layers.find { it.id == _selectedLayerId.value } ?: return
        if (layer.type != LayerType.VIDEO) return

        val worldTr = AnimationEngine.resolveWorldTransform(layer, doc.layers, _currentTime.value)
        val normX = ((canvasX - (worldTr.x - layer.w * 0.5f)) / layer.w.coerceAtLeast(1f)).coerceIn(0.05f, 0.95f)
        val normY = ((canvasY - (worldTr.y - layer.h * 0.5f)) / layer.h.coerceAtLeast(1f)).coerceIn(0.05f, 0.95f)

        trackingJob?.cancel()
        trackingJob = viewModelScope.launch {
            _motionTrackUi.value = MotionTrackUiState(isTracking = true, progress = 0f, statusMessage = "Melacak fitur optik...")
            try {
                val res = MotionTracker.trackVideoFeature(appContext, layer, normX, normY) { p ->
                    _motionTrackUi.value = _motionTrackUi.value.copy(
                        progress = p,
                        statusMessage = "Melacak gerakan ${(p * 100).roundToInt()}%"
                    )
                }
                updateLayerById(layer.id) { l ->
                    l.copy(keyframes = l.keyframes + ("x" to res.xKeyframes) + ("y" to res.yKeyframes))
                }
                _motionTrackUi.value = MotionTrackUiState(
                    isTracking = false,
                    progress = 1f,
                    statusMessage = "Pelacakan selesai (${res.trackedPointsCount} keyframe, akurasi ${(res.confidenceAvg * 100).roundToInt()}%)"
                )
                showToast("Motion Track: ${res.trackedPointsCount} keyframe dibuat")
            } catch (e: Exception) {
                _motionTrackUi.value = MotionTrackUiState(
                    isTracking = false,
                    statusMessage = "Gagal melacak: ${e.message}"
                )
            }
        }
    }

    fun cancelMotionTrack() {
        trackingJob?.cancel()
        _motionTrackUi.value = MotionTrackUiState()
    }

    // Effects
    fun addEffectToSelected(spec: EffectSpec) {
        val inst = EffectsCatalog.createInstance(spec)
        updateSelectedLayer { it.copy(effects = it.effects + inst) }
        _drawerMode.value = EditorDrawerMode.EFFECTS
    }

    fun toggleEffectEnabled(effectId: String) {
        updateSelectedLayer { l ->
            l.copy(effects = l.effects.map { if (it.id == effectId) it.copy(enabled = !it.enabled) else it })
        }
    }

    fun removeEffect(effectId: String) {
        updateSelectedLayer { l ->
            l.copy(effects = l.effects.filterNot { it.id == effectId })
        }
    }

    fun updateEffectParam(effectId: String, paramId: String, value: Float) {
        val propKey = "effect.$effectId.$paramId"
        setLayerAnimatedProperty(propKey, value, recordHistory = false)
    }

    // Markers
    fun toggleMarkerAtPlayhead() {
        val t = (_currentTime.value * 100f).roundToInt() / 100f
        updateDoc { doc ->
            val existing = doc.markers.find { abs(it.time - t) < 0.08f }
            val nextMarkers = if (existing != null) {
                doc.markers.filterNot { it.id == existing.id }
            } else {
                (doc.markers + TimelineMarker(time = t, comment = "Marker ${doc.markers.size + 1}")).sortedBy { it.time }
            }
            doc.copy(markers = nextMarkers)
        }
    }

    // Interactive Vector Pen Editing (with anchor & Bezier handle dragging, smooth/corner, symmetric, subdivision)
    fun startVectorPen(existingLayer: LayerData? = null) {
        _drawerMode.value = EditorDrawerMode.NONE
        if (existingLayer != null && existingLayer.vectorPath != null) {
            _vectorPenSession.value = VectorPenSession(
                layerId = existingLayer.id,
                nodes = existingLayer.vectorPath.nodes,
                closed = existingLayer.vectorPath.closed,
                selectedNodeIndex = existingLayer.vectorPath.nodes.lastIndex
            )
        } else {
            _vectorPenSession.value = VectorPenSession(
                layerId = null,
                nodes = emptyList(),
                closed = true
            )
        }
    }

    fun handleVectorPenTap(canvasX: Float, canvasY: Float) {
        val doc = _activeDoc.value ?: return
        val session = _vectorPenSession.value ?: return
        val cx = doc.project.width * 0.5f
        val cy = doc.project.height * 0.5f
        val lx = canvasX - cx
        val ly = canvasY - cy

        // First check if user tapped near an existing node to select it
        val hitIdx = session.nodes.indexOfFirst { hypot(it.x - lx, it.y - ly) <= 36f }
        if (hitIdx >= 0) {
            _vectorPenSession.value = session.copy(selectedNodeIndex = hitIdx, activeDragKind = null)
            return
        }

        val newNode = VectorNode(x = lx, y = ly, inX = -42f, inY = 0f, outX = 42f, outY = 0f, smooth = true, symmetric = true)
        val nextNodes = session.nodes + newNode
        _vectorPenSession.value = session.copy(nodes = nextNodes, selectedNodeIndex = nextNodes.lastIndex, activeDragKind = null)
    }

    fun beginVectorPenDrag(canvasX: Float, canvasY: Float): Boolean {
        val doc = _activeDoc.value ?: return false
        val session = _vectorPenSession.value ?: return false
        val cx = doc.project.width * 0.5f
        val cy = doc.project.height * 0.5f
        val lx = canvasX - cx
        val ly = canvasY - cy

        // Priority 1: handles of currently selected node
        if (session.selectedNodeIndex in session.nodes.indices) {
            val n = session.nodes[session.selectedNodeIndex]
            if (n.smooth && hypot((n.x + n.outX) - lx, (n.y + n.outY) - ly) <= 36f) {
                _vectorPenSession.value = session.copy(activeDragKind = VectorHandleKind.OUT_HANDLE)
                return true
            }
            if (n.smooth && hypot((n.x + n.inX) - lx, (n.y + n.inY) - ly) <= 36f) {
                _vectorPenSession.value = session.copy(activeDragKind = VectorHandleKind.IN_HANDLE)
                return true
            }
        }
        // Priority 2: any anchor node
        val anchorIdx = session.nodes.indexOfFirst { hypot(it.x - lx, it.y - ly) <= 38f }
        if (anchorIdx >= 0) {
            _vectorPenSession.value = session.copy(selectedNodeIndex = anchorIdx, activeDragKind = VectorHandleKind.ANCHOR)
            return true
        }
        return false
    }

    fun dragVectorPenHandle(dx: Float, dy: Float) {
        val session = _vectorPenSession.value ?: return
        val idx = session.selectedNodeIndex
        val kind = session.activeDragKind ?: return
        if (idx !in session.nodes.indices) return
        val nodes = session.nodes.toMutableList()
        val n = nodes[idx]
        nodes[idx] = when (kind) {
            VectorHandleKind.ANCHOR -> n.copy(x = n.x + dx, y = n.y + dy)
            VectorHandleKind.OUT_HANDLE -> {
                val nx = n.outX + dx
                val ny = n.outY + dy
                if (n.symmetric) n.copy(outX = nx, outY = ny, inX = -nx, inY = -ny)
                else n.copy(outX = nx, outY = ny)
            }
            VectorHandleKind.IN_HANDLE -> {
                val nx = n.inX + dx
                val ny = n.inY + dy
                if (n.symmetric) n.copy(inX = nx, inY = ny, outX = -nx, outY = -ny)
                else n.copy(inX = nx, inY = ny)
            }
        }
        _vectorPenSession.value = session.copy(nodes = nodes)
    }

    fun endVectorPenDrag() {
        val session = _vectorPenSession.value ?: return
        _vectorPenSession.value = session.copy(activeDragKind = null)
    }

    fun toggleVectorNodeSmooth() {
        val session = _vectorPenSession.value ?: return
        val idx = session.selectedNodeIndex
        if (idx !in session.nodes.indices) return
        val next = session.nodes.toMutableList()
        val cur = next[idx]
        next[idx] = cur.copy(smooth = !cur.smooth)
        _vectorPenSession.value = session.copy(nodes = next)
    }

    fun toggleVectorNodeSymmetric() {
        val session = _vectorPenSession.value ?: return
        val idx = session.selectedNodeIndex
        if (idx !in session.nodes.indices) return
        val next = session.nodes.toMutableList()
        val cur = next[idx]
        next[idx] = cur.copy(symmetric = !cur.symmetric)
        _vectorPenSession.value = session.copy(nodes = next)
    }

    fun insertVectorMidpoint() {
        val session = _vectorPenSession.value ?: return
        if (session.nodes.size < 2) return
        val seg = session.selectedNodeIndex.coerceIn(0, session.nodes.lastIndex)
        val next = ShapePathBuilder.insertMidpointNode(session.nodes, seg, session.closed)
        _vectorPenSession.value = session.copy(nodes = next, selectedNodeIndex = (seg + 1).coerceAtMost(next.lastIndex))
    }

    fun deleteSelectedVectorNode() {
        val session = _vectorPenSession.value ?: return
        if (session.selectedNodeIndex !in session.nodes.indices) return
        val next = session.nodes.toMutableList().apply { removeAt(session.selectedNodeIndex) }
        _vectorPenSession.value = session.copy(nodes = next, selectedNodeIndex = next.lastIndex)
    }

    fun undoVectorPenNode() {
        val session = _vectorPenSession.value ?: return
        if (session.nodes.isEmpty()) return
        val next = session.nodes.dropLast(1)
        _vectorPenSession.value = session.copy(nodes = next, selectedNodeIndex = next.lastIndex)
    }

    fun toggleVectorPenClosed() {
        val session = _vectorPenSession.value ?: return
        _vectorPenSession.value = session.copy(closed = !session.closed)
    }

    fun finishVectorPen() {
        val doc = _activeDoc.value ?: return
        val session = _vectorPenSession.value ?: return
        if (session.nodes.size < 2) {
            _vectorPenSession.value = null
            return
        }
        val pathData = VectorPathData(closed = session.closed, nodes = session.nodes)
        if (session.layerId != null) {
            updateLayerById(session.layerId) { l ->
                l.copy(shapeKind = ShapeKind.VECTOR_PATH, vectorPath = pathData)
            }
            _selectedLayerId.value = session.layerId
        } else {
            val layer = LayerData(
                type = LayerType.SHAPE,
                name = "Path Vektor",
                start = _currentTime.value,
                duration = min(4f, (doc.project.duration - _currentTime.value).coerceAtLeast(0.5f)),
                x = doc.project.width * 0.5f,
                y = doc.project.height * 0.5f,
                w = doc.project.width * 0.6f,
                h = doc.project.height * 0.4f,
                shapeKind = ShapeKind.VECTOR_PATH,
                vectorPath = pathData,
                colorHex = "#00D7A7",
                strokeHex = "#FFFFFF",
                strokeWidth = 4f
            )
            updateDoc { it.copy(layers = it.layers + layer) }
            _selectedLayerId.value = layer.id
        }
        _vectorPenSession.value = null
        _drawerMode.value = EditorDrawerMode.SHAPE_STUDIO
    }

    fun cancelVectorPen() {
        _vectorPenSession.value = null
    }

    // Exporting & Save-to-Device
    fun exportPng() {
        val doc = _activeDoc.value ?: return
        exportJob?.cancel()
        exportJob = viewModelScope.launch {
            _exportUi.value = ExportUiState(isExporting = true, progress = 0.3f, statusMessage = "Merender PNG...")
            try {
                val file = repository.exportPngFile(doc, _currentTime.value)
                _exportUi.value = ExportUiState(
                    isExporting = false,
                    progress = 1f,
                    statusMessage = "PNG siap: ${file.name}",
                    lastFile = file,
                    lastMimeType = "image/png"
                )
            } catch (e: Exception) {
                _exportUi.value = ExportUiState(statusMessage = "Gagal ekspor PNG: ${e.message}")
            }
        }
    }

    fun exportJson() {
        val doc = _activeDoc.value ?: return
        viewModelScope.launch {
            val file = repository.exportJsonFile(doc)
            _exportUi.value = ExportUiState(
                statusMessage = "Paket JSON siap: ${file.name}",
                lastFile = file,
                lastMimeType = "application/json"
            )
        }
    }

    fun exportXml() {
        val doc = _activeDoc.value ?: return
        viewModelScope.launch {
            val file = repository.exportXmlFile(doc)
            _exportUi.value = ExportUiState(
                statusMessage = "XML Interchange siap: ${file.name}",
                lastFile = file,
                lastMimeType = "text/xml"
            )
        }
    }

    fun exportAmproj() {
        val doc = _activeDoc.value ?: return
        viewModelScope.launch {
            _exportUi.value = ExportUiState(isExporting = true, progress = 0.5f, statusMessage = "Mengemas proyek .amproj ZIP...")
            try {
                val file = repository.exportAmprojZip(doc)
                _exportUi.value = ExportUiState(
                    isExporting = false,
                    progress = 1f,
                    statusMessage = "Paket .amproj siap: ${file.name}",
                    lastFile = file,
                    lastMimeType = "application/zip"
                )
            } catch (e: Exception) {
                _exportUi.value = ExportUiState(statusMessage = "Gagal mengemas .amproj: ${e.message}")
            }
        }
    }

    fun exportMp4(resolutionShort: Int, fps: Int, bitrateMbps: Int) {
        val doc = _activeDoc.value ?: return
        val (outW, outH) = AspectRatioPresets.computeDimensions(
            doc.project.ratio,
            resolutionShort,
            doc.project.width,
            doc.project.height
        )
        exportJob?.cancel()
        exportJob = viewModelScope.launch {
            _exportUi.value = ExportUiState(isExporting = true, progress = 0f, statusMessage = "Merender video MP4 + Audio AAC...")
            try {
                val file = repository.exportMp4File(doc, outW, outH, fps, bitrateMbps) { prog ->
                    _exportUi.value = _exportUi.value.copy(
                        progress = prog,
                        statusMessage = "Menyandikan MP4 ${(prog * 100).roundToInt()}%"
                    )
                }
                _exportUi.value = ExportUiState(
                    isExporting = false,
                    progress = 1f,
                    statusMessage = "Video MP4 selesai (${outW}×${outH} @ ${fps}fps)",
                    lastFile = file,
                    lastMimeType = "video/mp4"
                )
            } catch (e: CancellationException) {
                _exportUi.value = ExportUiState(statusMessage = "Ekspor dibatalkan")
            } catch (e: Exception) {
                _exportUi.value = ExportUiState(statusMessage = "Gagal ekspor MP4: ${e.message}")
            }
        }
    }

    fun shareLastExport() {
        val ui = _exportUi.value
        val file = ui.lastFile ?: return
        val mime = ui.lastMimeType ?: "*/*"
        repository.shareFile(file, mime)
    }

    fun saveLastExportToUri(targetUri: Uri) {
        val file = _exportUi.value.lastFile ?: return
        viewModelScope.launch {
            val ok = repository.saveFileToUserUri(file, targetUri)
            showToast(
                if (ok) OmsStrings.tr(_language.value, "Berhasil disimpan ke perangkat!", "Saved to device!")
                else OmsStrings.tr(_language.value, "Gagal menyimpan file", "Failed to save file")
            )
        }
    }

    fun cancelExport() {
        exportJob?.cancel()
        _exportUi.value = ExportUiState(statusMessage = "Ekspor dibatalkan")
    }

    override fun onCleared() {
        super.onCleared()
        AudioTrackEngine.releaseAllPlayback()
    }
}
