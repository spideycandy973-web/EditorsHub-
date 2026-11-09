package com.editorshub

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.editorshub.core.domain.model.Layer
import com.editorshub.core.domain.model.LayerType
import com.editorshub.core.domain.model.Project
import com.editorshub.core.domain.model.ShapeSpec
import com.editorshub.core.rendering.RenderGraph
import com.editorshub.core.rendering.canvas.CanvasCompositor
import com.editorshub.core.media.MediaRepository
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CanvasViewportTest {
    @Test fun projectCoordinatesScaleIntoPreviewViewport() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val compositor = CanvasCompositor(context, MediaRepository(context))
        val project = Project(
            id = "viewport",
            name = "Viewport",
            width = 1080,
            height = 1920,
            durationMs = 1000L,
            background = "#FF000000",
            layers = listOf(
                Layer(
                    id = "red",
                    name = "Red square",
                    type = LayerType.SHAPE,
                    durationMs = 1000L,
                    shape = ShapeSpec(width = 200f, height = 200f, fillColor = "#FFFF0000")
                )
            )
        )
        val bitmap = compositor.renderToBitmap(RenderGraph.resolve(project, 0L), 270, 480)
        try {
            val center = bitmap.getPixel(135, 240)
            val red = android.graphics.Color.red(center)
            val green = android.graphics.Color.green(center)
            val blue = android.graphics.Color.blue(center)
            assertTrue("Expected the centered red shape to render in the scaled viewport; got $center",
                red > 220 && green < 40 && blue < 40)
        } finally {
            bitmap.recycle()
        }
    }
}
