package com.example.render

import android.content.Context
import android.media.*
import android.net.Uri
import com.example.model.LayerData
import com.example.model.LayerType
import com.example.model.ProjectDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.*

data class AudioAnalysis(
    val sampleRate: Int,
    val durationSec: Float,
    val bucketsPerSec: Int,
    val bandsCount: Int,
    val spectrumBins: List<FloatArray>,
    val waveformPeaks: FloatArray
)

/**
 * Decodes real PCM + multi-band FFT frequency spectra from imported AUDIO / VIDEO layers
 * and manages synchronized real-time playback inside the editor.
 */
object AudioTrackEngine {

    private const val BUCKETS_PER_SEC = 20
    private const val BANDS = 32

    private val cache = ConcurrentHashMap<String, AudioAnalysis>()
    private val inFlight = ConcurrentHashMap<String, Boolean>()
    private val players = mutableMapOf<String, MediaPlayer>()

    fun getCachedAnalysis(uriStr: String?): AudioAnalysis? {
        if (uriStr.isNullOrBlank()) return null
        return cache[uriStr]
    }

    suspend fun ensureAnalyzed(context: Context, uriStr: String?): AudioAnalysis? {
        if (uriStr.isNullOrBlank()) return null
        cache[uriStr]?.let { return it }
        if (inFlight.putIfAbsent(uriStr, true) != null) return null
        return try {
            val result = withContext(Dispatchers.IO) { decodeSpectrumAndWaveform(context, uriStr) }
            if (result != null) cache[uriStr] = result
            result
        } finally {
            inFlight.remove(uriStr)
        }
    }

    /**
     * Returns normalized [0..1] bar heights for Audio Spectrum effect from active audio/video layers.
     */
    fun sampleSpectrumAt(doc: ProjectDocument, globalTime: Float, barCount: Int): FloatArray? {
        val activeAudioLayers = doc.layers.filter { layer ->
            !layer.hidden &&
                layer.volume > 0.01f &&
                (layer.type == LayerType.AUDIO || layer.type == LayerType.VIDEO) &&
                !layer.mediaUri.isNullOrBlank() &&
                globalTime >= layer.start &&
                globalTime <= layer.start + layer.duration
        }
        for (layer in activeAudioLayers) {
            val analysis = getCachedAnalysis(layer.mediaUri) ?: continue
            val localSec = ((globalTime - layer.start) * layer.playbackRate.coerceAtLeast(0.1f))
            val bucketIdx = (localSec * analysis.bucketsPerSec).roundToInt()
                .coerceIn(0, (analysis.spectrumBins.size - 1).coerceAtLeast(0))
            val bin = analysis.spectrumBins.getOrNull(bucketIdx) ?: continue
            val out = FloatArray(barCount)
            for (i in 0 until barCount) {
                val srcIdx = ((i.toFloat() / barCount.coerceAtLeast(1)) * bin.size).toInt().coerceIn(0, bin.lastIndex)
                out[i] = (bin[srcIdx] * layer.volume).coerceIn(0.02f, 1f)
            }
            return out
        }
        return null
    }

    fun getWaveformSlice(uriStr: String?, startSec: Float, durationSec: Float, outCount: Int): FloatArray? {
        val analysis = getCachedAnalysis(uriStr) ?: return null
        if (analysis.waveformPeaks.isEmpty()) return null
        val out = FloatArray(outCount)
        val totalPeaks = analysis.waveformPeaks.size
        for (i in 0 until outCount) {
            val t = startSec + (i.toFloat() / outCount.coerceAtLeast(1)) * durationSec
            val idx = ((t / analysis.durationSec.coerceAtLeast(0.1f)) * totalPeaks).roundToInt().coerceIn(0, totalPeaks - 1)
            out[i] = analysis.waveformPeaks[idx]
        }
        return out
    }

    fun syncPlayback(context: Context, doc: ProjectDocument, timeSec: Float, isPlaying: Boolean) {
        val activeLayers = if (!isPlaying) {
            emptyList()
        } else {
            doc.layers.filter { l ->
                !l.hidden &&
                    l.volume > 0.01f &&
                    (l.type == LayerType.AUDIO || l.type == LayerType.VIDEO) &&
                    !l.mediaUri.isNullOrBlank() &&
                    timeSec >= l.start &&
                    timeSec <= l.start + l.duration
            }
        }
        val activeIds = activeLayers.map { it.id }.toSet()

        // Pause or release inactive players
        val toRemove = players.keys.filter { it !in activeIds }
        for (id in toRemove) {
            try {
                players[id]?.stop()
                players[id]?.release()
            } catch (_: Exception) {
            }
            players.remove(id)
        }

        if (!isPlaying) return

        for (layer in activeLayers) {
            val uriStr = layer.mediaUri ?: continue
            val targetMs = ((timeSec - layer.start) * layer.playbackRate.coerceAtLeast(0.1f) * 1000f).roundToInt().coerceAtLeast(0)
            var player = players[layer.id]
            if (player == null) {
                try {
                    player = MediaPlayer().apply {
                        val parsedUri = Uri.parse(uriStr)
                        if (parsedUri.scheme == "file" && !parsedUri.path.isNullOrBlank()) {
                            setDataSource(parsedUri.path)
                        } else {
                            setDataSource(context, parsedUri)
                        }
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .build()
                        )
                        prepare()
                        setVolume(layer.volume.coerceIn(0f, 1f), layer.volume.coerceIn(0f, 1f))
                        if (targetMs < duration) seekTo(targetMs)
                        try {
                            playbackParams = playbackParams.setSpeed(layer.playbackRate.coerceIn(0.25f, 3f))
                        } catch (_: Exception) {
                        }
                        start()
                    }
                    players[layer.id] = player
                } catch (_: Exception) {
                    try {
                        player?.release()
                    } catch (_: Exception) {
                    }
                }
            } else {
                try {
                    player.setVolume(layer.volume.coerceIn(0f, 1f), layer.volume.coerceIn(0f, 1f))
                    val driftMs = abs(player.currentPosition - targetMs)
                    if (driftMs > 280 && targetMs < player.duration) {
                        player.seekTo(targetMs)
                    }
                    if (!player.isPlaying && targetMs < player.duration) {
                        player.start()
                    }
                } catch (_: Exception) {
                }
            }
        }
    }

    fun releaseAllPlayback() {
        players.values.forEach { p ->
            try {
                p.stop()
                p.release()
            } catch (_: Exception) {
            }
        }
        players.clear()
    }

    private fun decodeSpectrumAndWaveform(context: Context, uriStr: String): AudioAnalysis? {
        val monoPcm = decodeToMonoFloatPcm(context, Uri.parse(uriStr), targetSampleRate = 22050, maxDurationSec = 120f)
            ?: return null
        val samples = monoPcm.first
        val sampleRate = monoPcm.second
        if (samples.isEmpty()) return null

        val durationSec = samples.size.toFloat() / sampleRate.toFloat()
        val samplesPerBucket = (sampleRate / BUCKETS_PER_SEC).coerceAtLeast(256)
        val fftSize = 256
        val bins = mutableListOf<FloatArray>()
        val peaks = FloatArray(((samples.size + samplesPerBucket - 1) / samplesPerBucket).coerceAtLeast(1))

        val re = FloatArray(fftSize)
        val im = FloatArray(fftSize)
        var bucketIdx = 0
        var offset = 0
        while (offset < samples.size && bucketIdx < peaks.size) {
            var peak = 0f
            val end = min(samples.size, offset + samplesPerBucket)
            for (i in offset until end) {
                val a = abs(samples[i])
                if (a > peak) peak = a
            }
            peaks[bucketIdx] = peak.coerceIn(0f, 1f)

            for (i in 0 until fftSize) {
                val idx = offset + i
                val hann = (0.5f * (1f - cos(2f * PI.toFloat() * i / fftSize)))
                re[i] = if (idx < samples.size) samples[idx] * hann else 0f
                im[i] = 0f
            }
            fftInPlace(re, im)

            val bandVals = FloatArray(BANDS)
            val usableBins = fftSize / 2
            for (b in 0 until BANDS) {
                val binStart = ((b.toFloat() / BANDS).pow(1.4f) * usableBins).toInt().coerceIn(1, usableBins - 1)
                val binEnd = (((b + 1).toFloat() / BANDS).pow(1.4f) * usableBins).toInt().coerceIn(binStart + 1, usableBins)
                var energy = 0f
                for (k in binStart until binEnd) {
                    energy += hypot(re[k], im[k])
                }
                val avg = energy / (binEnd - binStart).coerceAtLeast(1)
                bandVals[b] = (ln(1f + avg * 4.5f) / 3.2f).coerceIn(0.03f, 1f)
            }
            bins.add(bandVals)
            bucketIdx++
            offset += samplesPerBucket
        }

        return AudioAnalysis(
            sampleRate = sampleRate,
            durationSec = durationSec,
            bucketsPerSec = BUCKETS_PER_SEC,
            bandsCount = BANDS,
            spectrumBins = bins,
            waveformPeaks = peaks
        )
    }

    /**
     * Decodes any Android-supported audio/video URI into normalized mono [-1..1] PCM samples
     * resampled cleanly to [targetSampleRate]. Also reused by VideoExporter for MP4 AAC audio mixing.
     */
    fun decodeToMonoFloatPcm(
        context: Context,
        uri: Uri,
        targetSampleRate: Int = 44100,
        maxDurationSec: Float = 180f
    ): Pair<FloatArray, Int>? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        return try {
            if (uri.scheme == "file" && !uri.path.isNullOrBlank()) {
                extractor.setDataSource(uri.path!!)
            } else {
                extractor.setDataSource(context, uri, null)
            }
            var audioTrackIndex = -1
            var trackFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    trackFormat = fmt
                    break
                }
            }
            if (audioTrackIndex < 0 || trackFormat == null) return null

            extractor.selectTrack(audioTrackIndex)
            val mime = trackFormat.getString(MediaFormat.KEY_MIME) ?: return null
            var srcRate = trackFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var srcChannels = trackFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(trackFormat, null, null, 0)
            codec.start()

            val info = MediaCodec.BufferInfo()
            val maxSrcSamples = (maxDurationSec * srcRate).toInt()
            val rawMono = ArrayList<Float>(min(maxSrcSamples, srcRate * 15))
            var sawInputEos = false
            var sawOutputEos = false

            while (!sawOutputEos && rawMono.size < maxSrcSamples) {
                if (!sawInputEos) {
                    val inIdx = codec.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        val buf = codec.getInputBuffer(inIdx)!!
                        val sampleSize = extractor.readSampleData(buf, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            sawInputEos = true
                        } else {
                            codec.queueInputBuffer(inIdx, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outIdx = codec.dequeueOutputBuffer(info, 10_000)
                if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val outFmt = codec.outputFormat
                    if (outFmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        srcRate = outFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    }
                    if (outFmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                        srcChannels = outFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
                    }
                } else if (outIdx >= 0) {
                    val outBuf = codec.getOutputBuffer(outIdx)
                    if (outBuf != null && info.size > 0) {
                        outBuf.position(info.offset)
                        outBuf.limit(info.offset + info.size)
                        val shortBuf = outBuf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                        val frameCount = shortBuf.remaining() / srcChannels
                        for (f in 0 until frameCount) {
                            var sum = 0f
                            for (ch in 0 until srcChannels) {
                                sum += shortBuf.get().toFloat() / 32768f
                            }
                            rawMono.add(sum / srcChannels)
                            if (rawMono.size >= maxSrcSamples) break
                        }
                    }
                    codec.releaseOutputBuffer(outIdx, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        sawOutputEos = true
                    }
                }
            }

            if (rawMono.isEmpty()) return null
            if (srcRate == targetSampleRate) {
                return rawMono.toFloatArray() to targetSampleRate
            }

            // Linear resample to targetSampleRate
            val outLen = ((rawMono.size.toDouble() / srcRate) * targetSampleRate).roundToInt().coerceAtLeast(1)
            val resampled = FloatArray(outLen)
            val ratio = (rawMono.size - 1).toDouble() / (outLen - 1).coerceAtLeast(1)
            for (i in 0 until outLen) {
                val srcPos = i * ratio
                val lo = srcPos.toInt().coerceIn(0, rawMono.lastIndex)
                val hi = (lo + 1).coerceIn(0, rawMono.lastIndex)
                val frac = (srcPos - lo).toFloat()
                resampled[i] = rawMono[lo] * (1f - frac) + rawMono[hi] * frac
            }
            resampled to targetSampleRate
        } catch (_: Exception) {
            null
        } finally {
            try {
                codec?.stop()
                codec?.release()
            } catch (_: Exception) {
            }
            try {
                extractor.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun fftInPlace(re: FloatArray, im: FloatArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        var len = 2
        while (len <= n) {
            val ang = (-2.0 * PI / len)
            val wlenRe = cos(ang).toFloat()
            val wlenIm = sin(ang).toFloat()
            var i = 0
            while (i < n) {
                var wRe = 1f
                var wIm = 0f
                for (k in 0 until len / 2) {
                    val uRe = re[i + k]
                    val uIm = im[i + k]
                    val idx2 = i + k + len / 2
                    val vRe = re[idx2] * wRe - im[idx2] * wIm
                    val vIm = re[idx2] * wIm + im[idx2] * wRe
                    re[i + k] = uRe + vRe
                    im[i + k] = uIm + vIm
                    re[idx2] = uRe - vRe
                    im[idx2] = uIm - vIm
                    val nextWRe = wRe * wlenRe - wIm * wlenIm
                    wIm = wRe * wlenIm + wIm * wlenRe
                    wRe = nextWRe
                }
                i += len
            }
            len = len shl 1
        }
    }
}
