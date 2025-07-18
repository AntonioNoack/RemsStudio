package me.anno.remsstudio.audio

import me.anno.Time.gameTimeN
import me.anno.animation.LoopingState
import me.anno.audio.AudioCache.playbackSampleRate
import me.anno.audio.AudioPools.FAPool
import me.anno.audio.AudioPools.SAPool
import me.anno.audio.streams.AudioStreamRaw.Companion.bufferSize
import me.anno.cache.AsyncCacheData
import me.anno.cache.CacheData
import me.anno.cache.CacheSection
import me.anno.cache.ICacheData
import me.anno.gpu.GFX
import me.anno.io.MediaMetadata
import me.anno.io.files.FileReference
import me.anno.maths.Maths.clamp
import me.anno.maths.Maths.max
import me.anno.remsstudio.audio.effects.Domain
import me.anno.remsstudio.audio.effects.SoundEffect
import me.anno.remsstudio.audio.effects.SoundPipeline.Companion.changeDomain
import me.anno.remsstudio.audio.effects.Time
import me.anno.remsstudio.objects.Camera
import me.anno.remsstudio.objects.video.Video
import me.anno.utils.hpc.ProcessingQueue
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToLong

@Suppress("MemberVisibilityCanBePrivate")
object AudioFXCache2 {

    private val rawCache = CacheSection<PipelineKey, AudioData>("AudioFX-RS-RawCache")
    private val effectCache = CacheSection<PipelineKey, AudioData>("AudioFX-RS-EffectCache")
    private val rangeCache = CacheSection<RangeKey, ShortData>("AudioFX-RS-RangeCache")

    // limit the number of requests for performance,
    // and accumulating many for the future is useless,
    // when the user is just scrolling through time
    private const val rangeRequestLimit = 8

    var audioTimeoutMillis = 10000L

    val SPLITS = 256

    data class EffectKey(val effect: SoundEffect, val data: Any, val previous: EffectKey?)

    data class PipelineKey(
        val file: FileReference,
        val time0: Time,
        val time1: Time,
        val bufferSize: Int,
        val is3D: Boolean,
        val audioAlphaSerialized: String,
        val repeat: LoopingState,
        val effectKey: EffectKey?,
        val getTime: (i: Int) -> Time
    ) {

        val hashCode = calculateHashCode()
        override fun hashCode(): Int {
            return hashCode
        }

        val lastEffectKey = if (effectKey == null) null
        else PipelineKey(
            file, time0, time1, bufferSize, is3D,
            audioAlphaSerialized, repeat, effectKey.previous,
            getTime
        )

        fun withDelta(deltaIndex: Int): PipelineKey {
            if (deltaIndex == 0) return this
            return PipelineKey(
                file, getTime(deltaIndex), getTime(deltaIndex + 1),
                bufferSize, is3D, audioAlphaSerialized, repeat, effectKey
            ) { getTime(it + deltaIndex) }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true

            if (other !is PipelineKey) return false

            if (hashCode != other.hashCode) return false
            if (bufferSize != other.bufferSize) return false
            if (is3D != other.is3D) return false
            if (time0 != other.time0) return false
            if (time1 != other.time1) return false
            if (audioAlphaSerialized != other.audioAlphaSerialized) return false
            if (repeat != other.repeat) return false
            if (effectKey != other.effectKey) return false
            if (file != other.file) return false

            return true
        }

        private fun calculateHashCode(): Int {
            var result = bufferSize.hashCode()
            result = 31 * result + file.hashCode()
            result = 31 * result + time0.hashCode()
            result = 31 * result + time1.hashCode()
            result = 31 * result + is3D.hashCode()
            result = 31 * result + audioAlphaSerialized.hashCode()
            result = 31 * result + repeat.hashCode()
            result = 31 * result + (effectKey?.hashCode() ?: 0)
            result = 31 * result + (lastEffectKey?.hashCode() ?: 0)
            return result
        }

    }

    class AudioData(
        val key: PipelineKey,
        var timeLeft: FloatArray?,
        var freqLeft: FloatArray?,
        var timeRight: FloatArray?,
        var freqRight: FloatArray?
    ) : ICacheData {

        override fun equals(other: Any?): Boolean {
            return other === this || (other is AudioData && other.key == key)
        }

        override fun hashCode(): Int {
            return key.hashCode()
        }

        constructor(key: PipelineKey, left: FloatArray, right: FloatArray, timeDomain: Boolean) : this(
            key,
            if (timeDomain) left else null,
            if (timeDomain) null else left,
            if (timeDomain) right else null,
            if (timeDomain) null else right
        )

        constructor(key: PipelineKey, left: FloatArray, right: FloatArray, domain: Domain) : this(
            key, left, right, domain == Domain.TIME_DOMAIN
        )

        var isDestroyed = 0L
        override fun destroy() {
            // LOGGER.info("Destroying ${hashCode()} $key")
            // printStackTrace()
            GFX.checkIsGFXThread()
            // todo why is it being destroyed twice????
            /*if (isDestroyed > 0L){
                Engine.shutdown()
                throw IllegalStateException("Cannot destroy twice, now $gameTime, then: $isDestroyed!")
            }*/
            isDestroyed = gameTimeN
            /*FAPool.returnBuffer(timeLeft)
            FAPool.returnBuffer(freqLeft)
            FAPool.returnBuffer(timeRight)
            FAPool.returnBuffer(freqRight)*/
        }

        fun getBuffersOfDomain(dst: Domain): Pair<FloatArray, FloatArray> {
            val buffer = this
            var left = if (dst == Domain.FREQUENCY_DOMAIN) buffer.freqLeft else buffer.timeLeft
            var right = if (dst == Domain.FREQUENCY_DOMAIN) buffer.freqRight else buffer.timeRight
            if (left == null) {
                left = if (dst == Domain.TIME_DOMAIN) buffer.freqLeft else buffer.timeLeft
                left!!
                val left2 = left.copyOf() // FAPool[left.size]
                changeDomain(dst, left2)
                left = left2
                if (dst == Domain.TIME_DOMAIN) buffer.timeLeft = left else buffer.freqLeft = left
            }
            if (right == null) {
                right = if (dst == Domain.TIME_DOMAIN) buffer.freqRight else buffer.timeRight
                right!!
                val right2 = right.copyOf() // FAPool[right.size]
                changeDomain(dst, right2)
                right = right2
                if (dst == Domain.TIME_DOMAIN) buffer.timeRight = right else buffer.freqRight = right
            }
            return left to right
        }

    }

    fun getBuffer(
        source: Video,
        destination: Camera,
        pipelineKey: PipelineKey,
        domain: Domain,
        async: Boolean
    ): Pair<FloatArray, FloatArray>? {
        val buffer = getBuffer(source, destination, pipelineKey, async) ?: return null
        return buffer.getBuffersOfDomain(domain)
    }

    fun getRawData(
        meta: MediaMetadata,
        source: Video?,
        destination: Camera?,
        key: PipelineKey
    ): AsyncCacheData<AudioData> {
        // we cannot simply return null from this function, so getEntryLimited isn't an option
        return rawCache.getEntry(key, timeout) { key, result ->
            val stream = AudioStreamRaw2(
                key.file, key.repeat,
                meta, key.is3D,
                source, destination
            )
            val pair = stream.getBuffer(key.bufferSize, key.time0.globalTime, key.time1.globalTime)
            result.value = AudioData(key, convert(pair.first), convert(pair.second), Domain.TIME_DOMAIN)
        }
    }

    fun convert(src: ShortArray): FloatArray {
        val dst = FAPool[src.size, false, true]
        for (idx in src.indices) dst[idx] = src[idx].toFloat()
        return dst
    }

    fun convert(src: FloatArray): ShortArray {
        val dst = SAPool[src.size, false, true]
        for (idx in src.indices) dst[idx] = src[idx].toInt().toShort()
        return dst
    }

    fun getBuffer0(
        meta: MediaMetadata,
        source: Video?,
        destination: Camera?,
        pipelineKey: PipelineKey,
        async: Boolean
    ): AudioData? {
        val effectKey = pipelineKey.effectKey
        if (effectKey != null) throw IllegalStateException()
        return getRawData(meta, source, destination, pipelineKey)
            .waitFor(async)
    }

    fun getBuffer0(
        meta: MediaMetadata,
        pipelineKey: PipelineKey,
        async: Boolean
    ): AudioData? = getBuffer0(meta, null, null, pipelineKey, async)

    fun getBuffer(
        source: Video,
        destination: Camera,
        key1: PipelineKey,
        async: Boolean
    ): AudioData? {
        if (key1.effectKey == null) {
            // get raw data
            val meta = source.forcedMeta ?: return null
            return getRawData(meta, source, destination, key1)
                .waitFor(async)
        }
        return effectCache.getEntry(key1, timeout) { key, result ->
            // get previous data, and process it
            val effect = key.effectKey!!.effect
            val previousKey = key.lastEffectKey!!
            val left = FAPool[bufferSize, true, true]
            val right = FAPool[bufferSize, true, true]
            val cachedSolutions = HashMap<Int, Pair<FloatArray, FloatArray>>()
            effect.apply({ deltaIndex ->
                cachedSolutions.getOrPut(deltaIndex) {
                    getBuffer(source, destination, previousKey.withDelta(deltaIndex), effect.inputDomain, false)!!
                }.first
            }, left, source, destination, key.time0, key.time1)
            effect.apply({ deltaIndex ->
                cachedSolutions.getOrPut(deltaIndex) {
                    getBuffer(source, destination, previousKey.withDelta(deltaIndex), effect.inputDomain, false)!!
                }.second
            }, right, source, destination, key.time0, key.time1)
            result.value = AudioData(key, left, right, effect.outputDomain)
        }.waitFor(async)
    }

    fun getBuffer(
        source: Video, destination: Camera,
        bufferSize: Int,
        domain: Domain,
        async: Boolean,
        getTime: (i: Int) -> Time
    ) = getBuffer(source, destination, getKey(source, destination, bufferSize, getTime), domain, async)

    fun getBuffer(index: Long, stream: AudioFileStream2, async: Boolean): Pair<FloatArray, FloatArray>? =
        getBuffer(stream.source, stream.destination, bufferSize, Domain.TIME_DOMAIN, async) {
            stream.getTime(index + it)
        }

    fun getRange(
        bufferSize: Int,
        t0: Double,
        t1: Double,
        identifier: String,
        source: Video,
        destination: Camera,
        async: Boolean = true
    ): ShortArray? {
        val index0 = (t0 * playbackSampleRate).roundToLong()// and (bufferSize-1).inv().toLong()
        var index1 = (t1 * playbackSampleRate).roundToLong()
        index1 = StrictMath.max(index1, index0 + SPLITS)
        // what if dt is too large, because we are viewing it from a distance -> approximate
        return getRange(bufferSize, index0, index1, identifier, source, destination, async)
    }

    private fun getTime(index: Long, audio: Video): Time {
        val globalTime = index * bufferSize.toDouble() / playbackSampleRate
        val localTime = audio.getLocalTimeFromRoot(globalTime, false)
        return Time(localTime, globalTime)
    }

    private val rangingProcessing = ProcessingQueue("AudioFX")
    private val rangingProcessing2 = ProcessingQueue("AudioFX-2")

    data class RangeKey(val i0: Long, val i1: Long, val identifier: String) {

        override fun equals(other: Any?): Boolean {
            if (other === this) return true
            if (other !is RangeKey) return false
            return other.i0 == i0 && other.i1 == i1 && other.identifier == identifier
        }

        val hashCode = 31 * (31 * i0.hashCode() + i1.hashCode()) + identifier.hashCode()
        override fun hashCode(): Int {
            return hashCode
        }

    }

    class ShortData : CacheData<ShortArray?>(null) {
        override fun destroy() {
            SAPool.returnBuffer(value)
        }
    }

    fun getRange(
        bufferSize: Int,
        index0: Long,
        index1: Long,
        identifier: String,
        source: Video,
        destination: Camera,
        async: Boolean = true
    ): ShortArray? {
        val queue = if (async) rangingProcessing2 else null
        return rangeCache.getEntryLimited(
            RangeKey(index0, index1, identifier),
            audioTimeoutMillis,
            queue,
            rangeRequestLimit
        ) { key, result ->
            val data = ShortData()
            rangingProcessing += {
                val splits = SPLITS
                val values = SAPool[splits * 2, false, true]
                var lastBufferIndex = 0L
                lateinit var buffer: Pair<FloatArray, FloatArray>
                val bufferSizeM1 = bufferSize - 1
                for (split in 0 until splits) {

                    var minV = +1e5f
                    var maxV = -1e5f

                    val deltaIndex = index1 - index0
                    val index0i = index0 + deltaIndex * split / splits
                    val index1i = StrictMath.min(index0i + 256, index0 + deltaIndex * (split + 1) / splits)
                    for (i in index0i until index1i) {

                        val bufferIndex = Math.floorDiv(i, bufferSize.toLong())
                        if (i == index0 || lastBufferIndex != bufferIndex) {
                            buffer = getBuffer(
                                source, destination, bufferSize,
                                Domain.TIME_DOMAIN, false
                            ) { getTime(bufferIndex + it, source) }!!
                            lastBufferIndex = bufferIndex
                        }

                        val localIndex = i.toInt() and bufferSizeM1
                        val v0 = buffer.first[localIndex]
                        val v1 = buffer.second[localIndex]

                        minV = min(minV, v0)
                        minV = min(minV, v1)
                        maxV = max(maxV, v0)
                        maxV = max(maxV, v1)

                    }

                    val minInt = floor(minV).toInt()
                    val maxInt = ceil(maxV).toInt()
                    values[split * 2 + 0] = clamp(minInt, -32768, 32767).toShort()
                    values[split * 2 + 1] = clamp(maxInt, -32768, 32767).toShort()

                }
                data.value = values
            }
            result.value = data
        }?.value?.value
    }

    fun getKey(
        source: Video, destination: Camera, bufferSize: Int,
        getTime: (i: Int) -> Time
    ): PipelineKey {
        val time0 = getTime(0)
        val time1 = getTime(1)
        var effectKeyI: EffectKey? = null
        val pipeline = source.pipeline
        for (effect in pipeline.effects) {
            val state = effect.getStateAsImmutableKey(source, destination, time0, time1)
            effectKeyI = EffectKey(effect, state, effectKeyI)
        }
        return PipelineKey(
            source.file, time0, time1, bufferSize, source.is3D,
            "${source.amplitude},${source.color}",
            source.isLooping.value,
            effectKeyI, getTime
        )
    }

    fun getIndex(time: Double, bufferSize: Int, sampleRate: Int): Double {
        return time * sampleRate.toDouble() / bufferSize
    }

    fun getTime(index: Long, bufferSize: Int, sampleRate: Int): Double {
        return index * bufferSize.toDouble() / sampleRate
    }

    fun getTime(index: Long, fraction: Double, bufferSize: Int, sampleRate: Int): Double {
        return (index + fraction) * bufferSize.toDouble() / sampleRate
    }

    private const val timeout = 20_000L // audio needs few memory, so we can keep all recent audio

}