package com.motionstudio.editor.core.rendering.gl

import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/** Aggregated, GPU-side post-processing uniforms derived from the effect stack. */
data class GlUniforms(
    val blur: Float = 0f,
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 1f,
    val hue: Float = 0f,
    val exposure: Float = 0f,
    val vignette: Float = 0f,
    val rgbSplit: Float = 0f,
    val pixelate: Float = 0f,
    val wave: Float = 0f,
    val noise: Float = 0f,
    val time: Float = 0f
)

object EffectUniformBuilder {
    fun from(effects: List<com.motionstudio.editor.core.rendering.ResolvedEffect>, timeMs: Long): GlUniforms {
        var u = GlUniforms(time = timeMs / 1000f)
        for (e in effects) {
            val p = e.params
            when (e.effect.type) {
                com.motionstudio.editor.core.domain.model.EffectType.GAUSSIAN_BLUR ->
                    u = u.copy(blur = maxOf(u.blur, p["amount"] ?: 0f))
                com.motionstudio.editor.core.domain.model.EffectType.BRIGHTNESS ->
                    u = u.copy(brightness = u.brightness + (p["amount"] ?: 0f))
                com.motionstudio.editor.core.domain.model.EffectType.CONTRAST ->
                    u = u.copy(contrast = u.contrast + (p["amount"] ?: 0f))
                com.motionstudio.editor.core.domain.model.EffectType.SATURATION ->
                    u = u.copy(saturation = u.saturation * (p["amount"] ?: 1f))
                com.motionstudio.editor.core.domain.model.EffectType.HUE ->
                    u = u.copy(hue = u.hue + (p["amount"] ?: 0f))
                com.motionstudio.editor.core.domain.model.EffectType.EXPOSURE ->
                    u = u.copy(exposure = u.exposure + (p["amount"] ?: 0f))
                com.motionstudio.editor.core.domain.model.EffectType.VIGNETTE ->
                    u = u.copy(vignette = maxOf(u.vignette, p["amount"] ?: 0f))
                com.motionstudio.editor.core.domain.model.EffectType.RGB_SPLIT,
                com.motionstudio.editor.core.domain.model.EffectType.CHROMATIC_ABERRATION ->
                    u = u.copy(rgbSplit = u.rgbSplit + (p["amount"] ?: 0f))
                com.motionstudio.editor.core.domain.model.EffectType.PIXELATION ->
                    u = u.copy(pixelate = maxOf(u.pixelate, p["amount"] ?: 0f))
                com.motionstudio.editor.core.domain.model.EffectType.WAVE_DISTORTION ->
                    u = u.copy(wave = maxOf(u.wave, p["amount"] ?: 0f))
                com.motionstudio.editor.core.domain.model.EffectType.NOISE ->
                    u = u.copy(noise = maxOf(u.noise, p["amount"] ?: 0f))
                else -> Unit
            }
        }
        return u
    }
}

/** OpenGL ES 2.0 shader chain used for GPU-accelerated preview and export. */
class GlProgram {
    private var program = 0
    private var textureId = 0
    private var positionHandle = 0
    private var texHandle = 0
    private var textureHandle = 0
    private var width = 1
    private var height = 1
    private val vertices: FloatBuffer

    init {
        // x, y, u, v  (quad as a triangle strip)
        val data = floatArrayOf(
            -1f, -1f, 0f, 1f,
            1f, -1f, 1f, 1f,
            -1f, 1f, 0f, 0f,
            1f, 1f, 1f, 0f
        )
        vertices = ByteBuffer.allocateDirect(data.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
        vertices.put(data).position(0)
    }

    fun init() {
        val vs = compile(GLES20.GL_VERTEX_SHADER, VERTEX)
        val fs = compile(GLES20.GL_FRAGMENT_SHADER, FRAGMENT)
        program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vs)
        GLES20.glAttachShader(program, fs)
        GLES20.glLinkProgram(program)
        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            throw IllegalStateException("GL link failed: ${GLES20.glGetProgramInfoLog(program)}")
        }
        GLES20.glDeleteShader(vs)
        GLES20.glDeleteShader(fs)
        positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        texHandle = GLES20.glGetAttribLocation(program, "aTexCoord")
        textureHandle = GLES20.glGetUniformLocation(program, "uTex")

        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        textureId = textures[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
    }

    fun viewport(w: Int, h: Int) {
        width = w; height = h
        GLES20.glViewport(0, 0, w, h)
    }

    fun draw(bitmap: Bitmap, u: GlUniforms) {
        if (program == 0) return
        GLES20.glUseProgram(program)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        GLES20.glUniform1i(textureHandle, 0)

        vertices.position(0)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 16, vertices)
        vertices.position(2)
        GLES20.glEnableVertexAttribArray(texHandle)
        GLES20.glVertexAttribPointer(texHandle, 2, GLES20.GL_FLOAT, false, 16, vertices)

        uniform2f("uResolution", width.toFloat(), height.toFloat())
        uniform1f("uTime", u.time)
        uniform1f("uBlur", u.blur)
        uniform1f("uBrightness", u.brightness)
        uniform1f("uContrast", u.contrast)
        uniform1f("uSaturation", u.saturation)
        uniform1f("uHue", u.hue)
        uniform1f("uExposure", u.exposure)
        uniform1f("uVignette", u.vignette)
        uniform1f("uRgbSplit", u.rgbSplit)
        uniform1f("uPixelate", u.pixelate)
        uniform1f("uWave", u.wave)
        uniform1f("uNoise", u.noise)

        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(texHandle)
    }

    private fun uniform1f(name: String, value: Float) =
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, name), value)

    private fun uniform2f(name: String, x: Float, y: Float) =
        GLES20.glUniform2f(GLES20.glGetUniformLocation(program, name), x, y)

    fun release() {
        if (textureId != 0) { GLES20.glDeleteTextures(1, intArrayOf(textureId), 0); textureId = 0 }
        if (program != 0) { GLES20.glDeleteProgram(program); program = 0 }
    }

    private fun compile(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) throw IllegalStateException("GL compile failed: ${GLES20.glGetShaderInfoLog(shader)}")
        return shader
    }

    companion object {
        private const val VERTEX = """
attribute vec2 aPosition;
attribute vec2 aTexCoord;
varying vec2 vTex;
void main() {
    gl_Position = vec4(aPosition, 0.0, 1.0);
    vTex = aTexCoord;
}
"""

        private const val FRAGMENT = """
precision mediump float;
varying vec2 vTex;
uniform sampler2D uTex;
uniform vec2 uResolution;
uniform float uTime, uBlur, uBrightness, uContrast, uSaturation, uHue, uExposure;
uniform float uVignette, uRgbSplit, uPixelate, uWave, uNoise;

vec3 rgb2hsv(vec3 c) {
    vec4 K = vec4(0.0, -1.0/3.0, 2.0/3.0, -1.0);
    vec4 p = mix(vec4(c.bg, K.wz), vec4(c.gb, K.xy), step(c.b, c.g));
    vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));
    float d = q.x - min(q.w, q.y);
    float e = 1.0e-10;
    return vec3(abs(q.z + (q.w - q.y) / (6.0 * d + e)), d / (q.x + e), q.x);
}
vec3 hsv2rgb(vec3 c) {
    vec4 K = vec4(1.0, 2.0/3.0, 1.0/3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}
float rand(vec2 co) { return fract(sin(dot(co, vec2(12.9898, 78.233))) * 43758.5453); }

void main() {
    vec2 uv = vTex;
    if (uPixelate > 0.0) {
        float blocks = max(2.0, uResolution.x / max(2.0, uPixelate));
        uv = (floor(uv * blocks) + 0.5) / blocks;
    }
    if (uWave > 0.0) uv.x += sin(uv.y * 20.0 + uTime * 3.0) * uWave / uResolution.x;
    float split = uRgbSplit / uResolution.x;
    vec3 col;
    if (uRgbSplit > 0.0) {
        col.r = texture2D(uTex, clamp(uv + vec2(split, 0.0), 0.0, 1.0)).r;
        col.g = texture2D(uTex, uv).g;
        col.b = texture2D(uTex, clamp(uv - vec2(split, 0.0), 0.0, 1.0)).b;
    } else {
        col = texture2D(uTex, uv).rgb;
    }
    if (uBlur > 0.0) {
        vec2 px = vec2(uBlur / uResolution.x, uBlur / uResolution.y);
        vec3 sum = vec3(0.0);
        for (int i = -2; i <= 2; i++) {
            for (int j = -2; j <= 2; j++) {
                sum += texture2D(uTex, clamp(uv + vec2(float(i) * px.x, float(j) * px.y), 0.0, 1.0)).rgb;
            }
        }
        col = mix(col, sum / 25.0, 0.65);
    }
    col *= pow(2.0, uExposure);
    col += uBrightness;
    col = (col - 0.5) * (1.0 + uContrast) + 0.5;
    float l = dot(col, vec3(0.2126, 0.7152, 0.0722));
    col = mix(vec3(l), col, uSaturation);
    if (abs(uHue) > 0.001) {
        vec3 hsv = rgb2hsv(clamp(col, 0.0, 1.0));
        hsv.x = fract(hsv.x + uHue / 360.0);
        col = hsv2rgb(hsv);
    }
    if (uVignette > 0.0) {
        vec2 d = uv - 0.5;
        col *= clamp(1.0 - dot(d, d) * 2.0 * uVignette, 0.0, 1.0);
    }
    if (uNoise > 0.0) col += (rand(uv * uResolution + uTime) - 0.5) * uNoise * 0.4;
    float a = texture2D(uTex, uv).a;
    gl_FragColor = vec4(clamp(col, 0.0, 1.0), a);
}
"""
    }
}
