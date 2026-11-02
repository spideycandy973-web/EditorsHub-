package com.motionstudio.editor.core.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.motionstudio.editor.core.domain.model.ExportResolution
import com.motionstudio.editor.core.domain.model.VideoCodec
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.map
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "motion_studio_settings")

/** Every field persists AND changes real behaviour somewhere in the app. */
data class AppSettings(
    val darkTheme: Boolean = true,
    val dynamicColor: Boolean = false,
    val uiScale: Float = 1f,
    val language: String = "system",
    val defaultProjectLocation: String = "internal",
    val autosaveIntervalMs: Long = 700L,
    val confirmBeforeDelete: Boolean = true,
    val previewQuality: Float = 0.6f,
    val cacheLimitMb: Int = 256,
    val hardwareAcceleration: Boolean = true,
    val backgroundRendering: Boolean = true,
    val proxyEnabled: Boolean = false,
    val proxyScale: Float = 0.5f,
    val defaultResolution: ExportResolution = ExportResolution.P1080,
    val defaultFps: Int = 30,
    val defaultCodec: VideoCodec = VideoCodec.H264,
    val defaultBitrate: Int = 8_000_000,
    val defaultQuality: Int = 80,
    val defaultSaveLocation: String = "gallery",
    val defaultIncludeAudio: Boolean = true,
    val snapping: Boolean = true,
    val magneticSnapping: Boolean = true,
    val showSafeArea: Boolean = false,
    val showGrid: Boolean = false,
    val showCenterGuides: Boolean = false,
    val showAspectGuides: Boolean = false,
    val checkerboard: Boolean = true,
    val aiEnabled: Boolean = false,
    val aiBaseUrl: String = "https://api.openai.com/v1",
    val aiModel: String = "gpt-4o-mini",
    val aiApiKey: String = "",
    val aiTemperature: Float = 0.4f,
    val aiMaxTokens: Int = 800
) {
    val aiConfigured: Boolean get() = aiEnabled && aiApiKey.isNotBlank() && aiBaseUrl.isNotBlank()
}

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object K {
        val DARK = booleanPreferencesKey("dark_theme")
        val DYNAMIC = booleanPreferencesKey("dynamic_color")
        val UI_SCALE = floatPreferencesKey("ui_scale")
        val LANGUAGE = stringPreferencesKey("language")
        val PROJECT_LOCATION = stringPreferencesKey("default_project_location")
        val AUTOSAVE = longPreferencesKey("autosave_interval_ms")
        val CONFIRM_DELETE = booleanPreferencesKey("confirm_before_delete")
        val QUALITY = floatPreferencesKey("preview_quality")
        val CACHE = intPreferencesKey("cache_limit_mb")
        val HW = booleanPreferencesKey("hardware_acceleration")
        val BG_RENDER = booleanPreferencesKey("background_rendering")
        val PROXY = booleanPreferencesKey("proxy_enabled")
        val PROXY_SCALE = floatPreferencesKey("proxy_scale")
        val RESOLUTION = stringPreferencesKey("default_resolution")
        val FPS = intPreferencesKey("default_fps")
        val CODEC = stringPreferencesKey("default_codec")
        val BITRATE = intPreferencesKey("default_bitrate")
        val QUALITY_EXPORT = intPreferencesKey("default_quality")
        val SAVE_LOCATION = stringPreferencesKey("default_save_location")
        val AUDIO = booleanPreferencesKey("default_audio")
        val SNAP = booleanPreferencesKey("snapping")
        val MAGNETIC = booleanPreferencesKey("magnetic_snapping")
        val SAFE = booleanPreferencesKey("show_safe_area")
        val GRID = booleanPreferencesKey("show_grid")
        val CENTER = booleanPreferencesKey("show_center_guides")
        val ASPECT = booleanPreferencesKey("show_aspect_guides")
        val CHECKER = booleanPreferencesKey("checkerboard")
        val AI_ENABLED = booleanPreferencesKey("ai_enabled")
        val AI_URL = stringPreferencesKey("ai_base_url")
        val AI_MODEL = stringPreferencesKey("ai_model")
        val AI_KEY = stringPreferencesKey("ai_api_key")
        val AI_TEMP = floatPreferencesKey("ai_temperature")
        val AI_MAX = intPreferencesKey("ai_max_tokens")
    }

    val settings: Flow<AppSettings> = context.dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { p -> p.toSettings() }

    private fun Preferences.toSettings() = AppSettings(
        darkTheme = this[K.DARK] ?: true,
        dynamicColor = this[K.DYNAMIC] ?: false,
        uiScale = (this[K.UI_SCALE] ?: 1f).coerceIn(0.8f, 1.4f),
        language = this[K.LANGUAGE] ?: "system",
        defaultProjectLocation = this[K.PROJECT_LOCATION] ?: "internal",
        autosaveIntervalMs = (this[K.AUTOSAVE] ?: 700L).coerceIn(200L, 10_000L),
        confirmBeforeDelete = this[K.CONFIRM_DELETE] ?: true,
        previewQuality = (this[K.QUALITY] ?: 0.6f).coerceIn(0.2f, 1f),
        cacheLimitMb = (this[K.CACHE] ?: 256).coerceIn(32, 2048),
        hardwareAcceleration = this[K.HW] ?: true,
        backgroundRendering = this[K.BG_RENDER] ?: true,
        proxyEnabled = this[K.PROXY] ?: false,
        proxyScale = (this[K.PROXY_SCALE] ?: 0.5f).coerceIn(0.25f, 1f),
        defaultResolution = runCatching {
            ExportResolution.valueOf(this[K.RESOLUTION] ?: "P1080")
        }.getOrDefault(ExportResolution.P1080),
        defaultFps = (this[K.FPS] ?: 30).coerceIn(1, 240),
        defaultCodec = runCatching { VideoCodec.valueOf(this[K.CODEC] ?: "H264") }
            .getOrDefault(VideoCodec.H264),
        defaultBitrate = this[K.BITRATE] ?: 8_000_000,
        defaultQuality = (this[K.QUALITY_EXPORT] ?: 80).coerceIn(10, 100),
        defaultSaveLocation = this[K.SAVE_LOCATION] ?: "gallery",
        defaultIncludeAudio = this[K.AUDIO] ?: true,
        snapping = this[K.SNAP] ?: true,
        magneticSnapping = this[K.MAGNETIC] ?: true,
        showSafeArea = this[K.SAFE] ?: false,
        showGrid = this[K.GRID] ?: false,
        showCenterGuides = this[K.CENTER] ?: false,
        showAspectGuides = this[K.ASPECT] ?: false,
        checkerboard = this[K.CHECKER] ?: true,
        aiEnabled = this[K.AI_ENABLED] ?: false,
        aiBaseUrl = this[K.AI_URL] ?: "https://api.openai.com/v1",
        aiModel = this[K.AI_MODEL] ?: "gpt-4o-mini",
        aiApiKey = this[K.AI_KEY] ?: "",
        aiTemperature = (this[K.AI_TEMP] ?: 0.4f).coerceIn(0f, 1f),
        aiMaxTokens = (this[K.AI_MAX] ?: 800).coerceIn(128, 4096)
    )

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        val current = try { settings.firstOrNull() ?: AppSettings() } catch (e: Exception) { AppSettings() }
        val n = transform(current)
        context.dataStore.edit { p ->
            p[K.DARK] = n.darkTheme; p[K.DYNAMIC] = n.dynamicColor; p[K.UI_SCALE] = n.uiScale
            p[K.LANGUAGE] = n.language; p[K.PROJECT_LOCATION] = n.defaultProjectLocation
            p[K.AUTOSAVE] = n.autosaveIntervalMs; p[K.CONFIRM_DELETE] = n.confirmBeforeDelete
            p[K.QUALITY] = n.previewQuality; p[K.CACHE] = n.cacheLimitMb
            p[K.HW] = n.hardwareAcceleration; p[K.BG_RENDER] = n.backgroundRendering
            p[K.PROXY] = n.proxyEnabled; p[K.PROXY_SCALE] = n.proxyScale
            p[K.RESOLUTION] = n.defaultResolution.name; p[K.FPS] = n.defaultFps
            p[K.CODEC] = n.defaultCodec.name; p[K.BITRATE] = n.defaultBitrate
            p[K.QUALITY_EXPORT] = n.defaultQuality; p[K.SAVE_LOCATION] = n.defaultSaveLocation
            p[K.AUDIO] = n.defaultIncludeAudio; p[K.SNAP] = n.snapping
            p[K.MAGNETIC] = n.magneticSnapping; p[K.SAFE] = n.showSafeArea
            p[K.GRID] = n.showGrid; p[K.CENTER] = n.showCenterGuides
            p[K.ASPECT] = n.showAspectGuides; p[K.CHECKER] = n.checkerboard
            p[K.AI_ENABLED] = n.aiEnabled; p[K.AI_URL] = n.aiBaseUrl
            p[K.AI_MODEL] = n.aiModel; p[K.AI_KEY] = n.aiApiKey
            p[K.AI_TEMP] = n.aiTemperature; p[K.AI_MAX] = n.aiMaxTokens
        }
    }

    fun cacheSizeBytes(): Long {
        fun size(f: File): Long = if (f.isDirectory) (f.listFiles()?.sumOf { size(it) } ?: 0L) else f.length()
        return size(context.cacheDir)
    }

    /** Removes caches and enforces the configured size limit by deleting oldest files first. */
    fun clearCache(): Long {
        var removed = 0L
        val dirs = listOf(File(context.cacheDir, "frames"), File(context.cacheDir, "thumbs"))
        for (dir in dirs) dir.listFiles()?.forEach { f ->
            if (f.isFile) { removed += f.length(); f.delete() }
        }
        val limit = runBlocking { settings.first().cacheLimitMb.toLong() * 1024L * 1024L }
        fun allFiles(): List<File> = context.cacheDir.walkTopDown()
            .filter { it.isFile && it.name != "keystore.properties" }.toList()
        var files = allFiles().sortedBy { it.lastModified() }
        var total = files.sumOf { it.length() }
        while (total > limit && files.isNotEmpty()) {
            val f = files.first()
            total -= f.length(); removed += f.length(); f.delete()
            files = files.drop(1)
        }
        return removed
    }
}
