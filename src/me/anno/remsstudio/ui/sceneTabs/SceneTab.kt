package me.anno.remsstudio.ui.sceneTabs

import me.anno.cache.ThreadPool
import me.anno.config.DefaultConfig
import me.anno.engine.EngineBase.Companion.dragged
import me.anno.engine.EngineBase.Companion.workspace
import me.anno.gpu.GFX
import me.anno.input.ActionManager
import me.anno.input.Key
import me.anno.io.files.FileReference
import me.anno.io.files.InvalidRef
import me.anno.io.json.saveable.JsonStringReader
import me.anno.io.json.saveable.JsonStringWriter
import me.anno.language.translation.NameDesc
import me.anno.remsstudio.RemsStudio.project
import me.anno.remsstudio.history.History
import me.anno.remsstudio.objects.Transform
import me.anno.remsstudio.ui.scene.SceneTabData
import me.anno.remsstudio.ui.sceneTabs.SceneTabs.currentTab
import me.anno.remsstudio.ui.sceneTabs.SceneTabs.open
import me.anno.ui.base.menu.Menu.ask
import me.anno.ui.base.menu.Menu.msg
import me.anno.ui.base.menu.Menu.openMenu
import me.anno.ui.base.menu.MenuOption
import me.anno.ui.base.text.TextPanel
import me.anno.ui.dragging.Draggable
import me.anno.ui.editor.files.FileExplorer
import me.anno.ui.editor.files.FileNames.toAllowedFilename
import me.anno.utils.Color.mixARGB
import org.apache.logging.log4j.LogManager

@Suppress("MemberVisibilityCanBePrivate")
class SceneTab(var file: FileReference, var scene: Transform, history: History?) : TextPanel("", DefaultConfig.style) {

    companion object {
        const val maxDisplayNameLength = 15
        private val LOGGER = LogManager.getLogger(SceneTab::class)
    }

    var history = history ?: try {
        if (file == InvalidRef) null // todo find project for file
        else JsonStringReader.readFirstOrNull(file, workspace, History::class).waitFor()
    } catch (_: Exception) {
        null
    } ?: History()

    private val longName get() = file.nullIfUndefined()?.name ?: scene.name
    private val shortName
        get() = longName.run {
            if (length > maxDisplayNameLength) {
                substring(0, maxDisplayNameLength - 3) + "..."
            } else this
        }

    init {
        text = shortName
        tooltip = longName
    }

    var hasChanged: Boolean = false
        set(value) {
            if (field != value) {
                val baseName = shortName
                val newText = if (value) "$baseName*" else baseName
                // RuntimeException("[SceneTab] $text -> $newText").printStackTrace()
                text = newText
                tooltip = longName
                field = value
            }
        }

    init {
        padding.top--
        padding.bottom--
        addLeftClickListener { open(this) }
        addRightClickListener {
            if (hasChanged) {
                openMenu(
                    windowStack, listOf(
                        MenuOption(NameDesc("Save", "", "ui.sceneTab.saved")) { save { } },
                        MenuOption(NameDesc("Close", "", "ui.sceneTab.closeSaved")) { save { close() } },
                        MenuOption(NameDesc("Close (Unsaved)", "", "ui.sceneTab.closeUnsaved")) { close() }
                    ))
            } else {
                openMenu(
                    windowStack, listOf(
                        MenuOption(NameDesc("Save", "", "ui.sceneTab.saved")) { save { } },
                        MenuOption(NameDesc("Close", "", "ui.sceneTab.close")) { close() }
                    ))
            }
        }
    }

    fun close() = SceneTabs.close(this)

    private val bg get() = background.originalColor
    private val bgLight = mixARGB(bg, 0xff777777.toInt(), 0.5f)
    override fun draw(x0: Int, y0: Int, x1: Int, y1: Int) {
        background.color = if (this === currentTab) bgLight else bg
        super.draw(x0, y0, x1, y1)
    }

    fun save(dst: FileReference, onSuccess: () -> Unit) {
        if (dst.isDirectory) dst.delete()
        LOGGER.info("Saving $dst, ${scene.listOfAll.joinToString { it.name }}")
        ThreadPool.start("SaveScene") {
            try {
                synchronized(scene) {
                    dst.getParent().mkdirs()
                    JsonStringWriter.save(listOf(scene, history), dst, workspace)
                    file = dst
                    hasChanged = false
                    LOGGER.info("Saved!")
                    onSuccess()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun save(onSuccess: () -> Unit) {
        if (file == InvalidRef) {
            var name = scene.name.trim()
            if (!name.endsWith(".json", true)) name = "$name.json"
            val name0 = name
            // todo replace /,\?,..
            name = name.toAllowedFilename() ?: ""
            if (name.isEmpty()) {
                val project = project ?: throw IllegalStateException("Missing project")
                val dst = project.scenes.getChild(name)
                if (dst.exists) {
                    ask(
                        windowStack,
                        NameDesc("Override %1?", "Replaces the old file", "ui.file.override")
                            .with("%1", dst.name)
                    ) {
                        file = dst
                        save(file, onSuccess)
                    }
                } else {
                    file = dst
                    save(file, onSuccess)
                    rootPanel.forAll { if (it is FileExplorer) it.invalidate() }
                }
            } else {
                msg(
                    windowStack,
                    NameDesc("'%1' is no valid file name, rename it!", "", "ui.file.invalidName")
                        .with("%1", name0)
                )
            }
        } else {
            save(file, onSuccess)
        }
    }

    override fun onGotAction(x: Float, y: Float, dx: Float, dy: Float, action: String, isContinuous: Boolean): Boolean {
        when (action) {
            "DragStart" -> {
                if (dragged?.getOriginal() != this) {
                    dragged = Draggable(
                        SceneTabData(this).toString(), "SceneTab", this,
                        TextPanel(shortName, style)
                    )
                }
            }
            else -> return super.onGotAction(x, y, dx, dy, action, isContinuous)
        }
        return true
    }

    override fun onKeyUp(x: Float, y: Float, key: Key) {
        if (key == Key.BUTTON_LEFT) ActionManager.executeGlobally(GFX.someWindow, 0f, 0f, false, listOf("DragEnd"))
        else super.onKeyUp(x, y, key)
    }

    override fun onPaste(x: Float, y: Float, data: String, type: String) {
        SceneTabs.onPaste(x, y, data, type)
    }
}
