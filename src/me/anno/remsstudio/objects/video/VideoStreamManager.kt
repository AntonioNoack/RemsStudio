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
import me.anno.remsstudio.ui.editor.cutting.FrameStatus
import me.anno.utils.structures.lists.Lists.any2
import me.anno.utils.structures.lists.Lists.firstOrNull2
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

        // 6 is the number of extra frames needed for blank-frame removal; use 7 just to be sure
        private val NUM_FRAMES_FOR_BLANK_FRAMES = 7
        private val MAX_PRELOADED_FRAMES = 8 // symmetry :3
        private val STREAM_CAPACITY = MAX_PRELOADED_FRAMES + NUM_FRAMES_FOR_BLANK_FRAMES
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
            dilation > 0.0
        } else {
            RemsStudio.editorTimeDilation * dilation > 0.0
        }
    }

    fun isCacheableVideo(): Boolean {
        val meta = video.meta ?: return false // ?? what do we answer here?
        return meta.videoFrameCount <= framesPerContainer
    }

    fun canUseVideoStreaming(): Boolean {
        return !isCacheableVideo() && !isScrubbing() &&
                hasSimpleTime() && isPlayingForward() &&
                video.isLooping.value == LoopingState.PLAY_ONCE
    }

    private val lastFile: FileReference? get() = stream?.file
    private var lastScale = 0
    private var lastFPS = 0.0
    var stream: VideoStream? = null
    private var timeoutNanos = 0L

    fun destroyIfUnused() {
        if (stream != null && timeoutNanos < Time.frameTimeNanos) {
            destroy()
        }
    }

    fun getFrame(scale: Int, frameIndex: Int, videoFPS: Double): GPUFrame? {
        val meta = video.meta ?: return null
        var stream = stream
        if (lastFile != meta.file || lastScale != scale || lastFPS != videoFPS || stream == null) {
            stream?.destroy()
            // create new stream
            // is ceilDiv correct?? roundDiv instead???
            val maxSize = ceilDiv(max(meta.videoWidth, meta.videoHeight), scale)
            stream = VideoStream(
                meta.file, meta, false,
                LoopingState.PLAY_LOOP, videoFPS, maxSize,
                STREAM_CAPACITY
            )
            lastScale = scale
            lastFPS = videoFPS
            stream.start(frameIndex / videoFPS)
            this.stream = stream
        }
        timeoutNanos = Time.frameTimeNanos + timeoutMillis * MILLIS_TO_NANOS
        val frame = stream.getFrame(frameIndex, NUM_FRAMES_FOR_BLANK_FRAMES)
        return when {
            frame == null -> null
            isFinalRendering && frameIndex != frame.frameIndex -> null
            !frame.isCreated -> null
            else -> frame
        }
    }

    fun getFrameStatus(frameIndex: Int): FrameStatus {
        val stream = stream ?: return FrameStatus.MISSING
        val frames = stream.sortedFrames
        val frame = synchronized(frames) {
            frames.firstOrNull2 { it.frameIndex == frameIndex }
        }
        return when {
            frame == null -> FrameStatus.MISSING
            frame.isCreated -> FrameStatus.STREAM_READY
            frame.isDestroyed -> FrameStatus.DESTROYED
            else -> FrameStatus.STREAMED_AWAITING_WAITING_GPU_UPLOAD
        }
    }

    override fun destroy() {
        stream?.destroy()
        stream = null
    }

}