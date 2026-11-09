package com.editorshub.core.rendering.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import com.editorshub.core.domain.keyframe.KeyframeEngine
import com.editorshub.core.domain.model.ExportConfig
import com.editorshub.core.domain.model.Project
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.min

private data class PcmData(val samples: FloatArray, val sampleRate: Int, val channels: Int)

/** Decodes audio with MediaCodec, mixes tracks with keyframed volume/fades and encodes AAC. */
@Singleton
class AudioMixer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val SAMPLE_RATE = 48000
        const val CHANNELS = 2
    }

    @Volatile var audioBitrate: Int = 128_000

    fun buildPcm(project: Project, config: ExportConfig): ShortArray {
        val totalUs = project.durationMs * 1000L
        val totalFrames = (totalUs * SAMPLE_RATE / 1_000_000L).toInt().coerceAtLeast(1)
        val mix = FloatArray(totalFrames * CHANNELS)
        for (track in project.audioTracks) {
            if (track.muted) continue
            val path = track.uri.removePrefix("file://")
            val data = runCatching { decode(path) }.getOrNull() ?: continue
            val startUs = track.startMs * 1000L
            val endUs = (track.startMs + track.durationMs) * 1000L
            val inPointUs = track.inPointMs * 1000L
            for (frame in 0 until totalFrames) {
                val timeUs = frame.toLong() * 1_000_000L / SAMPLE_RATE
                if (timeUs < startUs || timeUs >= endUs) continue
                val localMs = (timeUs - startUs) / 1000L
                var volume = track.volume * KeyframeEngine.trackValue(track.keyframes, "volume", localMs, 1f)
                if (track.fadeInMs > 0 && localMs < track.fadeInMs)
                    volume *= (localMs.toFloat() / track.fadeInMs)
                val remaining = track.durationMs - localMs
                if (track.fadeOutMs > 0 && remaining < track.fadeOutMs)
                    volume *= (remaining.toFloat() / track.fadeOutMs)
                val srcTimeUs = timeUs - startUs + inPointUs
                val srcFrame = (srcTimeUs * data.sampleRate / 1_000_000L).toInt()
                if (srcFrame < 0) continue
                for (ch in 0 until CHANNELS) {
                    val srcIndex = srcFrame * data.channels + (ch % data.channels)
                    if (srcIndex >= data.samples.size) continue
                    mix[frame * CHANNELS + ch] += data.samples[srcIndex] * volume
                }
            }
        }
        // Simple soft clip to avoid harsh digital distortion on loud mixes.
        val out = ShortArray(mix.size)
        for (i in mix.indices) {
            val v = mix[i].coerceIn(-1.2f, 1.2f)
            val soft = v / (1f + abs(v) * 0.25f)
            out[i] = (soft * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    private fun decode(path: String): PcmData {
        val extractor = MediaExtractor()
        extractor.setDataSource(path)
        var index = -1
        for (i in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("audio/")) { index = i; break }
        }
        if (index < 0) { extractor.release(); return PcmData(FloatArray(0), SAMPLE_RATE, 1) }
        extractor.selectTrack(index)
        val format = extractor.getTrackFormat(index)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: return PcmData(FloatArray(0), SAMPLE_RATE, 1)
        val codec = MediaCodec.createDecoderByType(mime)
        var sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE))
            format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else SAMPLE_RATE
        var channels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT))
            format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 1
        codec.configure(format, null, null, 0)
        codec.start()
        val chunks = ArrayList<FloatArray>()
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        var guard = 0
        var total = 0
        val limit = 24_000_000
        try {
            while (!outputDone && guard < 200_000 && total < limit) {
                guard++
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(10_000L)
                    if (inIdx >= 0) {
                        val buf = codec.getInputBuffer(inIdx)!!
                        val size = extractor.readSampleData(buf, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIdx, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIdx = codec.dequeueOutputBuffer(info, 10_000L)
                when {
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val f = codec.outputFormat
                        if (f.containsKey(MediaFormat.KEY_SAMPLE_RATE)) sampleRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        if (f.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                    outIdx >= 0 -> {
                        if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                            codec.getOutputBuffer(outIdx)?.let { buf ->
                                // MediaCodec may return a buffer with a non-zero payload offset.
                                val end = (info.offset + info.size).coerceAtMost(buf.capacity())
                                val start = info.offset.coerceIn(0, end)
                                buf.position(start)
                                buf.limit(end)
                                val sb = buf.slice().order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                                val n = min(sb.remaining(), limit - total)
                                val arr = FloatArray(n)
                                for (i in 0 until n) arr[i] = sb.get(i).toFloat() / Short.MAX_VALUE
                                chunks.add(arr)
                                total += n
                            }
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                }
                if (inputDone && guard > 50 && total == 0) outputDone = true
            }
        } finally {
            try { codec.stop(); codec.release() } catch (e: Exception) { /* ignore */ }
            extractor.release()
        }
        val samples = FloatArray(total)
        var offset = 0
        for (chunk in chunks) { chunk.copyInto(samples, offset); offset += chunk.size }
        return PcmData(samples, sampleRate, channels)
    }

    /** Encodes PCM to AAC-LC. [onFormat] must add the track and start the muxer. */
    fun encodeAac(
        pcm: ShortArray,
        onFormat: (MediaFormat) -> Int,
        onSample: (ByteBuffer, MediaCodec.BufferInfo) -> Unit
    ) {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, SAMPLE_RATE, CHANNELS)
        format.setInteger(MediaFormat.KEY_BIT_RATE, audioBitrate.coerceIn(32_000, 512_000))
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        val mime = MediaFormat.MIMETYPE_AUDIO_AAC
        val codec = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            .findEncoderForFormat(MediaFormat.createAudioFormat(mime, SAMPLE_RATE, CHANNELS))
            ?.let { MediaCodec.createByCodecName(it) } ?: MediaCodec.createEncoderByType(mime)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
        val info = MediaCodec.BufferInfo()
        var offset = 0
        var inputDone = false
        var outputDone = false
        var guard = 0
        try {
            while (!outputDone && guard < 200_000) {
                guard++
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(10_000L)
                    if (inIdx >= 0) {
                        val buf = codec.getInputBuffer(inIdx)!!
                        buf.clear()
                        val sb = buf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                        val chunk = min(2048, pcm.size - offset)
                        if (chunk > 0) sb.put(pcm, offset, chunk)
                        offset += chunk
                        val frames = offset / CHANNELS
                        val pts = frames * 1_000_000L / SAMPLE_RATE
                        val flags = if (offset >= pcm.size) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0
                        codec.queueInputBuffer(inIdx, 0, chunk * 2, pts, flags)
                        if (offset >= pcm.size) inputDone = true
                    }
                }
                val outIdx = codec.dequeueOutputBuffer(info, 10_000L)
                when {
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> onFormat(codec.outputFormat)
                    outIdx >= 0 -> {
                        codec.getOutputBuffer(outIdx)?.let { data ->
                            data.position(info.offset)
                            data.limit(info.offset + info.size)
                            if (info.size > 0) onSample(data, info)
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                }
                if (inputDone && guard > 100 && info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
            }
        } finally {
            try { codec.stop(); codec.release() } catch (e: Exception) { /* ignore */ }
        }
    }
}
