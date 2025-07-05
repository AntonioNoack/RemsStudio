package me.anno.remsstudio.objects.video

import me.anno.Time
import me.anno.animation.LoopingState
import me.anno.config.DefaultConfig
import me.anno.gpu.FinalRendering.isFinalRendering
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

    fun Video.getFrameAtLocalTimeForPreview(time: Double, width: Int, meta: MediaMetadata): GPUFrame? {

        // only load a single frame at a time?? idk...

        if (isFinalRendering) throw RuntimeException("Not supported")

        val sourceFPS = meta.videoFPS
        val duration = meta.videoDuration
        val isLooping = isLooping.value

        if (sourceFPS > 0.0 && time >= 0.0 && (isLooping != LoopingState.PLAY_ONCE || time <= duration)) {

            val rawZoomLevel = meta.videoWidth / width
            val scale = max(1, getCacheableZoomLevel(rawZoomLevel))

            val frameCount = max(1, (duration * sourceFPS).roundToInt())

            val localTime = isLooping[time, duration]
            val frameIndex = clamp((localTime * sourceFPS).toInt(), 0, frameCount - 1)
            val frameTime = Time.frameTimeNanos
            val frame = if (frameTime == lastFailureFrameTime) {
                // prevent loading too many frames at the same time -> just check whether it's available
                VideoCache.getVideoFrameWithoutGenerator(
                    file, scale, frameIndex, frameIndex,
                    1, sourceFPS
                )
            } else {
                VideoCache.getVideoFrame(
                    file, scale, frameIndex, frameIndex,
                    1, sourceFPS, previewTimeout, true
                ).value
            }
            if (frame != null && frame.isCreated) {
                return frame
            } else {
                lastFailureFrameTime = frameTime
            }
        }
        return null
    }
}