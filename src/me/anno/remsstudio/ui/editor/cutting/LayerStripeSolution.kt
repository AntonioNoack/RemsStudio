package me.anno.remsstudio.ui.editor.cutting

import me.anno.Build
import me.anno.animation.LoopingState
import me.anno.audio.streams.AudioStreamRaw.Companion.bufferSize
import me.anno.config.DefaultConfig
import me.anno.gpu.drawing.DrawGradients.drawRectGradient
import me.anno.gpu.drawing.DrawRectangles
import me.anno.gpu.drawing.DrawRectangles.drawRect
import me.anno.gpu.drawing.DrawStriped.drawRectStriped
import me.anno.io.MediaMetadata
import me.anno.io.files.InvalidRef
import me.anno.io.json.saveable.JsonStringWriter
import me.anno.io.saveable.Saveable
import me.anno.maths.Maths.clamp
import me.anno.maths.Maths.max
import me.anno.maths.Maths.posMod
import me.anno.remsstudio.RemsStudio
import me.anno.remsstudio.Selection
import me.anno.remsstudio.audio.AudioFXCache2
import me.anno.remsstudio.audio.AudioFXCache2.SPLITS
import me.anno.remsstudio.objects.Transform
import me.anno.remsstudio.objects.video.Video
import me.anno.remsstudio.objects.video.VideoPreview.getFrameAtLocalTimeForPreview
import me.anno.remsstudio.ui.editor.TimelinePanel.Companion.centralTime
import me.anno.remsstudio.ui.editor.TimelinePanel.Companion.dtHalfLength
import me.anno.remsstudio.ui.editor.cutting.FrameStatus.Companion.drawLoadingStatus
import me.anno.remsstudio.ui.editor.cutting.LayerView.Companion.maxLines
import me.anno.ui.UIColors
import me.anno.utils.Color.black
import me.anno.utils.Color.mixARGB
import me.anno.utils.Color.withAlpha
import me.anno.utils.pooling.JomlPools
import kotlin.math.*

class LayerStripeSolution(
    val x0: Int, var y0: Int, val x1: Int, var y1: Int,
    private val referenceTime: Double
) {
    companion object {

        private val stripeStride = 5
        private val relativeVideoBorder = 0.1
        private val stripeColorSelected = 0x33ffffff
        private val stripeColorError = 0xffff7777.toInt()

        private val frameStatusSize = DefaultConfig["debug.ui.layerView.showFrameStatus", if (Build.isDebug) 4 else 0]

        private val strBuilder = JsonStringWriter(16, InvalidRef)
        private fun toString(saveable: Saveable): String {
            synchronized(strBuilder) {
                strBuilder.clear()
                strBuilder.add(saveable)
                strBuilder.writeAllInList()
                return strBuilder.toString()
            }
        }
    }

    val timeScale = max(x1 - x0, 1) / (dtHalfLength * 2)

    val lines = Array(maxLines) {
        ArrayList<LayerViewGradient>((x1 - x0).shr(1))
    }

    // draw a stripe of the current image, or a symbol or sth...
    // done shader for the stripes (less draw calls)
    // if video, draw a few frames in small
    // if audio, draw audio levels


    fun draw(selectedTransform: List<Transform>, draggedTransform: Transform?) {

        val y = y0
        val h = y1 - y0

        val xTimeCorrection = ((referenceTime - centralTime) * timeScale).roundToInt()
        val timeOffset = -centralTime * timeScale

        val toStringCache = HashMap<Transform, String>()
        for (lineIndex in lines.indices) {
            val gradients = lines[lineIndex]

            val y0 = y + 3 + lineIndex * 3
            val h0 = h - 10

            for (j in gradients.indices) {
                val gradient = gradients[j]
                drawGradient(
                    gradient, selectedTransform, draggedTransform,
                    xTimeCorrection, h, y0, h0, timeOffset, toStringCache
                )
            }
        }
    }

    private fun drawGradient(
        gradient: LayerViewGradient,
        selectedTransform: List<Transform>, draggedTransform: Transform?,
        xTimeCorrection: Int, h: Int, y0: Int, h0: Int, timeOffset: Double, toStringCache: HashMap<Transform, String>
    ) {

        val transform = gradient.owner as? Transform
        val isStriped = selectedTransform === transform || draggedTransform === transform

        val video = transform as? Video
        val meta = video?.meta

        val hasAudio = meta?.hasAudio ?: false
        val hasVideo = meta?.hasVideo ?: false

        val c0 = gradient.c0
        val c1 = gradient.c1

        // bind gradients to edge, because often the stripe continues
        val ix0 = if (gradient.x0 == x0) x0 else gradient.x0 + xTimeCorrection
        val ix1 = if (gradient.x1 + 1 >= x1) x1 else gradient.x1 + xTimeCorrection + 1

        if (hasVideo) {
            drawVideo(
                video, meta, gradient,
                h, y0, h0, timeOffset, ix0, ix1, c0, c1
            )
        } else {
            drawRectGradient(ix0, y0, ix1 - ix0, h, c0, c1)
        }

        if (hasAudio) {
            drawAudio(video, hasVideo, h, ix0, ix1, toStringCache)
        }

        val hasError = transform?.lastWarning != null
        if (isStriped || hasError) {
            // check if the video element has an error
            // if so, add red stripes
            val color = if (hasError) stripeColorError else stripeColorSelected
            drawRectStriped(ix0, y0, ix1 - ix0, h0, timeOffset.toInt(), stripeStride, color)
        }

        if (transform in Selection.selectedTransforms) {
            val selectColor = UIColors.greenYellow.withAlpha(90)
            val h1 = if (hasVideo) h0 else h - 3
            drawRect(ix0, y0, ix1 - ix0, h1, selectColor)
        }
    }

    private fun drawVideo(
        video: Video, meta: MediaMetadata, gradient: LayerViewGradient,
        h: Int, y0: Int, h0: Int, timeOffset: Double, ix0: Int, ix1: Int, c0: Int, c1: Int
    ) {

        val frameWidth = (h * (1.0 + relativeVideoBorder) * meta.videoWidth / meta.videoHeight)

        val frameOffset = posMod(timeOffset, frameWidth)

        val frameIndex0 = floor((ix0 - frameOffset).toFloat() / frameWidth).toInt()
        val frameIndex1 = floor((ix1 - frameOffset).toFloat() / frameWidth).toInt()

        fun getFraction(x: Int, allow0: Boolean): Double {
            val lx = posMod((x + frameWidth - frameOffset), frameWidth)
            if (lx == 0.0 && !allow0) return 1.0
            return lx / frameWidth
        }

        fun getLerpedColor(x: Double) =
            mixARGB(c0, c1, (x - ix0).toFloat() / gradient.w)

        if (frameIndex0 != frameIndex1) {
            // split into multiple frames

            // middle frames
            for (frameIndex in frameIndex0 + 1 until frameIndex1) {
                val x0 = frameWidth * frameIndex + frameOffset
                val x1 = x0 + frameWidth
                val c0x = getLerpedColor(x0)
                val c1x = getLerpedColor(x1)
                drawVideoImpl(
                    x0.toInt(), x1.toInt(), y0, h0, c0x, c1x,
                    frameOffset, frameWidth, video, meta, 0f, 1f
                )
            }

            // first frame
            val x1 = (frameIndex0 + 1) * frameWidth + frameOffset
            if (x1 > ix0) {
                val f0 = getFraction(ix0, true)
                val lerpedC1 = getLerpedColor(x1 - 1)
                drawVideoImpl(
                    ix0, x1.toInt(), y0, h0, c0, lerpedC1,
                    frameOffset, frameWidth, video, meta, f0.toFloat(), 1f
                )
            }

            // last frame
            val x0 = frameIndex1 * frameWidth + frameOffset
            if (x0 < ix1) {
                val lerpedC0 = getLerpedColor(x0)
                val f1 = getFraction(ix1, false)
                drawVideoImpl(
                    x0.toInt(), ix1, y0, h0, lerpedC0, c1,
                    frameOffset, frameWidth, video, meta, 0f, f1.toFloat()
                )
            }

        } else {
            val f0 = getFraction(ix0, true)
            val f1 = getFraction(ix1, false)
            drawVideoImpl(
                ix0, ix1, y0, h0, c0, c1,
                frameOffset, frameWidth, video, meta, f0.toFloat(), f1.toFloat()
            )
        }
    }

    private fun drawAudio(
        audio: Video, hasVideo: Boolean,
        h: Int, ix0: Int, ix1: Int, toStringCache: HashMap<Transform, String>,
    ) {

        // todo get auto levels for pixels, which equal ranges of audio frames -> min, max, avg?, with weight?
        val identifier = toStringCache.getOrPut(audio) { toString(audio) }

        val camera = RemsStudio.currentCamera

        val color = if (hasVideo) 0xaa777777.toInt() else 0xff777777.toInt()
        val mix = 0.5f
        val fineColor = mixARGB(color, 0x77ff77, mix) or black
        val okColor = mixARGB(color, 0xffff77, mix) or black
        val criticalColor = mixARGB(color, 0xff7777, mix) or black

        val offset = (if (hasVideo) 0.75f else 0.5f) * h
        val scale = if (hasVideo) h / 128e3f else h / 65e3f

        val tStart = getTimeAt(ix0)
        val dt = getTimeAt(ix0 + SPLITS) - tStart

        val timeStartIndex = floor(tStart / dt).toLong()
        val timeEndIndex = ceil(getTimeAt(ix1) / dt).toLong()

        val b = DrawRectangles.startBatch()
        for (timeIndex in timeStartIndex until timeEndIndex) {

            val t0 = timeIndex * dt
            val t1 = (timeIndex + 1) * dt
            val xi = getXAt(t0).roundToInt()

            // get min, max, avg, of audio at this time point
            // time0: Time,
            // time1: Time,
            // speed: Double,
            // domain: Domain,
            // async: Boolean
            val range = AudioFXCache2.getRange(bufferSize, t0, t1, identifier, audio, camera)
                ?: continue
            drawAudioBuffer(ix0, ix1, xi, range, fineColor, okColor, criticalColor, scale, offset)
        }
        DrawRectangles.finishBatch(b)
    }

    private fun drawAudioBuffer(
        ix0: Int, ix1: Int, xi: Int,
        range: ShortArray,
        fineColor: Int, okColor: Int, criticalColor: Int,
        scale: Float, offset: Float
    ) {
        // todo get auto levels for pixels, which equal ranges of audio frames -> min, max, avg?, with weight?
        for (dx in 0 until SPLITS) {
            val x = xi + dx
            if (x !in ix0 until ix1) continue
            val minV = range[dx * 2 + 0]
            val maxV = range[dx * 2 + 1]
            val amplitude = max(abs(minV.toInt()), abs(maxV.toInt()))
            val colorMask = when {
                amplitude < 5000 -> black
                amplitude < 28000 -> fineColor
                amplitude < 32000 -> okColor
                else -> criticalColor
            }
            val min = minV * scale + offset
            val max = maxV * scale + offset
            if (max < min) continue
            val y01 = this.y0 + min.toInt()
            val y11 = this.y0 + max.toInt()
            drawRect(x, y01, 1, y11 + 1 - y01, colorMask)
        }
    }

    private val middleX: Double get() = (x0 + x1) * 0.5

    private fun getTimeAt(x: Int): Double = getTimeAt(x.toDouble())
    private fun getTimeAt(x: Double): Double = centralTime + (x - middleX) / timeScale
    private fun getXAt(time: Double): Double = (time - centralTime) * timeScale + middleX

    private fun clampTime(localTime: Double, video: Video): Double {
        return if (video.isLooping.value == LoopingState.PLAY_ONCE) clamp(localTime, 0.0, video.lastDuration)
        else localTime
    }

    private fun getCenterX(x0: Int, frameOffset: Double, frameWidth: Double) =
        x0 - posMod(x0 - frameOffset, frameWidth) + frameWidth * 0.5

    private fun drawVideoImpl(
        x0: Int, x1: Int, y: Int, h: Int,
        c0: Int, c1: Int,
        frameOffset: Double, frameWidth: Double,
        video: Video, meta: MediaMetadata,
        fract0: Float, fract1: Float
    ) {
        val f0 = fract0 * (1.0 + relativeVideoBorder) - relativeVideoBorder * 0.5
        val f1 = fract1 * (1.0 + relativeVideoBorder) - relativeVideoBorder * 0.5
        if (f1 <= 0f || f0 >= 1f) {
            drawRectGradient(x0, y, x1 - x0, h, c0, c1)
        } else {
            // get time at frameIndex
            val centerX = getCenterX(x0, frameOffset, frameWidth)
            val timeAtX = getTimeAt(centerX)
            val localTime = clampTime(video.getLocalTimeFromRoot(timeAtX, false), video)
            // get frame at time
            val videoWidth = (frameWidth / (1.0 + relativeVideoBorder)).toInt()
            val frame = video.getFrameAtLocalTimeForPreview(localTime, videoWidth, meta)
            if (frame == null || !frame.isCreated) {
                drawRectGradient(x0, y, x1 - x0, h, c0, c1)
            } else {
                // draw frame
                drawRectGradient(
                    x0, y, x1 - x0, h, c0, c1, frame,
                    JomlPools.vec4f.borrow().set(f0.toFloat(), 0f, f1.toFloat(), 1f)
                )
            }
        }

        val size = frameStatusSize
        if (size <= 0) return
        val fps = min(video.editorVideoFPS.value.toDouble(), meta.videoFPS)
        drawLoadingStatus(x0, y + h - size, x1, y + h, fps, meta, video) { x ->
            val timeAtX = getTimeAt(x)
            clampTime(video.getLocalTimeFromRoot(timeAtX, false), video)
        }
    }

}