package me.anno.remsstudio.test

import me.anno.animation.Interpolation
import me.anno.io.files.InvalidRef
import me.anno.io.json.saveable.JsonStringReader
import me.anno.remsstudio.animation.AnimatedProperty
import me.anno.utils.LOGGER

fun main() {

    val prop = AnimatedProperty.float()
    prop.isAnimated = true
    prop.addKeyframe(1.0, 1f)
    prop.addKeyframe(2.0, 2f)

    prop.keyframes.forEach { it.interpolation = Interpolation.EASE_IN }

    val asString = prop.toString()
    val fromString = JsonStringReader.read(asString, InvalidRef, false)
        .filterIsInstance<AnimatedProperty<*>>().first()

    LOGGER.info(asString)
    LOGGER.info(fromString)

}