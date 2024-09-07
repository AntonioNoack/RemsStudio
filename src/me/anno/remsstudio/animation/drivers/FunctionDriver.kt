package me.anno.remsstudio.animation.drivers

import me.anno.config.DefaultConfig
import me.anno.engine.inspector.Inspectable
import me.anno.io.base.BaseWriter
import me.anno.language.translation.Dict
import me.anno.language.translation.NameDesc
import me.anno.parser.SimpleExpressionParser.parseDouble
import me.anno.parser.SimpleExpressionParser.preparse
import me.anno.remsstudio.objects.Transform
import me.anno.ui.Style
import me.anno.ui.base.groups.PanelListY
import me.anno.ui.editor.SettingCategory
import me.anno.ui.input.TextInputML

@Deprecated("Drivers are too technical")
@Suppress("MemberVisibilityCanBePrivate")
class FunctionDriver : AnimationDriver() {

    // make them animated? no xD
    var formula = "sin(time*360)"
    var formulaParts: ArrayList<Any>? = preparse(formula)

    override fun createInspector(
        inspected: List<Inspectable>, list: PanelListY, transforms: List<Transform>, style: Style,
        getGroup: (NameDesc) -> SettingCategory
    ) {
        super.createInspector(inspected, list, transforms, style, getGroup)
        list += TextInputML(NameDesc("Function f(time)", "", "driver.function"), formula, style)
            .apply { base.enableSpellcheck = false }
            .addChangeListener { formula = it; updateFormula() }
            .setIsSelectedListener { show(emptyList()) }
            .setTooltip(Dict["Example: sin(time*pi)", "driver.function.desc"])
    }

    // update by time? would be possible... but still...
    fun updateFormula() {
        formulaParts = preparse(formula)
    }

    override fun save(writer: BaseWriter) {
        super.save(writer)
        writer.writeString("formula", formula)
    }

    override fun setProperty(name: String, value: Any?) {
        when (name) {
            "formula" -> {
                formula = value as? String ?: return
                updateFormula()
            }
            else -> super.setProperty(name, value)
        }
    }

    override fun getValue0(time: Double, keyframeValue: Double, index: Int): Double {
        val formulaParts = formulaParts ?: return 0.0
        return parseDouble(
            ArrayList(formulaParts), mapOf(
                "t" to time, "time" to time,
                "v" to keyframeValue, "value" to keyframeValue
            )
        ) ?: 0.0
    }

    override val className get() = "FunctionDriver"
    override fun getDisplayName() =
        if (formula.length <= maxFormulaDisplayLength) formula
        else Dict["Function f(time)", "driver.function"]

    companion object {
        // could support more, but is useless anyway xD
        val maxFormulaDisplayLength get() = DefaultConfig["driver.formula.maxLength", 15]
    }

}