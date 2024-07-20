package me.anno.remsstudio.objects.video

import me.anno.remsstudio.audio.AudioFileStreamOpenAL2
import me.anno.remsstudio.objects.Camera
import me.anno.remsstudio.objects.modes.VideoType

object AudioPlayback {
    /**
     * is synchronized with the audio thread
     * */
    fun Video.startPlayback(globalTime: Double, speed: Double, camera: Camera){
        when (type) {
            VideoType.VIDEO, VideoType.AUDIO -> {
                // why an exception? because I happened to run into this issue
                if (speed == 0.0) throw IllegalArgumentException("Audio speed must not be 0.0, because that's inaudible")
                stopPlayback()
                val meta = forcedMeta
                if (meta?.hasAudio == true) {
                    val component = AudioFileStreamOpenAL2(this, speed, globalTime, camera)
                    this.audioStream = component
                    component.start()
                } else audioStream = null
            }
            else -> {
                // image and image sequence cannot contain audio,
                // so we can avoid getting the metadata for the files with ffmpeg
                stopPlayback()
            }
        }
    }
}