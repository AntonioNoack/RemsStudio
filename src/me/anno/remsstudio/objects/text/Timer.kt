package me.anno.remsstudio.objects.text

import me.anno.config.DefaultConfig
import me.anno.language.translation.Dict
import me.anno.maths.Maths.fract
import me.anno.remsstudio.objects.Transform
import me.anno.engine.inspector.Inspectable
import me.anno.language.translation.NameDesc
import me.anno.ui.Style
import me.anno.ui.base.groups.PanelListY
import me.anno.ui.editor.SettingCategory
import me.anno.ui.input.TextInputML
import org.joml.Matrix4fArrayList
import org.joml.Vector4f
import java.util.*
import kotlin.math.floor

class Timer(parent: Transform? = null) : Text("", parent) {

    init {
        forceVariableBuffer = true
    } // saves buffer creation

    override fun getDocumentationURL() = "https://remsstudio.phychi.com/?s=learn/timer"

    var format = "hh:mm:ss.s2"

    override fun onDraw(stack: Matrix4fArrayList, time: Double, color: Vector4f) {

        val fract = fract(time)
        val s0 = floor(time).toLong()
        var s = s0
        var m = s / 60
        var h = m / 60
        var d = h / 24

        s %= 60
        if (s < 0) {
            s += 60
            m--
        }
        m %= 60
        if (m < 0) {
            m += 60
            h--
        }
        h %= 24
        if (h < 0) {
            h += 24
            d--
        }

        text.set(
            format
                .replace("s0", "")
                .replace("s6", "%.6f".format(Locale.ENGLISH, fract).substring(2))
                .replace("s5", "%.5f".format(Locale.ENGLISH, fract).substring(2))
                .replace("s4", "%.4f".format(Locale.ENGLISH, fract).substring(2))
                .replace("s3", "%.3f".format(Locale.ENGLISH, fract).substring(2))
                .replace("s2", "%.2f".format(Locale.ENGLISH, fract).substring(2))
                .replace("s1", "%.1f".format(Locale.ENGLISH, fract).substring(2))
                .replace("ZB", s0.toString(2))
                .replace("ZO", s0.toString(8))
                .replace("ZD", s0.toString(10))
                .replace("ZH", s0.toString(16))
                .replace("Z", s0.toString())
                .replace("ss", s.formatTwoDigits())
                .replace("mm", m.formatTwoDigits())
                .replace("hh", h.formatTwoDigits())
                .replace("dd", d.formatTwoDigits())
        )

        super.onDraw(stack, time, color)

    }

    private fun Long.formatTwoDigits() = if (this < 10) "0$this" else this.toString()

    override fun createInspector(
        inspected: List<Inspectable>, list: PanelListY, style: Style,
        getGroup: (NameDesc) -> SettingCategory
    ) {
        super.createInspector(inspected, list, style, getGroup)
        list.children.removeIf { it is TextInputML && it.base.placeholder == "Text" }
        list += vi(
            inspected, "Format",
            "ss=sec, mm=min, hh=hours, dd=days, s3=millis",
            "timer.format", null, format, style
        ) { it, _ ->
            for (x in inspected) if (x is Timer) x.format = it
        }
    }

    override val className get() = "Timer"
    override val defaultDisplayName get() = Dict["Timer", "obj.timer"]
    override val symbol get() = DefaultConfig["ui.symbol.timer", "\uD83D\uDD51"]
}