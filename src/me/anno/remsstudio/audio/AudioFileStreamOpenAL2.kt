package me.anno.remsstudio.audio

import me.anno.animation.LoopingState
import me.anno.audio.AudioCache.playbackSampleRate
import me.anno.audio.openal.ALBase
import me.anno.audio.openal.AudioTasks.addAudioTask
import me.anno.audio.openal.AudioTasks.addNextAudioTask
import me.anno.audio.openal.SoundBuffer
import me.anno.audio.openal.SoundSource
import me.anno.io.MediaMetadata
import me.anno.io.files.FileReference
import me.anno.remsstudio.objects.Camera
import me.anno.remsstudio.objects.video.Video
import org.apache.logging.log4j.LogManager
import org.lwjgl.openal.AL10.*
import java.nio.ByteBuffer
import java.nio.ShortBuffer
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max

// only play once, then destroy; it makes things easier
// (on user input and when finally rendering only)

// done? destroy OpenAL context when stopping playing, and recreate when starting
// done? to fix "AL lib: (EE) ALCwasapiPlayback_mixerProc: Failed to get padding: 0x88890004"
// we fixed sth like that

@Suppress("MemberVisibilityCanBePrivate")
class AudioFileStreamOpenAL2(
    file: FileReference,
    repeat: LoopingState,
    var startTime: Double,
    meta: MediaMetadata,
    sender: Video,
    listener: Camera,
    speed: Double
) : AudioFileStream2(
    file, repeat,
    getIndex(startTime, speed, playbackSampleRate),
    meta, sender, listener, speed, playbackSampleRate
) {

    constructor(audio: Video, speed: Double, globalTime: Double, listener: Camera) :
            this(
                audio.file, audio.isLooping.value, globalTime,
                MediaMetadata.getMeta(audio.file).waitFor()!!,
                audio, listener, speed
            )

    companion object {
        private val LOGGER = LogManager.getLogger(AudioFileStreamOpenAL2::class)
    }

    private val startTime0 = startTime

    var startTimeNanos = 0L
    var realStartTimeNanos = 0L
    val alSource by lazy { SoundSource(false, true) }

    var queued = AtomicLong()
    var processed = 0

    val buffers = ArrayList<SoundBuffer>()

    fun checkProcessed() {
        processed = alGetSourcei(alSource.sourcePtr, AL_BUFFERS_PROCESSED)
        ALBase.check()
    }

    fun start() {
        if (!isPlaying) {
            isPlaying = true
            startTimeNanos = System.nanoTime()
            realStartTimeNanos = startTimeNanos
            waitForRequiredBuffers()
        } else throw RuntimeException()
    }

    fun stop() {
        if (!isPlaying) return
        isPlaying = false
        alSource.stop()
        alSource.destroy()
        ALBase.check()
        // let's hope it works
        for (buffer in buffers) {
            buffer.destroy()
        }
        // ALBase.check()
        // somehow crashes..., buffers can't be reused either (without error)
        // buffers.toSet().forEach { it.destroy() }
        // ALBase.check()
    }

    val cachedBuffers = 10

    fun checkSession() {
        if (alSource.checkSessionWasReset()) {
            // reset all progress
            val time = System.nanoTime()
            // find start time and start index
            val deltaTime = (time - realStartTimeNanos) * 1e-9
            startTime = startTime0 + deltaTime * speed
            startIndex = getIndex(startTime, speed, playbackSampleRate)
            hadFirstBuffer = false
            queued.set(0)
            processed
            startTimeNanos = time
        }
    }

    fun waitForRequiredBuffers() {
        if (!isPlaying) return
        checkSession()
        val queued = queued.get()
        if (!isWaitingForBuffer && queued > 0) checkProcessed()
        // keep 2 on reserve
        if (queued < processed + cachedBuffers && !isWaitingForBuffer) {
            // request a buffer
            // only one at a time
            val index = startIndex + this.queued.getAndIncrement()
            // loading $index...
            requestNextBuffer(index, alSource.session)
        }
        if (isPlaying) {
            addNextAudioTask("wait", 1) {
                waitForRequiredBuffers()
                ALBase.check()
            }
        }
    }

    var hadFirstBuffer = false

    override fun onBufferFilled(stereoBuffer: ShortBuffer, byteBuffer: ByteBuffer, bufferIndex: Long, session: Int): Boolean {

        if (!isPlaying) return true

        addAudioTask("fill", 10) {
            if (isPlaying) {

                checkSession()

                // wait until we have enough data to be played back
                // then it needs to work like this, because that's already perfect:

                // load audio continuously the whole time, so we have it, when it's required

                if (!hadFirstBuffer) {

                    val dt = max(0f, (System.nanoTime() - startTimeNanos) * 1e-9f)

                    val startOffset = getFraction(startTime, speed, playbackSampleRate)
                    val samples = dt * playbackSampleRate + startOffset
                    val capacity = stereoBuffer.capacity()
                    val targetIndex = samples.toInt() * 2 - (bufferIndex - startIndex) * capacity
                    if (targetIndex < 0 && -targetIndex >= capacity) {
                        // this buffer is too new (?)...
                        LOGGER.warn("Buffer is too new, probably something has changed")
                        // return@addTask
                    }

                    if (capacity > targetIndex + 256 && targetIndex >= 0) {

                        LOGGER.info("Skipping ${dt}s, $targetIndex/$capacity")
                        stereoBuffer.position(targetIndex.toInt())

                    } else {
                        // else delayed, but we have no alternative
                        LOGGER.warn("Skipping first ${dt}s = first buffer completely")
                    }

                    hadFirstBuffer = true

                }

                ALBase.check()
                val soundBuffer = SoundBuffer()
                ALBase.check()
                soundBuffer.loadRaw16(stereoBuffer, byteBuffer, playbackSampleRate, AL_FORMAT_STEREO16)
                soundBuffer.ensureData()
                buffers.add(soundBuffer)

                ALBase.check()

                alSourceQueueBuffers(alSource.sourcePtr, soundBuffer.pointer)
                ALBase.check()

                alSource.play()
                ALBase.check()

                // time += openALSliceDuration
                isWaitingForBuffer = false
                ALBase.check()

            }
        }

        return false

    }

}