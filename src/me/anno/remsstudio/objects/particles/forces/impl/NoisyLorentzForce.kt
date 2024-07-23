package me.anno.remsstudio.objects.particles.forces.impl

import me.anno.engine.inspector.Inspectable
import me.anno.io.base.BaseWriter
import me.anno.language.translation.NameDesc
import me.anno.maths.noise.FullNoise
import me.anno.remsstudio.animation.AnimatedProperty
import me.anno.remsstudio.objects.inspectable.InspectableAnimProperty
import me.anno.remsstudio.objects.particles.Particle
import me.anno.remsstudio.objects.particles.ParticleState
import me.anno.ui.Style
import me.anno.ui.base.groups.PanelListY
import me.anno.ui.editor.SettingCategory
import org.joml.Vector3f
import org.joml.Vector4f
import java.util.*

@Suppress("MemberVisibilityCanBePrivate")
class NoisyLorentzForce : PerParticleForce(
    "Noisy Lorentz Force",
    "Circular motion by velocity, randomized by location", "lorentz.noisy"
) {

    lateinit var nx: FullNoise
    lateinit var ny: FullNoise
    lateinit var nz: FullNoise

    var seed = initRandomizers(0)

    val fieldScale = AnimatedProperty.vec4(Vector4f(1f))

    fun initRandomizers(seed: Long): Long {
        val random = Random(seed)
        nx = FullNoise(random.nextLong())
        ny = FullNoise(random.nextLong())
        nz = FullNoise(random.nextLong())
        return seed
    }

    fun getMagneticField(position: Vector3f, time: Double): Vector3f {
        val scale = fieldScale[time]
        val px = (position.x * scale.x)
        val py = (position.y * scale.y)
        val pz = (position.z * scale.z)
        val pw = (time * scale.w).toFloat()
        return Vector3f(
            nx[px, py, pz, pw],
            ny[px, py, pz, pw],
            nz[px, py, pz, pw]
        )
    }

    override fun getForce(state: ParticleState, time: Double, particles: List<Particle>): Vector3f {
        val velocity = state.dPosition
        val position = state.position
        val localMagneticField = getMagneticField(position, time)
        return velocity.cross(localMagneticField * strength[time], Vector3f())
    }

    override fun listProperties(): List<InspectableAnimProperty> {
        return super.listProperties() + listOf(
            InspectableAnimProperty(
                fieldScale, NameDesc(
                    "Field Scale",
                    "How quickly the field is changing; in x,y,z and time direction",
                    "obj.effect.fieldScale"
                )
            )
        )
    }

    override fun createInspector(
        inspected: List<Inspectable>, list: PanelListY, style: Style,
        getGroup: (NameDesc) -> SettingCategory
    ) {
        super.createInspector(inspected, list, style, getGroup)
        getGroup(NameDesc("Force Field", "", "obj.forces")) +=
            vi(inspected, "Seed", "For the random component", "forceField.seed", null, seed, style) { it, _ ->
                for (x in inspected) if (x is NoisyLorentzForce) x.seed = it
            }
    }

    override fun save(writer: BaseWriter) {
        super.save(writer)
        writer.writeLong("seed", nx.seed)
        writer.writeObject(this, "fieldScale", fieldScale)
    }

    override fun setProperty(name: String, value: Any?) {
        when (name) {
            "fieldScale" -> fieldScale.copyFrom(value)
            "seed" -> seed = initRandomizers(value as? Long ?: return)
            else -> super.setProperty(name, value)
        }
    }

    override val className get() = "NoisyLorentzForce"

}