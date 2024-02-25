package test

import me.anno.remsstudio.animation.AnimatedProperty
import me.anno.remsstudio.animation.AnimationIntegral.findIntegralX
import me.anno.remsstudio.animation.AnimationIntegral.getIntegral
import org.apache.logging.log4j.LogManager

fun main() {

    val logger = LogManager.getLogger("TestIntegral")

    val line = AnimatedProperty.float(0f)
    line.isAnimated = true
    line.addKeyframe(0.0, 1f)
    line.addKeyframe(1.0, 2f)

    for (i in 0 until 11) {
        val time = i / 10.0
        logger.info(line.getIntegral(time, false))
    }

    val ap = AnimatedProperty.float(0f)
    ap.isAnimated = true
    ap.addKeyframe(0.0, 0.0)
    ap.addKeyframe(10.0, 10.0)

    for (i in 1..50) {
        val target = i.toDouble()
        val time = ap.findIntegralX(0.0, 10.0, target)
        logger.info("$target: $time")
    }

}