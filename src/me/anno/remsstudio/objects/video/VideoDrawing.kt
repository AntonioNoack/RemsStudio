package me.anno.remsstudio.objects.video

import me.anno.Time
import me.anno.animation.LoopingState
import me.anno.config.DefaultConfig
import me.anno.gpu.FinalRendering.isFinalRendering
import me.anno.gpu.texture.Clamping
import me.anno.gpu.texture.Texture2D
import me.anno.gpu.texture.TextureCache
import me.anno.gpu.texture.TextureLib
import me.anno.gpu.texture.TextureLib.colorShowTexture
import me.anno.image.svg.SVGMeshCache
import me.anno.io.MediaMetadata
import me.anno.maths.Maths.clamp
import me.anno.maths.Maths.posMod
import me.anno.remsstudio.gpu.DrawSVGv2
import me.anno.remsstudio.gpu.GFXx3Dv2
import me.anno.remsstudio.gpu.GFXx3Dv2.draw3DVideo
import me.anno.remsstudio.gpu.TexFiltering
import me.anno.remsstudio.objects.video.Video.Companion.forceAutoScale
import me.anno.remsstudio.objects.video.Video.Companion.forceFullScale
import me.anno.remsstudio.objects.video.Video.Companion.framesPerContainer
import me.anno.remsstudio.objects.video.Video.Companion.tiling16x9
import me.anno.remsstudio.objects.video.VideoSize.calculateZoomLevel
import me.anno.remsstudio.objects.video.VideoSize.getCacheableZoomLevel
import me.anno.ui.editor.files.FileExplorerEntry.Companion.drawLoadingCircle
import me.anno.utils.Clipping
import me.anno.utils.pooling.JomlPools
import me.anno.video.VideoCache
import me.anno.video.VideoCache.getVideoFrameWithoutGenerator
import me.anno.video.formats.gpu.BlankFrameDetector
import me.anno.video.formats.gpu.GPUFrame
import org.apache.logging.log4j.LogManager
import org.joml.Matrix4fArrayList
import org.joml.Vector4f
import kotlin.math.*

object VideoDrawing {
    private val LOGGER = LogManager.getLogger(VideoDrawing::class)

    fun String.isFFMPEGOnlyExtension() = equals("webp", true)// || equals("jp2", true)

    @JvmStatic
    val imageTimeout: Long
        get() = DefaultConfig["ui.video.frameTimeout", 250L]

    fun Video.drawImage(stack: Matrix4fArrayList, time: Double, color: Vector4f) {
        val file = file
        val ext = file.lcExtension
        val tiling = tiling[time, JomlPools.vec4f.create()]
        val cornerRadius = cornerRadius[time, JomlPools.vec4f.create()]
        val filtering = filtering.value
        val clamping = clampMode.value
        val projection = uvProjection.value
        when {
            ext == "svg" -> {
                val wrapper = SVGMeshCache[file, imageTimeout]
                if (!wrapper.hasValue && !wrapper.hasBeenDestroyed) onMissingImageOrFrame(0)
                val image = wrapper.value
                if (image != null) {
                    DrawSVGv2.draw3DSVG(
                        this, time,
                        stack, image, TextureLib.whiteTexture,
                        color, filtering, clamping, tiling
                    )
                }
            }
            ext.isFFMPEGOnlyExtension() -> {
                // calculate required scale? no, without animation, we don't need to scale it down ;)
                val wrapper = VideoCache.getVideoFrame(file, 1, 0, 1, 1.0, imageTimeout)
                if (!wrapper.hasValue && !wrapper.hasBeenDestroyed) {
                    onMissingImageOrFrame(0)
                }
                val texture = wrapper.value
                if (texture != null && texture.isCreated) {
                    lastW = texture.width
                    lastH = texture.height
                    draw3DVideo(
                        this, time, stack, texture, color,
                        filtering, clamping, tiling, projection, cornerRadius
                    )
                }
            }
            else -> {// some image
                val texture = TextureCache[file, imageTimeout].value
                if (texture == null || !texture.isCreated()) onMissingImageOrFrame(0)
                else {
                    (texture as? Texture2D)?.rotation?.apply(stack)
                    lastW = texture.width
                    lastH = texture.height
                    flipTilingY(tiling)
                    draw3DVideo(
                        this, time, stack, texture, color,
                        filtering, clamping, tiling, projection, cornerRadius
                    )
                }
            }
        }
        JomlPools.vec4f.sub(2)
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


                val wrapper = TextureCache[meta.getImage(localTime), 5L]
                if (!wrapper.hasValue && !wrapper.hasBeenDestroyed) onMissingImageOrFrame((localTime * 1000).toInt())
                val frame = wrapper.value
                if (frame != null && frame.isCreated()) {
                    lastW = frame.width
                    lastH = frame.height
                    val tiling = tiling[time, JomlPools.vec4f.create()]
                    val cornerRadius = cornerRadius[time, JomlPools.vec4f.create()]
                    flipTilingY(tiling)
                    draw3DVideo(
                        this, time,
                        stack, frame, color, filtering.value, clampMode.value,
                        tiling, uvProjection.value, cornerRadius
                    )
                    JomlPools.vec4f.sub(2)
                    wasDrawn = true
                }

            } else wasDrawn = true

        }

        if (!wasDrawn && !isFinalRendering) {
            val color1 = JomlPools.vec4f.create()
            GFXx3Dv2.draw3D(
                stack, colorShowTexture, 16, 9,
                color1.set(0.5f, 0.5f, 0.5f, 1f).mul(color),
                TexFiltering.NEAREST, Clamping.REPEAT, tiling16x9, uvProjection.value
            )
            JomlPools.vec4f.sub(1)
        }

    }

    fun flipTilingY(tiling: Vector4f) {
        tiling.y = -tiling.y
    }

    fun Video.drawVideo(stack: Matrix4fArrayList, time: Double, color: Vector4f) {
        // drawing speakers is covering the video too much
        val meta = meta ?: return
        if (!meta.hasVideo) return

        val duration = meta.duration
        lastDuration = duration

        val forceAuto = isFinalRendering && forceAutoScale
        val forceFull = isFinalRendering && forceFullScale
        val videoScale = videoScale.value
        val zoomLevel = when {
            forceFull -> 1
            (videoScale < 1 || forceAuto) && uvProjection.value.doScale -> {
                val rawZoomLevel = calculateZoomLevel(stack, meta.videoWidth, meta.videoHeight) ?: return
                getCacheableZoomLevel(rawZoomLevel)
            }
            (videoScale < 1 || forceAuto) -> 1
            else -> videoScale
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

                val frameIndex = clamp(floor(frameIndexD).toInt(), 0, frameCount - 1)
                val frame0 = getFrame(zoomLevel, meta, frameIndex, videoFPS)

                val filtering = filtering.value
                val clamp = clampMode.value

                if (frame0 != null) {
                    lastW = meta.videoWidth
                    lastH = meta.videoHeight
                    val tiling = tiling[time, JomlPools.vec4f.create()]
                    val cornerRadius = cornerRadius[time, JomlPools.vec4f.create()]
                    draw3DVideo(
                        this, time, stack, frame0, color, filtering, clamp,
                        tiling, uvProjection.value, cornerRadius
                    )
                    JomlPools.vec4f.sub(2)
                    val mod = VideoCache.framesPerSlice
                    if (posMod(frame0.frameIndex, mod) != posMod(frameIndex, mod)) {
                        drawLoadingCircle(stack, (Time.nanoTime * 1e-9f) % 1f)
                    }
                    wasDrawn = true
                    lastFrame = frame0
                }
            } else wasDrawn = true
        }

        if (!wasDrawn) {
            val color1 = JomlPools.vec4f.create()
            GFXx3Dv2.draw3D(
                stack, colorShowTexture, 16, 9,
                color1.set(0.5f, 0.5f, 0.5f, 1f).mul(color),
                TexFiltering.NEAREST, Clamping.REPEAT, tiling16x9, uvProjection.value
            )
            JomlPools.vec4f.sub(1)
        }
    }

    private fun Video.getFrame(zoomLevel: Int, meta: MediaMetadata, frameIndex: Int, videoFPS: Double): GPUFrame? {

        val scale = max(1, zoomLevel)
        val bufferSize = framesPerContainer

        if (frameIndex < 0 || frameIndex >= max(1, meta.videoFrameCount)) {
            // a programming error probably
            throw IllegalArgumentException("Frame index must be within bounds!")
        }

        val useStreaming = streamManager.canUseVideoStreaming()
        fun getFrame(frameIndex: Int): GPUFrame? {
            if (frameIndex !in 0 until meta.videoFrameCount) return null
            if (useStreaming) {
                val frame = streamManager.getFrame(scale, frameIndex, videoFPS)
                if (frame == null) onMissingImageOrFrame(frameIndex)
                return frame
            } else {
                val wrapper = getVideoFrame(scale, frameIndex, videoFPS)
                if (!wrapper.hasValue && !wrapper.hasBeenDestroyed) onMissingImageOrFrame(frameIndex)
                val value = wrapper.value
                if (value != null && !value.isCreated && !value.isDestroyed) onMissingImageOrFrame(frameIndex)
                return if (value != null && value.isCreated) value else null
            }
        }

        var frame0 = if (usesBlankFrameDetection()) {
            BlankFrameDetector.getFrame(blankFrameThreshold) { delta ->
                getFrame(frameIndex + delta)
            }
        } else if (useStreaming || isFinalRendering) {
            val frame = getFrame(frameIndex)
            insertLastFrame(frameIndex)
            frame
        } else {
            findClosestFrame(frameIndex, scale, videoFPS)
        }

        if (frame0 == null || !frame0.isCreated || frame0.isDestroyed) {
            // onMissingImageOrFrame(frameIndex)
            frame0 = getVideoFrameWithoutGenerator(meta, frameIndex, bufferSize, videoFPS)
            if (frame0 == null || !frame0.isCreated || frame0.isDestroyed) frame0 = lastFrame
        }

        if (frame0 == null || !frame0.isCreated || frame0.isDestroyed) return null
        return frame0

    }

    private fun Video.findClosestFrame(frameIndex: Int, scale: Int, videoFPS: Double): GPUFrame? {
        var bestFrame = getVideoFrame(scale, frameIndex, videoFPS).value
        if (bestFrame != null) return bestFrame
        val lastFrames = lastXFrames
        for (i in lastFrames.indices) {
            if (bestFrame == null || abs(lastFrames[i] - frameIndex) < abs(bestFrame.frameIndex - frameIndex)) {
                bestFrame = getVideoFrameWithoutGenerator(scale, frameIndex, videoFPS)
            }
        }
        return bestFrame
    }

    private fun Video.insertLastFrame(frameIndex: Int) {
        val lastFrames = lastXFrames
        if (frameIndex in lastFrames) return // idk...
        for (i in 0 until lastFrames.lastIndex) {
            lastFrames[i] = lastFrames[i + 1]
        }
        lastFrames[lastFrames.lastIndex] = frameIndex
    }


}