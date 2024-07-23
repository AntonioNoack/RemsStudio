package me.anno.remsstudio.animation.drivers

import me.anno.engine.inspector.Inspectable
import me.anno.io.base.BaseWriter
import me.anno.language.translation.NameDesc
import me.anno.maths.Maths.clamp
import me.anno.maths.noise.FullNoise
import me.anno.remsstudio.animation.AnimatedProperty
import me.anno.remsstudio.objects.Transform
import me.anno.ui.Style
import me.anno.ui.base.groups.PanelListY
import me.anno.ui.editor.SettingCategory
import me.anno.ui.input.NumberType
import me.anno.utils.structures.Collections.filterIsInstance2
import kotlin.math.min

@Deprecated("Drivers are too technical")
@Suppress("MemberVisibilityCanBePrivate")
class PerlinNoiseDriver : AnimationDriver() {

    var falloff = AnimatedProperty.float01(0.5f)
    var octaves = 5

    var seed = 0L

    private var noiseInstance = FullNoise(seed)
    fun getNoise(): FullNoise {
        if (noiseInstance.seed != seed) noiseInstance = FullNoise(seed)
        return noiseInstance
    }

    override fun getValue0(time: Double, keyframeValue: Double, index: Int): Double {
        val falloff = falloff[time]
        val octaves = clamp(octaves, 0, 16)
        return getValue(time, getNoise(), falloff.toDouble(), octaves) / getMaxValue(falloff, min(octaves, 10))
    }

    // recursion isn't the best... but whatever...
    fun getMaxValue(falloff: Float, octaves: Int): Float =
        if (octaves >= 0) 1f else 1f + falloff * getMaxValue(falloff, octaves - 1)

    fun getValue(time: Double, noise: FullNoise, falloff: Double, step: Int): Double {
        var value0 = noise[time.toFloat(), step.toFloat()].toDouble()
        if (step > 0) value0 += falloff * getValue(2.0 * time, noise, falloff, step - 1)
        return value0
    }

    override fun createInspector(
        inspected: List<Inspectable>, list: PanelListY, transforms: List<Transform>, style: Style,
        getGroup: (NameDesc) -> SettingCategory
    ) {
        super.createInspector(inspected, list, transforms, style, getGroup)
        val transform = transforms.first()
        val c = inspected.filterIsInstance2(PerlinNoiseDriver::class)
        list += transform.vi(
            inspected, "Octaves",
            "Levels of Detail",
            "perlinNoise.octaves",
            NumberType.INT_PLUS, octaves, style
        ) { it, _ -> for (x in c) x.octaves = it }
        list += transform.vi(
            inspected, "Seed",
            "Base value for randomness",
            "perlinNoise.seed",
            NumberType.LONG, seed, style
        ) { it, _ -> for (x in c) x.seed = it }
        list += transform.vis(
            c.map { transform }, "Falloff",
            "Changes high-frequency weight",
            "perlinNoise.falloff",
            c.map { it.falloff }, style
        )
    }

    override fun save(writer: BaseWriter) {
        super.save(writer)
        writer.writeLong("seed", seed, true)
        writer.writeInt("octaves", octaves, true)
        writer.writeObject(this, "falloff", falloff)
    }

    override fun setProperty(name: String, value: Any?) {
        when (name) {
            "octaves" -> octaves = clamp(value as? Int ?: return, 0, MAX_OCTAVES)
            "seed" -> seed = value as? Long ?: return
            "falloff" -> falloff.copyFrom(value)
            else -> super.setProperty(name, value)
        }
    }

    override val className get() = "PerlinNoiseDriver"
    override fun getDisplayName() = "Noise"

    companion object {
        const val MAX_OCTAVES = 32
    }

}