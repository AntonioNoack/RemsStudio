package me.anno.remsstudio

import me.anno.engine.inspector.Inspectable
import me.anno.language.translation.Dict
import me.anno.language.translation.NameDesc
import me.anno.remsstudio.objects.Transform
import me.anno.ui.Style
import me.anno.ui.base.groups.PanelListY
import me.anno.ui.base.text.TextPanel
import me.anno.ui.editor.SettingCategory
import me.anno.ui.input.NumberType

/**
 * settings UI for beats-per-minute snapping
 * */
object BPMSnapping : Transform() {

    override val defaultDisplayName get() = "Render Settings"

    override fun createInspector(
        inspected: List<Inspectable>, list: PanelListY, style: Style,
        getGroup: (NameDesc) -> SettingCategory
    ) {
        val prefix = "bpmSnapping"
        val project = RemsStudio.project ?: return
        list += TextPanel(
            Dict["Snaps times of keyframes to every nth-repetition of your set BPM.\n" +
                    "Setting 0 disables snapping.", "obj.$prefix.help"], style
        )
        list += vi(
            inspected, "BPM (Beats Per Minute)",
            "Snaps times to multiples of this value. Setting this to zero disables snapping.",
            "$prefix.bpm",
            NumberType.FLOAT_PLUS,
            RemsStudio.timelineSnapping * 60.0,
            style
        ) { it, _ ->
            project.timelineSnapping = it / 60.0
            RenderSettings.save()
        }
        list += vi(
            inspected, "BPM Offset (Seconds)",
            "If your beat doesn't start at zero seconds.",
            "$prefix.offsetSeconds",
            NumberType.FLOAT, RemsStudio.timelineSnappingOffset, style
        ) { it, _ ->
            project.timelineSnappingOffset = it
            RenderSettings.save()
        }
        list += vi(
            inspected, "Snapping Radius (Pixels)",
            "For how many pixels left and right snapping should apply.",
            "$prefix.snappingRadiusPx",
            NumberType.INT_PLUS, RemsStudio.timelineSnappingRadius, style
        ) { it, _ ->
            project.timelineSnappingRadius = it
            RenderSettings.save()
        }
    }
}