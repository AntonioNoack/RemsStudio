package me.anno.remsstudio.video

import me.anno.cache.ThreadPool
import me.anno.gpu.Blitting
import me.anno.gpu.FinalRendering
import me.anno.gpu.GFX
import me.anno.gpu.GFXState
import me.anno.gpu.GFXState.alwaysDepthMode
import me.anno.gpu.GPUTasks.addGPUTask
import me.anno.gpu.GPUTasks.addNextGPUTask
import me.anno.gpu.blending.BlendMode
import me.anno.gpu.framebuffer.*
import me.anno.gpu.shader.renderer.Renderer
import me.anno.gpu.texture.Texture2D
import me.anno.image.raw.ByteImage
import me.anno.image.raw.ByteImageFormat
import me.anno.io.files.FileReference
import me.anno.utils.pooling.ByteBufferPool
import me.anno.video.VideoBackgroundTask.Companion.missingResource
import org.apache.logging.log4j.LogManager
import org.lwjgl.opengl.GL46C

abstract class FrameTask(
    val width: Int,
    val height: Int,
    private val fps: Double,
    private val motionBlurSteps: Int,
    private val shutterPercentage: Float,
    private val time: Double,
    private val dst: FileReference
) {

    var isCancelled = false

    abstract fun renderScene(
        time: Double,
        flipY: Boolean, renderer: Renderer
    )

    private val partialFrame = Framebuffer(
        "VideoBackgroundTask-partial", width, height, 1, TargetType.Float32x4, DepthBufferType.TEXTURE
    )

    private val averageFrame = Framebuffer(
        "VideoBackgroundTask-sum", width, height, 1, TargetType.Float32x4, DepthBufferType.TEXTURE
    )

    fun start(callback: () -> Unit) {
        addGPUTask("FrameTask", width, height) {
            start1(callback)
        }
    }

    private fun start1(callback: () -> Unit) {
        if (isCancelled) {
            callback()
            return
        }
        if (renderFrame(time)) {
            writeFrame(averageFrame)
            destroy()
            callback()
        } else {
            // waiting
            addNextGPUTask("FrameTask::start", 1) {
                start1(callback)
            }
        }
    }

    fun writeFrame(frame: Framebuffer) {

        val pixelByteCount = 3 * width * height
        val pixels = ByteBufferPool.allocateDirect(pixelByteCount)

        GFX.check()

        frame.bindDirectly()
        Frame.invalidate()

        // val t0 = Clock()
        pixels.position(0)
        Texture2D.setReadAlignment(width)
        GL46C.glReadPixels(0, 0, width, height, GL46C.GL_RGB, GL46C.GL_UNSIGNED_BYTE, pixels)
        pixels.position(0)
        // t0.stop("read pixels"), 0.03s on RX 580, 1080p

        GFX.check()

        ThreadPool.start("FrameTask::writeFrame") {// offload to other thread
            // val c1 = Clock()
            val image = ByteImage(width, height, ByteImageFormat.RGB)
            pixels.get(image.data, 0, image.data.size)
            // c1.stop("wrote to buffered image"), 0.025s on R5 2600, 1080p
            if (dst.exists) dst.delete()
            image.write(dst)
            // c1.stop("saved to file"), 0.07s on NVME SSD
            LOGGER.info("Wrote frame to $dst")
            ByteBufferPool.free(pixels)
        }
    }

    private fun renderFrame(time: Double): Boolean {

        GFX.check()

        // is this correct??? mmh...
        val renderer = Renderer.colorRenderer

        var needsMoreSources = false

        if (motionBlurSteps < 2 || shutterPercentage <= 1e-3f) {
            GFXState.useFrame(0, 0, width, height, averageFrame) {
                val missing = FinalRendering.runFinalRendering {
                    renderScene(time, true, renderer)
                }
                if (missing != null) {
                    // e.printStackTrace()
                    missingResource = missing
                    needsMoreSources = true
                }
            }
        } else {
            GFXState.useFrame(averageFrame) {

                averageFrame.clearColor(0)

                var i = 0
                while (i++ < motionBlurSteps && !needsMoreSources) {
                    FBStack.reset(width, height)
                    GFXState.useFrame(partialFrame, renderer) {
                        val timeI = time + (i - motionBlurSteps / 2f) * shutterPercentage / (fps * motionBlurSteps)
                        val missing = FinalRendering.runFinalRendering {
                            renderScene(timeI, true, renderer)
                        }
                        if (missing != null) {
                            // e.printStackTrace()
                            missingResource = missing
                            needsMoreSources = true
                        }
                    }
                    if (!needsMoreSources) {
                        partialFrame.bindTrulyNearest(0)
                        GFXState.blendMode.use(BlendMode.ADD) {
                            GFXState.depthMode.use(alwaysDepthMode) {
                                // write with alpha 1/motionBlurSteps
                                Blitting.copyColorWithSpecificAlpha(1f / motionBlurSteps, 1, true)
                            }
                        }
                    }
                }
            }
        }

        if (needsMoreSources) return false

        GFX.check()

        return true
    }

    fun destroy() {
        addGPUTask("FrameTask.destroy()", width, height) {
            partialFrame.destroy()
            averageFrame.destroy()
        }
    }

    companion object {
        @JvmStatic
        private val LOGGER = LogManager.getLogger(FrameTask::class)
    }
}