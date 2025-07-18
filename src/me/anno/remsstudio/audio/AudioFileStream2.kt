package me.anno.remsstudio.audio

import me.anno.animation.LoopingState
import me.anno.audio.streams.AudioFileStream
import me.anno.cache.AsyncCacheData
import me.anno.io.MediaMetadata
import me.anno.io.MediaMetadata.Companion.getMeta
import me.anno.io.files.FileReference
import me.anno.remsstudio.audio.AudioFXCache2.convert
import me.anno.remsstudio.audio.effects.Time
import me.anno.remsstudio.objects.Camera
import me.anno.remsstudio.objects.video.Video

// only play once, then destroy; it makes things easier
// (on user input and when finally rendering only)

// done viewing the audio levels is more important than effects
// done especially editing the audio levels is important (amplitude)


// idk does not work, if the buffers aren't filled fast enough -> always fill them fast enough...
// idk or restart playing...

/**
 * todo audio effects:
 * done better echoing ;)
 * todo velocity frequency change
 * done pitch
 * todo losing high frequencies in the distance
 * done audio becoming quiet in the distance
 * */
open class AudioFileStream2(
    file: FileReference,
    repeat: LoopingState,
    startIndex: Long,
    meta: MediaMetadata,
    val source: Video,
    val destination: Camera,
    speed: Double,
    playbackSampleRate: Int = 48000
) : AudioFileStream(file, repeat, startIndex, meta, speed, playbackSampleRate, true, false, true) {

    constructor(audio: Video, speed: Double, globalTime: Double, playbackSampleRate: Int, listener: Camera) :
            this(
                audio.file, audio.isLooping.value,
                getIndex(globalTime, speed, playbackSampleRate),
                getMeta(audio.file).waitFor()!!,
                audio, listener, speed, playbackSampleRate
            )

    init {
        source.pipeline.audio = source
    }

    fun getTime(index: Long): Time = getTime(frameIndexToTime(index))
    private fun getTime(globalTime: Double): Time = Time(globalToLocalTime(globalTime), globalTime)

    override fun getBuffer(bufferIndex: Long): AsyncCacheData<Pair<ShortArray?, ShortArray?>> {
        val data = AudioFXCache2.getBuffer(bufferIndex, this, false)!!
        return AsyncCacheData(Pair(convert(data.first), convert(data.second)))
    }

    // todo is this correct with the speed?
    private fun globalToLocalTime(time: Double) = source.getGlobalTime(time * speed)

}