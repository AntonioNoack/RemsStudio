package me.anno.remsstudio.audio.effects.falloff

import kotlin.math.max

class LinearFalloff : Falloff {

    constructor() : super()
    constructor(halfDistance: Float) : super(halfDistance)

    override fun getAmplitude(relativeDistance: Float): Float {
        return max(0f, 1f - 0.5f * relativeDistance)
    }

    override val displayName get() = "Linear Falloff"
    override val description get() = "Sound falloff ~ 1-distance"
    override fun clone() = LinearFalloff(halfDistance)

    override val className get() = "LinearFalloffEffect"

}