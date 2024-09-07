package me.anno.remsstudio.animation.drivers

import me.anno.config.DefaultConfig
import me.anno.io.base.BaseWriter
import me.anno.language.translation.Dict
import me.anno.remsstudio.objects.Transform
import me.anno.parser.SimpleExpressionParser.parseDouble
import me.anno.parser.SimpleExpressionParser.preparse
import me.anno.engine.inspector.Inspectable
import me.anno.language.translation.NameDesc
import me.anno.ui.editor.SettingCategory
import me.anno.ui.input.TextInput
import me.anno.ui.Style
import me.anno.ui.base.groups.PanelListY
import kotlin.math.PI
import kotlin.math.sin

@Deprecated("Drivers are too technical")
@Suppress("MemberVisibilityCanBePrivate")
class HarmonicDriver : AnimationDriver() {

    // use drivers to generate sound? rather not xD
    // maybe in debug mode

    // make them animated? no xD
    var harmonicsFormula = "1/n"
    val harmonics by lazy {
        FloatArray(maxHarmonics) {
            1f / (it + 1f)
        }
    }

    override fun createInspector(
        inspected: List<Inspectable>, list: PanelListY, transforms: List<Transform>, style: Style,
        getGroup: (NameDesc) -> SettingCategory
    ) {
        super.createInspector(inspected, list, transforms, style, getGroup)
        val name = getDisplayName()
        list += TextInput(NameDesc(name), "", harmonicsFormula, style.getChild("deep"))
            .addChangeListener { harmonicsFormula = it; updateHarmonics() }
            .setIsSelectedListener { show(emptyList()) }
            .setTooltip(Dict["Default value is 1/n, try [2,0,1][n-1]", "driver.harmonic.desc"])
    }

    // update by time? would be possible... but still...
    private fun updateHarmonics() {
        val prepared = preparse(harmonicsFormula)
        for (i in 0 until maxHarmonics) {
            val n = i + 1.0
            harmonics[i] = parseDouble(
                ArrayList(prepared), mapOf(
                    "n" to n, "i" to n
                )
            )?.toFloat() ?: harmonics[i]
        }
    }

    override fun save(writer: BaseWriter) {
        super.save(writer)
        writer.writeString("harmonics", harmonicsFormula)
    }

    override fun setProperty(name: String, value: Any?) {
        when(name){
            "harmonics" -> {
                harmonicsFormula = value as? String ?: return
                updateHarmonics()
            }
            else -> super.setProperty(name, value)
        }
    }

    override fun getValue0(time: Double, keyframeValue: Double, index: Int): Double {
        val w0 = time * 2.0 * PI
        var sum = 0.0
        for (idx in harmonics.indices) {
            sum += harmonics[idx] * sin((idx + 1f) * w0)
        }
        return sum
    }

    override val className get() = "HarmonicDriver"
    override fun getDisplayName() = Dict["Harmonics h(n)", "driver.harmonic"]

    companion object {
        // could support more, but is useless anyway xD
        val maxHarmonics get() = DefaultConfig["driver.harmonics.max", 32]
    }

}