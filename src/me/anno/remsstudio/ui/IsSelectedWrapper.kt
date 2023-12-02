package me.anno.remsstudio.ui

import me.anno.Time
import me.anno.gpu.drawing.DrawRectangles
import me.anno.maths.Maths.dtTo10
import me.anno.ui.Panel
import me.anno.ui.Style
import me.anno.ui.base.components.AxisAlignment
import me.anno.ui.base.components.Padding
import me.anno.ui.base.groups.PanelContainer
import me.anno.utils.Color
import me.anno.utils.Color.withAlpha
import me.anno.utils.types.Booleans.toInt

class IsSelectedWrapper(panel: Panel, val getIsSelected: () -> Boolean) :
    PanelContainer(panel, Padding(0), panel.style) {

    init {
        panel.alignmentX = AxisAlignment.FILL
        panel.alignmentY = AxisAlignment.FILL
    }

    private var color = getSelectionColor(style)
    private var strength = 0f

    private var isSelected = false
        set(value) {
            if (field != value) {
                field = value
                padding.set(value.toInt(1))
                strength = value.toInt().toFloat()
                invalidateLayout()
            }
        }

    override fun onUpdate() {
        super.onUpdate()
        isSelected = getIsSelected()
    }

    override fun onDraw(x0: Int, y0: Int, x1: Int, y1: Int) {
        super.onDraw(x0, y0, x1, y1)
        if (isSelected) {
            DrawRectangles.drawBorder(x, y, width, height, color.withAlpha(strength), padding.left)
            if (strength > 0.01f) invalidateDrawing()
            strength *= dtTo10(decaySpeed * Time.deltaTime).toFloat()
        }
    }

    companion object {
        val decaySpeed = 2.0
        fun getSelectionColor(style: Style): Int {
            return style.getColor("selectionColor", 0x44ccff or Color.black)
        }
    }
}