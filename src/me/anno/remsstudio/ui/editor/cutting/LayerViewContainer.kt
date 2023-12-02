package me.anno.remsstudio.ui.editor.cutting

import me.anno.ui.Style
import me.anno.ui.base.buttons.TextButton
import me.anno.ui.base.components.AxisAlignment
import me.anno.ui.base.components.Padding
import me.anno.ui.base.groups.PanelListX
import me.anno.ui.base.groups.PanelListY
import me.anno.ui.base.scrolling.ScrollPanelY
import me.anno.utils.structures.lists.Lists.count2

class LayerViewContainer(style: Style) : ScrollPanelY(Padding(0), style) {

    // todo this could be more beautiful, maybe automatically managed or sth like that...
    private val addLayerButton = TextButton("Add Layer", "Adds another layer to drag your sources onto", false, style)
        .addLeftClickListener { addLayer() }

    private val layers = child as PanelListY

    init {
        alignmentX = AxisAlignment.FILL
        layers += addLayerButton
        addLayerButton.alignmentX = AxisAlignment.FILL
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