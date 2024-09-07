package me.anno.remsstudio.ui.input

import me.anno.language.translation.NameDesc
import me.anno.remsstudio.animation.AnimatedProperty
import me.anno.ui.Style
import me.anno.ui.input.IntInput
import me.anno.ui.input.NumberType
import org.joml.Vector2i
import org.joml.Vector3i
import org.joml.Vector4i

class IntInputV2(
    title: NameDesc, visibilityKey: String, type: NumberType,
    private val owningProperty: AnimatedProperty<*>, style: Style,
) : IntInput(title, visibilityKey, type, style, NumberInputComponentV2(owningProperty, visibilityKey, style)) {

    constructor(
        title: NameDesc, visibilityKey: String,
        owningProperty: AnimatedProperty<*>, time: Double, style: Style
    ) : this(title, visibilityKey, owningProperty.type, owningProperty, style) {
        when (val value = owningProperty[time]) {
            is Int -> setValue(value, false)
            is Long -> setValue(value, false)
            else -> throw RuntimeException("Unknown type $value for ${javaClass.simpleName}")
        }
    }

    private val indexInProperty get() = indexInParent

    override fun getValue(value: Any): Long {
        return when (value) {
            is Vector2i -> value[indexInProperty].toLong()
            is Vector3i -> value[indexInProperty].toLong()
            is Vector4i -> value[indexInProperty].toLong()
            else -> super.getValue(value)
        }
    }

    override fun onEmpty(x: Float, y: Float) {
        val newValue = getValue(owningProperty.defaultValue ?: type.defaultValue)
        if (newValue != value) {
            setValue(newValue, true)
        }
    }

    override fun clone(): IntInputV2 {
        val clone = IntInputV2(title, visibilityKey, type, owningProperty, style)
        copyInto(clone)
        return clone
    }

}