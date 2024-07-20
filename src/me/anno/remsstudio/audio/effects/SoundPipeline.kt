package me.anno.remsstudio.audio.effects

import me.anno.Engine
import me.anno.engine.inspector.Inspectable
import me.anno.io.base.BaseWriter
import me.anno.io.saveable.Saveable
import me.anno.language.translation.NameDesc
import me.anno.remsstudio.RemsStudio
import me.anno.remsstudio.audio.effects.falloff.ExponentialFalloff
import me.anno.remsstudio.audio.effects.falloff.LinearFalloff
import me.anno.remsstudio.audio.effects.falloff.SquareFalloff
import me.anno.remsstudio.audio.effects.impl.EchoEffect
import me.anno.remsstudio.audio.effects.impl.EqualizerEffect
import me.anno.remsstudio.audio.effects.impl.NoiseSuppressionEffect
import me.anno.remsstudio.audio.effects.impl.PitchEffect
import me.anno.remsstudio.objects.Camera
import me.anno.remsstudio.objects.video.Video
import me.anno.ui.Panel
import me.anno.ui.Style
import me.anno.ui.base.groups.PanelListY
import me.anno.ui.editor.SettingCategory
import me.anno.ui.editor.stacked.Option
import me.anno.ui.editor.stacked.StackPanel
import me.anno.utils.structures.Collections.filterIsInstance2
import org.jtransforms.fft.FloatFFT_1D

class SoundPipeline() : Saveable(), Inspectable {

    lateinit var audio: Video
    lateinit var camera: Camera

    constructor(audio: Video) : this() {
        this.audio = audio
    }

    override fun createInspector(
        inspected: List<Inspectable>, list: PanelListY, style: Style,
        getGroup: (nameDesc: NameDesc) -> SettingCategory
    ) {
        val effectsGroup = getGroup(NameDesc("Audio Effects", "Audio Effects", "obj.audio-fx"))
        effectsGroup += object : StackPanel(
            "Effects Stack",
            "Effects can be added with RMB, are applied one after another",
            options.map { gen ->
                option { gen().apply { audio = this@SoundPipeline.audio } }
            },
            effects,
            {
                if (it is SoundEffect) {
                    option { it }
                } else null
            },
            style
        ) {

            override fun setValue(newValue: List<Inspectable>, mask: Int, notify: Boolean): Panel {
                if (newValue !== effects) {
                    effects.clear()
                    effects.addAll(newValue.filterIsInstance2(SoundEffect::class))
                }
                return this
            }

            override val value: List<Inspectable>
                get() = effects

            override fun onAddComponent(component: Inspectable, index: Int) {
                component as SoundEffect
                RemsStudio.largeChange("Add ${component.displayName}") {
                    if (index >= effects.size) {
                        effects.add(component)
                    } else {
                        effects.add(index, component)
                    }
                }
            }

            override fun onRemoveComponent(component: Inspectable) {
                component as SoundEffect
                RemsStudio.largeChange("Remove ${component.displayName}") {
                    effects.remove(component)
                }
            }

        }
    }

    val effects = ArrayList<SoundEffect>()

    //var isFirstBuffer = true
    /*var lastValue = 0f
    fun fixJumps(output: FloatArray, v0: Float, index1: Int, length: Int) {
        if (isFirstBuffer) {
            isFirstBuffer = false
            return
        }
        val v1 = output[index1]
        val v2 = output[index1 + 1]
        val delta = 2 * v1 - (v0 + v2) // flatten, keep the gradient
        if (abs(delta) > 0.2f * max(abs(v0), abs(v1))) {// high frequency, not ok -> cut it off
            val falloff = 6f
            val lastExponent = exp(-falloff)
            val amplitude = delta / (1f - lastExponent)
            for (i in 0 until length) {
                output[index1 + i] -= amplitude * (exp(-i * falloff / length) - lastExponent)
            }
        }
    }*/

    override fun save(writer: BaseWriter) {
        super.save(writer)
        for (stage in effects) {
            writer.writeObject(this, "stage", stage)
        }
    }

    override fun setProperty(name: String, value: Any?) {
        when (name) {
            "stage" -> {
                if (value is SoundEffect) {
                    effects.add(value)
                }
            }
            else -> super.setProperty(name, value)
        }
    }

    override val className get() = "SoundPipeline"
    override val approxSize get() = 100
    override fun isDefaultValue() = effects.isEmpty()

    fun clone(): SoundPipeline {
        val copy = SoundPipeline(audio)
        copy.effects.addAll(
            effects.map {
                it.clone().apply {
                    audio = this@SoundPipeline.audio
                }
            }
        )
        return copy
    }

    companion object {

        init {
            Engine.registerForShutdown {
                pl.edu.icm.jlargearrays.ConcurrencyUtils.shutdownThreadPoolAndAwaitTermination()
            }
        }

        fun changeDomain(
            dst: Domain, data: FloatArray,
            fft: FloatFFT_1D = FloatFFT_1D(data.size.toLong())
        ) {
            /*val rx = min(data.size / 2, 10)
            val r = (0 until rx)
            println(
                "changing ${data.size}x (" +
                        "${r.map { data[it] }},...," +
                        "${r.map { data[data.size - rx + it] }}) to $dst"
            )*/
            when (dst) {
                Domain.TIME_DOMAIN -> fft.realInverse(data, true)
                Domain.FREQUENCY_DOMAIN -> fft.realForward(data)
            }
            /*println(
                "  -> (${r.map { data[it] }},...," +
                        "${r.map { data[data.size - rx + it] }})"
            )*/
        }

        fun option(generator: () -> SoundEffect): Option {
            val sample = generator()
            return Option(NameDesc(sample.displayName, sample.description, ""), generator)
        }

        /**
         * could be extended/modified by mods or plugins
         * */
        val options = arrayListOf(
            { EchoEffect() },
            { EqualizerEffect() },
            { PitchEffect() },
            { SquareFalloff() },
            { LinearFalloff() },
            { ExponentialFalloff() },
            { NoiseSuppressionEffect() }
        )

    }


}