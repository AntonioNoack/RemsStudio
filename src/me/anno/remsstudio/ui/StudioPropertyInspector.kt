package me.anno.remsstudio.ui

import me.anno.config.DefaultStyle
import me.anno.remsstudio.ui.MenuUtils.drawTypeInCorner
import me.anno.engine.inspector.Inspectable
import me.anno.ui.Style
import me.anno.ui.canvas.Canvas
import me.anno.ui.editor.PropertyInspector

class StudioPropertyInspector(getInspectables: () -> List<Inspectable>, style: Style) :
    PropertyInspector(getInspectables, style) {

    override val canDrawOverBorders: Boolean
        get() = true

    private val fontColor = style.getColor("textColor", DefaultStyle.fontGray)
    override fun drawBackground(canvas: Canvas, dx: Int, dy: Int) {
        super.drawBackground(canvas, dx, dy)
        drawTypeInCorner("Properties", fontColor)
    }
}