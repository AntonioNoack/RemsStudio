package me.anno.remsstudio.audio.effects.impl

import audacity.soundtouch.TimeDomainStretch
import me.anno.audio.streams.AudioStreamRaw.Companion.bufferSize
import me.anno.engine.inspector.Inspectable
import me.anno.io.base.BaseWriter
import me.anno.language.translation.NameDesc
import me.anno.maths.Maths.clamp
import me.anno.remsstudio.audio.effects.Domain
import me.anno.remsstudio.audio.effects.SoundEffect
import me.anno.remsstudio.audio.effects.Time
import me.anno.remsstudio.objects.Camera
import me.anno.remsstudio.objects.video.Video
import me.anno.ui.Style
import me.anno.ui.base.groups.PanelListY
import me.anno.ui.editor.SettingCategory
import me.anno.ui.input.NumberType
import me.anno.utils.types.Casting.castToFloat2
import kotlin.math.abs

@Suppress("MemberVisibilityCanBePrivate")
class PitchEffect : SoundEffect(Domain.TIME_DOMAIN, Domain.TIME_DOMAIN) {

    companion object {
        val maxPitch = 20f
        val minPitch = 1f / maxPitch
        val pitchType = NumberType(1f, 1, 1f, false, true,
            { clamp(castToFloat2(it), minPitch, maxPitch) },
            { it is Float }
        )
    }

    override fun createInspector(
        inspected0: List<Inspectable>, list: PanelListY, style: Style,
        getGroup: (NameDesc) -> SettingCategory
    ) {
        // todo bug: effect broken, dragging not working?
        val inspected = listOf(audio)
        list += audio.vi(
            inspected,
            "Inverse Speed", "Making something play faster, increases the pitch; this is undone by this node",
            "pitch.inverseSpeed",
            null, inverseSpeed, style
        ) { it, _ -> inverseSpeed = it }
        list += audio.vi(
            inspected,
            "Value", "Pitch height, if Inverse Speed = false",
            "pitch.height",
            pitchType, pitch, style
        ) { it, _ -> pitch = it }
    }

    var inverseSpeed = false
    var pitch = 1f

    var tempo = 1f
    var hasTempo = false

    override fun getStateAsImmutableKey(source: Video, destination: Camera, time0: Time, time1: Time): Any {
        return Pair(pitch, tempo)
    }

    override fun apply(
        getDataSrc: (Int) -> FloatArray,
        dataDst: FloatArray,
        source: Video,
        destination: Camera,
        time0: Time,
        time1: Time
    ) {

        val stretch = TimeDomainStretch()
        stretch.setChannels(1)

        if (!hasTempo) {
            // todo can tempo be changed while running???...
            tempo = clamp(
                if (inverseSpeed) {
                    val localDt = abs(time1.localTime - time0.localTime)
                    val globalDt = abs(time1.globalTime - time0.globalTime)
                    (localDt / globalDt).toFloat()
                } else 1f / pitch, minPitch, maxPitch
            )
            stretch.setTempo(tempo)
            hasTempo = true
        }

        // nothing to do, should be exact enough
        if (tempo in 0.999f..1.001f) {
            getDataSrc(0).copyInto(dataDst)
            return
        }

        // give the stretching all data it needs
        stretch.putSamples(getDataSrc(-1))
        stretch.putSamples(getDataSrc(+0))
        stretch.putSamples(getDataSrc(+1))

        // then read the data, and rescale it to match the output
        val output = stretch.outputBuffer
        val output2 = output.backend

        // todo how do we match this as best as possible with the input and the following buffers?
        val offset = bufferSize // outputOffset
        val size = output.numSamples() - offset
        if (size > 0) {
            for (i in dataDst.indices) {
                dataDst[i] = output2[i + offset]
            }
        }

    }

    override fun save(writer: BaseWriter) {
        super.save(writer)
        writer.writeBoolean("inverseSpeed", inverseSpeed)
        writer.writeFloat("pitch", pitch)
    }

    override fun setProperty(name: String, value: Any?) {
        when (name) {
            "inverseSpeed" -> inverseSpeed = value == true
            "pitch" -> pitch = value as? Float ?: return
            else -> super.setProperty(name, value)
        }
    }

    override fun clone(): SoundEffect {
        val clone = PitchEffect()
        clone.inverseSpeed = inverseSpeed
        clone.pitch = pitch
        return clone
    }

    override val displayName get() = "Pitch Change"
    override val description get() = "Changes how high the frequencies are"
    override val className get() = "PitchEffect"

}