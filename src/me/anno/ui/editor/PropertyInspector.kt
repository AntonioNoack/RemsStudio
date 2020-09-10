package me.anno.ui.editor

import me.anno.objects.Inspectable
import me.anno.studio.Studio
import me.anno.ui.base.Panel
import me.anno.ui.base.scrolling.ScrollPanelY
import me.anno.ui.base.components.Padding
import me.anno.ui.base.constraints.AxisAlignment
import me.anno.ui.base.groups.PanelListY
import me.anno.ui.input.ColorInput
import me.anno.ui.input.FloatInput
import me.anno.ui.input.VectorInput
import me.anno.ui.style.Style

class PropertyInspector(style: Style):
    ScrollPanelY(style.getChild("propertyInspector"), Padding(3), AxisAlignment.MIN){

    val list = child as PanelListY
    val secondaryList = PanelListY(style)
    var lastSelected: Inspectable? = null
    var needsUpdate = false

    // init { padding.top += 6 }

    override fun onDraw(x0: Int, y0: Int, x1: Int, y1: Int) {
        val selected = Studio.selectedInspectable
        if(selected != lastSelected){
            lastSelected = selected
            needsUpdate = false
            list.clear()
            selected?.createInspector(list, style)
        } else if(needsUpdate){
            lastSelected = selected
            needsUpdate = false
            secondaryList.clear()
            selected?.createInspector(secondaryList, style)
            // is matching required? not really
            val src = secondaryList.listOfAll.iterator()
            val dst = list.listOfAll.iterator()
            while(src.hasNext() && dst.hasNext()){// works as long as the structure stays the same
                val s = src.next()
                val d = dst.next()
                when(s){
                    is FloatInput -> {
                        (d as? FloatInput)?.apply {
                            d.setValue(s.lastValue, false)
                        }
                    }
                    is VectorInput -> {
                        (d as? VectorInput)?.apply {
                            d.setValue(s, false)
                        }
                    }
                    is ColorInput -> {
                        (d as? ColorInput)?.apply {
                            // contentView.
                        }
                    }
                }
            }
            if(src.hasNext() != dst.hasNext()){
                // we (would?) need to update the structure...
            }
        }
        super.onDraw(x0, y0, x1, y1)
    }

    operator fun plusAssign(panel: Panel){
        list += panel
    }

}