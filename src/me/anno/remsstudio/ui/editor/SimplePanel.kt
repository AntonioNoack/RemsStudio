package me.anno.remsstudio.ui.editor

import me.anno.ui.Panel
import kotlin.math.max
import kotlin.math.min

/**
 * simple controls;
 * legacy, should be replaced with real panel soon
 * */
@Suppress("MemberVisibilityCanBePrivate")
class SimplePanel(
    val drawable: Panel,
    // -1 .. 0 .. 1
    var relativeX: Int = 0,
    var relativeY: Int = 0,
    // not bounded
    var deltaX: Int = 0,
    var deltaY: Int = 0,
    // size
    var sizeX: Int = 0,
    var sizeY: Int = 0
) {

    var px = 0
    var py = 0

    init {
        drawable.makeBackgroundTransparent()
        drawable.width = sizeX
        drawable.height = sizeY
        drawable.minW = sizeX
        drawable.minH = sizeY
    }

    fun setOnClickListener(listener: () -> Unit): SimplePanel {
        drawable.addLeftClickListener { listener() }
        return this
    }

    constructor(drawable: Panel, isLeft: Boolean, isTop: Boolean, leftOffset: Int, topOffset: Int, size: Int) :
            this(
                drawable, if (isLeft) -1 else 1, if (isTop) -1 else 1,
                leftOffset, topOffset, size, size
            )

    fun draw(
        x: Int, y: Int, w: Int, h: Int,
        x0: Int, y0: Int, x1: Int, y1: Int
    ) {
        px = x + deltaX + (relativeX + 1) * w / 2
        py = y + deltaY + (relativeY + 1) * h / 2
        // hide if half/a third the size is not enough
        drawable.setPosSize(px, py, sizeX, sizeY)
        drawable.draw(
            max(x0, px), max(y0, py),
            min(x1, px + sizeX), min(y1, py + sizeY)
        )
    }

    fun contains(x: Int, y: Int) = x - px in 0 until sizeX && y - py in 0 until sizeY
}