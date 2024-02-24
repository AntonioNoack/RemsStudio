package me.anno.remsstudio.ui.editor.cutting

import me.anno.gpu.drawing.DrawRectangles
import me.anno.io.MediaMetadata
import me.anno.io.files.FileReference
import me.anno.remsstudio.objects.Video
import me.anno.utils.Color.black
import me.anno.video.VideoCache
import me.anno.video.VideoFramesKey
import me.anno.video.VideoSlice
import me.anno.video.formats.gpu.BlankFrameDetector

enum class Status(val color: Int) {
    FULL_SCALE_READY(0x24ff2c or black),
    READY(0xbbe961 or black),
    BLANK(0x000000 or black),
    WAIT_FOR_GPU_UPLOAD(0x61e9e3 or black),
    BUFFER_LOADING(0xe9e561 or black),
    DESTROYED(0xca55e7 or black),
    NO_FRAME_NEEDED(0xa09da1 or black),
    MISSING(0x9b634e or black),
    INVALID_INDEX(0xe73838 or black),
    INVALID_SCALE(0xe73845 or black),
    META_LOADING(0x442d24 or black);

    companion object {

        private fun drawStatus(x: Int, y: Int, w: Int, h: Int, status: Status) {
            DrawRectangles.drawRect(x, y, w, h, status.color)
        }

        fun getFrameStatus(
            file: FileReference, scale: Int,
            frameIndex: Int, bufferLength: Int, fps: Double,
            blankFrameThreshold: Float
        ): Status {
            if (frameIndex < 0) return INVALID_INDEX
            if (scale < 1) return INVALID_SCALE
            val bufferIndex = frameIndex / bufferLength
            val localIndex = frameIndex % bufferLength
            val videoData = VideoCache.getEntryWithoutGenerator(
                VideoFramesKey(file, scale, bufferIndex, bufferLength, fps)
            ) as? VideoSlice
                ?: return MISSING
            val frame = videoData.frames.getOrNull(localIndex)
            return when {
                frame == null -> BUFFER_LOADING
                frame.isDestroyed -> DESTROYED
                frame.isCreated -> {
                    if (BlankFrameDetector.isBlankFrame(
                            file, scale, frameIndex, bufferLength,
                            fps, blankFrameThreshold
                        )
                    ) BLANK else READY
                }
                else -> WAIT_FOR_GPU_UPLOAD
            }
        }

        @Suppress("unused")
        fun drawLoadingStatus(
            x0: Int, y0: Int, x1: Int, y1: Int,
            file: FileReference, fps: Double = 0.0,
            blankFrameThreshold: Float,
            timeMapper: (x: Int) -> Double,
            bufferLength: Int = Video.framesPerContainer
        ) {
            val meta = MediaMetadata.getMeta(file, true)
            val maxScale = 32
            if (meta != null) {
                if (meta.videoFrameCount < 2) {
                    var bestStatus = MISSING
                    for (scale in 1 until maxScale) {
                        val status = getFrameStatus(file, scale, 0, 1, 1.0, blankFrameThreshold)
                        if (status.ordinal < bestStatus.ordinal) bestStatus = status
                    }
                    drawStatus(x0, y0, x1 - x0, y1 - y0, bestStatus)
                } else {
                    val fps2 = if (fps > 0.0) fps else meta.videoFPS
                    var lastFrameIndex = -1
                    var lastX = x0
                    var lastStatus = INVALID_SCALE
                    // from left to right query all video data
                    for (xi in x0 until x1) {
                        val time = timeMapper(xi)
                        var bestStatus = MISSING
                        if (time >= 0.0) {
                            val frameIndex = (time * meta.videoFrameCount / meta.videoDuration)
                                .toInt() % meta.videoFrameCount
                            // if frame index is same as previously, don't request again
                            if (frameIndex == lastFrameIndex) {
                                bestStatus = lastStatus
                            } else {
                                lastFrameIndex = frameIndex
                                for (scale in 1 until maxScale) {
                                    var status =
                                        getFrameStatus(file, scale, frameIndex, bufferLength, fps2, blankFrameThreshold)
                                    if (status == READY && scale == 1) status = FULL_SCALE_READY
                                    if (status.ordinal < bestStatus.ordinal) bestStatus = status
                                }
                            }
                        } else bestStatus = NO_FRAME_NEEDED
                        // optimize drawing routine: draw blocks as one
                        if (bestStatus != lastStatus && xi > x0) {
                            // draw previous stripe
                            drawStatus(lastX, y0, xi - lastX, y1 - y0, lastStatus)
                            lastX = xi
                        }
                        lastStatus = bestStatus
                    }
                    // draw last stripe
                    drawStatus(lastX, y0, x1 - lastX, y1 - y0, lastStatus)
                }
            } else drawStatus(x0, y0, x1 - x0, y1 - y0, META_LOADING)
        }
    }
}
