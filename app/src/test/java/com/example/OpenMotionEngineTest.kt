package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.ProjectRepository
import com.example.data.XmlInterchange
import com.example.engine.AnimationEngine
import com.example.engine.EasingPresets
import com.example.engine.ProjectTemplates
import com.example.model.*
import com.example.render.CompositionCanvasRenderer
import com.example.render.ShapePathBuilder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OpenMotionEngineTest {

    @Test
    fun all41ShapesBuildValidPathsAndBezierMidpointSubdivides() {
        for (kind in ShapeKind.entries) {
            val layer = if (kind == ShapeKind.VECTOR_PATH) {
                LayerData(
                    type = LayerType.SHAPE,
                    name = kind.name,
                    shapeKind = kind,
                    vectorPath = VectorPathData(
                        closed = true,
                        nodes = listOf(
                            VectorNode(-100f, -80f, outX = 40f, outY = 0f),
                            VectorNode(100f, -80f, inX = -40f, inY = 0f, outX = 0f, outY = 40f),
                            VectorNode(0f, 100f)
                        )
                    )
                )
            } else {
                LayerData(type = LayerType.SHAPE, name = kind.name, shapeKind = kind)
            }
            val path = ShapePathBuilder.buildPath(layer, 200f, 200f)
            assertFalse("Shape $kind produced an empty path", path.isEmpty)
        }

        val nodes = listOf(
            VectorNode(0f, 0f, outX = 20f, outY = 0f),
            VectorNode(100f, 0f, inX = -20f, inY = 0f)
        )
        val subdivided = ShapePathBuilder.insertMidpointNode(nodes, segmentIdx = 0, closed = false)
        assertEquals(3, subdivided.size)
        assertEquals(50f, subdivided[1].x, 1.5f)
        assertEquals(0f, subdivided[1].y, 1.5f)
    }

    @Test
    fun animationEngineEvaluatesKeyframesParentingAndCameraOrbit() {
        val parent = LayerData(
            id = "parent_1",
            type = LayerType.NULL,
            name = "Rig",
            x = 200f,
            y = 300f,
            w = 0f,
            h = 0f,
            keyframes = mapOf(
                "x" to listOf(
                    Keyframe(time = 0f, value = 200f, easing = EasingPresets.all.first().second),
                    Keyframe(time = 2f, value = 600f, easing = EasingPresets.all.first().second)
                )
            )
        )
        val child = LayerData(
            id = "child_1",
            type = LayerType.SHAPE,
            name = "Child",
            parentId = "parent_1",
            x = 50f,
            y = 25f
        )
        val cam = LayerData(
            id = "cam_1",
            type = LayerType.CAMERA,
            name = "Camera",
            orbitEnabled = true,
            orbitTargetId = "parent_1",
            orbitAzimuth = 90f,
            orbitElevation = 0f,
            orbitDistance = 1000f,
            lensMode = CameraLensMode.PRIME,
            dofEnabled = true,
            focusDistance = 950f,
            aperture = 2.8f
        )

        val worldChild = AnimationEngine.resolveWorldTransform(child, listOf(parent, child, cam), globalTime = 1f)
        // Parent x at t=1s is linearly interpolated to 400f -> child x = 400 + 50 = 450f
        assertEquals(450f, worldChild.x, 1f)
        assertEquals(325f, worldChild.y, 1f)

        val evalCam = AnimationEngine.evaluateActiveCamera(listOf(parent, child, cam), compWidth = 1080f, compHeight = 1920f, globalTime = 1f)
        assertNotNull(evalCam)
        assertTrue(evalCam!!.dofEnabled)
        assertEquals(1.08f, evalCam.focalMultiplier, 0.01f)
    }

    @Test
    fun xmlInterchangeRoundTripsComplexDocumentAndLayerStyles() {
        val vectorLayer = LayerData(
            id = "vec_1",
            type = LayerType.SHAPE,
            name = "Bintang Neon",
            shapeKind = ShapeKind.STAR,
            shapePoints = 7,
            shapeInner = 52f,
            strokeHex = "#00D7A7",
            strokeWidth = 6f,
            repeat = RepeatConfig(mode = RepeatModeKind.RADIAL, count = 6, radialRadius = 210f),
            layerStyle = LayerStyleConfig(
                innerGlowEnabled = true,
                innerGlowHex = "#43CBE8",
                innerGlowRadius = 24f,
                innerGlowAlpha = 0.8f,
                dashedStroke = true,
                dashLength = 22f,
                dashGap = 10f
            ),
            mask = MaskConfig(enabled = true, type = "ellipse", invert = true, matteMode = MatteModeKind.MASK, matteTargetId = "bg_shape"),
            keyframes = mapOf(
                "rotation" to listOf(
                    Keyframe(time = 0f, value = 0f),
                    Keyframe(time = 2f, value = 360f)
                )
            ),
            effects = listOf(EffectsCatalog.createInstance(EffectsCatalog.byId("glow")!!))
        )
        val camLayer = LayerData(
            id = "cam_1",
            type = LayerType.CAMERA,
            name = "Kamera",
            fov = 48f,
            dofEnabled = true,
            focusDistance = 1100f,
            aperture = 2.0f,
            lensMode = CameraLensMode.OPTICAL_ZOOM,
            orbitEnabled = true,
            orbitTargetId = "vec_1",
            orbitAzimuth = 35f,
            orbitElevation = 15f,
            orbitDistance = 1350f
        )

        val doc = ProjectDocument(
            project = ProjectData(name = "XML Test", width = 1080, height = 1920, fps = 60, duration = 6f, space3dActive = true),
            layers = listOf(vectorLayer, camLayer),
            markers = listOf(TimelineMarker(time = 1.5f, comment = "Beat"))
        )

        val xml = XmlInterchange.toAlightMotionXml(doc)
        val parsed = XmlInterchange.fromAlightMotionXml(xml)
        assertNotNull(parsed)
        assertEquals("XML Test", parsed!!.project.name)
        assertEquals(60, parsed.project.fps)
        assertTrue(parsed.project.space3dActive)
        assertEquals(2, parsed.layers.size)

        val parsedShape = parsed.layers[0]
        assertEquals(ShapeKind.STAR, parsedShape.shapeKind)
        assertEquals(7, parsedShape.shapePoints)
        assertEquals(RepeatModeKind.RADIAL, parsedShape.repeat.mode)
        assertEquals(6, parsedShape.repeat.count)
        assertTrue(parsedShape.layerStyle.innerGlowEnabled)
        assertTrue(parsedShape.layerStyle.dashedStroke)
        assertEquals(22f, parsedShape.layerStyle.dashLength, 0.1f)
        assertTrue(parsedShape.mask.invert)
        assertEquals(MatteModeKind.MASK, parsedShape.mask.matteMode)
        assertEquals(2, parsedShape.keyframes["rotation"]?.size)
        assertEquals(1, parsedShape.effects.size)

        val parsedCam = parsed.layers[1]
        assertEquals(CameraLensMode.OPTICAL_ZOOM, parsedCam.lensMode)
        assertTrue(parsedCam.dofEnabled)
        assertTrue(parsedCam.orbitEnabled)
        assertEquals(35f, parsedCam.orbitAzimuth, 0.1f)
        assertEquals(1, parsed.markers.size)
    }

    @Test
    fun amprojZipExportAndImportRoundTripWithCanvasRender() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = AppDatabase.getInstance(context)
        val repo = ProjectRepository(context, db.projectDao())

        // Create a dummy PNG asset on disk to verify bundled media extraction & URI remapping
        val dummyBmp = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.CYAN)
        }
        val mediaFile = File(context.cacheDir, "test_sprite.png")
        mediaFile.outputStream().use { dummyBmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        dummyBmp.recycle()

        val baseDoc = ProjectTemplates.createFromTemplate("pulse")
        val docWithMedia = baseDoc.copy(
            layers = baseDoc.layers + LayerData(
                type = LayerType.IMAGE,
                name = "Sprite",
                mediaUri = Uri.fromFile(mediaFile).toString(),
                x = 540f,
                y = 960f,
                w = 240f,
                h = 240f
            )
        )

        val amprojFile = repo.exportAmprojZip(docWithMedia)
        assertTrue(amprojFile.exists() && amprojFile.length() > 100L)

        val importResult = repo.importProjectFromUri(Uri.fromFile(amprojFile))
        assertTrue("Expected .amproj import to succeed: ${importResult.exceptionOrNull()}", importResult.isSuccess)
        val importedDoc = importResult.getOrThrow()
        assertEquals(docWithMedia.layers.size, importedDoc.layers.size)
        val importedMediaUri = importedDoc.layers.last().mediaUri
        assertNotNull(importedMediaUri)
        assertTrue(CompositionCanvasRenderer.isMediaUriAccessible(context, importedMediaUri))

        // Render full composition frame cleanly to bitmap and sample eyedropper pixel
        val bmp = Bitmap.createBitmap(270, 480, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.scale(0.25f, 0.25f)
        CompositionCanvasRenderer.render(context, canvas, importedDoc, timeSec = 0.8f)
        val sampled = CompositionCanvasRenderer.sampleColorAt(context, importedDoc, timeSec = 0.8f, canvasX = 540f, canvasY = 960f)
        assertNotEquals(0, sampled)
        assertTrue("Expected non-zero alpha on sampled pixel, got $sampled", Color.alpha(sampled) > 0)
        bmp.recycle()
    }
}
