package me.anno.remsstudio.objects.particles.forces.types

import me.anno.remsstudio.objects.particles.forces.ForceField
import me.anno.remsstudio.objects.particles.Particle
import me.anno.remsstudio.objects.particles.ParticleState
import org.joml.Vector3f

abstract class RelativeForceField(displayName: String, description: String, dictSubPath: String) :
    ForceField(displayName, description, dictSubPath) {

    abstract fun getForce(delta: Vector3f, time: Double): Vector3f

    override fun getForce(state: ParticleState, time: Double, particles: List<Particle>): Vector3f {
        val position = state.position
        val center = this.position[time]
        return getForce(position - center, time) * strength[time]
    }

}