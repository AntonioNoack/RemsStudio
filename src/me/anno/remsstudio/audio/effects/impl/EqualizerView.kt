package me.anno.remsstudio.audio.effects.impl

import me.anno.gpu.Cursor
import me.anno.gpu.drawing.DrawCurves.drawLine
import me.anno.gpu.drawing.DrawRectangles
import me.anno.gpu.drawing.DrawRectangles.drawRect
import me.anno.gpu.drawing.DrawTexts.drawSimpleTextCharByChar
import me.anno.gpu.drawing.GFXx2D.drawCircle
import me.anno.input.Input
import me.anno.input.Key
import me.anno.remsstudio.RemsStudio.editorTime
import me.anno.remsstudio.audio.effects.impl.EqualizerEffect.Companion.frequencies
import me.anno.remsstudio.ui.editor.TimelinePanel.Companion.global2Kf
import me.anno.ui.Panel
import me.anno.ui.Style
import me.anno.ui.UIColors
import me.anno.ui.base.components.AxisAlignment
import me.anno.ui.base.scrolling.ScrollPanelXY.Companion.drawShadowX
import me.anno.ui.base.scrolling.ScrollPanelXY.Companion.drawShadowY
import me.anno.utils.Color.black
import me.anno.utils.Color.white
import me.anno.utils.Color.withAlpha
import me.anno.utils.types.Booleans.hasFlag
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

class EqualizerView(val self: EqualizerEffect, style: Style) : Panel(style) {

    override fun calculateSize(w: Int, h: Int) {
        super.calculateSize(w, h)
        minW = 16 * (frequencies.size + 1)
        minH = 16 * 12
    }

    override val canDrawOverBorders: Boolean get() = true

    private var draggedIndex = -1
    private var hoveredIndex = -1

    private val unitX: Int get() = width / (frequencies.size + 1)
    private val unitY: Int get() = height / 12

    private fun getIndexAtX(x: Float): Int {
        return floor((x - this.x) / unitX - 1.25f).toInt()
    }

    private fun getYAtY(y: Float): Float {
        return 1f - (y - this.y) / height
    }

    override fun onKeyDown(x: Float, y: Float, key: Key) {
        if (key == Key.BUTTON_LEFT) {
            draggedIndex = getIndexAtX(x)
            hoveredIndex = draggedIndex
        } else super.onKeyDown(x, y, key)
    }

    override fun onKeyUp(x: Float, y: Float, key: Key) {
        if (key == Key.BUTTON_LEFT) {
            draggedIndex = -1
        } else super.onKeyUp(x, y, key)
    }

    override fun onMouseMoved(x: Float, y: Float, dx: Float, dy: Float) {
        if (Input.isLeftDown) {
            val time = global2Kf(editorTime)
            val yf = getYAtY(y)
            self.sliders.getOrNull(draggedIndex)?.addKeyframe(time, yf)
        } else super.onMouseMoved(x, y, dx, dy)
    }

    override fun onMouseClicked(x: Float, y: Float, button: Key, long: Boolean) {
        if (Input.isLeftDown) {
            // consume event
        } else super.onMouseClicked(x, y, button, long)
    }

    override fun onUpdate() {
        hoveredIndex = getIndexAtX(window?.mouseX ?: 0f)
        super.onUpdate()
    }

    override fun getCursor(): Cursor? {
        return if (hoveredIndex in frequencies.indices) Cursor.hand
        else null
    }

    override fun onDraw(x0: Int, y0: Int, x1: Int, y1: Int) {
        super.onDraw(x0, y0, x1, y1)

        // draw axes with legend (+/- 12dB)
        val numLines = 24
        val color = white
        val unitX = unitX
        val unitY = unitY
        val x0l = max(x0, x + (unitX * 1.25f).toInt())
        val x1l = min(x1, x + width - 2)
        for (i in 1 until numLines) {
            val alpha = if (i == 12) 93 else if (i % 5 == 2) 43 else 20
            val y = y + (height - 1) * i / numLines
            drawRect(x0l, y, max(x1l - x0l, 0), 1, color.withAlpha(alpha))
        }

        val bg0 = backgroundColor.withAlpha(0)

        val time = global2Kf(editorTime)
        for (i in 1 until frequencies.size) {
            drawLine(
                x + (unitX * (i + 0.75f)), y + (height * (1f - self.sliders[i - 1][time])),
                x + (unitX * (i + 1.75f)), y + (height * (1f - self.sliders[i][time])),
                1f, color.withAlpha(127), backgroundColor, false, 1f
            )
        }

        // draw draggable balls
        val radius = min(unitX, unitY) * 0.5f
        val isDragged = draggedIndex in frequencies.indices
        val colorI = if (isDragged) UIColors.midOrange else white.withAlpha(63)
        val relevantIndex = if (isDragged) draggedIndex else hoveredIndex
        for (i in frequencies.indices) {
            val circleX = x + (unitX * (i + 1.75f)).toInt()
            val circleY = y + (height * (1f - self.sliders[i][time])).toInt()
            drawRect(circleX, y0 + 2, 1, max(y1 - y0 - 4, 0), color.withAlpha(if (i.hasFlag(1)) 93 else 43))
            val radiusI = if (i == relevantIndex) radius + 2f else radius
            drawCircle(
                circleX, circleY, radiusI, radiusI, radius / radiusI,
                color, colorI, bg0, 1f
            )
        }

        fun drawLegendYText(yi: Int, text: String) {
            drawSimpleTextCharByChar(
                x + (unitX * 1.25f * 0.5f).toInt(), y + height.shr(1) - yi * (height - 1) / numLines, 2, text,
                color, bg0, AxisAlignment.CENTER, AxisAlignment.CENTER
            )
        }

        drawLegendYText(+10, "+10dB")
        drawLegendYText(+5, " +5dB")
        drawLegendYText(+0, " +0dB")
        drawLegendYText(-5, " -5dB")
        drawLegendYText(-10, "-10dB")

        fun drawLegendXText(xi: Int, text: String) {
            drawSimpleTextCharByChar(
                x + (unitX * (xi + 1.75f)).toInt(), y + unitY, 2, text,
                color, bg0, AxisAlignment.CENTER, AxisAlignment.CENTER
            )
        }

        drawLegendXText(1, "60Hz")
        drawLegendXText(3, "240Hz")
        drawLegendXText(5, "1kHz")
        drawLegendXText(7, "4kHz")
        drawLegendXText(9, "16kHz")

        val shadowColor = black.withAlpha(0.12f)
        val shadowRadius = 15
        val batch = DrawRectangles.startBatch()
        drawShadowX(x0, y0, x1, y1, shadowColor, shadowRadius)
        drawShadowY(x0, y0, x1, y1, shadowColor, shadowRadius)
        DrawRectangles.finishBatch(batch)

    }
}