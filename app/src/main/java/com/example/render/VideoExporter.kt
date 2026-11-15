package com.example.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import com.example.model.LayerType
import com.example.model.ProjectDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext
import kotlin.math.min
import kotlin.math.roundToInt

object VideoExporter {

    private const val AUDIO_SAMPLE_RATE = 44100
    private const val AUDIO_CHANNELS = 2
    private const val AUDIO_BITRATE = 128_000

    suspend fun exportPng(
        context: Context,
        doc: ProjectDocument,
        timeSec: Float,
        outputFile: File
    ): File = withContext(Dispatchers.IO) {
        outputFile.parentFile?.mkdirs()
        val w = doc.project.width.coerceIn(64, 3840)
        val h = doc.project.height.coerceIn(64, 3840)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        CompositionCanvasRenderer.render(
            context = context,
            canvas = canvas,
            doc = doc,
            timeSec = timeSec,
            selectedLayerId = null,
            showGuides = false,
            cameraViewActive = true
        )
        FileOutputStream(outputFile).use { fos ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, fos)
        }
        bmp.recycle()
        outputFile
    }

    suspend fun exportMp4(
        context: Context,
        doc: ProjectDocument,
        outWidth: Int,
        outHeight: Int,
        fps: Int,
        bitrateMbps: Int,
        outputFile: File,
        onProgress: (Float) -> Unit
    ): File = withContext(Dispatchers.Default) {
        outputFile.parentFile?.mkdirs()
        if (outputFile.exists()) outputFile.delete()

        val evenW = ((outWidth.coerceIn(128, 1920) + 15) / 16) * 16
        val evenH = ((outHeight.coerceIn(128, 1920) + 15) / 16) * 16
        val safeFps = fps.coerceIn(12, 60)
        val durationSec = doc.project.duration.coerceAtLeast(0.5f)
        val totalFrames = (durationSec * safeFps).roundToInt().coerceAtLeast(1)

        // 1. Mix active audio tracks into interleaved stereo 16-bit PCM if any audio/video audio exists
        val mixedPcm = buildMixedAudioTrack(context, doc, durationSec)
        val hasAudio = mixedPcm != null && mixedPcm.isNotEmpty()

        val videoFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, evenW, evenH).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, (bitrateMbps.coerceIn(2, 20) * 1_000_000))
            setInteger(MediaFormat.KEY_FRAME_RATE, safeFps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        val videoCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        videoCodec.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val inputSurface = videoCodec.createInputSurface()
        videoCodec.start()

        var audioCodec: MediaCodec? = null
        if (hasAudio) {
            try {
                val audioFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, AUDIO_SAMPLE_RATE, AUDIO_CHANNELS).apply {
                    setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                    setInteger(MediaFormat.KEY_BIT_RATE, AUDIO_BITRATE)
                    setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
                }
                audioCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
                    configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                    start()
                }
            } catch (_: Exception) {
                audioCodec = null
            }
        }

        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var videoTrackIdx = -1
        var audioTrackIdx = -1
        var videoFormatReady = false
        var audioFormatReady = (audioCodec == null)
        var muxerStarted = false

        val vInfo = MediaCodec.BufferInfo()
        val aInfo = MediaCodec.BufferInfo()

        // Buffer pending encoded samples if audio & video format readiness slightly staggered
        val pendingVideoSamples = mutableListOf<Pair<ByteArray, MediaCodec.BufferInfo>>()
        val pendingAudioSamples = mutableListOf<Pair<ByteArray, MediaCodec.BufferInfo>>()

        fun tryStartMuxer() {
            if (!muxerStarted && videoFormatReady && audioFormatReady) {
                muxer.start()
                muxerStarted = true
                for ((bytes, bi) in pendingVideoSamples) {
                    muxer.writeSampleData(videoTrackIdx, ByteBuffer.wrap(bytes), bi)
                }
                pendingVideoSamples.clear()
                for ((bytes, bi) in pendingAudioSamples) {
                    muxer.writeSampleData(audioTrackIdx, ByteBuffer.wrap(bytes), bi)
                }
                pendingAudioSamples.clear()
            }
        }

        fun copyBufferInfo(src: MediaCodec.BufferInfo): MediaCodec.BufferInfo =
            MediaCodec.BufferInfo().apply { set(0, src.size, src.presentationTimeUs, src.flags) }

        fun drainVideo(endOfStream: Boolean) {
            if (endOfStream) videoCodec.signalEndOfInputStream()
            while (true) {
                val outIdx = videoCodec.dequeueOutputBuffer(vInfo, 10_000)
                if (outIdx == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    if (!endOfStream) break
                } else if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if (!videoFormatReady) {
                        videoTrackIdx = muxer.addTrack(videoCodec.outputFormat)
                        videoFormatReady = true
                        tryStartMuxer()
                    }
                } else if (outIdx >= 0) {
                    val encodedData = videoCodec.getOutputBuffer(outIdx)
                    if (encodedData != null && vInfo.size > 0 && (vInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                        encodedData.position(vInfo.offset)
                        encodedData.limit(vInfo.offset + vInfo.size)
                        if (muxerStarted) {
                            muxer.writeSampleData(videoTrackIdx, encodedData, vInfo)
                        } else {
                            val copy = ByteArray(vInfo.size)
                            encodedData.get(copy)
                            pendingVideoSamples.add(copy to copyBufferInfo(vInfo))
                        }
                    }
                    videoCodec.releaseOutputBuffer(outIdx, false)
                    if ((vInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
                }
            }
        }

        fun drainAudio(endOfStream: Boolean) {
            val ac = audioCodec ?: return
            while (true) {
                val outIdx = ac.dequeueOutputBuffer(aInfo, 5_000)
                if (outIdx == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    if (!endOfStream) break
                } else if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if (!audioFormatReady) {
                        audioTrackIdx = muxer.addTrack(ac.outputFormat)
                        audioFormatReady = true
                        tryStartMuxer()
                    }
                } else if (outIdx >= 0) {
                    val encodedData = ac.getOutputBuffer(outIdx)
                    if (encodedData != null && aInfo.size > 0 && (aInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                        encodedData.position(aInfo.offset)
                        encodedData.limit(aInfo.offset + aInfo.size)
                        if (muxerStarted) {
                            muxer.writeSampleData(audioTrackIdx, encodedData, aInfo)
                        } else {
                            val copy = ByteArray(aInfo.size)
                            encodedData.get(copy)
                            pendingAudioSamples.add(copy to copyBufferInfo(aInfo))
                        }
                    }
                    ac.releaseOutputBuffer(outIdx, false)
                    if ((aInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
                }
            }
        }

        val compW = doc.project.width.toFloat().coerceAtLeast(1f)
        val compH = doc.project.height.toFloat().coerceAtLeast(1f)
        val scaleX = evenW / compW
        val scaleY = evenH / compH

        try {
            // Pre-analyze audio so real FFT audio spectrum effects render accurately offline
            for (layer in doc.layers) {
                if ((layer.type == LayerType.AUDIO || layer.type == LayerType.VIDEO) && !layer.mediaUri.isNullOrBlank()) {
                    AudioTrackEngine.ensureAnalyzed(context, layer.mediaUri)
                }
            }

            // Encode audio first/concurrently into AAC buffers
            if (audioCodec != null && mixedPcm != null) {
                val ac = audioCodec
                var sampleCursor = 0
                val chunkShorts = 2048 // 1024 stereo frames per AAC packet
                var ptsSamples = 0L
                while (sampleCursor < mixedPcm.size) {
                    coroutineContext.ensureActive()
                    val inIdx = ac.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        val inBuf = ac.getInputBuffer(inIdx)!!
                        inBuf.clear()
                        val count = min(chunkShorts, mixedPcm.size - sampleCursor)
                        val shortBuf = inBuf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                        for (i in 0 until count) {
                            shortBuf.put(mixedPcm[sampleCursor + i])
                        }
                        val ptsUs = (ptsSamples * 1_000_000L) / AUDIO_SAMPLE_RATE
                        ac.queueInputBuffer(inIdx, 0, count * 2, ptsUs, 0)
                        sampleCursor += count
                        ptsSamples += (count / AUDIO_CHANNELS)
                    }
                    drainAudio(false)
                }
                val eosIdx = ac.dequeueInputBuffer(10_000)
                if (eosIdx >= 0) {
                    val ptsUs = (ptsSamples * 1_000_000L) / AUDIO_SAMPLE_RATE
                    ac.queueInputBuffer(eosIdx, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                }
                drainAudio(true)
            }

            val frameBmp = Bitmap.createBitmap(evenW, evenH, Bitmap.Config.ARGB_8888)
            val bmpCanvas = Canvas(frameBmp)

            for (frame in 0 until totalFrames) {
                coroutineContext.ensureActive()
                val t = frame.toFloat() / safeFps.toFloat()

                bmpCanvas.save()
                bmpCanvas.scale(scaleX, scaleY)
                CompositionCanvasRenderer.render(
                    context = context,
                    canvas = bmpCanvas,
                    doc = doc,
                    timeSec = t,
                    selectedLayerId = null,
                    showGuides = false,
                    cameraViewActive = true
                )
                bmpCanvas.restore()

                val surfCanvas = inputSurface.lockCanvas(null)
                try {
                    surfCanvas.drawBitmap(frameBmp, 0f, 0f, null)
                } finally {
                    inputSurface.unlockCanvasAndPost(surfCanvas)
                }

                drainVideo(false)
                onProgress((frame + 1).toFloat() / totalFrames.toFloat())
            }
            frameBmp.recycle()
            drainVideo(true)
        } finally {
            try {
                videoCodec.stop()
                videoCodec.release()
            } catch (_: Exception) {}
            try {
                audioCodec?.stop()
                audioCodec?.release()
            } catch (_: Exception) {}
            try {
                inputSurface.release()
            } catch (_: Exception) {}
            try {
                if (muxerStarted) muxer.stop()
                muxer.release()
            } catch (_: Exception) {}
        }
        outputFile
    }

    private fun buildMixedAudioTrack(context: Context, doc: ProjectDocument, durationSec: Float): ShortArray? {
        val audioLayers = doc.layers.filter {
            !it.hidden &&
                it.volume > 0.01f &&
                (it.type == LayerType.AUDIO || it.type == LayerType.VIDEO) &&
                !it.mediaUri.isNullOrBlank()
        }
        if (audioLayers.isEmpty()) return null

        val totalMonoFrames = (durationSec * AUDIO_SAMPLE_RATE).roundToInt().coerceAtLeast(1)
        val mixFloat = FloatArray(totalMonoFrames)
        var anyDecoded = false

        for (layer in audioLayers) {
            val uri = try { Uri.parse(layer.mediaUri) } catch (_: Exception) { continue }
            val decoded = AudioTrackEngine.decodeToMonoFloatPcm(context, uri, AUDIO_SAMPLE_RATE, maxDurationSec = durationSec + 5f)
                ?: continue
            anyDecoded = true
            val src = decoded.first
            val startFrame = (layer.start * AUDIO_SAMPLE_RATE).roundToInt().coerceIn(0, totalMonoFrames - 1)
            val clipFrames = (layer.duration * AUDIO_SAMPLE_RATE).roundToInt()
            val endFrame = min(totalMonoFrames, startFrame + clipFrames)
            val rate = layer.playbackRate.coerceIn(0.25f, 3f)
            val vol = layer.volume.coerceIn(0f, 2f)

            for (f in startFrame until endFrame) {
                val srcIdx = ((f - startFrame) * rate).roundToInt()
                if (srcIdx in src.indices) {
                    mixFloat[f] += src[srcIdx] * vol
                }
            }
        }
        if (!anyDecoded) return null

        val stereoOut = ShortArray(totalMonoFrames * AUDIO_CHANNELS)
        for (i in 0 until totalMonoFrames) {
            val s = (mixFloat[i].coerceIn(-1f, 1f) * 32767f).roundToInt().toShort()
            stereoOut[i * 2] = s
            stereoOut[i * 2 + 1] = s
        }
        return stereoOut
    }
}
