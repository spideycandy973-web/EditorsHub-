package com.motionstudio.editor.core.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.motionstudio.editor.core.domain.model.ExportResolution
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "motion_studio_settings")

data class AppSettings(
    val darkTheme: Boolean = true,
    val dynamicColor: Boolean = false,
    val previewQuality: Float = 0.6f,
    val defaultResolution: ExportResolution = ExportResolution.P1080,
    val defaultFps: Int = 30,
    val defaultBitrate: Int = 8_000_000,
    val defaultIncludeAudio: Boolean = true,
    val language: String = "system",
    val cacheLimitMb: Int = 256,
    val snapping: Boolean = true
)

@Singleton
class SettingsRepository @Inject constructor(private val context: Context) {

    private object Keys {
        val DARK = booleanPreferencesKey("dark_theme")
        val DYNAMIC = booleanPreferencesKey("dynamic_color")
        val QUALITY = floatPreferencesKey("preview_quality")
        val RESOLUTION = stringPreferencesKey("default_resolution")
        val FPS = intPreferencesKey("default_fps")
        val BITRATE = intPreferencesKey("default_bitrate")
        val AUDIO = booleanPreferencesKey("default_audio")
        val LANGUAGE = stringPreferencesKey("language")
        val CACHE = intPreferencesKey("cache_limit_mb")
        val SNAP = booleanPreferencesKey("snapping")
    }

    val settings: Flow<AppSettings> = context.dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs ->
            AppSettings(
                darkTheme = prefs[Keys.DARK] ?: true,
                dynamicColor = prefs[Keys.DYNAMIC] ?: false,
                previewQuality = prefs[Keys.QUALITY] ?: 0.6f,
                defaultResolution = runCatching {
                    ExportResolution.valueOf(prefs[Keys.RESOLUTION] ?: "P1080")
                }.getOrDefault(ExportResolution.P1080),
                defaultFps = prefs[Keys.FPS] ?: 30,
                defaultBitrate = prefs[Keys.BITRATE] ?: 8_000_000,
                defaultIncludeAudio = prefs[Keys.AUDIO] ?: true,
                language = prefs[Keys.LANGUAGE] ?: "system",
                cacheLimitMb = prefs[Keys.CACHE] ?: 256,
                snapping = prefs[Keys.SNAP] ?: true
            )
        }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        val current = try { settings.firstOrNull() ?: AppSettings() }
            catch (e: Exception) { AppSettings() }
        val next = transform(current)
        context.dataStore.edit { prefs ->
            prefs[Keys.DARK] = next.darkTheme
            prefs[Keys.DYNAMIC] = next.dynamicColor
            prefs[Keys.QUALITY] = next.previewQuality
            prefs[Keys.RESOLUTION] = next.defaultResolution.name
            prefs[Keys.FPS] = next.defaultFps
            prefs[Keys.BITRATE] = next.defaultBitrate
            prefs[Keys.AUDIO] = next.defaultIncludeAudio
            prefs[Keys.LANGUAGE] = next.language
            prefs[Keys.CACHE] = next.cacheLimitMb
            prefs[Keys.SNAP] = next.snapping
        }
    }

    /** Removes cached preview frames and decoded bitmaps. */
    fun clearCache(): Long {
        var removed = 0L
        val dirs = listOf(context.cacheDir, java.io.File(context.cacheDir, "frames"))
        for (dir in dirs) {
            dir.listFiles()?.forEach { f ->
                if (f.isFile) { removed += f.length(); f.delete() }
            }
        }
        return removed
    }

    fun cacheSizeBytes(): Long =
        (context.cacheDir.listFiles()?.sumOf { if (it.isFile) it.length() else 0L } ?: 0L)
}
