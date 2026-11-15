package com.example.data

import com.example.model.*
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import kotlin.math.max

object XmlInterchange {
    private fun esc(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    fun toAlightMotionXml(doc: ProjectDocument): String {
        val p = doc.project
        val sb = StringBuilder()
        sb.appendLine("""<?xml version="1.0" encoding="utf-8"?>""")
        sb.appendLine(
            """<scene title="${esc(p.name)}" width="${p.width}" height="${p.height}" fps="${p.fps}" totalTime="${(p.duration * 1000).toInt()}" bgcolor="${esc(p.bgHex)}" ratio="${esc(p.ratio)}" space3d="${p.space3dActive}" omVersion="5.238">"""
        )
        for (layer in doc.layers) {
            val startMs = (layer.start * 1000).toInt()
            val endMs = ((layer.start + layer.duration) * 1000).toInt()
            val tag = when (layer.type) {
                LayerType.TEXT -> "text"
                LayerType.CAMERA -> "camera"
                LayerType.NULL -> "nullobj"
                LayerType.GROUP -> "embedScene"
                LayerType.AUDIO -> "audio"
                LayerType.IMAGE -> "image"
                LayerType.VIDEO -> "video"
                LayerType.MESH -> "mesh"
                LayerType.SHAPE -> "shape"
            }
            sb.appendLine(
                """  <$tag id="${esc(layer.id)}" label="${esc(layer.name)}" startTime="$startMs" endTime="$endMs" hidden="${layer.hidden}" locked="${layer.locked}" parent="${esc(layer.parentId ?: "")}" blend="${layer.blendMode.name}" shape="${layer.shapeKind.name}" shapePoints="${layer.shapePoints}" shapeInner="${layer.shapeInner}" shapeSides="${layer.shapeSides}" color="${esc(layer.colorHex)}" strokeColor="${esc(layer.strokeHex)}" strokeWidth="${layer.strokeWidth}" radius="${layer.radius}" space3d="${layer.space3d}" media="${esc(layer.mediaUri ?: "")}" volume="${layer.volume}" playbackRate="${layer.playbackRate}">"""
            )
            sb.appendLine(
                """    <transform x="${layer.x}" y="${layer.y}" z="${layer.z}" w="${layer.w}" h="${layer.h}" sx="${layer.scaleX}" sy="${layer.scaleY}" rot="${layer.rotation}" rotX="${layer.rotX}" rotY="${layer.rotY}" rotZ="${layer.rotZ}" opacity="${layer.opacity}" />"""
            )
            if (layer.type == LayerType.TEXT) {
                sb.appendLine("""    <content font="${esc(layer.fontFamily)}" size="${layer.fontSize}" weight="${layer.fontWeight}" italic="${layer.italic}" align="${layer.textAlign.name}" color="${esc(layer.colorHex)}">${esc(layer.textContent)}</content>""")
            }
            if (layer.type == LayerType.CAMERA) {
                sb.appendLine(
                    """    <cameraOpts fov="${layer.fov}" focusDistance="${layer.focusDistance}" dof="${layer.dofEnabled}" aperture="${layer.aperture}" lensMode="${layer.lensMode.name}" orbit="${layer.orbitEnabled}" orbitTarget="${esc(layer.orbitTargetId ?: "")}" azimuth="${layer.orbitAzimuth}" elevation="${layer.orbitElevation}" distance="${layer.orbitDistance}" />"""
                )
            }
            if (layer.type == LayerType.MESH) {
                sb.appendLine("""    <meshOpts kind="${layer.meshKind.name}" depth="${layer.depth}" />""")
            }
            if (layer.vectorPath != null && layer.vectorPath.nodes.isNotEmpty()) {
                sb.appendLine("""    <vector closed="${layer.vectorPath.closed}">""")
                for (n in layer.vectorPath.nodes) {
                    sb.appendLine("""      <node x="${n.x}" y="${n.y}" inX="${n.inX}" inY="${n.inY}" outX="${n.outX}" outY="${n.outY}" smooth="${n.smooth}" sym="${n.symmetric}" />""")
                }
                sb.appendLine("""    </vector>""")
            }
            if (layer.mask.enabled || layer.mask.matteMode != MatteModeKind.NONE) {
                sb.appendLine(
                    """    <mask enabled="${layer.mask.enabled}" type="${esc(layer.mask.type)}" invert="${layer.mask.invert}" matte="${layer.mask.matteMode.name}" target="${esc(layer.mask.matteTargetId ?: "")}" />"""
                )
            }
            if (layer.repeat.mode != RepeatModeKind.NONE) {
                sb.appendLine(
                    """    <repeat mode="${layer.repeat.mode.name}" count="${layer.repeat.count}" cols="${layer.repeat.cols}" rows="${layer.repeat.rows}" ox="${layer.repeat.offsetX}" oy="${layer.repeat.offsetY}" rot="${layer.repeat.offsetRotation}" scale="${layer.repeat.offsetScale}" radius="${layer.repeat.radialRadius}" />"""
                )
            }
            if (layer.layerStyle.innerGlowEnabled || layer.layerStyle.dashedStroke) {
                sb.appendLine(
                    """    <layerStyle innerGlow="${layer.layerStyle.innerGlowEnabled}" glowHex="${esc(layer.layerStyle.innerGlowHex)}" glowRadius="${layer.layerStyle.innerGlowRadius}" glowAlpha="${layer.layerStyle.innerGlowAlpha}" dashed="${layer.layerStyle.dashedStroke}" dashLen="${layer.layerStyle.dashLength}" dashGap="${layer.layerStyle.dashGap}" />"""
                )
            }
            for ((prop, keys) in layer.keyframes) {
                sb.appendLine("""    <property name="${esc(prop)}">""")
                for (kf in keys) {
                    sb.appendLine("""      <kf t="${kf.time}" v="${kf.value}" easing="${esc(kf.easing.preset)}" c1="${kf.easing.x1},${kf.easing.y1}" c2="${kf.easing.x2},${kf.easing.y2}" />""")
                }
                sb.appendLine("""    </property>""")
            }
            for (fx in layer.effects) {
                sb.appendLine("""    <effect id="${esc(fx.specId)}" enabled="${fx.enabled}">""")
                for ((k, v) in fx.params) {
                    sb.appendLine("""      <param name="${esc(k)}" value="$v" />""")
                }
                sb.appendLine("""    </effect>""")
            }
            sb.appendLine("""  </$tag>""")
        }
        for (marker in doc.markers) {
            sb.appendLine("""  <marker t="${marker.time}" comment="${esc(marker.comment)}" color="${esc(marker.colorHex)}" />""")
        }
        sb.appendLine("</scene>")
        return sb.toString()
    }

    /**
     * Parses Open Motion XML and standard Alight Motion XML interchange files into a native [ProjectDocument].
     */
    fun fromAlightMotionXml(xml: String): ProjectDocument? {
        return try {
            val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml.trim()))

            var projName = "Proyek XML Impor"
            var projW = 1080
            var projH = 1920
            var projFps = 30
            var projDur = 10f
            var projBg = "#10121D"
            var projRatio = "9:16"
            var space3d = false

            val layers = mutableListOf<LayerData>()
            val markers = mutableListOf<TimelineMarker>()

            var curLayer: LayerData? = null
            var curPropName: String? = null
            var curPropKeys = mutableListOf<Keyframe>()
            var curEffect: EffectInstance? = null
            var curEffectParams = mutableMapOf<String, Float>()
            var curVectorNodes = mutableListOf<VectorNode>()
            var curVectorClosed = true
            var insideVector = false
            var curTagDepth = 0

            val layerTags = setOf("shape", "text", "image", "video", "audio", "camera", "nullobj", "embedScene", "mesh", "bookmark")

            fun attr(name: String): String? = parser.getAttributeValue(null, name)
            fun attrF(name: String, def: Float): Float = attr(name)?.toFloatOrNull() ?: def
            fun attrI(name: String, def: Int): Int = attr(name)?.toIntOrNull() ?: def
            fun attrB(name: String, def: Boolean): Boolean = attr(name)?.equals("true", ignoreCase = true) ?: def

            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        val tag = parser.name.orEmpty()
                        if (tag == "scene" || tag == "project") {
                            projName = attr("title")?.ifBlank { null } ?: attr("name") ?: projName
                            projW = attrI("width", projW).coerceIn(128, 4096)
                            projH = attrI("height", projH).coerceIn(128, 4096)
                            projFps = attrI("fps", projFps).coerceIn(12, 120)
                            val totalMs = attr("totalTime")?.toFloatOrNull() ?: (attr("duration")?.toFloatOrNull()?.times(1000f))
                            if (totalMs != null && totalMs > 0f) {
                                projDur = (totalMs / 1000f).coerceIn(1f, 600f)
                            }
                            projBg = normalizeColorHex(attr("bgcolor") ?: attr("bgColor"), projBg)
                            projRatio = attr("ratio") ?: inferRatio(projW, projH)
                            space3d = attrB("space3d", false)
                        } else if (tag in layerTags && curLayer == null) {
                            val type = when (tag) {
                                "text" -> LayerType.TEXT
                                "camera" -> LayerType.CAMERA
                                "nullobj" -> LayerType.NULL
                                "embedScene" -> LayerType.GROUP
                                "audio" -> LayerType.AUDIO
                                "image" -> LayerType.IMAGE
                                "video" -> LayerType.VIDEO
                                "mesh" -> LayerType.MESH
                                else -> LayerType.SHAPE
                            }
                            val startRaw = attrF("startTime", attrF("start", 0f))
                            val endRaw = attrF("endTime", startRaw + 3000f)
                            val isMs = endRaw > 120f || attr("endTime") != null
                            val startSec = if (isMs) startRaw / 1000f else startRaw
                            val durSec = max(0.2f, if (isMs) (endRaw - startRaw) / 1000f else attrF("duration", 3f))
                            val shapeName = attr("shape") ?: attr("s")
                            val shapeKind = ShapeKind.entries.find {
                                it.name.equals(shapeName, true)
                            } ?: when (shapeName?.lowercase()) {
                                ".circle", "circle", "oval" -> ShapeKind.ELLIPSE
                                ".roundrect", "roundrect" -> ShapeKind.ROUND_RECT
                                ".star", "star" -> ShapeKind.STAR
                                ".triangle", "triangle" -> ShapeKind.TRIANGLE
                                else -> ShapeKind.RECT
                            }
                            val blend = BlendModeKind.entries.find { it.name.equals(attr("blend"), true) } ?: BlendModeKind.NORMAL
                            val layerMedia = attr("media")?.ifBlank { null } ?: attr("src")?.ifBlank { null }

                            curLayer = LayerData(
                                id = attr("id")?.ifBlank { null } ?: newUid("layer"),
                                type = type,
                                name = attr("label")?.ifBlank { null } ?: attr("name")?.ifBlank { null } ?: tag.replaceFirstChar { it.uppercase() },
                                start = startSec.coerceAtLeast(0f),
                                duration = durSec,
                                hidden = attrB("hidden", false),
                                locked = attrB("locked", false),
                                parentId = attr("parent")?.ifBlank { null },
                                space3d = attrB("space3d", type == LayerType.CAMERA || type == LayerType.MESH),
                                x = attrF("x", projW * 0.5f),
                                y = attrF("y", projH * 0.5f),
                                w = attrF("w", projW * 0.45f),
                                h = attrF("h", projH * 0.35f),
                                shapeKind = shapeKind,
                                shapePoints = attrI("shapePoints", 5),
                                shapeInner = attrF("shapeInner", 46f),
                                shapeSides = attrI("shapeSides", 6),
                                colorHex = normalizeColorHex(attr("color") ?: attr("fillColor"), "#00D7A7"),
                                strokeHex = normalizeColorHex(attr("strokeColor"), "#FFFFFF"),
                                strokeWidth = attrF("strokeWidth", 0f),
                                radius = attrF("radius", 28f),
                                blendMode = blend,
                                mediaUri = layerMedia,
                                volume = attrF("volume", 1f),
                                playbackRate = attrF("playbackRate", 1f)
                            )
                            curTagDepth = parser.depth
                        } else if (curLayer != null) {
                            when (tag) {
                                "transform" -> {
                                    curLayer = curLayer.copy(
                                        x = attrF("x", curLayer.x),
                                        y = attrF("y", curLayer.y),
                                        z = attrF("z", curLayer.z),
                                        w = attrF("w", curLayer.w),
                                        h = attrF("h", curLayer.h),
                                        scaleX = attrF("sx", attrF("scaleX", curLayer.scaleX)),
                                        scaleY = attrF("sy", attrF("scaleY", curLayer.scaleY)),
                                        rotation = attrF("rot", attrF("rotation", curLayer.rotation)),
                                        rotX = attrF("rotX", curLayer.rotX),
                                        rotY = attrF("rotY", curLayer.rotY),
                                        rotZ = attrF("rotZ", curLayer.rotZ),
                                        opacity = attrF("opacity", attrF("alpha", curLayer.opacity)).coerceIn(0f, 1f)
                                    )
                                }
                                "location" -> {
                                    val lx = attrF("value", curLayer.x)
                                    val ly = attr("value2")?.toFloatOrNull() ?: curLayer.y
                                    curLayer = curLayer.copy(x = lx, y = ly)
                                }
                                "content" -> {
                                    val font = attr("font") ?: curLayer.fontFamily
                                    val size = attrF("size", curLayer.fontSize)
                                    val weight = attrI("weight", curLayer.fontWeight)
                                    val italic = attrB("italic", curLayer.italic)
                                    val align = TextAlignKind.entries.find { it.name.equals(attr("align"), true) } ?: curLayer.textAlign
                                    val col = normalizeColorHex(attr("color"), curLayer.colorHex)
                                    val txt = parser.nextText().orEmpty()
                                    curLayer = curLayer.copy(
                                        fontFamily = font,
                                        fontSize = size,
                                        fontWeight = weight,
                                        italic = italic,
                                        textAlign = align,
                                        colorHex = col,
                                        textContent = txt.ifEmpty { curLayer.textContent }
                                    )
                                }
                                "cameraOpts" -> {
                                    val lens = CameraLensMode.entries.find { it.name.equals(attr("lensMode"), true) } ?: CameraLensMode.FREE
                                    curLayer = curLayer.copy(
                                        fov = attrF("fov", curLayer.fov),
                                        focusDistance = attrF("focusDistance", curLayer.focusDistance),
                                        dofEnabled = attrB("dof", curLayer.dofEnabled),
                                        aperture = attrF("aperture", curLayer.aperture),
                                        lensMode = lens,
                                        orbitEnabled = attrB("orbit", curLayer.orbitEnabled),
                                        orbitTargetId = attr("orbitTarget")?.ifBlank { null },
                                        orbitAzimuth = attrF("azimuth", curLayer.orbitAzimuth),
                                        orbitElevation = attrF("elevation", curLayer.orbitElevation),
                                        orbitDistance = attrF("distance", curLayer.orbitDistance)
                                    )
                                }
                                "meshOpts" -> {
                                    val kind = MeshKind.entries.find { it.name.equals(attr("kind"), true) } ?: MeshKind.CUBE
                                    curLayer = curLayer.copy(meshKind = kind, depth = attrF("depth", curLayer.depth))
                                }
                                "mask" -> {
                                    val mMode = MatteModeKind.entries.find { it.name.equals(attr("matte"), true) } ?: MatteModeKind.NONE
                                    curLayer = curLayer.copy(
                                        mask = curLayer.mask.copy(
                                            enabled = attrB("enabled", false),
                                            type = attr("type") ?: "rect",
                                            invert = attrB("invert", false),
                                            matteMode = mMode,
                                            matteTargetId = attr("target")?.ifBlank { null }
                                        )
                                    )
                                }
                                "repeat" -> {
                                    val rMode = RepeatModeKind.entries.find { it.name.equals(attr("mode"), true) } ?: RepeatModeKind.NONE
                                    curLayer = curLayer.copy(
                                        repeat = RepeatConfig(
                                            mode = rMode,
                                            count = attrI("count", 5),
                                            cols = attrI("cols", 3),
                                            rows = attrI("rows", 3),
                                            offsetX = attrF("ox", 90f),
                                            offsetY = attrF("oy", 0f),
                                            offsetRotation = attrF("rot", 0f),
                                            offsetScale = attrF("scale", 1f),
                                            radialRadius = attrF("radius", 180f)
                                        )
                                    )
                                }
                                "layerStyle" -> {
                                    curLayer = curLayer.copy(
                                        layerStyle = LayerStyleConfig(
                                            innerGlowEnabled = attrB("innerGlow", false),
                                            innerGlowHex = normalizeColorHex(attr("glowHex"), "#FFFFFF"),
                                            innerGlowRadius = attrF("glowRadius", 18f),
                                            innerGlowAlpha = attrF("glowAlpha", 0.65f),
                                            dashedStroke = attrB("dashed", false),
                                            dashLength = attrF("dashLen", 18f),
                                            dashGap = attrF("dashGap", 12f)
                                        )
                                    )
                                }
                                "vector" -> {
                                    insideVector = true
                                    curVectorClosed = attrB("closed", true)
                                    curVectorNodes = mutableListOf()
                                }
                                "node" -> if (insideVector) {
                                    curVectorNodes.add(
                                        VectorNode(
                                            x = attrF("x", 0f),
                                            y = attrF("y", 0f),
                                            inX = attrF("inX", 0f),
                                            inY = attrF("inY", 0f),
                                            outX = attrF("outX", 0f),
                                            outY = attrF("outY", 0f),
                                            smooth = attrB("smooth", true),
                                            symmetric = attrB("sym", true)
                                        )
                                    )
                                }
                                "property" -> {
                                    curPropName = attr("name")
                                    curPropKeys = mutableListOf()
                                }
                                "kf" -> if (curPropName != null) {
                                    val t = attrF("t", 0f)
                                    val v = attrF("v", 0f)
                                    val preset = attr("easing") ?: "linear"
                                    val c1 = attr("c1")?.split(",")
                                    val c2 = attr("c2")?.split(",")
                                    curPropKeys.add(
                                        Keyframe(
                                            time = t,
                                            value = v,
                                            easing = EasingSpec(
                                                preset = preset,
                                                x1 = c1?.getOrNull(0)?.toFloatOrNull() ?: 0.25f,
                                                y1 = c1?.getOrNull(1)?.toFloatOrNull() ?: 0.25f,
                                                x2 = c2?.getOrNull(0)?.toFloatOrNull() ?: 0.75f,
                                                y2 = c2?.getOrNull(1)?.toFloatOrNull() ?: 0.75f
                                            )
                                        )
                                    )
                                }
                                "effect" -> {
                                    val specId = attr("id") ?: "gaussian_blur"
                                    val spec = EffectsCatalog.byId(specId)
                                    curEffect = EffectInstance(
                                        specId = specId,
                                        name = spec?.nameId ?: specId,
                                        category = spec?.category ?: "Lainnya",
                                        enabled = attrB("enabled", true)
                                    )
                                    curEffectParams = mutableMapOf()
                                }
                                "param" -> if (curEffect != null) {
                                    val k = attr("name")
                                    val v = attr("value")?.toFloatOrNull()
                                    if (k != null && v != null) curEffectParams[k] = v
                                }
                            }
                        } else if (tag == "marker") {
                            markers.add(
                                TimelineMarker(
                                    time = attrF("t", 0f),
                                    comment = attr("comment") ?: "Marker",
                                    colorHex = normalizeColorHex(attr("color"), "#00D7A7")
                                )
                            )
                        }
                    }

                    XmlPullParser.END_TAG -> {
                        val tag = parser.name.orEmpty()
                        if (tag == "property" && curLayer != null && curPropName != null) {
                            if (curPropKeys.isNotEmpty()) {
                                curLayer = curLayer.copy(keyframes = curLayer.keyframes + (curPropName to curPropKeys))
                            }
                            curPropName = null
                        } else if (tag == "effect" && curLayer != null && curEffect != null) {
                            val mergedParams = (EffectsCatalog.byId(curEffect.specId)?.params?.associate { it.id to it.defaultValue } ?: emptyMap()) + curEffectParams
                            curLayer = curLayer.copy(effects = curLayer.effects + curEffect.copy(params = mergedParams))
                            curEffect = null
                        } else if (tag == "vector" && curLayer != null && insideVector) {
                            curLayer = curLayer.copy(
                                shapeKind = ShapeKind.VECTOR_PATH,
                                vectorPath = VectorPathData(closed = curVectorClosed, nodes = curVectorNodes)
                            )
                            insideVector = false
                        } else if (curLayer != null && tag in layerTags && parser.depth == curTagDepth) {
                            layers.add(curLayer)
                            curLayer = null
                        }
                    }
                }
                event = parser.next()
            }

            if (layers.isEmpty() && projName == "Proyek XML Impor" && !xml.contains("<scene", ignoreCase = true)) {
                return null
            }

            ProjectDocument(
                project = ProjectData(
                    id = newUid("project"),
                    name = projName,
                    width = projW,
                    height = projH,
                    fps = projFps,
                    duration = projDur,
                    bgHex = projBg,
                    ratio = projRatio,
                    space3dActive = space3d
                ),
                layers = layers,
                markers = markers
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun normalizeColorHex(raw: String?, fallback: String): String {
        if (raw.isNullOrBlank()) return fallback
        val clean = raw.trim()
        return if (clean.startsWith("#")) clean else "#$clean"
    }

    private fun inferRatio(w: Int, h: Int): String {
        val r = w.toFloat() / h.coerceAtLeast(1)
        return when {
            kotlin.math.abs(r - 9f / 16f) < 0.05f -> "9:16"
            kotlin.math.abs(r - 16f / 9f) < 0.05f -> "16:9"
            kotlin.math.abs(r - 4f / 5f) < 0.05f -> "4:5"
            kotlin.math.abs(r - 1f) < 0.05f -> "1:1"
            kotlin.math.abs(r - 4f / 3f) < 0.05f -> "4:3"
            else -> "Custom"
        }
    }
}
