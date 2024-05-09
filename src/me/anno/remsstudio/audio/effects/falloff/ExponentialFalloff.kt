package me.anno.remsstudio.audio.effects.falloff

import me.anno.maths.Maths.pow

class ExponentialFalloff : Falloff {

    constructor() : super()
    constructor(halfDistance: Float) : super(halfDistance)

    override fun getAmplitude(relativeDistance: Float): Float {
        return pow(0.5f, relativeDistance)
    }

    override fun clone() = ExponentialFalloff(halfDistance)

    override val displayName get() = "Exponential Falloff"
    override val description get() = "Sound falloff ~ 0.5 ^ distance"
    override val className get() = "ExponentialFalloffEffect"

}