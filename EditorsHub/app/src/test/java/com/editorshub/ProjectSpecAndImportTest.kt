package com.editorshub

import com.editorshub.core.data.ProjectSerializer
import com.editorshub.core.domain.model.*
import com.editorshub.core.importer.ImportKind
import com.editorshub.core.importer.ImportReport
import com.editorshub.core.importer.ImportStatus
import org.junit.Assert.*
import org.junit.Test

class ProjectSpecAndImportTest {
    @Test fun specFieldsArePersisted() {
        val spec = ProjectSpec(name = "4K film", width = 3840, height = 2160,
            fpsExact = 23.976, backgroundType = BackgroundType.LINEAR_GRADIENT,
            audioSampleRate = 96_000, colorSpace = ColorSpace.DISPLAY_P3, timebase = "1001/24000")
        val project = Project(id = "p", name = spec.name, width = spec.width,
            height = spec.height, fps = FrameRates.rounded(spec.fpsExact),
            fpsExact = spec.fpsExact, backgroundType = spec.backgroundType,
            audioSampleRate = spec.audioSampleRate, colorSpace = spec.colorSpace,
            timebase = spec.timebase)
        val restored = ProjectSerializer.decode(ProjectSerializer.encode(project))
        assertEquals(3840, restored.width)
        assertEquals(23.976, restored.fpsExact, 0.0001)
        assertEquals(BackgroundType.LINEAR_GRADIENT, restored.backgroundType)
        assertEquals(96_000, restored.audioSampleRate)
        assertEquals(ColorSpace.DISPLAY_P3, restored.colorSpace)
        assertEquals("1001/24000", restored.timebase)
    }

    @Test fun migrationFillsExactFpsForOldProjects() {
        val old = """{"id":"old","name":"Legacy","width":1080,"height":1920,"fps":30,"durationMs":5000,"background":"#FF000000","layers":[],"audioTracks":[],"transitions":[],"version":1,"updatedAt":0}"""
        val migrated = ProjectSerializer.migrate(old)
        assertEquals(2, migrated.version)
        assertEquals(30.0, migrated.fpsExact, 0.0001)
        assertEquals(BackgroundType.SOLID, migrated.backgroundType)
    }

    @Test fun reportHeadlineIsHonest() {
        assertEquals("Imported successfully", ImportReport(ImportKind.NATIVE_JSON, "a", 1,
            ImportStatus.IMPORTED).headline)
        assertEquals("Not convertible on this device", ImportReport(ImportKind.AFTER_EFFECTS_AEP,
            "a", 1, ImportStatus.UNSUPPORTED).headline)
    }
}
