package com.motionstudio.editor

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import com.motionstudio.editor.core.importer.AlightMotionXmlImporter
import com.motionstudio.editor.core.importer.NodeVideoZipImporter
import com.motionstudio.editor.core.domain.model.LayerType
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
