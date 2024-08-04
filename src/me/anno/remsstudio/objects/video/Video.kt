package me.anno.remsstudio.objects.video

import me.anno.animation.LoopingState
import me.anno.audio.openal.AudioTasks.addAudioTask
import me.anno.config.DefaultConfig
import me.anno.ecs.annotations.Range
import me.anno.engine.inspector.Inspectable
import me.anno.gpu.GFX.isFinalRendering
import me.anno.remsstudio.video.UVProjection
import me.anno.gpu.texture.Clamping
import me.anno.gpu.texture.TextureCache
import me.anno.gpu.texture.TextureReader.Companion.imageTimeout
import me.anno.image.svg.SVGMeshCache
import me.anno.io.MediaMetadata
import me.anno.io.MediaMetadata.Companion.getMeta
import me.anno.io.base.BaseWriter
import me.anno.io.files.FileReference
import me.anno.io.files.InvalidRef
import me.anno.language.translation.Dict
import me.anno.language.translation.NameDesc
import me.anno.remsstudio.RemsStudio.targetHeight
import me.anno.remsstudio.RemsStudio.targetWidth
import me.anno.remsstudio.animation.AnimatedProperty
import me.anno.remsstudio.audio.AudioFileStreamOpenAL2
import me.anno.remsstudio.audio.effects.SoundPipeline
import me.anno.remsstudio.gpu.TexFiltering
import me.anno.remsstudio.gpu.TexFiltering.Companion.getFiltering
import me.anno.remsstudio.objects.GFXTransform
import me.anno.remsstudio.objects.Transform
import me.anno.remsstudio.objects.models.SpeakerModel.drawSpeakers
import me.anno.remsstudio.objects.modes.VideoType
import me.anno.remsstudio.objects.video.VideoDrawing.drawImage
import me.anno.remsstudio.objects.video.VideoDrawing.drawImageSequence
import me.anno.remsstudio.objects.video.VideoDrawing.drawVideo
import me.anno.remsstudio.objects.video.VideoInspector.createVideoInspector
import me.anno.remsstudio.objects.video.VideoResourceClaiming.claimImage
import me.anno.remsstudio.objects.video.VideoResourceClaiming.claimImageSequence
import me.anno.remsstudio.objects.video.VideoResourceClaiming.claimVideo
import me.anno.remsstudio.objects.video.VideoSerialization.copyUnserializableProperties
import me.anno.remsstudio.objects.video.VideoSerialization.needSuperSetProperty
import me.anno.remsstudio.objects.video.VideoSerialization.save1
import me.anno.remsstudio.objects.video.VideoSymbol.getVideoSymbol
import me.anno.remsstudio.objects.video.VideoUpdate.videoUpdate
import me.anno.ui.Style
import me.anno.ui.base.groups.PanelListY
import me.anno.ui.editor.PropertyInspector.Companion.invalidateUI
import me.anno.ui.editor.SettingCategory
import me.anno.utils.structures.ValueWithDefault
import me.anno.utils.structures.ValueWithDefaultFunc
import me.anno.utils.structures.maps.BiMap
import me.anno.video.ImageSequenceMeta
import me.anno.video.MissingFrameException
import me.anno.video.VideoCache
import me.anno.video.formats.gpu.GPUFrame
import org.apache.logging.log4j.LogManager
import org.joml.Matrix4fArrayList
import org.joml.Vector3f
import org.joml.Vector4f
import kotlin.math.max
import kotlin.math.min

// game, where everything is explicitly rendered on 5-10 cubemaps first? lol
// sadly this doesn't make for any content yet, so it's this raw just a bad idea

// todo frame interpolation
// todo detect which frames actually changed, and interpolate between them
// (would smooth out slight stutter)

// todo AMD FSR as interpolation method

// todo auto-exposure correction by calculating the exposure, and adjusting the brightness

// todo feature tracking on videos as anchors, e.g. for easy blurry signs, or text above heads (marker on head/eyes)

// todo ask the user whether he wants to proceed, if there are warnings in the scene

/**
 * Images, Cubemaps, Videos, Audios, joint into one
 * */
@Suppress("MemberVisibilityCanBePrivate")
class Video(var file: FileReference = InvalidRef, parent: Transform? = null) : GFXTransform(parent) {

    constructor() : this(InvalidRef, null)

    companion object {

        val framesPerContainer = 128

        val editorFPS = intArrayOf(1, 2, 3, 5, 10, 24, 30, 60, 90, 120, 144, 240, 300, 360)

        private val LOGGER = LogManager.getLogger(Video::class)

        val forceAutoScale get() = DefaultConfig["rendering.video.forceAutoScale", true]
        val forceFullScale get() = DefaultConfig["rendering.video.forceFullScale", false]

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

    val streamManager = VideoStreamManager(this)

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

    fun usesBlankFrameDetection(): Boolean {
        return blankFrameThreshold > 0f
    }

    val amplitude = AnimatedProperty.floatPlus(1f)
    var pipeline = SoundPipeline(this)
    val isLooping = ValueWithDefaultFunc {
        if (file.lcExtension == "gif") LoopingState.PLAY_LOOP
        else LoopingState.PLAY_ONCE
    }

    var stayVisibleAtEnd = false

    var is3D = false

    val meta get() = getMeta(file, async = !isFinalRendering)
    val forcedMeta get() = getMeta(file, false)

    var needsUpdate = true
    var audioStream: AudioFileStreamOpenAL2? = null

    fun stopPlayback() {
        needsUpdate = false
        audioStream?.stop()
        audioStream = null // for garbage collection
    }

    override fun destroy() {
        super.destroy()
        streamManager.destroy()
        addAudioTask("stop", 1) { stopPlayback() }
    }

    // we need a flag, whether we draw in editor mode or not -> GFX.isFinalRendering
    // to do a separate mode, where resource availability is enforced? -> yes, we have that
    // Transforms, which load resources, should load async, and throw an error, if they don't block, while final-rendering

    override fun onReadingEnded() {
        super.onReadingEnded()
        needsUpdate = true
    }

    override fun getDocumentationURL(): String? = when (type) {
        VideoType.IMAGE -> "https://remsstudio.phychi.com/?s=learn/images"
        VideoType.VIDEO, VideoType.IMAGE_SEQUENCE -> "https://remsstudio.phychi.com/?s=learn/videos"
        else -> null
    }

    override fun clearCache() {
        lastTexture = null
        needsImageUpdate = true
        lastFile = null
    }

    var zoomLevel = 0

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

    fun onMissingImageOrFrame(frame: Int) {
        if (isFinalRendering) throw MissingFrameException("$file, $frame/${meta?.videoFrameCount}")
        else needsImageUpdate = true
    }

    var lastFrame: GPUFrame? = null

    fun getImage(): Any? {
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

    fun getVideoFrame(scale: Int, index: Int, fps: Double): GPUFrame? {
        return VideoCache.getVideoFrame(file, scale, index, framesPerContainer, fps, videoFrameTimeout, true)
    }

    var needsImageUpdate = false
    var lastTexture: Any? = null
    override fun claimLocalResources(lTime0: Double, lTime1: Double) {
        val minT = min(lTime0, lTime1)
        val maxT = max(lTime0, lTime1)
        when (type) {
            VideoType.VIDEO -> claimVideo(minT, maxT)
            VideoType.IMAGE_SEQUENCE -> claimImageSequence(minT, maxT)
            VideoType.IMAGE -> claimImage()
            VideoType.AUDIO, VideoType.UNKNOWN -> {} // nothing to do for audio
        }
        if (needsImageUpdate) {
            invalidateUI(true)
            needsImageUpdate = false
        }
    }

    var lastAddedEndKeyframesFile: FileReference? = null

    fun update() {
        val file = file
        videoUpdate(file, file.hasValidName())
    }

    override fun onDraw(stack: Matrix4fArrayList, time: Double, color: Vector4f) {

        needsImageUpdate = false

        val file = file
        if (file.hasValidName()) {
            videoUpdate(file, true)
            val meta = meta
            when (type) {
                VideoType.VIDEO -> drawVideo(stack, time, color)
                VideoType.IMAGE_SEQUENCE -> drawImageSequence(stack, time, color)
                VideoType.IMAGE -> drawImage(stack, time, color)
                VideoType.AUDIO -> {
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
        createVideoInspector(inspected, list, style, getGroup)
    }

    override fun save(writer: BaseWriter) {
        super.save(writer)
        save1(writer)
    }

    override fun setProperty(name: String, value: Any?) {
        if (needSuperSetProperty(name, value)) {
            super.setProperty(name, value)
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
        get() = getVideoSymbol()

    override fun clone(workspace: FileReference): Transform {
        val clone = super.clone(workspace) as Video
        return copyUnserializableProperties(clone)
    }
}
