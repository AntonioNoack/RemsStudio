package me.anno.remsstudio.audio.effects.falloff

import me.anno.audio.streams.AudioStreamRaw.Companion.bufferSize
import me.anno.engine.inspector.Inspectable
import me.anno.io.base.BaseWriter
import me.anno.language.translation.NameDesc
import me.anno.maths.Maths.mix
import me.anno.remsstudio.Selection.selectedTransforms
import me.anno.remsstudio.audio.effects.Domain
import me.anno.remsstudio.audio.effects.SoundEffect
import me.anno.remsstudio.audio.effects.Time
import me.anno.remsstudio.objects.video.Video
import me.anno.remsstudio.objects.Camera
import me.anno.ui.Style
import me.anno.ui.base.groups.PanelListY
import me.anno.ui.editor.SettingCategory
import me.anno.ui.input.NumberType
import org.joml.Vector3f

abstract class Falloff(var halfDistance: Float = 1f) : SoundEffect(Domain.TIME_DOMAIN, Domain.TIME_DOMAIN) {

    abstract fun getAmplitude(relativeDistance: Float): Float

    override fun getStateAsImmutableKey(source: Video, destination: Camera, time0: Time, time1: Time): Any {
        val amplitude0 = getAmplitude(source, destination, time0.globalTime)
        val amplitude1 = getAmplitude(source, destination, time1.globalTime)
        return Pair(amplitude0, amplitude1)
    }

    fun getAmplitude(source: Video, destination: Camera, globalTime: Double): Float {
        val position = source.getGlobalTransformTime(globalTime).first.transformPosition(Vector3f())
        val camera = destination.getGlobalTransformTime(globalTime).first.transformPosition(Vector3f())
        val distance = camera.distance(position)
        return getAmplitude(distance / halfDistance)
    }

    override fun apply(
        getDataSrc: (Int) -> FloatArray,
        dataDst: FloatArray,
        source: Video,
        destination: Camera,
        time0: Time,
        time1: Time
    ) {
        val dataSrc = getDataSrc(0)
        val amplitude0 = getAmplitude(source, destination, time0.globalTime)
        val amplitude1 = getAmplitude(source, destination, time1.globalTime)
        for (i in 0 until bufferSize) {
            val amplitude = mix(amplitude0, amplitude1, (i + 0.5f) / bufferSize)
            dataDst[i] = dataSrc[i] * amplitude
        }
    }

    override fun save(writer: BaseWriter) {
        super.save(writer)
        writer.writeFloat("halfDistance", halfDistance)
    }

    override fun setProperty(name: String, value: Any?) {
        when (name) {
            "halfDistance" -> halfDistance = value as? Float ?: return
            else -> super.setProperty(name, value)
        }
    }

    override fun createInspector(
        inspected: List<Inspectable>, list: PanelListY, style: Style,
        getGroup: (NameDesc) -> SettingCategory
    ) {
        list.add(audio.vi(
            selectedTransforms, "Half Distance",
            "Distance, where the amplitude is 50%",
            "falloff.halfDistance",
            NumberType.FLOAT_PLUS_EXP, halfDistance, style
        ) { it, _ -> halfDistance = it })
    }

}