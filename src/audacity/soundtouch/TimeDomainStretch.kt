package audacity.soundtouch

import me.anno.audio.AudioPools.FAPool
import me.anno.maths.Maths.clamp
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Class, that does the time-stretch (tempo change) effect for the processed sound.
 * stretches auto while keeping the pitch
 * src: https://github.com/audacity/audacity/blob/cce2c7b8830a7bb651d225863b792d23f336323f/lib-src/soundtouch/source/SoundTouch/TDStretch.cpp
 * */
@Suppress("MemberVisibilityCanBePrivate")
class TimeDomainStretch {

    companion object {

        const val FLT_MIN = 1e-38

        // Table for the hierarchical mixing position seeking algorithm
        val scanOffsets = arrayOf(
            shortArrayOf(
                124, 186, 248, 310, 372, 434, 496, 558, 620, 682, 744, 806,
                868, 930, 992, 1054, 1116, 1178, 1240, 1302, 1364, 1426, 1488, 0
            ),
            shortArrayOf(
                -100, -75, -50, -25, 25, 50, 75, 100, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
            ),
            shortArrayOf(
                -20, -15, -10, -5, 5, 10, 15, 20, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
            ),
            shortArrayOf(
                -4, -3, -2, -1, 1, 2, 3, 4, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
            ),
            shortArrayOf(
                121, 114, 97, 114, 98, 105, 108, 32, 104, 99, 117, 111,
                116, 100, 110, 117, 111, 115, 0, 0, 0, 0, 0, 0
            )
        )

        /// Default values for sound processing parameters:
        /// Notice that the default parameters are tuned for contemporary popular music
        /// processing. For speech processing applications these parameters suit better:
        ///     val DEFAULT_SEQUENCE_MS     40
        ///     val DEFAULT_SEEKWINDOW_MS   15
        ///     val DEFAULT_OVERLAP_MS      8
        ///

        /// Default length of a single processing sequence, in milliseconds. This determines to how
        /// long sequences the original sound is chopped in the time-stretch algorithm.
        ///
        /// The larger this value is, the lesser sequences are used in processing. In principle
        /// a bigger value sounds better when slowing down tempo, but worse when increasing tempo
        /// and vice versa.
        ///
        /// Increasing this value reduces computational burden & vice versa.
        const val DEFAULT_SEQUENCE_MS = 40
        // const val DEFAULT_SEQUENCE_MS = USE_AUTO_SEQUENCE_LEN

        /// Giving this value for the sequence length sets automatic parameter value
        /// according to tempo setting (recommended)
        // const val USE_AUTO_SEQUENCE_LEN = false

        /// Seeking window default length in milliseconds for algorithm that finds the best possible
        /// overlapping location. This determines from how wide window the algorithm may look for an
        /// optimal joining location when mixing the sound sequences back together.
        ///
        /// The bigger this window setting is, the higher the possibility to find a better mixing
        /// position will become, but at the same time large values may cause a "drifting" artifact
        /// because consequent sequences will be taken at more uneven intervals.
        ///
        /// If there's a disturbing artifact that sounds as if a constant frequency was drifting
        /// around, try reducing this setting.
        ///
        /// Increasing this value increases computational burden & vice versa.
        const val DEFAULT_SEEKWINDOW_MS = 15
        // const val DEFAULT_SEEKWINDOW_MS =      USE_AUTO_SEEKWINDOW_LEN

        /// Giving this value for the seek window length sets automatic parameter value
        /// according to tempo setting (recommended)
        // const val USE_AUTO_SEEKWINDOW_LEN = 0

        /// Overlap length in milliseconds. When the chopped sound sequences are mixed back together,
        /// to form a continuous sound stream, this parameter defines over how long period the two
        /// consecutive sequences are let to overlap each other.
        ///
        /// This shouldn't be that critical parameter. If you reduce the DEFAULT_SEQUENCE_MS setting
        /// by a large amount, you might wish to try a smaller value on this.
        ///
        /// Increasing this value increases computational burden & vice versa.
        const val DEFAULT_OVERLAP_MS = 8

    }

    /*****************************************************************************
     *
     * Implementation of the class 'TDStretch'
     *
     *****************************************************************************/

    private var channels = 2
    var sampleReq = 0
    private var tempo = 1f

    var pMidBuffer: FloatArray? = null

    var overlapLength = 0
    var seekLength = 0
    var seekWindowLength = 0

    var nominalSkip = 0f
    var skipFract = 0f

    val outputBuffer = SampleBuffer()
    val inputBuffer = SampleBuffer()

    var quickSeekEnabled = false
    var sampleRate = 48000
    var sequenceMs = 0
    var seekWindowMs = 0
    var overlapMs = 0
    var autoSeekSetting = true
    var autoSeqSetting = true

    init {
        setParameters(48000, DEFAULT_SEQUENCE_MS, DEFAULT_SEEKWINDOW_MS, DEFAULT_OVERLAP_MS)
        setTempo(tempo)
        clear()
    }

    // Sets routine control parameters. These control are certain time constants
    // defining how the sound is stretched to the desired duration.
    //
    // 'sampleRate' = sample rate of the sound
    // 'sequenceMS' = one processing sequence length in milliseconds (default = 82 ms)
    // 'seekwindowMS' = seeking window length for scanning the best overlapping
    //      position (default = 28 ms)
    // 'overlapMS' = overlapping length (default = 12 ms)
    fun setParameters(aSampleRate: Int, aSequenceMS: Int, aSeekWindowMS: Int, aOverlapMS: Int) {

        // accept only positive parameter values - if zero or negative, use old values instead
        if (aSampleRate > 0) this.sampleRate = aSampleRate
        if (aOverlapMS > 0) this.overlapMs = aOverlapMS

        if (aSequenceMS > 0) {
            this.sequenceMs = aSequenceMS
            autoSeqSetting = false
        } else if (aSequenceMS == 0) {
            // if zero, use automatic setting
            autoSeqSetting = true
        }

        if (aSeekWindowMS > 0) {
            this.seekWindowMs = aSeekWindowMS
            autoSeekSetting = false
        } else if (aSeekWindowMS == 0) {
            // if zero, use automatic setting
            autoSeekSetting = true
        }

        calcSeqParameters()

        calculateOverlapLength(overlapMs)

        // set tempo to recalculate 'sampleReq'
        setTempo(tempo)

    }

    /**
     * Overlaps samples in 'midBuffer' with the samples in 'src'
     * */
    fun overlapMono(dst: FloatPtr, src: FloatPtr) {

        val fScale = 1f / overlapLength

        var m1 = 0f
        var m2 = 1f

        val midBuffer = pMidBuffer!!
        for (i in 0 until overlapLength) {
            dst[i] = src[i] * m1 + midBuffer[i] * m2
            m1 += fScale
            m2 -= fScale
        }
    }

    fun clearMidBuffer() {
        pMidBuffer!!.fill(0f, 0, 2 * overlapLength)
    }

    fun clearInput() {
        inputBuffer.clear()
        clearMidBuffer()
    }

    /**
     * Clears the sample buffers
     * */
    fun clear() {
        outputBuffer.clear()
        clearInput()
    }

    /**
     * Seeks for the optimal overlap-mixing position.
     * */
    fun seekBestOverlapPosition(refPos: FloatPtr): Int {
        return if (quickSeekEnabled) {
            seekBestOverlapPositionQuick(refPos)
        } else {
            seekBestOverlapPositionFull(refPos)
        }
    }

    /**
     * Overlaps samples in 'midBuffer' with the samples in 'pInputBuffer' at position of 'ovlPos'.
     * */
    fun overlap(dst: FloatPtr, src: FloatPtr, ovlPos: Int) {
        if (channels == 2) {
            // stereo sound
            overlapStereo(dst, src + 2 * ovlPos)
        } else {
            // mono sound.
            overlapMono(dst, src + ovlPos)
        }
    }

    /**
     * Seeks for the optimal overlap-mixing position. The 'stereo' version of the routine
     *
     * The best position is determined as the position where the two overlapped
     * sample sequences are 'most alike', in terms of the highest cross-correlation
     * value over the overlapping period
     * */
    fun seekBestOverlapPositionFull(refPos: FloatPtr): Int {

        var bestOffs: Int
        var bestCorr: Double
        var corr: Double

        bestCorr = FLT_MIN
        bestOffs = 0

        // Scans for the best correlation value by testing each possible position
        // over the permitted range.
        for (i in 0 until seekLength) {

            // Calculates correlation value for the mixing position corresponding to 'i'
            corr = calcCrossCorr(refPos + channels * i, pMidBuffer!!)
            // heuristic rule to slightly favour values close to mid of the range
            val tmp = (2 * i - seekLength) / seekLength.toDouble()
            corr = ((corr + 0.1) * (1.0 - 0.25 * tmp * tmp))

            // Checks for the highest correlation value
            if (corr > bestCorr) {
                bestCorr = corr
                bestOffs = i
            }
        }

        // clear cross correlation routine state if necessary (is so e.g. in MMX routines).
        clearCrossCorrState()

        return bestOffs
    }


    /**
     * Seeks for the optimal overlap-mixing position. The 'stereo' version of the routine
     *
     * The best position is determined as the position where the two overlapped
     * sample sequences are 'most alike', in terms of the highest cross-correlation
     * value over the overlapping period
     * */
    fun seekBestOverlapPositionQuick(refPos: FloatPtr): Int {

        var bestCorr = FLT_MIN
        var bestOffs = scanOffsets[0][0]
        var corrOffset = 0.toShort()

        // Scans for the best correlation value using four-pass hierarchical search.
        //
        // The look-up table 'scans' has hierarchical position adjusting steps.
        // In first pass the routine searches for the highest correlation with
        // relatively coarse steps, then rescans the neighbourhood of the highest
        // correlation with better resolution and so on.

        val pMidBuffer = pMidBuffer!!
        for (scanCount in 0 until 4) {
            var j = 0
            while (scanOffsets[scanCount][j] != 0.toShort()) {
                val tempOffset = (corrOffset + scanOffsets[scanCount][j]).toShort()
                if (tempOffset >= seekLength) break

                // Calculates correlation value for the mixing position corresponding
                // to 'tempOffset'
                var corr = calcCrossCorr(refPos + channels * tempOffset, pMidBuffer)
                // heuristic rule to slightly favour values close to mid of the range
                val tmp = (2 * tempOffset - seekLength) / seekLength
                corr = ((corr + 0.1) * (1.0 - 0.25 * tmp * tmp))

                // Checks for the highest correlation value
                if (corr > bestCorr) {
                    bestCorr = corr
                    bestOffs = tempOffset
                }
                j++
            }
            corrOffset = bestOffs
        }

        // clear cross correlation routine state if necessary (is so e.g. in MMX routines).
        clearCrossCorrState()

        return bestOffs.toInt()
    }


    /**
     * clear cross correlation routine state if necessary
     * */
    fun clearCrossCorrState() {
        // default implementation is empty.
    }

    /**
     * Calculates processing sequence length according to tempo setting
     * */
    private fun calcSeqParameters() {

        // Adjust tempo param according to tempo, so that varying processing sequence length is used
        // at various tempo settings, between the given low...top limits
        val tempoLow = 0.5     // auto setting low tempo range (-50%)
        val tempoHigh = 2.0     // auto setting top tempo range (+100%)

        // sequence-ms setting values at above low & top tempo
        val seqAtMin = 125.0
        val seqAtMax = 50.0
        val seqLinear = ((seqAtMax - seqAtMin) / (tempoHigh - tempoLow))
        val seqConst = (seqAtMin - (seqLinear) * (tempoLow))

        // seek-window-ms setting values at above low & top tempo
        val seekAtMin = 25.0
        val seekAtMax = 15.0
        val seekLinear = ((seekAtMax - seekAtMin) / (tempoHigh - tempoLow))
        val seekConst = (seekAtMin - (seekLinear) * (tempoLow))

        var seq: Double
        var seek: Double

        if (autoSeqSetting) {
            seq = seqConst + seqLinear * tempo
            seq = clamp(seq, seqAtMax, seqAtMin)
            sequenceMs = (seq + 0.5).toInt()
        }

        if (autoSeekSetting) {
            seek = seekConst + seekLinear * tempo
            seek = clamp(seek, seekAtMax, seekAtMin)
            seekWindowMs = (seek + 0.5).toInt()
        }

        // Update seek window lengths
        seekWindowLength = (sampleRate * sequenceMs) / 1000
        if (seekWindowLength < 2 * overlapLength) {
            seekWindowLength = 2 * overlapLength
        }
        seekLength = (sampleRate * seekWindowMs) / 1000
    }

    /**
     * Sets new target tempo. Normal tempo = 'SCALE', smaller values represent slower tempo, larger faster tempo.
     * */
    fun setTempo(newTempo: Float) {

        tempo = newTempo

        // Calculate new sequence duration
        calcSeqParameters()

        // Calculate ideal skip length (according to tempo value)
        nominalSkip = tempo * (seekWindowLength - overlapLength)
        val idealSkip = (nominalSkip + 0.5f).toInt()

        // Calculate how many samples are needed in the 'inputBuffer' to process another batch of samples
        // sampleReq = max(idealSkip + overlapLength, seekWindowLength) + seekLength / 2
        sampleReq = max(idealSkip + overlapLength, seekWindowLength) + seekLength
    }

    /**
     * Sets the number of channels, 1 = mono, 2 = stereo
     * */
    fun setChannels(numChannels: Int) {

        if (channels == numChannels) return
        assert(numChannels == 1 || numChannels == 2)

        channels = numChannels
        inputBuffer.setChannels(channels)
        outputBuffer.setChannels(channels)

    }

    /**
     * Processes as many processing frames of the samples 'inputBuffer', store the result into 'outputBuffer'
     * */
    fun processSamples() {
        // Process samples as long as there are enough samples in 'inputBuffer' to form a processing frame.
        while (inputBuffer.numSamples() >= sampleReq) {

            // If tempo differs from the normal ('SCALE'), scan for the best overlapping position
            val offset = seekBestOverlapPosition(inputBuffer.ptrBegin())

            // Mix the samples in the 'inputBuffer' at position of 'offset' with the
            // samples in 'midBuffer' using sliding overlapping
            // ... first partially overlap with the end of the previous sequence
            // (that's in 'midBuffer')
            overlap(outputBuffer.ptrEnd(overlapLength), inputBuffer.ptrBegin(), offset)
            outputBuffer.putSamples(overlapLength)

            // ... then copy sequence samples from 'inputBuffer' to output:

            // length of sequence
            val temp = (seekWindowLength - 2 * overlapLength)

            // crosscheck that we don't have buffer overflow...
            if (inputBuffer.numSamples() < (offset + temp + overlapLength * 2)) {
                // throw RuntimeException("Should not happen!")
                continue // just in case, shouldn't really happen
            }

            outputBuffer.putSamples(inputBuffer.ptrBegin() + channels * (offset + overlapLength), temp)

            // Copies the end of the current sequence from 'inputBuffer' to
            // 'midBuffer' for being mixed with the beginning of the next
            // processing sequence and so on
            assert((offset + temp + overlapLength * 2) <= inputBuffer.numSamples())

            memcpyFloats(
                pMidBuffer!!, inputBuffer.ptrBegin() + channels * (offset + temp + overlapLength),
                channels * overlapLength
            )

            // Remove the processed samples from the input buffer. Update
            // the difference between integer & nominal skip step to 'skipFract'
            // to prevent the error from accumulating over time.
            skipFract += nominalSkip // real skip size
            val ovlSkip = skipFract.toInt() // rounded to integer skip
            skipFract -= ovlSkip // maintain the fraction part, i.e., real vs. integer skip
            inputBuffer.receiveSamples(ovlSkip)
        }
    }

    private fun memcpyFloats(dst: FloatArray, src: FloatPtr, length: Int) {
        src.array.values.copyInto(dst, 0, src.offset, src.offset + length)
    }

    fun putSamples(samples: FloatArray) {
        // Add the samples into the input buffer
        inputBuffer.putSamples(samples)
        // Process the samples in input buffer
        processSamples()
    }

    /**
     * Set new overlap length parameter & reallocate RefMidBuffer if necessary.
     * */
    fun acceptNewOverlapLength(newOverlapLength: Int) {

        assert(newOverlapLength >= 0)
        val prevOvl = overlapLength
        overlapLength = newOverlapLength

        if (overlapLength > prevOvl) {
            // ensure that 'pMidBuffer' is aligned to 16 byte boundary for efficiency
            // impossible in Java, but maybe already done in the backend
            pMidBuffer = FAPool[overlapLength * 2, true, true]// +4, align
            clearMidBuffer()
        }
    }

    /**
     * Overlaps samples in 'midBuffer' with the samples in 'pInput'
     * */
    private fun overlapStereo(dst: FloatPtr, src: FloatPtr) {

        val fScale = 1f / overlapLength

        var f1 = 0f
        var f2 = 1f

        val midBuffer = pMidBuffer!!
        for (i in 0 until 2 * overlapLength step 2) {
            dst[i + 0] = src[i + 0] * f1 + midBuffer[i + 0] * f2
            dst[i + 1] = src[i + 1] * f1 + midBuffer[i + 1] * f2

            f1 += fScale
            f2 -= fScale
        }
    }

    /**
     * Calculates overlapInMilliseconds period length in samples.
     * */
    private fun calculateOverlapLength(overlapInMilliseconds: Int) {

        assert(overlapInMilliseconds >= 0)
        var newOvl = (sampleRate * overlapInMilliseconds) / 1000
        if (newOvl < 16) newOvl = 16

        // must be divisible by 8
        newOvl -= newOvl % 8

        acceptNewOverlapLength(newOvl)
    }

    private fun calcCrossCorr(mixingPos: FloatPtr, compare: FloatArray): Double {

        var corr = 0.0
        var norm = 0.0

        // Same routine for stereo and mono. For Stereo, unroll by factor of 2.
        // For mono it's same routine yet unrolled by factor of 4.
        for (i in 0 until channels * overlapLength step 4) {
            corr += mixingPos[i] * compare[i] + mixingPos[i + 1] * compare[i + 1]
            norm += mixingPos[i] * mixingPos[i] + mixingPos[i + 1] * mixingPos[i + 1]

            // unroll the loop for better CPU efficiency:
            corr += mixingPos[i + 2] * compare[i + 2] + mixingPos[i + 3] * compare[i + 3]
            norm += mixingPos[i + 2] * mixingPos[i + 2] + mixingPos[i + 3] * mixingPos[i + 3]
        }
        return corr / sqrt(max(norm, 1e-9))
    }

}