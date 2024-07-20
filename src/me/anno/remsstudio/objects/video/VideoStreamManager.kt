package me.anno.remsstudio.objects.video

import me.anno.Engine
import me.anno.animation.LoopingState
import me.anno.cache.ICacheData
import me.anno.gpu.GFX.isFinalRendering
import me.anno.io.files.FileReference
import me.anno.maths.Maths.ceilDiv
import me.anno.remsstudio.RemsStudio
import me.anno.utils.Logging.hash32
import me.anno.video.VideoStream
import me.anno.video.formats.gpu.GPUFrame
import kotlin.math.max
import kotlin.math.sign

class VideoStreamManager(val video: Video) : ICacheData {

    fun hasSimpleTime(): Boolean {
        return video.allInHierarchy {
            !it.timeAnimated.isAnimated
        }
    }

    fun absoluteTimeDilation(): Double {
        return video.listOfHierarchy.map {
            sign(it.timeDilation.value)
        }.reduceRight { d, acc -> d * acc }
    }

    fun isPlayingForward(): Boolean {
        val dilation = absoluteTimeDilation()
        return if (isFinalRendering) {
            dilation > 0.0
        } else {
            RemsStudio.editorTimeDilation * dilation >= 0.0
        }
    }

    fun isCacheableVideo(): Boolean {
        val meta = video.meta ?: return false // ?? what do we answer here?
        return meta.videoFrameCount < 100 // what do we set here??
    }

    // todo support blank-frame-detection in streamed playback

    fun canUseVideoStreaming(): Boolean {
        return !video.usesBlankFrameDetection() && !isCacheableVideo() &&
                hasSimpleTime() && isPlayingForward()
    }

    private val lastFile: FileReference? get() = stream?.file
    private var lastScale = 0
    private var lastFPS = 0.0
    private var stream: VideoStream? = null
    private var wasFinalRendering: Boolean? = null

    fun getFrame(scale: Int, frameIndex: Int, videoFPS: Double): GPUFrame? {
        val meta = video.meta ?: return null
        if (wasFinalRendering != null) {
            if (wasFinalRendering != isFinalRendering) {
                Engine.requestShutdown()
                throw IllegalStateException("Changed from $wasFinalRendering to $isFinalRendering")
            }
        }
        wasFinalRendering = isFinalRendering
        if (lastFile != video.file || lastScale != scale || lastFPS != videoFPS) {
            stream?.destroy()
            // create new stream
            // is ceilDiv correct?? roundDiv instead???
            val maxSize = ceilDiv(max(meta.videoWidth, meta.videoHeight), scale)
            val stream = VideoStream(
                video.file, meta, false,
                LoopingState.PLAY_LOOP, videoFPS, maxSize
            )
            lastScale = scale
            lastFPS = videoFPS
            val startTime = (frameIndex) / videoFPS
            stream.start(startTime)
            this.stream = stream
        }
        val stream = stream!!
        val frame = stream.getFrame(frameIndex)
        return when {
            frame == null -> null
            isFinalRendering && frameIndex != frame.frameIndex -> null
            !frame.isCreated -> null
            else -> frame
        }
    }

    override fun destroy() {
        stream?.destroy()
        stream = null
    }

}