package me.anno.objects

import me.anno.audio.AudioManager
import me.anno.audio.AudioStreamOpenAL
import me.anno.gpu.GFX
import me.anno.io.ISaveable
import me.anno.io.base.BaseWriter
import me.anno.objects.animation.AnimatedProperty
import me.anno.studio.Studio
import me.anno.studio.Studio.nullCamera
import me.anno.ui.base.ButtonPanel
import me.anno.ui.base.groups.PanelListY
import me.anno.ui.editor.AudioLinePanel
import me.anno.ui.style.Style
import me.anno.video.FFMPEGMetadata.Companion.getMeta
import org.joml.Matrix4fArrayList
import org.joml.Vector4f
import java.io.File
import java.lang.IllegalArgumentException

// flat playback vs 3D playback
// respect scale? nah, rather not xD
// (it becomes pretty complicated, I think)

// todo speaker distance
// todo speaker symbols (if meta is not null, and audio is available)

open class Audio(var file: File = File(""), parent: Transform? = null): GFXTransform(parent){

    val amplitude = AnimatedProperty.floatPlus(1f)
    val forcedMeta get() = getMeta(file, false)
    val meta get() = getMeta(file, true)

    var is3D = false

    var needsUpdate = true
    var isLooping =
        if(file.extension.equals("gif", true)) LoopingState.PLAY_LOOP
        else LoopingState.PLAY_ONCE

    var component: AudioStreamOpenAL? = null

    /**
     * is synchronized with the audio thread
     * */
    fun start(globalTime: Double, speed: Double, camera: Camera){
        // why an exception? because I happended to run into this issue
        if(speed == 0.0) throw IllegalArgumentException("Audio speed must not be 0.0, because that's inaudible")
        needsUpdate = false
        component?.stop()
        val meta = forcedMeta
        if(meta?.hasAudio == true){
            val component = AudioStreamOpenAL(this, speed, globalTime, camera)
            this.component = component
            component.start()
        } else component = null
    }

    fun stop(){
        component?.stop()
        component = null // for garbage collection
    }

    override fun onDestroy() {
        super.onDestroy()
        GFX.addAudioTask { stop(); 1 }
    }

    // we need a flag, whether we draw in editor mode or not -> GFX.isFinalRendering
    // to do a separate mode, where resource availability is enforced? -> yes, we have that
    // Transforms, which load resources, should load async, and throw an error, if they don't block, while final-rendering

    override fun onDraw(stack: Matrix4fArrayList, time: Double, color: Vector4f) {

        // to do ensure, that the correct buffer is being generated -> done
        // to do we need to invalidate buffers, if we touch the custom timeline mode, or accelerate/decelerate audio... -> half done
        // how should we generate left/right audio? -> we need to somehow do this in software, too, for rendering -> started

        getMeta(file, true) // just in case we need it ;)

    }

    override fun createInspector(list: PanelListY, style: Style) {
        super.createInspector(list, style)
        list += VI("File Location", "Source file of this video", null, file, style){ file = it }
        val meta = forcedMeta
        if(meta?.hasAudio == true){
            list += AudioLinePanel(meta, this, style)
        }
        list += VI("Amplitude", "How loud it is", amplitude, style)
        list += VI("Looping Type", "Whether to repeat the song/video", null, isLooping, style){
            isLooping = it
            AudioManager.requestUpdate()
        }
        list += VI("Is 3D Sound", "Sound becomes directional", null, is3D, style){
            is3D = it
            AudioManager.requestUpdate()
        }
        val playbackTitles = "Text Playback" to "Stop Playback"
        fun getPlaybackTitle(invert: Boolean) = if((component == null) != invert) playbackTitles.first else playbackTitles.second
        val playbackButton = ButtonPanel(getPlaybackTitle(false), style)
        list += playbackButton
            .setSimpleClickListener {
                if(Studio.isPaused){
                    playbackButton.text = getPlaybackTitle(true)
                    if(component == null){
                        GFX.addAudioTask {
                            val audio = Audio(file, null)
                            audio.start(0.0, 1.0, nullCamera)
                            component = audio.component
                            1
                        }
                    } else GFX.addAudioTask { stop(); 1 }
                } else Studio.warn("Separated playback is only available with paused editor")
            }
            .setTooltip("Listen to the audio separated from the rest")
    }

    override fun save(writer: BaseWriter) {
        super.save(writer)
        writer.writeFile("src", file)
        writer.writeObject(this, "amplitude", amplitude)
        writer.writeInt("isLooping", isLooping.id, true)
    }

    override fun readInt(name: String, value: Int) {
        when(name){
            "isLooping" -> isLooping = LoopingState.getState(value)
            else -> super.readInt(name, value)
        }
    }

    override fun readObject(name: String, value: ISaveable?) {
        when(name){
            "amplitude" -> amplitude.copyFrom(value)
            else -> super.readObject(name, value)
        }
    }

    override fun readString(name: String, value: String) {
        when(name){
            "src" -> file = File(value)
            else -> super.readString(name, value)
        }
    }

    override fun onReadingEnded() {
        super.onReadingEnded()
        needsUpdate = true
    }

    override fun getClassName() = "Audio"

}