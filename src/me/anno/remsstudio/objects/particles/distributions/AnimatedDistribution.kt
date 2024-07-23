package me.anno.remsstudio.objects.particles.distributions

import me.anno.io.saveable.Saveable
import me.anno.io.base.BaseWriter
import me.anno.remsstudio.animation.AnimatedProperty
import me.anno.remsstudio.objects.Transform
import me.anno.remsstudio.objects.inspectable.InspectableVector
import me.anno.remsstudio.ui.ComponentUIV2
import me.anno.ui.Style
import me.anno.ui.base.groups.PanelListY
import me.anno.ui.input.NumberType
import me.anno.utils.structures.ValueWithDefault
import me.anno.utils.structures.ValueWithDefault.Companion.writeMaybe
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f
import java.util.*

@Suppress("MemberVisibilityCanBePrivate")
class AnimatedDistribution(
    distribution: Distribution = ConstantDistribution(),
    val type: NumberType,
    val defaultValues: List<Any>
) : Saveable() {

    val distributionI = ValueWithDefault(distribution)
    var distribution: Distribution
        get() = distributionI.value
        set(value) {
            distributionI.value = value
        }

    constructor() : this(NumberType.ANY, 0f)
    constructor(type: NumberType, defaultValue: Any) : this(ConstantDistribution(), type, listOf(defaultValue))
    constructor(type: NumberType, defaultValues: List<Any>) : this(ConstantDistribution(), type, defaultValues)

    val channels = ArrayList<AnimatedProperty<*>>()
    lateinit var properties: List<InspectableVector>

    val lastChanged get() = channels.maxOfOrNull { it.lastChanged } ?: 0L

    fun createInspector(
        c: List<Transform>,
        inspected: List<AnimatedDistribution>,
        list: PanelListY,
        style: Style
    ) {
        if (lastDist !== distribution) update()
        for (ins in inspected) {
            val properties = ins.properties
            for (index in properties.indices) {
                val property = properties[index]
                if (property.pType == InspectableVector.PType.ROTATION) channels[index].type = NumberType.ROT_YXZ
                if (property.pType == InspectableVector.PType.SCALE) channels[index].type = NumberType.SCALE
            }
        }
        val properties = properties
        for (index in properties.indices) {
            val property = properties[index]
            // could this crash? only if another property had differing amounts of channels
            list += ComponentUIV2.vis(
                c, property.nameDesc.name, property.nameDesc.desc, property.nameDesc.key,
                inspected.map { it.channels[index] },
                style
            )
        }
    }

    fun copyFrom(data: Any?) {
        copyFrom(data as? AnimatedDistribution ?: return)
    }

    fun copyFrom(data: AnimatedDistribution) {
        distribution = data.distribution
        update()
        val channels = data.channels
        for (index in channels.indices) {
            setChannel(index, channels[index])
        }
    }

    private fun createChannel(index: Int): AnimatedProperty<*> {
        return AnimatedProperty<Any>(type).apply {
            defaultValue = defaultValues[index % defaultValues.size]
        }
    }

    private var lastDist: Distribution? = null
    fun update() {
        if (lastDist === distribution) return
        properties = distribution.listProperties()
        while (properties.size > channels.size) channels += createChannel(channels.size)
        while (properties.size < channels.size) channels.removeAt(channels.lastIndex)
    }

    fun update(time: Double, random: Random) {
        update(time)
        distribution.random.setSeed(random.nextLong())
    }

    fun update(time: Double) {
        if (lastDist !== distribution) update()
        for (index in properties.indices) {
            val property = properties[index]
            when (type.numComponents) {
                1 -> property.value.set(channels[index][time] as Float)
                2 -> property.value.set(channels[index][time] as Vector2f)
                3 -> property.value.set(channels[index][time] as Vector3f)
                4 -> property.value.set(channels[index][time] as Vector4f)
            }
        }
    }

    fun nextV1(time: Double, random: Random): Float {
        update(time, random)
        return distribution.nextV1()
    }

    fun maxV1(time: Double): Float {
        update(time)
        return distribution.maxV1()
    }

    @Suppress("unused")
    fun nextV2(time: Double, random: Random): Vector2f {
        update(time, random)
        return distribution.nextV2()
    }

    fun nextV3(time: Double, random: Random): Vector3f {
        update(time, random)
        return distribution.nextV3()
    }

    @Suppress("unused")
    fun nextV4(time: Double, random: Random): Vector4f {
        update(time, random)
        return distribution.nextV4()
    }

    private fun Vector4f.set(v: Vector2f) = set(v.x, v.y, 0f, 0f)
    private fun Vector4f.set(v: Vector3f) = set(v.x, v.y, v.z, 0f)

    override fun save(writer: BaseWriter) {
        super.save(writer)
        update()
        writer.writeMaybe(this, "distribution", distributionI)
        for (i in channels.indices) {
            writer.writeObject(this, "channel[$i]", channels[i])
        }
    }

    override fun setProperty(name: String, value: Any?) {
        update()
        when (name) {
            "distribution" -> distribution = value as? Distribution ?: return
            "channel[0]" -> setChannel(0, value as? AnimatedProperty<*> ?: return)
            "channel[1]" -> setChannel(1, value as? AnimatedProperty<*> ?: return)
            "channel[2]" -> setChannel(2, value as? AnimatedProperty<*> ?: return)
            "channel[3]" -> setChannel(3, value as? AnimatedProperty<*> ?: return)
            "channel[4]" -> setChannel(4, value as? AnimatedProperty<*> ?: return)
            "channel[5]" -> setChannel(5, value as? AnimatedProperty<*> ?: return)
            "channel[6]" -> setChannel(6, value as? AnimatedProperty<*> ?: return)
            "channel[7]" -> setChannel(7, value as? AnimatedProperty<*> ?: return)
            else -> super.setProperty(name, value)
        }
    }

    fun setChannel(index: Int, channel: AnimatedProperty<*>) {
        while (channels.size <= index) channels += createChannel(channels.size)
        channels[index].copyFrom(channel)
    }

    override val approxSize get() = 35
    override fun isDefaultValue(): Boolean = !distributionI.isSet && channels.all { it.isDefaultValue() }
    override val className get() = "AnimatedDistribution"

}