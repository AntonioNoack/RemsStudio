package me.anno.remsstudio

import me.anno.animation.Type
import me.anno.config.DefaultConfig.style
import me.anno.io.Saveable
import me.anno.io.config.ConfigBasics
import me.anno.io.files.FileReference
import me.anno.io.files.FileReference.Companion.getReference
import me.anno.io.json.JsonReader
import me.anno.io.json.JsonWriter
import me.anno.io.text.TextReader
import me.anno.io.text.TextWriter
import me.anno.io.utils.StringMap
import me.anno.language.Language
import me.anno.remsstudio.RemsStudio.editorTime
import me.anno.remsstudio.RemsStudio.project
import me.anno.remsstudio.RemsStudioUILayouts.createDefaultMainUI
import me.anno.remsstudio.history.History
import me.anno.remsstudio.objects.Camera
import me.anno.remsstudio.objects.Transform
import me.anno.remsstudio.ui.StudioFileExplorer
import me.anno.remsstudio.ui.StudioTreeView
import me.anno.remsstudio.ui.StudioUITypeLibrary
import me.anno.remsstudio.ui.editor.cutting.LayerViewContainer
import me.anno.remsstudio.ui.scene.SceneTabData
import me.anno.remsstudio.ui.scene.StudioSceneView
import me.anno.remsstudio.ui.sceneTabs.SceneTab
import me.anno.remsstudio.ui.sceneTabs.SceneTabs
import me.anno.remsstudio.utils.Utils.getAnimated
import me.anno.studio.StudioBase.Companion.addEvent
import me.anno.studio.StudioBase.Companion.workspace
import me.anno.ui.Panel
import me.anno.ui.custom.CustomContainer
import me.anno.ui.custom.CustomList
import me.anno.utils.bugs.SumOf
import me.anno.utils.types.Casting.castToFloat
import me.anno.video.ffmpeg.FFMPEGEncodingBalance
import me.anno.video.ffmpeg.FFMPEGEncodingType
import org.apache.logging.log4j.LogManager
import kotlin.math.roundToInt

// todo option to reset the timeline
class Project(var name: String, val file: FileReference) : Saveable() {

    val configFile = getReference(file, "config.json")
    val uiFile = getReference(file, "ui.json")
    val tabsFile = getReference(file, "tabs.json")

    val config: StringMap

    init {
        val defaultConfig = StringMap()
        defaultConfig["general.name"] = name
        defaultConfig["target.width"] = 1920
        defaultConfig["target.height"] = 1080
        defaultConfig["target.fps"] = 30f
        config = ConfigBasics.loadConfig(configFile, file, defaultConfig, true)
    }

    val scenes = getReference(file, "Scenes")

    init {
        scenes.mkdirs()
    }

    lateinit var mainUI: Panel

    fun resetUIToDefault() {
        mainUI = createDefaultMainUI(style)
    }

    fun loadUI() {

        fun tabsDefault() {
            val ref = getReference(scenes, "Root.json")
            val tab0 = if (ref.exists) {
                try {
                    val data = TextReader.read(ref.inputStreamSync(), workspace, true)
                    val trans = data.filterIsInstance<Transform>().firstOrNull()
                    val history = data.filterIsInstance<History>().firstOrNull()
                    if (trans != null) Pair(trans, history ?: History()) else null
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            } else null
            val tab = SceneTab(
                ref,
                tab0?.first ?: Transform().run {
                    name = "Root"
                    Camera(this)
                    this
                }, tab0?.second ?: History()
            )
            tab.save {}
            addEvent {
                SceneTabs.closeAll()
                SceneTabs.open(tab)
                saveTabs()
            }
        }


        // tabs
        try {
            if (tabsFile.exists) {
                val loadedUIData = TextReader
                    .read(tabsFile, workspace, true)
                val sceneTabs = loadedUIData
                    .filterIsInstance<SceneTabData>()
                if (sceneTabs.isEmpty()) {
                    tabsDefault()
                } else {
                    addEvent {
                        SceneTabs.closeAll()
                        for (tabData in sceneTabs) {
                            try {
                                val tab = SceneTab(null, Transform(), null)
                                tabData.apply(tab)
                                SceneTabs.open(tab)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
            } else tabsDefault()
        } catch (e: Exception) {
            e.printStackTrace()
            tabsDefault()
        }

        // main ui
        try {
            if (uiFile.exists) {
                val loadedUIData = loadUI2()
                if (loadedUIData != null) {
                    mainUI = loadedUIData
                } else resetUIToDefault()
            } else resetUIToDefault()
        } catch (e: Exception) {
            e.printStackTrace()
            resetUIToDefault()
        }

        (config["editor.time"] as? Double)?.apply {
            editorTime = this
        }

    }

    fun saveTabs() {
        val data = SceneTabs.sceneTabs.map { SceneTabData(it) }
        TextWriter.save(data, tabsFile, workspace)
    }

    fun loadUI2(): Panel? {
        return uiFile.inputStreamSync().use { fis ->
            val library = StudioUITypeLibrary()
            val types = library.types
            val notFound = HashSet<String>()
            val style = style
            fun load(arr: List<*>?): Panel? {
                arr ?: return null
                return try {
                    val type = arr[0] as? String ?: return null
                    val obj = when (type) {
                        "CustomListX" -> CustomList(false, style)
                        "CustomListY" -> CustomList(true, style)
                        "TreeView" -> StudioTreeView(style)
                        "FileExplorer" -> StudioFileExplorer(project?.scenes, style)
                        "CuttingView", "LayerViewContainer" -> LayerViewContainer(style)
                        "SceneView", "StudioSceneView" -> StudioSceneView(style)
                        else -> types[type]?.constructor?.invoke()
                    }
                    if (obj == null) {
                        notFound += type
                        return null
                    }
                    val weight = castToFloat(arr[1]!!) ?: 1f
                    if (obj is CustomList) {
                        for (i in 2 until arr.size) {
                            obj.add(load(arr[i] as? List<*>) ?: continue)
                        }
                        obj.weight = weight
                        obj
                    } else {
                        val container = CustomContainer(obj, library, style)
                        container.weight = weight
                        container
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }

            val obj = load(JsonReader(fis).readArray())
            if (notFound.isNotEmpty()) LOGGER.warn("UI-Types $notFound not found!")
            obj
        }
    }

    fun saveUI() {
        uiFile.outputStream().use { fos ->
            val writer = JsonWriter(fos)
            val cdc = mainUI as CustomList
            fun write(c: Panel, w: Float) {
                when (c) {
                    is CustomContainer -> write(c.child, w)
                    is CustomList -> {
                        writer.open(true)
                        writer.write(if (c.isY) "CustomListY" else "CustomListX")
                        writer.write((w * 1000f).roundToInt())
                        val weightSum = SumOf.sumOf(c.children) { it.weight }
                        for (chi in c.children) {
                            write(chi, chi.weight / weightSum)
                        }
                        writer.close(true)
                    }
                    else -> {
                        writer.open(true)
                        writer.write(c.className)
                        writer.write((w * 1000f).roundToInt())
                        writer.close(true)
                    }
                }
            }
            write(cdc, 1f)
        }
    }

    // do we need multiple targets per project? maybe... soft links!
    // do we need a target at all? -> yes
    // a project always is a folder
    // zip this folder all the time to not waste SSD life time? -> no, we don't have that many files
    // -> we need to be able to show contents of zip files then

    var targetDuration = config["target.duration", 5.0]
    var targetSampleRate = config["target.sampleRate", 48000]
    var targetSizePercentage = config["target.sizePercentage", 100f]
    var targetWidth = config["target.width", 1920]
    var targetHeight = config["target.height", 1080]
    var targetFPS = config["target.fps", 30.0]
    var targetOutputFile = config["target.output", getReference(file, "output.mp4")]
    var targetVideoQuality = config["target.quality", 23]
    var motionBlurSteps = config.getAnimated<Int>("target.motionBlur.steps", MotionBlurType)
    var shutterPercentage = config.getAnimated<Float>("target.motionBlur.shutterPercentage", ShutterPercentageType)
    var nullCamera = createNullCamera(config["camera.null"] as? Camera)
    var language = Language.get(config["language", Language.AmericanEnglish.code])
    var ffmpegFlags = FFMPEGEncodingType[config["target.ffmpegFlags.id", FFMPEGEncodingType.DEFAULT.id]]
    var ffmpegBalance = FFMPEGEncodingBalance[config["target.encodingBalance", 0.5f]]

    override val className get() = "Project"
    override val approxSize get() = 1000
    override fun isDefaultValue() = false

    fun open() {}

    fun saveConfig() {
        config["general.name"] = name
        config["target.duration"] = targetDuration
        config["target.sizePercentage"] = targetSizePercentage
        config["target.width"] = targetWidth
        config["target.height"] = targetHeight
        config["target.fps"] = targetFPS
        config["target.quality"] = targetVideoQuality
        config["target.motionBlur.steps"] = motionBlurSteps
        config["target.motionBlur.shutterPercentage"] = shutterPercentage
        config["target.sampleRate"] = targetSampleRate
        config["target.output"] = targetOutputFile.toString()
        config["recent.files"] = SceneTabs.sceneTabs
            .filter { it.file != null }
            .joinToString("\n") { it.file.toString() }
        config["camera.null"] = nullCamera
        config["editor.time"] = editorTime
        config["language"] = language.code
        config["target.ffmpegFlags.id"] = ffmpegFlags.id
        config["target.encodingBalance"] = ffmpegBalance.value
        ConfigBasics.save(configFile, config.toString())
    }

    fun save() {
        saveConfig()
        SceneTabs.currentTab?.save {}
        saveUI()
    }

    fun createNullCamera(camera: Camera?): Camera {
        return (camera ?: Camera(null).apply {
            name = "Inspector Camera"
            onlyShowTarget = false
        }).apply {
            // higher far value to allow other far values to be seen
            farZ.defaultValue = 5000f
            timeDilation.setDefault(0.0) // the camera has no time, so no motion can be recorded
        }
    }

    companion object {
        private val LOGGER = LogManager.getLogger(Project::class)
        val MotionBlurType = Type.INT_PLUS.withDefaultValue(8)
        val ShutterPercentageType = Type.FLOAT_PLUS.withDefaultValue(1f)
    }

}