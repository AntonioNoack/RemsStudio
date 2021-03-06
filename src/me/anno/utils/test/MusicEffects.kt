package me.anno.utils.test

import me.anno.audio.AudioFXCache
import me.anno.audio.effects.Domain
import me.anno.audio.effects.Time
import me.anno.audio.effects.impl.EqualizerEffect
import me.anno.io.files.FileReference
import me.anno.io.files.FileReference.Companion.getReference
import me.anno.objects.Camera
import me.anno.objects.Video
import me.anno.utils.Maths.pow
import me.anno.utils.OS
import kotlin.math.roundToInt
import kotlin.math.sin

// todo test, how effects influence the looks and sound of waveforms

fun main() {

    val file = OS.downloads.getChild("Aitana 11 Raizones.mp4")!!
    if (!file.exists) throw RuntimeException("Missing file!")

    val bufferSize = 1024

    val audio = Video(file)
    val camera = Camera()
    val effect = EqualizerEffect()
    val pipeline = audio.pipeline
    pipeline.effects += effect
    pipeline.audio = audio
    pipeline.camera = camera

    for (i in effect.frequencies.indices) {
        effect.sliders[i].set(Math.random().toFloat()) // 0.37457f
    }

    val func = FloatArray(1024 * 2) { pow(sin(it * Math.PI / 9f).toFloat() * 9f, 3f) }
    val time = func.size / 48000.0

    fun print(data: FloatArray) {
        val size = 16
        println(
            "${
                (0 until size).joinToString {
                    data[1024 - size + it].roundToInt().toString()
                }
            } | ${(0 until size).joinToString { data[1024 + it].roundToInt().toString() }}"
        )
    }

    print(func)

    val t0 = Time(0.0, 0.0)
    val t1 = Time(time, time)
    val result = AudioFXCache.getBuffer(audio, camera, t0, t1, bufferSize, Domain.TIME_DOMAIN, false)
    print(result)

}
