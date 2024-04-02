package test

import me.anno.io.json.saveable.JsonStringReader
import me.anno.remsstudio.RemsRegistry
import me.anno.utils.OS.documents

fun main() {
    RemsRegistry.init()
    val path = documents.getChild("RemsStudio\\New Project2\\Scenes\\Root.json")
    val input = path.inputStreamSync()
    val instances = JsonStringReader.read(input, path.getParent(), false)
}