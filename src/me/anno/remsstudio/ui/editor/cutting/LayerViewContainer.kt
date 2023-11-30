package me.anno.remsstudio.ui.editor.cutting

import me.anno.remsstudio.RemsStudio
import me.anno.remsstudio.StudioActions.jumpToEnd
import me.anno.remsstudio.StudioActions.jumpToStart
import me.anno.remsstudio.StudioActions.nextFrame
import me.anno.remsstudio.StudioActions.previousFrame
import me.anno.ui.Panel
import me.anno.ui.Style
import me.anno.ui.base.SpacerPanel
import me.anno.ui.base.buttons.TextButton
import me.anno.ui.base.components.AxisAlignment
import me.anno.ui.base.components.Padding
import me.anno.ui.base.groups.PanelListX
import me.anno.ui.base.groups.PanelListY
import me.anno.ui.base.scrolling.ScrollPanelY
import me.anno.utils.Color.mixARGB
import me.anno.utils.structures.lists.Lists.count2

class LayerViewContainer(style: Style) : ScrollPanelY(Padding(0), style) {

    // todo this could be more beautiful, maybe automatically managed or sth like that...
    private val addLayerButton = TextButton("Add Layer", "Adds another layer to drag your sources onto", false, style)
        .addLeftClickListener { addLayer() }

    private val timeControls = PanelListX(style)
    private val bottomButtons = PanelListX(style)

    private val layers = child as PanelListY

    private fun setEditorTimeDilation(speed: Double) {
        RemsStudio.editorTimeDilation = if (RemsStudio.editorTimeDilation != speed) speed else 0.0
        RemsStudio.updateAudio()
    }

    init {
        alignmentX = AxisAlignment.FILL
        fun addButton(name: String, desc: String, action: (Panel) -> Unit) {
            timeControls.add(TextButton(name, 1.5f, style)
                .apply {
                    alignmentX = AxisAlignment.FILL
                    alignmentY = AxisAlignment.FILL
                    tooltip = desc
                    padding.set(1)
                }
                .addLeftClickListener(action))
        }

        val hideButton = TextButton(
            "Hide", "Hide Time Controls; Can be undone at the bottom of this panel",
            false, style
        )
        val showButton = TextButton("Show Time Controls", style)
        val bg = showButton.backgroundColor
        fun space(): Panel {
            return SpacerPanel(1, 1, style).apply {
                backgroundColor = mixARGB(backgroundColor, bg, 0.3f)
                weight = 1f
            }
        }
        timeControls.add(space())
        addButton("|<", "Jump to start (Left Arrow + Control)") { jumpToStart() }
        addButton(".<", "Jump to previous frame (Comma)") { previousFrame() }
        addButton("<<", "Playback 5x speed backwards") { setEditorTimeDilation(-5.0) }
        addButton("<", "Playback backwards (Control + Space)") { setEditorTimeDilation(-1.0) }
        addButton("(", "Playback 0.2x speed backwards (Control + Shift + Space)") { setEditorTimeDilation(-0.2) }
        addButton("||", "Pause (Space)") { setEditorTimeDilation(0.0) }
        addButton(")", "Playback 0.2x speed (Shift + Space)") { setEditorTimeDilation(+0.2) }
        addButton(">", "Playback (Space)") { setEditorTimeDilation(+1.0) }
        addButton(">>", "Playback 5x speed") { setEditorTimeDilation(+5.0) }
        addButton(">.", "Jump to next frame (Dot)") { nextFrame() }
        addButton(">|", "Jump to end (Right Arrow + Control)") { jumpToEnd() }
        timeControls.add(space())
        showButton.isVisible = false
        timeControls.add(hideButton.addLeftClickListener {
            timeControls.isVisible = false
            showButton.isVisible = true
        })
        layers += timeControls
        layers += bottomButtons
        timeControls.alignmentX = AxisAlignment.FILL
        bottomButtons.alignmentX = AxisAlignment.FILL
        addLayerButton.alignmentX = AxisAlignment.FILL
        addLayerButton.weight = 1f
        bottomButtons.add(addLayerButton)
        bottomButtons.add(showButton.addLeftClickListener {
            timeControls.isVisible = true
            showButton.isVisible = false
        })
        for (i in 0 until LayerView.defaultLayerCount) {
            addLayer()
        }
    }

    private fun addLayer() {
        val layerIndex = layers.children.count2 { it is LayerView }
        val v = LayerView(layerIndex, style)
        v.alignmentX = AxisAlignment.FILL
        v.parent = layers
        v.cuttingView = this
        layers.children.add(layers.children.lastIndex, v)
    }

    override val className get() = "LayerViewContainer"

}