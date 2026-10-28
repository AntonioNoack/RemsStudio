package me.anno.remsstudio.ui.editor.cutting

import me.anno.gpu.drawing.DrawGradients
import me.anno.remsstudio.objects.video.Video
import me.anno.remsstudio.objects.video.VideoPreview.getFrameAtLocalTimeForPreview
import me.anno.ui.Panel
import me.anno.ui.Style
import me.anno.ui.canvas.Canvas
import me.anno.utils.Color.white4
import org.joml.Vector4f

// todo why is this flickering, when moving the mouse???...
@Suppress("MemberVisibilityCanBePrivate")
class VideoPreviewPanel(
    val video: Video,
    val constMinH: Int, style: Style,
    val getTime: (x: Float) -> Double
) : Panel(style) {

    val constMinW = constMinH * video.lastW / video.lastH

    init {
        background.color = 0xff777777.toInt()
    }

    override val onMovementHideTooltip get() = false

    override fun calculateSize(w: Int, h: Int) {
        minW = constMinW
        minH = constMinH
    }

    override fun draw(canvas: Canvas) {
        val meta = video.meta ?: return
        val window = window ?: return
        val time = getTime(window.mouseX)
        val frame = video.getFrameAtLocalTimeForPreview(time, constMinW, meta) ?: return
        val color = white4
        DrawGradients.drawRectGradient(
            canvas.x0, canvas.y0, canvas.dx, canvas.dy,
            color, color, frame,
            Vector4f(0f, 0f, 1f, 1f)
        )
    }
}