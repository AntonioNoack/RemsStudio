package test

import me.anno.animation.Interpolation
import me.anno.io.files.InvalidRef
import me.anno.io.json.saveable.JsonStringReader
import me.anno.remsstudio.animation.AnimatedProperty
import org.apache.logging.log4j.LogManager

fun main() {

    val logger = LogManager.getLogger("KeyframeTime")

    val prop = AnimatedProperty.float(0f)
    prop.isAnimated = true
    prop.addKeyframe(1.0, 1f)
    prop.addKeyframe(2.0, 2f)

    for (kf in prop.keyframes) {
        kf.interpolation = Interpolation.EASE_IN
    }

    val asString = prop.toString()
    val fromString = JsonStringReader.read(asString, InvalidRef, false)
        .filterIsInstance<AnimatedProperty<*>>().first()

    logger.info(asString)
    logger.info(fromString)

}