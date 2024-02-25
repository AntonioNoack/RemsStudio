package me.anno.remsstudio

import me.anno.engine.EngineBase.Companion.workspace
import me.anno.gpu.GFX
import me.anno.gpu.GFXBase
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
import me.anno.remsstudio.objects.Audio
import me.anno.remsstudio.objects.Camera
import me.anno.remsstudio.objects.Transform
import me.anno.remsstudio.video.FrameTaskV2
import me.anno.remsstudio.video.videoAudioCreatorV2
import me.anno.ui.base.menu.Menu.ask
import me.anno.ui.base.menu.Menu.msg
import me.anno.ui.base.progress.ProgressBar
import me.anno.utils.files.FileChooser
import me.anno.utils.types.Strings.defaultImportType
import me.anno.utils.types.Strings.getImportType
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
            GFXBase.mayIdle = !value
            field = value
        }

    val div = 4

    private val LOGGER = LogManager.getLogger(Rendering::class)

    fun renderPart(size: Int, ask: Boolean, callback: () -> Unit) {
        renderVideo(RemsStudio.targetWidth / size, RemsStudio.targetHeight / size, ask, callback)
    }

    fun renderSetPercent(ask: Boolean, callback: () -> Unit) {
        val project = project ?: throw IllegalStateException("Missing project")
        renderVideo(
            max(div, (project.targetWidth * project.targetSizePercentage / 100).roundToInt()),
            max(div, (project.targetHeight * project.targetSizePercentage / 100).roundToInt()),
            ask, callback
        )
    }

    fun filterAudio(scene: Transform): List<Audio> {
        return scene.listOfAll
            .filterIsInstance<Audio>()
            .filter {
                it.forcedMeta?.hasAudio == true && (it.amplitude.isAnimated || it.amplitude[0.0] * 32e3f > 1f)
            }.toList()
    }

    fun renderVideo(width: Int, height: Int, ask: Boolean, callback: () -> Unit) {

        val divW = width % div
        val divH = height % div
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
        val cameras = scene.listOfAll.filterIsInstance<Camera>()
        return cameras.firstOrNull() ?: RemsStudio.nullCamera ?: Camera()
    }

    fun overrideAudio(callback: () -> Unit) {
        if (isRendering) return onAlreadyRendering()
        FileChooser.selectFiles(
            NameDesc("Choose source file"),
            allowFiles = true, allowFolders = false,
            allowMultiples = false, toSave = false,
            startFolder = project?.scenes ?: workspace,
            filters = emptyList() // todo video filter
        ) {
            if (it.size == 1) {
                val video = it.first()
                if (video != project?.targetOutputFile) {
                    overrideAudio(video, callback)
                } else LOGGER.warn("Files must not be the same")
            }
        }
    }

    fun overrideAudio(video: FileReference, callback: () -> Unit) {

        val meta = getMeta(video, false)!!

        isRendering = true
        LOGGER.info("Rendering audio onto video")

        val duration = meta.duration
        val sampleRate = max(1, RemsStudio.targetSampleRate)

        val scene = root.clone()
        val audioSources = filterAudio(scene)

        // if empty, skip?
        LOGGER.info("Found ${audioSources.size} audio sources")

        // todo progress bar didn't show up :/, why?
        val progress = GFX.someWindow.addProgressBar(object :
            ProgressBar("Audio Override", "Samples", duration * sampleRate) {
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
                createOrAppendAudio(targetOutputFile, video, false)
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

        fun createProgressBar(): ProgressBar {
            return object : ProgressBar("Audio Export", "Samples", duration * sampleRate) {
                override fun formatProgress(): String {
                    return "$name: ${progress.toLong()} / ${total.toLong()} $unit"
                }
            }
        }

        val progress = GFX.someWindow.addProgressBar(createProgressBar())
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
        val importType = targetOutputFile.extension.getImportType()
        if (importType == defaultImportType && RenderType.entries.none { importType == it.importType }) {
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