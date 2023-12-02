package me.anno.remsstudio.ui

import me.anno.gpu.drawing.DrawRectangles.drawBorder
import me.anno.remsstudio.animation.AnimatedProperty
import me.anno.remsstudio.ui.IsSelectedWrapper.Companion.getSelectionColor
import me.anno.ui.Panel
import me.anno.ui.base.components.AxisAlignment
import me.anno.ui.base.components.Padding
import me.anno.ui.base.groups.PanelContainer
import me.anno.utils.types.Booleans.toInt

class IsAnimatedWrapper(panel: Panel, val values: AnimatedProperty<*>) :
    PanelContainer(panel, Padding(0), panel.style) {

    private var isAnimated = false
        set(value) {
            if (field != value) {
                field = value
                padding.set(value.toInt())
                invalidateLayout()
            }
        }

    override fun onUpdate() {
        super.onUpdate()
        isAnimated = values.isAnimated
    }

    private val color = getSelectionColor(style)
    override fun onDraw(x0: Int, y0: Int, x1: Int, y1: Int) {
        super.onDraw(x0, y0, x1, y1)
        if (isAnimated) {
            drawBorder(x, y, width, height, color, padding.left)
        }
    }

    init {
        alignmentX = AxisAlignment.FILL
    }
}