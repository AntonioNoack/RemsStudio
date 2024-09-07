package me.anno.remsstudio

import me.anno.config.DefaultConfig
import me.anno.engine.inspector.Inspectable
import me.anno.gpu.GFX
import me.anno.io.base.BaseWriter
import me.anno.language.translation.Dict
import me.anno.language.translation.NameDesc
import me.anno.remsstudio.RemsStudio.editorTime
import me.anno.remsstudio.RemsStudio.motionBlurSteps
import me.anno.remsstudio.RemsStudio.project
import me.anno.remsstudio.RemsStudio.shutterPercentage
import me.anno.remsstudio.RemsStudio.targetDuration
import me.anno.remsstudio.RemsStudio.targetHeight
import me.anno.remsstudio.RemsStudio.targetOutputFile
import me.anno.remsstudio.RemsStudio.targetWidth
import me.anno.remsstudio.Rendering.overrideAudio
import me.anno.remsstudio.Rendering.renderAudio
import me.anno.remsstudio.Rendering.renderFrame
import me.anno.remsstudio.Rendering.renderPart
import me.anno.remsstudio.Rendering.renderSetPercent
import me.anno.remsstudio.objects.Transform
import me.anno.remsstudio.ui.scene.StudioSceneView
import me.anno.ui.Style
import me.anno.ui.base.buttons.TextButton
import me.anno.ui.base.groups.PanelListY
import me.anno.ui.base.text.TextPanel
import me.anno.ui.editor.SettingCategory
import me.anno.ui.editor.frames.FrameSizeInput
import me.anno.ui.input.*
import me.anno.utils.Color.black
import me.anno.utils.Color.mixARGB
import me.anno.utils.process.DelayedTask
import me.anno.video.ffmpeg.FFMPEGEncodingBalance
import me.anno.video.ffmpeg.FFMPEGEncodingType
import kotlin.math.max

object RenderSettings : Transform() {

    override val defaultDisplayName get() = "Render Settings"

    override fun createInspector(
        inspected: List<Inspectable>, list: PanelListY, style: Style,
        getGroup: (NameDesc) -> SettingCategory
    ) {

        val prefix = "renderSettings"
        val project = project ?: throw IllegalStateException("Missing project")
        list += TextPanel(defaultDisplayName, style)
            .apply { focusTextColor = textColor } // disable focus-color
        list += vi(
            inspected, "Duration (Seconds)", "Video length in seconds", "$prefix.videoLength",
            NumberType.FLOAT_PLUS, targetDuration, style
        ) { it, _ ->
            project.targetDuration = it
            save()
        }

        list += vi(
            inspected,
            "Relative Frame Size (%)", "For rendering tests, in percent", "$prefix.relativeFrameSize",
            NumberType.FLOAT_PERCENT, project.targetSizePercentage, style
        ) { it, _ ->
            project.targetSizePercentage = it
            save()
        }

        list += FrameSizeInput(
            NameDesc("Frame Size (Pixels)", "", "obj.$prefix.frameSizePixels"),
            "${project.targetWidth}x${project.targetHeight}", style
        )
            .setChangeListener { w, h ->
                project.targetWidth = max(1, w)
                project.targetHeight = max(1, h)
                save()
            }
            .setTooltip("Size of resulting video")

        var framesRates = DefaultConfig["rendering.frameRates", "60"]
            .split(',')
            .mapNotNull { it.trim().toDoubleOrNull() }
            .toMutableList()

        if (framesRates.isEmpty()) framesRates = arrayListOf(60.0)
        if (project.targetFPS !in framesRates) framesRates.add(0, project.targetFPS)

        list += EnumInput(
            NameDesc(
                "Frame Rate (Hz)",
                "Higher values give a smoother result, but render longer, and need more storage space.",
                "obj.$prefix.frameRateHz"
            ), NameDesc(project.targetFPS.toString()),
            framesRates.map { NameDesc(it.toString()) },
            style
        )
            .setChangeListener { value, _, _ ->
                project.targetFPS = value.englishName.toDouble()
                save()
            }
            .setTooltip("The fps of the video, or how many frame are shown per second")

        list += IntInput(
            NameDesc("Video Quality", "", "obj.$prefix.videoQuality"), "obj.videoQuality",
            project.targetVideoQuality, NumberType.VIDEO_QUALITY_CRF, style
        )
            .setChangeListener {
                project.targetVideoQuality = it.toInt()
                save()
            }
            .setTooltip(Dict["0 = lossless, 23 = default, 51 = worst; worse results have smaller file sizes", "obj.$prefix.videoQuality.desc"])

        val mbs = vi(
            "Motion-Blur-Steps", "0,1 = no motion blur, e.g. 16 = decent motion blur, sub-frames per frame",
            "$prefix.motionBlurSteps",
            project.motionBlurSteps, style
        )
        val mbs0 = mbs.child as IntInput
        val mbsListener = mbs0.changeListener
        mbs0.setChangeListener {
            mbsListener(it)
            save()
        }
        list += mbs

        list += BooleanInput(
            NameDesc(
                "Render Transparency",
                "Only supported by webm at the moment.",
                "obj.$prefix.renderTransparency"
            ), project.targetTransparency, false, style
        ).setChangeListener {
            project.targetTransparency = it
            save()
        }

        val samples = EnumInput(
            NameDesc(
                "GPU Samples",
                "Smooths edges. 1 = default. Support depends on GPU.",
                "obj.$prefix.gpuSamples"
            ),
            NameDesc("MSAA ${project.targetSamples}x"),
            listOf(1, 2, 4, 8, 16, 32, 64, 128).map {
                NameDesc(
                    if (it == 1) "No MSAA"
                    else if (it <= GFX.maxSamples) "MSAA ${it}x"
                    else "MSAA ${it}x (unsupported)"
                )
            }, style
        )
        samples.setChangeListener { _, index, _ ->
            project.targetSamples = 1 shl index
            // invalidate rendering as a preview
            // currently, this value affects editor rendering, too for wysiwyg
            for (window in GFX.windows) {
                for (window1 in window.windowStack) {
                    window1.panel.forAllVisiblePanels {
                        if (it is StudioSceneView) it.invalidateDrawing()
                    }
                }
            }
            save()
        }
        list += samples

        val shp = vi(
            "Shutter-Percentage (0-1)",
            "[Motion Blur] 1 = full frame is used; 0.1 = only 1/10th of a frame time is used",
            "$prefix.shutterPercentage",
            project.shutterPercentage, style
        )
        val shp0 = shp.child as FloatInput
        val shpListener = shp0.changeListener
        shp0.setChangeListener {
            shpListener(it)
            save()
        }
        list += shp

        list += EnumInput(
            NameDesc(
                "Encoding Speed / Compression",
                "How much time is spent on compressing the video into a smaller file",
                "obj.$prefix.encodingSpeed"
            ),
            project.ffmpegBalance.nameDesc, FFMPEGEncodingBalance.entries.map { it.nameDesc }, style
        ).setChangeListener { _, index, _ -> project.ffmpegBalance = FFMPEGEncodingBalance.entries[index]; save() }

        list += EnumInput(
            NameDesc(
                "Encoding Type",
                "Helps FFMPEG with the encoding process",
                "obj.$prefix.encodingType"
            ),
            project.ffmpegFlags.nameDesc, FFMPEGEncodingType.entries.map { it.nameDesc }, style
        ).setChangeListener { _, index, _ -> project.ffmpegFlags = FFMPEGEncodingType.entries[index]; save() }

        val fileInput = FileInput(
            NameDesc("Output File", "", "obj.$prefix.outputFile"),
            style, targetOutputFile, emptyList()
        )
        fileInput.setTooltip(Dict["Where the render result shall be stored/saved.", "obj.$prefix.outputFile.desc"])
        val originalColor = fileInput.base2.textColor
        fun updateFileInputColor() {
            val file = project.targetOutputFile
            fileInput.base2.run {
                textColor = mixARGB(
                    originalColor, when {
                        file.isDirectory -> 0xff0000 or black
                        file.exists -> 0xffff00 or black
                        else -> 0x00ff00 or black
                    }, 0.5f
                )
                focusTextColor = textColor
            }
        }

        updateFileInputColor()
        fileInput.addChangeListener { file ->
            project.targetOutputFile = file
            updateFileInputColor()
            save()
        }
        list += fileInput

        val callback: () -> Unit = { GFX.someWindow.requestAttentionMaybe() }

        list += TextButton(
            NameDesc(
                "Render at 100%",
                "Create video at full resolution",
                "obj.$prefix.render100Percent"
            ), false, style
        ).addLeftClickListener { renderPart(1, true, callback) }
        list += TextButton(
            NameDesc(
                "Render at 50%",
                "Create video at half resolution",
                "obj.$prefix.render50Percent"
            ), false, style
        ).addLeftClickListener { renderPart(2, true, callback) }
        list += TextButton(
            NameDesc(
                "Render at 25%",
                "Create video at quarter resolution",
                "obj.$prefix.render25Percent"
            ), false, style
        ).addLeftClickListener { renderPart(4, true, callback) }
        list += TextButton(
            NameDesc(
                "Render at Set%",
                "Create video at your custom set relative resolution",
                "obj.$prefix.renderSetPercent"
            ), false, style
        ).addLeftClickListener { renderSetPercent(true, callback) }
        list += TextButton(
            NameDesc(
                "Render Audio only",
                "Only creates an audio file; no video is rendered nor saved.",
                "obj.$prefix.renderAudio"
            ), false, style
        ).addLeftClickListener { renderAudio(true, callback) }
        list += TextButton(
            NameDesc(
                "Override Audio",
                "Overrides the audio of the selected file; this is useful to fix too quiet videos.",
                "obj.$prefix.overrideAudio"
            ), false, style
        ).addLeftClickListener { overrideAudio(callback) }
        list += TextButton(
            NameDesc(
                "Render Current Frame",
                "Renders the current frame into an image file.",
                "obj.$prefix.renderCurrentFrame"
            ), false, style
        ).addLeftClickListener { renderFrame(targetWidth, targetHeight, editorTime, true, callback) }
    }

    private val savingTask = DelayedTask { actuallySave() }

    fun save() {
        savingTask.update()
    }

    private fun actuallySave() {
        save()
        project!!.saveConfig()
    }

    override fun save(writer: BaseWriter) {
        super.save(writer)
        // hack for us to allow editing animated properties
        writer.writeObject(this, "motionBlurSteps", motionBlurSteps)
        writer.writeObject(this, "shutterPercentage", shutterPercentage)
    }

}