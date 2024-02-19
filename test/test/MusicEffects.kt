package test

import me.anno.maths.Maths.pow
import me.anno.remsstudio.audio.AudioFXCache2
import me.anno.remsstudio.audio.effects.Domain
import me.anno.remsstudio.audio.effects.Time
import me.anno.remsstudio.audio.effects.impl.EqualizerEffect
import me.anno.remsstudio.objects.Camera
import me.anno.remsstudio.objects.Video
import me.anno.utils.OS
import org.apache.logging.log4j.LogManager
import kotlin.math.roundToInt
import kotlin.math.sin

// todo test, how effects influence the looks and sound of waveforms

fun main() {

    val logger = LogManager.getLogger("MusicEffects")

    val file = OS.downloads.getChild("Aitana 11 Raizones.mp4")
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
        logger.info(
            "${
                (0 until size).joinToString {
                    data[1024 - size + it].roundToInt().toString()
                }
            } | ${(0 until size).joinToString { data[1024 + it].roundToInt().toString() }}"
        )
    }

    print(func)

    val result = AudioFXCache2.getBuffer(audio, camera, bufferSize, Domain.TIME_DOMAIN, false) {
        Time(time * it)
    }
    print(result)

}
