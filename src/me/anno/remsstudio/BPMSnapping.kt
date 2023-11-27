package me.anno.remsstudio

import me.anno.animation.Type
import me.anno.remsstudio.objects.Transform
import me.anno.studio.Inspectable
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

        val project = RemsStudio.project!!
        list += TextPanel(
            "Snaps times of keyframes to every nth-repetition of your set BPM.\n" +
                    "Setting 0 disables snapping.",
            style
        )
        list += vi(
            inspected, "BPM (Beats Per Minute)",
            "Snaps times to multiples of this value. Setting this to zero disables snapping.",
            Type.FLOAT_PLUS,
            RemsStudio.timelineSnapping * 60.0,
            style
        ) {
            project.timelineSnapping = it / 60.0
            RenderSettings.save()
        }
        list += vi(
            inspected, "BPM Offset (Seconds)",
            "If your beat doesn't start at zero seconds.",
            Type.FLOAT, RemsStudio.timelineSnappingOffset, style
        ) {
            project.timelineSnappingOffset = it
            RenderSettings.save()
        }
        list += vi(
            inspected, "Snapping Radius (Pixels)",
            "For how many pixels left and right snapping should apply.",
            Type.INT_PLUS, RemsStudio.timelineSnappingRadius, style
        ) {
            project.timelineSnappingRadius = it
            RenderSettings.save()
        }
    }
}