package com.editorshub.core.rendering.export

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.editorshub.core.domain.model.ExportConfig
import com.editorshub.core.domain.model.Project
import com.editorshub.core.domain.model.VideoCodec
import com.editorshub.core.domain.timeline.TimelineMath
import com.editorshub.core.rendering.RenderGraph
import com.editorshub.core.rendering.audio.AudioMixer
import com.editorshub.core.rendering.canvas.CanvasCompositor
import com.editorshub.core.rendering.gl.EffectUniformBuilder
import com.editorshub.core.rendering.gl.GlFrameWriter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton

sealed class ExportResult {
    data class Success(val file: File, val uri: Uri?, val bytes: Long) : ExportResult()
    data class Failure(val message: String) : ExportResult()
    data object Cancelled : ExportResult()
}

private class MuxState(var track: Int = -1, var started: Boolean = false)

/**
 * Real export: renders each frame through the compositor + OpenGL ES shader chain into a
 * MediaCodec input Surface, muxes H.264/H.265 video with AAC audio into MP4, then saves to
 * MediaStore. Progress is per rendered frame and cancellation is checked every frame.
 */
@Singleton
class VideoExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val compositor: CanvasCompositor,
    private val audioMixer: AudioMixer
) {
    suspend fun export(
        project: Project,
        config: ExportConfig,
        onProgress: (percent: Int, label: String) -> Unit,
        cancelled: () -> Boolean
    ): ExportResult = withContext(Dispatchers.Default) {
        val width = (config.width.coerceIn(16, 4096) / 2) * 2
        val height = (config.height.coerceIn(16, 4096) / 2) * 2
        val fps = config.fps.coerceIn(1, 240)
        val fpsExact = config.fpsExact.coerceIn(1.0, 240.0)
        val outDir = File(context.cacheDir, "exported").apply { mkdirs() }
        val stamp = System.currentTimeMillis()
        val videoOnly = File(outDir, "motion_${stamp}_video.mp4")
        val finalFile = File(outDir, "motion_${stamp}.mp4")

        val mime = if (config.codec == VideoCodec.H265) MediaFormat.MIMETYPE_VIDEO_HEVC
            else MediaFormat.MIMETYPE_VIDEO_AVC
        val encoderName = findEncoder(mime, width, height)
            ?: findEncoder(MediaFormat.MIMETYPE_VIDEO_AVC, width, height)
        if (encoderName == null) {
            return@withContext ExportResult.Failure("No hardware ${config.codec} encoder on this device.")
        }
        val actualMime = if (encoderName.contains("hevc", true) || encoderName.contains("265", true))
            MediaFormat.MIMETYPE_VIDEO_HEVC else MediaFormat.MIMETYPE_VIDEO_AVC

        var encoder: MediaCodec? = null
        var writer: GlFrameWriter? = null
        var muxer: MediaMuxer? = null
        try {
            val format = MediaFormat.createVideoFormat(actualMime, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfoCompat.COLOR_FORMAT_SURFACE)
                setInteger(MediaFormat.KEY_BIT_RATE, config.bitrate.coerceIn(500_000, 80_000_000))
                setFloat(MediaFormat.KEY_FRAME_RATE, fpsExact.toFloat())
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }
            encoder = MediaCodec.createByCodecName(encoderName)
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val surface = encoder.createInputSurface()
            writer = GlFrameWriter(surface, width, height)
            muxer = MediaMuxer(videoOnly.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            encoder.start()
            val state = MuxState()
            val info = MediaCodec.BufferInfo()
            val total = TimelineMath.frameCount(project.durationMs, fpsExact)

            for (i in 0 until total) {
                if (cancelled()) {
                    cleanup(encoder, writer, muxer)
                    return@withContext ExportResult.Cancelled
                }
                val timeMs = TimelineMath.frameTimeExact(i, fpsExact)
                val frame = RenderGraph.resolve(project, timeMs)
                var full: Bitmap? = null
                var scaled: Bitmap? = null
                try {
                    full = compositor.renderToBitmap(frame, project.width, project.height)
                    scaled = if (full.width != width || full.height != height)
                        Bitmap.createScaledBitmap(full, width, height, true) else full
                    // CanvasCompositor has already applied each layer's effect stack.
                    // A global aggregate pass here would duplicate effects across the whole frame.
                    val uniforms = com.editorshub.core.rendering.gl.GlUniforms(time = timeMs / 1000f)
                    val presentationTimeNs = (i * 1_000_000_000.0 / fpsExact).toLong()
                    writer.draw(scaled, uniforms, presentationTimeNs)
                    drain(encoder, muxer, state, info, false)
                } finally {
                    if (scaled != null && scaled !== full && !scaled.isRecycled) scaled.recycle()
                    if (full != null && !full.isRecycled) full.recycle()
                }
                onProgress(((i + 1) * 100) / total, "Frame ${i + 1} / $total")
            }

            encoder.signalEndOfInputStream()
            drain(encoder, muxer, state, info, true)
            encoder.stop()
            encoder.release(); encoder = null
            writer.release(); writer = null
            muxer.stop()
            muxer.release(); muxer = null

            val withAudio = config.includeAudio && project.audioTracks.isNotEmpty()
            val output = if (withAudio) {
                val ok = muxAudio(videoOnly, finalFile, project, config)
                if (ok) finalFile else videoOnly
            } else videoOnly

            onProgress(100, "Saving")
            val uri = saveToMediaStore(output, "EditorsHub_$stamp.mp4", config.saveLocation)
            ExportResult.Success(output, uri, output.length())
        } catch (e: kotlinx.coroutines.CancellationException) {
            cleanup(encoder, writer, muxer)
            ExportResult.Cancelled
        } catch (e: Exception) {
            cleanup(encoder, writer, muxer)
            ExportResult.Failure(e.message ?: "Unknown render error")
        } catch (e: OutOfMemoryError) {
            cleanup(encoder, writer, muxer)
            ExportResult.Failure("Not enough memory to render this resolution. Try a lower export size.")
        }
    }

    private fun findEncoder(mime: String, w: Int, h: Int): String? = try {
        val format = MediaFormat.createVideoFormat(mime, w, h)
        MediaCodecList(MediaCodecList.REGULAR_CODECS).findEncoderForFormat(format)
    } catch (e: Exception) { null }

    private fun drain(
        encoder: MediaCodec,
        muxer: MediaMuxer,
        state: MuxState,
        info: MediaCodec.BufferInfo,
        endOfStream: Boolean
    ) {
        var guard = 0
        while (guard < 2000) {
            guard++
            val index = encoder.dequeueOutputBuffer(info, if (endOfStream) 10_000L else 0L)
            when {
                index == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream) return
                }
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    state.track = muxer.addTrack(encoder.outputFormat)
                    muxer.start()
                    state.started = true
                }
                index >= 0 -> {
                    val buffer: ByteBuffer? = encoder.getOutputBuffer(index)
                    if (buffer != null && info.size > 0 && state.started && state.track >= 0) {
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        muxer.writeSampleData(state.track, buffer, info)
                    }
                    encoder.releaseOutputBuffer(index, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
                else -> if (!endOfStream) return
            }
            if (endOfStream && guard > 5 && info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
        }
    }

    private fun muxAudio(videoOnly: File, output: File, project: Project, config: ExportConfig): Boolean {
        return try {
            val pcm = audioMixer.buildPcm(project, config)
            audioMixer.audioBitrate = config.audioBitrate
            val extractor = MediaExtractor()
            extractor.setDataSource(videoOnly.absolutePath)
            var videoSrc = -1
            for (i in 0 until extractor.trackCount) {
                val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/")) { videoSrc = i; break }
            }
            if (videoSrc < 0) { extractor.release(); return false }
            extractor.selectTrack(videoSrc)
            val muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val videoDst = muxer.addTrack(extractor.getTrackFormat(videoSrc))
            var audioDst = -1
            audioMixer.encodeAac(
                pcm = pcm,
                onFormat = { format ->
                    audioDst = muxer.addTrack(format)
                    muxer.start()
                    // Video samples are copied once the muxer has both tracks.
                    val info = MediaCodec.BufferInfo()
                    val buffer = ByteBuffer.allocate(2 * 1024 * 1024)
                    while (true) {
                        buffer.clear()
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) break
                        buffer.position(0)
                        buffer.limit(size)
                        info.set(0, size, extractor.sampleTime, extractor.sampleFlags)
                        muxer.writeSampleData(videoDst, buffer, info)
                        extractor.advance()
                    }
                    audioDst
                },
                onSample = { data, info ->
                    if (audioDst >= 0) muxer.writeSampleData(audioDst, data, info)
                }
            )
            muxer.stop()
            muxer.release()
            extractor.release()
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun saveToMediaStore(file: File, name: String, saveLocation: String): Uri? {
        if (saveLocation == "app") {
            val dir = File(context.filesDir, "Movies").apply { mkdirs() }
            val dest = File(dir, name)
            file.copyTo(dest, true)
            return androidx.core.content.FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", dest)
        }
        return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/EditorsHub")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values
            ) ?: return null
            context.contentResolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { it.copyTo(out) }
            }
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
            uri
        } else {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                "EditorsHub"
            ).apply { mkdirs() }
            val dest = File(dir, name)
            file.copyTo(dest, true)
            Uri.fromFile(dest)
        }
        } catch (e: Exception) { null }
    }

    private fun cleanup(encoder: MediaCodec?, writer: GlFrameWriter?, muxer: MediaMuxer?) {
        try { encoder?.stop() } catch (e: Exception) { /* ignore */ }
        try { encoder?.release() } catch (e: Exception) { /* ignore */ }
        try { writer?.release() } catch (e: Exception) { /* ignore */ }
        try { muxer?.stop() } catch (e: Exception) { /* ignore */ }
        try { muxer?.release() } catch (e: Exception) { /* ignore */ }
    }
}

private object MediaCodecInfoCompat {
    const val COLOR_FORMAT_SURFACE = android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
}
