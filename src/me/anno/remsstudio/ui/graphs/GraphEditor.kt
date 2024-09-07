package me.anno.remsstudio.ui.graphs

import me.anno.animation.Interpolation
import me.anno.language.translation.NameDesc
import me.anno.remsstudio.Selection
import me.anno.ui.Style
import me.anno.ui.base.SpacerPanel
import me.anno.ui.base.components.AxisAlignment
import me.anno.ui.base.groups.PanelList
import me.anno.ui.base.groups.PanelListY
import me.anno.ui.base.scrolling.ScrollPanelX
import me.anno.ui.base.text.TextPanel
import me.anno.ui.input.EnumInput
import me.anno.ui.input.components.Checkbox
import me.anno.utils.Color.black

@Suppress("MemberVisibilityCanBePrivate")
class GraphEditor(style: Style) : PanelListY(style) {

    val controls = ScrollPanelX(style)
    val body = GraphEditorBody(this, style)

    val font = style.getFont("text")

    class MaskCheckbox(private val maskColor: Int, val index: Int, size: Int, style: Style) :
        Checkbox(true, true, size, style) {
        override fun getColor(): Int = maskColor

        @Suppress("UNUSED_PARAMETER")
        override var isEnabled: Boolean
            get() {
                // hide them, when a channel isn't available
                val property = Selection.selectedProperties.firstOrNull()
                return property != null && index < property.type.numComponents
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
        cc.padding.add(2)
        cc += TextPanel("Channel Mask: ", style).apply {
            textAlignmentY = AxisAlignment.CENTER
        }
        // mask buttons for x,y,z,w
        for (mask in channelMasks) {
            mask.alignmentY = AxisAlignment.CENTER
            cc += mask
            cc += SpacerPanel(3, 1, style).makeBackgroundTransparent()
        }
        cc += EnumInput(
            // todo change the state automatically based on the selected keyframes
            NameDesc("Interpolation"), true, Interpolation.LINEAR_BOUNDED.nameDesc,
            Interpolation.entries.map { it.nameDesc }, style
        ).setChangeListener { _, index, _ ->
            val type = Interpolation.entries[index]
            for (kf in body.selectedKeyframes) {
                kf.interpolation = type
            }
            body.invalidateDrawing()
        }
    }

    override fun onUpdate() {
        super.onUpdate()
        controls.isVisible = body.selectedKeyframes.isNotEmpty()
    }

    override val className get() = "GraphEditor"

}