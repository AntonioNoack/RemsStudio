package test

import me.anno.io.saveable.Saveable.Companion.registerCustomClass
import me.anno.io.files.InvalidRef
import me.anno.io.json.saveable.JsonStringReader
import me.anno.remsstudio.animation.AnimatedProperty
import me.anno.remsstudio.animation.Keyframe
import me.anno.remsstudio.objects.text.Text
import org.apache.logging.log4j.LogManager
import org.joml.Vector4f

fun main() {

    val logger = LogManager.getLogger("Interpolation")

    registerCustomClass(Text())
    registerCustomClass(Keyframe<Int>())
    registerCustomClass(AnimatedProperty.any())

    // fixed :)
    val data = "[{\"class\":\"Text\",\"AnimatedProperty:color\":{\"b:isAnimated\":true,\"Keyframe[]:vs\":[4," +
            "{\"d:time\":0.00,\"v4:value\":[0.9852581,0.49354506,0.81277037,0]}," +
            "{\"d:time\":0.25,\"v4:value\":[0.9852581,0.49354506,0.81277037,1]}," +
            "{\"d:time\":0.50,\"v4:value\":[0.9852581,0.49354506,0.81277037,1]}," +
            "{\"d:time\":1.00,\"v4:value\":[0.9852581,0.49354506,0.81277037,0]}]}}]"
    // val data = "[{\"class\":\"Text\",\"i:*ptr\":1,\"AnimatedProperty:startCursor\":{\"b:isAnimated\":true,\"Keyframe[]:vs\":[2,{\"d:time\":0,\"i:value\":-1},{\"d:time\":1,\"i:value\":74}]}}]"
    val element = JsonStringReader.read(data, InvalidRef, false).first() as Text

    val animation = element.color

    val steps = 10
    for (i in 0 until steps) {
        val f = i * 1.0 / steps
        val v = animation.getValueAt(f) as Vector4f
        logger.info("$f: $v")
    }

}