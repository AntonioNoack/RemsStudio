package me.anno.remsstudio.utils

import me.anno.fonts.Codepoints.codepoints
import me.anno.maths.Maths
import me.anno.maths.Maths.clamp
import me.anno.maths.Maths.max
import me.anno.utils.types.Strings.joinChars0
import kotlin.math.roundToInt
import kotlin.random.Random

@Suppress("MemberVisibilityCanBePrivate")
object StringMixer {

    fun mixLength(aLength: Int, bLength: Int, f: Double, max: Int): Int {
        return clamp(Maths.mix(aLength.toDouble(), bLength.toDouble(), f).roundToInt(), 0, max)
    }

    fun mixIndices(l0: Int, l1: Int, f: Double): Int {
        return Maths.mix(l0.toDouble(), l1.toDouble(), f).roundToInt()
    }

    fun mixSubstring(s: String, l0: Int, l1: Int, d0: Int, d1: Int, f: Double): String {
        return s.substring(mixIndices(l0, l1, f), mixIndices(d0, d1, f))
    }

    fun mixContains(a: String, b: String, f: Double): String {
        val firstIndex = a.indexOf(b, 0, true)
        return mixSubstring(a, 0, 0, firstIndex, 0, f) + b + a.substring(
            mixIndices(firstIndex + b.length, a.length, f)
        )
    }

    fun mix(a: String, b: String, f: Double): String {

        if (f <= 0.0 || a == b) return a
        if (f >= 1.0) return b

        // lines are special
        if (a.indexOf('\n') >= 0 || b.indexOf('\n') >= 0) {
            val la = a.split('\n')
            val lb = b.split('\n')
            return (0 until max(la.size, lb.size)).joinToString("\n") {
                mix(la.getOrNull(it) ?: "", lb.getOrNull(it) ?: "", f)
            }
        }

        val aLength = a.length
        val bLength = b.length

        val g = 1.0 - f

        val aIsLonger = aLength > bLength
        val bIsLonger = bLength > aLength

        return when {
            aIsLonger && a.startsWith(b, true) ->
                a.substring(0, mixLength(aLength, bLength, f, aLength))
            bIsLonger && b.startsWith(a, true) ->
                b.substring(0, mixLength(aLength, bLength, f, bLength))
            aIsLonger && a.endsWith(b, true) ->
                a.substring(clamp(((aLength - bLength) * f).roundToInt(), 0, aLength))
            bIsLonger && b.endsWith(a, true) ->
                b.substring(clamp(((bLength - aLength) * g).roundToInt(), 0, bLength))
            aIsLonger && a.contains(b, true) -> mixContains(a, b, f)
            bIsLonger && b.contains(a, true) -> mixContains(b, a, g)
            else -> {
                var hasSpecial = false
                val min = Char.MIN_HIGH_SURROGATE
                val max = Char.MAX_HIGH_SURROGATE
                for (i in a.indices) {
                    if (a[i] in min..max) {
                        hasSpecial = true
                        break
                    }
                }
                if (!hasSpecial) {
                    for (i in b.indices) {
                        if (b[i] in min..max) {
                            hasSpecial = true
                            break
                        }
                    }
                }
                if (hasSpecial) {
                    mixSpecial(a, b, f, g)
                } else {
                    mixAscii(a, b, f, g)
                }
            }
        }
    }

    private fun mixAscii(a: String, b: String, f: Double, g: Double): String {
        val aLength = a.length
        val bLength = b.length
        return if (aLength == bLength) {
            // totally different -> mix randomly for hacking-like effect (??...)
            val chars = CharArray(a.length)
            val random = Random(1234)
            val shuffled = (0 until aLength).shuffled(random)
            val shuffleEnd = (g * aLength).roundToInt()
            for (i in 0 until aLength) {
                val code = if (shuffled[i] < shuffleEnd) a[i] else b[i]
                chars[i] = code
            }
            chars.concatToString()
        } else {
            val aLength2 = (aLength * g).roundToInt()
            val bLength2 = (bLength * f).roundToInt()
            val aEndIndex = clamp(aLength2, 0, aLength)
            val bStartIndex = clamp(bLength - bLength2, 0, b.lastIndex)
            a.substring(0, aEndIndex) + b.substring(bStartIndex, b.length)
        }
    }

    private fun mixSpecial(a: String, b: String, f: Double, g: Double): String {
        val aChars = a.codepoints()
        val bChars = b.codepoints()
        val aLength = a.length
        val bLength = b.length
        if (aChars.size == bChars.size) {
            // totally different -> mix randomly for hacking-like effect (??...)
            val str = StringBuilder(max(aLength, bLength))
            val random = Random(1234)
            val shuffled = aChars.indices.shuffled(random)
            val shuffleEnd = (g * aChars.size).roundToInt()
            for (i in aChars.indices) {
                val code = if (shuffled[i] < shuffleEnd) aChars[i] else bChars[i]
                str.append(code.joinChars0())
            }
            return str.toString()
        } else {
            val aLength2 = (aLength * g).roundToInt()
            val bLength2 = (bLength * f).roundToInt()
            val aEndIndex = clamp(aLength2, 0, a.length)
            val bStartIndex = clamp(b.length - bLength2, 0, b.lastIndex)
            return a.substring(0, aEndIndex) + b.substring(bStartIndex, b.length)
        }
    }
}