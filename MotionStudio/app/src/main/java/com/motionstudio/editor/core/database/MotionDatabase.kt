package com.motionstudio.editor.core.database

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** Last explicitly saved snapshot. */
    val publishedJson: String,
    /** Latest autosave snapshot; differs from publishedJson when a session was interrupted. */
    val autosaveJson: String,
    val thumbnailPath: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val version: Int,
    val layerCount: Int
)

@Entity(tableName = "project_versions")
data class ProjectVersionEntity(
    @PrimaryKey(autoGenerate = true) val uid: Long = 0L,
    val projectId: String,
    val version: Int,
    val json: String,
    val createdAt: Long
)

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    fun observe(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun get(id: String): ProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: ProjectEntity)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun delete(id: String)

    @Insert
    suspend fun addVersion(entity: ProjectVersionEntity)

    @Query("SELECT * FROM project_versions WHERE projectId = :id ORDER BY version DESC")
    suspend fun versions(id: String): List<ProjectVersionEntity>

    @Query("DELETE FROM project_versions WHERE projectId = :id")
    suspend fun deleteVersions(id: String)
}

@Database(
    entities = [ProjectEntity::class, ProjectVersionEntity::class],
    version = 1,
    exportSchema = false
)
abstract class MotionDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
}
