package me.anno.remsstudio.audio.effects.impl

import me.anno.audio.streams.AudioStreamRaw.Companion.bufferSize
import me.anno.io.ISaveable
import me.anno.io.base.BaseWriter
import me.anno.maths.Maths.clamp
import me.anno.maths.Maths.mix
import me.anno.remsstudio.animation.AnimatedProperty
import me.anno.remsstudio.audio.effects.Domain
import me.anno.remsstudio.audio.effects.SoundEffect
import me.anno.remsstudio.audio.effects.Time
import me.anno.remsstudio.objects.Audio
import me.anno.remsstudio.objects.Camera
import me.anno.ui.base.groups.PanelListY
import me.anno.ui.editor.SettingCategory
import me.anno.ui.style.Style
import kotlin.math.sqrt

class NoiseSuppressionEffect : SoundEffect(Domain.TIME_DOMAIN, Domain.TIME_DOMAIN) {

    // todo implement auto noise level detection
    // var automaticNoiseLevelDetection = false
    private val noiseLevel = AnimatedProperty.floatPlus() // maybe should be in dB...

    override fun getStateAsImmutableKey(source: Audio, destination: Camera, time0: Time, time1: Time): Any {
        return noiseLevel.toString()
    }

    override fun apply(
        getDataSrc: (Int) -> FloatArray,
        dataDst: FloatArray, // is this stereo?
        source: Audio,
        destination: Camera,
        time0: Time,
        time1: Time
    ) {

        // create running average :)
        // based on that average, mute or not the audio :)
        val sm1 = getDataSrc(-1)
        val src = getDataSrc(+0)
        val sp1 = getDataSrc(+1)

        var accu = 0f
        for (x in sm1) {
            accu += x * x
        }

        for (x in src) {
            accu += x * x
        }

        val accuFactor = sqrt(0.5f / bufferSize) / 32e3f

        val noiseLevel = noiseLevel
        if (!noiseLevel.isAnimated && noiseLevel.drivers[0] == null) {
            val noiseLevelI = noiseLevel[time0.localTime]
            val invNoiseLevelI = 1f / noiseLevelI
            for (i in 0 until bufferSize) {
                val ratio = sqrt(accu) * accuFactor * invNoiseLevelI
                dataDst[i] = src[i] * clamp(ratio - 1f)
                val x0 = sm1[i]
                val x1 = sp1[i]
                accu += x1 * x1 - x0 * x0
            }
        } else {
            val t0 = time0.localTime
            val t1 = time1.localTime
            for (i in 0 until bufferSize) {
                val ratio = sqrt(accu) * accuFactor / noiseLevel[mix(t0, t1, i.toDouble() / bufferSize)]
                dataDst[i] = src[i] * clamp(ratio - 1f)
                val x0 = sm1[i]
                val x1 = sp1[i]
                accu += x1 * x1 - x0 * x0
            }
        }
    }

    override fun createInspector(
        list: PanelListY,
        style: Style,
        getGroup: (title: String, description: String, dictSubPath: String) -> SettingCategory
    ) {
        list += audio.vi("Noise Level", "All audio below this relative level will be silenced", noiseLevel, style)
    }

    override fun save(writer: BaseWriter) {
        super.save(writer)
        writer.writeObject(this, "noiseLevel", noiseLevel)
    }

    override fun readObject(name: String, value: ISaveable?) {
        if (name == "noiseLevel") noiseLevel.copyFrom(value)
        else super.readObject(name, value)
    }

    override val displayName: String = "Noise Suppression"
    override val description: String = "Removes noise; only works if noise is pretty quiet"
    override val className get() = "NoiseSuppressionEffect"

}