package me.anno.remsstudio

import me.anno.ui.input.NumberType
import me.anno.remsstudio.objects.Transform
import me.anno.engine.inspector.Inspectable
import me.anno.ui.Style
import me.anno.ui.base.groups.PanelListY
import me.anno.ui.base.text.TextPanel
import me.anno.ui.editor.SettingCategory

object BPMSnapping : Transform() {

    override val defaultDisplayName get() = "Render Settings"

    override fun createInspector(
        inspected: List<Inspectable>,
        list: PanelListY,
        style: Style,
        getGroup: (title: String, description: String, dictSubPath: String) -> SettingCategory
    ) {
        val project = RemsStudio.project ?: return
        list += TextPanel(
            "Snaps times of keyframes to every nth-repetition of your set BPM.\n" +
                    "Setting 0 disables snapping.",
            style
        )
        list += vi(
            inspected, "BPM (Beats Per Minute)",
            "Snaps times to multiples of this value. Setting this to zero disables snapping.",
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
            NumberType.FLOAT, RemsStudio.timelineSnappingOffset, style
        ) { it, _ ->
            project.timelineSnappingOffset = it
            RenderSettings.save()
        }
        list += vi(
            inspected, "Snapping Radius (Pixels)",
            "For how many pixels left and right snapping should apply.",
            NumberType.INT_PLUS, RemsStudio.timelineSnappingRadius, style
        ) { it, _ ->
            project.timelineSnappingRadius = it
            RenderSettings.save()
        }
    }
}