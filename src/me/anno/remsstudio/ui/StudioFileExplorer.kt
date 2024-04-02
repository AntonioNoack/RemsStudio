package me.anno.remsstudio.ui

import me.anno.config.DefaultStyle
import me.anno.io.files.FileReference
import me.anno.io.files.Reference.getReference
import me.anno.remsstudio.objects.Transform.Companion.toTransform
import me.anno.remsstudio.ui.MenuUtils.drawTypeInCorner
import me.anno.remsstudio.ui.sceneTabs.SceneTabs
import me.anno.ui.Style
import me.anno.ui.editor.files.FileExplorer
import me.anno.ui.editor.files.FileExplorerOption
import me.anno.ui.editor.files.FileNames.toAllowedFilename
import me.anno.utils.files.Files

@Suppress("MemberVisibilityCanBePrivate")
class StudioFileExplorer(file: FileReference?, style: Style) : FileExplorer(file, true, style) {

    override fun getFolderOptions(): List<FileExplorerOption> {
        return super.getFolderOptions() + listOf(StudioUITypeLibrary.createTransform)
    }

    override fun onDoubleClick(file: FileReference) {
        SceneTabs.open(file)
    }

    override fun onPaste(x: Float, y: Float, data: String, type: String) {
        when (type) {
            "Transform" -> pasteTransform(data)
            else -> {
                if (!pasteTransform(data)) {
                    if (data.length < 2048) {
                        val ref = getReference(data)
                        if (ref.exists) {
                            switchTo(ref)
                        }// else super.onPaste(x, y, data, type)
                    }// else super.onPaste(x, y, data, type)
                }
            }
        }
    }

    fun pasteTransform(data: String): Boolean {
        val transform = data.toTransform() ?: return false
        var name = transform.name.toAllowedFilename() ?: transform.defaultDisplayName
        // make .json lowercase
        if (name.endsWith(".json", true)) {
            name = name.substring(0, name.length - 5)
        }
        name += ".json"
        Files.findNextFile(folder.getChild(name), 1, '-').writeText(data)
        invalidate()
        return true
    }

    override val canDrawOverBorders: Boolean
        get() = true

    private val fontColor = style.getColor("textColor", DefaultStyle.fontGray)
    override fun drawBackground(x0: Int, y0: Int, x1: Int, y1: Int, dx: Int, dy: Int) {
        super.drawBackground(x0, y0, x1, y1, dx, dy)
        drawTypeInCorner("Files", fontColor)
    }

    override val className get() = "StudioFileExplorer"
}