package test

import me.anno.remsstudio.RemsStudio
import me.anno.utils.OS

fun main() {
    val project = OS.documents.getChild("RemsStudio/Simple Image")
    val argsList = ArrayList<String>()
    argsList += "-y"
    argsList += listOf("-w", "1920")
    argsList += listOf("-h", "1080")
    argsList += listOf("-i", project.getChild("scenes/Root.json").toString())
    argsList += listOf("-o", project.getChild("output.mp4").toString())
    RemsStudio.main(argsList.toTypedArray())
}