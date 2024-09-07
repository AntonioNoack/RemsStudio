package me.anno.remsstudio

import me.anno.Build
import me.anno.Time.gameTime
import me.anno.Time.rawDeltaTime
import me.anno.audio.openal.ALBase
import me.anno.audio.openal.AudioManager
import me.anno.audio.openal.AudioTasks.addAudioTask
import me.anno.config.DefaultConfig
import me.anno.config.DefaultStyle.baseTheme
import me.anno.engine.EngineBase
import me.anno.engine.Events.addEvent
import me.anno.engine.GFXSettings
import me.anno.engine.OfficialExtensions
import me.anno.extensions.ExtensionLoader
import me.anno.gpu.GFX
import me.anno.gpu.OSWindow
import me.anno.input.ActionManager
import me.anno.input.Input.keyUpCtr
import me.anno.installer.Installer
import me.anno.io.files.FileReference
import me.anno.language.Language
import me.anno.language.translation.Dict
import me.anno.language.translation.NameDesc
import me.anno.remsstudio.CheckVersion.checkVersion
import me.anno.remsstudio.animation.AnimatedProperty
import me.anno.remsstudio.audio.AudioManager2
import me.anno.remsstudio.cli.RemsCLI
import me.anno.remsstudio.gpu.ShaderLibV2
import me.anno.remsstudio.objects.Camera
import me.anno.remsstudio.objects.Transform
import me.anno.remsstudio.objects.text.Text
import me.anno.remsstudio.ui.StudioFileImporter
import me.anno.remsstudio.ui.editor.TimelinePanel.Companion.centralTime
import me.anno.remsstudio.ui.editor.TimelinePanel.Companion.dtHalfLength
import me.anno.remsstudio.ui.scene.ScenePreview
import me.anno.remsstudio.ui.sceneTabs.SceneTabs.currentTab
import me.anno.ui.Panel
import me.anno.ui.Style
import me.anno.ui.editor.PropertyInspector.Companion.invalidateUI
import me.anno.ui.editor.WelcomeUI
import me.anno.ui.editor.files.FileContentImporter
import me.anno.utils.OS
import me.anno.utils.hpc.ProcessingQueue
import kotlin.math.abs
import kotlin.math.min

// todo bugs:
//  - video files cannot be properly deleted, because files can't be deleted when reading them
//  - treeview needs padding: last item cannot be properly selected

// to do morphing: up/down/left/right
// to do morphing: rect/hexagon shape?, rotate it?
// to do hexagon/triangle/rectangle pixelation: rotate it?

// todo make music x times calmer, if another audio line (voice) is on as an optional feature

// todo use reflectionMap for mesh rendering where available
//       - option to bake it around the mesh?

// todo scripting?...
// todo gizmos

// Launch4j

// todo calculate in-between frames for video by motion vectors; https://www.youtube.com/watch?v=PEe-ZeVbTLo ?
// 30 -> 60 fps
// the topic is complicated...

// todo mode to move vertices of rectangle one by one (requires clever transforms, or a new type of GFXTransform)
// for perspective matching: e.g. moving a fake image onto another image

// todo proxies for sped-up videos? e.g. every 10th frame? idk...

// todo discard alpha being > or < than x / map alpha

// todo splines for the polygon line

// todo translations for everything...
// todo limit the history to entries with 5x the same name? how exactly?...

// todo saturation/lightness controls by hue

@Suppress("MemberVisibilityCanBePrivate")
object RemsStudio : EngineBase(NameDesc("Rem's Studio"), 10302, true), WelcomeUI {

    val defaultWindowStack get() = GFX.someWindow.windowStack
    var hideUnusedProperties = false

    lateinit var currentCamera: Camera
    var lastTouchedCamera: Camera? = null

    override var language: Language
        get() = project?.language ?: super.language
        set(value) {
            project?.language = value
        }

    private fun updateLastLocalTime(parent: Transform, time: Double) {
        val localTime = parent.getLocalTime(time)
        parent.lastLocalTime = localTime
        val children = parent.children
        for (i in children.indices) {
            val child = children[i]
            updateLastLocalTime(child, localTime)
        }
    }

    private fun updateEditorTime() {
        // if we'd use the clamped deltaTime, audio and video would run out of sync
        val oldTime = editorTime
        editorTime += rawDeltaTime * editorTimeDilation
        if (editorTime <= 0.0 && editorTimeDilation < 0.0) {
            editorTimeDilation = 0.0
            editorTime = 0.0
        }
        // when playing video, and the time hasn't been touched manually, slide the time panel,
        // when the time reaches the end: slide by 1x window width
        if (isTimeVisible(oldTime) && !isTimeVisible(editorTime)) {
            centralTime += dtHalfLength * 2.0
        }
    }

    private fun isTimeVisible(time: Double): Boolean {
        return abs(centralTime - time) < dtHalfLength
    }

    override fun loadConfig() {
        RemsRegistry.init()
        RemsConfig.init()
    }

    override fun onGameInit() {
        OfficialExtensions.register()
        ExtensionLoader.load()
        gfxSettings = GFXSettings.get(DefaultConfig["editor.gfx", GFXSettings.LOW.id], GFXSettings.LOW)
        workspace = DefaultConfig["workspace.dir", OS.documents.getChild(configName)]
        Installer.checkFFMPEGInstall()
        checkVersion()
        AudioManager2.init()
        ShaderLibV2.init()
    }

    override fun isSelected(obj: Any?): Boolean {
        return contains(obj, Selection.selectedInspectables) ||
                contains(obj, Selection.selectedProperties) ||
                contains(obj, Selection.selectedTransforms)
    }

    private fun <V> contains(obj: Any?, list: List<V>?): Boolean {
        return list != null && obj in list
    }

    override fun createBackground(style: Style): Panel {
        val background = ScenePreview(style)
        root.children.clear()
        Text("Rem's Studio", root).apply {
            relativeCharSpacing = 0.12f
            invalidate()
        }
        return background
    }

    override fun loadProject(name: String, folder: FileReference): Pair<String, FileReference> {
        val project = Project(name.trim(), folder)
        RemsStudio.project = project
        project.open()
        GFX.someWindow.title = "Rem's Studio: ${project.name}"
        return Pair(project.name, project.file)
    }

    override fun createProjectUI() {
        RemsStudioUILayouts.createEditorUI(this)
    }

    override fun createUI() {
        Dict.loadDefault()
        create(this)
        StudioActions.register()
        ActionManager.init()
    }

    override fun openHistory() {
        history?.display()
    }

    override fun save() {
        project?.save()
    }

    override fun getDefaultFileLocation(): FileReference {
        return workspace
    }

    override fun getPersistentStorage(): FileReference {
        return project?.file ?: super.getPersistentStorage()
    }

    override fun importFile(file: FileReference) {
        addEvent {
            StudioFileImporter.addChildFromFile(root, file, FileContentImporter.SoftLinkMode.ASK, true) {}
        }
    }

    var project: Project? = null

    var editorTime = 0.5

    var editorTimeDilation = 0.0
        set(value) {
            if (field != value) {
                updateAudio()
                field = value
            }
        }

    val isPaused get() = editorTimeDilation == 0.0
    val isPlaying get() = editorTimeDilation != 0.0

    val targetDuration get(): Double = project?.targetDuration ?: Double.POSITIVE_INFINITY
    val targetTransparency get() = project?.targetTransparency ?: false
    val targetSampleRate get(): Int = project?.targetSampleRate ?: 48000
    val targetFPS get(): Double = project?.targetFPS ?: 60.0
    val targetWidth get(): Int = project?.targetWidth ?: GFX.someWindow.width
    val targetHeight get(): Int = project?.targetHeight ?: GFX.someWindow.height
    val targetOutputFile get(): FileReference = project!!.targetOutputFile
    val motionBlurSteps get(): AnimatedProperty<Int> = project!!.motionBlurSteps
    val targetSamples get(): Int = project?.targetSamples ?: min(GFX.maxSamples, 8)
    val shutterPercentage get() = project!!.shutterPercentage
    val history get() = currentTab?.history
    val nullCamera get() = project?.nullCamera
    val timelineSnapping get(): Double = project?.timelineSnapping ?: 0.0
    val timelineSnappingOffset get(): Double = project?.timelineSnappingOffset ?: 0.0
    val timelineSnappingRadius get(): Int = project?.timelineSnappingRadius ?: 10

    var root = Transform()

    var currentlyDrawnCamera: Camera? = nullCamera

    override fun onGameLoopStart() {
        updateEditorTime()
        updateLastLocalTime(root, editorTime)
    }

    override fun onGameLoop(window: OSWindow, w: Int, h: Int) {
        DefaultConfig.saveMaybe("main.config")
        baseTheme.values.saveMaybe("style.config")
        Selection.update()
        super.onGameLoop(window, w, h)
    }

    override fun onGameLoopEnd() {

    }

    override fun onGameClose() {

    }

    private var lastCode: Any? = null
    fun incrementalChange(title: String, run: () -> Unit) =
        incrementalChange(title, title, run)

    val savingWorker = ProcessingQueue("Saving")

    fun incrementalChange(title: String, groupCode: String, run: () -> Unit) {
        val history = history ?: return run()
        val code = groupCode to keyUpCtr
        if (lastCode != code) {
            change(title, code, run)
            lastCode = code
        } else {
            run()
            // register for change
            if (savingWorker.remaining == 0) {
                savingWorker += {
                    synchronized(history) {
                        history.update(title, code)
                    }
                }
            }
        }
        currentTab?.hasChanged = true
        invalidateUI(false)
    }

    fun largeChange(title: String, run: () -> Unit) {
        change(title, gameTime, run)
        lastCode = null
        currentTab?.hasChanged = true
        invalidateUI(true)
    }

    private fun change(title: String, code: Any, run: () -> Unit) {
        val history = history ?: return run()
        synchronized(history) {
            if (history.isEmpty()) {
                history.put("Start State", Unit)
            }
            run()
            history.put(title, code)
        }
    }

    fun updateAudio() {
        addAudioTask("update", 100) {
            // update the audio player...
            if (isPlaying) {
                AudioManager.requestUpdate()
            } else {
                AudioManager2.stop()
            }
            ALBase.check()
        }
    }

    override fun clearAll() {
        super.clearAll()
        root.forAllInHierarchy { it.clearCache() }
    }

    @JvmStatic
    fun main(args: Array<String>) {

        // to do integration-test scene with ALL rendering/playback features
        // -> Example Project
        // todo publish that project, and write an article for it
        // todo publish example projects for all wiki pages?

        Build.isDebug = true // false
        // Build.isShipped = true
        Build.lock()

        if (args.isEmpty()) {
            run()
        } else {
            RemsCLI.main(args)
        }
    }

}