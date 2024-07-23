package me.anno.remsstudio.objects.inspectable

import me.anno.language.translation.NameDesc
import me.anno.ui.input.NumberType
import org.joml.Vector4f

data class InspectableVector(val value: Vector4f, val nameDesc: NameDesc, val pType: PType) {
    enum class PType(val type: NumberType?) {
        DEFAULT(null),
        ROTATION(NumberType.ROT_YXZ),
        SCALE(NumberType.SCALE)
    }
}