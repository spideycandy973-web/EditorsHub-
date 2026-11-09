package com.editorshub

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.editorshub.core.domain.effects.EffectCatalog
import com.editorshub.core.domain.model.Effect
import com.editorshub.core.domain.model.EffectType
import com.editorshub.core.domain.model.GradientType
import com.editorshub.core.domain.model.Layer
import com.editorshub.core.domain.model.LayerType
import com.editorshub.core.domain.model.Project
import com.editorshub.core.domain.model.ShapeKind
import com.editorshub.core.domain.model.ShapeSpec
import com.editorshub.core.media.MediaRepository
import com.editorshub.core.rendering.ThumbnailGenerator
import com.editorshub.core.rendering.canvas.CanvasCompositor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ThumbnailGeneratorTest {
    private fun generator(): ThumbnailGenerator {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return ThumbnailGenerator(context, CanvasCompositor(context, MediaRepository(context)))
    }

    @Test fun generatesVisiblePngFromFirstActiveFrame() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val generator = generator()
        val project = Project(
            id = "thumbnail-regression",
            name = "Thumbnail regression",
            width = 1080,
            height = 1920,
            durationMs = 10_000L,
            background = "#FF000000",
            layers = listOf(
                Layer(
                    id = "late-red-square",
                    name = "Late red square",
                    type = LayerType.SHAPE,
                    startMs = 2_000L,
                    durationMs = 5_000L,
                    shape = ShapeSpec(
                        width = 400f,
                        height = 400f,
                        fillColor = "#FFFF0000"
                    )
                )
            )
        )
        // Simulate an interrupted earlier write: a zero-byte file must be replaced,
        // not shown as a blank thumbnail by Coil.
        val stale = File(context.filesDir, "thumbnails/${project.id}.png")
        stale.parentFile?.mkdirs()
        stale.writeBytes(ByteArray(0))

        val path = generator.generate(project, 1_000L)
        assertNotNull("Thumbnail generation should return a file path", path)
        val file = File(path!!)
        assertTrue("Thumbnail file should contain PNG data", file.length() > 64L)
        val decoded = BitmapFactory.decodeFile(path)
        assertNotNull("Thumbnail file should decode as a bitmap", decoded)
        decoded!!
        try {
            assertTrue(decoded.width > 0 && decoded.height > 0)
            val center = decoded.getPixel(decoded.width / 2, decoded.height / 2)
            assertTrue(
                "Expected the first active frame's red shape at the center, got $center",
                Color.red(center) > 220 && Color.green(center) < 40 && Color.blue(center) < 40
            )
        } finally {
            decoded.recycle()
            file.delete()
        }
    }

    @Test fun effectPreviewRendersVisibleFrame() {
        val generator = generator()
        val project = Project(
            id = "effect-preview",
            name = "Effect preview",
            width = 480,
            height = 480,
            durationMs = 2_000L,
            background = "#FF101228",
            // Layer 0 is topmost, so keep the subject above the solid background.
            layers = listOf(
                Layer(
                    id = "subject",
                    name = "Subject",
                    type = LayerType.SHAPE,
                    durationMs = 2_000L,
                    shape = ShapeSpec(
                        kind = ShapeKind.STAR,
                        width = 280f,
                        height = 280f,
                        gradient = GradientType.RADIAL,
                        fillColor = "#FFFFFFFF",
                        fillColor2 = "#FF22D3EE"
                    ),
                    effects = listOf(
                        Effect(
                            id = "blur",
                            type = EffectType.GAUSSIAN_BLUR,
                            params = EffectCatalog.defaults(EffectType.GAUSSIAN_BLUR)
                        )
                    )
                ),
                Layer(
                    id = "bg",
                    name = "Background",
                    type = LayerType.SOLID,
                    durationMs = 2_000L
                )
            )
        )
        val bitmap = generator.renderBitmap(project, 900L, maxDimension = 480)
        assertNotNull("Effect preview should render a bitmap", bitmap)
        bitmap!!
        try {
            assertEquals(480, bitmap.width)
            assertEquals(480, bitmap.height)
            val background = Color.parseColor("#FF101228")
            var visiblePixels = 0
            var y = 0
            while (y < bitmap.height) {
                var x = 0
                while (x < bitmap.width) {
                    val pixel = bitmap.getPixel(x, y)
                    if (pixel != background && Color.alpha(pixel) > 0 &&
                        Color.red(pixel) + Color.green(pixel) + Color.blue(pixel) > 90) {
                        visiblePixels++
                    }
                    x += 8
                }
                y += 8
            }
            assertTrue("Effect preview should contain visible shape pixels", visiblePixels > 8)
        } finally {
            bitmap.recycle()
        }
    }
}
