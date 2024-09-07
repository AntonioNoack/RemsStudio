package me.anno.remsstudio.ui

import me.anno.config.DefaultStyle
import me.anno.language.translation.NameDesc
import me.anno.remsstudio.RemsStudio
import me.anno.remsstudio.StudioActions
import me.anno.remsstudio.ui.MenuUtils.drawTypeInCorner
import me.anno.ui.Panel
import me.anno.ui.Style
import me.anno.ui.base.SpacerPanel
import me.anno.ui.base.buttons.TextButton
import me.anno.ui.base.components.AxisAlignment
import me.anno.ui.base.groups.PanelListX
import me.anno.utils.Color

class TimeControlsPanel(style: Style) : PanelListX(style) {

    private fun setEditorTimeDilation(speed: Double) {
        val speed1 = if (RemsStudio.editorTimeDilation == speed) 0.0 else speed
        StudioActions.setEditorTimeDilation(speed1, true)
    }

    init {
        val showButton = TextButton(NameDesc("Show Time Controls"), style)
        val bg = showButton.backgroundColor
        backgroundColor = Color.mixARGB(backgroundColor, bg, 0.3f)
        fun space(): Panel {
            return SpacerPanel(1, 1, style).apply {
                makeBackgroundTransparent()
                weight = 1f
            }
        }

        fun addButton(name: String, desc: String, action: (Panel) -> Unit) {
            add(
                TextButton(NameDesc(name), 1.5f, style)
                    .apply {
                        alignmentX = AxisAlignment.FILL
                        alignmentY = AxisAlignment.FILL
                        tooltip = desc
                        padding.set(1)
                    }
                    .addLeftClickListener(action))
        }

        add(space())
        addButton("|<", "Jump to start (Left Arrow + Control)") { StudioActions.jumpToStart() }
        addButton(".<", "Jump to previous frame (Comma)") { StudioActions.previousFrame() }
        addButton("<<", "Playback 5x speed backwards") { setEditorTimeDilation(-5.0) }
        addButton("<", "Playback backwards (Control + Space)") { setEditorTimeDilation(-1.0) }
        addButton("(", "Playback 0.2x speed backwards (Control + Shift + Space)") { setEditorTimeDilation(-0.2) }
        addButton("||", "Pause (Space)") { setEditorTimeDilation(0.0) }
        addButton(")", "Playback 0.2x speed (Shift + Space)") { setEditorTimeDilation(+0.2) }
        addButton(">", "Playback (Space)") { setEditorTimeDilation(+1.0) }
        addButton(">>", "Playback 5x speed") { setEditorTimeDilation(+5.0) }
        addButton(">.", "Jump to next frame (Dot)") { StudioActions.nextFrame() }
        addButton(">|", "Jump to end (Right Arrow + Control)") { StudioActions.jumpToEnd() }
        add(space())
    }

    override val canDrawOverBorders: Boolean
        get() = true

    private val fontColor = style.getColor("textColor", DefaultStyle.fontGray)
    override fun drawBackground(x0: Int, y0: Int, x1: Int, y1: Int, dx: Int, dy: Int) {
        super.drawBackground(x0, y0, x1, y1, dx, dy)
        drawTypeInCorner("Time Control", fontColor)
    }

    override val className: String
        get() = "TimeControlsPanel"
}