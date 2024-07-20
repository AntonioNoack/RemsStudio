package me.anno.remsstudio.objects.video

import me.anno.animation.LoopingState
import me.anno.gpu.GFX.isFinalRendering
import me.anno.gpu.texture.TextureCache
import me.anno.maths.Maths.clamp
import me.anno.maths.Maths.mix
import me.anno.remsstudio.objects.video.Video.Companion.framesPerContainer
import me.anno.remsstudio.objects.video.Video.Companion.videoFrameTimeout
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object VideoResourceClaiming {

    fun Video.claimImage() {
        val texture = getImage()
        if (lastTexture != texture) {
            needsImageUpdate = true
            lastTexture = texture
        }
    }

    fun Video.claimVideo(minT: Double, maxT: Double) {

        if (streamManager.canUseVideoStreaming()) {
            // skipping any claims
            return
        }

        val meta = meta
        if (meta != null) {

            val sourceFPS = meta.videoFPS
            val duration = meta.videoDuration
            val isLooping = isLooping.value

            if (sourceFPS > 0.0) {
                if (maxT >= 0.0 && (stayVisibleAtEnd || isLooping != LoopingState.PLAY_ONCE || minT < duration)) {

                    // use full fps when rendering to correctly render at max fps with time dilation
                    // issues arise, when multiple frames should be interpolated together into one
                    // at this time, we chose the center frame only.
                    val videoFPS =
                        if (isFinalRendering) sourceFPS else min(sourceFPS, editorVideoFPS.value.toDouble())

                    // calculate how many buffers are required from start to end
                    // clamp to max number of buffers, or maybe 20
                    val buff0 = (minT * videoFPS).toInt()
                    val buff1 = (maxT * videoFPS).toInt()
                    val steps = clamp(2 + (buff1 - buff0) / framesPerContainer, 2, 20)

                    val frameCount = max(1, (duration * videoFPS).roundToInt())

                    var lastBuffer = -1
                    for (step in 0 until steps) {
                        val f0 = mix(minT, maxT, step / (steps - 1.0))
                        val localTime0 = isLooping[f0, duration]
                        val frameIndex = (localTime0 * videoFPS).toInt()
                        if (frameIndex < 0 || frameIndex >= frameCount) continue
                        val buffer = frameIndex / framesPerContainer
                        if (buffer != lastBuffer) {
                            lastBuffer = buffer
                            getVideoFrame(max(1, zoomLevel), frameIndex, videoFPS)
                        }
                    }
                }
            }
        }
    }

    fun Video.claimImageSequence(minT: Double, maxT: Double) {
        val meta = imageSequenceMeta ?: return
        if (meta.isValid) {

            val duration = meta.duration
            val isLooping = isLooping.value

            if (maxT >= 0.0 && (stayVisibleAtEnd || isLooping != LoopingState.PLAY_ONCE || minT < duration)) {

                // draw the current texture
                val localTime0 = isLooping[minT, duration]
                val localTime1 = isLooping[maxT, duration]

                val index0 = meta.getIndex(localTime0)
                val index1 = meta.getIndex(localTime1)

                if (index1 >= index0) {
                    for (i in index0..index1) {
                        TextureCache[meta.getImage(i), videoFrameTimeout, true]
                    }
                } else {
                    for (i in index1 until meta.matches.size) {
                        TextureCache[meta.getImage(i), videoFrameTimeout, true]
                    }
                    for (i in 0 until index0) {
                        TextureCache[meta.getImage(i), videoFrameTimeout, true]
                    }
                }
            }
        }
    }
}