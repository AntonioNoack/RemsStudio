package me.anno.remsstudio.objects.particles.forces.impl

import me.anno.io.ISaveable
import me.anno.io.base.BaseWriter
import me.anno.maths.noise.FullNoise
import me.anno.remsstudio.animation.AnimatedProperty
import me.anno.remsstudio.objects.inspectable.InspectableAnimProperty
import me.anno.remsstudio.objects.particles.Particle
import me.anno.remsstudio.objects.particles.ParticleState
import me.anno.studio.Inspectable
import me.anno.ui.Style
import me.anno.ui.base.groups.PanelListY
import me.anno.ui.editor.SettingCategory
import org.joml.Vector3f
import org.joml.Vector4f
import java.util.*

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
                fieldScale,
                "Field Scale",
                "How quickly the field is changing; in x,y,z and time direction"
            )
        )
    }

    override fun createInspector(
        inspected: List<Inspectable>,
        list: PanelListY,
        style: Style,
        getGroup: (title: String, description: String, dictSubPath: String) -> SettingCategory
    ) {
        super.createInspector(inspected, list, style, getGroup)
        getGroup("Force Field", "", "forces") +=
            vi(inspected, "Seed", "For the random component", null, seed, style) { it, _ ->
                for (x in inspected) if (x is NoisyLorentzForce) x.seed = it
            }
    }

    override fun save(writer: BaseWriter) {
        super.save(writer)
        writer.writeLong("seed", nx.seed)
        writer.writeObject(this, "fieldScale", fieldScale)
    }

    override fun readObject(name: String, value: ISaveable?) {
        when (name) {
            "fieldScale" -> fieldScale.copyFrom(value)
            else -> super.readObject(name, value)
        }
    }

    override fun readLong(name: String, value: Long) {
        when (name) {
            "seed" -> seed = initRandomizers(value)
            else -> super.readLong(name, value)
        }
    }

    override val className get() = "NoisyLorentzForce"

}