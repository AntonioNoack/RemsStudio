package me.anno.remsstudio.audio.pattern

import me.anno.Time
import me.anno.config.DefaultConfig.style
import me.anno.gpu.drawing.DrawTexts.drawSimpleTextCharByChar
import me.anno.gpu.drawing.GFXx2D.drawCircle
import me.anno.input.Input
import me.anno.input.Key
import me.anno.language.translation.NameDesc
import me.anno.maths.Maths.pow
import me.anno.maths.Maths.unmix
import me.anno.remsstudio.RemsStudio
import me.anno.ui.Panel
import me.anno.ui.base.components.AxisAlignment
import me.anno.ui.base.groups.PanelListY
import me.anno.ui.base.menu.Menu.openMenu
import me.anno.ui.base.menu.MenuOption
import me.anno.ui.input.TextInput
import me.anno.utils.Color.black
import me.anno.utils.Color.mixARGB
import me.anno.utils.types.Booleans.toInt
import kotlin.math.min

/**
 * record/define musical pattern
 *  - then extract high points of an audio signal/allow markers
 *  - and then cut or synchronize (speed-change) the points :3
 * https://www.youtube.com/watch?v=sff_2WMOLrs&t=1212s
 * */
@Suppress("MemberVisibilityCanBePrivate")
class PatternRecorderCore(val tp: TextInput) : Panel(style) {

    val times = ArrayList<Double>()

    var level = 0f
    var isRecording = false

    private var changeListener: ((DoubleArray) -> Unit)? = null

    override fun onUpdate() {
        super.onUpdate()
        level *= pow(0.1f, Time.deltaTime.toFloat())
        if (level > 0.01f) invalidateDrawing()
    }

    override fun calculateSize(w: Int, h: Int) {
        super.calculateSize(w, h)
        val s = (tp.textSize * 4f).toInt()
        minW = s
        minH = s
    }

    override fun onDraw(x0: Int, y0: Int, x1: Int, y1: Int) {
        backgroundColor = black
        super.onDraw(x0, y0, x1, y1)
        // draw record button
        val s = min(width, height)
        val xc = x + width / 2
        val yc = y + height / 2
        val r = s * 0.5f
        val q = r * 0.8f
        val mainColor = if (isRecording) 0xff0000 or black else -1
        val color = mixARGB(mainColor, backgroundColor, 0.5f * level)
        val bg = backgroundColor and 0xffffff
        drawCircle(xc, yc, r, r, 0.95f, backgroundColor, color, backgroundColor)
        drawCircle(xc, yc, q, q, 0.00f, color, color, bg)
        // draw number of samples in center
        drawSimpleTextCharByChar(xc, yc, 1, times.size.toString(), AxisAlignment.CENTER, AxisAlignment.CENTER)
        // todo draw melody somehow :), maybe like FrameTimes
    }

    private fun callAction() {
        openMenu(
            windowStack, NameDesc("Recording"), listOf(
                MenuOption(
                    NameDesc(
                        "Record", "Deletes all samples, and re-records; press any key when the time is right",
                        "ui.patternRecorder.start"
                    )
                ) {
                    times.clear()
                    onTimesChange()
                    ensurePlaying()
                    isRecording = true
                    invalidateDrawing()
                    requestFocus()
                },
                MenuOption(
                    NameDesc(
                        "Stop Recording", "You can press ESC as well",
                        "ui.patternRecorder.stop"
                    )
                ) {
                    isRecording = false
                    invalidateDrawing()
                    RemsStudio.editorTimeDilation = 0.0 // pause :)
                }
            )
        )
    }

    override fun onEnterKey(x: Float, y: Float) {
        callAction()
    }

    override fun onEscapeKey(x: Float, y: Float) {
        isRecording = false
    }

    private fun ensurePlaying() {
        // start playback :)
        if (RemsStudio.editorTimeDilation == 0.0) {
            RemsStudio.editorTimeDilation = 1.0
        }
    }

    private fun onTimesChange() {
        tp.base.setText(times.joinToString(), false)
        changeListener?.invoke(times.toDoubleArray())
    }

    override fun onKeyDown(x: Float, y: Float, key: Key) {
        if (key == Key.BUTTON_LEFT) callAction()
        else if (isRecording && key != Key.KEY_ESCAPE && key != Key.KEY_ENTER && !Input.isShiftDown && !Input.isControlDown) {
            times.add(RemsStudio.editorTime)
            onTimesChange()
            ensurePlaying()
            level = 1f
            invalidateDrawing()
        } else super.onKeyDown(x, y, key)
    }

    companion object {
        fun create(v0: DoubleArray? = null, changeListener: ((DoubleArray) -> Unit)? = null): Panel {
            val l = PanelListY(style)
            val e = TextInput(style)
            val p = PatternRecorderCore(e)
            e.setEnterListener { txt ->
                p.times.clear()
                p.times.addAll(txt.split(',').mapNotNull { it.toDoubleOrNull() }.sorted())
                p.onTimesChange()
            }
            if (v0 != null) {
                p.times.addAll(v0.toList())
                p.onTimesChange()
            }
            p.changeListener = changeListener
            l.add(p)
            l.add(e)
            return l
        }

        fun mapTime(rhythm: DoubleArray, timestamps: DoubleArray, t: Double): Double {
            val s = min(rhythm.size, timestamps.size)
            if (s <= 0) return t
            if (s == 1) return timestamps[0] - rhythm[0] + t
            var idx = rhythm.binarySearch(t, 0, s)
            if (idx < 0) idx = -2 - idx
            else idx--
            if (idx < 0) return timestamps[0] - rhythm[0] + t
            val sm1 = s - 1
            if (idx >= sm1) return timestamps[sm1] - rhythm[sm1] + t
            val t0 = rhythm[idx]
            val t1 = rhythm[idx + 1]
            val relTime = unmix(t0, t1, t)
            val isRightSide = relTime >= 0.5f && idx + 1 < s
            val dstTime = timestamps[idx + isRightSide.toInt()]
            val localTime = t - if (isRightSide) t1 else t0
            return dstTime + localTime
        }
    }
}