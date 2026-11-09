package com.editorshub.ui.feature.editor

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.editorshub.core.data.ProjectRepository
import com.editorshub.core.data.ProjectSerializer
import com.editorshub.core.domain.effects.EffectCatalog
import com.editorshub.core.domain.keyframe.KeyframeEngine
import com.editorshub.core.domain.model.*
import com.editorshub.core.domain.timeline.TimelineMath
import com.editorshub.core.media.MediaKind
import com.editorshub.core.media.MediaRepository
import com.editorshub.core.rendering.ThumbnailGenerator
import com.editorshub.core.rendering.export.ExportResult
import com.editorshub.core.rendering.export.ExportWorker
import com.editorshub.core.rendering.export.VideoExporter
import com.editorshub.core.settings.AppSettings
import com.editorshub.core.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import kotlin.math.abs

enum class EditorTab {
    ADD, PROPERTIES, TRANSFORM, KEYFRAMES, EFFECTS, TEXT, SHAPE, VECTOR, MASKS,
    AUDIO, TRANSITIONS, PROJECT, BORDER, BLEND, COLOR
}

sealed class ExportUiState {
    data object Idle : ExportUiState()
    data class Running(val percent: Int, val label: String) : ExportUiState()
    data class Success(val file: String, val uri: String) : ExportUiState()
    data class Failure(val message: String) : ExportUiState()
}

data class EditorUiState(
    val project: Project? = null,
    val selectedLayerId: String? = null,
    val selectedLayerIds: Set<String> = emptySet(),
    val multiSelect: Boolean = false,
    val playheadMs: Long = 0L,
    val playing: Boolean = false,
    val zoomPxPerSecond: Float = 70f,
    val tab: EditorTab = EditorTab.ADD,
    val selectedProperty: String = "x",
    val autoKey: Boolean = false,
    val snapping: Boolean = true,
    val magneticSnapping: Boolean = true,
    val gpuPreview: Boolean = true,
    val previewQuality: Float = 0.6f,
    val previewZoom: Float = 1f,
    val proxyEnabled: Boolean = false,
    val proxyScale: Float = 0.5f,
    val checkerboard: Boolean = true,
    val showSafeArea: Boolean = false,
    val showGrid: Boolean = false,
    val showCenterGuides: Boolean = false,
    val showAspectGuides: Boolean = false,
    val backgroundRendering: Boolean = true,
    val autosaveIntervalMs: Long = 700L,
    val defaultResolution: ExportResolution = ExportResolution.P1080,
    val defaultFps: Int = 30,
    val defaultCodec: VideoCodec = VideoCodec.H264,
    val defaultBitrate: Int = 8_000_000,
    val defaultQuality: Int = 80,
    val defaultSaveLocation: String = "gallery",
    val defaultIncludeAudio: Boolean = true,
    val undoAvailable: Boolean = false,
    val redoAvailable: Boolean = false,
    val export: ExportUiState = ExportUiState.Idle,
    val message: String? = null
)

@HiltViewModel
class EditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ProjectRepository,
    private val media: MediaRepository,
    private val settings: SettingsRepository,
    private val thumbnails: ThumbnailGenerator,
    private val workManager: WorkManager,
    private val exporter: VideoExporter
) : ViewModel() {

    private val projectId: String = savedStateHandle["projectId"] ?: ""
    private val _ui = MutableStateFlow(EditorUiState())
    val ui: StateFlow<EditorUiState> = _ui.asStateFlow()

    private var autosaveJob: Job? = null
    private var playbackJob: Job? = null
    private var exportUuid: UUID? = null
    private var keyframeClipboard: List<Keyframe> = emptyList()
    private val undoStack = ArrayDeque<Project>()
    private val redoStack = ArrayDeque<Project>()
    private var layerClipboard: List<Layer> = emptyList()
    private var currentSettings = AppSettings()

    init {
        viewModelScope.launch {
            val p = runCatching { repository.load(projectId) }.getOrNull()
            if (p != null) {
                _ui.value = _ui.value.copy(
                    project = p,
                    selectedLayerId = p.layers.firstOrNull()?.id,
                    tab = if (p.layers.isNotEmpty()) EditorTab.PROPERTIES else EditorTab.ADD,
                    playheadMs = 0L
                )
                settings.settings
                    .catch { emit(AppSettings()) }
                    .collectLatest { s ->
                    currentSettings = s
                    _ui.value = _ui.value.copy(
                        snapping = s.snapping,
                        magneticSnapping = s.magneticSnapping,
                        previewQuality = s.previewQuality,
                        gpuPreview = s.hardwareAcceleration,
                        proxyEnabled = s.proxyEnabled,
                        proxyScale = s.proxyScale,
                        checkerboard = s.checkerboard,
                        showSafeArea = s.showSafeArea,
                        showGrid = s.showGrid,
                        showCenterGuides = s.showCenterGuides,
                        showAspectGuides = s.showAspectGuides,
                        backgroundRendering = s.backgroundRendering,
                        autosaveIntervalMs = s.autosaveIntervalMs,
                        defaultResolution = s.defaultResolution,
                        defaultFps = s.defaultFps,
                        defaultCodec = s.defaultCodec,
                        defaultBitrate = s.defaultBitrate,
                        defaultQuality = s.defaultQuality,
                        defaultSaveLocation = s.defaultSaveLocation,
                        defaultIncludeAudio = s.defaultIncludeAudio
                    )
                }
            } else {
                _ui.value = _ui.value.copy(message = "Project not found")
            }
        }
    }

    private fun project(): Project = _ui.value.project ?: Project()
    private fun selected(): Layer? {
        val s = _ui.value
        return s.project?.layers?.firstOrNull { it.id == s.selectedLayerId }
    }

    private fun commit(transform: (Project) -> Project) {
        val previous = project()
        val next = transform(previous)
        if (next != previous) {
            undoStack.addLast(previous)
            if (undoStack.size > 80) undoStack.removeFirst()
            redoStack.clear()
        }
        _ui.value = _ui.value.copy(project = next,
            undoAvailable = undoStack.isNotEmpty(), redoAvailable = redoStack.isNotEmpty())
        scheduleAutosave(next)
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        redoStack.addLast(project())
        val restored = undoStack.removeLast()
        _ui.value = _ui.value.copy(project = restored,
            undoAvailable = undoStack.isNotEmpty(), redoAvailable = true)
        scheduleAutosave(restored)
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        undoStack.addLast(project())
        val restored = redoStack.removeLast()
        _ui.value = _ui.value.copy(project = restored,
            undoAvailable = true, redoAvailable = redoStack.isNotEmpty())
        scheduleAutosave(restored)
    }

    private fun scheduleAutosave(p: Project) {
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            delay(_ui.value.autosaveIntervalMs)
            runCatching {
                val thumb = withContext(Dispatchers.Default) { thumbnails.generate(p) }
                repository.save(p, thumbnailPath = thumb, autosaveOnly = true)
            }
        }
    }

    fun saveNow() = viewModelScope.launch {
        val p = project()
        runCatching {
            val thumb = withContext(Dispatchers.Default) { thumbnails.generate(p) }
            repository.save(p, thumbnailPath = thumb, autosaveOnly = false)
            message("Project saved (version recorded)")
        }.onFailure { message("Save failed: ${it.message}") }
    }

    fun recover() = viewModelScope.launch {
        val interrupted = runCatching { repository.recoverInterrupted() }.getOrDefault(emptyList())
        message(if (interrupted.isEmpty()) "No interrupted sessions" else "${interrupted.size} recoverable session(s)")
    }

    private fun message(text: String) {
        _ui.value = _ui.value.copy(message = text)
        viewModelScope.launch { delay(2600); if (_ui.value.message == text) _ui.value = _ui.value.copy(message = null) }
    }

    // ------------------------------------------------------------------ playback
    fun togglePlay() { if (_ui.value.playing) pause() else play() }

    fun play() {
        if (playbackJob?.isActive == true) return
        val p = project()
        _ui.value = _ui.value.copy(playing = true)
        playbackJob = viewModelScope.launch {
            val exact = p.fpsExact.coerceIn(1.0, 240.0)
            val frameMs = (1000.0 / exact).toLong().coerceAtLeast(1L)
            val origin = System.nanoTime()
            val start = _ui.value.playheadMs
            val duration = p.durationMs.coerceAtLeast(1L)
            while (isActive && _ui.value.playing) {
                var t = start + (System.nanoTime() - origin) / 1_000_000L
                if (t >= duration) t %= duration
                _ui.value = _ui.value.copy(playheadMs = t)
                delay(frameMs)
            }
        }
    }

    fun pause() {
        playbackJob?.cancel()
        _ui.value = _ui.value.copy(playing = false)
    }

    fun seek(ms: Long) {
        val end = project().durationMs.coerceAtLeast(0L)
        val wasPlaying = _ui.value.playing
        if (wasPlaying) pause()
        _ui.value = _ui.value.copy(playheadMs = ms.coerceIn(0L, end))
        // Rebase the playback clock after a seek instead of snapping back to the old origin.
        if (wasPlaying) play()
    }

    fun stepFrames(count: Int) {
        pause()
        val frame = (1000.0 / project().fpsExact.coerceIn(1.0, 240.0)).toLong().coerceAtLeast(1L)
        seek(_ui.value.playheadMs + count * frame)
    }

    // ------------------------------------------------------------------- ui state
    fun selectLayer(id: String?) = _ui.value.let { _ui.value = it.copy(selectedLayerId = id) }
    fun setTab(tab: EditorTab) { _ui.value = _ui.value.copy(tab = tab) }
    fun setZoom(v: Float) { _ui.value = _ui.value.copy(zoomPxPerSecond = v.coerceIn(12f, 320f)) }
    fun setAutoKey(v: Boolean) { _ui.value = _ui.value.copy(autoKey = v) }
    fun setSnapping(v: Boolean) { _ui.value = _ui.value.copy(snapping = v); viewModelScope.launch { settings.update { it.copy(snapping = v) } } }
    fun setGpuPreview(v: Boolean) { _ui.value = _ui.value.copy(gpuPreview = v) }
    fun setPreviewZoom(v: Float) { _ui.value = _ui.value.copy(previewZoom = v.coerceIn(0.4f, 2.5f)) }
    fun setSelectedProperty(v: String) { _ui.value = _ui.value.copy(selectedProperty = v) }

    fun setProjectSettings(width: Int, height: Int, fps: Int, durationMs: Long, background: String) {
        commit { p -> p.copy(width = width.coerceIn(16, 4096), height = height.coerceIn(16, 4096),
            fps = fps.coerceIn(1, 120), durationMs = durationMs.coerceAtLeast(500L), background = background) }
    }

    fun renameProject(name: String) { commit { it.copy(name = name) } }

    // --------------------------------------------------------------------- layers
    fun addLayer(type: LayerType, requestedShape: ShapeKind? = null) {
        val p = project()
        val id = UUID.randomUUID().toString()
        val shapeKind = requestedShape ?: if (type == LayerType.SHAPE) ShapeKind.ROUNDED_RECTANGLE else ShapeKind.RECTANGLE
        val shapeName = shapeLabel(shapeKind)
        val name = if (type == LayerType.SHAPE) {
            val count = p.layers.count { it.name.startsWith(shapeName, ignoreCase = true) } + 1
            "$shapeName $count"
        } else defaultName(type)
        val isOpenStroke = shapeKind in setOf(ShapeKind.LINE, ShapeKind.ARC)
        val shapeSpec = if (type == LayerType.SHAPE || type == LayerType.SOLID) ShapeSpec(
            kind = shapeKind,
            width = 200f,
            height = 200f,
            cornerRadius = if (shapeKind == ShapeKind.ROUNDED_RECTANGLE) 25f else 0f,
            fillColor = "#FFBA7E38",
            strokeColor = "#FF000000",
            strokeWidth = if (isOpenStroke) 8f else 4f,
            strokeEnabled = isOpenStroke
        ) else null
        val layerStartMs = _ui.value.playheadMs.coerceAtLeast(0L)
        val layerDurationMs = minOf(5000L, (p.durationMs - layerStartMs).coerceAtLeast(500L))
        val layer = Layer(
            id = id, name = name, type = type,
            startMs = layerStartMs,
            durationMs = layerDurationMs,
            text = if (type == LayerType.TEXT) TextSpec() else null,
            shape = shapeSpec,
            vector = if (type == LayerType.VECTOR) VectorSpec(
                commands = listOf(
                    VectorCommand("M", listOf(-200f, -200f)),
                    VectorCommand("L", listOf(200f, -200f)),
                    VectorCommand("L", listOf(0f, 220f)),
                    VectorCommand("Z")
                )
            ) else null
        )
        commit { it.copy(durationMs = maxOf(it.durationMs, layer.startMs + layer.durationMs), layers = listOf(layer) + it.layers) }
        _ui.value = _ui.value.copy(selectedLayerId = id, tab = when (type) {
            LayerType.TEXT -> EditorTab.TEXT
            LayerType.SHAPE, LayerType.SOLID -> EditorTab.PROPERTIES
            LayerType.VECTOR -> EditorTab.VECTOR
            else -> EditorTab.TRANSFORM
        })
        message("$name added")
    }

    private fun shapeLabel(kind: ShapeKind): String = when (kind) {
        ShapeKind.CIRCLE -> "Circle"
        ShapeKind.ROUNDED_RECTANGLE -> "Rounded Rectangle"
        ShapeKind.CROSS -> "Plus"
        ShapeKind.ARC -> "Arc"
        ShapeKind.TRIANGLE -> "Triangle"
        ShapeKind.CALLOUT -> "Callout"
        ShapeKind.PIE -> "Pie"
        ShapeKind.HEXAGON -> "Hexagon"
        ShapeKind.FLOWER -> "Flower"
        ShapeKind.ARROW -> "Arrow"
        ShapeKind.TRAPEZOID -> "Trapezoid"
        ShapeKind.CRESCENT -> "Crescent"
        ShapeKind.RECTANGLE -> "Rectangle"
        ShapeKind.STAR -> "Star"
        ShapeKind.POLYGON -> "Polygon"
        ShapeKind.LINE -> "Line"
        ShapeKind.HEART -> "Heart"
        ShapeKind.BEAM -> "Beam"
        ShapeKind.FLAG -> "Curve"
        ShapeKind.SEAL -> "Burst Seal"
        ShapeKind.DIAMOND -> "Diamond"
        ShapeKind.PENTAGON -> "Pentagon"
        ShapeKind.SPARKLE -> "Sparkle"
        ShapeKind.BURST -> "Burst"
        ShapeKind.RING -> "Ring"
        ShapeKind.HALF_CIRCLE -> "Half Circle"
        ShapeKind.OCTAGON -> "Octagon"
        ShapeKind.LEAF -> "Leaf"
        ShapeKind.ARROWHEAD -> "Arrowhead"
        ShapeKind.CROWN -> "Crown"
        ShapeKind.HOURGLASS -> "Hourglass"
        ShapeKind.SECTOR -> "Sector"
        ShapeKind.NOTCHED_SQUARE -> "Notched Square"
        ShapeKind.WAVE -> "Wave"
    }

    private fun defaultName(type: LayerType) = when (type) {
        LayerType.VIDEO -> "Video"; LayerType.IMAGE -> "Image"; LayerType.AUDIO -> "Audio"
        LayerType.TEXT -> "Text"; LayerType.SHAPE -> "Shape"; LayerType.VECTOR -> "Vector"
        LayerType.SOLID -> "Solid"; LayerType.EFFECT -> "Effects"; LayerType.GROUP -> "Group"
    }

    fun importMedia(uri: Uri, name: String?) = viewModelScope.launch {
        val imported = runCatching { media.import(uri, name) }.getOrNull()
        if (imported == null) { message("Could not import media"); return@launch }
        val info = imported.info
        val path = "file://${info.path}"
        val insertAtMs = _ui.value.playheadMs.coerceAtLeast(0L)
        if (info.kind == MediaKind.AUDIO) {
            val track = AudioTrack(
                id = UUID.randomUUID().toString(), uri = path, name = info.name,
                startMs = insertAtMs,
                durationMs = info.durationMs.coerceAtLeast(1000L),
                peaks = media.waveform(info.path)
            )
            commit { it.copy(
                durationMs = maxOf(it.durationMs, track.startMs + track.durationMs),
                audioTracks = it.audioTracks + track
            ) }
            _ui.value = _ui.value.copy(tab = EditorTab.AUDIO)
        } else {
            val type = if (info.kind == MediaKind.VIDEO) LayerType.VIDEO else LayerType.IMAGE
            val layer = Layer(
                id = UUID.randomUUID().toString(), name = info.name, type = type,
                startMs = insertAtMs, sourceUri = path,
                durationMs = if (info.durationMs > 0) info.durationMs.coerceAtMost(30_000L) else 5000L
            )
            commit { it.copy(
                durationMs = maxOf(it.durationMs, layer.startMs + layer.durationMs),
                layers = listOf(layer) + it.layers
            ) }
            _ui.value = _ui.value.copy(selectedLayerId = layer.id, tab = EditorTab.TRANSFORM)
        }
        message("${info.name} imported")
    }

    fun deleteLayer(id: String) = commit { p ->
        p.copy(layers = p.layers.filterNot { it.id == id }
            .map { if (it.parentId == id) it.copy(parentId = null) else it },
            transitions = p.transitions.filterNot { it.fromLayerId == id || it.toLayerId == id })
    }.also {
        if (_ui.value.selectedLayerId == id) _ui.value = _ui.value.copy(selectedLayerId = project().layers.firstOrNull()?.id)
    }

    fun duplicateLayer(id: String) {
        val layer = project().layers.firstOrNull { it.id == id } ?: return
        val copy = layer.copy(id = UUID.randomUUID().toString(), name = "${layer.name} copy")
        commit { p ->
            val index = p.layers.indexOfFirst { it.id == id }
            p.copy(layers = p.layers.toMutableList().apply { add(index + 1, copy) })
        }
        _ui.value = _ui.value.copy(selectedLayerId = copy.id)
    }

    fun reorderLayer(from: Int, to: Int) = commit { it.copy(layers = TimelineMath.reordered(it.layers, from, to)) }

    fun renameLayer(id: String, name: String) = commit { p ->
        p.copy(layers = p.layers.map { if (it.id == id) it.copy(name = name) else it })
    }

    fun toggleVisible(id: String) = commit { p ->
        p.copy(layers = p.layers.map { if (it.id == id) it.copy(visible = !it.visible) else it })
    }

    fun toggleLock(id: String) = commit { p ->
        p.copy(layers = p.layers.map { if (it.id == id) it.copy(locked = !it.locked) else it })
    }

    fun setBlend(id: String, blend: BlendMode) = commit { p ->
        p.copy(layers = p.layers.map { if (it.id == id) it.copy(blendMode = blend) else it })
    }

    fun setFit(id: String, fit: FitMode) = commit { p ->
        p.copy(layers = p.layers.map { if (it.id == id) it.copy(fit = fit) else it })
    }

    fun setParent(id: String, parentId: String?) = commit { p ->
        p.copy(layers = p.layers.map { if (it.id == id) it.copy(parentId = parentId) else it })
    }

    fun splitLayer(id: String) {
        val p = project()
        val layer = p.layers.firstOrNull { it.id == id } ?: return
        if (layer.locked) { message("Unlock this layer before splitting it"); return }
        val result = TimelineMath.split(layer, _ui.value.playheadMs, p.fps, UUID.randomUUID().toString())
        if (result == null) { message("Playhead is outside the layer"); return }
        commit { proj ->
            val index = proj.layers.indexOfFirst { it.id == id }
            proj.copy(layers = proj.layers.toMutableList().apply {
                removeAt(index); add(index, result.first); add(index + 1, result.second)
            })
        }
        message("Layer split")
    }

    fun moveClip(id: String, deltaMs: Long) = commit { p ->
        val layers = p.layers.map { l ->
            if (l.id != id || l.locked) return@map l
            var moved = TimelineMath.move(l, deltaMs, p.fps, _ui.value.snapping)
            if (_ui.value.magneticSnapping) {
                val edges = p.layers.filter { it.id != id }
                    .flatMap { listOf(it.startMs, it.startMs + it.durationMs) }
                val target = listOf(moved.startMs, moved.startMs + moved.durationMs)
                for (t in target) {
                    val near = edges.minByOrNull { kotlin.math.abs(it - t) }
                    if (near != null && kotlin.math.abs(near - t) < 120L) {
                        moved = moved.copy(startMs = (moved.startMs + (near - t)).coerceAtLeast(0L))
                        break
                    }
                }
            }
            moved
        }
        val contentEnd = layers.maxOfOrNull { it.startMs + it.durationMs } ?: 0L
        p.copy(durationMs = maxOf(p.durationMs, contentEnd), layers = layers)
    }

    fun trimStart(id: String, deltaMs: Long) = commit { p ->
        p.copy(layers = p.layers.map { if (it.id == id && !it.locked) TimelineMath.trimStart(it, deltaMs, p.fps, _ui.value.snapping) else it })
    }

    fun trimEnd(id: String, deltaMs: Long) = commit { p ->
        val layers = p.layers.map {
            if (it.id == id && !it.locked) TimelineMath.resizeEnd(it, deltaMs, p.fps, _ui.value.snapping) else it
        }
        val contentEnd = layers.maxOfOrNull { it.startMs + it.durationMs } ?: 0L
        p.copy(durationMs = maxOf(p.durationMs, contentEnd), layers = layers)
    }

    // ------------------------------------------------------------------ transform
    fun setTransform(property: String, value: Float) {
        val id = _ui.value.selectedLayerId ?: return
        val layer = selected() ?: return
        if (layer.locked) { message("Unlock this layer before editing it"); return }
        val local = _ui.value.playheadMs - layer.startMs
        val animated = _ui.value.autoKey || layer.keyframes.any { it.property == property }
        commit { p ->
            p.copy(layers = p.layers.map { l ->
                if (l.id != id) return@map l
                if (animated) {
                    val tracks = l.keyframes.toMutableList()
                    val idx = tracks.indexOfFirst { it.property == property }
                    val track = if (idx >= 0) tracks[idx] else KeyframeTrack(property)
                    val updated = KeyframeEngine.upsert(track, local, value, Easing.EASE_IN_OUT)
                    if (idx >= 0) tracks[idx] = updated else tracks.add(updated)
                    l.copy(keyframes = tracks)
                } else {
                    l.copy(transform = applyTransform(l.transform, property, value))
                }
            })
        }
    }

    private fun applyTransform(t: Transform, property: String, v: Float): Transform = when (property) {
        "x" -> t.copy(x = v); "y" -> t.copy(y = v)
        "scaleX" -> t.copy(scaleX = v); "scaleY" -> t.copy(scaleY = v)
        "rotation" -> t.copy(rotation = v); "opacity" -> t.copy(opacity = v)
        "anchorX" -> t.copy(anchorX = v); "anchorY" -> t.copy(anchorY = v)
        "skewX" -> t.copy(skewX = v)
        else -> t
    }

    fun addKeyframe(property: String) {
        val id = _ui.value.selectedLayerId ?: return
        val layer = selected() ?: return
        if (layer.locked) { message("Unlock this layer before editing it"); return }
        val local = _ui.value.playheadMs - layer.startMs
        val current = KeyframeEngine.trackValue(layer.keyframes, property, local,
            when (property) {
                "x" -> layer.transform.x; "y" -> layer.transform.y
                "scaleX" -> layer.transform.scaleX; "scaleY" -> layer.transform.scaleY
                "rotation" -> layer.transform.rotation; "opacity" -> layer.transform.opacity
                else -> 0f
            })
        commit { p -> p.copy(layers = p.layers.map { l ->
            if (l.id != id) return@map l
            val tracks = l.keyframes.toMutableList()
            val idx = tracks.indexOfFirst { it.property == property }
            val track = if (idx >= 0) tracks[idx] else KeyframeTrack(property)
            val updated = KeyframeEngine.upsert(track, local, current, Easing.LINEAR)
            if (idx >= 0) tracks[idx] = updated else tracks.add(updated)
            l.copy(keyframes = tracks)
        }) }
    }

    fun removeKeyframe(property: String, keyframeId: String) = commit { p ->
        p.copy(layers = p.layers.map { l -> l.copy(
            keyframes = l.keyframes.map { if (it.property == property) KeyframeEngine.remove(it, keyframeId) else it }
        ) })
    }

    fun moveKeyframe(property: String, keyframeId: String, timeMs: Long) = commit { p ->
        p.copy(layers = p.layers.map { l -> l.copy(
            keyframes = l.keyframes.map { track ->
                if (track.property != property) track
                else track.copy(keyframes = track.keyframes.map {
                    if (it.id == keyframeId) it.copy(timeMs = timeMs.coerceAtLeast(0)) else it
                }.sortedBy { it.timeMs })
            }
        ) })
    }

    fun setKeyframeValue(property: String, keyframeId: String, value: Float) = commit { p ->
        p.copy(layers = p.layers.map { l -> l.copy(
            keyframes = l.keyframes.map { track ->
                if (track.property != property) track
                else track.copy(keyframes = track.keyframes.map {
                    if (it.id == keyframeId) it.copy(value = value) else it
                }.sortedBy { it.timeMs })
            }
        ) })
    }

    fun setKeyframeBezier(
        property: String, keyframeId: String,
        cp1x: Float, cp1y: Float, cp2x: Float, cp2y: Float
    ) = commit { p ->
        p.copy(layers = p.layers.map { l -> l.copy(
            keyframes = l.keyframes.map { track ->
                if (track.property != property) track
                else track.copy(keyframes = track.keyframes.map {
                    if (it.id == keyframeId) it.copy(
                        easing = Easing.BEZIER,
                        cp1x = cp1x.coerceIn(0f, 1f), cp1y = cp1y,
                        cp2x = cp2x.coerceIn(0f, 1f), cp2y = cp2y
                    ) else it
                })
            }
        ) })
    }

    fun setKeyframeEasing(property: String, keyframeId: String, easing: Easing) = commit { p ->
        p.copy(layers = p.layers.map { l -> l.copy(
            keyframes = l.keyframes.map { track ->
                if (track.property != property) track
                else track.copy(keyframes = track.keyframes.map {
                    if (it.id == keyframeId) it.copy(easing = easing) else it
                })
            }
        ) })
    }

    fun copyKeyframes(property: String) {
        val track = selected()?.keyframes?.firstOrNull { it.property == property } ?: return
        keyframeClipboard = track.keyframes
        message("${track.keyframes.size} keyframes copied")
    }

    fun pasteKeyframes(property: String) {
        if (keyframeClipboard.isEmpty()) { message("Nothing copied"); return }
        val id = _ui.value.selectedLayerId ?: return
        commit { p -> p.copy(layers = p.layers.map { l ->
            if (l.id != id) return@map l
            val tracks = l.keyframes.toMutableList()
            val idx = tracks.indexOfFirst { it.property == property }
            val pasted = KeyframeTrack(property, keyframeClipboard.map {
                it.copy(id = UUID.randomUUID().toString())
            })
            if (idx >= 0) tracks[idx] = pasted else tracks.add(pasted)
            l.copy(keyframes = tracks)
        }) }
        message("Keyframes pasted")
    }

    /** Real motion presets: they write keyframes, they are not decorative. */
    fun applyPreset(name: String) {
        val id = _ui.value.selectedLayerId ?: return
        val layer = selected() ?: return
        val d = layer.durationMs
        val tracks = mutableListOf<KeyframeTrack>()
        fun track(property: String, vararg points: Pair<Long, Float>) =
            tracks.add(KeyframeTrack(property, points.map { (t, v) ->
                Keyframe(id = UUID.randomUUID().toString(), timeMs = t, value = v, easing = Easing.EASE_IN_OUT)
            }))
        when (name) {
            "Fade in" -> track("opacity", 0L to 0f, minOf(800L, d) to 1f)
            "Fade out" -> track("opacity", maxOf(0L, d - 800L) to 1f, d to 0f)
            "Slide up" -> track("y", 0L to 400f, minOf(900L, d) to 0f)
            "Slide in" -> track("x", 0L to 700f, minOf(900L, d) to 0f)
            "Pop" -> { track("scaleX", 0L to 0.4f, minOf(250L, d) to 1.15f, minOf(500L, d) to 1f)
                track("scaleY", 0L to 0.4f, minOf(250L, d) to 1.15f, minOf(500L, d) to 1f) }
            "Spin" -> track("rotation", 0L to 0f, d to 360f)
            "Pulse" -> {
                val pts = mutableListOf<Pair<Long, Float>>()
                var t = 0L
                while (t <= d) { pts.add(t to if ((t / 500L) % 2L == 0L) 1f else 1.12f); t += 500L }
                track("scaleX", *pts.toTypedArray()); track("scaleY", *pts.toTypedArray())
            }
            "Float" -> track("y", 0L to 0f, d / 2 to -40f, d to 0f)
            else -> return
        }
        commit { p -> p.copy(layers = p.layers.map { l ->
            if (l.id != id) return@map l
            val merged = l.keyframes.toMutableList()
            for (t in tracks) {
                val idx = merged.indexOfFirst { it.property == t.property }
                if (idx >= 0) merged[idx] = t else merged.add(t)
            }
            l.copy(keyframes = merged)
        }) }
        message("$name applied")
    }

    fun clearKeyframes(property: String) = commit { p ->
        p.copy(layers = p.layers.map { l -> l.copy(
            keyframes = l.keyframes.filterNot { it.property == property }
        ) })
    }

    // -------------------------------------------------------------------- effects
    fun addEffect(type: EffectType) {
        val id = _ui.value.selectedLayerId ?: return
        if (selected()?.locked == true) { message("Unlock this layer before editing it"); return }
        val effect = Effect(id = UUID.randomUUID().toString(), type = type,
            params = EffectCatalog.defaults(type))
        commit { p -> p.copy(layers = p.layers.map { if (it.id == id) it.copy(effects = it.effects + effect) else it }) }
        message("${EffectCatalog.spec(type).label} added")
    }

    fun removeEffect(effectId: String) = commit { p ->
        p.copy(layers = p.layers.map { it.copy(effects = it.effects.filterNot { e -> e.id == effectId }) })
    }

    fun toggleEffect(effectId: String) = commit { p ->
        p.copy(layers = p.layers.map { l -> l.copy(
            effects = l.effects.map { if (it.id == effectId) it.copy(enabled = !it.enabled) else it }
        ) })
    }

    fun reorderEffect(effectId: String, delta: Int) = commit { p ->
        p.copy(layers = p.layers.map { l ->
            val list = l.effects.toMutableList()
            val i = list.indexOfFirst { it.id == effectId }
            if (i < 0) return@map l
            val j = (i + delta).coerceIn(0, list.size - 1)
            val e = list.removeAt(i); list.add(j, e)
            l.copy(effects = list)
        })
    }

    fun setEffectParam(effectId: String, key: String, value: Float) {
        val id = _ui.value.selectedLayerId ?: return
        val layer = selected() ?: return
        if (layer.locked) { message("Unlock this layer before editing it"); return }
        val local = _ui.value.playheadMs - layer.startMs
        commit { p -> p.copy(layers = p.layers.map { l ->
            if (l.id != id) return@map l
            l.copy(effects = l.effects.map { e ->
                if (e.id != effectId) return@map e
                if (_ui.value.autoKey || e.animated.containsKey(key)) {
                    val track = e.animated[key] ?: KeyframeTrack(key)
                    e.copy(animated = e.animated + (key to KeyframeEngine.upsert(
                        track, local, value, Easing.LINEAR)))
                } else e.copy(params = e.params + (key to value))
            })
        }) }
    }

    // -------------------------------------------------------- text / shape / vector
    fun updateText(transform: (TextSpec) -> TextSpec) = commit { p ->
        p.copy(layers = p.layers.map { l ->
            if (l.id == _ui.value.selectedLayerId && !l.locked && l.text != null) l.copy(text = transform(l.text)) else l
        })
    }

    fun updateShape(transform: (ShapeSpec) -> ShapeSpec) = commit { p ->
        p.copy(layers = p.layers.map { l ->
            if (l.id == _ui.value.selectedLayerId && !l.locked && l.shape != null) l.copy(shape = transform(l.shape)) else l
        })
    }

    fun updateVector(transform: (VectorSpec) -> VectorSpec) = commit { p ->
        p.copy(layers = p.layers.map { l ->
            if (l.id == _ui.value.selectedLayerId && !l.locked && l.vector != null) l.copy(vector = transform(l.vector)) else l
        })
    }

    // ---------------------------------------------------------------------- masks
    fun addMask(kind: MaskKind) = commit { p ->
        p.copy(layers = p.layers.map { l ->
            if (l.id == _ui.value.selectedLayerId && !l.locked)
                l.copy(masks = l.masks + MaskSpec(id = UUID.randomUUID().toString(), kind = kind))
            else l
        })
    }

    fun updateMask(maskId: String, transform: (MaskSpec) -> MaskSpec) = commit { p ->
        p.copy(layers = p.layers.map { l -> l.copy(
            masks = l.masks.map { if (it.id == maskId) transform(it) else it }
        ) })
    }

    fun removeMask(maskId: String) = commit { p ->
        p.copy(layers = p.layers.map { l -> l.copy(masks = l.masks.filterNot { it.id == maskId }) })
    }

    // ----------------------------------------------------------------- transitions
    fun setTransitionIn(type: TransitionType?, durationMs: Long) = commit { p ->
        p.copy(layers = p.layers.map { l ->
            if (l.id == _ui.value.selectedLayerId)
                l.copy(transitionIn = type?.let { TransitionSpec(it, durationMs) })
            else l
        })
    }

    fun setTransitionOut(type: TransitionType?, durationMs: Long) = commit { p ->
        p.copy(layers = p.layers.map { l ->
            if (l.id == _ui.value.selectedLayerId)
                l.copy(transitionOut = type?.let { TransitionSpec(it, durationMs) })
            else l
        })
    }

    fun addCrossfade(fromId: String, toId: String, durationMs: Long) = commit { p ->
        p.copy(transitions = p.transitions + TransitionClip(
            id = UUID.randomUUID().toString(), fromLayerId = fromId, toLayerId = toId,
            durationMs = durationMs
        ))
    }

    // ---------------------------------------------------------------------- audio
    fun setAudioTrack(id: String, transform: (AudioTrack) -> AudioTrack) = commit { p ->
        p.copy(audioTracks = p.audioTracks.map { if (it.id == id) transform(it) else it })
    }

    fun moveAudioTrack(id: String, deltaMs: Long) = commit { p ->
        val current = p.audioTracks.firstOrNull { it.id == id } ?: return@commit p
        var nextStart = TimelineMath.snapExact(
            current.startMs + deltaMs,
            p.fpsExact,
            _ui.value.snapping
        ).coerceAtLeast(0L)
        if (_ui.value.magneticSnapping) {
            val edges = p.layers.flatMap { listOf(it.startMs, it.startMs + it.durationMs) } +
                p.audioTracks.filterNot { it.id == id }
                    .flatMap { listOf(it.startMs, it.startMs + it.durationMs) }
            for (target in listOf(nextStart, nextStart + current.durationMs)) {
                val near = edges.minByOrNull { kotlin.math.abs(it - target) }
                if (near != null && kotlin.math.abs(near - target) < 120L) {
                    nextStart = (nextStart + near - target).coerceAtLeast(0L)
                    break
                }
            }
        }
        val audioTracks = p.audioTracks.map {
            if (it.id == id) it.copy(startMs = nextStart) else it
        }
        val audioEnd = audioTracks.maxOfOrNull { it.startMs + it.durationMs } ?: 0L
        p.copy(durationMs = maxOf(p.durationMs, audioEnd), audioTracks = audioTracks)
    }

    fun removeAudioTrack(id: String) = commit { p ->
        p.copy(audioTracks = p.audioTracks.filterNot { it.id == id })
    }

    // ------------------------------------------------------------- selection & clipboard
    fun toggleMultiSelect() {
        val on = !_ui.value.multiSelect
        _ui.value = _ui.value.copy(multiSelect = on,
            selectedLayerIds = if (on) setOfNotNull(_ui.value.selectedLayerId) else emptySet())
    }

    fun toggleLayerSelection(id: String) {
        val cur = _ui.value.selectedLayerIds.toMutableSet()
        if (!cur.add(id)) cur.remove(id)
        _ui.value = _ui.value.copy(selectedLayerIds = cur, selectedLayerId = id)
    }

    fun copyLayers() {
        val ids = if (_ui.value.multiSelect) _ui.value.selectedLayerIds else setOfNotNull(_ui.value.selectedLayerId)
        layerClipboard = project().layers.filter { it.id in ids }
        message("${layerClipboard.size} layer(s) copied")
    }

    fun pasteLayers() {
        if (layerClipboard.isEmpty()) { message("Nothing copied"); return }
        val pasted = layerClipboard.map { it.copy(
            id = UUID.randomUUID().toString(),
            name = "${it.name} copy",
            startMs = _ui.value.playheadMs.coerceAtLeast(0L),
            parentId = null,
            keyframes = it.keyframes.map { t -> t.copy(keyframes = t.keyframes.map {
                k -> k.copy(id = UUID.randomUUID().toString()) }) },
            effects = it.effects.map { e -> e.copy(id = UUID.randomUUID().toString()) },
            masks = it.masks.map { m -> m.copy(id = UUID.randomUUID().toString()) }
        ) }
        commit { p -> p.copy(layers = pasted + p.layers) }
        message("${pasted.size} layer(s) pasted")
    }

    fun groupSelected() {
        val ids = if (_ui.value.multiSelect) _ui.value.selectedLayerIds else setOfNotNull(_ui.value.selectedLayerId)
        if (ids.size < 2) { message("Select at least two layers"); return }
        val groupId = UUID.randomUUID().toString()
        val group = Layer(id = groupId, name = "Group", type = LayerType.GROUP,
            startMs = 0L, durationMs = project().durationMs)
        commit { p -> p.copy(layers = listOf(group) + p.layers.map {
            if (it.id in ids) it.copy(parentId = groupId) else it }) }
        message("Layers grouped")
    }

    fun ungroupSelected() {
        val ids = if (_ui.value.multiSelect) _ui.value.selectedLayerIds else setOfNotNull(_ui.value.selectedLayerId)
        commit { p -> p.copy(
            layers = p.layers.filterNot { it.id in ids && it.type == LayerType.GROUP }
                .map { if (it.parentId in ids) it.copy(parentId = null) else it }
        ) }
        message("Ungrouped")
    }

    fun toggleEnabled(id: String) = commit { p ->
        p.copy(layers = p.layers.map { if (it.id == id) it.copy(enabled = !it.enabled) else it })
    }

    fun toggleMuteLayer(id: String) = commit { p ->
        p.copy(layers = p.layers.map { if (it.id == id) it.copy(muted = !it.muted) else it })
    }

    fun toggleMuteAudio(id: String) = commit { p ->
        p.copy(audioTracks = p.audioTracks.map { if (it.id == id) it.copy(muted = !it.muted) else it })
    }

    fun extendClip(id: String, deltaMs: Long) = commit { p ->
        p.copy(layers = p.layers.map { if (it.id == id) TimelineMath.resizeEnd(it, deltaMs, p.fps, _ui.value.snapping) else it })
    }

    fun zoomToFit() {
        val d = project().durationMs.coerceAtLeast(1000L)
        // 70 dp/s is the default; fit into a ~3200dp-wide viewport.
        setZoom((3200f * 1000f / d).coerceIn(12f, 320f))
    }

    fun zoomToSelection() {
        val ids = if (_ui.value.multiSelect) _ui.value.selectedLayerIds else setOfNotNull(_ui.value.selectedLayerId)
        val sel = project().layers.filter { it.id in ids }
        if (sel.isEmpty()) { zoomToFit(); return }
        val start = sel.minOf { it.startMs }
        val end = sel.maxOf { it.startMs + it.durationMs }
        setZoom((3200f * 1000f / (end - start).coerceAtLeast(500L)).coerceIn(12f, 320f))
        seek(start)
    }

    fun setGuide(name: String, value: Boolean) {
        _ui.value = when (name) {
            "checkerboard" -> _ui.value.copy(checkerboard = value)
            "safe" -> _ui.value.copy(showSafeArea = value)
            "grid" -> _ui.value.copy(showGrid = value)
            "center" -> _ui.value.copy(showCenterGuides = value)
            else -> _ui.value.copy(showAspectGuides = value)
        }
        viewModelScope.launch {
            runCatching {
                settings.update { st -> when (name) {
                    "checkerboard" -> st.copy(checkerboard = value)
                    "safe" -> st.copy(showSafeArea = value)
                    "grid" -> st.copy(showGrid = value)
                    "center" -> st.copy(showCenterGuides = value)
                    else -> st.copy(showAspectGuides = value)
                } }
            }
        }
    }

    // --------------------------------------------------------------------- export
    fun startExport(config: ExportConfig) {
        val p = project()
        if (!_ui.value.backgroundRendering) {
            // Direct foreground export path (still real rendering, no WorkManager).
            viewModelScope.launch {
                _ui.value = _ui.value.copy(export = ExportUiState.Running(0, "Rendering"))
                val result = runCatching {
                    exporter.export(p, config, { percent, label ->
                        _ui.value = _ui.value.copy(export = ExportUiState.Running(percent, label))
                    }, { false })
                }.getOrElse { ExportResult.Failure("Render failed: ${it.message}") }
                _ui.value = _ui.value.copy(export = when (result) {
                    is ExportResult.Success -> ExportUiState.Success(result.file.absolutePath,
                        result.uri?.toString() ?: "")
                    is ExportResult.Failure -> ExportUiState.Failure(result.message)
                    ExportResult.Cancelled -> ExportUiState.Failure("Export cancelled")
                })
            }
            return
        }
        val data = Data.Builder()
            .putString(ExportWorker.KEY_PROJECT, ProjectSerializer.encode(p))
            .putInt(ExportWorker.KEY_WIDTH, config.width)
            .putInt(ExportWorker.KEY_HEIGHT, config.height)
            .putInt(ExportWorker.KEY_FPS, config.fps)
            .putInt(ExportWorker.KEY_BITRATE, config.bitrate)
            .putDouble(ExportWorker.KEY_FPS_EXACT, config.fpsExact)
            .putInt(ExportWorker.KEY_AUDIO_BITRATE, config.audioBitrate)
            .putString(ExportWorker.KEY_SAVE_LOCATION, config.saveLocation)
            .putString(ExportWorker.KEY_CODEC, config.codec.name)
            .putBoolean(ExportWorker.KEY_AUDIO, config.includeAudio)
            .build()
        val request = OneTimeWorkRequestBuilder<ExportWorker>()
            .setInputData(data)
            .addTag("export")
            .build()
        exportUuid = request.id
        try {
            workManager.enqueue(request)
        } catch (e: Exception) {
            _ui.value = _ui.value.copy(export = ExportUiState.Failure(
                "Could not start background render: ${e.message}"))
            return
        }
        _ui.value = _ui.value.copy(export = ExportUiState.Running(0, "Starting…"))
        viewModelScope.launch {
            workManager.getWorkInfoByIdLiveData(request.id).asFlow().collect { info ->
                if (info == null) return@collect
                val progress = info.progress
                when (info.state) {
                    WorkInfo.State.RUNNING -> _ui.value = _ui.value.copy(
                        export = ExportUiState.Running(
                            progress.getInt(ExportWorker.KEY_PROGRESS, 0),
                            progress.getString(ExportWorker.KEY_LABEL) ?: "Rendering"
                        )
                    )
                    WorkInfo.State.SUCCEEDED -> {
                        val out = info.outputData
                        _ui.value = _ui.value.copy(export = ExportUiState.Success(
                            out.getString(ExportWorker.KEY_FILE) ?: "",
                            out.getString(ExportWorker.KEY_URI) ?: ""
                        ))
                    }
                    WorkInfo.State.FAILED -> _ui.value = _ui.value.copy(
                        export = ExportUiState.Failure(
                            info.outputData.getString(ExportWorker.KEY_ERROR) ?: "Export failed"
                        )
                    )
                    WorkInfo.State.CANCELLED -> _ui.value = _ui.value.copy(
                        export = ExportUiState.Failure("Export cancelled")
                    )
                    else -> Unit
                }
            }
        }
    }

    fun cancelExport() { exportUuid?.let { workManager.cancelWorkById(it) } }
    fun dismissExport() { _ui.value = _ui.value.copy(export = ExportUiState.Idle) }
}
