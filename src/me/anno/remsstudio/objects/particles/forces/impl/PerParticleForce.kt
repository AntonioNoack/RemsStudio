package me.anno.remsstudio.objects.particles.forces.impl

import me.anno.remsstudio.objects.particles.forces.ForceField
import org.joml.Matrix4fArrayList
import org.joml.Vector4f

abstract class PerParticleForce(displayName: String, description: String, dictSubPath: String) :
    ForceField(displayName, description, dictSubPath) {

    override fun onDraw(stack: Matrix4fArrayList, time: Double, color: Vector4f) {
        drawForcePerParticle(stack, time, color)
    }

}