package test

import me.anno.audio.AudioReadable
import me.anno.engine.OfficialExtensions
import me.anno.image.ImageWriter
import me.anno.io.files.inner.temporary.InnerTmpAudioFile
import me.anno.maths.Maths.TAU
import me.anno.maths.Maths.sq
import me.anno.remsstudio.audio.AudioFXCache2
import me.anno.remsstudio.audio.effects.Domain
import me.anno.remsstudio.audio.effects.Time
import me.anno.remsstudio.audio.effects.impl.EqualizerEffect
import me.anno.remsstudio.audio.effects.impl.EqualizerEffect.Companion.frequencies
import me.anno.remsstudio.objects.video.Video
import me.anno.remsstudio.objects.Camera
import org.joml.Vector2f
import org.junit.jupiter.api.Test
import kotlin.math.*
import kotlin.test.assertEquals

class EqualizerTest {

    val bufferSize = 4096 // where is that value coming from?
    val sampleRate = 48000

    val range = 10f.pow(12f / 10f)

    fun interface Sampler {
        fun sample(time: Double, channel: Int): Double
    }

    private fun getBuffer(audio: Video): Pair<FloatArray, FloatArray> {
        return AudioFXCache2.getBuffer(audio, Camera(), bufferSize, Domain.TIME_DOMAIN, false) {
            Time((it + 3) * bufferSize / sampleRate.toDouble())
        }!!
    }

    private fun sample(sampler: Sampler): InnerTmpAudioFile {
        return object : InnerTmpAudioFile() {
            override val channels: Int get() = 2
            override fun sample(time: Double, channel: Int): Short {
                return sampler.sample(time, channel).toInt().toShort()
            }
        }
    }

    @Test
    fun testEqualityTransform() {
        val frequencyHz = 500
        val w = frequencyHz * TAU
        val a = 32000.0
        val file = sample { time, channel ->
            if (channel == 0) sin(time * w) * a else 0.0
        }

        val audio = Video(file)
        val effect = EqualizerEffect()
        audio.pipeline.effects.add(effect)
        val (left, right) = getBuffer(audio)

        checkEquals(left, 0, file)
        checkEquals(right, 1, file)
        checkEqualsZero(right, 0f)
    }

    @Test
    fun testSliderMin() {
        val frequencyHz = 500
        val w = frequencyHz * TAU
        val a = 32000f
        val file = sample { time, _ ->
            sin(time * w) * a
        }

        val audio = Video(file)
        val effect = EqualizerEffect()
        for (sl in effect.sliders) sl.set(0f)
        audio.pipeline.effects.add(effect)
        val (left, right) = getBuffer(audio)

        checkEqualsZero(left, a * 1.001f / range)
        checkEqualsZero(right, a * 1.001f / range)
    }

    @Test
    fun testRemoveOneFrequency() {
        OfficialExtensions.initForTests()
        for (i in frequencies.indices) {
            val frequencyHz = frequencies[i]
            val w = frequencyHz * TAU
            val a = 32000f
            val src = sample { time, _ ->
                sin(time * w) * a
            }
            val audio = Video(src)
            val effect = EqualizerEffect()
            for (di in -1..1) {
                effect.sliders.getOrNull(i + di)?.set(0f)
            }
            audio.pipeline.effects.add(effect)
            val (left, _) = getBuffer(audio)
            if (false) ImageWriter.writeImageCurve(
                2048 * 4, 512, true, -1, 0,
                1, (0 until bufferSize).map {
                    Vector2f(240f * it, left[it])
                } + (0 until bufferSize).map {
                    val time = it.toDouble() / sampleRate
                    Vector2f(240f * it, src.sample(time, 0).toFloat())
                }, "f$frequencyHz.png"
            )
            val average = left.average().toFloat()
            val remainder = sqrt(left.map { sq(it - average) }.average()).toFloat()
            assertEquals(remainder, 0f, 1500f)
        }
    }

    private fun checkEquals(buffer: FloatArray, channel: Int, file: AudioReadable) {
        assertEquals(bufferSize, buffer.size)
        for (i in buffer.indices) {
            val time = i.toDouble() / sampleRate
            val c0 = buffer[min(i + 2, buffer.lastIndex)]
            val c1 = buffer[i]
            val c2 = buffer[max(i - 2, 0)]
            val expected = file.sample(time, channel)
            val tolerance = abs(c1 - c0) + abs(c2 - c1)
            assertEquals(expected.toFloat(), c1, tolerance)
        }
    }

    private fun checkEqualsZero(buffer: FloatArray, tolerance: Float) {
        assertEquals(bufferSize, buffer.size)
        var maxDeviation = 0f
        for (i in buffer.indices) {
            maxDeviation = max(maxDeviation, abs(buffer[i]))
        }
        assertEquals(maxDeviation, 0f, tolerance)
        println("|$maxDeviation| < $tolerance")
    }

}
