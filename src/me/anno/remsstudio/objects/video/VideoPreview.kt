package me.anno.remsstudio.objects.video

import me.anno.animation.LoopingState
import me.anno.gpu.GFX.isFinalRendering
import me.anno.io.MediaMetadata
import me.anno.maths.Maths.clamp
import me.anno.remsstudio.objects.video.VideoSize.getCacheableZoomLevel
import me.anno.video.formats.gpu.GPUFrame
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object VideoPreview {

    fun Video.getFrameAtLocalTimeForPreview(time: Double, width: Int, meta: MediaMetadata): GPUFrame? {

        // only load a single frame at a time?? idk...

        if (isFinalRendering) throw RuntimeException("Not supported")

        val sourceFPS = meta.videoFPS
        val duration = meta.videoDuration
        val isLooping = isLooping.value

        if (sourceFPS > 0.0) {
            if (time >= 0.0 && (isLooping != LoopingState.PLAY_ONCE || time <= duration)) {

                val rawZoomLevel = meta.videoWidth / width
                val scale = max(1, getCacheableZoomLevel(rawZoomLevel))

                val videoFPS = min(sourceFPS, editorVideoFPS.value.toDouble())
                val frameCount = max(1, (duration * videoFPS).roundToInt())

                // draw the current texture
                val localTime = isLooping[time, duration]
                val frameIndex = clamp((localTime * videoFPS).toInt(), 0, frameCount - 1)

                val frame = getVideoFrame(scale, frameIndex, videoFPS)

                if (frame != null && frame.isCreated) {
                    lastW = frame.width
                    lastH = frame.height
                    return frame
                }
            }
        }

        return null

    }

}