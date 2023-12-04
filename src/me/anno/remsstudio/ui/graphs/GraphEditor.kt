package me.anno.remsstudio.ui.graphs

import me.anno.animation.Interpolation
import me.anno.remsstudio.Selection
import me.anno.ui.Style
import me.anno.ui.base.SpacerPanel
import me.anno.ui.base.buttons.TextButton
import me.anno.ui.base.components.AxisAlignment
import me.anno.ui.base.groups.PanelList
import me.anno.ui.base.groups.PanelListY
import me.anno.ui.base.scrolling.ScrollPanelX
import me.anno.ui.base.text.TextPanel
import me.anno.ui.input.components.Checkbox
import me.anno.utils.Color.black

class GraphEditor(style: Style) : PanelListY(style) {

    val controls = ScrollPanelX(style)
    val body = GraphEditorBody(this, style)

    val font = style.getFont("text")

    class MaskCheckbox(private val maskColor: Int, val index: Int, size: Int, style: Style) :
        Checkbox(true, true, size, style) {
        override fun getColor(): Int = maskColor
        override var isVisible: Boolean
            get() {
                // hide them, when a channel isn't available
                val property = Selection.selectedProperties?.firstOrNull()
                return property != null && index < property.type.components
            }
            set(value) {}
    }

    val channelMasks = Array(4) { index ->
        val color = when (index) {
            0 -> 0xff6666
            1 -> 0x55ff55
            2 -> 0x7777ff
            else -> -1
        } or black
        // todo these are not centered... why???
        // todo these cannot be toggled when small :/
        MaskCheckbox(color, index, font.sizeInt - 2, style)
    }

    init {
        this += controls
        body.alignmentX = AxisAlignment.FILL
        body.weight = 1f
        this += body
        val cc = controls.child as PanelList
        cc += TextPanel("Channel Mask: ", style)
        // mask buttons for x,y,z,w
        for (mask in channelMasks) {
            mask.alignmentY = AxisAlignment.CENTER
            cc += mask
        }
        cc += SpacerPanel(4, 1, style)
        // todo enum input, but change the state automatically based on the selected keyframes
        cc += TextPanel("Interpolation: ", style)
        for (type in Interpolation.values()) {
            cc += TextButton(type.symbol, true, style).apply {
                padding.left = 2
                padding.right = 2
                padding.top = 0
                padding.bottom = 0
            }
                .setTooltip(if (type.description.isEmpty()) type.displayName else "${type.displayName}: ${type.description}")
                .addLeftClickListener {
                    for (kf in body.selectedKeyframes) {
                        kf.interpolation = type
                    }
                    body.invalidateDrawing()
                }
        }
    }

    override fun onUpdate() {
        super.onUpdate()
        controls.isVisible = body.selectedKeyframes.isNotEmpty()
    }

    override val className get() = "GraphEditor"

}