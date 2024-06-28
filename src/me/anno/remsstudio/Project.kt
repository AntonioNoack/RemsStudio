package me.anno.remsstudio

import me.anno.config.DefaultConfig.style
import me.anno.engine.EngineBase.Companion.workspace
import me.anno.engine.Events.addEvent
import me.anno.gpu.GFX
import me.anno.io.saveable.Saveable
import me.anno.io.config.ConfigBasics
import me.anno.io.files.FileReference
import me.anno.io.files.InvalidRef
import me.anno.io.json.generic.JsonReader
import me.anno.io.json.generic.JsonWriter
import me.anno.io.json.saveable.JsonStringReader
import me.anno.io.json.saveable.JsonStringWriter
import me.anno.io.utils.StringMap
import me.anno.language.Language
import me.anno.remsstudio.RemsStudio.editorTime
import me.anno.remsstudio.RemsStudio.project
import me.anno.remsstudio.RemsStudioUILayouts.createDefaultMainUI
import me.anno.remsstudio.history.History
import me.anno.remsstudio.objects.Camera
import me.anno.remsstudio.objects.Transform
import me.anno.remsstudio.ui.*
import me.anno.remsstudio.ui.editor.cutting.LayerViewContainer
import me.anno.remsstudio.ui.scene.SceneTabData
import me.anno.remsstudio.ui.scene.StudioSceneView
import me.anno.remsstudio.ui.sceneTabs.SceneTab
import me.anno.remsstudio.ui.sceneTabs.SceneTabs
import me.anno.remsstudio.utils.Utils.getAnimated
import me.anno.ui.Panel
import me.anno.ui.custom.CustomContainer
import me.anno.ui.custom.CustomList
import me.anno.ui.input.NumberType
import me.anno.utils.structures.Collections.filterIsInstance2
import me.anno.utils.structures.lists.Lists.firstInstanceOrNull2
import me.anno.utils.types.AnyToFloat
import me.anno.video.ffmpeg.FFMPEGEncodingBalance
import me.anno.video.ffmpeg.FFMPEGEncodingType
import org.apache.logging.log4j.LogManager
import kotlin.math.min
import kotlin.math.roundToInt

@Suppress("MemberVisibilityCanBePrivate")
class Project(var name: String, val file: FileReference) : Saveable() {

    val configFile = file.getChild("config.json")
    val uiFile get() = getUILayoutFile("ui")
    val tabsFile = file.getChild("tabs.json")

    val config: StringMap

    init {
        val defaultConfig = StringMap()
        defaultConfig["general.name"] = name
        defaultConfig["target.width"] = 1920
        defaultConfig["target.height"] = 1080
        defaultConfig["target.fps"] = 30f
        config = ConfigBasics.loadConfig(configFile, file, defaultConfig, true)
    }

    val scenes = file.getChild("Scenes")

    init {
        scenes.mkdirs()
    }

    lateinit var mainUI: Panel

    fun resetUIToDefault() {
        mainUI = createDefaultMainUI(style)
    }

    fun loadInitialUI() {

        fun tabsDefault() {
            val ref = scenes.getChild("Root.json")
            val tab0 = if (ref.exists) {
                try {
                    val data = JsonStringReader.read(ref.inputStreamSync(), workspace, true)
                    val trans = data.firstInstanceOrNull2(Transform::class)
                    val history = data.firstInstanceOrNull2(History::class)
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
                val loadedUIData = JsonStringReader
                    .read(tabsFile, workspace, true)
                val sceneTabs = loadedUIData
                    .filterIsInstance2(SceneTabData::class)
                if (sceneTabs.isEmpty()) {
                    tabsDefault()
                } else {
                    addEvent {
                        SceneTabs.closeAll()
                        for (tabData in sceneTabs) {
                            try {
                                val tab = SceneTab(InvalidRef, Transform(), null)
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
            val loadedUIData = loadUILayout()
            if (loadedUIData != null) {
                mainUI = loadedUIData
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
        JsonStringWriter.save(data, tabsFile, workspace)
    }

    fun loadUILayout(name: String = uiFile.nameWithoutExtension): Panel? {
        return loadUILayout(getUILayoutFile(name))
    }

    fun loadUILayout(file: FileReference): Panel? {
        LOGGER.debug("Trying to load layout from {}", file)
        if (!file.exists || file.isDirectory) {
            LOGGER.warn("$file doesn't exist")
            return null
        }
        return file.inputStreamSync().use { fis ->
            val library = StudioUITypeLibrary()
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
                        "PropertyInspector" -> StudioPropertyInspector({ Selection.selectedInspectables }, style)
                        "TimeControlsPanel" -> TimeControlsPanel(style)
                        else -> library.getType(type)?.generator?.invoke()
                    }
                    if (obj == null) {
                        notFound += type
                        return null
                    }
                    val weight = AnyToFloat.getFloat(arr[1], 1f)
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

    fun saveUILayout(name: String = uiFile.nameWithoutExtension) {
        getUILayoutFile(name).outputStream().use { fos ->
            val writer = JsonWriter(fos)
            val cdc = mainUI as CustomList
            fun write(c: Panel, w: Float) {
                when (c) {
                    is CustomContainer -> write(c.child, w)
                    is CustomList -> {
                        writer.beginArray()
                        writer.write(if (c.isY) "CustomListY" else "CustomListX")
                        writer.write((w * 1000f).roundToInt())
                        val weightSum = c.children.sumOf { it.weight.toDouble() }.toFloat()
                        for (chi in c.children) {
                            write(chi, chi.weight / weightSum)
                        }
                        writer.endArray()
                    }
                    else -> {
                        writer.beginArray()
                        writer.write(c.className)
                        writer.write((w * 1000f).roundToInt())
                        writer.endArray()
                    }
                }
            }
            write(cdc, 1f)
        }
    }

    // do we need multiple targets per project? maybe... soft links!
    // do we need a target at all? -> yes
    // a project always is a folder
    // zip this folder all the time to not waste SSD lifetime? -> no, we don't have that many files
    // -> we need to be able to show contents of zip files then


    var timelineSnapping = config["editor.timelineSnapping", 0.0]
    var timelineSnappingOffset = config["editor.timelineSnappingOffset", 0.0]
    var timelineSnappingRadius = config["editor.timelineSnappingRadius", 10]
    var targetDuration = config["target.duration", 5.0]
    var targetSampleRate = config["target.sampleRate", 48000]
    var targetSizePercentage = config["target.sizePercentage", 100f]
    var targetTransparency = config["target.transparency", false]
    var targetWidth = config["target.width", 1920]
    var targetHeight = config["target.height", 1080]
    var targetFPS = config["target.fps", 30.0]
    var targetOutputFile = config["target.output", file.getChild("output.mp4")]
    var targetVideoQuality = config["target.quality", 23]
    var targetSamples = config["target.samples", min(8, GFX.maxSamples)]
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
        config["target.samples"] = targetSamples
        config["target.output"] = targetOutputFile.toString()
        config["recent.files"] = SceneTabs.sceneTabs
            .filter { it.file != InvalidRef }
            .joinToString("\n") { it.file.toString() }
        config["camera.null"] = nullCamera
        config["editor.time"] = editorTime
        config["language"] = language.code
        config["target.ffmpegFlags.id"] = ffmpegFlags.id
        config["target.encodingBalance"] = ffmpegBalance.value
        config["target.transparency"] = targetTransparency
        config["editor.timelineSnapping"] = timelineSnapping
        config["editor.timelineSnappingOffset"] = timelineSnappingOffset
        config["editor.timelineSnappingRadius"] = timelineSnappingRadius
        ConfigBasics.save(configFile, config.toString())
    }

    fun save() {
        saveConfig()
        SceneTabs.currentTab?.save {}
        saveUILayout()
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

        val MotionBlurType = NumberType.INT_PLUS.withDefaultValue(8)
        val ShutterPercentageType = NumberType.FLOAT_PLUS.withDefaultValue(1f)

        fun getUILayoutFile(name: String): FileReference {
            return ConfigBasics.getConfigFile("$name.layout.json")
        }

        fun getUIFiles(): List<FileReference> {
            return ConfigBasics.configFolder.listChildren()
                .filter { it.name.endsWith(".layout.json") }
        }
    }
}