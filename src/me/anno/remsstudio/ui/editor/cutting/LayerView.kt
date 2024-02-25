package me.anno.remsstudio.ui.editor.cutting

import me.anno.Time.gameTime
import me.anno.cache.CacheData
import me.anno.engine.EngineBase.Companion.dragged
import me.anno.engine.EngineBase.Companion.shiftSlowdown
import me.anno.engine.EngineBase.Companion.workspace
import me.anno.gpu.GFX
import me.anno.gpu.drawing.DrawRectangles.drawRect
import me.anno.input.Input
import me.anno.input.Input.isControlDown
import me.anno.input.Input.keysDown
import me.anno.input.Input.mouseKeysDown
import me.anno.input.Input.needsLayoutUpdate
import me.anno.input.Key
import me.anno.io.files.FileReference
import me.anno.io.json.saveable.JsonStringReader
import me.anno.language.translation.NameDesc
import me.anno.maths.Maths.clamp
import me.anno.maths.Maths.mix
import me.anno.maths.Maths.sq
import me.anno.remsstudio.RemsStudio
import me.anno.remsstudio.RemsStudio.isPlaying
import me.anno.remsstudio.Selection.select
import me.anno.remsstudio.Selection.selectTransform
import me.anno.remsstudio.Selection.selectedTransforms
import me.anno.remsstudio.animation.Keyframe
import me.anno.remsstudio.objects.Transform
import me.anno.remsstudio.objects.Video
import me.anno.remsstudio.ui.MenuUtils.drawTypeInCorner
import me.anno.remsstudio.ui.StudioFileImporter.addChildFromFile
import me.anno.remsstudio.ui.editor.TimelinePanel
import me.anno.ui.Panel
import me.anno.ui.Style
import me.anno.ui.base.menu.Menu.openMenu
import me.anno.ui.base.menu.MenuOption
import me.anno.ui.dragging.Draggable
import me.anno.ui.editor.files.FileContentImporter
import me.anno.utils.Color.white4
import me.anno.utils.hpc.ProcessingQueue
import me.anno.video.VideoCache
import kotlin.math.abs
import kotlin.math.roundToInt

class LayerView(val timelineSlot: Int, style: Style) : TimelinePanel(style) {

    // todo display name?

    // todo select multiple elements to move them around together
    // todo they shouldn't be parent and children, because that would have awkward results...

    val height1 = style.getSize("fontSize", 15) * 3

    override fun getTooltipPanel(x: Float, y: Float): Panel? {
        val video = getTransformAt(x, y) as? Video
        return if (video != null) {
            val data = VideoCache.getEntry(Triple(video, height1, video.file), 1000, false) {
                CacheData(VideoPreviewPanel(video, height1 * 2, style) {
                    video.getLocalTimeFromRoot(getTimeAt(it), false)
                })
            } as CacheData<*>
            data.value as VideoPreviewPanel
        } else null
    }

    var drawn: List<Transform>? = null
    val computer = LayerViewComputer(this)

    lateinit var cuttingView: LayerViewContainer

    val alphaMultiplier = 0.7f

    var draggedTransform: Transform? = null
    var draggedKeyframes: List<Keyframe<*>>? = null

    var hoveredTransform: Transform? = null
    var hoveredKeyframes: List<Keyframe<*>>? = null

    companion object {
        val minAlphaInt = 1
        val minAlpha = minAlphaInt / 255f
        val minDistSq = sq(3f / 255f)
        val maxLines = 5
        val defaultLayerCount = 8
        val taskQueue = ProcessingQueue("LayerView::calculateSolution")
    }

    var needsUpdate = false

    var solution: LayerStripeSolution? = null

    var visualStateCtr = 0
    override fun getVisualState() =
        Pair(
            super.getVisualState(),
            if ((isHovered && mouseKeysDown.isNotEmpty()) || isPlaying) visualStateCtr++
            else if (isHovered) {
                val window = window
                if (window != null) getTransformAt(window.mouseX, window.mouseY) else null
            } else null
        )

    override fun onUpdate() {
        super.onUpdate()
        solution?.keepResourcesLoaded()
    }

    override val canDrawOverBorders: Boolean
        get() = true

    var lastTime = gameTime

    // calculation is fast, drawing is slow
    override fun onDraw(x0: Int, y0: Int, x1: Int, y1: Int) {

        drawnStrings.clear()

        // val t0 = System.nanoTime()
        // 80-100µ for background and time axis
        drawBackground(x0, y0, x1, y1)
        drawTimeAxis(x0, y0, x1, y1, timelineSlot == 0)

        val parent = uiParent
        if (parent != null && parent.children.lastOrNull { it is LayerView } == this) {
            drawTypeInCorner("Cutting", fontColor)
        }

        // val t1 = System.nanoTime()
        val solution = solution
        val needsUpdate = needsUpdate ||
                solution == null ||
                x0 != solution.x0 ||
                x1 != solution.x1 ||
                isHovered ||
                mouseKeysDown.isNotEmpty() ||
                keysDown.isNotEmpty() ||
                abs(this.lastTime - gameTime) > if (needsLayoutUpdate(GFX.activeWindow!!)) 5e7 else 1e9

        if (needsUpdate && !computer.isCalculating) {
            lastTime = gameTime
            taskQueue += {
                try {
                    // may throw a null pointer exception,
                    // if the scene changes while calculating
                    computer.calculateSolution(x0, y0, x1, y1)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                computer.isCalculating = false
            }
        }

        this.solution?.apply {
            this.y0 = y
            this.y1 = y + height
            draw(selectedTransforms, draggedTransform)
        }

        val draggedTransform = draggedTransform
        val draggedKeyframes = draggedKeyframes

        if (draggedTransform == null || draggedKeyframes == null) {
            if (isHovered) {
                val window = window!!
                val hovered = getTransformAt(window.mouseX, window.mouseY)
                    ?: selectedTransforms.firstOrNull { it.timelineSlot.value == timelineSlot }
                hoveredTransform = hovered
                if (hovered != null) drawLines(x0, y0, x1, y1, hovered)
            }
        } else drawLines(x0, y0, x1, y1, draggedTransform)

    }

    @Suppress("UNUSED_PARAMETER")
    private fun drawLines(x0: Int, y0: Int, x1: Int, y1: Int, transform: Transform) {
        val color = transform.color
        if (color.isAnimated) {
            val window = window!!
            var ht0 = getTimeAt(window.mouseX - 5f)
            var ht1 = getTimeAt(window.mouseX + 5f)
            val hx0 = getXAt(ht0)
            val hx1 = getXAt(ht1)
            for (tr in transform.listOfInheritanceReversed) {
                ht0 = tr.getLocalTime(ht0)
                ht1 = tr.getLocalTime(ht1)
            }
            val keyframes = draggedKeyframes ?: color[ht0, ht1]
            hoveredKeyframes = keyframes
            var x = x0 - 1
            for (kf in keyframes) {
                val relativeTime = (kf.time - ht0) / (ht1 - ht0)
                val x2 = mix(hx0, hx1, relativeTime).toInt()
                if (x2 > x) {
                    drawRect(x2, y0, 1, y1 - y0, accentColor)
                    x = x2
                }
            }
        } else hoveredKeyframes = null
    }

    private fun getTransformAt(x: Float, y: Float): Transform? {
        val drawn = drawn ?: return null
        var bestTransform: Transform? = null
        val yInt = y.toInt()
        if (drawn.isNotEmpty()) {
            var ctr = 0
            val globalTime = getTimeAt(x)
            val root = RemsStudio.root
            root.lastLocalTime = root.getLocalTime(globalTime)
            root.updateLocalColor(white4, root.lastLocalTime)
            val calculated = computer.calculated
            if (calculated != null) {
                for (i in calculated.indices) {
                    val tr = calculated[i]
                    if (tr !== root) {
                        val p = tr.parent ?: continue
                        val localTime = tr.getLocalTime(p.lastLocalTime)
                        tr.lastLocalTime = localTime
                        tr.updateLocalColor(p.lastLocalColor, localTime)
                    }
                }
            }
            for (i in drawn.indices) {
                val tr = drawn[i]
                val color = tr.lastLocalColor
                val alpha = color.w * alphaMultiplier
                if (alpha >= minAlpha && tr.isVisible(tr.lastLocalTime)) {
                    if (yInt - (this.y + 3 + ctr * 3) in 0..height - 10) {
                        bestTransform = tr
                    }
                    ctr++
                }
            }
        }
        return bestTransform
    }

    // done hold / move up/down / move sideways
    // done right click cut
    // done move start/end times
    // done highlight the hovered panel?

    override fun onKeyDown(x: Float, y: Float, key: Key) {
        if (key == Key.BUTTON_LEFT) {
            var draggedTransform = getTransformAt(x, y)
            this.draggedTransform = draggedTransform
            if (draggedTransform != null) {
                select(draggedTransform, draggedTransform.color)
                if (draggedTransform == hoveredTransform) {
                    val hoveredKeyframes = hoveredKeyframes
                    draggedKeyframes = if (hoveredKeyframes?.isNotEmpty() == true) {
                        hoveredKeyframes
                    } else null
                }
            } else {
                // move the keyframes of the last selected transform,
                // they may be invisible
                val hoveredTransform = hoveredTransform
                val hoveredKeyframes = hoveredKeyframes
                if (hoveredTransform != null && hoveredKeyframes?.isNotEmpty() == true) {
                    draggedTransform = hoveredTransform
                    this.draggedTransform = draggedTransform
                    select(draggedTransform, draggedTransform.color)
                    draggedKeyframes = hoveredKeyframes
                }
            }
        } else super.onKeyDown(x, y, key)
    }

    override fun onDeleteKey(x: Float, y: Float) {
        RemsStudio.largeChange("Deleted Component") {
            for (selectedTransform in selectedTransforms)
                selectedTransform.destroy()
            select(emptyList<Transform>(), null)
        }
    }

    override fun onMouseMoved(x: Float, y: Float, dx: Float, dy: Float) {
        val transform = draggedTransform
        val draggedKeyframes = draggedKeyframes
        if (transform != null) {
            if (draggedKeyframes != null) {
                if (draggedKeyframes.isNotEmpty()) {
                    val dilation = transform.listOfInheritance
                        .fold(1.0) { t0, tx -> t0 * tx.timeDilation.value }
                    val dt = shiftSlowdown * dilation * dx * dtHalfLength * 2 / width
                    if (dt != 0.0) {
                        // todo apply snapping, if possible
                        // todo only apply for small deltas
                        RemsStudio.incrementalChange("Move Keyframes") {
                            for (kf in draggedKeyframes) {
                                kf.time += dt
                            }
                        }
                    }
                }
            } else {
                val thisSlot = this@LayerView.timelineSlot
                if (dx != 0f) {
                    RemsStudio.incrementalChange("Change Time Dilation / Offset") {
                        if (isControlDown) {
                            // todo scale around the time=0 point?
                            // todo first find this point...
                            val factor = clamp(1f - shiftSlowdown * dx / width, 0.01f, 100f)
                            transform.timeDilation.value *= factor
                        } else {
                            // todo use parent dilation?...
                            val dt = shiftSlowdown * dx * dtHalfLength * 2 / width
                            transform.timeOffset.value += dt
                        }
                    }
                }
                var sumDY = (y - Input.mouseDownY) / height1
                if (sumDY < 0) sumDY += 0.5f
                else sumDY -= 0.5f
                if (sumDY.isFinite()) {
                    val newSlot = thisSlot + sumDY.roundToInt()
                    if (newSlot != transform.timelineSlot.value) {
                        RemsStudio.largeChange("Changed Timeline Slot") {
                            transform.timelineSlot.value = newSlot
                        }
                    }
                }
            }
        } else super.onMouseMoved(x, y, dx, dy)
    }

    override fun onKeyUp(x: Float, y: Float, key: Key) {
        if (key == Key.BUTTON_LEFT || key == Key.BUTTON_RIGHT) {
            draggedTransform = null
            draggedKeyframes = null
        } else super.onKeyUp(x, y, key)
    }

    override fun onMouseClicked(x: Float, y: Float, button: Key, long: Boolean) {
        when (button) {
            Key.BUTTON_RIGHT -> {
                val transform = getTransformAt(x, y)
                if (transform != null) {
                    val localTime = transform.lastLocalTime
                    // get the options for this transform
                    val options = ArrayList<MenuOption>()
                    options += MenuOption(
                        NameDesc(
                            "Split Here",
                            "Cuts the element in two halves",
                            "ui.cutting.splitHere"
                        )
                    ) {
                        RemsStudio.largeChange("Split Component") {
                            SplitTransform.split(transform, localTime)
                        }
                    }
                    openMenu(windowStack, options)
                } else super.onMouseClicked(x, y, button, long)
            }
            else -> super.onMouseClicked(x, y, button, long)
        }
    }

    fun findElements(): List<Transform> {
        val list = ArrayList<Transform>()
        fun inspect(parent: Transform): Boolean {
            val isRequired = parent.children.count { child ->
                inspect(child)
            } > 0 || parent.timelineSlot.value == timelineSlot
            if (isRequired) {
                list += parent
            }
            return isRequired
        }
        inspect(RemsStudio.root)
        return list.reversed()
    }

    override fun onPaste(x: Float, y: Float, data: String, type: String) {
        if (!data.startsWith("[")) return super.onPaste(x, y, data, type)
        try {
            val childMaybe = JsonStringReader.read(data, workspace, true).firstOrNull { it is Transform } as? Transform
            val child = childMaybe ?: return super.onPaste(x, y, data, type)
            val original = (dragged as? Draggable)?.getOriginal() as? Transform
            RemsStudio.largeChange("Pasted Component / Changed Timeline Slot") {
                if (original != null) {
                    original.timelineSlot.value = timelineSlot
                } else {
                    val root = RemsStudio.root
                    root.addChild(child)
                    root.timelineSlot.value = timelineSlot
                    selectTransform(child)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            super.onPaste(x, y, data, type)
        }
    }

    override fun onPasteFiles(x: Float, y: Float, files: List<FileReference>) {
        val time = getTimeAt(x)
        for (file in files) {
            addChildFromFile(RemsStudio.root, file, FileContentImporter.SoftLinkMode.ASK, true) {
                it.timeOffset.value = time
                it.timelineSlot.value = timelineSlot
            }
        }
    }

    override fun calculateSize(w: Int, h: Int) {
        super.calculateSize(w, h)
        minW = w
        minH = height1
    }

}