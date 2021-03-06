package me.anno.audio.openal

import me.anno.audio.openal.AudioManager.openALSession
import me.anno.cache.data.ICacheData
import org.lwjgl.openal.AL10.*
import org.lwjgl.stb.STBVorbis.*
import org.lwjgl.stb.STBVorbisInfo
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.system.MemoryUtil.NULL
import org.newdawn.slick.openal.WaveData
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ShortBuffer
import java.util.*

class SoundBuffer: ICacheData {

    var buffer = -1
    var session = -1
    var data: ShortBuffer? = null // pcm = amplitudes by time
    var sampleRate = 0

    fun ensurePointer(){
        if(buffer < 0 || session != openALSession){
            buffer = alGenBuffers()
            session = openALSession
        }
        if(buffer < 0) throw RuntimeException("Failed to create OpenAL buffer")
    }

    fun ensureData(){
        if(data == null) throw RuntimeException("Missing data!")
        ensurePointer()
        ALBase.check()
        alBufferData(buffer, AL_FORMAT_STEREO16, data!!, sampleRate)
        ALBase.check()
    }

    constructor()

    constructor(file: File): this(){
        load(file)
    }

    constructor(waveData: WaveData): this(){
        loadWAV(waveData)
    }

    fun loadRawStereo16(data: ShortBuffer, sampleRate: Int){
        this.data = data
        this.sampleRate = sampleRate
    }

    fun loadWAV(waveData: WaveData){
        ensurePointer()
        data = waveData.data.asShortBuffer()
        alBufferData(buffer, waveData.format, waveData.data, waveData.samplerate)
        waveData.dispose()
        ALBase.check()
    }

    fun loadOGG(file: File){
        STBVorbisInfo.malloc().use { info ->
            val pcm = readVorbis(file, info)
            val format = if(info.channels() == 1) AL_FORMAT_MONO16 else AL_FORMAT_STEREO16
            ensurePointer()
            alBufferData(buffer, format, pcm, info.sample_rate())
            ALBase.check()
        }
    }

    fun load(file: File) {
        val name = file.name
        when (val ending = name.split('.').last().lowercase(Locale.getDefault())) {
            "ogg" -> loadOGG(file)
            "wav" -> loadWAV(WaveData.create(file.inputStream()))
            else -> throw RuntimeException("Unknown audio format $ending!")
        }
    }

    private fun readVorbis(file: File, info: STBVorbisInfo): ShortBuffer {
        MemoryStack.stackPush().use { stack ->
            val vorbis = ioResourceToByteBuffer(file)
            val error = stack.mallocInt(1)
            val decoder: Long = stb_vorbis_open_memory(vorbis, error, null)
            ALBase.check()
            if (decoder == NULL) {
                throw RuntimeException("Failed to open Ogg Vorbis file. Error: " + error[0])
            }
            stb_vorbis_get_info(decoder, info)
            val channels = info.channels()
            val lengthSamples = stb_vorbis_stream_length_in_samples(decoder)
            val pcm = MemoryUtil.memAllocShort(lengthSamples)
            this.data = pcm
            pcm.limit(stb_vorbis_get_samples_short_interleaved(decoder, channels, pcm) * channels)
            stb_vorbis_close(decoder)
            return pcm
        }
    }

    fun ioResourceToByteBuffer(file: File): ByteBuffer {
        val bytes = file.readBytes()
        val buffer = ByteBuffer
            .allocateDirect(bytes.size)
            .order(ByteOrder.nativeOrder())
        buffer.position(0)
        buffer.put(bytes)
        buffer.position(0)
        return buffer
    }

    override fun destroy(){
        if(buffer > -1 && session == openALSession){
            alDeleteBuffers(buffer)
            buffer = -1
        }
        if(data != null){
            // val toFree = pcm
            data = null
            // crashes Java 8, even with 15s delay
            // MemoryUtil.memFree(toFree)
        }
    }

}