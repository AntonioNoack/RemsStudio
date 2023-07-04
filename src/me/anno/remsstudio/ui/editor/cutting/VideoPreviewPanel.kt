package me.anno.remsstudio.ui.editor.cutting

import me.anno.gpu.drawing.DrawGradients
import me.anno.remsstudio.objects.Video
import me.anno.ui.Panel
import me.anno.ui.style.Style
import org.joml.Vector4f

// todo why is this flickering, when moving the mouse???...
class VideoPreviewPanel(
    val video: Video,
    val height1: Int, style: Style,
    val getTime: (x: Float) -> Double
) : Panel(style) {

    val width1 = height1 * video.lastW / video.lastH

    init {
        backgroundColor = 0xff777777.toInt()
    }

    override val onMovementHideTooltip get() = false

    override fun calculateSize(w: Int, h: Int) {
        minW = width1
        minH = height1
    }

    override fun onDraw(x0: Int, y0: Int, x1: Int, y1: Int) {
        val meta = video.meta ?: return
        val window = window!!
        val time = getTime(window.mouseX)
        val frame = video.getFrameAtLocalTime(time, width1, meta)
        if (frame != null) {
            DrawGradients.drawRectGradient(
                x0, y0, x1 - x0, y1 - y0, color, color, frame,
                Vector4f(0f, 0f, 1f, 1f)
            )
        }
    }

    companion object {
        private val color = Vector4f(1f)
    }

}