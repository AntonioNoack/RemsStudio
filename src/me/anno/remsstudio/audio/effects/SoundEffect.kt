package me.anno.remsstudio.audio.effects

import me.anno.engine.EngineBase.Companion.workspace
import me.anno.engine.inspector.Inspectable
import me.anno.io.json.saveable.JsonStringReader
import me.anno.io.saveable.Saveable
import me.anno.remsstudio.objects.Camera
import me.anno.remsstudio.objects.video.Video

abstract class SoundEffect(val inputDomain: Domain, val outputDomain: Domain) : Saveable(), Inspectable {

    // for the inspector
    lateinit var audio: Video

    abstract fun apply(
        getDataSrc: (Int) -> FloatArray,
        dataDst: FloatArray,
        source: Video,
        destination: Camera,
        time0: Time, time1: Time,
    )

    abstract fun getStateAsImmutableKey(
        source: Video,
        destination: Camera,
        time0: Time, time1: Time
    ): Any

    abstract val displayName: String
    abstract val description: String

    open fun clone() = JsonStringReader.read(toString(), workspace, true).first() as SoundEffect

    override val approxSize get() = 10
    override fun isDefaultValue() = false

}