package com.editorshub.core.rendering.gl

import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/** GLSurfaceView renderer: GPU post-processing of the composited preview bitmap. */
class GlPreviewRenderer(
    private val bitmapProvider: () -> Bitmap?,
    private val uniformsProvider: () -> GlUniforms
) : GLSurfaceView.Renderer {

    private val program = GlProgram()

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        program.init()
        GLES20.glClearColor(0f, 0f, 0f, 1f)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        program.viewport(width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        val bitmap = bitmapProvider() ?: return
        program.draw(bitmap, uniformsProvider())
    }

    fun release() = program.release()
}
