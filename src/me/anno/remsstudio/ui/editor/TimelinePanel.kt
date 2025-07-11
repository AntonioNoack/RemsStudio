package me.anno.remsstudio.ui.editor

import me.anno.config.DefaultStyle
import me.anno.fonts.FontManager
import me.anno.fonts.FontStats
import me.anno.fonts.keys.TextCacheKey
import me.anno.gpu.GFX
import me.anno.gpu.drawing.DrawRectangles
import me.anno.gpu.drawing.DrawRectangles.drawRect
import me.anno.gpu.drawing.DrawTexts.drawSimpleTextCharByChar
import me.anno.input.Input
import me.anno.input.Key
import me.anno.language.translation.NameDesc
import me.anno.maths.Maths.clamp
import me.anno.maths.Maths.fract
import me.anno.maths.Maths.mix
import me.anno.maths.Maths.pow
import me.anno.remsstudio.RemsStudio
import me.anno.remsstudio.RemsStudio.editorTime
import me.anno.remsstudio.RemsStudio.project
import me.anno.remsstudio.RemsStudio.targetDuration
import me.anno.remsstudio.RemsStudio.targetFPS
import me.anno.remsstudio.RemsStudio.updateAudio
import me.anno.remsstudio.Selection
import me.anno.remsstudio.objects.Transform
import me.anno.remsstudio.ui.graphs.GraphEditorBody.Companion.shouldMove
import me.anno.remsstudio.ui.graphs.GraphEditorBody.Companion.shouldScrub
import me.anno.ui.Panel
import me.anno.ui.Style
import me.anno.ui.base.components.AxisAlignment
import me.anno.ui.base.menu.Menu.openMenu
import me.anno.ui.base.menu.MenuOption
import me.anno.utils.Color.black
import me.anno.utils.Color.mixARGB
import me.anno.utils.Color.mulAlpha
import me.anno.utils.types.Strings.formatTime
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sqrt

// todo subpixel adjusted lines, only if subpixel rendering affects x-axis

@Suppress("MemberVisibilityCanBePrivate")
open class TimelinePanel(style: Style) : Panel(style) {

    var drawnStrings = ArrayList<TextCacheKey>(64)

    val accentColor = style.getColor("accentColor", black)

    override fun draw(x0: Int, y0: Int, x1: Int, y1: Int) {
        drawnStrings.clear()
        drawBackground(x0, y0, x1, y1)
        drawTimeAxis(x0, y0, x1, y1, true)
    }

    val font = style.getFont("tinyText")
    val fontColor = style.getColor("textColor", DefaultStyle.fontGray)
    val endColor = style.getColor("endColor", mixARGB(fontColor, 0xff0000 or black, 0.5f))

    fun drawCurrentTime() {
        GFX.loadTexturesSync.push(true)
        val text = getTimeString(editorTime, 0.0)
        val color = mixARGB(fontColor, background.color, 0.8f)
        drawSimpleTextCharByChar(x + width / 2, y + height / 2, 0, text, color, background.color, AxisAlignment.CENTER)
        GFX.loadTexturesSync.pop()
    }

    override fun onPropertiesChanged() {
    }

    companion object {

        var centralValue = 0.0
        var dvHalfHeight = 1.0

        var dtHalfLength = 30.0
        var centralTime = dtHalfLength

        val timeFractions = listOf(
            0.2f, 0.5f,
            1f, 2f, 5f, 10f, 20f, 30f, 60f,
            120f, 300f, 600f, 1200f, 1800f, 3600f,
            3600f * 1.5f, 3600f * 2f, 3600f * 5f,
            3600f * 6f, 3600f * 12f, 3600f * 24f
        )

        var lastOwner: Transform = RemsStudio.root

        fun updateLocalTime() {

            lastOwner = Selection.selectedTransforms.firstOrNull() ?: lastOwner

            val owner = lastOwner

            // only simple time transforms are supported
            time0 = 0.0
            time1 = 1.0

            for (t in owner.listOfInheritanceReversed) {
                // localTime0 = (parentTime - timeOffset) * timeDilation
                val offset = t.timeOffset.value
                val dilation = t.timeDilation.value
                time0 = (time0 - offset) * dilation
                time1 = (time1 - offset) * dilation
            }

            // make sure the values are ok-ish
            if (abs(time1 - time0) !in 0.001..1000.0) {
                time0 = 0.0
                time1 = 1.0
            }

        }

        var time0 = 0.0
        var time1 = 1.0

        fun kf2Global(t: Double) = (t - time0) / (time1 - time0)
        fun global2Kf(t: Double) = mix(time0, time1, t)

        fun clampTime() {
            dtHalfLength = clamp(dtHalfLength, 2.0 / targetFPS, timeFractions.last().toDouble())
            // centralTime = max(centralTime, dtHalfLength)
        }

        val movementSpeed get() = 0.05f * sqrt(GFX.someWindow.width * GFX.someWindow.height.toFloat())

        /**
         * when changing a property, what should be the dt for snapping
         * */
        val keyframeSnappingDt
            get(): Double {
                return if (RemsStudio.editorTimeDilation == 0.0) 10f * dtHalfLength / GFX.someWindow.width
                else 1e-6
            }

        fun moveRight(sign: Float): Boolean {
            val delta = sign * dtHalfLength * 0.05f
            editorTime += delta
            updateAudio()
            centralTime += delta
            clampTime()
            return true
        }

        data class TimestampKey(val time: Double, val step: Double)

        val timestampCache = HashMap<TimestampKey, String>()


        fun get0XString(time: Int) = if (time < 10) "0$time" else "$time"
        fun get00XString(time: Int) = if (time < 10) "0$time" else if (time < 100) "00$time" else "$time"

        fun getTimeString(time: Double, step: Double): String {
            val key = TimestampKey(time, step)
            val old = timestampCache[key]
            if (old != null) return old
            if (timestampCache.size > 500) timestampCache.clear()
            val solution =
                if (time < 0) "-${getTimeString(-time, step)}"
                else {
                    val s = time.toInt()
                    val m = s / 60
                    val h = m / 60
                    val subTime = ((time % 1) * targetFPS).roundToInt()
                    if (h < 1) "${get0XString(m % 60)}:${get0XString(s % 60)}${if (step < 1f) "/${get0XString(subTime)}" else ""}"
                    else "${get0XString(h)}:${get0XString(m % 60)}:${get0XString(s % 60)}${
                        if (step < 1f) "/${
                            get0XString(
                                subTime
                            )
                        }" else ""
                    }"
                }
            timestampCache[key] = solution
            return solution
        }

        fun normTime01(time: Double) = (time - centralTime) / dtHalfLength * 0.5 + 0.5
        fun normAxis11(lx: Float, x0: Int, size: Int) = (lx - x0) / size * 2f - 1f
    }

    fun getTimeAt(mx: Float) = centralTime + dtHalfLength * normAxis11(mx, x, width)
    fun getXAt(time: Double) = x + width * normTime01(time)

    fun drawTimeAxis(x0: Int, y0: Int, x1: Int, y1: Int, drawText: Boolean) {

        val y02 = if (drawText) y0 else y0 - (2 + font.sizeInt)

        // make the step amount dependent on width and font size
        val deltaFrame = 500 * dtHalfLength * font.size / width

        val timeStep = getTimeStep(deltaFrame * 0.2)

        val strongLineColor = fontColor and 0x4fffffff
        val fineLineColor = fontColor and 0x1fffffff
        val veryFineLineColor = fontColor and 0x10ffffff

        val batch = DrawRectangles.startBatch()

        // very fine lines, 20x as many
        drawTimeAxis(timeStep * 0.05, x0, y02, x1, y1, veryFineLineColor, false)

        // fine lines, 5x as many
        drawTimeAxis(timeStep * 0.2, x0, y02, x1, y1, fineLineColor, drawText)

        // strong lines
        drawTimeAxis(timeStep, x0, y02, x1, y1, strongLineColor, false)

        drawLine(targetDuration, y02, y1, endColor)
        drawLine(editorTime, y02, y1, accentColor)

        DrawRectangles.finishBatch(batch)

    }

    override fun onUpdate() {
        super.onUpdate()
        for (key in drawnStrings) {
            FontManager.getTexture(key)
        }
    }

    fun drawTimeAxis(
        timeStep: Double, x0: Int, y0: Int, x1: Int, y1: Int,
        lineColor: Int, drawText: Boolean
    ) {

        val minFrame = centralTime - dtHalfLength
        val maxFrame = centralTime + dtHalfLength

        val minStepIndex = (minFrame / timeStep).toLong() - 1
        val maxStepIndex = (maxFrame / timeStep).toLong() + 1

        val fontSize = font.sizeInt
        val fontColor = fontColor
        val backgroundColor = background.color and black.inv()

        val lineY = y0 + 2 + fontSize
        val lineH = y1 - y0 - 4 - fontSize

        // splitting this results in 30% less time used
        // probably because of program switching
        // 8% more are gained by assigning the color only once
        if (lineH > 0) {
            for (stepIndex in maxStepIndex downTo minStepIndex) {
                val time = stepIndex * timeStep
                val x = getXAt(time).roundToInt()
                if (x > x0 + 1 && x + 2 < x1) {
                    drawRect(x, lineY, 1, lineH, lineColor)
                }
            }
        }

        if (drawText) {
            for (stepIndex in maxStepIndex downTo minStepIndex) {
                val time = stepIndex * timeStep
                val x = getXAt(time).roundToInt()
                val text = getTimeString(time, timeStep)
                drawSimpleTextCharByChar(
                    x, y0, 2, text, fontColor, backgroundColor, AxisAlignment.CENTER
                )
            }
        }

    }

    fun drawLine(time: Double, y0: Int, y1: Int, color: Int) {
        if (!time.isFinite()) return
        // if there are sub-pixels, we could use those...
        val x = getXAt(time).toFloat()
        val xFloor = floor(x)
        val x0 = xFloor.toInt()
        val alpha1 = x - xFloor
        val alpha0 = 1f - alpha1
        // simple interpolation
        // it looks way better than without (it looks a little lagging without)
        drawRect(x0 + 0, y0 + 2, 1, y1 - y0 - 4, color.mulAlpha(alpha0))
        drawRect(x0 + 1, y0 + 2, 1, y1 - y0 - 4, color.mulAlpha(alpha1))
    }

    fun getTimeStep(time: Double): Double {
        return timeFractions.minByOrNull { abs(it - time) }!!.toDouble()
    }

    fun getCrossSize(style: Style): Int {
        val fontSize = style.getSize("text.fontSize", FontStats.getDefaultFontSize())
        return style.getSize("customizable.crossSize", fontSize)
    }

    fun isCursorOnCross(x: Float, y: Float, crossSize: Int = getCrossSize(style)): Boolean {
        val crossSize1 = crossSize + 4f // +4f for 2*padding
        return x - (this.x + width - crossSize1) in 0f..crossSize1 && y - this.y in 0f..crossSize1
    }

    override fun onMouseClicked(x: Float, y: Float, button: Key, long: Boolean) {
        when {
            isCursorOnCross(x, y) -> super.onMouseClicked(x, y, button, long)
            button == Key.BUTTON_LEFT -> jumpToX(x)
            else -> {
                val options = listOf(
                    MenuOption(
                        NameDesc(
                            "Set End Here",
                            "Sets the end of the project to here",
                            "ui.timePanel.setEndHere"
                        )
                    ) {
                        project?.targetDuration = getTimeAt(x)
                    },
                    MenuOption(NameDesc("Jump to Start", "Set the time to 0", "ui.timePanel.jumpToStart")) {
                        jumpToT(0.0)
                    },
                    MenuOption(
                        NameDesc(
                            "Jump to End",
                            "Set the time to the end of the project",
                            "ui.timePanel.jumpToEnd"
                        )
                    ) {
                        jumpToT(targetDuration)
                    }
                )
                openMenu(windowStack, options)
            }
        }
    }

    fun jumpToX(x: Float) = jumpToT(getTimeAt(x))
    fun jumpToT(t: Double) {
        RemsStudio.largeChange("Timeline jump to ${t.formatTime()}/${(fract(t) * targetFPS).toInt()}") {
            editorTime = t
        }
        updateAudio()
    }

    override fun onMouseMoved(x: Float, y: Float, dx: Float, dy: Float) {
        if (shouldScrub()) {
            // scrubbing
            editorTime = getTimeAt(x)
        } else if (shouldMove()) {
            // move left/right
            val dt = dx * dtHalfLength / (width / 2f)
            centralTime -= dt
            clampTime()
        }
    }

    override fun onMouseWheel(x: Float, y: Float, dx: Float, dy: Float, byMouse: Boolean) {
        if (Input.isControlDown) { // hack to allow scrolling the parent
            super.onMouseWheel(x, y, dy, -dx, byMouse)
        } else {
            val scale = pow(1.05f, dx)
            // set the center to the cursor
            // works great :D
            val normalizedX = (x - width / 2f) / (width / 2f)
            centralTime += normalizedX * dtHalfLength * (1f - scale)
            dtHalfLength *= scale
            centralTime += dtHalfLength * 20f * dy / width
            clampTime()
        }
    }

    override val className get() = "TimelinePanel"

}