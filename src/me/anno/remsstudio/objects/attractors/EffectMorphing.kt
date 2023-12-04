package me.anno.remsstudio.objects.attractors

import me.anno.config.DefaultConfig
import me.anno.io.ISaveable
import me.anno.io.base.BaseWriter
import me.anno.language.translation.Dict
import me.anno.remsstudio.animation.AnimatedProperty
import me.anno.remsstudio.objects.Transform
import me.anno.studio.Inspectable
import me.anno.ui.Style
import me.anno.ui.base.groups.PanelListY
import me.anno.ui.editor.SettingCategory

class EffectMorphing : Transform() {

    var lastInfluence = 0f

    val zooming = AnimatedProperty.float(1f)
    val motion = AnimatedProperty.vec2()
    val swirl = AnimatedProperty.float()
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
        fx += vis(c, "Strength", "The effective scale", c.map { it.zooming }, style)
        fx += vis(c, "Motion", "Moves pixels left/right/up/down", c.map { it.motion }, style)
        fx += vis(c, "Swirl", "Swirls pixels around the center", c.map { it.swirl }, style)
        fx += vis(c, "Sharpness", "How sharp the lens effect is", c.map { it.sharpness }, style)
    }

    override fun save(writer: BaseWriter) {
        super.save(writer)
        writer.writeObject(this, "influence", zooming)
        writer.writeObject(this, "motion", motion)
        writer.writeObject(this, "swirl", swirl)
        writer.writeObject(this, "sharpness", sharpness)
    }

    override fun readObject(name: String, value: ISaveable?) {
        when (name) {
            "influence" -> zooming.copyFrom(value)
            "sharpness" -> sharpness.copyFrom(value)
            "motion" -> motion.copyFrom(value)
            "swirl" -> swirl.copyFrom(value)
            else -> super.readObject(name, value)
        }
    }

}