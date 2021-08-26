package me.anno.ui.input

import me.anno.config.DefaultConfig
import me.anno.input.MouseButton
import me.anno.io.files.FileFileRef
import me.anno.io.files.FileReference
import me.anno.io.files.FileReference.Companion.getReference
import me.anno.io.files.InvalidRef
import me.anno.ui.base.SpacePanel
import me.anno.ui.base.buttons.TextButton
import me.anno.ui.base.constraints.WrapAlign
import me.anno.ui.base.groups.PanelListX
import me.anno.ui.base.menu.Menu.openMenu
import me.anno.ui.base.menu.MenuOption
import me.anno.ui.editor.files.FileExplorer.Companion.openInExplorerDesc
import me.anno.ui.style.Style
import me.anno.utils.hpc.Threads.threadWithName
import me.anno.utils.files.FileExplorerSelectWrapper
import me.anno.utils.files.LocalFile.toGlobalFile
import me.anno.utils.files.LocalFile.toLocalPath
import java.io.File

class FileInput(
    title: String, style: Style, f0: FileReference, val isDirectory: Boolean = false
) : PanelListX(style) {

    val button = TextButton(DefaultConfig["ui.symbol.folder", "\uD83D\uDCC1"], true, style)
    val base = TextInput(title, "", false, f0.toString2(), style)
    val base2 = base.base

    // val text get() = base.text

    init {
        setTooltip(title)
        base.apply {
            this += WrapAlign.LeftCenter
            setChangeListener {
                this@FileInput.changeListener(it.toGlobalFile())
            }
        }
        button.apply {
            setSimpleClickListener {
                threadWithName("SelectFile/Folder") {
                    var file2 = file
                    while (file2 != InvalidRef && !file2.exists) {
                        val file3 = file2.getParent() ?: InvalidRef
                        if (file3 == InvalidRef || file3 == file2 || file3.exists) {
                            file2 = file3
                            break
                        } else {
                            file2 = file3
                        }
                    }
                    val file3 = if (file2 == InvalidRef) null else (file2 as? FileFileRef)?.file
                    // todo select the file using our own explorer (?), because ours may be better
                    FileExplorerSelectWrapper.selectFileOrFolder(file3, isDirectory) { file ->
                        if (file != null) {
                            changeListener(getReference(file))
                            base.setValue(file.toString2(), false)
                        }
                    }
                }
            }
            setTooltip("Select the file in your default file explorer")
            textColor = textColor and 0x7fffffff
            focusTextColor = textColor
        }
        this += button
        // for a symmetric border
        val border = style.getPadding("borderSize", 2).left
        if (border > 0) this += SpacePanel(border, 0, style).apply { backgroundColor = 0 }
        this += base//ScrollPanelX(base, Padding(), style, AxisAlignment.MIN)
    }

    private fun File.toString2() = toLocalPath()
    private fun FileReference.toString2() = toLocalPath()
    // toString().replace('\\', '/') // / is easier to type

    val file get(): FileReference = base.text.toGlobalFile()

    var changeListener = { _: FileReference -> }
    fun setChangeListener(listener: (FileReference) -> Unit): FileInput {
        this.changeListener = listener
        return this
    }

    fun setResetListener(listener: () -> FileReference): FileInput {
        base.setResetListener { listener().toLocalPath() }
        return this
    }

    fun setText(text: String, notify: Boolean) {
        base.setValue(text.replace('\\', '/'), notify)
    }

    override fun onMouseClicked(x: Float, y: Float, button: MouseButton, long: Boolean) {
        when {
            button.isRight -> openMenu(listOf(MenuOption(openInExplorerDesc) { file.openInExplorer() }))
            else -> super.onMouseClicked(x, y, button, long)
        }
    }

    fun setIsSelectedListener(listener: () -> Unit): FileInput {
        base.setIsSelectedListener(listener)
        return this
    }

}