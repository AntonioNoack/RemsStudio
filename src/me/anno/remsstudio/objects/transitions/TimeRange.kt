package me.anno.remsstudio.objects.transitions

import me.anno.maths.Maths.unmix
import me.anno.remsstudio.objects.Transform
import kotlin.math.max
import kotlin.math.min

data class TimeRange<V: Transform>(val child: V, var min: Double, var max: Double) {

    val center get() = (min + max) * 0.5

    fun overlaps(other: TimeRange<*>): Boolean {
        return max(min, other.min) < min(max, other.max)
    }

    fun getProgress(time: Double): Float {
        return unmix(min, max, time).toFloat()
    }
}