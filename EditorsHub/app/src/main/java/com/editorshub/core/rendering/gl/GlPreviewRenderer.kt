package com.editorshub.core.rendering.gl

import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.Log
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * GLSurfaceView renderer: GPU post-processing of the composited preview bitmap.
 *
 * The GL thread is not the main thread, but an uncaught exception there still
 * terminates the process. Every GL callback is therefore guarded: a shader or
 * texture failure results in a black frame rather than an app crash.
 */
class GlPreviewRenderer(
    private val bitmapProvider: () -> Bitmap?,
    private val uniformsProvider: () -> GlUniforms
) : GLSurfaceView.Renderer {

    private val program = GlProgram()
    @Volatile private var ready = false

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        ready = try {
            program.init()
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            true
        } catch (e: Throwable) {
            Log.w(TAG, "OpenGL preview initialisation failed; showing a plain frame", e)
            false
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        if (!ready || width <= 0 || height <= 0) return
        try { program.viewport(width, height) }
        catch (e: Throwable) { Log.w(TAG, "Viewport update failed", e) }
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        if (!ready) return
        try {
            val bitmap = bitmapProvider() ?: return
            if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) return
            program.draw(bitmap, uniformsProvider())
        } catch (e: Throwable) {
            Log.w(TAG, "OpenGL preview frame skipped", e)
        }
    }

    fun release() = try { program.release() } catch (e: Throwable) {
        Log.w(TAG, "OpenGL release failed", e)
    }

    private companion object { const val TAG = "EditorsHubGL" }
}
