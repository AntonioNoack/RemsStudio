package me.anno.remsstudio.audio

import me.anno.audio.openal.AudioManager
import me.anno.maths.Maths.MILLIS_TO_NANOS
import me.anno.remsstudio.RemsStudio
import me.anno.remsstudio.RemsStudio.editorTime
import me.anno.remsstudio.RemsStudio.editorTimeDilation
import me.anno.remsstudio.RemsStudio.root
import me.anno.remsstudio.objects.Audio
import me.anno.remsstudio.objects.Transform
import kotlin.math.abs

object AudioManager2 {

    val camera by lazy { RemsStudio.nullCamera!! }
    fun updateTime(time: Double, dilation: Double, transform: Transform) {
        if (transform is Audio) {
            transform.startPlayback(time, dilation, camera)
        }
        for (child in transform.children) {
            updateTime(time, dilation, child)
        }
    }

    fun checkTree(transform: Transform) {
        if (transform is Audio && transform.needsUpdate) {
            transform.needsUpdate = false
            transform.startPlayback(editorTime, editorTimeDilation, camera)
        }
        for (child in transform.children) {
            checkTree(child)
        }
    }

    fun stop(transform: Transform = root) {
        if (transform is Audio) {
            transform.stopPlayback()
        }
        for (child in transform.children) {
            stop(child)
        }
    }

    fun init() {
        AudioManager.onUpdate = { time ->
            if (RemsStudio.isPlaying && AudioManager.ctr++ > 15) {
                AudioManager.ctr = 0;checkTree(root)
            }
            if (RemsStudio.isPlaying && AudioManager.needsUpdate && abs(time - AudioManager.lastUpdate) > 200 * MILLIS_TO_NANOS) {
                // ensure 200 ms delay between changing the time / dilation
                // for performance reasons
                AudioManager.lastUpdate = time
                AudioManager.needsUpdate = false
                updateTime(editorTime, editorTimeDilation, root)
            }
        }
    }

}