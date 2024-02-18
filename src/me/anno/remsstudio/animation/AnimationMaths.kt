package me.anno.remsstudio.animation

import me.anno.ui.input.NumberType
import me.anno.remsstudio.utils.StringMixer
import org.joml.Quaternionf
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f
import kotlin.math.roundToInt

object AnimationMaths {

    // to save allocations
    fun v2(dst: Any?) = dst as? Vector2f ?: Vector2f()
    fun v3(dst: Any?) = dst as? Vector3f ?: Vector3f()
    fun v4(dst: Any?) = dst as? Vector4f ?: Vector4f()
    fun q4(dst: Any?) = dst as? Quaternionf ?: Quaternionf()

    /**
     * b + a * f
     * */
    fun mulAdd(first: Any, second: Any, f: Double, dst: Any?): Any {
        return when (first) {
            is Float -> first + (second as Float) * f.toFloat()
            is Double -> first + (second as Double) * f
            is Vector2f -> {
                second as Vector2f
                val g = f.toFloat()
                val result = v2(dst)
                result.set(
                    first.x + second.x * g,
                    first.y + second.y * g
                )
            }
            is Vector3f -> {
                second as Vector3f
                val g = f.toFloat()
                val result = v3(dst)
                result.set(
                    first.x + second.x * g,
                    first.y + second.y * g,
                    first.z + second.z * g
                )
            }
            is Vector4f -> {
                second as Vector4f
                val g = f.toFloat()
                val result = v4(dst)
                result.set(
                    first.x + second.x * g,
                    first.y + second.y * g,
                    first.z + second.z * g,
                    first.w + second.w * g
                )
            }
            is String -> StringMixer.mix(first.toString(), second.toString(), f)
            else -> throw RuntimeException("don't know how to mul-add $second and $first")
        }
    }

    /**
     * a * f
     * */
    fun mul(a: Any, f: Double, dst: Any?): Any {
        return when (a) {
            is Int -> a * f
            is Long -> a * f
            is Float -> a * f.toFloat()
            is Double -> a * f
            is Vector2f -> v2(dst).set(a).mul(f.toFloat())
            is Vector3f -> v3(dst).set(a).mul(f.toFloat())
            is Vector4f -> v4(dst).set(a).mul(f.toFloat())
            is String -> a//a.substring(0, clamp((a.length * f).roundToInt(), 0, a.length))
            else -> throw RuntimeException("don't know how to mul $a")
        }
    }

    /**
     * a * (1-f) + f * b
     * */
    fun mix(a: Any?, b: Any?, f: Double, type: NumberType): Any {
        val g = 1.0 - f
        return when (type) {
            NumberType.INT,
            NumberType.INT_PLUS -> ((a as Int) * g + f * (b as Int)).roundToInt()
            NumberType.LONG -> ((a as Long) * g + f * (b as Long)).toLong()
            NumberType.FLOAT,
            NumberType.FLOAT_01, NumberType.FLOAT_01_EXP,
            NumberType.FLOAT_PLUS -> ((a as Float) * g + f * (b as Float)).toFloat()
            NumberType.DOUBLE -> (a as Double) * g + f * (b as Double)
            NumberType.SKEW_2D -> (a as Vector2f).lerp(b as Vector2f, f.toFloat(), Vector2f())
            NumberType.POSITION,
            NumberType.ROT_YXZ,
            NumberType.SCALE -> (a as Vector3f).lerp(b as Vector3f, f.toFloat(), Vector3f())
            NumberType.COLOR, NumberType.TILING -> (a as Vector4f).lerp(b as Vector4f, f.toFloat(), Vector4f())
            NumberType.QUATERNION -> (a as Quaternionf).slerp(b as Quaternionf, f.toFloat())
            NumberType.STRING -> StringMixer.mix(a.toString(), b.toString(), f)
            else -> throw RuntimeException("don't know how to linearly interpolate $a and $b")
        }
    }

}