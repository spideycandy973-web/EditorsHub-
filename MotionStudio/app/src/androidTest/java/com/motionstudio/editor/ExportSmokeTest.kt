package com.motionstudio.editor

import android.content.Context
import android.media.MediaMetadataRetriever
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.motionstudio.editor.core.domain.model.ExportConfig
import com.motionstudio.editor.core.domain.model.Layer
import com.motionstudio.editor.core.domain.model.LayerType
import com.motionstudio.editor.core.domain.model.Project
import com.motionstudio.editor.core.media.MediaRepository
import com.motionstudio.editor.core.rendering.audio.AudioMixer
import com.motionstudio.editor.core.rendering.canvas.CanvasCompositor
import com.motionstudio.editor.core.rendering.export.ExportResult
import com.motionstudio.editor.core.rendering.export.VideoExporter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExportSmokeTest {

    @Test fun exportsRealMp4FromMultilayerComposition() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val media = MediaRepository(ctx)
        val compositor = CanvasCompositor(ctx, media)
        val exporter = VideoExporter(ctx, compositor, AudioMixer(ctx))
        val project = Project(
            id = "smoke", name = "Smoke", width = 320, height = 240,
            fps = 10, durationMs = 700L, background = "#FF101030",
            layers = listOf(
                Layer(id = "bg", name = "Background", type = LayerType.SOLID,
                    startMs = 0, durationMs = 700L),
                Layer(id = "txt", name = "Title", type = LayerType.TEXT,
                    startMs = 0, durationMs = 700L,
                    text = com.motionstudio.editor.core.domain.model.TextSpec(
                        text = "MOTION", fontSize = 48f))
            )
        )
        val result = runBlocking {
            exporter.export(project, ExportConfig(
                width = 320, height = 240, fps = 10, bitrate = 1_000_000,
                includeAudio = false
            ), { _, _ -> }, { false })
        }
        assertTrue("Export failed: $result", result is ExportResult.Success)
        val success = result as ExportResult.Success
        assertTrue(success.file.length() > 1000)
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(success.file.absolutePath)
        val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        retriever.release()
        assertNotNull(duration)
        assertTrue("Duration was $duration", duration!! > 0)
    }
}
