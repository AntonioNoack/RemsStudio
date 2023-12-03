package test

import me.anno.image.ImageWriter
import me.anno.maths.Maths
import me.anno.remsstudio.audio.pattern.PatternRecorderCore
import org.joml.Vector2f
import kotlin.math.abs

fun main() {
    // testUI { create() }

    // test time mapping visually :)
    val steps = 500
    val minT = 0f
    val maxT = 10f
    val rhythm = doubleArrayOf(1.0, 3.0, 5.0, 7.0, 9.0)
    val timestamps = doubleArrayOf(-10.0, 10.0, -10.0, 10.0, -10.0)

    val pts = (0 until steps).map {
        Maths.mix(minT, maxT, it / (steps - 1f))
    }.map {
        Vector2f(it, -PatternRecorderCore.mapTime(rhythm, timestamps, it.toDouble()).toFloat())
    }
    val maxGradient = 2f * (maxT - minT) / steps
    for (i in 1 until steps) {
        if (abs(pts[i].y - pts[i - 1].y) > maxGradient) {
            println("${pts[i - 1]},${pts[i]}")
        }
    }
    ImageWriter.writeImageCurve(
        steps, steps, true,
        -1, 0, 1, pts, ""
    )
}
