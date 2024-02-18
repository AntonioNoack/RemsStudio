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
import org.joml.Vector4f

class EffectColoring : Transform() {

    var lastInfluence = 0f
    val influence = AnimatedProperty.float(1f)
    val sharpness = AnimatedProperty.float(20f)

    init {
        color.set(Vector4f(1f, 0f, 0f, 1f))
    }

    override val className get() = "EffectColoring"
    override val defaultDisplayName get() = Dict["Effect: Coloring", "obj.effect.coloring"]
    override val symbol get() = DefaultConfig["ui.symbol.fx.coloring", "\uD83C\uDFA8"]

    override fun createInspector(
        inspected: List<Inspectable>,
        list: PanelListY,
        style: Style,
        getGroup: (title: String, description: String, dictSubPath: String) -> SettingCategory
    ) {
        super.createInspector(inspected, list, style, getGroup)
        val c = inspected.filterIsInstance<EffectColoring>()
        val fx = getGroup("Effect", "", "effect")
        fx += vis(c, "Strength", "How much this color shall be used", c.map { it.influence }, style)
        fx += vis(c, "Sharpness", "How sharp the circle is", c.map { it.sharpness }, style)
    }

    override fun save(writer: BaseWriter) {
        super.save(writer)
        writer.writeObject(this, "influence", influence)
        writer.writeObject(this, "sharpness", sharpness)
    }

    override fun setProperty(name: String, value: Any?) {
        when (name) {
            "influence" -> influence.copyFrom(value)
            "sharpness" -> sharpness.copyFrom(value)
            else -> super.setProperty(name, value)
        }
    }
}