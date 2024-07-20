package me.anno.remsstudio.video

import me.anno.io.files.FileReference
import me.anno.remsstudio.animation.AnimatedProperty
import me.anno.remsstudio.audio.AudioCreatorV2
import me.anno.remsstudio.objects.video.Video
import me.anno.remsstudio.objects.Camera
import me.anno.remsstudio.objects.Transform
import me.anno.ui.base.progress.ProgressBar
import me.anno.video.VideoAudioCreator
import me.anno.video.VideoCreator

fun videoAudioCreatorV2(
    videoCreator: VideoCreator,
    samples: Int,
    scene: Transform,
    camera: Camera,
    durationSeconds: Double,
    sampleRate: Int,
    audioSources: List<Video>,
    motionBlurSteps: AnimatedProperty<Int>,
    shutterPercentage: AnimatedProperty<Float>,
    output: FileReference,
    progress: ProgressBar
) = VideoAudioCreator(
    videoCreator,
    VideoBackgroundTaskV2(videoCreator, samples, scene, camera, motionBlurSteps, shutterPercentage, progress),
    object : AudioCreatorV2(scene, camera, audioSources, durationSeconds, sampleRate, progress) {
        override fun hasStreams(): Boolean {// will be starting
            val answer = super.hasStreams()
            if (answer && !progress.isCancelled) {// this is hacky :/
                progress.progress = 0.0
                progress.total = durationSeconds * sampleRate
            }
            return answer
        }
    },
    output
)