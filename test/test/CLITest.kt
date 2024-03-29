package test

import me.anno.remsstudio.RemsStudio
import me.anno.utils.OS

fun main() {
    val argsList = ArrayList<String>()
    argsList += "-y"
    argsList += listOf("-w", "512")
    argsList += listOf("-h", "512")
    argsList += listOf("-i", OS.documents.getChild("RemsStudio/Audio Tests/scenes/root.json").toString())
    argsList += listOf("-o", OS.desktop.getChild("output.mp4").toString())
    RemsStudio.main(argsList.toTypedArray())
}