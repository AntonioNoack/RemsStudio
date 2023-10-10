package me.anno.remsstudio.cli

import me.anno.Engine
import me.anno.Time
import me.anno.cache.Cache
import me.anno.gpu.GFX
import me.anno.gpu.framebuffer.Frame
import me.anno.gpu.hidden.HiddenOpenGLContext
import me.anno.gpu.shader.ShaderLib
import me.anno.gpu.texture.Texture2D
import me.anno.gpu.texture.Texture2D.Companion.bindTexture
import me.anno.installer.Installer.checkInstall
import me.anno.io.config.ConfigBasics
import me.anno.io.files.FileReference.Companion.getReference
import me.anno.io.files.InvalidRef
import me.anno.io.text.TextReader
import me.anno.remsstudio.*
import me.anno.remsstudio.gpu.ShaderLibV2
import me.anno.remsstudio.objects.Transform
import me.anno.studio.CommandLines.parseDouble
import me.anno.studio.CommandLines.parseFloat
import me.anno.studio.CommandLines.parseInt
import me.anno.studio.StudioBase.Companion.workEventTasks
import me.anno.utils.Sleep.sleepABit
import me.anno.utils.types.Strings.getImportType
import me.anno.utils.types.Strings.isBlank2
import me.anno.utils.types.Strings.parseTimeOrNull
import org.apache.commons.cli.*
import org.apache.logging.log4j.LogManager
import org.lwjgl.opengl.GL11C
import java.util.*

object RemsCLI {

    @JvmStatic
    fun main(args: Array<String>) {
        RemsRegistry.init()
        val options = Options()
        options.addOption("o", "output", true, "output file")
        options.addOption("i", "input", true, "scene file")
        options.addOption("fps", true, "frames per second")
        options.addOption("shutter", true, "shutter percentage")
        options.addOption("motionBlurSteps", true, "motion blur steps")
        options.addOption("constantRateFactor", "crf", true, "video quality, 0 = lossless, 23 = default, 51 = worst")
        options.addOption("w", "width", true, "width of the rendered result")
        options.addOption("h", "height", true, "height of the rendered result")
        options.addOption("?", "help", false, "shows all options")
        options.addOption("f", "force", false, "forces existing files to be overridden")
        options.addOption("n", "no", false, "if asked to override file, answer 'no' automatically")
        options.addOption("y", "yes", false, "if asked to override file, answer 'yes' automatically")
        options.addOption("t", "type", true, "VIDEO (with audio), AUDIO or IMAGE")
        // options.addOption("config", true, "use a special config file") // todo implement
        options.addOption("start", true, "start time in seconds")
        options.addOption("end", true, "end time in seconds")
        options.addOption("duration", true, "end time after start, in seconds")
        options.addOption("project", true, "project settings, containing resolution, frame size, duration, ...")
        // todo balance
        options.addOption("samples", true, "audio samples per second & channel, e.g. 41000 or 48000")
        // todo all other relevant factors
        val parser = DefaultParser()
        try {
            val line = parser.parse(options, args)
            parse(line, options)
        } catch (e: ParseException) {
            error("Parsing failed! ${e.message}")
        }
    }

    fun parse(line: CommandLine, options: Options) {

        if (line.hasOption("help") || line.hasOption("?")) {
            return printHelp(options)
        }

        val sceneSourceFile = if (line.hasOption("input")) {
            getReference(line.getOptionValue("input"))
        } else return error("Input needs to be defined")

        // find project above scene source
        var project0 = getReference(sceneSourceFile).getParent() ?: InvalidRef
        var project1 = project0
        while (true) {
            project1 = project1.getParent() ?: break
            if (project1.getChild("config.json").exists &&
                project1.getChild("tabs.json").exists
            ) {
                project0 = project1
                break
            }
        }

        val yes = line.hasOption("yes") || line.hasOption("force")
        val no = line.hasOption("no")
        if (yes && no) return error("Cannot enable yes and no at the same time")

        init()

        val scene = try {
            TextReader.readFirstOrNull<Transform>(sceneSourceFile, project0, true)
                ?: return error("Could not find scene")
        } catch (e: RuntimeException) {
            e.printStackTrace()
            return error("Error in input file")
        }
        RemsStudio.root = scene

        val project = if (line.hasOption("project")) {
            Project("Unnamed", getReference(line.getOptionValue("project")))
        } else Project("Unknown", getReference(ConfigBasics.cacheFolder, "project0").apply { mkdirs() })
        RemsStudio.project = project

        project.targetFPS = line.parseDouble("fps", project.targetFPS)
        project.shutterPercentage.set(line.parseFloat("shutter", project.shutterPercentage[0.0]))
        project.motionBlurSteps.set(line.parseInt("motionBlurSteps", project.motionBlurSteps[0.0]))
        project.targetVideoQuality = line.parseInt("constantRateFactor", project.targetVideoQuality)
        project.targetSampleRate = line.parseInt("sampleRate", project.targetSampleRate)

        val outputFile = if (line.hasOption("output")) {
            getReference(line.getOptionValue("output"))
        } else project.targetOutputFile

        val renderType = if (line.hasOption("type")) {
            when (val type = line.getOptionValue("type").lowercase(Locale.getDefault())) {
                "video" -> Rendering.RenderType.VIDEO
                "audio" -> Rendering.RenderType.AUDIO
                "image" -> Rendering.RenderType.FRAME
                else -> return error("Unknown type $type")
            }
        } else when (outputFile.extension.getImportType()) {
            "Video" -> Rendering.RenderType.VIDEO
            "Audio" -> Rendering.RenderType.AUDIO
            else -> Rendering.RenderType.FRAME
        }

        val startTime = parseTime(line, "start", 0.0)
        val duration0 = parseTime(line, "duration", project.targetDuration)
        val endTime = parseTime(line, "end", startTime + duration0)

        scene.timeOffset.value += startTime

        val duration = endTime - startTime
        if (duration < 0 && renderType != Rendering.RenderType.FRAME) throw ParseException("Duration cannot be < 0")

        val output = Rendering.findTargetOutputFile(renderType)
        if (output.isDirectory || output.exists) {
            if (no) {
                warn("Aborted, because file already existed!")
                return
            } else if (!yes) {
                // ask
                if (!ask("Override file?", false)) {
                    return
                }
            }
        }

        project.targetOutputFile = outputFile

        val width = parseSize(line, "width", project.targetWidth)
        val height = parseSize(line, "height", project.targetHeight)

        // init OpenGL, shaders and textures
        if (renderType != Rendering.RenderType.AUDIO) initGFX()

        var isDone = false
        when (renderType) {
            Rendering.RenderType.VIDEO -> Rendering.renderVideo(
                width,
                height,
                false
            ) { isDone = true }

            Rendering.RenderType.AUDIO -> Rendering.renderAudio(
                false
            ) { isDone = true }

            Rendering.RenderType.FRAME -> Rendering.renderFrame(
                width,
                height,
                startTime,
                false
            ) { isDone = true }
        }

        // update loop (cache, GFX tasks)
        while (!isDone) {
            Texture2D.destroyTextures()
            GFX.resetFBStack()
            Time.updateTime()
            Cache.update()
            bindTexture(GL11C.GL_TEXTURE_2D, 0)
            // BlendDepth.reset()
            GL11C.glDisable(GL11C.GL_CULL_FACE)
            GFX.check()
            Frame.reset()
            GFX.workGPUTasks(false)
            workEventTasks()
            sleepABit(true)
        }

        LOGGER.info("Done")
        Engine.requestShutdown()

    }

    fun <V> warn(msg: String, value: V): V {
        warn(msg)
        return value
    }

    fun initGFX() {
        // good size? mmh...
        HiddenOpenGLContext.setSize(2048, 2048)
        HiddenOpenGLContext.createOpenGL()
        ShaderLib.init()
        ShaderLibV2.init()
    }

    fun init() {
        RemsStudio.setupNames()
        RemsConfig.init()
        checkInstall()
        // checkVersion() needs to be killed, if the time is long over ;)
    }

    fun ask(question: String, default: Boolean): Boolean {
        LOGGER.info("[Q] $question (y/n)")
        val br = System.`in`.bufferedReader()
        while (true) {
            val line = br.readLine() ?: return default
            if (!line.isBlank2()) {
                when (line.lowercase(Locale.getDefault())) {
                    "t", "true", "y", "yes", "1" -> return true
                    "f", "false", "n", "no", "0" -> return false
                }
            }
        }
    }

    private fun parseTime(line: CommandLine, name: String, defaultValue: Double): Double {
        return if (line.hasOption(name)) {
            val value = line.getOptionValue(name)
            value.parseTimeOrNull() ?: throw ParseException("Could not parse time '$value' for '$name'")
        } else defaultValue
    }

    private fun parseSize(line: CommandLine, name: String, defaultValue: Int): Int {
        return if (line.hasOption(name)) {
            val value = line.getOptionValue(name)
            val valueInt = value.toIntOrNull() ?: throw ParseException("Could not parse size '$value' for '$name'")
            if (valueInt < 1) throw ParseException("Size must not be smaller than 1!")
            return valueInt
        } else defaultValue
    }

    private fun readText(source: String): String {
        return if (source.startsWith("text://", true)) source.substring(7)
        else getReference(source).readTextSync()
    }

    private fun printHelp(options: Options) {
        val formatter = HelpFormatter()
        formatter.printHelp("RemsStudio", options)
    }

    fun warn(message: String) {
        LOGGER.warn(message)
    }

    fun error(message: String) {
        LOGGER.error(message)
    }

    private val LOGGER = LogManager.getLogger(RemsCLI::class)

}