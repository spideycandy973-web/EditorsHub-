package com.editorshub.core.media

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

enum class MediaKind { VIDEO, IMAGE, AUDIO, UNKNOWN }

data class MediaInfo(
    val path: String,
    val name: String,
    val kind: MediaKind,
    val mime: String,
    val width: Int = 0,
    val height: Int = 0,
    val durationMs: Long = 0L,
    val rotation: Int = 0
)

data class ImportedMedia(val info: MediaInfo, val uri: String)

@Singleton
class MediaRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val mediaDir = File(context.filesDir, "media").apply { mkdirs() }

    private fun newCache(maxKb: Int) = object : LruCache<String, Bitmap>(maxKb) {
        override fun sizeOf(key: String, value: Bitmap): Int =
            maxOf(1, value.byteCount / 1024)
    }

    private val frameCache = newCache(24 * 1024)
    private val bitmapCache = newCache(32 * 1024)

    /** Copies a SAF document into private storage so project references survive. */
    suspend fun import(uri: Uri, displayName: String? = null): ImportedMedia = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (e: Exception) { /* document already persisted, or no persistable permission */ }

        val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
        val name = displayName ?: "media_${System.currentTimeMillis()}"
        val extension = extensionFor(mime, name)
        val id = UUID.randomUUID().toString()
        val target = File(mediaDir, "$id.$extension")
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: throw IllegalArgumentException("Cannot open $uri")

        val info = probe(target.absolutePath).copy(name = name)
        ImportedMedia(info, uri.toString())
    }

    private fun extensionFor(mime: String, name: String): String {
        val fromName = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (fromName.length in 2..5) return fromName
        return when {
            mime.startsWith("video/mp4") -> "mp4"
            mime.startsWith("video/webm") -> "webm"
            mime.startsWith("image/png") -> "png"
            mime.startsWith("image/webp") -> "webp"
            mime.startsWith("image/gif") -> "gif"
            mime.startsWith("image/") -> "jpg"
            mime.startsWith("audio/") -> "m4a"
            else -> "bin"
        }
    }

    fun probe(path: String): MediaInfo {
        val mime = guessMime(path)
        val kind = when {
            mime.startsWith("video/") -> MediaKind.VIDEO
            mime.startsWith("image/") -> MediaKind.IMAGE
            mime.startsWith("audio/") -> MediaKind.AUDIO
            else -> MediaKind.UNKNOWN
        }
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val d = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val rot = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (kind == MediaKind.IMAGE) {
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, opts)
                MediaInfo(path, File(path).name, MediaKind.IMAGE, mime, opts.outWidth, opts.outHeight)
            } else {
                MediaInfo(path, File(path).name, kind, mime, w, h, d, rot)
            }
        } catch (e: Exception) {
            MediaInfo(path, File(path).name, kind, mime)
        } finally {
            try { retriever.release() } catch (e: Exception) { /* ignore */ }
        }
    }

    private fun guessMime(path: String): String = when (path.substringAfterLast('.').lowercase(Locale.ROOT)) {
        "mp4", "m4v", "mov" -> "video/mp4"
        "webm" -> "video/webm"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "mp3" -> "audio/mpeg"
        "wav" -> "audio/wav"
        "aac", "m4a" -> "audio/mp4a-latm"
        "ogg" -> "audio/ogg"
        else -> "application/octet-stream"
    }

    /** Decode an image with a bounded sample size (memory-efficient). */
    fun image(path: String, maxSize: Int = 2048): Bitmap? {
        val key = "$path@$maxSize"
        bitmapCache.get(key)?.let { return it }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        val largest = max(bounds.outWidth, bounds.outHeight)
        while (largest / sample > maxSize) sample *= 2
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bmp = BitmapFactory.decodeFile(path, opts) ?: return null
        bitmapCache.put(key, bmp)
        return bmp
    }

    /** Real decoded video frame. Cached by 40ms bucket to avoid repeated seeks. */
    fun videoFrame(path: String, timeMs: Long, maxSize: Int = 1280): Bitmap? {
        val bucket = timeMs / 40L
        val key = "$path@$bucket@$maxSize"
        frameCache.get(key)?.let { return it }
        val retriever = MediaMetadataRetriever()
        val bmp = try {
            retriever.setDataSource(path)
            val raw = retriever.getFrameAtTime(timeMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST)
            if (raw != null) {
                val largest = max(raw.width, raw.height)
                if (largest > maxSize) {
                    val scale = maxSize.toFloat() / largest
                    Bitmap.createScaledBitmap(
                        raw, max(1, (raw.width * scale).toInt()), max(1, (raw.height * scale).toInt()), true
                    )
                } else raw
            } else null
        } catch (e: Exception) { null }
        finally { try { retriever.release() } catch (e: Exception) { /* ignore */ } }
        if (bmp != null) frameCache.put(key, bmp)
        return bmp
    }

    /** Genuine PCM waveform peaks decoded with MediaExtractor + MediaCodec. */
    fun waveform(path: String, buckets: Int = 160): List<Float> = try {
        WaveformExtractor.extract(path, buckets)
    } catch (e: Exception) {
        List(buckets) { 0f }
    }

    fun clearCaches() {
        frameCache.evictAll()
        bitmapCache.evictAll()
    }
}

object WaveformExtractor {
    fun extract(path: String, buckets: Int): List<Float> {
        val extractor = MediaExtractor()
        extractor.setDataSource(path)
        var audioIndex = -1
        for (i in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("audio/")) { audioIndex = i; break }
        }
        if (audioIndex < 0) { extractor.release(); return List(buckets) { 0f } }
        extractor.selectTrack(audioIndex)
        val format = extractor.getTrackFormat(audioIndex)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: return List(buckets) { 0f }
        val totalUs = if (format.containsKey(MediaFormat.KEY_DURATION))
            format.getLong(MediaFormat.KEY_DURATION) else 1L
        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()
        val peaks = FloatArray(buckets)
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        var guard = 0
        try {
            while (!outputDone && guard < 200_000) {
                guard++
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(10_000L)
                    if (inIndex >= 0) {
                        val buffer: ByteBuffer = codec.getInputBuffer(inIndex)!!
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIndex = codec.dequeueOutputBuffer(info, 10_000L)
                if (outIndex >= 0) {
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 && info.size > 0) {
                        codec.getOutputBuffer(outIndex)?.let { buf ->
                            val shorts = buf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                            var peak = 0f
                            for (i in 0 until shorts.remaining()) {
                                val v = abs(shorts.get(i).toFloat() / Short.MAX_VALUE)
                                if (v > peak) peak = v
                            }
                            val bucket = ((info.presentationTimeUs.toFloat() / totalUs) * buckets)
                                .toInt().coerceIn(0, buckets - 1)
                            if (peak > peaks[bucket]) peaks[bucket] = peak
                        }
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    // PCM layout changed; continue reading buffers.
                }
                if (inputDone && outIndex == MediaCodec.INFO_TRY_AGAIN_LATER && info.flags and
                    MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
            }
        } finally {
            try { codec.stop(); codec.release() } catch (e: Exception) { /* ignore */ }
            extractor.release()
        }
        val maxPeak = peaks.maxOrNull()?.takeIf { it > 0f } ?: 1f
        return peaks.map { min(1f, it / maxPeak) }
    }
}
