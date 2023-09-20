package me.anno.remsstudio.objects.attractors

import me.anno.config.DefaultConfig
import me.anno.io.ISaveable
import me.anno.io.base.BaseWriter
import me.anno.language.translation.Dict
import me.anno.remsstudio.animation.AnimatedProperty
import me.anno.remsstudio.objects.Transform
import me.anno.studio.Inspectable
import me.anno.ui.base.groups.PanelListY
import me.anno.ui.editor.SettingCategory
import me.anno.ui.Style

class EffectMorphing : Transform() {

    var lastInfluence = 0f
    val influence = AnimatedProperty.float(1f)
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
        fx += vis(c, "Strength", "The effective scale", c.map { it.influence }, style)
        fx += vis(c, "Sharpness", "How sharp the lens effect is", c.map { it.sharpness }, style)
    }

    override fun save(writer: BaseWriter) {
        super.save(writer)
        writer.writeObject(this, "influence", influence)
        writer.writeObject(this, "sharpness", sharpness)
    }

    override fun readObject(name: String, value: ISaveable?) {
        when (name) {
            "influence" -> influence.copyFrom(value)
            "sharpness" -> sharpness.copyFrom(value)
            else -> super.readObject(name, value)
        }
    }

}