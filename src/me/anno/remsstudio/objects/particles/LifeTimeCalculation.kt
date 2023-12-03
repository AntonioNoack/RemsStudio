package me.anno.remsstudio.objects.particles

import me.anno.remsstudio.objects.particles.distributions.AnimatedDistribution
import me.anno.remsstudio.objects.particles.forces.ForceField
import me.anno.remsstudio.objects.particles.forces.impl.BetweenParticleGravity
import me.anno.utils.structures.lists.Lists.none2
import org.apache.logging.log4j.LogManager
import kotlin.math.min

object LifeTimeCalculation {

    private val LOGGER = LogManager.getLogger(LifeTimeCalculation::class)

    // todo use this, but for it to work we need to support jumps in ParticleSystem.states
    fun findReasonableLastTime(time: Double, forces: List<ForceField>, lifeTime: AnimatedDistribution): Double {
        if (time <= 0.0) return 0.0
        val canSkipTime = forces.none2 { it is BetweenParticleGravity }
        return if (canSkipTime) {
            // find time such that
            //  for all t in 0.0 until thatTime, t + lifeTime(t) <= time
            // do bisection
            val minTime = findLastValidLifetime(time, lifeTime)
            var step = time - minTime
            var thatTime = minTime
            for (i in 0 until 53) {
                step *= 0.5
                val nextTime = thatTime + step
                if (nextTime == thatTime) break
                if (fulfillsLifetimeLimit(thatTime, nextTime, time, lifeTime)) {
                    thatTime = nextTime
                }
            }
            LOGGER.warn("Reasonable last time for $time: $thatTime")
            thatTime
        } else 0.0 // :/ we only could skip time if there was a period of no particles
    }

    private fun fulfillsLifetimeLimit(
        startTime: Double,
        thatTime: Double,
        time: Double,
        lifeTime: AnimatedDistribution
    ): Boolean {
        // find whether
        //  for all t in startTime until thatTime, t + lifeTime(t) <= time
        var t0 = startTime
        var l0 = lifeTime.maxV1(t0)
        if (t0 + l0 > time) return false // shouldn't happen
        while (true) {
            val nextKeyframeTime = lifeTime.channels.minOf { it.nextKeyframe(0.0) }
            val t1 = min(thatTime, min(t0 + l0, nextKeyframeTime))
            val l1 = lifeTime.maxV1(t1)
            if (t1 + l1 > time) return false
            if (t1 == thatTime) return true
            t0 = t1
            l0 = l1
        }
    }

    private fun findLastValidLifetime(time: Double, lifeTime: AnimatedDistribution): Double {
        // find whether
        //  for all t in 0.0 until thatTime, t + lifeTime(t) <= time
        var t0 = 0.0
        var l0 = lifeTime.maxV1(t0)
        if (t0 + l0 > time) return t0
        while (true) {
            val nextKeyframeTime = lifeTime.channels.minOf { it.nextKeyframe(0.0) }
            val t1 = min(time, min(t0 + l0, nextKeyframeTime))
            val l1 = lifeTime.maxV1(t1)
            if (t1 + l1 > time) return t0
            if (t1 == time) return time
            t0 = t1
            l0 = l1
        }
    }
}