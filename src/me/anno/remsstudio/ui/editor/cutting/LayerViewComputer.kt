package me.anno.remsstudio.ui.editor.cutting

import me.anno.engine.Events.addEvent
import me.anno.remsstudio.RemsStudio
import me.anno.remsstudio.objects.Transform
import me.anno.remsstudio.objects.Video
import me.anno.remsstudio.ui.editor.TimelinePanel
import me.anno.utils.Color.white4
import org.joml.Vector4f

class LayerViewComputer(private val view: LayerView) {

    var isCalculating = false
    var calculated: List<Transform>? = null

    fun calculateSolution(x0: Int, y0: Int, x1: Int, y1: Int) {

        isCalculating = true
        view.needsUpdate = false

        val solution = LayerStripeSolution(x0, y0, x1, y1, TimelinePanel.centralTime)
        val stripes = solution.lines

        val root = RemsStudio.root

        val transforms = view.findElements()

        // load all metas
        for (transform in transforms) {
            if (transform is Video) {
                transform.update()
                transform.forcedMeta
            }
        }

        val timelineSlot = view.timelineSlot
        val drawn = transforms.filter { it.timelineSlot.value == timelineSlot }.reversed()
        view.drawn = drawn

        if (drawn.isNotEmpty()) {
            val stepSize = 1

            val leftTime = view.getTimeAt(x0.toFloat())
            val dt = TimelinePanel.dtHalfLength * 2.0 / view.width
            val white = white4

            val size = transforms.size

            // hashmaps are slower, but thread safe
            val localTime = DoubleArray(size)
            val localColor = Array(size) { Vector4f() }

            val parentIndices = IntArray(size)
            val transformMap = HashMap<Transform, Int>()
            for (i in transforms.indices) {
                transformMap[transforms[i]] = i
            }

            for (i in 1 until transforms.size) {
                parentIndices[i] = transformMap[transforms[i].parent]!!
            }

            val drawnIndices = IntArray(drawn.size)
            var k = 0
            for (entry in drawn) drawnIndices[k++] = transformMap[entry]!!

            for (x in x0 until x1 step stepSize) {

                val i = x - x0
                var lineIndex = 0
                val globalTime = leftTime + i * dt

                val rootTime = root.getLocalTime(globalTime)
                localTime[0] = rootTime
                localColor[0] = root.getLocalColor(white, rootTime)

                for (index in 1 until size) {
                    val parent = parentIndices[index]
                    val transform = transforms[index]
                    val localTimeI = transform.getLocalTime(localTime[parent])
                    localTime[index] = localTimeI
                    localColor[index] = transform.getLocalColor(localColor[parent], localTimeI, localColor[index])
                }

                for (index in drawnIndices) {
                    val tr = transforms[index]
                    val color = localColor[index]
                    val alpha = color.w * view.alphaMultiplier
                    if (tr.isVisible(localTime[index]) && alpha >= LayerView.minAlpha) {
                        color.w = alpha

                        val list = stripes[lineIndex]
                        if (list.isEmpty()) {
                            list += LayerViewGradient(tr, x, x, color, color)
                        } else {
                            val last = list.last()
                            if (last.owner === tr && last.x1 + stepSize >= x && last.isLinear(x, stepSize, color)) {
                                last.setEnd(x, stepSize, color)
                            } else {
                                list += LayerViewGradient(tr, x - stepSize + 1, x, color, color)
                            }
                        }

                        if (lineIndex + 1 >= LayerView.maxLines) break
                        else lineIndex++
                    }
                }
            }
        }

        for (stripe in stripes) {
            stripe.removeIf { !it.needsDrawn() }
        }

        addEvent {
            this.calculated = transforms
            view.solution = solution
            view.invalidateDrawing()
        }

    }

}