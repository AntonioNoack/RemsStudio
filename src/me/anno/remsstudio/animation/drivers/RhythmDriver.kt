package me.anno.remsstudio.animation.drivers

import me.anno.io.base.BaseWriter
import me.anno.remsstudio.RemsStudio
import me.anno.remsstudio.audio.pattern.PatternRecorderCore
import me.anno.ui.Style
import me.anno.ui.base.groups.PanelListY
import me.anno.ui.base.text.TextPanel
import me.anno.ui.editor.SettingCategory

class RhythmDriver : AnimationDriver() {

    var rhythm: DoubleArray = DoubleArray(0)
    var timestamps: DoubleArray = rhythm

    override fun createInspector(
        list: PanelListY, style: Style,
        getGroup: (title: String, description: String, dictSubPath: String) -> SettingCategory
    ) {
        // todo register changes in history
        super.createInspector(list, style, getGroup)
        list += TextPanel("Rhythm (record while listening to target music)", style)
        list += PatternRecorderCore.create(rhythm) { rhythm = it }
        list += TextPanel("Timestamps (record while watching timelapse)", style)
        list += PatternRecorderCore.create(timestamps) { timestamps = it }
    }

    override fun getValue0(time: Double, keyframeValue: Double, index: Int): Double {
        if (RemsStudio.isSelected(this)) return 0.0
        return PatternRecorderCore.mapTime(rhythm, timestamps, time) - time
    }

    override fun save(writer: BaseWriter) {
        super.save(writer)
        writer.writeDoubleArray("rhythm", rhythm)
        writer.writeDoubleArray("timestamps", timestamps)
    }

    override fun setProperty(name: String, value: Any?) {
        when (name) {
            "rhythm" -> rhythm = value as? DoubleArray ?: return
            "timestamps" -> timestamps = value as? DoubleArray ?: return
            else -> super.setProperty(name, value)
        }
    }

    override fun getDisplayName() = "Rhythm"
    override val className get() = "RhythmDriver"

}