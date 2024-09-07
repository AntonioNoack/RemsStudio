package me.anno.remsstudio.objects.video

import me.anno.audio.openal.AudioManager
import me.anno.audio.openal.AudioTasks.addAudioTask
import me.anno.engine.inspector.Inspectable
import me.anno.gpu.texture.Texture2D
import me.anno.language.translation.NameDesc
import me.anno.remsstudio.RemsStudio.isPaused
import me.anno.remsstudio.RemsStudio.nullCamera
import me.anno.remsstudio.objects.ColorGrading
import me.anno.remsstudio.objects.Transform
import me.anno.remsstudio.objects.modes.VideoType
import me.anno.remsstudio.objects.video.AudioPlayback.startPlayback
import me.anno.remsstudio.objects.video.Video.Companion.editorFPS
import me.anno.remsstudio.objects.video.Video.Companion.forceAutoScale
import me.anno.remsstudio.objects.video.Video.Companion.forceFullScale
import me.anno.remsstudio.objects.video.Video.Companion.videoScaleNames
import me.anno.ui.Panel
import me.anno.ui.Style
import me.anno.ui.base.SpyPanel
import me.anno.ui.base.buttons.TextButton
import me.anno.ui.base.groups.PanelListY
import me.anno.ui.base.text.UpdatingTextPanel
import me.anno.ui.editor.SettingCategory
import me.anno.ui.input.EnumInput
import me.anno.ui.input.FloatInput
import me.anno.ui.input.NumberType
import me.anno.utils.structures.Collections.filterIsInstance2
import me.anno.utils.types.Booleans.toInt
import me.anno.utils.types.Floats.f2
import me.anno.utils.types.Strings.formatTime2
import org.apache.logging.log4j.LogManager
import kotlin.math.max

object VideoInspector {
    private val LOGGER = LogManager.getLogger(VideoInspector::class)

    fun Video.createVideoInspector(
        inspected: List<Inspectable>, list: PanelListY, style: Style,
        getGroup: (NameDesc) -> SettingCategory
    ) {

        if (inspected.size == 1) { // else not really supported well
            val pipeline = pipeline
            for (it in pipeline.effects) {
                it.audio = this
            }
            pipeline.audio = this
            pipeline.createInspector(list, style, getGroup)
        }

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

        list += vi(
            inspected, "File Location", "Source file of this video", "video.fileLocation",
            null, file, style
        ) { newFile, _ ->
            for (x in c) x.file = newFile
        }

        val colorGroup = getGroup(NameDesc("Color", "", "obj.color"))
        colorGroup += vis(
            c, "Corner Radius", "Makes the corners round", "cornerRadius",
            c.map { it.cornerRadius }, style
        )

        val uvMap = getGroup(NameDesc("Texture", "", "obj.uvs"))
        uvMap += img(
            vis(
                c, "Tiling", "(tile count x, tile count y, offset x, offset y)", "video.tiling",
                c.map { it.tiling }, style
            )
        )
        uvMap += img(
            vi(
                inspected, "UV-Projection", "Can be used for 360°-Videos", "video.uvProjection",
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
        (if (forceFullScale || forceAutoScale) editor else quality()) += vid(
            EnumInput(
                NameDesc(
                    "Preview Scale",
                    "Full video resolution isn't always required. Define it yourself, or set it to automatic.",
                    "obj.video.previewScale"
                ),
                NameDesc(videoScaleNames.reverse[videoScale.value] ?: "Auto"),
                videoScales.map { NameDesc(it.key) }, style
            )
                .setChangeListener { _, index, _ -> for (x in c) x.videoScale.value = videoScales[index].value }
                .setIsSelectedListener { show(t, null) })

        editor += vid(
            EnumInput(
                NameDesc(
                    "Preview FPS",
                    "Smoother preview, heavier calculation",
                    "obj.video.previewFps"
                ),
                NameDesc(editorVideoFPS.value.toString()),
                editorFPS.filterIndexed { index, it -> index == 0 || it * 0.98 <= (meta?.videoFPS ?: 1e85) }
                    .map { NameDesc(it.toString()) }, style
            )
                .setChangeListener { _, index, _ -> for (x in c) x.editorVideoFPS.value = editorFPS[index] }
                .setIsSelectedListener { show(t, null) })

        quality() += vid(
            FloatInput(
                NameDesc(
                    "Blank Frames Removal",
                    "When a set percentage of pixels change within 1 frame, that frame is removed from the source\n" +
                            "The higher, the more frames are accepted; 0 = disabled\n" +
                            "Cannot handle more than two adjacent blank frames", "obj.video.blankFrameRemoval"
                ), blankFrameThreshold,
                NumberType.FLOAT_03, style
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
            if ((audioStream == null) != invert) playbackTitles.first else playbackTitles.second

        val playbackButton = TextButton(NameDesc(getPlaybackTitle(false)), false, style)
        audio += aud(playbackButton
            .addLeftClickListener {
                if (isPaused) {
                    playbackButton.text = getPlaybackTitle(true)
                    if (audioStream == null) {
                        addAudioTask("start", 5) {
                            val audio2 = Video(file, null)
                            audio2.update() // load type
                            audio2.startPlayback(0.0, 1.0, nullCamera!!)
                            audioStream = audio2.audioStream
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
}