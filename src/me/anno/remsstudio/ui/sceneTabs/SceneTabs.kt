package me.anno.remsstudio.ui.sceneTabs

import me.anno.config.DefaultConfig
import me.anno.engine.EngineBase.Companion.dragged
import me.anno.engine.Events.addEvent
import me.anno.io.files.FileReference
import me.anno.language.translation.Dict
import me.anno.remsstudio.RemsStudio
import me.anno.remsstudio.ui.StudioFileImporter.addChildFromFile
import me.anno.ui.base.groups.PanelList
import me.anno.ui.base.scrolling.ScrollPanelX
import me.anno.ui.editor.files.FileContentImporter
import me.anno.utils.structures.lists.Lists.getOrPrevious
import org.apache.logging.log4j.LogManager

// may there only be once instance? yes
object SceneTabs : ScrollPanelX(DefaultConfig.style) {

    private val LOGGER = LogManager.getLogger(SceneTabs::class)

    val content = child as PanelList
    val panelChildren = content.children
    val sceneTabs get() = panelChildren.filterIsInstance<SceneTab>()

    var currentTab: SceneTab? = null

    fun open(file: FileReference) {
        val opened = sceneTabs.firstOrNull { it.file == file }
        if (opened != null) {
            open(opened)
        } else {
            addEvent {
                addChildFromFile(null, file, FileContentImporter.SoftLinkMode.COPY_CONTENT, false) { transform ->
                    var file2 = file
                    if (file2.lcExtension != "json") {
                        file2 = file2.getParent().getChild(file2.name + ".json")
                    }
                    val tab = SceneTab(file2, transform, null)
                    content += tab
                    open(tab)
                }
            }
        }
    }

    override fun onPaste(x: Float, y: Float, data: String, type: String) {
        when (type) {
            "SceneTab" -> {
                val tab = dragged?.getOriginal() as? SceneTab ?: return
                if (!tab.contains(x, y)) {
                    val oldIndex = tab.indexInParent
                    val newIndex = panelChildren.map { it.x + it.width / 2 }.count { it < x }
                    // LOGGER.info("$oldIndex -> $newIndex, $x ${children2.map { it.x + it.w/2 }}")
                    if (oldIndex < newIndex) {
                        panelChildren.add(newIndex, tab)
                        panelChildren.removeAt(oldIndex)
                    } else if (oldIndex > newIndex) {
                        panelChildren.removeAt(oldIndex)
                        panelChildren.add(newIndex, tab)
                    }
                    invalidateLayout()
                }// else done
                dragged = null
            }
            else -> super.onPaste(x, y, data, type)
        }
    }

    fun open(sceneTab: SceneTab) {
        if (currentTab == sceneTab) return
        synchronized(this) {
            currentTab = sceneTab
            RemsStudio.root = sceneTab.scene
            if (sceneTab !in sceneTabs) {
                content += sceneTab
            }
        }
    }

    fun close(sceneTab: SceneTab) {
        if (currentTab === sceneTab) {
            if (panelChildren.size == 1) {
                LOGGER.warn(Dict["Cannot close last element", "ui.sceneTabs.cannotCloseLast"])
            } else {
                val index = sceneTab.indexInParent
                sceneTab.removeFromParent()
                open(panelChildren.getOrPrevious(index) as SceneTab)
            }
        } else sceneTab.removeFromParent()
    }

    fun closeAll() {
        panelChildren.clear()
    }
}