package com.editorshub.core.rendering.gl

import android.graphics.Bitmap
import android.opengl.EGLSurface
import android.view.Surface

/** GPU write path: renders each composited frame through the shader chain into an encoder Surface. */
class GlFrameWriter(surface: Surface, width: Int, height: Int) {
    private val egl = EglCore()
    private var eglSurface: EGLSurface? = null
    private val program = GlProgram()

    init {
        egl.init()
        val s = egl.createWindowSurface(surface)
        check(s != android.opengl.EGL14.EGL_NO_SURFACE) { "eglCreateWindowSurface failed" }
        eglSurface = s
        egl.makeCurrent(s)
        program.init()
        program.viewport(width, height)
    }

    fun draw(bitmap: Bitmap, uniforms: GlUniforms, presentationTimeNs: Long) {
        program.draw(bitmap, uniforms)
        eglSurface?.let { surface ->
            egl.presentationTime(surface, presentationTimeNs.coerceAtLeast(0L))
            check(egl.swap(surface)) { "eglSwapBuffers failed while writing video frame" }
        }
    }

    fun release() {
        eglSurface?.let { egl.releaseSurface(it) }
        eglSurface = null
        program.release()
        egl.release()
    }
}
