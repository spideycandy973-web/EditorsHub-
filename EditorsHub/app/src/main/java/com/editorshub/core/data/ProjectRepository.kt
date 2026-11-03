package com.editorshub.core.data

import com.editorshub.core.database.ProjectDao
import com.editorshub.core.database.ProjectEntity
import com.editorshub.core.database.ProjectVersionEntity
import com.editorshub.core.domain.model.PROJECT_FORMAT_VERSION
import com.editorshub.core.domain.model.Project
import com.editorshub.core.domain.model.ProjectSummary
import com.editorshub.core.domain.model.ProjectSpec
import com.editorshub.core.domain.model.FrameRates
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

interface ProjectRepository {
    fun observeProjects(): Flow<List<ProjectSummary>>
    suspend fun create(name: String, width: Int, height: Int, fps: Int, durationMs: Long): Project
    suspend fun create(spec: ProjectSpec): Project
    suspend fun load(id: String): Project?
    suspend fun save(project: Project, thumbnailPath: String? = null, autosaveOnly: Boolean = false)
    suspend fun rename(id: String, name: String)
    suspend fun duplicate(id: String): Project?
    suspend fun delete(id: String)
    suspend fun versions(id: String): List<Pair<Int, Long>>
    suspend fun restoreVersion(id: String, version: Int): Project?
    suspend fun recoverInterrupted(): List<ProjectSummary>
}

@Singleton
class ProjectRepositoryImpl @Inject constructor(
    private val dao: ProjectDao
) : ProjectRepository {

    override fun observeProjects(): Flow<List<ProjectSummary>> = dao.observe().map { rows ->
        rows.map { row -> row.toSummary() }
    }

    private fun ProjectEntity.toSummary(): ProjectSummary {
        val project = runCatching {
            ProjectSerializer.migrate(autosaveJson.ifBlank { publishedJson })
        }.getOrDefault(Project())
        val thumbBytes = thumbnailPath?.let { java.io.File(it).length() } ?: 0L
        return ProjectSummary(
            id = id,
            name = name,
            updatedAt = updatedAt,
            createdAt = createdAt,
            layerCount = layerCount,
            version = version,
            thumbnailPath = thumbnailPath,
            interrupted = autosaveJson != publishedJson,
            width = project.width,
            height = project.height,
            fpsExact = project.fpsExact,
            durationMs = project.durationMs,
            bytes = autosaveJson.toByteArray(Charsets.UTF_8).size.toLong() + thumbBytes
        )
    }

    override suspend fun create(name: String, width: Int, height: Int, fps: Int, durationMs: Long): Project =
        create(ProjectSpec(name = name, width = width, height = height,
            fpsExact = fps.toDouble(), durationMs = durationMs))

    override suspend fun create(spec: ProjectSpec): Project {
        val now = System.currentTimeMillis()
        val project = Project(
            id = UUID.randomUUID().toString(),
            name = spec.name,
            width = spec.width.coerceIn(16, 4096),
            height = spec.height.coerceIn(16, 4096),
            fps = FrameRates.rounded(spec.fpsExact),
            fpsExact = spec.fpsExact,
            durationMs = spec.durationMs,
            background = spec.background,
            backgroundType = spec.backgroundType,
            backgroundEnd = spec.backgroundEnd,
            backgroundAngle = spec.backgroundAngle,
            audioSampleRate = spec.audioSampleRate,
            colorSpace = spec.colorSpace,
            previewQuality = spec.previewQuality,
            timebase = spec.timebase,
            version = PROJECT_FORMAT_VERSION,
            updatedAt = now,
            createdAt = now
        )
        save(project)
        return project
    }

    override suspend fun load(id: String): Project? {
        val entity = dao.get(id) ?: return null
        val raw = entity.autosaveJson.ifBlank { entity.publishedJson }
        return runCatching { ProjectSerializer.migrate(raw) }.getOrNull()
    }

    override suspend fun save(project: Project, thumbnailPath: String?, autosaveOnly: Boolean) {
        val now = System.currentTimeMillis()
        val normalized = project.copy(
            version = PROJECT_FORMAT_VERSION,
            updatedAt = now,
            fps = project.fps.coerceIn(1, 120),
            durationMs = project.durationMs.coerceAtLeast(100L)
        )
        val json = ProjectSerializer.encode(normalized)
        val existing = dao.get(normalized.id)
        val published = if (autosaveOnly) (existing?.publishedJson ?: json) else json
        val nextVersion = if (!autosaveOnly) (existing?.version ?: 0) + 1 else (existing?.version ?: 1)
        dao.put(
            ProjectEntity(
                id = normalized.id,
                name = normalized.name,
                publishedJson = published,
                autosaveJson = json,
                thumbnailPath = thumbnailPath ?: existing?.thumbnailPath,
                createdAt = existing?.createdAt ?: normalized.createdAt.takeIf { it > 0 } ?: now,
                updatedAt = now,
                version = nextVersion,
                layerCount = normalized.layers.size
            )
        )
        if (!autosaveOnly) {
            dao.addVersion(
                ProjectVersionEntity(
                    projectId = normalized.id,
                    version = nextVersion,
                    json = json,
                    createdAt = now
                )
            )
        }
    }

    override suspend fun rename(id: String, name: String) {
        val project = load(id) ?: return
        save(project.copy(name = name))
    }

    override suspend fun duplicate(id: String): Project? {
        val original = load(id) ?: return null
        val copy = original.copy(
            id = UUID.randomUUID().toString(),
            name = "${original.name} copy",
            updatedAt = System.currentTimeMillis(),
            layers = original.layers.map { it.copy(id = UUID.randomUUID().toString()) }
        )
        save(copy)
        return copy
    }

    override suspend fun delete(id: String) {
        dao.deleteVersions(id)
        dao.delete(id)
    }

    override suspend fun versions(id: String): List<Pair<Int, Long>> =
        dao.versions(id).map { it.version to it.createdAt }

    override suspend fun restoreVersion(id: String, version: Int): Project? {
        val snapshot = dao.versions(id).firstOrNull { it.version == version } ?: return null
        val project = runCatching { ProjectSerializer.migrate(snapshot.json) }
            .getOrNull()?.copy(id = id) ?: return null
        save(project)
        return project
    }

    override suspend fun recoverInterrupted(): List<ProjectSummary> =
        observeProjects().let { flow ->
            // Materialise once; Room Flow emits the current snapshot immediately.
            var result: List<ProjectSummary> = emptyList()
            try {
                result = flow.first().filter { p -> p.interrupted }
            } catch (e: Exception) { /* no snapshot yet */ }
            result
        }
}
