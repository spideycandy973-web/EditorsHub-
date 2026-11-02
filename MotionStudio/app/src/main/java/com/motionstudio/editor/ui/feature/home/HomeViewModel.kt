package com.motionstudio.editor.ui.feature.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.motionstudio.editor.core.data.ProjectRepository
import com.motionstudio.editor.core.data.TemplateCatalog
import com.motionstudio.editor.core.domain.model.AudioTrack
import com.motionstudio.editor.core.domain.model.Layer
import com.motionstudio.editor.core.domain.model.LayerType
import com.motionstudio.editor.core.domain.model.ProjectSummary
import com.motionstudio.editor.core.media.MediaKind
import com.motionstudio.editor.core.media.MediaRepository
import com.motionstudio.editor.core.rendering.ThumbnailGenerator
import com.motionstudio.editor.core.settings.AppSettings
import com.motionstudio.editor.core.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: ProjectRepository,
    private val settings: SettingsRepository,
    private val media: MediaRepository,
    private val thumbnails: ThumbnailGenerator
) : ViewModel() {

    val projects: StateFlow<List<ProjectSummary>> = repository.observeProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val appSettings: StateFlow<AppSettings> = settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())
    val templates = TemplateCatalog.templates

    private val _open = MutableSharedFlow<String>()
    val openProject = _open.asSharedFlow()

    private val _message = MutableSharedFlow<String>()
    val message = _message.asSharedFlow()

    fun createProject(name: String, template: TemplateCatalog.Template? = null) = viewModelScope.launch {
        val project = if (template != null) {
            template.build().copy(id = UUID.randomUUID().toString(), name = name)
        } else {
            repository.create(name, 1080, 1920, 30, 10_000L)
        }
        repository.save(project)
        saveThumbnail(project)
        _open.emit(project.id)
    }

    fun importMedia(uris: List<Uri>, names: List<String?>) = viewModelScope.launch {
        val project = repository.create("Imported media", 1080, 1920, 30, 10_000L)
        val layers = mutableListOf<Layer>()
        val audio = mutableListOf<AudioTrack>()
        uris.forEachIndexed { i, uri ->
            val imported = runCatching { media.import(uri, names.getOrNull(i)) }.getOrNull() ?: return@forEachIndexed
            val info = imported.info
            val path = "file://${info.path}"
            when (info.kind) {
                MediaKind.AUDIO -> audio.add(
                    AudioTrack(
                        id = UUID.randomUUID().toString(), uri = path, name = info.name,
                        durationMs = info.durationMs.coerceAtLeast(1000L),
                        peaks = media.waveform(info.path)
                    )
                )
                MediaKind.IMAGE, MediaKind.VIDEO -> layers.add(
                    Layer(
                        id = UUID.randomUUID().toString(), name = info.name,
                        type = if (info.kind == MediaKind.VIDEO) LayerType.VIDEO else LayerType.IMAGE,
                        sourceUri = path,
                        durationMs = if (info.durationMs > 0) info.durationMs.coerceAtMost(30_000L) else 5000L
                    )
                )
                MediaKind.UNKNOWN -> Unit
            }
        }
        val updated = project.copy(layers = layers, audioTracks = audio,
            durationMs = maxOf(5000L, layers.maxOfOrNull { it.durationMs } ?: 5000L))
        repository.save(updated)
        saveThumbnail(updated)
        _open.emit(updated.id)
    }

    private suspend fun saveThumbnail(project: com.motionstudio.editor.core.domain.model.Project) {
        val path = withContext(Dispatchers.Default) { thumbnails.generate(project) }
        if (path != null) repository.save(project, thumbnailPath = path, autosaveOnly = true)
    }

    fun rename(id: String, name: String) = viewModelScope.launch { repository.rename(id, name) }
    fun duplicate(id: String) = viewModelScope.launch {
        val copy = repository.duplicate(id)
        if (copy != null) { saveThumbnail(copy); _message.emit("Project duplicated") }
    }
    fun delete(id: String) = viewModelScope.launch { repository.delete(id); _message.emit("Project deleted") }

    fun updateSettings(transform: (AppSettings) -> AppSettings) = viewModelScope.launch {
        settings.update(transform)
    }

    fun clearCache() = settings.clearCache()
    fun cacheSize() = settings.cacheSizeBytes()
}
