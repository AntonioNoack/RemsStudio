package test

import me.anno.io.binary.BinaryReader
import me.anno.io.binary.BinaryWriter
import me.anno.io.files.InvalidRef
import me.anno.io.json.saveable.JsonStringWriter
import me.anno.remsstudio.objects.Camera
import me.anno.remsstudio.objects.Transform
import me.anno.remsstudio.objects.Video
import me.anno.remsstudio.objects.geometric.Circle
import me.anno.remsstudio.objects.geometric.Polygon
import me.anno.remsstudio.objects.particles.ParticleSystem
import me.anno.remsstudio.objects.text.Text
import me.anno.utils.OS
import org.apache.logging.log4j.LogManager
import java.io.*
import java.util.zip.DeflaterOutputStream

fun main() {

    val logger = LogManager.getLogger("TestIO")

    val candidates = listOf(
        Video(),
        Text(),
        ParticleSystem(),
        Transform(),
        Camera(),
        Polygon(),
        Video(),
        Text(),
        ParticleSystem(),
        Transform(),
        Camera(),
        Polygon(),
        Transform(),
        Text(),
        ParticleSystem(),
        Transform(),
        Camera(),
        Polygon(),
        Video(),
        Text(),
        ParticleSystem(),
        Transform(),
        Camera(),
        Polygon(),
        Circle()
    )

    // load all files into the cache
    candidates.forEach { it.save(BinaryWriter(DataOutputStream(ByteArrayOutputStream()))) }

    val file = OS.desktop.getChild("raw.bin")

    // binary
    val bin0 = System.nanoTime()
    var bos: ByteArrayOutputStream
    lateinit var binaryValue: ByteArray
    for (i in 0 until 100) {
        bos = ByteArrayOutputStream(4096)
        DataOutputStream(bos).use { dos ->
            val writer = BinaryWriter(dos)
            candidates.forEach { writer.add(it) }
            writer.writeAllInList()
        }
        binaryValue = bos.toByteArray()
    }
    val binaryWriteTime = System.nanoTime() - bin0

    file.writeBytes(binaryValue)

    // load all files into the cache
    candidates.forEach { it.save(JsonStringWriter(InvalidRef)) }

    // text
    val text0 = System.nanoTime()
    lateinit var textValue: String
    for (i in 0 until 100) {
        val writer = JsonStringWriter(InvalidRef)
        candidates.forEach { writer.add(it) }
        writer.writeAllInList()
        textValue = writer.toString()
    }
    val textWriteTime = System.nanoTime() - text0

    logger.info("text write time: ${textWriteTime / 1e9}")
    logger.info("binary write time: ${binaryWriteTime / 1e9}")

    logger.info("length as text: ${textValue.length}")
    logger.info("length in binary: ${binaryValue.size}")

    logger.info("length text, compressed: ${compress(textValue.toByteArray())}")
    logger.info("length binary, compressed: ${compress(binaryValue)}")

    DataInputStream(ByteArrayInputStream(binaryValue)).use { dis ->
        val reader = BinaryReader(dis)
        reader.readAllInList()
        val content = reader.sortedContent
        for (c in content) logger.info(c.className)
    }

}

fun compress(bytes: ByteArray): Int {
    val out0 = ByteArrayOutputStream()
    val out = DeflaterOutputStream(out0)
    out.write(bytes)
    out.finish()
    return out0.toByteArray().size
}