package com.motionstudio.editor.core.importer

import com.motionstudio.editor.core.domain.model.Project

enum class ImportKind { NATIVE_JSON, NATIVE_XML, ALIGHT_XML, NODE_VIDEO_ZIP, AFTER_EFFECTS_AEPX, AFTER_EFFECTS_AEP, UNKNOWN }
enum class ImportStatus { IMPORTED, CONVERTED_WITH_MODIFICATIONS, PARTIAL, UNSUPPORTED }

data class ImportReport(
    val kind: ImportKind,
    val fileName: String,
    val bytes: Long,
    val status: ImportStatus,
    val layersImported: Int = 0,
    val keyframesImported: Int = 0,
    val effectsImported: Int = 0,
    val masksImported: Int = 0,
    val assetsFound: Int = 0,
    val assetsRelinked: Int = 0,
    val assetsDuplicated: Int = 0,
    val missingMedia: List<String> = emptyList(),
    val unsupported: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val originalPath: String = "",
    val logPath: String? = null,
    val projectId: String? = null
) {
    val headline: String get() = when (status) {
        ImportStatus.IMPORTED -> "Imported successfully"
        ImportStatus.CONVERTED_WITH_MODIFICATIONS -> "Converted with modifications"
        ImportStatus.PARTIAL -> "Partially imported"
        ImportStatus.UNSUPPORTED -> "Not convertible on this device"
    }
}

sealed class ImportResult {
    data class Success(val report: ImportReport, val project: Project) : ImportResult()
    data class ReportOnly(val report: ImportReport) : ImportResult()
    data class Failure(val message: String, val report: ImportReport?) : ImportResult()
    data object Cancelled : ImportResult()
}

data class ImportedAsset(val name: String, val path: String, val mime: String)
