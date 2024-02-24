package me.anno.remsstudio

import me.anno.Build
import me.anno.Time.deltaTime
import me.anno.Time.gameTime
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
import me.anno.remsstudio.CheckVersion.checkVersion
import me.anno.remsstudio.animation.AnimatedProperty
import me.anno.remsstudio.audio.AudioManager2
import me.anno.remsstudio.cli.RemsCLI
import me.anno.remsstudio.gpu.ShaderLibV2
import me.anno.remsstudio.objects.Camera
import me.anno.remsstudio.objects.Transform
import me.anno.remsstudio.objects.text.Text
import me.anno.remsstudio.ui.StudioFileImporter
import me.anno.remsstudio.ui.scene.ScenePreview
import me.anno.remsstudio.ui.sceneTabs.SceneTabs.currentTab
import me.anno.ui.Panel
import me.anno.ui.Style
import me.anno.ui.base.components.AxisAlignment
import me.anno.ui.editor.PropertyInspector.Companion.invalidateUI
import me.anno.ui.editor.WelcomeUI
import me.anno.ui.editor.files.FileContentImporter
import me.anno.utils.OS
import me.anno.utils.hpc.ProcessingQueue
import kotlin.math.min

// todo improvements:
//  if playing forward, and time is non-modified, use VideoStream for much better playback performance

// todo bug: when editing a driver, we should see its curve
// todo bug: there is a webm file, whose video is black, and the audio only plays in the file explorer, not the studio :(
// todo right-click option to remove linear sections from keyframe panel;
// todo right-click option to thin out sections from keyframe panel;
// todo right-click option to select by specific channel only (e.g. to rect-select all y over 0.5);
// to do morphing: up/down/left/right
// to do morphing: rect/hexagon shape?, rotate it?
// to do hexagon/triangle/rectangle pixelation: rotate it?
// done record multiple properties at once, e.g. camera motion and rotation
// done morphing: swirl

// todo respect masks when editing multiple instances at once

// todo make music x times calmer, if another audio line (voice) is on as an optional feature

// todo bugs:
//  - shadows on SDF text have a black border, but they shouldn't
//  - sometimes delete-key isn't registered as such
//  - video files cannot be properly deleted, because files can't be deleted when reading them

// todo use reflectionMap for mesh rendering where available
//       - option to bake it around the mesh?

// todo isolate and remove certain frequencies from audio
// todo visualize audio frequency, always!!!, from 25Hz to 48kHz
// inspiration: https://www.youtube.com/watch?v=RA5UiLYWdbM

// todo show loudness of audio based on perceived amplitude, instead of real amplitude

// todo scripting?...
// todo gizmos

// Launch4j

// todo calculate in-between frames for video by motion vectors; https://www.youtube.com/watch?v=PEe-ZeVbTLo ?
// 30 -> 60 fps
// the topic is complicated...

// todo mode to move vertices of rectangle one by one (requires clever transforms, or a new type of GFXTransform)
// for perspective matching: e.g. moving a fake image onto another image

// todo create proxies only for sections of video
// todo proxies for sped-up videos? e.g. every 10th frame? idk...

// proxy creation uses 100% cpu... prevent that somehow, or decrease process priority?
// it uses 36% on its own -> heavier weight?
// -> idk how on Windows; done for Linux

// nearby frame compression (small changes between frames, could use lower resolution) on the gpu side? maybe...
// -> would maybe allow 60fps playback better

// todo discard alpha being > or < than x / map alpha

// todo splines for the polygon line

// todo sketching: draw frame by frame, only save x,y,radius?
//  record drawing, and use meta-ball like shapes (maybe)

// todo translations for everything...
// todo limit the history to entries with 5x the same name? how exactly?...

// todo saturation/lightness controls by hue

// to do Mod with "hacked"-text effect for text: swizzle characters and introduce others?

// todo when playing video, and the time hasn't been touched manually, slide the time panel, when the time reaches the end: slide by 1x window width

object RemsStudio : EngineBase("Rem's Studio", 10301, true), WelcomeUI {

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
        editorTime += deltaTime * editorTimeDilation
        if (editorTime <= 0.0 && editorTimeDilation < 0.0) {
            editorTimeDilation = 0.0
            editorTime = 0.0
        }
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
            blockAlignmentX.set(0f)
            blockAlignmentY.set(0f)
            textAlignment.set(0f)
            relativeCharSpacing = 0.12f
            invalidate()
        }
        background.alignmentX = AxisAlignment.FILL
        background.alignmentY = AxisAlignment.FILL
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
        root.findFirstInAll { it.clearCache(); false }
    }

    // UI with traditional editor?
    // - adding effects
    // - simple mask overlays
    // - simple color correction

    @JvmStatic
    fun main(args: Array<String>) {

        // to do integration-test scene with ALL rendering/playback features
        // -> Example Project
        // todo publish that project, and write an article for it
        // todo publish example projects for all wiki pages?

        Build.isDebug = false
        Build.isShipped = true
        Build.lock()

        if (args.isEmpty()) {
            run()
        } else {
            RemsCLI.main(args)
        }
    }

}