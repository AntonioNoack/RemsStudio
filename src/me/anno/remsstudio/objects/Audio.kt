package me.anno.remsstudio.objects

import me.anno.animation.LoopingState
import me.anno.audio.openal.AudioTasks.addAudioTask
import me.anno.engine.inspector.Inspectable
import me.anno.io.MediaMetadata.Companion.getMeta
import me.anno.io.base.BaseWriter
import me.anno.io.files.FileReference
import me.anno.io.files.InvalidRef
import me.anno.remsstudio.animation.AnimatedProperty
import me.anno.remsstudio.audio.AudioFileStreamOpenAL2
import me.anno.remsstudio.audio.effects.SoundPipeline
import me.anno.ui.Style
import me.anno.ui.base.groups.PanelListY
import me.anno.ui.editor.SettingCategory
import me.anno.utils.files.LocalFile.toGlobalFile
import me.anno.utils.structures.ValueWithDefault.Companion.writeMaybe
import me.anno.utils.structures.ValueWithDefaultFunc
import org.joml.Matrix4fArrayList
import org.joml.Vector4f

// flat playback vs 3D playback
// respect scale? nah, rather not xD
// (it becomes pretty complicated, I think)

abstract class Audio(var file: FileReference = InvalidRef, parent: Transform? = null) : GFXTransform(parent) {

    val amplitude = AnimatedProperty.floatPlus(1f)
    var pipeline = SoundPipeline(this)
    val isLooping = ValueWithDefaultFunc {
        if (file.lcExtension == "gif") LoopingState.PLAY_LOOP
        else LoopingState.PLAY_ONCE
    }

    var stayVisibleAtEnd = false

    var is3D = false

    open val meta get() = getMeta(file, true)
    open val forcedMeta get() = getMeta(file, false)

    var needsUpdate = true
    var component: AudioFileStreamOpenAL2? = null

    /**
     * is synchronized with the audio thread
     * */
    open fun startPlayback(globalTime: Double, speed: Double, camera: Camera) {
        // why an exception? because I happened to run into this issue
        if (speed == 0.0) throw IllegalArgumentException("Audio speed must not be 0.0, because that's inaudible")
        stopPlayback()
        val meta = forcedMeta
        if (meta?.hasAudio == true) {
            val component = AudioFileStreamOpenAL2(this, speed, globalTime, camera)
            this.component = component
            component.start()
        } else component = null
    }

    fun stopPlayback() {
        needsUpdate = false
        component?.stop()
        component = null // for garbage collection
    }

    override fun onDestroy() {
        super.onDestroy()
        addAudioTask("stop", 1) { stopPlayback() }
    }

    // we need a flag, whether we draw in editor mode or not -> GFX.isFinalRendering
    // to do a separate mode, where resource availability is enforced? -> yes, we have that
    // Transforms, which load resources, should load async, and throw an error, if they don't block, while final-rendering

    override fun onDraw(stack: Matrix4fArrayList, time: Double, color: Vector4f) {

        // to do ensure, that the correct buffer is being generated -> done
        // to do we need to invalidate buffers, if we touch the custom timeline mode, or accelerate/decelerate audio... -> half done
        // how should we generate left/right audio? -> we need to somehow do this in software, too, for rendering -> started

        meta // just in case we need it ;)

    }

    override fun createInspector(
        inspected: List<Inspectable>,
        list: PanelListY,
        style: Style,
        getGroup: (title: String, description: String, dictSubPath: String) -> SettingCategory
    ) {
        super.createInspector(inspected, list, style, getGroup)
        if (inspected.size == 1) { // else not really supported well
            val pipeline = pipeline
            for (it in pipeline.effects) {
                it.audio = this
            }
            pipeline.audio = this
            pipeline.createInspector(list, style, getGroup)
        }
    }

    override fun save(writer: BaseWriter) {
        super.save(writer)
        writer.writeFile("file", file)
        writer.writeObject(this, "amplitude", amplitude)
        writer.writeObject(this, "effects", pipeline)
        writer.writeMaybe(this, "isLooping", isLooping)
    }

    override fun setProperty(name: String, value: Any?) {
        when (name) {
            "isLooping" -> isLooping.value = LoopingState.getState(value as? Int ?: return)
            "amplitude" -> amplitude.copyFrom(value)
            "effects" -> pipeline = value as? SoundPipeline ?: return
            "src", "file", "path" -> file =
                (value as? String)?.toGlobalFile() ?: (value as? FileReference) ?: InvalidRef
            else -> super.setProperty(name, value)
        }
    }

    override fun onReadingEnded() {
        super.onReadingEnded()
        needsUpdate = true
    }
}