package me.anno.remsstudio.utils

import me.anno.io.files.FileReference
import me.anno.io.files.FileReference.Companion.getReference
import me.anno.utils.OS.documents
import me.anno.utils.types.Strings.addPrefix
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

fun main() {

    val folder = getReference(documents, "IdeaProjects\\RemsStudio\\out\\artifacts\\Mod")
    val src = folder.getChild("RemsStudio.jar")

    val dst = folder.getChild("RemsStudio-clear.jar")
    val zos = ZipOutputStream(dst.outputStream())
    val blacklist = listOf(
        "defaultLUT.png",
        "coldLUT.png",
        "checked.png",
        "unchecked.png",
        "cross.png",
        "icon.ico",
        "icon.obj",
        "icon.png",
        "icon_old.png",
        "sepiaLUT.png",
        "rem redbubble 2.jpg",
        "rem sakamileo deviantart.png",
        "rem nikonokao redbubble.png",
        "rem-face-500.png",
        "rem-face-342.png",
        "lang",
        "file",
        "shader",
        "mesh"
    )

    fun add(file: FileReference, path: String) {
        if (path !in blacklist) {
            if (file.isDirectory) {
                for (child in file.listChildren() ?: emptyList()) {
                    add(child, addPrefix(path, "/", child.name))
                }
            } else {
                zos.putNextEntry(ZipEntry(path))
                zos.write(file.readBytes())
                zos.closeEntry()
            }
        }
    }
    for (file in src.listChildren()!!) {
        add(file, file.name)
    }
    zos.close()
}