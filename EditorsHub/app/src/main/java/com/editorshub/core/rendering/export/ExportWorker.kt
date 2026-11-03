package com.editorshub.core.rendering.export

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.editorshub.R
import com.editorshub.core.data.ProjectSerializer
import com.editorshub.core.domain.model.ExportConfig
import com.editorshub.core.domain.model.VideoCodec
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Background export with genuine progress and cancellation. */
@HiltWorker
class ExportWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted params: WorkerParameters,
    private val exporter: VideoExporter
) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_PROJECT = "project_json"
        const val KEY_WIDTH = "width"
        const val KEY_HEIGHT = "height"
        const val KEY_FPS = "fps"
        const val KEY_BITRATE = "bitrate"
        const val KEY_CODEC = "codec"
        const val KEY_AUDIO = "audio"
        const val KEY_FPS_EXACT = "fps_exact"
        const val KEY_AUDIO_BITRATE = "audio_bitrate"
        const val KEY_SAVE_LOCATION = "save_location"
        const val KEY_PROGRESS = "progress"
        const val KEY_LABEL = "label"
        const val KEY_FILE = "file"
        const val KEY_URI = "uri"
        const val KEY_ERROR = "error"
        private const val CHANNEL = "editorshub_export"
        private const val NOTIFICATION_ID = 4101
    }

    override suspend fun doWork(): Result {
        val json = inputData.getString(KEY_PROJECT) ?: return Result.failure()
        val project = runCatching { ProjectSerializer.migrate(json) }
            .getOrElse { return Result.failure(workDataOf(KEY_ERROR to "Corrupt project JSON")) }
        val config = ExportConfig(
            width = inputData.getInt(KEY_WIDTH, 1080),
            height = inputData.getInt(KEY_HEIGHT, 1920),
            fps = inputData.getInt(KEY_FPS, 30),
            fpsExact = inputData.getDouble(KEY_FPS_EXACT, 30.0),
            bitrate = inputData.getInt(KEY_BITRATE, 8_000_000),
            audioBitrate = inputData.getInt(KEY_AUDIO_BITRATE, 128_000),
            codec = runCatching { VideoCodec.valueOf(inputData.getString(KEY_CODEC) ?: "H264") }
                .getOrDefault(VideoCodec.H264),
            includeAudio = inputData.getBoolean(KEY_AUDIO, true),
            saveLocation = inputData.getString(KEY_SAVE_LOCATION) ?: "gallery"
        )
        try { setForeground(foregroundInfo()) } catch (e: Exception) { /* notification unavailable */ }

        val result = exporter.export(
            project = project,
            config = config,
            onProgress = { percent, label ->
                setProgressAsync(workDataOf(KEY_PROGRESS to percent, KEY_LABEL to label))
            },
            cancelled = { isStopped }
        )
        return when (result) {
            is ExportResult.Success -> Result.success(
                workDataOf(
                    KEY_FILE to result.file.absolutePath,
                    KEY_URI to (result.uri?.toString() ?: ""),
                    KEY_PROGRESS to 100
                )
            )
            is ExportResult.Failure -> Result.failure(workDataOf(KEY_ERROR to result.message))
            ExportResult.Cancelled -> Result.failure(workDataOf(KEY_ERROR to "Export cancelled"))
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo()

    private fun foregroundInfo(): ForegroundInfo {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL, "Video export", NotificationManager.IMPORTANCE_LOW)
                )
            }
        }
        val notification = NotificationCompat.Builder(appContext, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_export)
            .setContentTitle(appContext.getString(R.string.app_name))
            .setContentText(appContext.getString(R.string.export_in_progress, 0))
            .setOngoing(true)
            .setProgress(100, 0, false)
            .build()
        return ForegroundInfo(NOTIFICATION_ID, notification)
    }
}
