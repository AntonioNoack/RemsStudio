package me.anno.ui.base.groups

import me.anno.gpu.GFX
import me.anno.input.Input
import me.anno.input.MouseButton
import me.anno.ui.base.Panel
import me.anno.ui.base.Visibility
import me.anno.ui.base.constraints.AxisAlignment
import me.anno.ui.base.scrolling.ScrollPanelY.Companion.scrollSpeed
import me.anno.ui.base.scrolling.ScrollableY
import me.anno.ui.base.scrolling.ScrollbarY
import me.anno.ui.style.Style
import me.anno.utils.maths.Maths.clamp
import me.anno.utils.maths.Maths.fract
import me.anno.utils.maths.Maths.mix
import me.anno.utils.structures.tuples.Quad
import kotlin.math.max

class PanelListMultiline(val sorter: Comparator<Panel>?, style: Style) : PanelGroup(style), ScrollableY {

    override val children = ArrayList<Panel>(256)
    override val child: Panel
        get() = this

    // different modes for left/right alignment
    var childAlignmentX = AxisAlignment.CENTER

    var scaleChildren = false
    var childWidth: Int
    var childHeight: Int

    init {
        val defaultSize = 100
        childWidth = style.getSize("childWidth", defaultSize)
        childHeight = style.getSize("childHeight", defaultSize)
    }

    override fun invalidateLayout() {
        window?.needsLayout?.add(this)
    }

    override fun getLayoutState() =
        Pair(
            children.count { it.visibility == Visibility.VISIBLE },
            Quad(
                childWidth,
                childHeight,
                scrollPositionY,
                maxScrollPositionY
            )
        )

    var rows = 1
    var columns = 1
    var calcChildWidth = 0
    var calcChildHeight = 0
    var minH2 = 0

    var spacing = style.getSize("childSpacing", 1)

    override var scrollPositionY = 0f
    var isDownOnScrollbar = false

    override val maxScrollPositionY get() = max(0, minH2 - h)
    val scrollbar = ScrollbarY(this, style)
    val scrollbarWidth = style.getSize("scrollbarWidth", 8)
    val scrollbarPadding = style.getSize("scrollbarPadding", 1)

    override fun calculateSize(w: Int, h: Int) {

        val children = children
        if (sorter != null) {
            children.sortWith(sorter)
        }

        updateSize(w, h)
        for (i in children.indices) {
            val child = children[i]
            if (child.visibility != Visibility.GONE) {
                child.calculateSize(calcChildWidth, calcChildHeight)
                // child.applyConstraints()
            }
        }

    }

    var autoScrollTargetPosition = 0f
    var autoScrollEndTime = 0L
    var autoScrollPerNano = 1f
    var autoScrollLastUpdate = 0L

    fun scrollTo(itemIndex: Int, fractionY: Float) {
        val child = children.getOrNull(itemIndex) ?: return
        val currentY = child.y + fractionY * child.h
        val targetY = Input.mouseY
        val newScrollPosition = scrollPositionY + (currentY - targetY)
        smoothlyScrollTo(newScrollPosition, 0.25f)
    }

    override fun tickUpdate() {
        super.tickUpdate()
        if (autoScrollLastUpdate < autoScrollEndTime) {
            val delta = autoScrollPerNano * (GFX.gameTime - autoScrollLastUpdate)
            if (delta > 0L) {
                scrollPositionY = if (autoScrollLastUpdate < autoScrollEndTime && delta < 1f) {
                    mix(scrollPositionY, autoScrollTargetPosition, delta)
                } else autoScrollTargetPosition
                clampScrollPosition()
                invalidateLayout()
                autoScrollLastUpdate = GFX.gameTime
            }
        }
    }

    fun smoothlyScrollTo(y: Float, duration: Float = 1f) {
        autoScrollTargetPosition = clamp(y, 0f, maxScrollPositionY.toFloat())
        autoScrollPerNano = 5e-9f / duration
        autoScrollEndTime = GFX.gameTime + (duration * 1e9f).toLong()
        autoScrollLastUpdate = GFX.gameTime
        if (duration <= 0f) scrollPositionY = autoScrollTargetPosition
        invalidateDrawing()
    }

    fun getItemIndexAt(x: Float, y: Float): Int {
        val lx = x - this.x
        val ly = y - this.y + scrollPositionY - spacing
        val itemX = (lx * columns / w).toInt()
        val itemY = (ly * rows / h).toInt()
        return clamp(itemX + itemY * columns, 0, children.lastIndex)
    }

    fun getItemFractionY(y: Float): Float {
        val ly = y - this.y + scrollPositionY - spacing
        val itemY = ly * rows / h
        return fract(itemY)
    }

    fun clear() = children.clear()
    operator fun plusAssign(child: Panel) {
        children.plusAssign(child)
        child.parent = this
        invalidateLayout()
    }

    override fun remove(child: Panel) {
        children.remove(child)
        invalidateLayout()
    }

    private fun updateCount() {
        val childCount = children.count { it.visibility == Visibility.VISIBLE }
        columns = max(1, (w + spacing) / (childWidth + spacing))
        rows = max(1, (childCount + columns - 1) / columns)
    }

    private fun updateScale() {
        val childScale = if (scaleChildren) max(1f, ((w + spacing) / columns - spacing) * 1f / childWidth) else 1f
        calcChildWidth = if (scaleChildren) (childWidth * childScale).toInt() else childWidth
        calcChildHeight = if (scaleChildren) (childHeight * childScale).toInt() else childHeight
    }

    private fun updateSize(w: Int, h: Int) {
        updateCount()
        updateScale()
        minW = max(w, calcChildWidth)
        minH = max((calcChildHeight + spacing) * rows - spacing, h)
        minH += childHeight / 6 /* Reserve, because somehow it's not enough... */
        minH2 = minH
    }

    override fun applyPlacement(w: Int, h: Int) {
        updateSize(w, h)
        super.applyPlacement(w, h)
    }

    override fun placeInParent(x: Int, y: Int) {
        super.placeInParent(x, y)

        val w = w - scrollbarWidth
        val contentW = columns * childWidth

        val scroll = scrollPositionY.toInt()
        var i = 0
        for (j in children.indices) {
            val child = children[j]
            if (child.visibility != Visibility.GONE) {
                val ix = i % columns
                val iy = i / columns
                val cx = x + when (childAlignmentX) {
                    AxisAlignment.MIN, AxisAlignment.FILL -> ix * (calcChildWidth + spacing) + spacing
                    AxisAlignment.CENTER -> ix * calcChildWidth + max(0, w - contentW) * (ix + 1) / (columns + 1)
                    AxisAlignment.MAX -> w - (columns - ix) * (calcChildWidth + spacing)
                }
                val cy = y + iy * (calcChildHeight + spacing) + spacing - scroll
                // child.placeInParent(cx, cy)
                child.place(cx, cy, calcChildWidth, calcChildHeight)
                i++
            }
        }

    }

    override fun onDraw(x0: Int, y0: Int, x1: Int, y1: Int) {
        super.onDraw(x0, y0, x1, y1)
        clampScrollPosition()
        if (maxScrollPositionY > 0f) {
            scrollbar.x = x1 - scrollbarWidth - scrollbarPadding
            scrollbar.y = y + scrollbarPadding
            scrollbar.w = scrollbarWidth
            scrollbar.h = h - 2 * scrollbarPadding
            drawChild(scrollbar, x0, y0, x1, y1)
        }
    }

    override fun onMouseWheel(x: Float, y: Float, dx: Float, dy: Float) {
        if (!Input.isShiftDown && !Input.isControlDown) {
            val delta = dx - dy
            val scale = scrollSpeed
            if ((delta > 0f && scrollPositionY >= maxScrollPositionY) ||
                (delta < 0f && scrollPositionY <= 0f)
            ) {// if done scrolling go up the hierarchy one
                super.onMouseWheel(x, y, dx, dy)
            } else {
                scrollPositionY += scale * delta
                clampScrollPosition()
                invalidateLayout()
            }
        } else super.onMouseWheel(x, y, dx, dy)
    }

    private fun clampScrollPosition() {
        scrollPositionY = clamp(scrollPositionY, 0f, maxScrollPositionY.toFloat())
    }

    override fun onMouseDown(x: Float, y: Float, button: MouseButton) {
        isDownOnScrollbar = scrollbar.contains(x, y, scrollbarPadding * 2)
        if (!isDownOnScrollbar) super.onMouseDown(x, y, button)
    }

    override fun onMouseUp(x: Float, y: Float, button: MouseButton) {
        isDownOnScrollbar = false
        super.onMouseUp(x, y, button)
    }

    override fun onMouseMoved(x: Float, y: Float, dx: Float, dy: Float) {
        if (isDownOnScrollbar) {
            scrollbar.onMouseMoved(x, y, dx, dy)
            clampScrollPosition()
        } else super.onMouseMoved(x, y, dx, dy)
    }

}