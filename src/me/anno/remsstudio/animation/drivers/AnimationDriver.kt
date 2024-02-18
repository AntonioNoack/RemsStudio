package me.anno.remsstudio.animation.drivers

import me.anno.engine.inspector.Inspectable
import me.anno.io.Saveable
import me.anno.io.base.BaseWriter
import me.anno.language.translation.Dict
import me.anno.language.translation.NameDesc
import me.anno.remsstudio.Selection.select
import me.anno.remsstudio.Selection.selectProperty
import me.anno.remsstudio.Selection.selectedTransforms
import me.anno.remsstudio.animation.AnimatedProperty
import me.anno.remsstudio.objects.Transform
import me.anno.ui.Style
import me.anno.ui.WindowStack
import me.anno.ui.base.groups.PanelListY
import me.anno.ui.base.menu.Menu.openMenu
import me.anno.ui.base.menu.MenuOption
import me.anno.ui.base.text.TextPanel
import me.anno.ui.editor.SettingCategory
import me.anno.ui.input.NumberType
import org.joml.Vector2d
import org.joml.Vector3d
import org.joml.Vector4d

abstract class AnimationDriver : Saveable(), Inspectable {

    var frequency = 1.0
    var amplitude = AnimatedProperty.float(1f)

    fun getFloatValue(time: Double, keyframeValue: Double, index: Int) =
        getValue(time, keyframeValue, index).toFloat()

    fun getValue(time: Double, keyframeValue: Double, index: Int) =
        getValue0(time * frequency, keyframeValue, index) * amplitude[time]

    open fun getValue(time: Double, keyframeValue: Vector2d): Vector2d {
        return Vector2d(
            getValue0(time * frequency, keyframeValue.x, 0) * amplitude[time],
            getValue0(time * frequency, keyframeValue.y, 1) * amplitude[time]
        )
    }

    open fun getValue(time: Double, keyframeValue: Vector3d): Vector3d {
        return Vector3d(
            getValue0(time * frequency, keyframeValue.x, 0) * amplitude[time],
            getValue0(time * frequency, keyframeValue.y, 1) * amplitude[time],
            getValue0(time * frequency, keyframeValue.z, 2) * amplitude[time]
        )
    }

    open fun getValue(time: Double, keyframeValue: Vector4d): Vector4d {
        return Vector4d(
            getValue0(time * frequency, keyframeValue.x, 0) * amplitude[time],
            getValue0(time * frequency, keyframeValue.y, 1) * amplitude[time],
            getValue0(time * frequency, keyframeValue.z, 2) * amplitude[time],
            getValue0(time * frequency, keyframeValue.w, 3) * amplitude[time]
        )
    }

    abstract fun getValue0(time: Double, keyframeValue: Double, index: Int): Double
    override val approxSize get() = 5
    override fun isDefaultValue() = false

    open fun createInspector(
        inspected: List<Inspectable>,
        list: PanelListY,
        transforms: List<Transform>,
        style: Style,
        getGroup: (title: String, description: String, dictSubPath: String) -> SettingCategory
    ) {
        val transform = transforms[0]
        list += transform.vis(
            listOf(transform), "Amplitude", "Scale of randomness", "driver.amplitude",
            listOf(amplitude), style
        )
        list += transform.vi(
            inspected, "Frequency", "How fast it's changing", "driver.frequency",
            NumberType.DOUBLE, frequency, style
        ) { it, _ -> frequency = it }
    }

    fun show(toShow: List<AnimatedProperty<*>>?) {
        select(selectedTransforms, toShow)
    }

    override fun save(writer: BaseWriter) {
        super.save(writer)
        writer.writeDouble("frequency", frequency, true)
        writer.writeObject(this, "amplitude", amplitude)
    }

    override fun setProperty(name: String, value: Any?) {
        when (name) {
            "amplitude" -> amplitude.copyFrom(value)
            "frequency" -> frequency = value as? Double ?: return
            else -> super.setProperty(name, value)
        }
    }

    abstract fun getDisplayName(): String

    // requires, that an object is selected
    override fun createInspector(
        list: PanelListY,
        style: Style,
        getGroup: (title: String, description: String, dictSubPath: String) -> SettingCategory
    ) = createInspector(listOf(this), list, style, getGroup)

    override fun createInspector(
        inspected: List<Inspectable>,
        list: PanelListY,
        style: Style,
        getGroup: (title: String, description: String, dictSubPath: String) -> SettingCategory
    ) {
        list += TextPanel(Dict["Driver Inspector", "driver.inspector.title"], style)
        val t = selectedTransforms
        createInspector(inspected, list, t, style, getGroup)
    }


    companion object {
        fun openDriverSelectionMenu(
            windowStack: WindowStack,
            oldDriver: AnimationDriver?,
            whenSelected: (AnimationDriver?) -> Unit
        ) {
            fun add(create: () -> AnimationDriver): () -> Unit = { whenSelected(create()) }
            val options = arrayListOf(
                MenuOption(NameDesc("Harmonics", "sin(pi*i*t)", "obj.driver.harmonics"), add { HarmonicDriver() }),
                MenuOption(
                    NameDesc("Noise", "Perlin Noise, Randomness", "obj.driver.noise"),
                    add { PerlinNoiseDriver() }),
                MenuOption(
                    NameDesc("Custom", "Specify your own formula", "obj.driver.custom"),
                    add { FunctionDriver() }),
                MenuOption( // todo only add for "Advanced Time"?
                    NameDesc(
                        "Rhythm",
                        "Map timestamps to musical rhythm for satisfying timelapses",
                        "obj.driver.rhythm"
                    ),
                    add { RhythmDriver() }
                )
            )
            if (oldDriver != null) {
                options.add(0,
                    MenuOption(NameDesc("Customize", "Change the driver properties", "driver.edit")) {
                        selectProperty(listOf(oldDriver))
                        // todo why doesn't it draw the lines when adding (Selection.selectedProperties ?: emptyList())?
                    })
                options += MenuOption(
                    NameDesc("Remove Driver", "Changes back to keyframe-animation", "driver.remove")
                ) { whenSelected(null) }
            }
            openMenu(
                windowStack,
                if (oldDriver == null) NameDesc("Add Driver", "", "driver.add")
                else NameDesc("Change Driver", "", "driver.change"),
                options
            )
        }
    }

}