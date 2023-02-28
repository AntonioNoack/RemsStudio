package me.anno.remsstudio.audio

import me.anno.audio.streams.AudioStream
import me.anno.remsstudio.objects.Audio
import me.anno.remsstudio.objects.Camera
import me.anno.remsstudio.objects.Transform
import me.anno.ui.base.progress.ProgressBar
import me.anno.video.AudioCreator

open class AudioCreatorV2(
    val scene: Transform,
    val camera: Camera,
    val audioSources: List<Audio>,
    durationSeconds: Double,
    sampleRate: Int,
    val progress: ProgressBar
) : AudioCreator(durationSeconds, sampleRate) {

    override fun hasStreams(): Boolean {
        return audioSources.isNotEmpty()
    }

    override fun createStreams(): List<AudioStream> {
        return audioSources.map { AudioFileStream2(it, 1.0, 0.0, sampleRate, camera) }
    }

    override fun onStreaming(bufferIndex: Long, streamIndex: Int) {
        progress.progress = (bufferSize * bufferIndex).toDouble()
        isCancelled = progress.isCancelled
    }

}