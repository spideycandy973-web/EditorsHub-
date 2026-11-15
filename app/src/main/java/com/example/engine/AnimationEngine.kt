package com.example.engine

import com.example.model.*
import kotlin.math.*

data class EvaluatedTransform(
    val x: Float,
    val y: Float,
    val z: Float,
    val scaleX: Float,
    val scaleY: Float,
    val rotation: Float,
    val rotX: Float,
    val rotY: Float,
    val rotZ: Float,
    val opacity: Float
)

data class EvaluatedCamera(
    val layer: LayerData,
    val x: Float,
    val y: Float,
    val z: Float,
    val rotX: Float,
    val rotY: Float,
    val rotZ: Float,
    val fov: Float,
    val focalMultiplier: Float,
    val focusDistance: Float,
    val aperture: Float,
    val dofEnabled: Boolean
)

object EasingPresets {
    val all: List<Pair<String, EasingSpec>> = listOf(
        "Linear" to EasingSpec("linear", 0.25f, 0.25f, 0.75f, 0.75f),
        "Ease Out" to EasingSpec("easeOut", 0.16f, 1.0f, 0.30f, 1.0f),
        "Ease In" to EasingSpec("easeIn", 0.70f, 0.0f, 0.84f, 0.0f),
        "In-Out" to EasingSpec("easeInOut", 0.42f, 0.0f, 0.58f, 1.0f),
        "Expo Out" to EasingSpec("expoOut", 0.16f, 1.0f, 0.22f, 1.0f),
        "Overshoot" to EasingSpec("overshoot", 0.34f, 1.56f, 0.64f, 1.0f),
        "Snap" to EasingSpec("snap", 0.12f, 0.92f, 0.20f, 1.0f),
        "Elastic" to EasingSpec("elastic", 0.28f, 1.45f, 0.48f, 0.96f)
    )

    fun evaluate(spec: EasingSpec, tRaw: Float): Float {
        val t = tRaw.coerceIn(0f, 1f)
        if (spec.preset == "linear") return t
        if (spec.preset == "elastic") {
            if (t <= 0f) return 0f
            if (t >= 1f) return 1f
            val p = 0.38f
            return (2.0.pow((-10f * t).toDouble()) * sin(((t - p / 4f) * (2f * PI.toFloat()) / p).toDouble()) + 1.0).toFloat()
        }
        return solveCubicBezierY(spec.x1, spec.y1, spec.x2, spec.y2, t)
    }

    private fun sampleCurve(p1: Float, p2: Float, u: Float): Float {
        val inv = 1f - u
        return 3f * inv * inv * u * p1 + 3f * inv * u * u * p2 + u * u * u
    }

    private fun solveCubicBezierY(x1: Float, y1: Float, x2: Float, y2: Float, targetX: Float): Float {
        var lo = 0f
        var hi = 1f
        var u = targetX
        for (i in 0 until 14) {
            u = (lo + hi) * 0.5f
            val x = sampleCurve(x1.coerceIn(0f, 1f), x2.coerceIn(0f, 1f), u)
            if (x < targetX) lo = u else hi = u
        }
        return sampleCurve(y1, y2, u)
    }
}

object AnimationEngine {

    fun basePropertyValue(layer: LayerData, prop: String): Float {
        return when {
            prop == "x" -> layer.x
            prop == "y" -> layer.y
            prop == "z" -> layer.z
            prop == "w" -> layer.w
            prop == "h" -> layer.h
            prop == "scaleX" -> layer.scaleX
            prop == "scaleY" -> layer.scaleY
            prop == "scaleZ" -> layer.scaleZ
            prop == "rotation" -> layer.rotation
            prop == "rotX" -> layer.rotX
            prop == "rotY" -> layer.rotY
            prop == "rotZ" -> layer.rotZ
            prop == "skewX" -> layer.skewX
            prop == "skewY" -> layer.skewY
            prop == "opacity" -> layer.opacity
            prop == "radius" -> layer.radius
            prop == "strokeWidth" -> layer.strokeWidth
            prop == "fov" -> layer.fov
            prop == "focusDistance" -> layer.focusDistance
            prop == "aperture" -> layer.aperture
            prop == "orbitAzimuth" -> layer.orbitAzimuth
            prop == "orbitElevation" -> layer.orbitElevation
            prop == "orbitDistance" -> layer.orbitDistance
            prop.startsWith("effect.") -> {
                val parts = prop.split(".")
                if (parts.size == 3) {
                    layer.effects.find { it.id == parts[1] }?.params?.get(parts[2]) ?: 0f
                } else 0f
            }
            else -> 0f
        }
    }

    fun withPropertyValue(layer: LayerData, prop: String, value: Float): LayerData {
        return when {
            prop == "x" -> layer.copy(x = value)
            prop == "y" -> layer.copy(y = value)
            prop == "z" -> layer.copy(z = value)
            prop == "w" -> layer.copy(w = value.coerceAtLeast(4f))
            prop == "h" -> layer.copy(h = value.coerceAtLeast(4f))
            prop == "scaleX" -> layer.copy(scaleX = value)
            prop == "scaleY" -> layer.copy(scaleY = value)
            prop == "scaleZ" -> layer.copy(scaleZ = value)
            prop == "rotation" -> layer.copy(rotation = value)
            prop == "rotX" -> layer.copy(rotX = value)
            prop == "rotY" -> layer.copy(rotY = value)
            prop == "rotZ" -> layer.copy(rotZ = value)
            prop == "skewX" -> layer.copy(skewX = value)
            prop == "skewY" -> layer.copy(skewY = value)
            prop == "opacity" -> layer.copy(opacity = value.coerceIn(0f, 1f))
            prop == "radius" -> layer.copy(radius = value.coerceAtLeast(0f))
            prop == "strokeWidth" -> layer.copy(strokeWidth = value.coerceAtLeast(0f))
            prop == "fov" -> layer.copy(fov = value.coerceIn(10f, 150f))
            prop == "focusDistance" -> layer.copy(focusDistance = value.coerceAtLeast(50f))
            prop == "aperture" -> layer.copy(aperture = value.coerceIn(1.2f, 22f))
            prop == "orbitAzimuth" -> layer.copy(orbitAzimuth = value)
            prop == "orbitElevation" -> layer.copy(orbitElevation = value.coerceIn(-85f, 85f))
            prop == "orbitDistance" -> layer.copy(orbitDistance = value.coerceIn(200f, 5000f))
            prop.startsWith("effect.") -> {
                val parts = prop.split(".")
                if (parts.size == 3) {
                    val updatedEffects = layer.effects.map { fx ->
                        if (fx.id == parts[1]) fx.copy(params = fx.params + (parts[2] to value)) else fx
                    }
                    layer.copy(effects = updatedEffects)
                } else layer
            }
            else -> layer
        }
    }

    fun evaluateProperty(layer: LayerData, prop: String, globalTime: Float): Float {
        val keys = layer.keyframes[prop]?.sortedBy { it.time }
        if (keys.isNullOrEmpty()) return basePropertyValue(layer, prop)
        val localTime = (globalTime - layer.start).coerceAtLeast(0f)
        if (localTime <= keys.first().time) return keys.first().value
        if (localTime >= keys.last().time) return keys.last().value
        for (i in 0 until keys.size - 1) {
            val a = keys[i]
            val b = keys[i + 1]
            if (localTime >= a.time && localTime <= b.time) {
                val span = (b.time - a.time).coerceAtLeast(0.0001f)
                val u = ((localTime - a.time) / span).coerceIn(0f, 1f)
                val eased = EasingPresets.evaluate(a.easing, u)
                return a.value + (b.value - a.value) * eased
            }
        }
        return keys.last().value
    }

    fun toggleKeyframe(layer: LayerData, prop: String, globalTime: Float, currentValue: Float? = null): LayerData {
        val localTime = ((globalTime - layer.start).coerceIn(0f, layer.duration) * 100f).roundToInt() / 100f
        val existing = layer.keyframes[prop].orEmpty().sortedBy { it.time }
        val matchIdx = existing.indexOfFirst { abs(it.time - localTime) < 0.04f }
        val updatedList = if (matchIdx >= 0) {
            existing.toMutableList().apply { removeAt(matchIdx) }
        } else {
            val v = currentValue ?: evaluateProperty(layer, prop, globalTime)
            (existing + Keyframe(time = localTime, value = v)).sortedBy { it.time }
        }
        val nextMap = layer.keyframes.toMutableMap()
        if (updatedList.isEmpty()) nextMap.remove(prop) else nextMap[prop] = updatedList
        return layer.copy(keyframes = nextMap)
    }

    fun setOrUpdateKeyframeIfAnimated(layer: LayerData, prop: String, globalTime: Float, value: Float): LayerData {
        val updatedBase = withPropertyValue(layer, prop, value)
        val existing = updatedBase.keyframes[prop]
        if (existing.isNullOrEmpty()) return updatedBase
        val localTime = ((globalTime - updatedBase.start).coerceIn(0f, updatedBase.duration) * 100f).roundToInt() / 100f
        val sorted = existing.sortedBy { it.time }.toMutableList()
        val idx = sorted.indexOfFirst { abs(it.time - localTime) < 0.04f }
        if (idx >= 0) {
            sorted[idx] = sorted[idx].copy(value = value)
        } else {
            sorted.add(Keyframe(time = localTime, value = value))
            sorted.sortBy { it.time }
        }
        return updatedBase.copy(keyframes = updatedBase.keyframes + (prop to sorted))
    }

    fun hasKeyframeAt(layer: LayerData, prop: String, globalTime: Float): Boolean {
        val localTime = (globalTime - layer.start).coerceAtLeast(0f)
        return layer.keyframes[prop].orEmpty().any { abs(it.time - localTime) < 0.04f }
    }

    fun resolveWorldTransform(
        layer: LayerData,
        allLayers: List<LayerData>,
        globalTime: Float,
        visited: MutableSet<String> = mutableSetOf()
    ): EvaluatedTransform {
        var lx = evaluateProperty(layer, "x", globalTime)
        var ly = evaluateProperty(layer, "y", globalTime)
        val lz = evaluateProperty(layer, "z", globalTime)
        var lsx = evaluateProperty(layer, "scaleX", globalTime)
        var lsy = evaluateProperty(layer, "scaleY", globalTime)
        var lrot = evaluateProperty(layer, "rotation", globalTime)
        val lrx = evaluateProperty(layer, "rotX", globalTime)
        val lry = evaluateProperty(layer, "rotY", globalTime)
        val lrz = evaluateProperty(layer, "rotZ", globalTime)
        var lop = evaluateProperty(layer, "opacity", globalTime)

        // Procedural motion effects ("swing", "pulse_scale", "auto_shake", "spin_auto")
        val localT = (globalTime - layer.start).coerceAtLeast(0f)
        for (fx in layer.effects) {
            if (!fx.enabled) continue
            when (fx.specId) {
                "swing" -> {
                    val freq = fx.params["frequency"] ?: 1.2f
                    val ang = fx.params["angle"] ?: 12f
                    val amp = fx.params["amplitude"] ?: 18f
                    val wave = sin(localT * freq * 2f * PI.toFloat())
                    lrot += wave * ang
                    ly += cos(localT * freq * 2f * PI.toFloat()) * amp
                }
                "pulse_scale" -> {
                    val spd = fx.params["speed"] ?: 2f
                    val amt = fx.params["amount"] ?: 0.14f
                    val mul = 1f + sin(localT * spd * 2f * PI.toFloat()) * amt
                    lsx *= mul
                    lsy *= mul
                }
                "auto_shake" -> {
                    val mag = fx.params["magnitude"] ?: 24f
                    val freq = fx.params["frequency"] ?: 8f
                    val rAmt = fx.params["rotAmount"] ?: 3f
                    val w = localT * freq * 2f * PI.toFloat()
                    lx += (sin(w * 1.13f) * 0.65f + cos(w * 2.31f) * 0.35f) * mag
                    ly += (cos(w * 1.37f) * 0.65f + sin(w * 1.93f) * 0.35f) * mag
                    lrot += sin(w * 1.71f) * rAmt
                }
                "spin_auto" -> {
                    val spd = fx.params["speed"] ?: 90f
                    lrot += localT * spd
                }
            }
        }

        val parentId = layer.parentId
        if (parentId != null && !visited.contains(layer.id)) {
            visited.add(layer.id)
            val parent = allLayers.find { it.id == parentId }
            if (parent != null && parent.type != LayerType.CAMERA) {
                val pt = resolveWorldTransform(parent, allLayers, globalTime, visited)
                val dx = (lx - parent.w * 0.5f) * pt.scaleX
                val dy = (ly - parent.h * 0.5f) * pt.scaleY
                val rad = Math.toRadians(pt.rotation.toDouble())
                val cosR = cos(rad).toFloat()
                val sinR = sin(rad).toFloat()
                lx = pt.x + (dx * cosR - dy * sinR)
                ly = pt.y + (dx * sinR + dy * cosR)
                lsx *= pt.scaleX
                lsy *= pt.scaleY
                lrot += pt.rotation
                lop *= pt.opacity
            }
        }

        return EvaluatedTransform(
            x = lx,
            y = ly,
            z = lz,
            scaleX = lsx,
            scaleY = lsy,
            rotation = lrot,
            rotX = lrx,
            rotY = lry,
            rotZ = lrz,
            opacity = lop.coerceIn(0f, 1f)
        )
    }

    fun activeCamera(allLayers: List<LayerData>, globalTime: Float): LayerData? {
        return allLayers.lastOrNull {
            it.type == LayerType.CAMERA &&
                !it.hidden &&
                globalTime >= it.start &&
                globalTime <= it.start + it.duration
        }
    }

    fun evaluateActiveCamera(
        allLayers: List<LayerData>,
        compWidth: Float,
        compHeight: Float,
        globalTime: Float
    ): EvaluatedCamera? {
        val cam = activeCamera(allLayers, globalTime) ?: return null
        var cx = evaluateProperty(cam, "x", globalTime)
        var cy = evaluateProperty(cam, "y", globalTime)
        var cz = evaluateProperty(cam, "z", globalTime)
        var rx = evaluateProperty(cam, "rotX", globalTime)
        var ry = evaluateProperty(cam, "rotY", globalTime)
        val rz = evaluateProperty(cam, "rotZ", globalTime)
        var fov = evaluateProperty(cam, "fov", globalTime)

        var focalMult = 1f
        when (cam.lensMode) {
            CameraLensMode.PRIME -> {
                fov = 46f
                focalMult = 1.08f
            }
            CameraLensMode.OPTICAL_ZOOM -> {
                focalMult = (55f / fov.coerceIn(15f, 130f)).coerceIn(0.45f, 3.2f)
            }
            CameraLensMode.DIGITAL -> {
                focalMult = (55f / fov.coerceIn(15f, 130f)).coerceIn(0.45f, 4f) * 1.15f
            }
            CameraLensMode.FREE -> Unit
        }

        if (cam.orbitEnabled) {
            val az = Math.toRadians(evaluateProperty(cam, "orbitAzimuth", globalTime).toDouble())
            val el = Math.toRadians(evaluateProperty(cam, "orbitElevation", globalTime).toDouble())
            val dist = evaluateProperty(cam, "orbitDistance", globalTime).coerceAtLeast(200f)
            val targetLayer = cam.orbitTargetId?.let { tid -> allLayers.find { it.id == tid } }
            val targetTr = targetLayer?.let { resolveWorldTransform(it, allLayers, globalTime) }
            val tx = (targetTr?.x ?: (compWidth * 0.5f)) - compWidth * 0.5f
            val ty = (targetTr?.y ?: (compHeight * 0.5f)) - compHeight * 0.5f
            val tz = targetTr?.z ?: 0f

            val horizDist = dist * cos(el).toFloat()
            cx = tx + horizDist * sin(az).toFloat()
            cy = ty - dist * sin(el).toFloat()
            cz = tz + horizDist * cos(az).toFloat()
            ry += evaluateProperty(cam, "orbitAzimuth", globalTime) * 0.35f
            rx += evaluateProperty(cam, "orbitElevation", globalTime) * 0.35f
        }

        // Camera shake effect on camera layer itself
        val localT = (globalTime - cam.start).coerceAtLeast(0f)
        cam.effects.filter { it.enabled && it.specId == "auto_shake" }.forEach { fx ->
            val mag = fx.params["magnitude"] ?: 24f
            val freq = fx.params["frequency"] ?: 8f
            val w = localT * freq * 2f * PI.toFloat()
            cx += sin(w * 1.19f) * mag
            cy += cos(w * 1.43f) * mag
        }

        return EvaluatedCamera(
            layer = cam,
            x = cx,
            y = cy,
            z = cz,
            rotX = rx,
            rotY = ry,
            rotZ = rz,
            fov = fov,
            focalMultiplier = focalMult,
            focusDistance = evaluateProperty(cam, "focusDistance", globalTime),
            aperture = evaluateProperty(cam, "aperture", globalTime),
            dofEnabled = cam.dofEnabled
        )
    }
}
