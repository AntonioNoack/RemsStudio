package me.anno.remsstudio.audio.effects.falloff

import me.anno.remsstudio.audio.effects.SoundEffect
import kotlin.math.max

class LinearFalloff() : Falloff() {

    constructor(halfDistance: Float) : this() {
        this.halfDistance = halfDistance
    }

    override fun getAmplitude(relativeDistance: Float): Float {
        return max(0f, 1f - 0.5f * relativeDistance)
    }

    override val displayName get() = "Linear Falloff"
    override val description get() = "Sound falloff ~ 1-distance"
    override fun clone(): SoundEffect = LinearFalloff(halfDistance)

    override val className get() = "LinearFalloffEffect"

}