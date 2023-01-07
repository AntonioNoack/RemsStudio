package me.anno.remsstudio.ui

import me.anno.config.DefaultConfig
import me.anno.config.DefaultConfig.style
import me.anno.gpu.drawing.DrawTexts.getTextSizeX
import me.anno.io.files.InvalidRef
import me.anno.io.text.TextWriter
import me.anno.io.utils.StringMap
import me.anno.language.translation.Dict
import me.anno.language.translation.NameDesc
import me.anno.maths.Maths.clamp
import me.anno.remsstudio.RemsStudio
import me.anno.remsstudio.RemsStudio.defaultWindowStack
import me.anno.remsstudio.RemsStudio.lastTouchedCamera
import me.anno.remsstudio.RemsStudio.nullCamera
import me.anno.remsstudio.Selection
import me.anno.remsstudio.objects.Camera
import me.anno.remsstudio.objects.Rectangle
import me.anno.remsstudio.objects.Transform
import me.anno.remsstudio.objects.Transform.Companion.toTransform
import me.anno.remsstudio.objects.effects.MaskLayer
import me.anno.ui.base.Font
import me.anno.ui.base.menu.Menu
import me.anno.ui.base.menu.MenuOption
import me.anno.ui.base.text.TextPanel
import me.anno.ui.editor.treeView.TreeView
import me.anno.ui.editor.treeView.TreeViewPanel
import me.anno.ui.style.Style
import me.anno.utils.Color.black
import me.anno.utils.Color.toARGB
import me.anno.utils.structures.lists.UpdatingList
import me.anno.utils.types.Strings.joinChars
import org.apache.logging.log4j.LogManager
import org.joml.Vector3f
import org.joml.Vector4f
import java.util.*
import kotlin.streams.toList

class StudioTreeView(style: Style) :
    TreeView<Transform>(
        UpdatingList {
            val nc = nullCamera
            if (nc == null) listOf(RemsStudio.root)
            else listOf(nc, RemsStudio.root)
        },
        StudioFileImporter, true, style
    ) {

    override fun getDragType(element: Transform): String = "Transform"

    override fun stringifyForCopy(element: Transform): String = TextWriter.toText(element, InvalidRef)

    override fun getSymbol(element: Transform): String {
        return element.symbol
    }

    override fun removeChild(parent: Transform, child: Transform) {
        parent.removeChild(child)
    }

    override fun setCollapsed(element: Transform, collapsed: Boolean) {
        element.isCollapsedI.value = collapsed
    }

    override fun isCollapsed(element: Transform): Boolean {
        return element.isCollapsed
    }

    override fun setName(element: Transform, name: String) {
        element.nameI.value = name
    }

    override fun getName(element: Transform): String {
        return element.name.ifBlank { element.defaultDisplayName }
    }

    override fun getParent(element: Transform): Transform? {
        return element.parent
    }

    override fun getChildren(element: Transform): List<Transform> {
        return element.children
    }

    override fun destroy(element: Transform) {
        element.onDestroy()
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

    override fun selectElement(element: Transform?) {
        Selection.selectTransform(element)
    }

    override fun selectElementMaybe(element: Transform?) {
        // if already selected, don't inspect that property/driver
        if (Selection.selectedTransform == element &&
            (Selection.selectedProperty != null ||
                    (Selection.selectedInspectable != null && Selection.selectedInspectable != element))
        ) {
            Selection.clear()
        } else selectElement(element)
    }

    override fun focusOnElement(element: Transform) {
        zoomToObject(element)
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
        return if (element is Camera) {
            element.defaultDisplayName + Dict[", drag onto scene to view", "ui.treeView.dragCameraToView"]
        } else element::class.simpleName
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

    /*override fun onDeleteKey(x: Float, y: Float) {
        val panel = list.children.firstOrNull { it.contains(x, y) }
        if (panel is TreeViewPanel<*>) {
            val element = panel.getElement() as Transform
            val parent = getParent(element)
            if (parent != null) {
                RemsStudio.largeChange("Deleted Component ${getName(element)}") {
                    removeChild(parent, element)
                    for (it in element.listOfAll.toList()) destroy(it)
                }
            }
        }
    }*/

    override fun isValidElement(element: Any?): Boolean {
        return element is Transform
    }

    override fun toggleCollapsed(element: Transform) {
        val name = getName(element)
        val isCollapsed = isCollapsed(element)
        RemsStudio.largeChange(if (isCollapsed) "Expanded $name" else "Collapsed $name") {
            val target = !isCollapsed
            // remove children from the selection???...
            val targets = windowStack.inFocus.filterIsInstance<TreeViewPanel<*>>()
            for (it in targets) {
                @Suppress("unchecked_cast")
                val element2 = it.getElement() as Transform
                setCollapsed(element2, target)
            }
            if (targets.isEmpty()) {
                setCollapsed(element, target)
            }
        }
    }

    companion object {

        @JvmStatic
        fun main(args: Array<String>) {
            // test for layout width bugs
            // works now :)
            val s = 1024
            println("\uD83C\uDF9E️" == "\uD83C\uDF9E")
            val font0 = Font("Verdana", 15)
            val font1 = Font("Segoe UI Emoji", 15)
            println(listOf(127902).joinChars())
            println(listOf(127902).joinChars().codePoints().toList())
            println(getTextSizeX(font0, listOf(127902).joinChars(), s, s))
            println(getTextSizeX(font1, listOf(127902).joinChars(), s, s))
            println(getTextSizeX(font0, listOf(65039).joinChars(), s, s))
            println(getTextSizeX(font1, listOf(65039).joinChars(), s, s))
            println("\uD83C\uDF9E️")
            println("\uD83C\uDF9E️".codePoints().toList())
            println("xx " + listOf(127902).joinChars().length)
            println("xx " + listOf(65039).joinChars().length)
            println("xx " + "\uD83C\uDF9E️".length + "\uD83C\uDF9E".length + " xx " + "\uD83C\uDF9E️".codePoints().toList().joinChars().length)
            println("xx " + "\uD83C\uDF9E️".codePoints().toList().joinChars().codePoints().toList())
            println(getTextSizeX(font0, "\uD83C\uDF9E️", s, s))
            println(getTextSizeX(font1, "\uD83C\uDF9E️", s, s))
            println("\uD83C\uDFA5️")
            println("\uD83C\uDFA5️".codePoints().toList())
            println(getTextSizeX(font0, "\uD83C\uDFA5️", s, s))
            println(getTextSizeX(font1, "\uD83C\uDFA5️", s, s))
            println(getTextSizeX(font0, "\uD83C\uDFA5", s, s))
            println(getTextSizeX(font1, "\uD83C\uDFA5", s, s))
            val tp = TextPanel("\uD83C\uDFA5️", style)
            println(tp.font)
        }

        fun zoomToObject(obj: Transform) {
            // instead of asking for the name, move the camera towards the target
            // todo also zoom in/out correctly to match the object...
            // identify the currently used camera
            val camera = lastTouchedCamera ?: nullCamera ?: return
            val time = RemsStudio.editorTime
            // calculate the movement, which would be necessary
            val cameraToWorld = camera.parent?.getGlobalTransform(time)
            val objectToWorld = obj.getGlobalTransform(time)
            val objectWorldPosition = objectToWorld.transformPosition(Vector3f(0f, 0f, 0f))
            val objectCameraPosition = if (cameraToWorld == null) objectWorldPosition else cameraToWorld.invert()
                .transformPosition(objectWorldPosition)
            LOGGER.info(objectCameraPosition)
            // apply this movement
            RemsStudio.largeChange("Move Camera to Object") {
                camera.position.addKeyframe(camera.lastLocalTime, objectCameraPosition)
            }
            /* askName(this.x, this.y, NameDesc(), getElement().name, NameDesc("Change Name"), { textColor }) {
                 getElement().name = it
             }*/
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
                    MenuOption(NameDesc(option.title, option.description, "")) {
                        RemsStudio.largeChange("Added ${option.title}") {
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

    override val className get() = "StudioTreeView"
    override fun getIndexInParent(parent: Transform, child: Transform): Int {
        return child.indexInParent
    }

    override fun addChild(element: Transform, child: Any, index: Int) {
        element.addChild(index, child as Transform)
    }

}