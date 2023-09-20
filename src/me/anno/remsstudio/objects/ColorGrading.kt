package me.anno.remsstudio.objects

import me.anno.remsstudio.animation.AnimatedProperty
import me.anno.studio.Inspectable
import me.anno.ui.Panel
import me.anno.ui.base.text.TextPanel
import me.anno.ui.editor.SettingCategory
import me.anno.ui.Style

object ColorGrading {

    fun createInspector(
        inspected: List<Inspectable>,
        c: List<Transform>,
        cgPower: List<AnimatedProperty<*>>,
        cgSaturation: List<AnimatedProperty<*>>,
        cgSlope: List<AnimatedProperty<*>>,
        cgOffsetAdd: List<AnimatedProperty<*>>,
        cgOffsetSub: List<AnimatedProperty<*>>,
        img: (Panel) -> Panel,
        getGroup: (name: String, ttt: String, id: String) -> SettingCategory,
        style: Style
    ) {

        val group = getGroup("Color Grading (ASC CDL)", "", "color-grading")
        group.addChild(
            img(TextPanel(
                "" +
                        "1. tint by slope\n" +
                        "2. add color with offset\n" +
                        "3. control the power\n" +
                        "4. (de)saturate", style
            ).apply {
                textColor = textColor and 0x77ffffff
                focusTextColor = textColor
            })
        )

        val t = c[0]
        val power = t.vis(
            inspected, c, "Power", "sRGB, Linear, ...", "cg.power",
            cgPower, style
        )
        val slope = t.vis(
            inspected, c, "Slope", "Intensity or Tint", "cg.slope",
            cgSlope, style
        )
        val offset1 = t.vis(
            inspected, c, "Plus Offset", "Can be used to color black objects", "cg.offset",
            cgOffsetAdd, style
        )
        val offset2 = t.vis(
            inspected, c, "Minus Offset", "Can be used to color white objects", "cg.offset.sub",
            cgOffsetSub, style
        )

        group.addChild(img(power))
        group.addChild(img(slope))
        group.addChild(img(offset1))
        group.addChild(img(offset2))

        val satDesc = "0 = gray scale, 1 = normal, -1 = inverted colors"
        group.addChild(img(t.vis(inspected, c, "Saturation", satDesc, "cg.saturation", cgSaturation, style)))

    }

}