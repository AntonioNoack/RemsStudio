package me.anno.remsstudio.audio.effects.impl

import me.anno.audio.AudioPools.FAPool
import me.anno.engine.inspector.Inspectable
import me.anno.io.base.BaseWriter
import me.anno.language.translation.NameDesc
import me.anno.maths.Maths.clamp
import me.anno.maths.Maths.mix
import me.anno.maths.Maths.pow
import me.anno.remsstudio.animation.AnimatedProperty
import me.anno.remsstudio.audio.effects.Domain
import me.anno.remsstudio.audio.effects.SoundEffect
import me.anno.remsstudio.audio.effects.Time
import me.anno.remsstudio.objects.video.Video
import me.anno.remsstudio.objects.Camera
import me.anno.ui.Style
import me.anno.ui.base.groups.PanelListY
import me.anno.ui.editor.SettingCategory
import me.anno.utils.structures.lists.Lists.createArrayList
import org.apache.logging.log4j.LogManager
import org.jtransforms.fft.FloatFFT_1D
import kotlin.math.abs
import kotlin.math.log2

// todo: this effect is completely broken :(
@Suppress("MemberVisibilityCanBePrivate")
class EqualizerEffect : SoundEffect(Domain.TIME_DOMAIN, Domain.TIME_DOMAIN) {

    companion object {
        private val LOGGER = LogManager.getLogger(EqualizerEffect::class)

        // val frequencies ~ 32 .. 32000 = 2 ^ (5 .. 15)
        // 30,60,120,240,
        // 480,960,...
        val frequencies = Array(11) {
            30.shl(it)
        }
    }

    override fun createInspector(
        inspected: List<Inspectable>, list: PanelListY, style: Style,
        getGroup: (nameDesc: NameDesc) -> SettingCategory
    ) {
        list += EqualizerView(this, style.getChild("deep"))
    }

    private val range = pow(10f, 2.4f) // +/- 12dB

    val sliders = createArrayList(frequencies.size) {
        AnimatedProperty.float01(0.5f)
    }

    override fun getStateAsImmutableKey(source: Video, destination: Camera, time0: Time, time1: Time): Any {
        return sliders.joinToString()
    }

    override fun save(writer: BaseWriter) {
        super.save(writer)
        writer.writeObjectList(this, "sliders", sliders.toList())
    }

    override fun setProperty(name: String, value: Any?) {
        when (name) {
            "sliders" -> {
                val values = value as? List<*> ?: return
                for (index in values.indices) {
                    val vi = values[index]
                    sliders.getOrNull(index)?.copyFrom(vi)
                }
            }
            else -> super.setProperty(name, value)
        }
    }

    fun getAmplitude(frequency: Float, sliders: FloatArray): Float {
        val index = log2(frequency) - 5
        val index0 = clamp(index.toInt(), 0, frequencies.size - 2)
        val index1 = index0 + 1
        return pow(
            range, mix(
                sliders[index0],
                sliders[index1],
                clamp(index - index0)
            ) - 0.5f
        )
    }

    override fun apply(
        getDataSrc: (Int) -> FloatArray,
        dataDst: FloatArray,
        source: Video,
        destination: Camera,
        time0: Time,
        time1: Time
    ) {

        val dataSrc1 = getDataSrc(0)

        val dt = time1.localTime - time0.localTime
        val time = time0.localTime + dt / 2

        val sliders = sliders.map { it[time] }
        if (sliders.all { abs(it - 0.5) < 1e-3f }) {
            LOGGER.info("no change at all, ${dataSrc1.size} vs ${dataDst.size}")
            dataSrc1.copyInto(dataDst)
            return
        }

        val firstSlider = sliders.first()
        if (sliders.all { abs(it - firstSlider) < 1e-3f }) {
            // LOGGER.info("all the same")
            // just multiply everything
            val amplitude = pow(range, firstSlider - 0.5f)
            for (i in dataDst.indices) dataDst[i] = dataSrc1[i] * amplitude
        } else {
            val half = dataSrc1.size.shr(1)
            val dataSrc0 = getDataSrc(-1)
            val dataSrc2 = getDataSrc(+1)
            val tmp = joinTogether(dataSrc0, dataSrc1, dataSrc2)
            val fft = FloatFFT_1D((dataSrc0.size * 2).toLong())
            fft.realForward(tmp)
            val slidersArray = sliders.toFloatArray()
            val invDt = (1 / dt).toFloat()
            for (i in dataSrc0.indices) {
                val frequencyHz = i * invDt
                val multiplier = getAmplitude(frequencyHz, slidersArray)
                if (multiplier != 1f) {
                    val j = i * 2
                    val k = j + 1
                    tmp[j] *= multiplier
                    tmp[k] *= multiplier
                }
            }
            fft.realInverse(tmp, true)
            tmp.copyInto(dataDst, 0, half, half * 3)
            FAPool.returnBuffer(tmp)
        }
    }

    private fun joinTogether(s0: FloatArray, s1: FloatArray, s2: FloatArray): FloatArray {
        val half = s0.size.shr(1)
        val tmp = FAPool[half * 4, false, false]
        s0.copyInto(tmp, 0, half, half * 2)
        s1.copyInto(tmp, half, 0, half * 2)
        s2.copyInto(tmp, half * 3, 0, half)
        return tmp
    }

    override val displayName get() = "Equalizer"
    override val description get() = "Changes the volume highs/mids/lows"
    override val className get() = "EqualizerEffect"

}