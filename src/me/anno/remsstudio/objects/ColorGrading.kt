package me.anno.remsstudio.objects

import me.anno.language.translation.NameDesc
import me.anno.remsstudio.animation.AnimatedProperty
import me.anno.ui.Panel
import me.anno.ui.Style
import me.anno.ui.base.text.TextPanel
import me.anno.ui.editor.SettingCategory
import me.anno.utils.Color.withAlpha

object ColorGrading {
    fun createInspector(
        c: List<Transform>,
        cgPower: List<AnimatedProperty<*>>,
        cgSaturation: List<AnimatedProperty<*>>,
        cgSlope: List<AnimatedProperty<*>>,
        cgOffsetAdd: List<AnimatedProperty<*>>,
        cgOffsetSub: List<AnimatedProperty<*>>,
        img: (Panel) -> Panel,
        getGroup: (NameDesc) -> SettingCategory,
        style: Style
    ) {

        val group = getGroup(NameDesc("Color Grading (ASC CDL)", "", "obj.color-grading"))
        group.addChild(
            img(TextPanel(
                "" +
                        "1. tint by slope\n" +
                        "2. add color with offset\n" +
                        "3. control the power\n" +
                        "4. (de)saturate", style
            ).apply {
                textColor = textColor.withAlpha(0.5f)
                focusTextColor = textColor
            })
        )

        val t = c[0]
        val power = t.vis(
            c, "Power", "sRGB, Linear, ...",
            "cg.power", cgPower, style
        )
        val slope = t.vis(
            c, "Slope", "Intensity or Tint",
            "cg.slope", cgSlope, style
        )
        val offset1 = t.vis(
            c, "Plus Offset", "Can be used to color black objects",
            "cg.offset", cgOffsetAdd, style
        )
        val offset2 = t.vis(
            c, "Minus Offset", "Can be used to color white objects",
            "cg.offset.sub", cgOffsetSub, style
        )

        val sat = t.vis(
            c, "Saturation", "0 = gray scale, 1 = normal, -1 = inverted colors",
            "cg.saturation", cgSaturation, style
        )

        group.addChild(img(power))
        group.addChild(img(slope))
        group.addChild(img(offset1))
        group.addChild(img(offset2))
        group.addChild(img(sat))
    }
}