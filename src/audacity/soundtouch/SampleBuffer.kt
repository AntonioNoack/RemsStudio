package audacity.soundtouch

import me.anno.utils.structures.arrays.FloatArrayList

/**
 * https://github.com/audacity/audacity/blob/cce2c7b8830a7bb651d225863b792d23f336323f/lib-src/soundtouch/source/SoundTouch/FIFOSampleBuffer.cpp
 * */
class SampleBuffer {

    private var channels = 0
    private var samplesInBuffer = 0

    private var bufferPos = 0

    val backend = FloatArrayList(1024)

    fun ptrBegin() = FloatPtr(backend, bufferPos * channels)

    fun ptrEnd(slackCapacity: Int): FloatPtr {
        val endOffset = samplesInBuffer * channels
        backend.ensureCapacity(endOffset + slackCapacity)
        return FloatPtr(backend, endOffset)
    }

    fun numSamples() = samplesInBuffer

    fun putSamples(samples: Int) {
        samplesInBuffer += samples
    }

    fun putSamples(ptr: FloatArray) {
        val samples = ptr.size / channels
        val values = samples * channels
        for (i in 0 until values) {
            backend += ptr[i]
        }
        samplesInBuffer += samples
    }

    fun putSamples(ptr: FloatPtr, samples: Int) {
        val values = samples * channels
        for (i in 0 until values) {
            backend += ptr[i]
        }
        samplesInBuffer += samples
    }

    fun setChannels(c: Int) {
        channels = c
    }

    fun clear() {
        samplesInBuffer = 0
        bufferPos = 0
    }

    fun receiveSamples(maxSamples: Int): Int {
        if (maxSamples >= samplesInBuffer) {
            val tmp = samplesInBuffer
            samplesInBuffer = 0
            return tmp
        }

        samplesInBuffer -= maxSamples
        bufferPos += maxSamples

        return maxSamples
    }

    fun isEmpty() = samplesInBuffer == 0

}