package com.editorshub.ui.feature.home

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.core.content.FileProvider
import com.editorshub.core.ai.AiKeyStore
import com.editorshub.core.data.ProjectRepository
import com.editorshub.core.data.ProjectSerializer
import com.editorshub.core.data.TemplateCatalog
import com.editorshub.core.domain.model.*
import com.editorshub.core.importer.ImportResult
import com.editorshub.core.importer.ProjectImporter
import com.editorshub.core.media.MediaKind
import com.editorshub.core.media.MediaRepository
import com.editorshub.core.rendering.ThumbnailGenerator
import com.editorshub.core.rendering.export.ExportWorker
import com.editorshub.core.settings.AppSettings
import com.editorshub.core.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

enum class ProjectSort { DATE, NAME, DURATION, SIZE }

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: ProjectRepository,
    private val settings: SettingsRepository,
    private val media: MediaRepository,
    private val thumbnails: ThumbnailGenerator,
    private val workManager: WorkManager,
    private val projectImporter: ProjectImporter
) : ViewModel() {

    val projects: StateFlow<List<ProjectSummary>> = repository.observeProjects()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val appSettings: StateFlow<AppSettings> = settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())
    val templates = TemplateCatalog.templates

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    private val _sort = MutableStateFlow(ProjectSort.DATE)
    val sort: StateFlow<ProjectSort> = _sort.asStateFlow()
    private val _grid = MutableStateFlow(false)
    val grid: StateFlow<Boolean> = _grid.asStateFlow()

    val visible: StateFlow<List<ProjectSummary>> = combine(projects, _query, _sort) { list, q, s ->
        val filtered = if (q.isBlank()) list else list.filter { it.name.contains(q, true) }
        when (s) {
            ProjectSort.DATE -> filtered.sortedByDescending { it.updatedAt }
            ProjectSort.NAME -> filtered.sortedBy { it.name.lowercase() }
            ProjectSort.DURATION -> filtered.sortedByDescending { it.durationMs }
            ProjectSort.SIZE -> filtered.sortedByDescending { it.bytes }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _open = MutableSharedFlow<String>()
    val openProject = _open.asSharedFlow()
    private val _message = MutableSharedFlow<String>()
    val message = _message.asSharedFlow()
    private val _info = MutableStateFlow<ProjectSummary?>(null)
    val info: StateFlow<ProjectSummary?> = _info.asStateFlow()

    fun setQuery(v: String) { _query.value = v }
    fun setSort(v: ProjectSort) { _sort.value = v }
    fun setGrid(v: Boolean) { _grid.value = v }
    fun showInfo(s: ProjectSummary?) { _info.value = s }

    fun createProject(spec: ProjectSpec) = viewModelScope.launch {
        runCatching {
            val project = repository.create(spec)
            saveThumbnail(project)
            _open.emit(project.id)
        }.onFailure { _message.emit("Could not create project: ${it.message}") }
    }

    fun createFromTemplate(template: TemplateCatalog.Template) = viewModelScope.launch {
        runCatching {
            val project = template.build().copy(id = UUID.randomUUID().toString())
            repository.save(project)
            saveThumbnail(project)
            _open.emit(project.id)
        }.onFailure { _message.emit("Could not create from template: ${it.message}") }
    }

    /** Downloads and imports a shared package URL. Only HTTPS is accepted; data is streamed to app-private storage. */
    fun importPackageLink(rawUrl: String) = viewModelScope.launch {
        val parsed = runCatching { URI(rawUrl.trim()) }.getOrNull()
        if (parsed == null || parsed.scheme?.equals("https", ignoreCase = true) != true || parsed.host.isNullOrBlank()) {
            _message.emit("Enter a valid HTTPS project-package link")
            return@launch
        }
        val url = runCatching { URL(parsed.toString()) }.getOrNull()
            ?: run { _message.emit("This package link is not valid"); return@launch }
        val pathExt = parsed.path.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val targetExt = pathExt.takeIf { it in setOf("xml", "json", "zip", "amproj", "aepx", "aep", "ehp", "node", "nvp") }
            ?: "zip"
        val target = File(context.filesDir, "package-downloads/${UUID.randomUUID()}.$targetExt")
        val maxBytes = 512L * 1024L * 1024L
        runCatching {
            withContext(Dispatchers.IO) {
                target.parentFile?.mkdirs()
                val connection = (url.openConnection() as? HttpURLConnection)
                    ?: error("Only HTTPS package links are supported")
                try {
                    connection.connectTimeout = 20_000
                    connection.readTimeout = 30_000
                    connection.instanceFollowRedirects = true
                    connection.connect()
                    if (connection.url.protocol != "https") error("The package redirected to a non-secure link")
                    if (connection.responseCode !in 200..299) error("Server returned HTTP ${connection.responseCode}")
                    val declared = connection.contentLengthLong
                    if (declared > maxBytes) error("Package is larger than 512 MB")
                    connection.inputStream.use { input ->
                        target.outputStream().buffered().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var total = 0L
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                total += read
                                if (total > maxBytes) error("Package is larger than 512 MB")
                                output.write(buffer, 0, read)
                            }
                        }
                    }
                } finally {
                    connection.disconnect()
                }
                if (target.length() == 0L) error("The package download was empty")
                val contentUri = FileProvider.getUriForFile(context,
                    "${context.packageName}.fileprovider", target)
                when (val imported = projectImporter.import(contentUri, { _, _ -> Unit }, { false })) {
                    is ImportResult.Success -> {
                        _message.emit("Package imported: ${imported.project.name}")
                        _open.emit(imported.project.id)
                    }
                    is ImportResult.Failure -> error(imported.message)
                    is ImportResult.ReportOnly -> error(imported.report.headline)
                    ImportResult.Cancelled -> error("Package import was cancelled")
                }
            }
        }.onFailure { error ->
            _message.emit("Package import failed: ${error.message ?: "network error"}")
        }
        target.delete()
    }

    fun importMedia(uris: List<Uri>, names: List<String?>) = viewModelScope.launch {
        val project = runCatching { repository.create(ProjectSpec(name = "Imported media")) }
            .getOrElse { _message.emit("Could not create project: ${it.message}"); return@launch }
        val layers = mutableListOf<Layer>()
        val audio = mutableListOf<AudioTrack>()
        uris.forEachIndexed { i, uri ->
            val imported = runCatching { media.import(uri, names.getOrNull(i)) }.getOrNull() ?: return@forEachIndexed
            val info = imported.info
            val path = "file://${info.path}"
            when (info.kind) {
                MediaKind.AUDIO -> audio.add(AudioTrack(
                    id = UUID.randomUUID().toString(), uri = path, name = info.name,
                    durationMs = info.durationMs.coerceAtLeast(1000L),
                    peaks = media.waveform(info.path)))
                MediaKind.IMAGE, MediaKind.VIDEO -> layers.add(Layer(
                    id = UUID.randomUUID().toString(), name = info.name,
                    type = if (info.kind == MediaKind.VIDEO) LayerType.VIDEO else LayerType.IMAGE,
                    sourceUri = path, startMs = 0L,
                    durationMs = if (info.durationMs > 0) info.durationMs.coerceAtMost(30_000L) else 5000L))
                MediaKind.UNKNOWN -> Unit
            }
        }
        val updated = project.copy(layers = layers, audioTracks = audio,
            durationMs = maxOf(5000L, layers.maxOfOrNull { it.durationMs } ?: 5000L))
        runCatching {
            repository.save(updated)
            saveThumbnail(updated)
            _open.emit(updated.id)
        }.onFailure { _message.emit("Import failed: ${it.message}") }
    }

    private suspend fun saveThumbnail(project: Project) {
        runCatching {
            val path = withContext(Dispatchers.Default) { thumbnails.generate(project) }
            if (path != null) repository.save(project, thumbnailPath = path, autosaveOnly = true)
        }
    }

    fun rename(id: String, name: String) = viewModelScope.launch {
        runCatching { repository.rename(id, name) }
    }
    fun duplicate(id: String) = viewModelScope.launch {
        val copy = runCatching { repository.duplicate(id) }.getOrNull()
        if (copy != null) { saveThumbnail(copy); _message.emit("Project duplicated") }
        else _message.emit("Duplicate failed")
    }
    fun delete(id: String) = viewModelScope.launch {
        runCatching { repository.delete(id) }
        _message.emit("Project deleted")
    }

    /** Real share: writes the versioned native project JSON and returns its path. */
    suspend fun shareProject(id: String): String? = runCatching {
        val project = repository.load(id) ?: throw NoSuchElementException("Project not found")
        val configured = runCatching { settings.settings.first() }.getOrDefault(AppSettings())
        val base = if (configured.defaultProjectLocation == "external")
            (context.getExternalFilesDir(null) ?: context.filesDir) else context.cacheDir
        val dir = File(base, "shared").apply { mkdirs() }
        val safe = project.name.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val f = File(dir, "$safe.ehproj")
        f.writeText(ProjectSerializer.encode(project.copy(name = project.name)))
        f.absolutePath
    }.getOrNull()

    /** Real export: enqueues the background MediaCodec/MediaMuxer worker. */
    fun exportProject(id: String, config: ExportConfig, onQueued: (UUID) -> Unit) = viewModelScope.launch {
        val project = runCatching { repository.load(id) }.getOrNull()
            ?: run { _message.emit("Project not found"); return@launch }
        val data = Data.Builder()
            .putString(ExportWorker.KEY_PROJECT, ProjectSerializer.encode(project))
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
            .setInputData(data).addTag("export").build()
        runCatching { workManager.enqueue(request) }.onFailure {
            _message.emit("Could not start export: ${it.message}")
            return@launch
        }
        onQueued(request.id)
        _message.emit("Export started in background")
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) = viewModelScope.launch {
        runCatching { settings.update(transform) }
    }
    fun setAiKey(plain: String) = viewModelScope.launch {
        runCatching { settings.update { it.copy(aiApiKey = AiKeyStore.encrypt(plain)) } }
    }
    fun aiKeyConfigured(): Boolean {
        return appSettings.value.aiApiKey.isNotBlank()
    }
    fun clearCache(): Long = settings.clearCache(appSettings.value.cacheLimitMb)
    fun cacheSize() = settings.cacheSizeBytes()

    fun formatDate(ms: Long): String =
        SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(ms))
    fun formatDuration(ms: Long): String {
        val total = ms / 1000
        return "%02d:%02d".format(total / 60, total % 60)
    }
    fun formatBytes(b: Long): String = when {
        b < 1024 -> "$b B"
        b < 1024 * 1024 -> "%.1f KB".format(b / 1024.0)
        else -> "%.1f MB".format(b / 1024.0 / 1024.0)
    }
    fun formatFps(exact: Double): String = if (exact % 1.0 == 0.0) "${exact.toInt()} fps"
        else "%.3f fps".format(exact)
}
