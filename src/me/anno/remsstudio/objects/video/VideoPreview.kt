package me.anno.remsstudio.objects.video

import me.anno.Time
import me.anno.animation.LoopingState
import me.anno.config.DefaultConfig
import me.anno.io.MediaMetadata
import me.anno.maths.Maths.clamp
import me.anno.remsstudio.objects.video.VideoSize.getCacheableZoomLevel
import me.anno.video.VideoCache
import me.anno.video.formats.gpu.GPUFrame
import kotlin.math.max
import kotlin.math.roundToInt

object VideoPreview {

    private val previewTimeout: Long
        get() = DefaultConfig["ui.video.previewTimeout", 500L]

    private var lastFailureFrameTime = 0L

    private fun isTimeValid(sourceFPS: Double, time: Double, isLooping: LoopingState, duration: Double): Boolean {
        return sourceFPS > 0.0 && time >= 0.0 && (isLooping != LoopingState.PLAY_ONCE || time <= duration)
    }

    fun Video.getFrameIndexForPreview(time: Double, meta: MediaMetadata): Int {
        val sourceFPS = meta.videoFPS
        val duration = meta.videoDuration
        val isLooping = isLooping.value

        val frameCount = max(1, (duration * sourceFPS).roundToInt())
        val localTime = isLooping[time, duration]
        val frameIndex = clamp((localTime * sourceFPS).toInt(), 0, frameCount - 1)
        return frameIndex
    }

    private fun getFrameScaleForPreview(width: Int, meta: MediaMetadata): Int {
        val rawZoomLevel = meta.videoWidth / width
        return max(1, getCacheableZoomLevel(rawZoomLevel))
    }

    fun Video.getFrameAtLocalTimeForPreview(time: Double, width: Int, meta: MediaMetadata): GPUFrame? {

        val sourceFPS = meta.videoFPS
        val duration = meta.videoDuration
        val isLooping = isLooping.value

        if (!isTimeValid(sourceFPS, time, isLooping, duration)) {
            return null
        }

        val frameIndex = getFrameIndexForPreview(time, meta)
        if (frameIndex < 0) return null

        val scale = getFrameScaleForPreview(width, meta)
        val frameTime = Time.frameTimeNanos
        val frame = if (frameTime == lastFailureFrameTime) {
            // prevent loading too many frames at the same time -> just check whether it's available
            VideoCache.getVideoFrameWithoutGenerator(
                file, scale, frameIndex, frameIndex,
                1, sourceFPS
            ).value
        } else {
            VideoCache.getVideoFrameImpl(
                file, scale, frameIndex, frameIndex,
                1, sourceFPS, previewTimeout
            ).value
        }
        if (frame != null && frame.isCreated) {
            return frame
        } else {
            lastFailureFrameTime = frameTime
            return null
        }
    }
}