package me.anno.remsstudio.animation

import me.anno.animation.Interpolation
import me.anno.animation.Type
import me.anno.io.Saveable
import me.anno.io.base.BaseWriter
import me.anno.utils.types.AnyToFloat
import org.joml.*

@Suppress("unused")
class Keyframe<V>(var time: Double, var value: V, var interpolation: Interpolation) :
    Saveable(), Comparable<Keyframe<V>> {

    @Suppress("unchecked_cast")
    constructor() : this(0.0, 0f as V, Interpolation.SPLINE)
    constructor(time: Double, value: V) : this(time, value, Interpolation.SPLINE)

    override fun compareTo(other: Keyframe<V>): Int = time.compareTo(other.time)

    override fun isDefaultValue() = false
    override val className get() = "Keyframe"
    override val approxSize get() = 1

    override fun save(writer: BaseWriter) {
        super.save(writer)
        writer.writeDouble("time", time)
        writer.writeSomething(this, "value", value, true)
        writer.writeInt("mode", interpolation.id)
    }

    override fun readInt(name: String, value: Int) {
        when (name) {
            "mode" -> interpolation = Interpolation.getType(value)
            else -> super.readInt(name, value)
        }
    }

    fun setValueUnsafe(value: Any?) {
        @Suppress("unchecked_cast")
        this.value = value as V
    }

    fun getChannelAsFloat(index: Int): Float {
        return AnyToFloat.getFloat(value!!, index, 0f)
    }

    override fun readDouble(name: String, value: Double) {
        when (name) {
            "time" -> time = value
            else -> super.readDouble(name, value)
        }
    }

    override fun readSomething(name: String, value: Any?) {
        when (name) {
            "value" -> {
                @Suppress("unchecked_cast")
                this.value = value as V
            }
            else -> super.readSomething(name, value)
        }
    }

    @Suppress("useless_cast")
    fun setValue(index: Int, v: Float, type: Type) {
        @Suppress("unchecked_cast")
        val tmp: Any= type.clamp(
            when (val value = value) {
                is Int -> v.toInt()
                is Long -> v.toLong()
                is Float -> v
                is Double -> v.toDouble()
                is Vector2f -> when (index) {
                    0 -> Vector2f(v, value.y)
                    else -> Vector2f(value.x, v)
                }
                is Vector3f -> when (index) {
                    0 -> Vector3f(v, value.y, value.z)
                    1 -> Vector3f(value.x, v, value.z)
                    else -> Vector3f(value.x, value.y, v)
                }
                is Vector4f -> when (index) {
                    0 -> Vector4f(v, value.y, value.z, value.w)
                    1 -> Vector4f(value.x, v, value.z, value.w)
                    2 -> Vector4f(value.x, value.y, v, value.w)
                    else -> Vector4f(value.x, value.y, value.z, v)
                }
                is Quaternionf -> when (index) {
                    0 -> Quaternionf(v, value.y, value.z, value.w)
                    1 -> Quaternionf(value.x, v, value.z, value.w)
                    2 -> Quaternionf(value.x, value.y, v, value.w)
                    else -> Quaternionf(value.x, value.y, value.z, v)
                }
                is String -> v as Any
                else -> throw RuntimeException("todo implement Keyframe.getValue(index) for $value")
            }
        )
        value = tmp as V
    }

    companion object {
        fun getWeights(f0: Keyframe<*>, f1: Keyframe<*>, f2: Keyframe<*>, f3: Keyframe<*>, t0: Double): Vector4d {
            val interpolation = (if (t0 > 1.0) f2 else f1).interpolation
            return interpolation.getWeights(f0.time, f1.time, f2.time, f3.time, t0)
        }
    }

}