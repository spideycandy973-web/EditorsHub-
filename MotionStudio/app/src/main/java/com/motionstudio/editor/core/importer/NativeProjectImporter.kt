package com.motionstudio.editor.core.importer

import com.motionstudio.editor.core.data.ProjectSerializer
import com.motionstudio.editor.core.domain.model.Project
import java.io.File
import java.io.InputStream
import java.util.UUID

/** Native EditorsHub project JSON — streamed, no size cap, ids remapped on import. */
object NativeProjectImporter {

    data class Draft(val project: Project, val missingMedia: List<String>, val warnings: List<String>)

    fun parseJson(input: InputStream, fileName: String): Draft {
        val text = input.readTextStreaming()
        val parsed = ProjectSerializer.migrate(text)
        val missing = mutableListOf<String>()
        val layers = parsed.layers.map { l ->
            val src = l.sourceUri
            if (src != null && !src.startsWith("data:") &&
                !File(src.removePrefix("file://")).exists()) missing.add(src)
            l.copy(id = UUID.randomUUID().toString(),
                effects = l.effects.map { it.copy(id = UUID.randomUUID().toString()) },
                masks = l.masks.map { it.copy(id = UUID.randomUUID().toString()) },
                keyframes = l.keyframes.map { t -> t.copy(keyframes = t.keyframes.map {
                    it.copy(id = UUID.randomUUID().toString()) }) })
        }
        val audio = parsed.audioTracks.map { a ->
            if (!a.uri.startsWith("data:") && !File(a.uri.removePrefix("file://")).exists()) missing.add(a.uri)
            a.copy(id = UUID.randomUUID().toString())
        }
        return Draft(
            parsed.copy(
                id = UUID.randomUUID().toString(),
                name = parsed.name.ifBlank { fileName.substringBeforeLast('.') },
                layers = layers, audioTracks = audio,
                createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()
            ),
            missing.distinct(),
            if (missing.isEmpty()) emptyList() else listOf("Some media references could not be resolved.")
        )
    }

    fun parseXml(input: InputStream, fileName: String): Draft {
        val draft = AlightMotionXmlImporter.parse(input, fileName, 0L)
        return Draft(draft.project.copy(name = draft.project.name.ifBlank { fileName.substringBeforeLast('.') }),
            draft.missingMedia, draft.warnings + draft.unsupported.map { "Unsupported: $it" })
    }
}
