package me.anno.remsstudio.ui

import me.anno.config.DefaultConfig
import me.anno.config.DefaultStyle
import me.anno.io.files.InvalidRef
import me.anno.io.json.saveable.JsonStringWriter
import me.anno.io.utils.StringMap
import me.anno.language.translation.Dict
import me.anno.language.translation.NameDesc
import me.anno.maths.Maths.clamp
import me.anno.remsstudio.RemsStudio
import me.anno.remsstudio.RemsStudio.defaultWindowStack
import me.anno.remsstudio.RemsStudio.lastTouchedCamera
import me.anno.remsstudio.RemsStudio.nullCamera
import me.anno.remsstudio.Selection
import me.anno.remsstudio.objects.*
import me.anno.remsstudio.objects.Transform.Companion.toTransform
import me.anno.remsstudio.objects.effects.MaskLayer
import me.anno.remsstudio.objects.video.Video
import me.anno.remsstudio.ui.MenuUtils.drawTypeInCorner
import me.anno.ui.Style
import me.anno.ui.base.menu.Menu
import me.anno.ui.base.menu.MenuOption
import me.anno.ui.editor.treeView.TreeView
import me.anno.utils.Color.black
import me.anno.utils.Color.toARGB
import me.anno.utils.types.Strings.camelCaseToTitle
import org.apache.logging.log4j.LogManager
import org.joml.Vector3f
import org.joml.Vector4f
import java.util.*

class StudioTreeView(style: Style) :
    TreeView<Transform>(StudioFileImporter, true, style) {

    override fun listRoots(): List<Transform> {
        val nc = nullCamera
        return if (nc == null) listOf(RemsStudio.root)
        else listOf(nc, RemsStudio.root)
    }

    override fun removeRoot(root: Transform) {
        LOGGER.debug("Removing root is not supported")
    }

    override fun getDragType(element: Transform) = "Transform"
    override fun stringifyForCopy(element: Transform) = JsonStringWriter.toText(element, InvalidRef)
    override fun getSymbol(element: Transform) = element.symbol
    override fun isCollapsed(element: Transform) = element.isCollapsed
    override fun getName(element: Transform) = element.name.ifBlank { element.defaultDisplayName }
    override fun getParent(element: Transform) = element.parent
    override fun getChildren(element: Transform) = element.children

    override fun removeChild(parent: Transform, child: Transform) {
        parent.removeChild(child)
        invalidateLayout()
    }

    override fun setCollapsed(element: Transform, collapsed: Boolean) {
        element.isCollapsedI.value = collapsed
        invalidateLayout()
    }

    override fun setName(element: Transform, name: String) {
        element.nameI.value = name
        invalidateLayout()
    }

    override fun destroy(element: Transform) {
        element.destroy()
        invalidateLayout()
    }

    override fun canBeInserted(parent: Transform, element: Transform, index: Int): Boolean {
        val immutable = parent.listOfInheritance.any { it.areChildrenImmutable }
        return !immutable
    }

    override fun canBeRemoved(element: Transform): Boolean {
        val parent = element.parent ?: return false // root cannot be removed
        val immutable = parent.listOfInheritance.any { it.areChildrenImmutable }
        return !immutable
    }

    override fun selectElements(elements: List<Transform>) {
        Selection.selectTransform(elements)
    }

    override fun selectElementsMaybe(elements: List<Transform>) {
        // if already selected, don't inspect that property/driver
        if (Selection.selectedTransforms == elements &&
            (Selection.selectedProperties.isNotEmpty() ||
                    (Selection.selectedInspectables.isNotEmpty() && Selection.selectedInspectables != elements))
        ) {
            Selection.clear()
        } else {
            selectElements(elements)
        }
    }

    override fun focusOnElement(element: Transform): Boolean {
        return zoomToObject(element)
    }

    override fun openAddMenu(parent: Transform) {
        Companion.openAddMenu(parent)
    }

    private val tmp = Vector4f()
    private val accentColor = style.getColor("accentColor", black)
    override fun getLocalColor(element: Transform, isHovered: Boolean, isInFocus: Boolean): Int {
        val dst = element.getLocalColor(tmp)
        dst.w = 0.5f + 0.5f * clamp(dst.w, 0f, 1f)
        var textColor = dst.toARGB()
        if (isHovered) textColor = sample.run { uiSymbol ?: text }.hoverColor
        if (isInFocus) textColor = accentColor
        return textColor
    }

    override fun getTooltipText(element: Transform): String? {
        return when (element) {
            is Camera -> element.defaultDisplayName + Dict[", drag onto scene to view", "ui.treeView.dragCameraToView"]
            is Video -> element.type.displayName.name
            is MeshTransform -> "Mesh" // todo translate this
            else -> element::class.simpleName?.camelCaseToTitle()
        }
    }

    override fun onPaste(x: Float, y: Float, data: String, type: String) {
        if (!tryPasteTransform(data)) {
            super.onPaste(x, y, data, type)
        }
    }

    /**
     * returns true on success
     * */
    private fun tryPasteTransform(data: String): Boolean {
        val transform = data.toTransform() ?: return false
        RemsStudio.largeChange("Pasted ${transform.name}") {
            RemsStudio.root.addChild(transform)
        }
        return true
    }

    override fun isValidElement(element: Any?) = element is Transform

    companion object {

        fun zoomToObject(obj: Transform): Boolean {
            // instead of asking for the name, move the camera towards the target
            // todo also zoom in/out correctly to match the object...
            // identify the currently used camera
            val camera = lastTouchedCamera ?: nullCamera ?: return false
            val time = RemsStudio.editorTime
            // calculate the movement, which would be necessary
            val cameraToWorld = camera.parent?.getGlobalTransform(time)
            val objectToWorld = obj.getGlobalTransform(time)
            val objectWorldPosition = objectToWorld.transformPosition(Vector3f(0f, 0f, 0f))

            @Suppress("IfThenToElvis")
            val objectCameraPosition = if (cameraToWorld == null) objectWorldPosition else
                cameraToWorld.invert().transformPosition(objectWorldPosition)
            LOGGER.info(objectCameraPosition)
            // apply this movement
            RemsStudio.largeChange("Move Camera to Object") {
                camera.position.addKeyframe(camera.lastLocalTime, objectCameraPosition)
            }
            return true
        }

        private val LOGGER = LogManager.getLogger(StudioTreeView::class)
        fun openAddMenu(baseTransform: Transform) {
            fun add(action: (Transform) -> Transform): () -> Unit = { Selection.selectTransform(action(baseTransform)) }
            val options = DefaultConfig["createNewInstancesList"] as? StringMap
            if (options != null) {
                val extras = ArrayList<MenuOption>()
                if (baseTransform.parent != null) {
                    extras += Menu.menuSeparator1
                    extras += MenuOption(
                        NameDesc(
                            "Add Mask",
                            "Creates a mask component, which can be used for many effects",
                            "ui.objects.addMask"
                        )
                    ) {
                        val parent = baseTransform.parent!!
                        val i = parent.children.indexOf(baseTransform)
                        if (i < 0) throw RuntimeException()
                        val mask = MaskLayer.create(listOf(Rectangle.create()), listOf(baseTransform))
                        mask.isFullscreen = true
                        parent.setChildAt(mask, i)
                    }
                }
                val additional = baseTransform.getAdditionalChildrenOptions().map { option ->
                    MenuOption(option.nameDesc) {
                        RemsStudio.largeChange("Added ${option.nameDesc.name}") {
                            val new = option.generator() as Transform
                            baseTransform.addChild(new)
                            Selection.selectTransform(new)
                        }
                    }
                }
                if (additional.isNotEmpty()) {
                    extras += Menu.menuSeparator1
                    extras += additional
                }
                val ws = defaultWindowStack
                Menu.openMenu(
                    defaultWindowStack,
                    ws.mouseX, ws.mouseY, NameDesc("Add Child", "", "ui.objects.add"),
                    options.entries
                        .sortedBy { (key, _) -> key.lowercase(Locale.getDefault()) }
                        .map { (key, value) ->
                            val sample = if (value is Transform) value.clone() else value.toString().toTransform()
                            MenuOption(NameDesc(key, sample?.defaultDisplayName ?: "", ""), add {
                                val newT = if (value is Transform) value.clone() else value.toString().toTransform()
                                newT!!
                                it.addChild(newT)
                                newT
                            })
                        } + extras
                )
            } else LOGGER.warn(Dict["Reset the config to enable this menu!", "config.warn.needsReset.forMenu"])
        }
    }

    override fun moveChange(run: () -> Unit) {
        RemsStudio.largeChange("Moved Component", run)
    }

    override fun addChild(element: Transform, child: Any, type: Char, index: Int): Boolean {
        element.addChild(index, child as Transform)
        return true
    }

    private val fontColor = style.getColor("textColor", DefaultStyle.fontGray)
    override fun drawBackground(x0: Int, y0: Int, x1: Int, y1: Int, dx: Int, dy: Int) {
        super.drawBackground(x0, y0, x1, y1, dx, dy)
        drawTypeInCorner("Tree", fontColor)
    }

    override val className get() = "StudioTreeView"
}