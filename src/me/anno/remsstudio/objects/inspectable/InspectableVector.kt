package me.anno.remsstudio.objects.inspectable

import me.anno.ui.input.NumberType
import org.joml.Vector4f

data class InspectableVector(val value: Vector4f, val title: String, val description: String, val pType: PType) {

    constructor(value: Vector4f, title: String, type: PType) : this(value, title, "", type)

    enum class PType(val type: NumberType?) {
        DEFAULT(null),
        ROTATION(NumberType.ROT_YXZ),
        SCALE(NumberType.SCALE)
    }
}