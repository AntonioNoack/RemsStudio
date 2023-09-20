package me.anno.remsstudio.objects.text

import me.anno.remsstudio.RemsStudio
import me.anno.remsstudio.Selection
import me.anno.remsstudio.animation.AnimatedProperty
import me.anno.remsstudio.objects.Transform
import me.anno.studio.Inspectable
import me.anno.ui.base.buttons.TextButton
import me.anno.ui.base.groups.PanelListY
import me.anno.ui.editor.FontListMenu.createFontInput
import me.anno.ui.editor.SettingCategory
import me.anno.ui.editor.color.spaces.HSLuv
import me.anno.ui.input.BooleanInput
import me.anno.ui.Style
import org.joml.Vector3f
import org.joml.Vector4f

fun Text.createInspectorWithoutSuperImpl(
    inspected: List<Inspectable>,
    list: PanelListY,
    style: Style,
    getGroup: (title: String, description: String, dictSubPath: String) -> SettingCategory
) {
    val t = inspected.filterIsInstance<Transform>()
    val c = inspected.filterIsInstance<Text>()

    list += vis(inspected, c, "Text", "", "", c.map { it.text }, style)
    /*list += TextInputML("Text", style, text[lastLocalTime])
        .addChangeListener {
            RemsStudio.incrementalChange("text") {
                getSelfWithShadows().forEach { c ->
                    c.putValue(c.text, it, true)
                }
            }
        }
        .setIsSelectedListener { show(null) }*/

    val fontGroup = getGroup("Font", "", "font")
    fontGroup += createFontInput(font.name, style) {
        RemsStudio.largeChange("Change Font to '$it'") {
            for (x in c) for (e in x.getSelfWithShadows()) {
                e.font = e.font.withName(it)
            }
        }
        invalidate()
    }.setIsSelectedListener { show(t, null) }

    fontGroup += BooleanInput("Italic", font.isItalic, false, style)
        .setChangeListener {
            RemsStudio.largeChange("Italic: $it") {
                for (x in c) for (e in x.getSelfWithShadows()) {
                    e.font = e.font.withItalic(it)
                }
            }
            invalidate()
        }
        .setIsSelectedListener { show(t, null) }
    fontGroup += BooleanInput("Bold", font.isBold, false, style)
        .setChangeListener {
            RemsStudio.largeChange("Bold: $it") {
                for (x in c) for (e in x.getSelfWithShadows()) {
                    e.font = e.font.withBold(it)
                }
            }
            invalidate()
        }
        .setIsSelectedListener { show(t, null) }
    fontGroup += BooleanInput("Small Caps", smallCaps, false, style)
        .setChangeListener {
            RemsStudio.largeChange("Small Caps: $it") {
                for (x in c) for (e in x.getSelfWithShadows()) {
                    e.smallCaps = it
                }
            }
            invalidate()
        }
        .setIsSelectedListener { show(t, null) }

    val alignGroup = getGroup("Alignment", "", "alignment")
    fun align(title: String, value: List<AnimatedProperty<*>>) {
        // , xAxis: Boolean, set: (self: Text, AxisAlignment) -> Unit
        alignGroup += vis(inspected, c, title, "", "", value, style)
        /* operator fun AxisAlignment.get(x: Boolean) = if (x) xName else yName
         alignGroup += EnumInput(
             title, true,
             value[xAxis],
             AxisAlignment.values().map { NameDesc(it[xAxis]) }, style
         )
             .setIsSelectedListener { show(null) }
             .setChangeListener { name, _, _ ->
                 val alignment = AxisAlignment.values().first { it[xAxis] == name }
                 RemsStudio.largeChange("Set $title to $name") {
                     getSelfWithShadows().forEach { set(it, alignment) }
                 }
                 invalidate()
             }*/
    }

    align("Text Alignment", c.map { it.textAlignment })//, true)// { self, it -> self.textAlignment = it }
    align("Block Alignment X", c.map { it.blockAlignmentX })//, true)// { self, it -> self.blockAlignmentX = it }
    align("Block Alignment Y", c.map { it.blockAlignmentY })//, false)// { self, it -> self.blockAlignmentY = it }

    val spaceGroup = getGroup("Spacing", "", "spacing")
    // make this element separable from the parent???
    spaceGroup += vi(
        inspected, "Character Spacing",
        "Space between individual characters",
        "text.characterSpacing",
        null, relativeCharSpacing, style
    ) {
        RemsStudio.incrementalChange("char space") { for (x in c) x.relativeCharSpacing = it }
        invalidate()
    }
    spaceGroup += vis(
        inspected, c, "Line Spacing",
        "How much lines are apart from each other",
        "text.lineSpacing",
        c.map { it.relativeLineSpacing }, style
    )
    spaceGroup += vi(
        inspected, "Tab Size", "Relative tab size, in widths of o's", "text.tabSpacing",
        Text.tabSpaceType, relativeTabSize, style
    ) {
        RemsStudio.incrementalChange("tab size") { for (x in c) x.relativeTabSize = it }
        invalidate()
    }
    spaceGroup += vi(
        inspected, "Line Break Width",
        "How broad the text shall be, at maximum; < 0 = no limit", "text.widthLimit",
        Text.lineBreakType, lineBreakWidth, style
    ) {
        RemsStudio.incrementalChange("line break width") { for (x in c) x.lineBreakWidth = it }
        invalidate()
    }

    val ops = getGroup("Operations", "", "operations")
    ops += TextButton("Create Shadow", false, style)
        .addLeftClickListener {
            // such a mess is the result of copying colors from the editor ;)
            val signalColor = Vector4f(HSLuv.toRGB(Vector3f(0.000f, 0.934f, 0.591f)), 1f)
            val pos = Vector3f(0.01f, -0.01f, -0.001f)
            for (x in c) {
                val shadow = x.clone() as Text
                shadow.name = "Shadow"
                shadow.comment = "Keep \"shadow\" in the name for automatic property inheritance"
                // this avoids user reports, from people, who can't see their shadow
                // making something black should be simple
                shadow.color.set(signalColor)
                shadow.position.set(pos)
                shadow.relativeLineSpacing =
                    relativeLineSpacing // evil ;), because we link instances instead of making a copy
                RemsStudio.largeChange("Add Text Shadow") { x.addChild(shadow) }
                Selection.selectTransform(shadow)
            }
        }

    val rpgEffects = getGroup("RPG Effects", "", "rpg-effects")
    rpgEffects += vis(
        c, "Start Cursor", "The first character index to be drawn", c.map { it.startCursor },
        style
    )
    rpgEffects += vis(
        c, "End Cursor", "The last character index to be drawn; -1 = unlimited", c.map { it.endCursor },
        style
    )

    val outline = getGroup("Outline", "", "outline")
    outline.setTooltip("Needs Rendering Mode = SDF or Merged SDF")
    outline += vi(
        inspected, "Rendering Mode",
        "Mesh: Sharp, Signed Distance Fields: with outline", "text.renderingMode",
        null, renderingMode, style
    ) { for (x in c) x.renderingMode = it }
    outline += vis(
        inspected, c, "Color 1", "First Outline Color", "outline.color1",
        c.map { it.outlineColor0 }, style
    )
    outline += vis(
        inspected, c, "Color 2", "Second Outline Color", "outline.color2",
        c.map { it.outlineColor1 }, style
    )
    outline += vis(
        inspected, c, "Color 3", "Third Outline Color", "outline.color3",
        c.map { it.outlineColor2 }, style
    )
    outline += vis(
        inspected, c, "Widths", "[Main, 1st, 2nd, 3rd]", "outline.widths",
        c.map { it.outlineWidths }, style
    )
    outline += vis(
        inspected, c, "Smoothness", "How smooth the edge is, [Main, 1st, 2nd, 3rd]", "outline.smoothness",
        c.map { it.outlineSmoothness }, style
    )
    outline += vis(
        inspected, c, "Depth",
        "For non-merged SDFs to join close characters correctly; needs a distance from the background",
        "outline.depth",
        c.map { it.outlineDepth }, style
    )
    outline += vi(
        inspected, "Rounded Corners", "Makes corners curvy", "outline.roundCorners",
        null, roundSDFCorners, style
    ) {
        for (x in c) x.roundSDFCorners = it
        invalidate()
    }
    // outline += vis(inspected, c, "Start Angle", "", "", c.map { it.startDegrees }, style)
    // outline += vis(inspected, c, "End Angle", "", "", c.map { it.endDegrees }, style)

    val shadows = getGroup("Shadow", "", "shadow")
    shadows += vis(inspected, c, "Color", "", "shadow.color", c.map { it.shadowColor }, style)
    shadows += vis(inspected, c, "Offset", "", "shadow.offset", c.map { it.shadowOffset }, style)
    shadows += vis(inspected, c, "Smoothness", "", "shadow.smoothness", c.map { it.shadowSmoothness }, style)

}
