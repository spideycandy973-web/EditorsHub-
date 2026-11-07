package com.editorshub

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import com.editorshub.core.importer.AlightMotionXmlImporter
import com.editorshub.core.importer.NativeXmlExporter
import com.editorshub.core.importer.NodeVideoZipImporter
import com.editorshub.core.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class ProjectImporterInstrumentedTest {

    private val sampleXml = """
        <AlightMotion version="1.0">
          <Composition name="Imported" width="1080" height="1920" fps="29.97" duration="6">
            <Layers>
              <Layer name="Headline" type="text" start="0" duration="5" x="10" y="20" opacity="0.8">
                <text>Hello Motion</text>
                <Keyframes><Keyframe property="opacity" time="0" value="0" easing="easeInOut"/>
                <Keyframe property="opacity" time="1000" value="1"/></Keyframes>
                <Effects><Effect type="glow" amount="30"/></Effects>
                <Masks><Mask type="circle" feather="12"/></Masks>
              </Layer>
              <Layer name="Shape" type="shape" start="1" duration="4">
                <Shape kind="star" width="500" height="500"/>
              </Layer>
            </Layers>
          </Composition>
        </AlightMotion>
    """.trimIndent()

    @Test fun xmlIsActuallyReconstructed() {
        val draft = AlightMotionXmlImporter.parse(
            ByteArrayInputStream(sampleXml.toByteArray()), "sample.xml", sampleXml.length.toLong())
        assertEquals(2, draft.project.layers.size)
        assertEquals("Headline", draft.project.layers[0].name)
        assertEquals(LayerType.TEXT, draft.project.layers[0].type)
        assertEquals("Hello Motion", draft.project.layers[0].text?.text)
        assertEquals(29.97, draft.project.fpsExact, 0.001)
        assertEquals(2, draft.counts[1]) // keyframes
        assertEquals(1, draft.counts[2]) // effects
        assertEquals(1, draft.counts[3]) // masks
        assertTrue(draft.project.layers[0].keyframes.isNotEmpty())
    }

    @Test fun nativeXmlExportRoundTripsProjectData() {
        val layer = Layer(
            id = "shape-1", name = "Outlined star", type = LayerType.SHAPE,
            startMs = 275L, durationMs = 2750L,
            transform = Transform(x = 24f, y = -18f, rotation = 17f, skewX = 6f),
            shape = ShapeSpec(
                kind = ShapeKind.STAR, width = 420f, height = 360f, starPoints = 7,
                strokeEnabled = false, strokeWidth = 4f, strokeCap = 2, strokeJoin = 1,
                strokeStart = 25f, strokeEnd = 75f,
                shadowEnabled = true, shadowSize = 18f, shadowAlpha = 0.65f
            ),
            keyframes = listOf(KeyframeTrack("opacity", listOf(
                Keyframe(id = "kf-1", timeMs = 900L, value = 0.4f, easing = Easing.EASE_IN_OUT)
            ))),
            effects = listOf(Effect(
                id = "fx-1", type = EffectType.GLOW, params = mapOf("amount" to 22f, "strength" to 0.8f)
            ))
        )
        val original = Project(
            id = "project-1", name = "XML Round Trip", width = 720, height = 1280,
            fps = 30, fpsExact = 29.97, durationMs = 5000L,
            layers = listOf(layer), createdAt = 10L, updatedAt = 20L
        )
        val xml = NativeXmlExporter.encode(original)
        val restored = AlightMotionXmlImporter.parse(
            ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)), "roundtrip.xml", xml.length.toLong()
        ).project

        assertEquals(original.name, restored.name)
        assertEquals(original.width, restored.width)
        assertEquals(original.height, restored.height)
        assertEquals(original.fpsExact, restored.fpsExact, 0.0001)
        assertEquals(original.durationMs, restored.durationMs)
        assertEquals(original.layers.first().copy(id = restored.layers.first().id), restored.layers.first())
        assertEquals(original.layers.first().shape, restored.layers.first().shape)
        assertEquals(original.layers.first().effects, restored.layers.first().effects)
        assertEquals(original.layers.first().keyframes, restored.layers.first().keyframes)
    }

    @Test fun zipIsStreamedAndAssetsExtracted() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val archive = File(ctx.cacheDir, "test_project.zip")
        val temp = File(ctx.cacheDir, "extract_test").apply { mkdirs() }
        val media = File(ctx.filesDir, "media").apply { mkdirs() }
        ZipOutputStream(archive.outputStream()).use { zos ->
            zos.putNextEntry(ZipEntry("project.json"))
            zos.write("""{"name":"Node Project","width":720,"height":1280,"fps":30,"duration":5,
                "layers":[{"name":"Clip","type":"image","source":"clip.png","start":0,"duration":5}]}""".toByteArray())
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("clip.png"))
            zos.write(ByteArray(1024) { 7 })
            zos.closeEntry()
        }
        val draft = NodeVideoZipImporter.parse(archive, temp, media) { }
        assertEquals("Node Project", draft.project.name)
        assertEquals(720, draft.project.width)
        assertEquals(1, draft.assets.size)
        assertTrue(draft.project.layers.first().sourceUri?.contains("file://") == true)
        archive.delete()
    }
}
