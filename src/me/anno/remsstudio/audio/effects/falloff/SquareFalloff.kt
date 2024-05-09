package me.anno.remsstudio.audio.effects.falloff

class SquareFalloff : Falloff {

    constructor() : super()
    constructor(halfDistance: Float) : super(halfDistance)

    override fun getAmplitude(relativeDistance: Float): Float {
        return 1f / (1f + relativeDistance * relativeDistance)
    }

    override val displayName get() = "Square Falloff"
    override val description get() = "Sound falloff ~ 1/(1+distance²)"
    override fun clone() = SquareFalloff(halfDistance)

    override val className get() = "SquareFalloffEffect"

}