package me.anno.remsstudio.objects.video

import me.anno.animation.LoopingState
import me.anno.gpu.GFX.isFinalRendering
import me.anno.gpu.texture.Clamping
import me.anno.gpu.texture.Texture2D
import me.anno.gpu.texture.TextureCache
import me.anno.gpu.texture.TextureLib
import me.anno.gpu.texture.TextureLib.colorShowTexture
import me.anno.gpu.texture.TextureReader.Companion.imageTimeout
import me.anno.image.svg.SVGMeshCache
import me.anno.io.MediaMetadata
import me.anno.maths.Maths.fract
import me.anno.remsstudio.gpu.GFXx3Dv2
import me.anno.remsstudio.gpu.GFXx3Dv2.draw3DVideo
import me.anno.remsstudio.gpu.GFXxSVGv2
import me.anno.remsstudio.gpu.TexFiltering
import me.anno.remsstudio.objects.video.Video.Companion.forceAutoScale
import me.anno.remsstudio.objects.video.Video.Companion.forceFullScale
import me.anno.remsstudio.objects.video.Video.Companion.framesPerContainer
import me.anno.remsstudio.objects.video.Video.Companion.tiling16x9
import me.anno.remsstudio.objects.video.Video.Companion.videoFrameTimeout
import me.anno.remsstudio.objects.video.VideoSize.calculateSize
import me.anno.remsstudio.objects.video.VideoSize.getCacheableZoomLevel
import me.anno.utils.Clipping
import me.anno.video.VideoCache
import me.anno.video.VideoCache.getVideoFrameWithoutGenerator
import me.anno.video.ffmpeg.FrameReader.Companion.isFFMPEGOnlyExtension
import me.anno.video.formats.gpu.BlankFrameDetector
import me.anno.video.formats.gpu.GPUFrame
import org.apache.logging.log4j.LogManager
import org.joml.Matrix4fArrayList
import org.joml.Vector4f
import kotlin.math.*

object VideoDrawing {
    private val LOGGER = LogManager.getLogger(VideoDrawing::class)

    fun Video.drawImage(stack: Matrix4fArrayList, time: Double, color: Vector4f) {
        val file = file
        val ext = file.lcExtension
        when {
            ext == "svg" -> {
                val bufferData = SVGMeshCache[file, imageTimeout, true]
                if (bufferData == null) onMissingImageOrFrame(0)
                else {
                    GFXxSVGv2.draw3DSVG(
                        this, time,
                        stack, bufferData, TextureLib.whiteTexture,
                        color, TexFiltering.NEAREST, clampMode.value, tiling[time]
                    )
                }
            }
            ext.isFFMPEGOnlyExtension() -> {
                val tiling = tiling[time]
                // calculate required scale? no, without animation, we don't need to scale it down ;)
                val texture = VideoCache.getVideoFrame(file, 1, 0, 1, 1.0, imageTimeout, true)
                if (texture == null || !texture.isCreated) onMissingImageOrFrame(0)
                else {
                    lastW = texture.width
                    lastH = texture.height
                    draw3DVideo(
                        this, time, stack, texture, color,
                        filtering.value, clampMode.value, tiling, uvProjection.value,
                        cornerRadius[time]
                    )
                }
            }
            else -> {// some image
                val tiling = tiling[time]
                val texture = TextureCache[file, imageTimeout, true]
                if (texture == null || !texture.isCreated()) onMissingImageOrFrame(0)
                else {
                    (texture as? Texture2D)?.rotation?.apply(stack)
                    lastW = texture.width
                    lastH = texture.height
                    draw3DVideo(
                        this, time, stack, texture, color,
                        filtering.value, clampMode.value, tiling, uvProjection.value,
                        cornerRadius[time]
                    )
                }
            }
        }
    }

    /**
     * todo bug: when final rendering, then sometimes frames are just black...
     * */
    fun Video.drawImageSequence(stack: Matrix4fArrayList, time: Double, color: Vector4f) {

        val meta = imageSequenceMeta
        var wasDrawn = false

        if (meta != null && meta.isValid) {

            val isLooping = isLooping.value
            val duration = meta.duration
            LOGGER.debug("drawing image sequence, setting duration to $duration")
            lastDuration = duration

            if (time >= 0.0 && (isLooping != LoopingState.PLAY_ONCE || time <= duration)) {

                // draw the current texture
                val localTime = isLooping[time, duration]

                val frame = TextureCache[meta.getImage(localTime), 5L, true]
                if (frame == null || !frame.isCreated()) onMissingImageOrFrame((localTime * 1000).toInt())
                else {
                    lastW = frame.width
                    lastH = frame.height
                    draw3DVideo(
                        this, time,
                        stack, frame, color, filtering.value, clampMode.value,
                        tiling[time], uvProjection.value, cornerRadius[time]
                    )
                    wasDrawn = true
                }

            } else wasDrawn = true

        }

        if (!wasDrawn && !isFinalRendering) {
            GFXx3Dv2.draw3D(
                stack, colorShowTexture, 16, 9,
                Vector4f(0.5f, 0.5f, 0.5f, 1f).mul(color),
                TexFiltering.NEAREST, Clamping.REPEAT, tiling16x9, uvProjection.value
            )
        }

    }

    fun Video.drawVideo(stack: Matrix4fArrayList, time: Double, color: Vector4f) {
        // drawing speakers is covering the video too much
        val meta = meta
        if (meta != null && meta.hasVideo) {
            drawVideo(meta, stack, time, color) { zoom, index, fps ->
                getFrame(zoom, meta, index, fps)
            }
        }
    }

    fun Video.drawVideo(
        meta: MediaMetadata, stack: Matrix4fArrayList, time: Double, color: Vector4f,
        getFrame: (zoomLevel: Int, frameIndex0: Int, videoFPS: Double) -> GPUFrame?
    ) {

        val duration = meta.duration
        lastDuration = duration

        val forceAuto = isFinalRendering && forceAutoScale
        val forceFull = isFinalRendering && forceFullScale
        val zoomLevel = when {
            forceFull -> 1
            (videoScale.value < 1 || forceAuto) && uvProjection.value.doScale -> {
                val rawZoomLevel = calculateSize(stack, meta.videoWidth, meta.videoHeight) ?: return
                getCacheableZoomLevel(rawZoomLevel)
            }
            (videoScale.value < 1 || forceAuto) -> 1
            else -> videoScale.value
        }

        this.zoomLevel = zoomLevel

        var wasDrawn = false

        val isLooping = isLooping.value
        val sourceFPS = meta.videoFPS

        if (sourceFPS > 0.0) {
            val scale = GFXx3Dv2.getScale(meta.videoWidth, meta.videoHeight)
            val isVisible = Clipping.isPlaneVisible(stack, meta.videoWidth * scale, meta.videoHeight * scale)
            if (time >= 0.0 && (isLooping != LoopingState.PLAY_ONCE || time <= duration) && isVisible) {

                // use the full fps when rendering to correctly render at max fps with time dilation
                // issues arise, when multiple frames should be interpolated together into one
                // at this time, we chose the center frame only.
                val videoFPS = if (isFinalRendering) sourceFPS else min(sourceFPS, editorVideoFPS.value.toDouble())

                val frameCount = max(1, min(meta.videoFrameCount, (duration * videoFPS).roundToInt()))
                // I had a file, where the fps was incorrect
                // ("chose $frameCount from $duration * $videoFPS (max: ${meta.videoFPS}), max is ${meta.videoFrameCount}")

                // draw the current texture
                val localTime = isLooping[time, duration]
                val frameIndexD = localTime * videoFPS

                val frameIndex0 = floor(frameIndexD).toInt() % frameCount
                val frame0 = getFrame(zoomLevel, frameIndex0, videoFPS)

                val filtering = filtering.value
                val clamp = clampMode.value

                // todo why is it still flickering???
                val useInterpolation = false
                if (!useInterpolation) {
                    if (frame0 != null) {
                        lastW = meta.videoWidth
                        lastH = meta.videoHeight
                        draw3DVideo(
                            this, time, stack, frame0, color, filtering, clamp,
                            tiling[time], uvProjection.value, cornerRadius[time]
                        )
                        wasDrawn = true
                        lastFrame = frame0
                    }
                } else {
                    val interpolation = fract(frameIndexD).toFloat()
                    val frameIndex1 = ceil(frameIndexD).toInt() % frameCount
                    val frame1 = getFrame(zoomLevel, frameIndex1, videoFPS)
                    if (frame0 != null && frame1 != null) {
                        lastW = meta.videoWidth
                        lastH = meta.videoHeight
                        draw3DVideo(
                            this, time, stack, frame0, frame1, interpolation, color, filtering, clamp,
                            tiling[time], uvProjection.value, cornerRadius[time]
                        )
                        wasDrawn = true
                    }
                }

                // stack.scale(0.1f)
                // draw3D(stack, FontManager.getString("Verdana",15f, "$frameIndex/$fps/$duration/$frameCount")!!, Vector4f(1f,1f,1f,1f), 0f)
                // stack.scale(10f)

            } else wasDrawn = true
        }

        if (!wasDrawn) {
            GFXx3Dv2.draw3D(
                stack, colorShowTexture, 16, 9,
                Vector4f(0.5f, 0.5f, 0.5f, 1f).mul(color),
                TexFiltering.NEAREST, Clamping.REPEAT, tiling16x9, uvProjection.value
            )
        }
    }

    fun Video.getFrame(zoomLevel: Int, meta: MediaMetadata, frameIndex: Int, videoFPS: Double): GPUFrame? {

        val scale = max(1, zoomLevel)
        val bufferSize = framesPerContainer
        val timeout = videoFrameTimeout
        val file = file

        if (frameIndex < 0 || frameIndex >= max(1, meta.videoFrameCount)) {
            // a programming error probably
            throw IllegalArgumentException("Frame index must be within bounds!")
        }

        var frame0 = if (blankFrameThreshold > 0f) {
            BlankFrameDetector.getFrame(file, scale, frameIndex, bufferSize, videoFPS, timeout, meta, true)
        } else {
            getVideoFrameCustom(scale, frameIndex, videoFPS)
        }

        if (frame0 == null || !frame0.isCreated || frame0.isDestroyed) {
            onMissingImageOrFrame(frameIndex)
            frame0 = getVideoFrameWithoutGenerator(meta, frameIndex, bufferSize, videoFPS)
            if (frame0 == null || !frame0.isCreated || frame0.isDestroyed) frame0 = lastFrame
        }

        if (frame0 == null || !frame0.isCreated || frame0.isDestroyed) return null
        return frame0

    }


}