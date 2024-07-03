package test

import me.anno.io.files.InvalidRef
import me.anno.io.json.saveable.JsonStringReader
import me.anno.io.json.saveable.JsonStringWriter
import me.anno.remsstudio.objects.Transform
import me.anno.utils.Clock.Companion.measure
import org.apache.logging.log4j.LogManager

fun main() {
    for (i in 0 until 100) {
        test()
    }
}

fun test() {
    val logger = LogManager.getLogger("ReaderVsNative")
    // ratios aren't too great, but not too bad either
    val lines = 1_000_000
    val genName = { Math.random().toString() }
    // val readerFile = File.createTempFile("RemsStudio", ".tmp")
    val readerText = measure(logger, "Reader-create") {
        JsonStringWriter(InvalidRef).apply {
            writeStringList("strings", (0 until lines).map { genName() })
        }.toString()
    }
    // readerFile.writeText(readerText)
    // LOGGER.info(readerText)
    // val nativeFile = File.createTempFile("RemsStudio", ".tmp")
    val nativeText = (0 until lines).joinToString("\n") { genName() }
    // nativeFile.writeText(nativeText)
    /*measure("Reader") {//   423 ms for 1M
        TextReader(readerFile.readText()).readProperty(Transform())
    }*/
    measure(logger, "Reader-raw") {//   415 ms for 1M
        JsonStringReader(readerText, InvalidRef).readProperty(Transform())
    }
    /*measure("Native-1") {// 119 ms for 1M
        nativeFile.readLines()
    }*/
    /*measure("Native-2") {// 106 ms for 1M
        nativeFile.readText().split('\n')
    }*/
    measure(logger, "Native-2-raw") {// 52 ms for 1M
        nativeText.split('\n')
    }
    // readerFile.delete()
    // nativeFile.delete()
}