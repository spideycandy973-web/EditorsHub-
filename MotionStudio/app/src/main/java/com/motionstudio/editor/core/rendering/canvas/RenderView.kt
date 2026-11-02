package com.motionstudio.editor.core.rendering.canvas

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import com.motionstudio.editor.core.rendering.RenderFrame

/** Hardware-accelerated view that draws the real composition on every invalidation. */
class RenderView(
    context: Context,
    private val compositor: CanvasCompositor
) : View(context) {
    private var frame: RenderFrame? = null
    init { setLayerType(LAYER_TYPE_HARDWARE, null) }

    fun update(frame: RenderFrame) {
        this.frame = frame
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val f = frame
        if (f == null) { canvas.drawColor(Color.BLACK); return }
        compositor.draw(canvas, f, width, height)
    }
}
