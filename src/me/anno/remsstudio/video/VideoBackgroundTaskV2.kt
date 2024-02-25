package me.anno.remsstudio.video

import me.anno.gpu.shader.renderer.Renderer
import me.anno.remsstudio.Scene
import me.anno.remsstudio.animation.AnimatedProperty
import me.anno.remsstudio.objects.Camera
import me.anno.remsstudio.objects.Transform
import me.anno.ui.base.progress.ProgressBar
import me.anno.video.VideoBackgroundTask
import me.anno.video.VideoCreator
import kotlin.math.min

class VideoBackgroundTaskV2(
    video: VideoCreator,
    samples: Int,
    val scene: Transform,
    val camera: Camera,
    val motionBlurSteps: AnimatedProperty<Int>,
    val shutterPercentage: AnimatedProperty<Float>,
    val progressBar: ProgressBar
) : VideoBackgroundTask(video, samples) {

    override fun getMotionBlurSteps(time: Double): Int {
        return motionBlurSteps[time]
    }

    override fun getShutterPercentage(time: Double): Float {
        return shutterPercentage[time]
    }

    override fun renderScene(time: Double, flipY: Boolean, renderer: Renderer) {
        isCancelled = progressBar.isCancelled
        progressBar.progress = min(time * creator.fps, progressBar.total * 0.999999)
        Scene.draw(
            camera, scene, 0, 0, creator.width, creator.height, time,
            true, renderer, null
        )
    }
}