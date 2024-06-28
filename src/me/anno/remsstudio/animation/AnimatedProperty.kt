package me.anno.remsstudio.animation

import me.anno.Time
import me.anno.animation.Interpolation
import me.anno.gpu.GFX.glThread
import me.anno.io.saveable.Saveable
import me.anno.io.base.BaseWriter
import me.anno.maths.Maths.clamp
import me.anno.remsstudio.RemsStudio.root
import me.anno.remsstudio.animation.AnimationMaths.mul
import me.anno.remsstudio.animation.AnimationMaths.mulAdd
import me.anno.remsstudio.animation.Keyframe.Companion.getWeights
import me.anno.remsstudio.animation.drivers.AnimationDriver
import me.anno.remsstudio.utils.WrongClassType
import me.anno.ui.input.NumberType
import me.anno.utils.structures.Collections.filterIsInstance2
import me.anno.utils.structures.lists.UnsafeArrayList
import me.anno.utils.types.AnyToDouble.getDouble
import org.apache.logging.log4j.LogManager
import org.joml.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@Suppress("MemberVisibilityCanBePrivate")
class AnimatedProperty<V>(var type: NumberType, var defaultValue: V) : Saveable() {

    @Suppress("UNCHECKED_CAST")
    constructor(type: NumberType) : this(type, type.defaultValue as V)

    constructor() : this(NumberType.ANY)

    companion object {

        private val LOGGER = LogManager.getLogger(AnimatedProperty::class)

        fun any() = AnimatedProperty<Any>(NumberType.ANY)
        fun int(defaultValue: Int) = AnimatedProperty(NumberType.INT, defaultValue)
        fun intPlus(defaultValue: Int) = AnimatedProperty(NumberType.INT_PLUS, defaultValue)
        fun long(defaultValue: Long) = AnimatedProperty(NumberType.LONG, defaultValue)
        fun float(defaultValue: Float) = AnimatedProperty(NumberType.FLOAT, defaultValue)
        fun floatPlus(defaultValue: Float) = AnimatedProperty(NumberType.FLOAT_PLUS, defaultValue)
        fun float01(defaultValue: Float) = AnimatedProperty(NumberType.FLOAT_01, defaultValue)
        fun float01exp(defaultValue: Float) = AnimatedProperty(NumberType.FLOAT_01_EXP, defaultValue)
        fun double(defaultValue: Double) = AnimatedProperty(NumberType.DOUBLE, defaultValue)
        fun vec2(defaultValue: Vector2f) = AnimatedProperty(NumberType.VEC2, defaultValue)
        fun vec3(defaultValue: Vector3f) = AnimatedProperty(NumberType.VEC3, defaultValue)
        fun vec4(defaultValue: Vector4f) = AnimatedProperty(NumberType.VEC4, defaultValue)
        fun pos() = AnimatedProperty<Vector3f>(NumberType.POSITION)
        fun pos(defaultValue: Vector3f) = AnimatedProperty(NumberType.POSITION, defaultValue)
        fun pos2D() = AnimatedProperty<Vector2f>(NumberType.POSITION_2D)
        fun rotYXZ() = AnimatedProperty<Vector3f>(NumberType.ROT_YXZ)
        fun rotY() = AnimatedProperty<Float>(NumberType.ROT_Y)
        fun scale() = AnimatedProperty<Vector3f>(NumberType.SCALE)
        fun scale(defaultValue: Vector3f) = AnimatedProperty(NumberType.SCALE, defaultValue)
        fun color(defaultValue: Vector4f) = AnimatedProperty(NumberType.COLOR, defaultValue)
        fun color3(defaultValue: Vector3f) = AnimatedProperty(NumberType.COLOR3, defaultValue)
        fun skew() = AnimatedProperty<Vector2f>(NumberType.SKEW_2D)
        fun tiling() = AnimatedProperty<Vector4f>(NumberType.TILING)
        fun string() = AnimatedProperty(NumberType.STRING, "")
        fun alignment() = AnimatedProperty(NumberType.ALIGNMENT, 0f)
    }

    val drivers = arrayOfNulls<AnimationDriver>(type.numComponents)

    var isAnimated = false
    var lastChanged = 0L
    val keyframes = UnsafeArrayList<Keyframe<V>>()

    fun ensureCorrectType(v: Any?): V {
        @Suppress("UNCHECKED_CAST")
        return type.acceptOrNull(v) as V ?: throw RuntimeException("got $v for $type")
    }

    @Suppress("UNCHECKED_CAST")
    fun clampAny(value: Any) = clamp(value as V)

    @Suppress("UNCHECKED_CAST")
    fun clamp(value: V): V = type.clampFunc?.invoke(value) as V ?: value

    fun set(value: V): AnimatedProperty<V> {
        synchronized(this) {
            checkThread()
            keyframes.clear()
            keyframes.add(Keyframe(0.0, clamp(value)))
            keyframes.sort()
            return this
        }
    }

    fun addKeyframe(time: Double, value: Any) =
        addKeyframe(time, value, 0.001)

    fun addKeyframe(time: Double, value: Any?, equalityDt: Double): Keyframe<V>? {
        val value2 = type.acceptOrNull(value)
        return if (value2 != null) {
            @Suppress("UNCHECKED_CAST")
            addKeyframeInternal(time, clamp(value2 as V), equalityDt)
        } else {
            LOGGER.warn("Value $value is not accepted by type $type!")
            null
        }
    }

    fun checkIsAnimated() {
        isAnimated = keyframes.size >= 2 || drivers.any { it != null }
    }

    private fun addKeyframeInternal(time: Double, value: V, equalityDt: Double): Keyframe<V> {
        synchronized(this) {
            checkThread()
            ensureCorrectType(value)
            if (isAnimated) {
                for ((index, it) in keyframes.withIndex()) {
                    if (abs(it.time - time) < equalityDt) {
                        return keyframes[index].apply {
                            this.time = time
                            this.value = value
                        }
                    }
                }
                var index = keyframes.binarySearch { it.time.compareTo(time) }
                if (index < 0) index = -1 - index
                val interpolation =
                    keyframes.getOrNull(clamp(index, 0, keyframes.lastIndex))?.interpolation ?: Interpolation.SPLINE
                val newFrame = Keyframe(time, value, interpolation)
                keyframes.add(newFrame)
                sort()
                lastChanged = Time.nanoTime
                return newFrame
            } else {
                if (keyframes.size >= 1) {
                    keyframes[0].value = value
                    lastChanged = Time.nanoTime
                    return keyframes[0]
                } else {
                    val newFrame = Keyframe(time, value, Interpolation.SPLINE)
                    keyframes.add(newFrame)
                    lastChanged = Time.nanoTime
                    return newFrame
                }
            }
        }
    }

    fun checkThread() {
        if (glThread != null && Thread.currentThread() != glThread &&
            !root.listOfAll.none {
                when (this) {
                    it.color, it.position, it.rotationYXZ,
                    it.colorMultiplier, it.skew -> true
                    else -> false
                }
            }
        ) {
            throw RuntimeException()
        }
    }

    /**
     * true, if found
     * */
    fun remove(keyframe: Keyframe<*>): Boolean {
        checkThread()
        synchronized(this) {
            lastChanged = Time.nanoTime
            return keyframes.remove(keyframe)
        }
    }

    operator fun get(t0: Double, t1: Double): List<Keyframe<V>> {
        val i0 = max(0, getIndexBefore(t0))
        val i1 = min(getIndexBefore(t1) + 1, keyframes.size)
        return if (i1 > i0) keyframes.subList(i0, i1).filter { it.time in t0..t1 }
        else emptyList()
    }

    fun getAnimatedValue(time: Double, dst: V? = null): V {
        synchronized(this) {
            val size = keyframes.size
            @Suppress("UNCHECKED_CAST")
            return when {
                size == 0 -> return when (val v = defaultValue) {
                    is Vector2f -> AnimationMaths.v2(dst).set(v) as V
                    is Vector3f -> AnimationMaths.v3(dst).set(v) as V
                    is Vector4f -> AnimationMaths.v4(dst).set(v) as V
                    is Quaternionf -> AnimationMaths.q4(dst).set(v) as V
                    else -> v
                }
                size == 1 || !isAnimated -> keyframes[0].value
                else -> {

                    val index = clamp(getIndexBefore(time), 0, keyframes.size - 2)
                    val frame0 = keyframes.getOrElse(index - 1) { keyframes[0] }
                    val frame1 = keyframes[index]
                    val frame2 = keyframes[index + 1]
                    val frame3 = keyframes.getOrElse(index + 2) { keyframes.last() }
                    if (frame1 == frame2) return frame1.value

                    val t1 = frame1.time
                    val t2 = frame2.time

                    val f = (time - t1) / max(t2 - t1, 1e-16)
                    val w = getWeights(frame0, frame1, frame2, frame3, f)

                    // LOGGER.info("weights: ${w.print()}, values: ${frame0.value}, ${frame1.value}, ${frame2.value}, ${frame3.value}")

                    var valueSum: Any? = null
                    var weightSum = 0.0
                    fun addMaybe(value: V, weight: Double) {
                        if (weightSum == 0.0) {
                            valueSum = toCalc(value)
                            if (weight != 1.0) valueSum = mul(valueSum!!, weight, dst)
                            weightSum = weight
                        } else if (weight != 0.0) {
                            // add value with weight...
                            valueSum = mulAdd(valueSum!!, toCalc(value), weight, dst)
                            weightSum += weight
                        }// else done
                    }

                    addMaybe(frame0.value, w.x)
                    addMaybe(frame1.value, w.y)
                    addMaybe(frame2.value, w.z)
                    addMaybe(frame3.value, w.w)

                    return clamp(fromCalc(valueSum!!))

                }
            }
        }
    }

    operator fun get(time: Double, dst: V? = null): V = getValueAt(time, dst)

    fun getValueAt(time: Double, dst: Any? = null): V {
        val hasDrivers = drivers.any { it != null }
        val animatedValue = getAnimatedValue(time)
        if (!hasDrivers) return animatedValue
        val v = animatedValue ?: defaultValue ?: 0.0
        val v0 = getDouble(v, 0, 0.0)
        val v1 = getDouble(v, 1, 0.0)
        val v2 = getDouble(v, 2, 0.0)
        val v3 = getDouble(v, 3, 0.0)
        // replace the components, which have drivers, with the driver values
        @Suppress("UNCHECKED_CAST", "USELESS_CAST")
        return when (animatedValue) {
            is Int -> drivers[0]?.getValue(time, v0, 0)?.toInt() ?: animatedValue
            is Long -> drivers[0]?.getValue(time, v0, 0)?.toLong() ?: animatedValue
            is Float -> getFloat(0, time, v0, animatedValue)
            is Double -> drivers[0]?.getValue(time, v0, 0) ?: animatedValue
            is Vector2f -> AnimationMaths.v2(dst).set(
                getFloat(0, time, v0, animatedValue.x),
                getFloat(1, time, v1, animatedValue.y)
            )
            is Vector3f -> AnimationMaths.v3(dst).set(
                getFloat(0, time, v0, animatedValue.x),
                getFloat(1, time, v1, animatedValue.y),
                getFloat(2, time, v2, animatedValue.z)
            )
            is Vector4f -> AnimationMaths.v4(dst).set(
                getFloat(0, time, v0, animatedValue.x),
                getFloat(1, time, v1, animatedValue.y),
                getFloat(2, time, v2, animatedValue.z),
                getFloat(3, time, v3, animatedValue.w)
            )
            is Quaternionf -> AnimationMaths.q4(dst).set(
                getFloat(0, time, v0, animatedValue.x),
                getFloat(1, time, v1, animatedValue.y),
                getFloat(2, time, v2, animatedValue.z),
                getFloat(3, time, v3, animatedValue.w)
            ) as Any
            else -> throw RuntimeException("Replacing components with drivers in $animatedValue is not yet supported!")
        } as V
    }

    private fun getFloat(driverIndex: Int, time: Double, vi: Double, av: Float): Float {
        val driver = drivers[driverIndex]
        return driver?.getFloatValue(time, vi, driverIndex) ?: av
    }

    fun nextKeyframe(time: Double): Double {
        return keyframes.firstOrNull { it.time > time }?.time
            ?: Double.POSITIVE_INFINITY
    }

    private fun toCalc(a: V): Any {
        return when (a) {
            is Int -> a.toDouble()
            is Float -> a
            is Double -> a
            is Long -> a.toDouble()
            is Vector2f, is Vector3f, is Vector4f, is Quaternionf -> a
            is Vector2d, is Vector3d, is Vector4d, is Quaterniond -> a
            is String -> a
            else -> throw RuntimeException("don't know how to calc $a")
        } // needed by Intellij Kotlin compiler
    }

    private fun fromCalc(a: Any): V = clampAny(a)

    private fun getIndexBefore(time: Double): Int {
        // get the index of the time
        val rawIndex = keyframes.binarySearch { it.time.compareTo(time) }
        return (if (rawIndex < 0) -rawIndex - 1 else rawIndex) - 1
    }

    override val className get() = "AnimatedProperty"
    override val approxSize get() = 10

    override fun save(writer: BaseWriter) {
        super.save(writer)
        sort()
        // must be written before keyframes!!
        writer.writeBoolean("isAnimated", isAnimated)
        if (isAnimated) {
            writer.writeObjectList(this, "vs", keyframes)
        } else {
            // isAnimated = false is default
            val value0 = keyframes.firstOrNull()?.value
            if (value0 != null && value0 != defaultValue) {
                writer.writeSomething(this, "v", value0, true)
            }
        }
        for (i in 0 until min(type.numComponents, drivers.size)) {
            writer.writeObject(this, "driver$i", drivers[i])
        }
    }

    fun sort() {
        synchronized(this) {
            keyframes.sort()
        }
    }

    override fun setProperty(name: String, value: Any?) {
        when (name) {
            "keyframe0", "v" -> addKeyframe(0.0, value ?: return)
            "isAnimated" -> isAnimated = value == true
            "driver0" -> setDriver(0, value as? AnimationDriver ?: return)
            "driver1" -> setDriver(1, value as? AnimationDriver ?: return)
            "driver2" -> setDriver(2, value as? AnimationDriver ?: return)
            "driver3" -> setDriver(3, value as? AnimationDriver ?: return)
            "keyframes", "vs" -> {
                if (value is Keyframe<*>) {
                    val castValue = type.acceptOrNull(value.value)
                    if (castValue != null) {
                        @Suppress("UNCHECKED_CAST")
                        addKeyframe(value.time, clamp(castValue as V) as Any, 0.0)?.apply {
                            interpolation = value.interpolation
                        }
                    } else warnDroppedKeyframe(value)
                } else if (value is List<*>) {
                    for (vi in value.filterIsInstance2(Keyframe::class)) {
                        val castValue = type.acceptOrNull(vi.value)
                        if (castValue != null) {
                            @Suppress("UNCHECKED_CAST")
                            addKeyframe(vi.time, clamp(castValue as V) as Any, 0.0)?.apply {
                                interpolation = vi.interpolation
                            }
                        } else warnDroppedKeyframe(vi)
                    }
                } else WrongClassType.warn("keyframe", value as? Saveable)
            }
            else -> super.setProperty(name, value)
        }
    }

    private fun warnDroppedKeyframe(vi: Keyframe<*>){
        LOGGER.warn("Dropped keyframe!, incompatible type ${vi.value} for $type")
    }

    fun setDriver(index: Int, value: Saveable?) {
        if (index >= drivers.size) {
            LOGGER.warn("Driver$index out of bounds for ${type.numComponents}/${drivers.size}/$type")
            return
        }
        if (value is AnimationDriver) {
            drivers[index] = value
            lastChanged = Time.nanoTime
        } else WrongClassType.warn("driver", value)
    }

    fun copyFrom(obj: Any?, force: Boolean = false) {
        if (obj === this && !force) throw RuntimeException("Probably a typo!")
        if (obj is AnimatedProperty<*>) {
            isAnimated = obj.isAnimated
            keyframes.clear()
            for (src in obj.keyframes) {
                val castValue = type.acceptOrNull(src.value!!)
                if (castValue != null) {
                    @Suppress("UNCHECKED_CAST")
                    val dst = Keyframe(src.time, clamp(castValue as V), src.interpolation)
                    keyframes.add(dst)
                } else LOGGER.warn("${src.value} is not accepted by $type")
                // else convert the type??...
            }
            for (i in 0 until type.numComponents) {
                this.drivers[i] = obj.drivers.getOrNull(i)
            }
            lastChanged = Time.nanoTime
        } else LOGGER.warn("copy-from-object $obj is not an AnimatedProperty!")
    }

    fun clear() {
        isAnimated = false
        for (i in drivers.indices) {
            drivers[i] = null
        }
        keyframes.clear()
        lastChanged = Time.nanoTime
    }

    override fun isDefaultValue() =
        !isAnimated && (keyframes.isEmpty() || keyframes[0].value == defaultValue) && drivers.all { it == null }

}