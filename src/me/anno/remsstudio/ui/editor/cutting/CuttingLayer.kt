package me.anno.remsstudio.ui.editor.cutting

import me.anno.Time.gameTime
import me.anno.cache.CacheSection
import me.anno.engine.EngineBase.Companion.dragged
import me.anno.engine.EngineBase.Companion.workspace
import me.anno.gpu.drawing.DrawRectangles.drawRect
import me.anno.input.Input
import me.anno.input.Input.isControlDown
import me.anno.input.Input.keysDown
import me.anno.input.Input.mouseKeysDown
import me.anno.input.Input.shiftSlowdown
import me.anno.input.Key
import me.anno.io.files.FileReference
import me.anno.io.json.saveable.JsonStringReader
import me.anno.language.translation.NameDesc
import me.anno.maths.Maths.MILLIS_TO_NANOS
import me.anno.maths.Maths.clamp
import me.anno.maths.Maths.mix
import me.anno.maths.Maths.sq
import me.anno.remsstudio.RemsStudio
import me.anno.remsstudio.Selection.select
import me.anno.remsstudio.Selection.selectTransform
import me.anno.remsstudio.Selection.selectedTransforms
import me.anno.remsstudio.animation.Keyframe
import me.anno.remsstudio.objects.Transform
import me.anno.remsstudio.objects.video.Video
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
import me.anno.utils.Color.withAlpha
import me.anno.utils.hpc.ProcessingQueue
import me.anno.utils.types.Floats.toIntOr
import kotlin.math.*

@Suppress("MemberVisibilityCanBePrivate")
class CuttingLayer(val timelineSlot: Int, style: Style) : TimelinePanel(style) {

    companion object {
        val minAlphaInt = 1
        val minAlpha = minAlphaInt / 255f
        val minDistSq = sq(3f / 255f)
        val maxLines = 5
        val defaultLayerCount = 8
        val taskQueue = ProcessingQueue("LayerView::calculateSolution")

        private data class VideoPreviewKey(val video: Video, val height1: Int, val file: FileReference)

        private val vppCache = CacheSection<VideoPreviewKey, VideoPreviewPanel>("VideoPreviews")
    }

    // todo display name?

    val constMinH = style.getSize("fontSize", 15) * 3

    override fun getTooltipPanel(x: Float, y: Float): Panel? {
        val video = getTransformAt(x, y) as? Video
        return if (video != null) {
            vppCache.getEntry(VideoPreviewKey(video, constMinH, video.file), 1000) { key, result ->
                result.value = VideoPreviewPanel(key.video, key.height1 * 2, style) {
                    key.video.getLocalTimeFromRoot(getTimeAt(it), false)
                }
            }.value
        } else null
    }

    var drawn: List<Transform>? = null
    val computer = LayerViewComputer(this)

    lateinit var cuttingView: CuttingEditor

    val alphaMultiplier = 0.7f

    var draggedKeyframes: List<Keyframe<*>> = emptyList()
    var isDraggingTransforms = false

    var hoveredTransform: Transform? = null
    var hoveredKeyframes: List<Keyframe<*>> = emptyList()

    var needsUpdate = false

    var solution: LayerStripeSolution? = null

    override val canDrawOverBorders: Boolean
        get() = true

    var lastTime = gameTime

    private val renderSize = RenderPosSize()

    // calculation is fast, drawing is slow
    override fun draw(x0: Int, y0: Int, x1: Int, y1: Int) {

        drawnStrings.clear()

        // val t0 = System.nanoTime()
        // 80-100µ for background and time axis
        drawBackground(x0, y0, x1, y1)
        drawTimeAxis(x0, y0, x1, y1, timelineSlot == 0)

        val parent = uiParent
        if (parent != null && parent.children.lastOrNull { it is CuttingLayer } == this) {
            drawTypeInCorner("Cutting", fontColor)
        }

        updateSolutionIfNeeded(x0, y0, x1, y1)
        drawSolution()

        drawKeyframes(x0, y0, x1, y1)

        if (Input.isShiftDown && Input.isLeftDown &&
            windowStack.inFocus0 is CuttingLayer
        ) showRectangleSelection(x0, y0, x1, y1)
    }

    private fun showRectangleSelection(x0: Int, y0: Int, x1: Int, y1: Int) {
        val window = window ?: return
        val minX = max(min(window.mouseDownX, window.mouseX).toIntOr(), x0)
        val maxX = min(max(window.mouseDownX, window.mouseX).toIntOr(), x1)
        val minY = min(window.mouseDownY, window.mouseY)
        val maxY = max(window.mouseDownY, window.mouseY)
        if (maxY <= y0 || minY >= y1 || maxX <= minX) return // not affected

        // show selection
        drawRect(minX, y0, maxX - minX, y1 - y0, accentColor.withAlpha(127))
    }

    private fun isSelectingByY(): Boolean {
        val window = window ?: return false
        val minY = min(window.mouseDownY, window.mouseY)
        val maxY = max(window.mouseDownY, window.mouseY)
        val y0 = y
        val y1 = y0 + height
        return !(maxY <= y0 || minY >= y1)// not affected
    }

    private fun updateSolutionIfNeeded(x0: Int, y0: Int, x1: Int, y1: Int) {
        val hasConstantSize = renderSize.updateSize(x1 - x0, x0)

        // val t1 = System.nanoTime()
        val solution = solution
        val needsUpdate = needsUpdate ||
                solution == null ||
                x0 != solution.x0 ||
                x1 != solution.x1 ||
                isHovered ||
                mouseKeysDown.isNotEmpty() ||
                keysDown.isNotEmpty() ||
                abs(lastTime - gameTime) > 500 * MILLIS_TO_NANOS

        if (needsUpdate && !computer.isCalculating && hasConstantSize) {
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
    }

    private fun drawSolution() {
        val solution = solution
        if (solution != null) {
            solution.y0 = y
            solution.y1 = y + height
            val isSelected: (Transform) -> Boolean = { it in selectedTransforms }
            solution.draw(selectedTransforms, isSelected)
        }
    }

    private fun drawKeyframes(x0: Int, y0: Int, x1: Int, y1: Int) {
        val draggedKeyframes = draggedKeyframes
        hoveredKeyframes = if (draggedKeyframes.isNotEmpty()) {
            val transform = selectedTransforms.firstOrNull()
            drawLines(x0, y0, x1, y1, transform, draggedKeyframes)
        } else {
            val window = window ?: return
            val hovered = getTransformAt(window.mouseX, window.mouseY)
                ?: selectedTransforms.firstOrNull { it.timelineSlot.value == timelineSlot }
            hoveredTransform = hovered
            drawLines(x0, y0, x1, y1, hovered, null)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun drawLines(
        x0: Int, y0: Int, x1: Int, y1: Int,
        transform: Transform?,
        draggedKeyframes: List<Keyframe<*>>?
    ): List<Keyframe<*>> {
        if (transform == null) return emptyList()
        val color = transform.color
        if (!color.isAnimated) return emptyList()

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
        var x = x0 - 1
        for (kf in keyframes) {
            val relativeTime = (kf.time - ht0) / (ht1 - ht0)
            val x2 = mix(hx0, hx1, relativeTime).toIntOr()
            if (x2 > x) {
                drawRect(x2, y0, 1, y1 - y0, accentColor)
                x = x2
            }
        }
        return keyframes
    }

    private fun getTransformAt(x: Float, y: Float): Transform? {
        return getTransformAt(x, y) { true }
    }

    private fun updateLastLocalTimeForSlot(x: Float) {
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
    }

    private fun getTransformAt(x: Float, y: Float, filter: (Transform) -> Boolean): Transform? {
        val drawn = drawn ?: return null
        var bestTransform: Transform? = null
        val yInt = y.toInt()
        if (drawn.isNotEmpty()) {
            updateLastLocalTimeForSlot(x)
            var ctr = 0
            for (i in drawn.indices) {
                val transform = drawn[i]
                if (!filter(transform)) continue
                val color = transform.lastLocalColor
                val alpha = color.w * alphaMultiplier
                if (alpha >= minAlpha && transform.isVisible(transform.lastLocalTime)) {
                    if (yInt - (this.y + 3 + ctr * 3) in 0..height - 10) {
                        bestTransform = transform
                    }
                    ctr++
                }
            }
        }
        return bestTransform
    }

    private fun updateLastLocalTimeForAll(x: Float) {
        val globalTime = getTimeAt(x)
        val root = RemsStudio.root
        root.lastLocalTime = root.getLocalTime(globalTime)
        root.updateLocalColor(white4, root.lastLocalTime)
        fun update(tr: Transform, parent: Transform) {
            val localTime = tr.getLocalTime(parent.lastLocalTime)
            tr.lastLocalTime = localTime
            tr.updateLocalColor(parent.lastLocalColor, localTime)
            for (child in tr.children) update(child, tr)
        }
        for (child in root.children) {
            update(child, root)
        }
    }

    private fun getTransformsInRange(x0: Int, x1: Int, candidates: List<Transform>): List<Transform> {
        // todo could be quite expensive :(, this can be optimized a lot for elements with trivial color...
        val found = HashSet<Transform>(candidates.size)
        for (x in x0..x1) {
            updateLastLocalTimeForAll(x.toFloat())
            for (i in candidates.indices) {
                val transform = candidates[i]
                if (transform in found) continue

                val color = transform.lastLocalColor
                val alpha = color.w * alphaMultiplier
                if (alpha >= minAlpha && transform.isVisible(transform.lastLocalTime)) {
                    found.add(transform)
                }
            }
        }
        return candidates.filter { it in found }
    }

    override fun onDeleteKey(x: Float, y: Float) {
        RemsStudio.largeChange("Deleted Component") {
            for (selectedTransform in selectedTransforms)
                selectedTransform.destroy()
            select(emptyList(), emptyList())
        }
    }

    override fun onMouseMoved(x: Float, y: Float, dx: Float, dy: Float) {
        val dragging = Input.isLeftDown && isAnyChildInFocus
        val draggedKeyframes = draggedKeyframes
        if (dragging && Input.isShiftDown) {
            // handled separately
        } else if (dragging && draggedKeyframes.isNotEmpty()) {
            val transform = selectedTransforms.firstOrNull() ?: return
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
        } else if (dragging && isDraggingTransforms) {
            if (dx != 0f) {
                RemsStudio.incrementalChange("Change Time Dilation / Offset") {
                    val factor = clamp(exp(-shiftSlowdown * dx / (window?.width ?: width)), 0.01f, 100f)
                    val offset = shiftSlowdown * dx * dtHalfLength * 2 / width
                    // they shouldn't be parent and children, because that would have awkward results...
                    val withoutParents = removeChildrenOfParents(selectedTransforms)
                    for (transform in withoutParents) {
                        if (isControlDown) {
                            // todo scale around the time=0 point?
                            // todo first find this point...
                            transform.timeDilation.value *= factor
                        } else {
                            // todo use parent dilation?...
                            transform.timeOffset.value += offset
                        }
                    }
                }
            }

            val height = max(10, this.height)
            val prevIndex = (floor(y - dy - this.y) / height).toInt()
            val currIndex = (floor(y - this.y) / height).toInt()
            if (currIndex != prevIndex) {
                val delta = currIndex - prevIndex
                RemsStudio.largeChange("Changed Timeline Slot") {
                    for (transform in selectedTransforms) {
                        transform.timelineSlot.value += delta
                    }
                }
            }
        } else super.onMouseMoved(x, y, dx, dy)
    }

    private fun removeChildrenOfParents(transforms: List<Transform>): List<Transform> {
        val searchSet = transforms.toSet()
        return transforms.filter { tr ->
            var element = tr
            var parentsNotIncluded = true
            while (true) {
                element = element.parent ?: break
                if (element in searchSet) {
                    parentsNotIncluded = false
                    break
                }
            }
            parentsNotIncluded
        }
    }

    override fun onKeyDown(x: Float, y: Float, key: Key) {
        if (key == Key.BUTTON_LEFT) {
            val selectedTransforms = selectedTransforms
            draggedKeyframes = if (selectedTransforms.size == 1) hoveredKeyframes else emptyList()
            isDraggingTransforms = draggedKeyframes.isEmpty() &&
                    getTransformAt(x, y) { it in selectedTransforms } != null
        }
        super.onKeyDown(x, y, key)
    }

    override fun onKeyUp(x: Float, y: Float, key: Key) {
        if (key == Key.BUTTON_LEFT || key == Key.BUTTON_RIGHT) {
            if (Input.isShiftDown) {
                applyRectangleSelect(x)
            } else draggedKeyframes = emptyList()
        } else super.onKeyUp(x, y, key)
    }

    private fun applyRectangleSelect(x: Float) {
        val window = window ?: return
        val layers = uiParent!!.children.filterIsInstance<CuttingLayer>()
        val candidateTransforms = layers
            .filter { it.isSelectingByY() }
            .flatMap { it.drawn ?: emptyList() }

        val minX = max(min(window.mouseDownX, x).toIntOr(), this.x)
        val maxX = min(max(window.mouseDownX, x).toIntOr(), this.x + this.width)
        val inRange = getTransformsInRange(minX, maxX, candidateTransforms)
        select(inRange)
    }

    private fun select(newSelected: List<Transform>) {
        select(newSelected, newSelected.map { it.color })
        draggedKeyframes = if (newSelected.size == 1) hoveredKeyframes else emptyList()
    }

    override fun onMouseClicked(x: Float, y: Float, button: Key, long: Boolean) {
        when (button) {
            Key.BUTTON_LEFT -> {
                isDraggingTransforms = false
                val hovered = getTransformAt(x, y)
                if (hovered != null) {
                    // toggle it instead of adding
                    val newSelected = if (isControlDown && selectedTransforms.isNotEmpty()) {
                        if (hovered !in selectedTransforms) selectedTransforms + hovered
                        else selectedTransforms.filter { it != hovered }
                    } else listOf(hovered)
                    select(newSelected)
                } else {
                    select(emptyList())
                    super.onMouseClicked(x, y, button, long)
                }
            }
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

    fun findTransformsInSlotAndTheirHierarchy(): List<Transform> {
        val list = ArrayList<Transform>()
        fun collect(parent: Transform): Boolean {
            val isRequired = parent.children.count { child -> collect(child) } > 0 ||
                    parent.timelineSlot.value == timelineSlot
            if (isRequired) {
                list += parent
            }
            return isRequired
        }
        collect(RemsStudio.root)
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
        minH = constMinH
    }

}