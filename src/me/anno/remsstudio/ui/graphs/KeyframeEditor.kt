package me.anno.remsstudio.ui.graphs

import me.anno.animation.Interpolation
import me.anno.language.translation.NameDesc
import me.anno.remsstudio.Selection
import me.anno.remsstudio.animation.Keyframe
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
class KeyframeEditor(style: Style) : PanelListY(style) {

    val controls = ScrollPanelX(style)
    val body = KeyframeEditorBody(this, style)

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

    val interpolationInput = EnumInput(
        // todo change the state automatically based on the selected keyframes
        NameDesc("Interpolation"), true, Interpolation.LINEAR_BOUNDED.nameDesc,
        Interpolation.entries.map { it.nameDesc }, style
    )

    init {
        this += controls
        body.alignmentX = AxisAlignment.FILL
        body.weight = 1f
        this += body
        val cc = controls.child as PanelList
        cc.padding.add(2)
        cc += TextPanel("Channel Mask: ", style).apply {
            textAlignmentY = AxisAlignment.CENTER
            tooltip = "Which channels are selectable/editable. Use this when you want to only change x for example."
        }
        // mask buttons for x,y,z,w
        for (mask in channelMasks) {
            mask.alignmentY = AxisAlignment.CENTER
            cc += mask
            cc += SpacerPanel(3, 1, style).makeBackgroundTransparent()
        }
        cc += interpolationInput.setChangeListener { _, index, _ ->
            val type = Interpolation.entries[index]
            for (kf in body.selectedKeyframes) {
                kf.interpolation = type
            }
        }
    }

    private var lastKeyframe: Keyframe<*>? = null
    override fun onUpdate() {
        super.onUpdate()
        val kf = body.selectedKeyframes.firstOrNull()
        val isVisible = kf != null
        if (controls.isVisible != isVisible || lastKeyframe != kf) {
            if (isVisible) {
                val title = kf.interpolation.nameDesc
                interpolationInput.setValue(title, false)
            }
            controls.isVisible = isVisible
            lastKeyframe = kf
        }
    }

    override val className get() = "GraphEditor"

}