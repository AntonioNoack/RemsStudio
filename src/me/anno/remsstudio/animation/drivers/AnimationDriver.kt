package me.anno.remsstudio.animation.drivers

import me.anno.engine.inspector.Inspectable
import me.anno.io.base.BaseWriter
import me.anno.io.saveable.Saveable
import me.anno.language.translation.Dict
import me.anno.language.translation.NameDesc
import me.anno.remsstudio.Selection.select
import me.anno.remsstudio.Selection.selectedTransforms
import me.anno.remsstudio.animation.AnimatedProperty
import me.anno.remsstudio.objects.Transform
import me.anno.ui.Style
import me.anno.ui.base.groups.PanelListY
import me.anno.ui.base.text.TextPanel
import me.anno.ui.editor.SettingCategory
import me.anno.ui.input.NumberType
import org.joml.Vector2d
import org.joml.Vector3d
import org.joml.Vector4d

@Deprecated("Drivers are too technical (and would need better UI anyway)")
@Suppress("MemberVisibilityCanBePrivate")
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
        inspected: List<Inspectable>, list: PanelListY, transforms: List<Transform>, style: Style,
        getGroup: (NameDesc) -> SettingCategory
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

    fun show(toShow: List<AnimatedProperty<*>>) {
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

    override fun createInspector(
        inspected: List<Inspectable>, list: PanelListY, style: Style,
        getGroup: (NameDesc) -> SettingCategory
    ) {
        list += TextPanel(Dict["Driver Inspector", "driver.inspector.title"], style)
        val t = selectedTransforms
        createInspector(inspected, list, t, style, getGroup)
    }
}