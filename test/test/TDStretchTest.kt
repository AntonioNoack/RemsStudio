package test

import audacity.soundtouch.TimeDomainStretch
import me.anno.maths.Maths.PIf
import kotlin.math.sin

fun main() {
    val instance = TimeDomainStretch()
    instance.setChannels(1)
    instance.setTempo(2f)
    val f = PIf / 10f
    val bs = 6400
    val a = 1f
    instance.putSamples(FloatArray(bs) { a * sin(it * f) })
    instance.putSamples(FloatArray(bs) { a * sin((it + bs) * f) })
    instance.putSamples(FloatArray(bs) { a * sin((it + bs * 2) * f) })
    println(instance.inputBuffer.backend)
    println(instance.outputBuffer.backend)
}