package me.anno.remsstudio.objects.attractors

import me.anno.config.DefaultConfig
import me.anno.engine.inspector.Inspectable
import me.anno.io.base.BaseWriter
import me.anno.language.translation.Dict
import me.anno.remsstudio.animation.AnimatedProperty
import me.anno.remsstudio.objects.Transform
import me.anno.ui.Style
import me.anno.ui.base.groups.PanelListY
import me.anno.ui.editor.SettingCategory

class EffectMorphing : Transform() {

    var lastInfluence = 0f

    val zooming = AnimatedProperty.float(1f)
    val chromatic = AnimatedProperty.float(0f)
    val swirlStrength = AnimatedProperty.float(0f)
    val swirlPower = AnimatedProperty.float(1f)
    val sharpness = AnimatedProperty.float(20f)

    override val className get() = "EffectMorphing"
    override val defaultDisplayName get() = Dict["Effect: Morphing", "obj.effect.morphing"]
    override val symbol get() = DefaultConfig["ui.symbol.fx.morphing", "\uD83D\uDCA0"]

    override fun createInspector(
        inspected: List<Inspectable>,
        list: PanelListY,
        style: Style,
        getGroup: (title: String, description: String, dictSubPath: String) -> SettingCategory
    ) {
        super.createInspector(inspected, list, style, getGroup)
        val c = inspected.filterIsInstance<EffectMorphing>()
        val fx = getGroup("Effect", "", "effects")
        fx += vis(
            c, "Strength", "The effective scale",
            c.map { it.zooming }, style
        )
        // like https://www.youtube.com/watch?v=QbwgQSwMSGM, at 6:27
        fx += vis(
            c, "Chromatic Aberration", "Separates the effect for R/G/B channels; only works for video/images.",
            c.map { it.chromatic }, style
        )
        fx += vis(
            c, "Swirl Strength", "How badly/which way around it swirls",
            c.map { it.swirlStrength }, style
        )
        fx += vis(
            c, "Swirl Power", "How badly it swirls; 1 ~ wobble, 20 ~ swirls (at 1 Swirl Strength)",
            c.map { it.swirlPower }, style
        )
        fx += vis(
            c, "Sharpness", "How sharp the lens effect is",
            c.map { it.sharpness }, style
        )
    }

    override fun save(writer: BaseWriter) {
        super.save(writer)
        writer.writeObject(this, "influence", zooming)
        writer.writeObject(this, "chromatic", chromatic)
        writer.writeObject(this, "swirl", swirlStrength)
        writer.writeObject(this, "swirlPower", swirlPower)
        writer.writeObject(this, "sharpness", sharpness)
    }

    override fun setProperty(name: String, value: Any?) {
        when (name) {
            "influence" -> zooming.copyFrom(value)
            "sharpness" -> sharpness.copyFrom(value)
            "chromatic" -> chromatic.copyFrom(value)
            "swirl" -> swirlStrength.copyFrom(value)
            "swirlPower" -> swirlPower.copyFrom(value)
            else -> super.setProperty(name, value)
        }
    }
}