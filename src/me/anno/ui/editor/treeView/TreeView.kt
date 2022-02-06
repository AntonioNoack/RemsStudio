package me.anno.ui.editor.treeView

import me.anno.config.DefaultConfig
import me.anno.gpu.drawing.DrawRectangles.drawRect
import me.anno.input.MouseButton
import me.anno.io.files.FileReference
import me.anno.remsstudio.objects.Transform
import me.anno.remsstudio.Selection
import me.anno.ui.Panel
import me.anno.ui.base.Visibility
import me.anno.ui.base.components.Padding
import me.anno.ui.base.groups.PanelList
import me.anno.ui.base.scrolling.ScrollPanelXY
import me.anno.ui.editor.files.FileContentImporter
import me.anno.ui.style.Style
import me.anno.maths.Maths.fract

// todo select multiple elements, filter for common properties, and apply them all together :)

// todo search elements
// todo search with tags

abstract class TreeView<V>(
    val sources: List<V>,
    val fileContentImporter: FileContentImporter<V>,
    val showSymbols: Boolean,
    style: Style
) : ScrollPanelXY(Padding(5), style.getChild("treeView")) {

    val list = content as PanelList
    val sample get() = list.children.first() as TreeViewPanel<*>

    init {
        padding.top = 16
    }

    val elementByIndex = ArrayList<V>()

    var inset = style.getSize("fontSize", 12) / 3
    var collapsedSymbol = DefaultConfig["ui.symbol.collapsed", "\uD83D\uDDBF"]

    var needsTreeUpdate = true
    var focused: Panel? = null
    var takenElement: Transform? = null

    // Selection.select(element, null)
    abstract fun selectElement(element: V?)

    // zoomToObject
    abstract fun focusOnElement(element: V)

    abstract fun openAddMenu(parent: V)

    abstract fun getChildren(element: V): List<V>

    abstract fun isCollapsed(element: V): Boolean

    abstract fun setCollapsed(element: V, collapsed: Boolean)

    abstract fun addChild(element: V, child: Any)

    abstract fun removeChild(element: V, child: V)

    abstract fun getSymbol(element: V): String

    abstract fun getTooltipText(element: V): String?

    abstract fun getParent(element: V): V?

    abstract fun destroy(element: V)

    // element.name.ifBlank { element.defaultDisplayName })
    abstract fun getName(element: V): String

    abstract fun setName(element: V, name: String)

    // val index = parentChildren.indexOf(self)
    // parentChildren.add(index, child)
    // child.parent = p
    abstract fun addBefore(self: V, sibling: Any)

    // val index = parentChildren.indexOf(self)
    // parentChildren.add(index + 1, child)
    // child.parent = p
    abstract fun addAfter(self: V, sibling: Any)

    abstract fun stringifyForCopy(element: V): String

    // todo define these functions
    // todo use these functions to show indicator colors
    // todo use these functions to actually forbid the action
    abstract fun canBeRemoved(element: V): Boolean
    abstract fun canBeInserted(parent: V, element: V, index: Int): Boolean

    abstract fun getDragType(element: V): String

    fun selectElementMaybe(element: V?) {
        // if already selected, don't inspect that property/driver
        if (Selection.selectedTransform == element) Selection.clear()
        selectElement(element)
    }

    open fun getLocalColor(element: V, isHovered: Boolean, isInFocus: Boolean): Int {
        return -1
    }

    private fun addToTreeList(element: V, depth: Int, index0: Int): Int {
        var index = index0
        val panel = getOrCreateChildPanel(index++, element)
        val isCollapsed = isCollapsed(element)
        //(panel.parent!!.children[0] as SpacePanel).minW = inset * depth + panel.padding.right
        val symbol = if (isCollapsed) collapsedSymbol else getSymbol(element)
        panel.setText(symbol.trim(), getName(element))
        val padding = panel.padding
        val left = inset * depth + padding.right
        if (padding.left != left) {
            padding.left = left
            invalidateLayout()
        }
        if (!isCollapsed) {
            val children = getChildren(element)
            for (i in children.indices) {
                val child = children[i]
                index = addToTreeList(child, depth + 1, index)
            }
        }// todo else show that it's collapsed, if there is no symbol
        // invalidateLayout()
        return index
    }

    // if the size of the tree is large, this can use up
    // quite a lot of time -> only update when necessary
    private fun updateTree() {
        needsTreeUpdate = false
        var index = 0
        val sources = sources
        for (i in sources.indices) {
            val element = sources[i]
            index = addToTreeList(element, 0, index)
        }
        // make the rest invisible (instead of deleting them)
        val children = list.children
        for (i in index until children.size) {
            val child = children[i]
            child.visibility = Visibility.GONE
        }
    }

    override fun invalidateLayout() {
        super.invalidateLayout()
        needsTreeUpdate = true
    }

    override fun tickUpdate() {
        super.tickUpdate()
        if (needsTreeUpdate) {
            updateTree()
        }
    }

    override fun onDraw(x0: Int, y0: Int, x1: Int, y1: Int) {
        super.onDraw(x0, y0, x1, y1)
        updateTree()
        drawLineOfTakenElement(x0, y0, x1, y1)
    }

    private fun drawLineOfTakenElement(x0: Int, y0: Int, x1: Int, y1: Int) {

        val focused = focused
        if (focused?.isInFocus != true) {
            takenElement = null
            return
        }

        if (takenElement != null) {
            // sample element, assumes that the height of all elements is the same
            // this will most likely be the case
            val h = focused.h
            val window = window!!
            val mx = window.mouseX.toInt()
            val my = window.mouseY.toInt()
            val my0 = list.children.firstOrNull()?.y ?: 0
            val hoveredTransformIndex = (my - my0).toFloat() / (h + list.spacing)
            val fractionalHTI = fract(hoveredTransformIndex)
            if (fractionalHTI in 0.25f..0.75f) {
                // on top
                // add as child
                val targetY = my - 1 + h / 2 - (fractionalHTI * h).toInt()
                drawRect(this.x + 2, targetY, 3, 1, -1)
            } else {
                // in between
                // add in between elements
                val targetY = my - 1 + h / 2 - (fract(hoveredTransformIndex + 0.5f) * h).toInt()
                drawRect(this.x + 2, targetY, 3, 1, -1)
            }
            drawDraggedElement(focused, x0, y0, x1, y1, mx, my)
        }
    }

    private fun drawDraggedElement(focused: Panel, x0: Int, y0: Int, x1: Int, y1: Int, mx: Int, my: Int) {
        val x = focused.x
        val y = focused.y
        focused.x = mx - focused.w / 2
        focused.y = my - focused.h / 2
        focused.draw(x0, y0, x1, y1)
        focused.x = x
        focused.y = y
    }

    override fun onMouseClicked(x: Float, y: Float, button: MouseButton, long: Boolean) {
        if (button.isRight) {
            // correct? maybe 😄
            openAddMenu(sources.last())
        } else super.onMouseClicked(x, y, button, long)
    }

    private fun getOrCreateChildPanel(index: Int, element: V): TreeViewPanel<*> {
        if (index < list.children.size) {
            elementByIndex[index] = element
            val panel = list.children[index] as TreeViewPanel<*>
            panel.visibility = Visibility.VISIBLE
            return panel
        }
        elementByIndex += element
        val child = TreeViewPanel(
            { elementByIndex[index] }, ::getName, ::setName, this::openAddMenu,
            fileContentImporter, showSymbols, this, style
        )
        child.padding.left = 4
        // todo checkbox with custom icons
        list += child
        return child
    }

    // done display, where we'd move it
    // done between vs at the end vs at the start
    // todo we'd need a selection mode with the arrow keys, too...

    override fun onPasteFiles(x: Float, y: Float, files: List<FileReference>) {
        for (file in files) {
            fileContentImporter.addChildFromFile(
                sources.lastOrNull(),
                file,
                FileContentImporter.SoftLinkMode.ASK,
                true
            ) {}
        }
    }

}