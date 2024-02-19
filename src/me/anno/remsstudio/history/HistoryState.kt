package me.anno.remsstudio.history

import me.anno.io.Saveable
import me.anno.io.base.BaseWriter
import me.anno.remsstudio.RemsStudio
import me.anno.remsstudio.RemsStudio.defaultWindowStack
import me.anno.remsstudio.RemsStudio.nullCamera
import me.anno.remsstudio.Selection
import me.anno.remsstudio.Selection.select
import me.anno.remsstudio.objects.Camera
import me.anno.remsstudio.objects.Transform
import me.anno.remsstudio.ui.scene.StudioSceneView
import me.anno.remsstudio.ui.sceneTabs.SceneTabs
import me.anno.ui.Panel
import me.anno.ui.base.groups.PanelGroup
import me.anno.ui.editor.PropertyInspector.Companion.invalidateUI
import me.anno.utils.types.AnyToInt

class HistoryState() : Saveable() {

    constructor(title: String, code: Any) : this() {
        this.title = title
        this.code = code
    }

    var title = ""
    var code: Any? = null

    var root: Transform? = null
    var selectedUUIDs: List<Int> = emptyList()
    var selectedPropName: String? = null
    var usedCameras = IntArray(0)
    var editorTime = 0.0

    override fun hashCode(): Int {
        var result = root.toString().hashCode()
        result = 31 * result + selectedUUIDs.hashCode()
        result = 31 * result + usedCameras.contentHashCode()
        result = 31 * result + editorTime.hashCode()
        return result
    }

    override fun equals(other: Any?): Boolean {
        return other is HistoryState &&
                other.selectedUUIDs == selectedUUIDs &&
                other.root.toString() == root.toString() &&
                other.usedCameras.contentEquals(usedCameras) &&
                other.editorTime == editorTime
    }

    fun Transform.getRoot(): Transform = this.parent?.getRoot() ?: this
    fun Transform.getUUID() = this.getRoot().listOfAll.indexOf(this)

    fun apply() {
        val root = root ?: return
        RemsStudio.root = root
        SceneTabs.currentTab?.scene = root
        RemsStudio.editorTime = editorTime
        val listOfAll = root.listOfAll.toList()
        select(selectedUUIDs, selectedPropName)
        for (window in defaultWindowStack) {
            var index = 0
            window.panel.forAll {
                if (it is StudioSceneView) {
                    it.camera = if (index in usedCameras.indices) {
                        val cameraIndex = usedCameras[index]
                        listOfAll.firstOrNull { camera -> camera.getUUID() == cameraIndex } as? Camera ?: nullCamera!!
                    } else {
                        nullCamera!!
                    }
                    index++
                }
            }
        }
        invalidateUI(true)
    }

    fun capture(previous: HistoryState?) {

        val state = this
        state.editorTime = RemsStudio.editorTime

        if (previous?.root?.toString() != RemsStudio.root.toString()) {
            // create a clone, if it was changed
            state.root = RemsStudio.root.clone()
        } else {
            // else, just reuse it; this is more memory and storage friendly
            state.root = previous.root
        }

        state.title = title
        state.selectedUUIDs = Selection.selectedTransforms.map { it.getUUID() }
        state.usedCameras = defaultWindowStack.map { window ->
            asyncSequenceOfAll(window.panel)
                .filterIsInstance<StudioSceneView>()
                .map { it.camera.getUUID() }.toList()
        }.flatten().toIntArray()

    }

    private fun asyncSequenceOfAll(panel: Panel): Sequence<Panel> {
        return sequence {
            yield(panel)
            if (panel is PanelGroup) {
                val children = panel.children
                for (i in children.indices) {
                    val child = children.getOrNull(i) ?: break
                    yieldAll(asyncSequenceOfAll(child))
                }
            }
        }
    }

    companion object {
        fun capture(title: String, code: Any, previous: HistoryState?): HistoryState {
            val state = HistoryState(title, code)
            state.capture(previous)
            return state
        }
    }

    override fun save(writer: BaseWriter) {
        super.save(writer)
        writer.writeObject(this, "root", root)
        writer.writeString("title", title)
        writer.writeIntArray("selectedUUIDs", selectedUUIDs.toIntArray())
        writer.writeIntArray("usedCameras", usedCameras)
        writer.writeDouble("editorTime", editorTime)
    }

    override fun setProperty(name: String, value: Any?) {
        when (name) {
            "title" -> title = value as? String ?: return
            "editorTime" -> editorTime = value as? Double ?: return
            "selectedUUID" -> selectedUUIDs = listOf(AnyToInt.getInt(value, 0))
            "usedCameras" -> usedCameras =
                value as? IntArray ?: (value as? LongArray)?.map { it.toInt() }?.toIntArray() ?: return
            "selectedUUIDs" -> selectedUUIDs = (value as? IntArray)?.toList() ?: return
            "root" -> root = value as? Transform ?: return
            else -> super.setProperty(name, value)
        }
    }

    override val className get() = "HistoryState"
    override val approxSize get() = 1_000_000_000
    override fun isDefaultValue() = false
}