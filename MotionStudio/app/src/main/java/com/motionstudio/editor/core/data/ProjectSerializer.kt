package com.motionstudio.editor.core.data

import com.motionstudio.editor.core.domain.model.PROJECT_FORMAT_VERSION
import com.motionstudio.editor.core.domain.model.Project
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Structured, versioned JSON project format. Pure JVM, fully unit-testable. */
object ProjectSerializer {

    val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        coerceInputValues = true
    }

    fun encode(project: Project): String = json.encodeToString(project)

    fun decode(raw: String): Project = json.decodeFromString(raw)

    /** Forward migration: fills missing fields with defaults and stamps the current version. */
    fun migrate(raw: String): Project {
        val parsed = decode(raw)
        val healed = parsed.copy(
            version = PROJECT_FORMAT_VERSION,
            fps = parsed.fps.coerceIn(1, 120),
            width = parsed.width.coerceIn(16, 4096),
            height = parsed.height.coerceIn(16, 4096),
            durationMs = parsed.durationMs.coerceAtLeast(100L),
            layers = parsed.layers.map { layer ->
                layer.copy(
                    durationMs = layer.durationMs.coerceAtLeast(33L),
                    inPointMs = layer.inPointMs.coerceAtLeast(0L),
                    startMs = layer.startMs.coerceAtLeast(0L)
                )
            }
        )
        return healed
    }
}
