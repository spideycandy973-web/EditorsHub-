package com.motionstudio.editor

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.motionstudio.editor.core.media.MediaKind
import com.motionstudio.editor.core.media.MediaRepository
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

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
        FileOutputStream(file).use { it.write(data) }
        val repo = MediaRepository(ctx)
        val info = repo.probe(file.absolutePath)
        assertEquals(MediaKind.AUDIO, info.kind)
        val peaks = repo.waveform(file.absolutePath, 160)
        assertEquals(160, peaks.size)
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
