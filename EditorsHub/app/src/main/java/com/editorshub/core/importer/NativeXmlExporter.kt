package com.editorshub.core.importer

import com.editorshub.core.domain.model.Effect
import com.editorshub.core.domain.model.Layer
import android.util.Base64
import com.editorshub.core.data.ProjectSerializer
import com.editorshub.core.domain.model.Project

/** Human-readable XML interchange format. It deliberately contains project data only. */
object NativeXmlExporter {
    fun encode(project: Project): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        append("<EditorsHubMotion version=\"1\">\n")
        append("  <composition name=\"${esc(project.name)}\" width=\"${project.width}\" height=\"${project.height}\"")
        append(" fps=\"${project.fpsExact}\" durationMs=\"${project.durationMs}\"")
        append(" background=\"${esc(project.background)}\" />\n")
        append("  <layers>\n")
        project.layers.forEach { layer -> appendLayer(layer) }
        append("  </layers>\n")
        if (project.audioTracks.isNotEmpty()) {
            append("  <audioTracks>\n")
            project.audioTracks.forEach { track ->
                append("    <track type=\"audio\" name=\"${esc(track.name)}\" source=\"${esc(track.uri)}\"")
                append(" startMs=\"${track.startMs}\" durationMs=\"${track.durationMs}\" />\n")
            }
            append("  </audioTracks>\n")
        }
        val payload = Base64.encodeToString(
            ProjectSerializer.encode(project).toByteArray(Charsets.UTF_8), Base64.NO_WRAP
        )
        append("  <editorsHubData encoding=\"base64-json\">$payload</editorsHubData>\n")
        append("</EditorsHubMotion>\n")
    }

    private fun StringBuilder.appendLayer(layer: Layer) {
        append("    <layer type=\"${layer.type.name.lowercase()}\" name=\"${esc(layer.name)}\"")
        append(" startMs=\"${layer.startMs}\" durationMs=\"${layer.durationMs}\"")
        append(" x=\"${layer.transform.x}\" y=\"${layer.transform.y}\"")
        append(" scaleX=\"${layer.transform.scaleX}\" scaleY=\"${layer.transform.scaleY}\"")
        append(" rotation=\"${layer.transform.rotation}\" opacity=\"${layer.transform.opacity}\"")
        layer.sourceUri?.takeIf { it.isNotBlank() }?.let { append(" source=\"${esc(it)}\"") }
        append(" blend=\"${layer.blendMode.name}\">")
        append("\n")
        layer.shape?.let { shape ->
            append("      <shape kind=\"${shape.kind.name.lowercase()}\" width=\"${shape.width}\" height=\"${shape.height}\"")
            append(" cornerRadius=\"${shape.cornerRadius}\" sides=\"${shape.sides}\"")
            append(" starPoints=\"${shape.starPoints}\" innerRadius=\"${shape.innerRadius}\"")
            append(" fill=\"${esc(shape.fillColor)}\" fill2=\"${esc(shape.fillColor2)}\"")
            append(" gradient=\"${shape.gradient.name}\" gradientAngle=\"${shape.gradientAngle}\"")
            append(" strokeColor=\"${esc(shape.strokeColor)}\" strokeWidth=\"${shape.strokeWidth}\"")
            append(" strokeEnabled=\"${shape.strokeEnabled}\" strokeCap=\"${shape.strokeCap}\"")
            append(" strokeJoin=\"${shape.strokeJoin}\" strokeStart=\"${shape.strokeStart}\" strokeEnd=\"${shape.strokeEnd}\"")
            append(" shadowEnabled=\"${shape.shadowEnabled}\" shadowSize=\"${shape.shadowSize}\"")
            append(" shadowAlpha=\"${shape.shadowAlpha}\" shadowX=\"${shape.shadowX}\" shadowY=\"${shape.shadowY}\"")
            append(" shadowColor=\"${esc(shape.shadowColor)}\" />\n")
        }
        layer.text?.let { text ->
            append("      <text>${esc(text.text)}</text>\n")
        }
        if (layer.keyframes.isNotEmpty()) {
            append("      <keyframes>\n")
            layer.keyframes.forEach { track ->
                track.keyframes.forEach { kf ->
                    append("        <keyframe property=\"${esc(track.property)}\" time=\"${kf.timeMs}\"")
                    append(" value=\"${kf.value}\" easing=\"${kf.easing.name.lowercase().replace("_", "")}\" />\n")
                }
            }
            append("      </keyframes>\n")
        }
        if (layer.effects.isNotEmpty()) {
            append("      <effects>\n")
            layer.effects.forEach { appendEffect(it) }
            append("      </effects>\n")
        }
        if (layer.masks.isNotEmpty()) {
            append("      <masks>\n")
            layer.masks.forEach { mask ->
                append("        <mask type=\"${mask.kind.name.lowercase()}\" feather=\"${mask.featherPx}\"")
                append(" opacity=\"${mask.opacity}\" inverted=\"${mask.inverted}\" />\n")
            }
            append("      </masks>\n")
        }
        append("    </layer>\n")
    }

    private fun StringBuilder.appendEffect(effect: Effect) {
        val amount = effect.params.values.firstOrNull() ?: 0f
        append("        <effect type=\"${effect.type.name.lowercase()}\" enabled=\"${effect.enabled}\" amount=\"$amount\" />\n")
    }

    private fun esc(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
