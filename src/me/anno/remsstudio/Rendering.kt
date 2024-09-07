package me.anno.remsstudio

import me.anno.engine.EngineBase.Companion.workspace
import me.anno.engine.Events.addEvent
import me.anno.gpu.GFX
import me.anno.gpu.WindowManagement
import me.anno.io.MediaMetadata.Companion.getMeta
import me.anno.io.files.FileReference
import me.anno.io.files.InvalidRef
import me.anno.language.translation.NameDesc
import me.anno.remsstudio.RemsStudio.motionBlurSteps
import me.anno.remsstudio.RemsStudio.project
import me.anno.remsstudio.RemsStudio.root
import me.anno.remsstudio.RemsStudio.shutterPercentage
import me.anno.remsstudio.RemsStudio.targetOutputFile
import me.anno.remsstudio.RemsStudio.targetTransparency
import me.anno.remsstudio.audio.AudioCreatorV2
import me.anno.remsstudio.objects.Camera
import me.anno.remsstudio.objects.Transform
import me.anno.remsstudio.objects.video.Video
import me.anno.remsstudio.video.FrameTaskV2
import me.anno.remsstudio.video.videoAudioCreatorV2
import me.anno.ui.base.menu.Menu.ask
import me.anno.ui.base.menu.Menu.msg
import me.anno.ui.base.progress.ProgressBar
import me.anno.utils.files.FileChooser
import me.anno.utils.files.FileExtensionFilter
import me.anno.utils.structures.Collections.filterIsInstance2
import me.anno.utils.structures.lists.Lists.firstInstanceOrNull2
import me.anno.utils.types.Strings
import me.anno.utils.types.Strings.getImportTypeByExtension
import me.anno.video.VideoCreator
import me.anno.video.VideoCreator.Companion.defaultQuality
import me.anno.video.ffmpeg.FFMPEGEncodingBalance
import me.anno.video.ffmpeg.FFMPEGEncodingType
import org.apache.logging.log4j.LogManager
import kotlin.concurrent.thread
import kotlin.math.max
import kotlin.math.roundToInt

@Suppress("MemberVisibilityCanBePrivate")
object Rendering {

    var isRendering = false
        set(value) {
            WindowManagement.mayIdle = !value
            field = value
        }

    const val minimumDivisor = 4

    private val LOGGER = LogManager.getLogger(Rendering::class)

    fun renderPart(size: Int, ask: Boolean, callback: () -> Unit) {
        renderVideo(RemsStudio.targetWidth / size, RemsStudio.targetHeight / size, ask, callback)
    }

    fun renderSetPercent(ask: Boolean, callback: () -> Unit) {
        val project = project ?: throw IllegalStateException("Missing project")
        renderVideo(
            max(minimumDivisor, (project.targetWidth * project.targetSizePercentage / 100).roundToInt()),
            max(minimumDivisor, (project.targetHeight * project.targetSizePercentage / 100).roundToInt()),
            ask, callback
        )
    }

    fun filterAudio(scene: Transform): List<Video> {
        return scene.listOfAll
            .filterIsInstance2(Video::class)
            .filter {
                it.forcedMeta?.hasAudio == true && (it.amplitude.isAnimated || it.amplitude[0.0] * 32e3f > 1f)
            }.toList()
    }

    fun renderVideo(width: Int, height: Int, ask: Boolean, callback: () -> Unit) {

        val divW = width % minimumDivisor
        val divH = height % minimumDivisor
        if (divW != 0 || divH != 0) return renderVideo(
            width - divW,
            height - divH,
            ask, callback
        )

        if (isRendering) return onAlreadyRendering()

        val targetOutputFile = findTargetOutputFile(RenderType.VIDEO)
        if (targetOutputFile.exists && ask) {
            return askOverridingIsAllowed(targetOutputFile) {
                renderVideo(width, height, false, callback)
            }
        }

        val isGif = targetOutputFile.lcExtension == "gif"

        isRendering = true
        LOGGER.info("Rendering video at $width x $height")

        val duration = RemsStudio.targetDuration
        val tmpFile = getTmpFile(targetOutputFile)
        val fps = RemsStudio.targetFPS
        val totalFrameCount = max(1, (fps * duration).toInt() + 1)
        val sampleRate = max(1, RemsStudio.targetSampleRate)
        val samples = RemsStudio.targetSamples
        val project = project

        val scene = root.clone()

        val audioSources = if (isGif) emptyList() else filterAudio(scene)

        val balance = project?.ffmpegBalance ?: FFMPEGEncodingBalance.M0
        val type = project?.ffmpegFlags ?: FFMPEGEncodingType.DEFAULT

        val videoCreator = VideoCreator(
            width, height,
            RemsStudio.targetFPS, totalFrameCount, balance, type,
            project?.targetVideoQuality ?: defaultQuality,
            targetTransparency,
            if (audioSources.isEmpty()) targetOutputFile else tmpFile
        )

        val progress = GFX.someWindow.addProgressBar(
            object : ProgressBar(
                "Rendering", "Frames",
                totalFrameCount.toDouble()
            ) {
                override fun formatProgress(): String {
                    val progress = progress.toInt()
                    val total = total.toInt()
                    return "$name: $progress / $total $unit"
                }
            }
        )

        val videoAudioCreator = videoAudioCreatorV2(
            videoCreator, samples, scene, findCamera(scene), duration, sampleRate, audioSources,
            motionBlurSteps, shutterPercentage, targetOutputFile, progress
        )

        videoAudioCreator.onFinished = {
            // todo sometimes the "pipe fails", and that is reported as a green bar -> needs to be red (cancelled)
            //  this happens after something has been cancelled :/ -> FFMPEG not closed down?
            isRendering = false
            progress.finish()
            callback()
            tmpFile.invalidate()
            targetOutputFile.invalidate()
        }

        videoCreator.init()
        videoAudioCreator.start()

    }

    private fun getTmpFile(file: FileReference) =
        file.getSibling(file.nameWithoutExtension + ".tmp." + targetOutputFile.extension)

    fun renderFrame(width: Int, height: Int, time: Double, ask: Boolean, callback: () -> Unit) {

        val targetOutputFile = findTargetOutputFile(RenderType.FRAME)
        if (targetOutputFile.exists && ask) {
            return askOverridingIsAllowed(targetOutputFile) {
                renderFrame(width, height, time, false, callback)
            }
        }

        LOGGER.info("Rendering frame at $time, $width x $height")

        val scene = root.clone() // so that changed don't influence the result
        val camera = findCamera(scene)
        FrameTaskV2(
            width, height,
            RemsStudio.targetFPS,
            scene, camera,
            motionBlurSteps[time],
            shutterPercentage[time],
            time,
            targetOutputFile
        ).start(callback)

    }

    private fun findCamera(scene: Transform): Camera {
        val camera0 = scene.listOfAll.firstInstanceOrNull2(Camera::class)
        return camera0 ?: RemsStudio.nullCamera ?: Camera()
    }

    fun overrideAudio(callback: () -> Unit) {
        if (isRendering) return onAlreadyRendering()
        FileChooser.selectFiles(
            NameDesc("Choose source file"),
            allowFiles = true, allowFolders = false,
            allowMultiples = false, toSave = false,
            startFolder = project?.scenes ?: workspace,
            filters = listOf(
                FileExtensionFilter(
                    NameDesc("Video"),
                    Strings.findImportTypeExtensions("Video")
                ),
                FileExtensionFilter(NameDesc("*"), emptyList())
            )
        ) { videoSources ->
            if (videoSources.size == 1) {
                val videoSrc = videoSources.first()
                if (videoSrc != project?.targetOutputFile) {
                    addEvent { // wait for other window to be closed, so the progress bar chooses a good window
                        overrideAudio(videoSrc, callback)
                    }
                } else LOGGER.warn("Files must not be the same")
            }
        }
    }

    fun overrideAudio(videoSrc: FileReference, callback: () -> Unit) {

        val meta = getMeta(videoSrc, false)!!

        isRendering = true
        LOGGER.info("Rendering audio onto video")

        val duration = meta.duration
        val sampleRate = max(1, RemsStudio.targetSampleRate)

        val scene = root.clone()
        val audioSources = filterAudio(scene)

        // if empty, skip?
        LOGGER.info("Found ${audioSources.size} audio sources")

        val progress = GFX.someWindow.addProgressBar(object :
            ProgressBar("Audio Override", "Samples", duration * sampleRate) {
            override fun formatProgress(): String {
                return "$name: ${progress.toLong()} / ${total.toLong()} $unit"
            }
        })
        AudioCreatorV2(scene, findCamera(scene), audioSources, duration, sampleRate, progress).apply {
            onFinished = {
                println("Finished overriding audio")
                isRendering = false
                progress.finish()
                callback()
                targetOutputFile.invalidate()
            }
            thread(name = "Rendering::renderAudio()") {
                createOrAppendAudio(targetOutputFile, videoSrc, false)
            }
        }
    }

    fun renderAudio(ask: Boolean, callback: () -> Unit) {

        if (isRendering) return onAlreadyRendering()

        val targetOutputFile = findTargetOutputFile(RenderType.AUDIO)
        if (targetOutputFile.exists && ask) {
            return askOverridingIsAllowed(targetOutputFile) {
                renderAudio(false, callback)
            }
        }

        isRendering = true
        LOGGER.info("Rendering audio")

        val duration = RemsStudio.targetDuration
        val sampleRate = max(1, RemsStudio.targetSampleRate)

        val scene = root.clone()
        val audioSources = filterAudio(scene)

        // todo if is empty, send a warning instead of doing something

        val progress = GFX.someWindow.addProgressBar(
            object : ProgressBar("Audio Export", "Samples", duration * sampleRate) {
                override fun formatProgress(): String {
                    return "$name: ${progress.toLong()} / ${total.toLong()} $unit"
                }
            })
        AudioCreatorV2(scene, findCamera(scene), audioSources, duration, sampleRate, progress).apply {
            onFinished = {
                isRendering = false
                progress.finish()
                callback()
                targetOutputFile.invalidate()
            }
            thread(name = "Rendering::renderAudio()") {
                createOrAppendAudio(targetOutputFile, InvalidRef, false)
            }
        }

    }

    private fun onAlreadyRendering() {
        val windowStack = GFX.someWindow.windowStack
        msg(
            windowStack, NameDesc(
                "Rendering already in progress!",
                "If you think, this is an error, please restart!",
                "ui.warn.renderingInProgress"
            )
        )
    }

    private fun askOverridingIsAllowed(targetOutputFile: FileReference, callback: () -> Unit) {
        val windowStack = GFX.someWindow.windowStack
        ask(windowStack, NameDesc("Override %1?").with("%1", targetOutputFile.name), callback)
    }

    enum class RenderType(
        val importType: String,
        val extension: String,
        val defaultName: String = "output.$extension"
    ) {
        VIDEO("Video", ".mp4"),
        AUDIO("Audio", ".mp3"),
        FRAME("Image", ".png")
    }

    fun findTargetOutputFile(type: RenderType): FileReference {
        var targetOutputFile = targetOutputFile
        val defaultExtension = type.extension
        val defaultName = type.defaultName
        do {
            val file0 = targetOutputFile
            if (targetOutputFile.exists && targetOutputFile.isDirectory) {
                targetOutputFile = targetOutputFile.getChild(defaultName)
            } else if (!targetOutputFile.name.contains('.')) {
                targetOutputFile = targetOutputFile.getSiblingWithExtension(defaultExtension)
            }
        } while (file0 !== targetOutputFile)
        val importType = getImportTypeByExtension(targetOutputFile.lcExtension)
        if (importType == "Text" && RenderType.entries.none { importType == it.importType }) {
            LOGGER.warn("The file extension .${targetOutputFile.extension} is unknown! Your export may fail!")
            return targetOutputFile
        }
        val targetType = type.importType
        if (importType != targetType) {
            // wrong extension -> place it automatically
            val fileName = targetOutputFile.nameWithoutExtension + defaultExtension
            return targetOutputFile.getSibling(fileName)
        }
        return targetOutputFile
    }

}