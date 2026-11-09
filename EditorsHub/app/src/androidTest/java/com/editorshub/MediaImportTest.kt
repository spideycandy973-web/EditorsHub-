package com.editorshub

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.editorshub.core.media.MediaKind
import com.editorshub.core.media.MediaRepository
import com.editorshub.core.rendering.audio.AudioMixer
import com.editorshub.core.domain.model.AudioTrack
import com.editorshub.core.domain.model.ExportConfig
import com.editorshub.core.domain.model.Project
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.sin

@RunWith(AndroidJUnit4::class)
class MediaImportTest {

    @Test fun imageProbeAndDecode() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val file = File(ctx.cacheDir, "probe.png")
        val bmp = Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888)
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val repo = MediaRepository(ctx)
        val info = repo.probe(file.absolutePath)
        assertEquals(MediaKind.IMAGE, info.kind)
        assertEquals(64, info.width)
        assertEquals(48, info.height)
        assertNotNull(repo.image(file.absolutePath, 128))
    }

    @Test fun audioProbeAndWaveformDoesNotCrash() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val file = File(ctx.cacheDir, "tone.wav")
        // Minimal 44-byte WAV header + 1 second of silence (8 kHz mono 16-bit).
        val data = ByteArray(44 + 16000)
        writeWavHeader(data, 16000)
        // 440Hz tone gives the waveform and mixer tests a known non-silent signal.
        for (i in 0 until 8000) {
            val sample = (sin(2.0 * PI * 440.0 * i / 8000.0) * 12000.0).toInt()
            val offset = 44 + i * 2
            data[offset] = (sample and 0xFF).toByte()
            data[offset + 1] = ((sample shr 8) and 0xFF).toByte()
        }
        FileOutputStream(file).use { it.write(data) }
        val repo = MediaRepository(ctx)
        val info = repo.probe(file.absolutePath)
        assertEquals(MediaKind.AUDIO, info.kind)
        val peaks = repo.waveform(file.absolutePath, 160)
        assertEquals(160, peaks.size)
        assertTrue("Waveform should contain decoded audio peaks", peaks.maxOrNull()!! > 0.9f)

        val project = Project(
            id = "audio-mix",
            name = "Audio mix",
            durationMs = 1000L,
            audioTracks = listOf(AudioTrack(
                id = "tone", uri = "file://${file.absolutePath}",
                startMs = 0L, durationMs = 1000L
            ))
        )
        val mixed = AudioMixer(ctx).buildPcm(project, ExportConfig(includeAudio = true))
        assertTrue("Audio mixer should produce non-silent PCM", mixed.any { kotlin.math.abs(it.toInt()) > 1000 })
    }

    private fun writeWavHeader(b: ByteArray, dataSize: Int) {
        fun putInt(offset: Int, value: Int) {
            b[offset] = (value and 0xFF).toByte()
            b[offset + 1] = ((value shr 8) and 0xFF).toByte()
            b[offset + 2] = ((value shr 16) and 0xFF).toByte()
            b[offset + 3] = ((value shr 24) and 0xFF).toByte()
        }
        fun putShort(offset: Int, value: Int) {
            b[offset] = (value and 0xFF).toByte()
            b[offset + 1] = ((value shr 8) and 0xFF).toByte()
        }
        "RIFF".forEachIndexed { i, c -> b[i] = c.code.toByte() }
        putInt(4, 36 + dataSize)
        "WAVE".forEachIndexed { i, c -> b[8 + i] = c.code.toByte() }
        "fmt ".forEachIndexed { i, c -> b[12 + i] = c.code.toByte() }
        putInt(16, 16); putShort(20, 1); putShort(22, 1)
        putInt(24, 8000); putInt(28, 16000); putShort(32, 2); putShort(34, 16)
        "data".forEachIndexed { i, c -> b[36 + i] = c.code.toByte() }
        putInt(40, dataSize)
    }
}
