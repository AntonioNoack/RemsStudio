package me.anno.remsstudio.objects

import me.anno.animation.LoopingState
import me.anno.audio.openal.AudioManager
import me.anno.audio.openal.AudioTasks.addAudioTask
import me.anno.config.DefaultConfig
import me.anno.ecs.annotations.Range
import me.anno.engine.inspector.Inspectable
import me.anno.gpu.GFX
import me.anno.gpu.GFX.isFinalRendering
import me.anno.gpu.drawing.UVProjection
import me.anno.gpu.texture.Clamping
import me.anno.gpu.texture.Texture2D
import me.anno.gpu.texture.TextureCache
import me.anno.gpu.texture.TextureLib
import me.anno.gpu.texture.TextureLib.colorShowTexture
import me.anno.gpu.texture.TextureReader.Companion.imageTimeout
import me.anno.image.svg.SVGMeshCache
import me.anno.io.MediaMetadata
import me.anno.io.MediaMetadata.Companion.getMeta
import me.anno.io.base.BaseWriter
import me.anno.io.files.FileReference
import me.anno.io.files.InvalidRef
import me.anno.io.files.WebRef
import me.anno.language.translation.Dict
import me.anno.language.translation.NameDesc
import me.anno.maths.Maths.clamp
import me.anno.maths.Maths.fract
import me.anno.maths.Maths.mix
import me.anno.maths.Maths.pow
import me.anno.remsstudio.RemsStudio.isPaused
import me.anno.remsstudio.RemsStudio.nullCamera
import me.anno.remsstudio.RemsStudio.targetHeight
import me.anno.remsstudio.RemsStudio.targetWidth
import me.anno.remsstudio.Scene
import me.anno.remsstudio.animation.AnimatedProperty
import me.anno.remsstudio.gpu.GFXx3Dv2
import me.anno.remsstudio.gpu.GFXx3Dv2.draw3DVideo
import me.anno.remsstudio.gpu.GFXxSVGv2
import me.anno.remsstudio.gpu.TexFiltering
import me.anno.remsstudio.gpu.TexFiltering.Companion.getFiltering
import me.anno.remsstudio.objects.lists.Element
import me.anno.remsstudio.objects.lists.SplittableElement
import me.anno.remsstudio.objects.models.SpeakerModel.drawSpeakers
import me.anno.remsstudio.objects.modes.VideoType
import me.anno.ui.Panel
import me.anno.ui.Style
import me.anno.ui.base.SpyPanel
import me.anno.ui.base.buttons.TextButton
import me.anno.ui.base.groups.PanelListY
import me.anno.ui.base.text.UpdatingTextPanel
import me.anno.ui.editor.PropertyInspector.Companion.invalidateUI
import me.anno.ui.editor.SettingCategory
import me.anno.ui.input.EnumInput
import me.anno.ui.input.FloatInput
import me.anno.ui.input.NumberType
import me.anno.utils.Clipping
import me.anno.utils.structures.Collections.filterIsInstance2
import me.anno.utils.structures.ValueWithDefault
import me.anno.utils.structures.ValueWithDefault.Companion.writeMaybe
import me.anno.utils.structures.ValueWithDefaultFunc
import me.anno.utils.structures.maps.BiMap
import me.anno.utils.types.Booleans.toInt
import me.anno.utils.types.Floats.f2
import me.anno.utils.types.Strings.formatTime2
import me.anno.utils.types.Strings.getImportTypeByExtension
import me.anno.video.ImageSequenceMeta
import me.anno.video.ImageSequenceMeta.Companion.imageSequenceIdentifier
import me.anno.video.MissingFrameException
import me.anno.video.VideoCache
import me.anno.video.VideoCache.getVideoFrameWithoutGenerator
import me.anno.video.ffmpeg.FrameReader.Companion.isFFMPEGOnlyExtension
import me.anno.video.formats.gpu.BlankFrameDetector
import me.anno.video.formats.gpu.GPUFrame
import org.apache.logging.log4j.LogManager
import org.joml.Matrix4f
import org.joml.Matrix4fArrayList
import org.joml.Vector3f
import org.joml.Vector4f
import kotlin.math.*

// game, where everything is explicitly rendered on 5-10 cubemaps first? lol
// sadly this doesn't make for any content yet, so it's this raw just a bad idea

// todo frame interpolation
// todo detect which frames actually changed, and interpolate between them
// (would smooth out slight stutter)

// todo AMD FSR as interpolation method

// todo auto-exposure correction by calculating the exposure, and adjusting the brightness

// todo feature tracking on videos as anchors, e.g. for easy blurry signs, or text above heads (marker on head/eyes)

// todo ask the user whether he wants to proceed, if there are warnings in the scene

// todo this class is too long, split its parts

/**
 * Images, Cubemaps, Videos, Audios, joint into one
 * */
@Suppress("MemberVisibilityCanBePrivate")
class Video(file: FileReference = InvalidRef, parent: Transform? = null) :
    Audio(file, parent), SplittableElement {

    constructor() : this(InvalidRef, null)

    companion object {

        val framesPerContainer = 128

        val editorFPS = intArrayOf(1, 2, 3, 5, 10, 24, 30, 60, 90, 120, 144, 240, 300, 360)

        private val LOGGER = LogManager.getLogger(Video::class)

        private val forceAutoScale get() = DefaultConfig["rendering.video.forceAutoScale", true]
        private val forceFullScale get() = DefaultConfig["rendering.video.forceFullScale", false]

        val videoScaleNames = BiMap<String, Int>(10)

        init {
            videoScaleNames["Auto"] = 0
            videoScaleNames["Original"] = 1
            for (i in listOf(2, 3, 4, 6, 8, 12, 16, 32, 64)) {
                videoScaleNames["1/$i"] = i
            }
        }

        val videoFrameTimeout get() = if (isFinalRendering) 2000L else 10000L
        val tiling16x9 = Vector4f(8f, 4.5f, 0f, 0f)

    }

    // uv
    val tiling = AnimatedProperty.tiling()
    val uvProjection = ValueWithDefault(UVProjection.Planar)
    val clampMode = ValueWithDefault(Clamping.MIRRORED_REPEAT)

    // filtering
    val filtering = ValueWithDefaultFunc { DefaultConfig.getFiltering("default.video.filtering", TexFiltering.CUBIC) }

    // resolution
    val videoScale = ValueWithDefaultFunc { DefaultConfig["default.video.scale", 1] }

    var lastFile: FileReference? = null
    var lastMeta: MediaMetadata? = null
    var lastDuration = Double.POSITIVE_INFINITY

    var imageSequenceMeta: ImageSequenceMeta? = null
    val imSeqExampleMeta: MediaMetadata?
        get() = imageSequenceMeta?.matches?.firstOrNull()?.first?.run {
            getMeta(
                this,
                true
            )
        }

    var type = VideoType.UNKNOWN

    @Range(0.0, 3.0)
    var blankFrameThreshold = 0f

    override fun getDocumentationURL(): String? = when (type) {
        VideoType.IMAGE -> "https://remsstudio.phychi.com/?s=learn/images"
        VideoType.VIDEO, VideoType.IMAGE_SEQUENCE -> "https://remsstudio.phychi.com/?s=learn/videos"
        else -> null
    }

    override fun clearCache() {
        lastTexture = null
        needsImageUpdate = true
        lastFile = null
        LOGGER.info("Clear cache")
    }

    override fun startPlayback(globalTime: Double, speed: Double, camera: Camera) {
        when (type) {
            VideoType.VIDEO, VideoType.AUDIO -> {
                super.startPlayback(globalTime, speed, camera)
            }
            else -> {
                // image and image sequence cannot contain audio,
                // so we can avoid getting the metadata for the files with ffmpeg
                stopPlayback()
            }
        }
    }

    private var zoomLevel = 0

    var editorVideoFPS = ValueWithDefault(60)

    val cgOffsetAdd = AnimatedProperty.color3(Vector3f())
    val cgOffsetSub = AnimatedProperty.color3(Vector3f())
    val cgSlope = AnimatedProperty.color(Vector4f(1f, 1f, 1f, 1f))
    val cgPower = AnimatedProperty.color(Vector4f(1f, 1f, 1f, 1f))
    val cgSaturation = AnimatedProperty.float(1f)

    // todo support this for polygons, too?
    val cornerRadius = AnimatedProperty.vec4(Vector4f(0f))

    var lastW = 16
    var lastH = 9

    override fun getEndTime(): Double = when (isLooping.value) {
        LoopingState.PLAY_ONCE -> {
            if (stayVisibleAtEnd) Double.POSITIVE_INFINITY
            else when (type) {
                VideoType.IMAGE_SEQUENCE -> imageSequenceMeta?.duration
                VideoType.IMAGE -> Double.POSITIVE_INFINITY
                VideoType.UNKNOWN -> Double.POSITIVE_INFINITY
                else -> meta?.duration
            } ?: Double.POSITIVE_INFINITY
        }
        else -> Double.POSITIVE_INFINITY
    }

    override fun isVisible(localTime: Double): Boolean {
        val looping = isLooping.value
        return localTime >= 0.0 && (stayVisibleAtEnd || looping != LoopingState.PLAY_ONCE || localTime < lastDuration)
    }

    override fun transformLocally(pos: Vector3f, time: Double): Vector3f {
        val doScale = uvProjection.value.doScale && lastW != lastH
        return if (doScale) {
            val avgSize =
                if (lastW * targetHeight > lastH * targetWidth) lastW.toFloat() * targetHeight / targetWidth else lastH.toFloat()
            val sx = lastW / avgSize
            val sy = lastH / avgSize
            Vector3f(pos.x / sx, -pos.y / sy, pos.z)
        } else {
            Vector3f(pos.x, -pos.y, pos.z)
        }
    }

    fun calculateSize(matrix: Matrix4f, w: Int, h: Int): Int? {

        // gl_Position = transform * vec4(betterUV, 0.0, 1.0);

        // clamp points to edges of screens, if outside, clamp on the z edges
        // -> just generally clamp the polygon...
        // the most extreme cases should be on a quad always, because it's linear
        // -> clamp all axis separately

        val avgSize =
            if (w * targetHeight > h * targetWidth) w.toFloat() * targetHeight / targetWidth else h.toFloat()
        val sx = w / avgSize
        val sy = h / avgSize

        fun getPoint(x: Float, y: Float): Vector4f {
            return matrix.transformProject(Vector4f(x * sx, y * sy, 0f, 1f))
        }

        val v00 = getPoint(-1f, -1f)
        val v01 = getPoint(-1f, +1f)
        val v10 = getPoint(+1f, -1f)
        val v11 = getPoint(+1f, +1f)

        // check these points by drawing them on the screen
        // they were correct as of 12th July 2020, 9:18 am
        /*
        for(pt in listOf(v00, v01, v10, v11)){
            val x = GFX.windowX + (+pt.x * 0.5f + 0.5f) * GFX.windowWidth
            val y = GFX.windowY + (-pt.y * 0.5f + 0.5f) * GFX.windowHeight
            drawRect(x.toInt()-2, y.toInt()-2, 5, 5, 0xff0000 or black)
        }
        */

        val zRange = Clipping.getZ(v00, v01, v10, v11) ?: return null

        // calculate the depth based on the z value
        fun unmapZ(z: Float): Float {
            val n = Scene.nearZ
            val f = Scene.farZ
            val top = 2 * f * n
            val bottom = (z * (f - n) - (f + n))
            return -top / bottom // the usual z is negative -> invert it :)
        }

        val closestDistance = min(unmapZ(zRange.first), unmapZ(zRange.second))

        // calculate the zoom level based on the distance
        val pixelZoom = GFX.viewportHeight * 1f / (closestDistance * h) // e.g., 0.1 for a window far away
        val availableRedundancy = 1f / pixelZoom // 0.1 zoom means that we only need every 10th pixel

        return max(1, availableRedundancy.toInt())

    }

    private fun getCacheableZoomLevel(level: Int): Int {
        return when {
            level < 1 -> 1
            level <= 6 || level == 8 || level == 12 || level == 16 -> level
            else -> {
                val stepsIn2 = 3
                val log = log2(level.toFloat())
                val roundedLog = round(stepsIn2 * log) / stepsIn2
                pow(2f, roundedLog).toInt()
            }
        }
    }

    /**
     * todo when final rendering, then sometimes frames are just black...
     * */
    private fun drawImageSequence(meta: ImageSequenceMeta, stack: Matrix4fArrayList, time: Double, color: Vector4f) {

        var wasDrawn = false

        if (meta.isValid) {

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
                        stack, frame, color, this@Video.filtering.value, this@Video.clampMode.value,
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

    private fun onMissingImageOrFrame(frame: Int) {
        if (isFinalRendering) throw MissingFrameException("$file, $frame/${meta?.videoFrameCount}")
        else needsImageUpdate = true
        // LOGGER.info("missing frame")
    }

    fun getFrameAtLocalTimeForPreview(time: Double, width: Int, meta: MediaMetadata): GPUFrame? {

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
                val frameIndex = (localTime * videoFPS).toInt() % frameCount

                val frame = getVideoFrameCustom(scale, frameIndex, videoFPS)

                if (frame != null && frame.isCreated) {
                    lastW = frame.width
                    lastH = frame.height
                    return frame
                }
            }
        }

        return null

    }

    private var lastFrame: GPUFrame? = null

    private fun drawVideo(
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

                val filtering = this@Video.filtering.value
                val clamp = this@Video.clampMode.value

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

    fun getFrame(zoomLevel: Int, meta: MediaMetadata, frameIndex: Int, videoFPS: Double): GPUFrame? {

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

    private fun getImage(): Any? {
        val ext = file.extension
        return when {
            ext.equals("svg", true) ->
                SVGMeshCache[file, imageTimeout, true]
            ext.equals("webp", true) || ext.equals("dds", true) ->
                // calculate required scale? no, without animation, we don't need to scale it down ;)
                VideoCache.getVideoFrame(file, 1, 0, 1, 1.0, imageTimeout, true)
            else -> // some image
                TextureCache[file, imageTimeout, true]
        }
    }

    private fun drawImage(stack: Matrix4fArrayList, time: Double, color: Vector4f) {
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

    private fun getVideoFrameCustom(scale: Int, index: Int, fps: Double): GPUFrame? {
        return VideoCache.getVideoFrame(file, scale, index, framesPerContainer, fps, videoFrameTimeout, true)
    }

    var needsImageUpdate = false
    var lastTexture: Any? = null
    override fun claimLocalResources(lTime0: Double, lTime1: Double) {

        val minT = min(lTime0, lTime1)
        val maxT = max(lTime0, lTime1)

        when (type) {
            VideoType.VIDEO -> {

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
                                    getVideoFrameCustom(max(1, zoomLevel), frameIndex, videoFPS)
                                }
                            }
                        }
                    }
                }
            }
            VideoType.IMAGE_SEQUENCE -> {

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
            // nothing to do for image and audio
            VideoType.IMAGE -> {
                val texture = getImage()
                if (lastTexture != texture) {
                    needsImageUpdate = true
                    lastTexture = texture
                }
            }
            VideoType.AUDIO, VideoType.UNKNOWN -> {}
        }

        if (needsImageUpdate) {
            invalidateUI(true)
            needsImageUpdate = false
        }

    }

    var lastAddedEndKeyframesFile: FileReference? = null

    fun update() {
        val file = file
        update(file, file.hasValidName())
    }

    fun update(file: FileReference, hasValidName: Boolean) {
        if (!hasValidName) return
        val meta = meta
        if (file !== lastFile || meta !== lastMeta) {
            lastFile = file
            lastMeta = meta
            type = if (file !is WebRef && file.name.contains(imageSequenceIdentifier)) {
                // async in the future?
                val imageSequenceMeta = ImageSequenceMeta(file)
                this.imageSequenceMeta = imageSequenceMeta
                VideoType.IMAGE_SEQUENCE
            } else if (meta == null) {
                when (getImportTypeByExtension(file.lcExtension)) {
                    "Video" -> VideoType.VIDEO
                    "Audio" -> VideoType.AUDIO
                    else -> VideoType.IMAGE
                }
            } else {
                if (meta.hasVideo) {
                    if (meta.videoFrameCount > 1) VideoType.VIDEO
                    else VideoType.IMAGE
                } else if (meta.hasAudio) VideoType.AUDIO
                else VideoType.UNKNOWN
            }
            lastWarning = null
            when (type) {
                VideoType.VIDEO, VideoType.AUDIO, VideoType.UNKNOWN -> {
                    if (meta != null && meta.hasVideo) {
                        if (file != lastAddedEndKeyframesFile) {
                            lastAddedEndKeyframesFile = file
                        }
                        lastDuration = meta.duration
                    }
                    type = if (meta == null) {
                        lastWarning = if (file.exists) {
                            "Video file is invalid"
                        } else {
                            "File does not exist"
                        }
                        VideoType.UNKNOWN
                    } else {
                        when (getImportTypeByExtension(file.lcExtension)) {
                            "Video" -> VideoType.VIDEO
                            "Audio" -> VideoType.AUDIO
                            else -> VideoType.IMAGE
                        }
                    }
                }
                VideoType.IMAGE_SEQUENCE -> {
                    val meta2 = imageSequenceMeta!!
                    if (!meta2.isValid) lastWarning = "No image sequence matches found"
                }
                VideoType.IMAGE -> {
                    // todo check if the image is valid...
                }
            }
        }
    }

    override fun onDraw(stack: Matrix4fArrayList, time: Double, color: Vector4f) {

        needsImageUpdate = false

        val file = file
        if (file.hasValidName()) {

            update(file, true)

            when (type) {
                VideoType.VIDEO -> {
                    val meta = meta
                    if (meta != null && meta.hasVideo) {
                        drawVideo(meta, stack, time, color) { zoom, index, fps ->
                            getFrame(zoom, meta, index, fps)
                        }
                    }
                    // very intrusive :/
                    /*if(meta?.hasAudio == true){
                        drawSpeakers(stack, time, color)
                    }*/
                }
                VideoType.IMAGE_SEQUENCE -> {
                    val meta = imageSequenceMeta!!
                    drawImageSequence(meta, stack, time, color)
                }
                VideoType.IMAGE -> drawImage(stack, time, color)
                VideoType.AUDIO -> {
                    val meta = meta
                    if (meta != null) lastDuration = meta.duration
                    drawSpeakers(stack, Vector4f(color), is3D, amplitude[time])
                }
                VideoType.UNKNOWN -> {}
            }

        } else {
            drawSpeakers(stack, Vector4f(color), is3D, amplitude[time])
            lastWarning = "Invalid filename"
        }

    }

    override fun createInspector(
        inspected: List<Inspectable>, list: PanelListY, style: Style,
        getGroup: (NameDesc) -> SettingCategory
    ) {
        super.createInspector(inspected, list, style, getGroup)
        val t = inspected.filterIsInstance2(Transform::class)
        val c = inspected.filterIsInstance2(Video::class)

        // to hide elements, which are not usable / have no effect
        val videoPanels = ArrayList<Panel>()
        val imagePanels = ArrayList<Panel>()
        val audioPanels = ArrayList<Panel>()

        fun vid(panel: Panel): Panel {
            videoPanels += panel
            return panel
        }

        fun img(panel: Panel): Panel {
            imagePanels += panel
            return panel
        }

        fun aud(panel: Panel): Panel {
            audioPanels += panel
            return panel
        }

        val infoGroup = getGroup(NameDesc("Info", "File information", "obj.info"))
        infoGroup += UpdatingTextPanel(250, style) { "Type: ${type.name}" }
        infoGroup += UpdatingTextPanel(250, style) {
            if (type == VideoType.IMAGE) null
            else "Duration: ${(lastMeta?.duration ?: imageSequenceMeta?.duration).formatTime2(2)}"
        }
        infoGroup += vid(UpdatingTextPanel(250, style) { "Video Duration: ${lastMeta?.videoDuration.formatTime2(2)}" })
        infoGroup += img(UpdatingTextPanel(250, style) {
            val meta = lastMeta ?: imSeqExampleMeta
            val frame = getImage() as? Texture2D
            val w = max(meta?.videoWidth ?: 0, frame?.width ?: 0)
            val h = max(meta?.videoHeight ?: 0, frame?.height ?: 0)
            "Resolution: $w x $h"
        })
        infoGroup += vid(UpdatingTextPanel(250, style) { "Frame Rate: ${lastMeta?.videoFPS?.f2()} frames/s" })
        infoGroup += img(UpdatingTextPanel(250, style) {
            "Frame Count: ${lastMeta?.videoFrameCount ?: imageSequenceMeta?.frameCount}"
        })
        // infoGroup += vid(UpdatingTextPanel(250, style) { "Video Start Time: ${meta?.videoStartTime}s" })
        infoGroup += aud(UpdatingTextPanel(250, style) { "Audio Duration: ${lastMeta?.audioDuration.formatTime2(2)}" })
        infoGroup += aud(UpdatingTextPanel(250, style) { "Sample Rate: ${lastMeta?.audioSampleRate} samples/s" })
        infoGroup += aud(UpdatingTextPanel(250, style) { "Sample Count: ${lastMeta?.audioSampleCount} samples" })

        list += vi(inspected, "File Location", "Source file of this video", null, file, style) { newFile, _ ->
            for (x in c) x.file = newFile
        }

        val colorGroup = getGroup(NameDesc("Color", "", "obj.color"))
        colorGroup += vis(c, "Corner Radius", "Makes the corners round", c.map { it.cornerRadius }, style)

        val uvMap = getGroup(NameDesc("Texture", "", "obj.uvs"))
        uvMap += img(
            vis(
                c, "Tiling", "(tile count x, tile count y, offset x, offset y)", c.map { it.tiling },
                style
            )
        )
        uvMap += img(
            vi(
                inspected, "UV-Projection", "Can be used for 360°-Videos",
                null, uvProjection.value, style
            ) { it, _ -> for (x in c) x.uvProjection.value = it })
        uvMap += img(
            vi(
                inspected, "Filtering", "Pixelated look?", "texture.filtering",
                null, filtering.value, style
            ) { it, _ -> for (x in c) x.filtering.value = it })
        uvMap += img(
            vi(
                inspected, "Clamping", "For tiled images", "texture.clamping",
                null, clampMode.value, style
            ) { it, _ -> for (x in c) x.clampMode.value = it })

        fun invalidateTimeline() {
            AudioManager.requestUpdate()
            // todo this needs multiple frames of invalidation, probably...
            /*addEvent { // needs a little timeout
                for (window in GFX.windows) {
                    for (window1 in window.windowStack) {
                        window1.panel.forAllVisiblePanels {
                            if (it is LayerView) it.invalidateLayout()
                        }
                    }
                }
            }*/
        }

        val time = getGroup(NameDesc("Time", "", "obj.time"))
        time += vi(
            inspected, "Looping Type", "Whether to repeat the song/video", "video.loopingType",
            "video.loopingType",
            null, isLooping.value, style
        ) { it, _ ->
            for (x in c) x.isLooping.value = it
            invalidateTimeline()
        }
        time += vi(
            inspected, "Stay Visible At End",
            "Normally a video fades out, or loops; this lets it stay on the last frame",
            "video.stayVisibleAtEnd", "video.stayVisibleAtEnd",
            null, stayVisibleAtEnd, style
        ) { it, _ ->
            for (x in c) x.stayVisibleAtEnd = it
            invalidateTimeline()
        }

        val editor = getGroup(NameDesc("Editor", "", "obj.editor"))
        fun quality() = getGroup(NameDesc("Quality", "", "obj.quality"))

        // quality; if controlled automatically, then editor; else quality
        val videoScales = videoScaleNames.entries.sortedBy { it.value }
        (if (forceFullScale || forceAutoScale) editor else quality()) += vid(EnumInput(
            "Preview Scale",
            "Full video resolution isn't always required. Define it yourself, or set it to automatic.",
            videoScaleNames.reverse[videoScale.value] ?: "Auto",
            videoScales.map { NameDesc(it.key) },
            style
        )
            .setChangeListener { _, index, _ -> for (x in c) x.videoScale.value = videoScales[index].value }
            .setIsSelectedListener { show(t, null) })

        editor += vid(EnumInput(
            "Preview FPS",
            "Smoother preview, heavier calculation",
            editorVideoFPS.value.toString(),
            editorFPS.filterIndexed { index, it -> index == 0 || it * 0.98 <= (meta?.videoFPS ?: 1e85) }
                .map { NameDesc(it.toString()) },
            style
        )
            .setChangeListener { _, index, _ -> for (x in c) x.editorVideoFPS.value = editorFPS[index] }
            .setIsSelectedListener { show(t, null) })

        quality() += vid(
            FloatInput(
                "Blank Frames Removal",
                "When a set percentage of pixels change within 1 frame, that frame is removed from the source\n" +
                        "The higher, the more frames are accepted; 0 = disabled\n" +
                        "Cannot handle more than two adjacent blank frames",
                blankFrameThreshold, NumberType.FLOAT_03, style
            )
                .setChangeListener { for (x in c) x.blankFrameThreshold = it.toFloat() }
                .setIsSelectedListener { show(t, null) })


        ColorGrading.createInspector(
            c, c.map { it.cgPower }, c.map { it.cgSaturation }, c.map { it.cgSlope }, c.map { it.cgOffsetAdd },
            c.map { it.cgOffsetSub }, { img(it) },
            getGroup, style
        )

        val audio = getGroup(NameDesc("Audio", "", "obj.audio"))
        audio += aud(vis(c, "Amplitude", "How loud it is", "audio.amplitude", c.map { it.amplitude }, style))
        audio += aud(vi(inspected, "Is 3D Sound", "Sound becomes directional", "audio.3d", null, is3D, style) { it, _ ->
            for (x in c) x.is3D = it
            AudioManager.requestUpdate()
        })

        val playbackTitles = "Test Playback" to "Stop Playback"
        fun getPlaybackTitle(invert: Boolean) =
            if ((component == null) != invert) playbackTitles.first else playbackTitles.second

        val playbackButton = TextButton(getPlaybackTitle(false), false, style)
        audio += aud(playbackButton
            .addLeftClickListener {
                if (isPaused) {
                    playbackButton.text = getPlaybackTitle(true)
                    if (component == null) {
                        addAudioTask("start", 5) {
                            val audio2 = Video(file, null)
                            audio2.update() // load type
                            audio2.startPlayback(0.0, 1.0, nullCamera!!)
                            component = audio2.component
                        }
                    } else {
                        addAudioTask("stop", 1) {
                            stopPlayback()
                        }
                    }
                } else LOGGER.warn("Separated playback is only available with paused editor")
            }
            .setTooltip("Listen to the audio separated from the rest"))

        var lastState = -1
        list += SpyPanel(style) {
            val meta = lastMeta
            val isValid = file.hasValidName() && meta != null
            val hasAudio = isValid && meta?.hasAudio == true
            val hasImage = isValid && type != VideoType.AUDIO
            val hasVideo = isValid && when (type) {
                VideoType.IMAGE_SEQUENCE, VideoType.VIDEO -> true
                else -> false
            } && meta?.hasVideo == true
            val hasImSeq = isValid && type == VideoType.IMAGE_SEQUENCE
            val state = hasAudio.toInt(1) + hasImage.toInt(2) + hasVideo.toInt(4) + hasImSeq.toInt(8)
            if (state != lastState) {
                lastState = state
                for (p in audioPanels) p.isVisible = hasAudio
                for (p in videoPanels) p.isVisible = hasVideo
                for (p in imagePanels) p.isVisible = hasImage
                list.invalidateLayout()
            }
        }

    }

    override fun save(writer: BaseWriter) {
        super.save(writer)
        writer.writeObject(this, "tiling", tiling)
        writer.writeMaybe(this, "filtering", filtering)
        writer.writeMaybe(this, "clamping", clampMode)
        writer.writeMaybe(this, "uvProjection", uvProjection)
        writer.writeMaybe(this, "videoScale", videoScale)
        writer.writeObject(this, "cgSaturation", cgSaturation)
        writer.writeObject(this, "cgOffsetAdd", cgOffsetAdd)
        writer.writeObject(this, "cgOffsetSub", cgOffsetSub)
        writer.writeObject(this, "cgSlope", cgSlope)
        writer.writeObject(this, "cgPower", cgPower)
        writer.writeMaybe(this, "editorVideoFPS", editorVideoFPS)
        writer.writeBoolean("stayVisibleAtEnd", stayVisibleAtEnd)
        writer.writeObject(this, "cornerRadius", cornerRadius)
    }

    override fun setProperty(name: String, value: Any?) {
        when (name) {
            "stayVisibleAtEnd" -> stayVisibleAtEnd = value == true
            "tiling" -> tiling.copyFrom(value)
            "cgSaturation" -> cgSaturation.copyFrom(value)
            "cgOffset", "cgOffsetAdd" -> cgOffsetAdd.copyFrom(value)
            "cgOffsetSub" -> cgOffsetSub.copyFrom(value)
            "cgSlope" -> cgSlope.copyFrom(value)
            "cgPower" -> cgPower.copyFrom(value)
            "videoScale" -> videoScale.value = value as? Int ?: return
            "filtering" -> filtering.value = filtering.value.find(value as? Int ?: return)
            "clamping" -> clampMode.value = Clamping.entries.firstOrNull { it.id == value } ?: return
            "uvProjection" -> uvProjection.value = UVProjection.entries.firstOrNull { it.id == value } ?: return
            "editorVideoFPS" -> editorVideoFPS.value = clamp(value as? Int ?: return, 1, 1000)
            "cornerRadius" -> cornerRadius.copyFrom(value)
            else -> super.setProperty(name, value)
        }
    }

    override val className get() = "Video"

    override val defaultDisplayName: String
        get() {
            // file can be null
            return (if (file != InvalidRef && file.hasValidName()) file.name
            else Dict["Video", "obj.video"])
        }

    override val symbol: String
        get() {
            return when (if (file.hasValidName()) type else VideoType.VIDEO) {
                VideoType.AUDIO -> DefaultConfig["ui.symbol.audio", "\uD83D\uDD09"]
                VideoType.IMAGE -> DefaultConfig["ui.symbol.image", "\uD83D\uDDBC️️"]
                VideoType.VIDEO -> DefaultConfig["ui.symbol.video", "\uD83C\uDF9E️"]
                VideoType.IMAGE_SEQUENCE -> DefaultConfig["ui.symbol.imageSequence", "\uD83C\uDF9E️"]
                VideoType.UNKNOWN -> "?"
            }
        }

    override fun getSplittingModes(): List<String> {
        return listOf("Video", "Audio", "Both")
    }

    override fun getSplitElement(mode: String, index: Int): Element {

        // todo create a transform, where only a single frame is visible
        // todo we should create a frames filter option for that probably...

        val hasVideo: Boolean
        val hasAudio: Boolean
        when (mode) {
            "Video" -> {
                hasVideo = true
                hasAudio = false
            }
            "Audio" -> {
                hasVideo = false
                hasAudio = true
            }
            else -> {
                hasVideo = true
                hasAudio = true
            }
        }

        val child = clone() as Video
        if (!hasAudio) child.amplitude.set(0f)
        if (!hasVideo) child.scale.set(Vector3f())

        val color = child.color
        val meta = forcedMeta!!
        val time = meta.videoFPS * (index + 0.1)
        val oldColor = color[time]
        color.clear()
        val epsilon = 1e-9
        color.addKeyframe(time - epsilon, Vector4f(oldColor).apply { w = 0f })
        color.addKeyframe(time, Vector4f(oldColor))
        color.addKeyframe(time + epsilon, Vector4f(oldColor).apply { w = 0f })
        child.timeOffset.value = time
        child.timeDilation.value = epsilon

        return Element(meta.videoWidth, meta.videoHeight, child)

    }

    override fun getSplitLength(mode: String): Int {
        return forcedMeta?.videoFrameCount ?: 0
    }

    override fun clone(workspace: FileReference): Transform {
        val clone = super.clone(workspace) as Video
        clone.lastFrame = lastFrame
        clone.lastW = lastW
        clone.lastH = lastH
        clone.lastDuration = lastDuration
        clone.lastFile = lastFile
        clone.lastAddedEndKeyframesFile = lastAddedEndKeyframesFile
        clone.type = type
        clone.needsImageUpdate = needsImageUpdate
        return clone
    }
}
