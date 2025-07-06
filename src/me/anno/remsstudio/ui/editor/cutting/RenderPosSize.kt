package me.anno.remsstudio.ui.editor.cutting

import me.anno.Time
import me.anno.maths.Maths.MILLIS_TO_NANOS
import kotlin.math.abs
import kotlin.math.max

class RenderPosSize {

    var renderWidth = 0
    var renderPos = 0
    private var lastChangeTime = 0L

    private fun isSameWidth(width: Int, currWidth: Int): Boolean {
        return abs(width - currWidth) < 16 + max(width, currWidth).shr(1)
    }

    private fun isSamePos(pos: Int, currPos: Int): Boolean {
        return abs(pos - currPos) < 64
    }

    private fun resize(width: Int, height: Int, time: Long) {
        renderWidth = width
        renderPos = height
        lastChangeTime = time
    }

    fun updateSize(width: Int, pos: Int): Boolean {
        if (width == renderWidth && pos == renderPos) return true
        val time = Time.nanoTime
        if (isSameWidth(width, renderWidth) && isSamePos(pos, renderPos)) {
            if (time - lastChangeTime > 500 * MILLIS_TO_NANOS) {
                resize(width, pos, time)
            }
        } else {
            resize(width, pos, time)
        }

        return width == renderWidth && pos == renderPos
    }
}