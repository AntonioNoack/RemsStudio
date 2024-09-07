package me.anno.remsstudio.ui.input

import me.anno.language.translation.NameDesc
import me.anno.remsstudio.animation.AnimatedProperty
import me.anno.ui.Style
import me.anno.ui.input.FloatInput
import me.anno.ui.input.NumberType
import me.anno.utils.types.AnyToDouble
import me.anno.utils.types.AnyToFloat
import org.joml.*

class FloatInputV2(
    title: NameDesc, visibilityKey: String, type: NumberType,
    private val owningProperty: AnimatedProperty<*>, style: Style
) : FloatInput(
    title, visibilityKey, type, style,
    NumberInputComponentV2(owningProperty, visibilityKey, style),
) {

    private val indexInProperty get() = indexInParent

    constructor(
        title: NameDesc,
        visibilityKey: String,
        owningProperty: AnimatedProperty<*>,
        time: Double, style: Style
    ) : this(title, visibilityKey, owningProperty.type, owningProperty, style) {
        when (val value = owningProperty[time]) {
            is Float -> setValue(value, false)
            is Double -> setValue(value, false)
            else -> throw RuntimeException("Unknown type $value for ${javaClass.simpleName}")
        }
    }

    override fun getValue(value: Any): Double {
        return when (value) {
            is Vector2f, is Vector3f, is Vector4f,
            is Quaternionf -> AnyToFloat.getFloat(value, indexInProperty, 0f).toDouble()
            is Vector2d, is Vector3d, is Vector4d,
            is Quaterniond -> AnyToDouble.getDouble(value, indexInProperty, 0.0)
            else -> super.getValue(value)
        }
    }

    override fun onEmpty(x: Float, y: Float) {
        val newValue = getValue(owningProperty.defaultValue ?: type.defaultValue)
        if (newValue != value) {
            setValue(newValue, true)
        }
    }

    override fun clone(): FloatInputV2 {
        val clone = FloatInputV2(title, visibilityKey, type, owningProperty, style)
        copyInto(clone)
        return clone
    }

    override val className: String
        get() = "FloatInputV2"

}