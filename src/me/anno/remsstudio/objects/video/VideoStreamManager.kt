package me.anno.remsstudio.objects.video

import me.anno.Time
import me.anno.animation.LoopingState
import me.anno.cache.ICacheData
import me.anno.gpu.FinalRendering.isFinalRendering
import me.anno.gpu.GFX
import me.anno.input.Input
import me.anno.io.files.FileReference
import me.anno.maths.Maths.MILLIS_TO_NANOS
import me.anno.maths.Maths.ceilDiv
import me.anno.remsstudio.RemsStudio
import me.anno.remsstudio.objects.Transform
import me.anno.remsstudio.objects.video.Video.Companion.framesPerContainer
import me.anno.remsstudio.ui.editor.TimelinePanel
import me.anno.utils.structures.lists.Lists.any2
import me.anno.video.VideoStream
import me.anno.video.formats.gpu.GPUFrame
import kotlin.math.max
import kotlin.math.sign

class VideoStreamManager(val video: Video) : ICacheData {

    companion object {

        private var timeoutMillis = 250L

        fun isScrubbing(): Boolean {
            return !isFinalRendering &&
                    Input.isControlDown &&
                    GFX.windows.any2 { it.windowStack.inFocus0 is TimelinePanel }
        }
    }

    fun hasSimpleTime(): Boolean {
        return video.allInHierarchy {
            !it.timeAnimated.isAnimated
        }
    }

    fun absoluteTimeDilation(): Double {
        var result = 1.0
        var element: Transform = video
        while (true) {
            result *= sign(element.timeDilation.value)
            element = element.parent ?: return result
        }
    }

    fun isPlayingForward(): Boolean {
        val dilation = absoluteTimeDilation()
        return if (isFinalRendering) {
            dilation >= 0.0
        } else {
            RemsStudio.editorTimeDilation * dilation >= 0.0
        }
    }

    fun isCacheableVideo(): Boolean {
        val meta = video.meta ?: return false // ?? what do we answer here?
        return meta.videoFrameCount <= framesPerContainer
    }

    fun canUseVideoStreaming(): Boolean {
        return !isCacheableVideo() && !isScrubbing() && hasSimpleTime() && isPlayingForward()
    }

    private val lastFile: FileReference? get() = stream?.file
    private var lastScale = 0
    private var lastFPS = 0.0
    private var stream: VideoStream? = null
    private var timeoutNanos = 0L

    fun destroyIfUnused() {
        if (stream != null && timeoutNanos < Time.frameTimeNanos) {
            destroy()
        }
    }

    fun getFrame(scale: Int, frameIndex: Int, videoFPS: Double): GPUFrame? {
        val meta = video.meta ?: return null
        if (lastFile != meta.file || lastScale != scale || lastFPS != videoFPS) {
            stream?.destroy()
            // create new stream
            // is ceilDiv correct?? roundDiv instead???
            val maxSize = ceilDiv(max(meta.videoWidth, meta.videoHeight), scale)
            val stream = VideoStream(
                meta.file, meta, false,
                LoopingState.PLAY_LOOP, videoFPS, maxSize
            )
            lastScale = scale
            lastFPS = videoFPS
            stream.start(frameIndex / videoFPS)
            this.stream = stream
        }
        timeoutNanos = Time.frameTimeNanos + timeoutMillis * MILLIS_TO_NANOS
        val stream = stream!!
        // 6 is the number of extra frames needed for blank-frame removal; use 7 just to be sure
        val frame = stream.getFrame(frameIndex, 7)
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