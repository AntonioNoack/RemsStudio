package me.anno.remsstudio.ui.input

import me.anno.language.translation.NameDesc
import me.anno.remsstudio.RemsStudio
import me.anno.remsstudio.Selection
import me.anno.remsstudio.animation.AnimatedProperty
import me.anno.ui.Style
import me.anno.ui.input.ColorInput
import org.joml.Vector4f

class ColorInputV2(
    title: NameDesc, visibilityKey: String,
    oldValue: Vector4f, withAlpha: Boolean, val property: AnimatedProperty<*>, style: Style
) : ColorInput(title, visibilityKey, oldValue, withAlpha, style, ColorChooserV2(style, withAlpha, property)) {
    override fun draw(x0: Int, y0: Int, x1: Int, y1: Int) {
        if (RemsStudio.hideUnusedProperties) {
            val focused1 = titleView.isAnyChildInFocus || base.isAnyChildInFocus
            val focused2 = focused1 || property in Selection.selectedProperties
            base.isVisible = focused2
        }
        super.draw(x0, y0, x1, y1)
    }
}