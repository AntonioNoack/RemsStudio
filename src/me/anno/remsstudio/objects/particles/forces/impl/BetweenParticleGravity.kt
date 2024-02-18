package me.anno.remsstudio.objects.particles.forces.impl

import me.anno.io.Saveable
import me.anno.io.base.BaseWriter
import me.anno.remsstudio.animation.AnimatedProperty
import me.anno.remsstudio.objects.particles.forces.ForceField
import me.anno.remsstudio.objects.inspectable.InspectableAnimProperty
import me.anno.remsstudio.objects.particles.Particle
import me.anno.remsstudio.objects.particles.ParticleState
import me.anno.maths.Maths.pow
import org.joml.Matrix4fArrayList
import org.joml.Vector3f
import org.joml.Vector4f

class BetweenParticleGravity : ForceField(
    "Between-Particle Gravity",
    "Gravity towards all other particles; expensive to compute", "betweenParticleGravity"
) {

    val exponent = AnimatedProperty.float(2f)

    override fun onDraw(stack: Matrix4fArrayList, time: Double, color: Vector4f) {
        super.onDraw(stack, time, color)
        drawForcePerParticle(stack, time, color)
    }

    override fun getForce(state: ParticleState, time: Double, particles: List<Particle>): Vector3f {
        val sum = Vector3f()
        val position = state.position
        val exponent = -(exponent[time] + 1f)
        for (secondParticle in particles) {
            // particle is alive
            val secondState = secondParticle.states.lastOrNull() ?: continue
            if (secondState !== state) {
                val delta = secondState.position - position
                val l = delta.length()
                sum.add(delta * pow(l, exponent))
            }
        }
        return sum.mul(strength[time])
    }

    override fun listProperties(): List<InspectableAnimProperty> {
        return super.listProperties() + listOf(
            InspectableAnimProperty(
                exponent,
                "Exponent",
                "How quickly the force declines with distance"
            )
        )
    }

    override fun save(writer: BaseWriter) {
        super.save(writer)
        writer.writeObject(this, "exponent", exponent)
    }

    override fun setProperty(name: String, value: Any?) {
        when(name){
            "exponent" -> exponent.copyFrom(value)
            else -> super.setProperty(name, value)
        }
    }

    override val className get() = "MultiGravityForce"

}