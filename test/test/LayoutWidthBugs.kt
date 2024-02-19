package test

import me.anno.config.DefaultConfig
import me.anno.fonts.Font
import me.anno.gpu.drawing.DrawTexts
import me.anno.ui.base.text.TextPanel
import me.anno.utils.types.Strings.joinChars
import kotlin.streams.toList

fun main() {
    // test for layout width bugs
    // works now :)
    val s = 1024
    println("\uD83C\uDF9E️" == "\uD83C\uDF9E")
    val font0 = Font("Verdana", 15)
    val font1 = Font("Segoe UI Emoji", 15)
    println(listOf(127902).joinChars())
    println(listOf(127902).joinChars().codePoints().toList())
    val a = false
    println(DrawTexts.getTextSizeX(font0, listOf(127902).joinChars(), s, s, a))
    println(DrawTexts.getTextSizeX(font1, listOf(127902).joinChars(), s, s, a))
    println(DrawTexts.getTextSizeX(font0, listOf(65039).joinChars(), s, s, a))
    println(DrawTexts.getTextSizeX(font1, listOf(65039).joinChars(), s, s, a))
    println("\uD83C\uDF9E️")
    println("\uD83C\uDF9E️".codePoints().toList())
    println("xx " + listOf(127902).joinChars().length)
    println("xx " + listOf(65039).joinChars().length)
    println(
        "xx " + "\uD83C\uDF9E️".length + "\uD83C\uDF9E".length + " xx " + "\uD83C\uDF9E️".codePoints().toList()
            .joinChars().length
    )
    println("xx " + "\uD83C\uDF9E️".codePoints().toList().joinChars().codePoints().toList())
    println(DrawTexts.getTextSizeX(font0, "\uD83C\uDF9E️", s, s, a))
    println(DrawTexts.getTextSizeX(font1, "\uD83C\uDF9E️", s, s, a))
    println("\uD83C\uDFA5️")
    println("\uD83C\uDFA5️".codePoints().toList())
    println(DrawTexts.getTextSizeX(font0, "\uD83C\uDFA5️", s, s, a))
    println(DrawTexts.getTextSizeX(font1, "\uD83C\uDFA5️", s, s, a))
    println(DrawTexts.getTextSizeX(font0, "\uD83C\uDFA5", s, s, a))
    println(DrawTexts.getTextSizeX(font1, "\uD83C\uDFA5", s, s, a))
    val tp = TextPanel("\uD83C\uDFA5️", DefaultConfig.style)
    println(tp.font)
}
