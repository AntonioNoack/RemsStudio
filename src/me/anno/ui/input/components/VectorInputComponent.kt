package me.anno.ui.input.components

import me.anno.animation.AnimatedProperty
import me.anno.animation.Type
import me.anno.ui.input.FloatInput
import me.anno.ui.input.IntInput
import me.anno.ui.input.VectorInput
import me.anno.ui.input.VectorIntInput
import me.anno.ui.style.Style
import me.anno.utils.types.Floats.anyToDouble

fun VectorInputComponent(
    title: String, visibilityKey: String,
    type: Type, owningProperty: AnimatedProperty<*>?,
    indexInProperty: Int,
    owner: VectorInput,
    style: Style
): FloatInput {
    val base = FloatInput(style, title, visibilityKey, type, owningProperty, indexInProperty)
    base.setChangeListener { owner.onChange() }
    return base
}

fun VectorInputIntComponent(
    title: String, visibilityKey: String,
    type: Type, owningProperty: AnimatedProperty<*>?,
    indexInProperty: Int,
    owner: VectorIntInput,
    style: Style
): IntInput {
    val base = IntInput(style, title, visibilityKey, type, owningProperty, indexInProperty)
    base.setChangeListener { owner.onChange() }
    return base
}