package me.anno.remsstudio.objects.particles

import me.anno.gpu.FinalRendering.onMissingResource
import me.anno.maths.Maths
import me.anno.remsstudio.objects.Transform
import me.anno.remsstudio.objects.particles.forces.ForceField
import me.anno.utils.pooling.JomlPools
import me.anno.utils.structures.lists.UnsafeArrayList
import me.anno.utils.types.Floats.f2
import me.anno.utils.types.Floats.toRadians
import org.joml.Matrix4fArrayList
import org.joml.Vector3f
import org.joml.Vector4f
import kotlin.math.max
import kotlin.math.min

@Suppress("MemberVisibilityCanBePrivate")
class Particle(
    var type: Transform,
    val birthTime: Double,
    val lifeTime: Double,
    val mass: Float,
    val color: Vector4f,
    val scale: Vector3f,
    simulationStep: Double
) {

    private val isScaled = scale.x != 1f || scale.y != 1f || scale.z != 1f

    private val maxStateCount = max((lifeTime / simulationStep).toInt() + 2, 2)
    val hasDied get() = states.size >= maxStateCount
    val opacity get() = color.w

    override fun toString(): String {
        return "Particle[${type.className}, ${birthTime.f2()}]"
    }

    val states = UnsafeArrayList<ParticleState>()

    private val tmpPosition = Vector3f()
    private val tmpRotation = Vector3f()

    fun lastTime(simulationStep: Double) = birthTime + (states.size - 2) * simulationStep

    fun getValue(index0: Int, indexF: Float, dst: Vector3f, getValue: (ParticleState) -> Vector3f): Vector3f {
        val state0 = states.getOrElse(index0) { states.last() }
        val state1 = states.getOrElse(index0 + 1) { states.last() }
        return getValue(state0).lerp(getValue(state1), indexF, dst)
    }

    /**
     * gets the position at index index0, with fractional part indexF
     * used in force fields to display them
     * */
    fun getPosition(index0: Int, indexF: Float): Vector3f {
        val state0 = states.getOrElse(index0) { states.last() }
        val state1 = states.getOrElse(index0 + 1) { states.last() }
        return state0.position.lerp(state1.position, indexF, tmpPosition)
    }

    fun isAlive(time: Double) = (time - birthTime) in 0.0..lifeTime

    fun getLifeOpacity(time: Double, timeStep: Double, fadingIn: Double, fadingOut: Double): Double {
        // if (lifeTime < timeStep) return 0.0
        val particleTime = time - birthTime
        if (particleTime <= 0.0 || particleTime >= lifeTime) return 0.0
        val fading = fadingIn + fadingOut
        if (fading > lifeTime) {
            return getLifeOpacity(time, timeStep, lifeTime * fadingIn / fading, lifeTime * fadingOut / fading)
        }
        if (particleTime < fadingIn) return particleTime / fadingIn
        if (particleTime > lifeTime - fadingOut) return (lifeTime - particleTime) / fadingOut
        return 1.0
    }

    fun draw(
        stack: Matrix4fArrayList,
        time: Double, color: Vector4f,
        simulationStep: Double,
        fadeIn: Double, fadeOut: Double
    ) {
        val lifeOpacity = getLifeOpacity(time, simulationStep, fadeIn, fadeOut).toFloat()
        val opacity = Maths.clamp(lifeOpacity * this.color.w, 0f, 1f)
        if (opacity > 1e-3f) {// else not visible
            stack.next {
                try {

                    val particleTime = time - birthTime
                    val index = particleTime / simulationStep
                    val index0 = index.toInt()
                    val indexF = Maths.fract(index).toFloat()

                    val state0 = states[min(index0, states.lastIndex)]
                    val state1 = states[min(index0 + 1, states.lastIndex)]

                    val position = state0.position.lerp(state1.position, indexF, tmpPosition)
                    val rotation = state0.rotation.lerp(state1.rotation, indexF, tmpRotation)

                    if (position.lengthSquared() > 1e-26f) stack.translate(position)
                    if (rotation.y != 0f) stack.rotateY(rotation.y.toRadians())
                    if (rotation.x != 0f) stack.rotateX(rotation.x.toRadians())
                    if (rotation.z != 0f) stack.rotateZ(rotation.z.toRadians())
                    if (isScaled) stack.scale(scale)

                    // normalize time for calculated functions?
                    // node editor? like in Blender or Unreal Engine
                    val particleColor = JomlPools.vec4f.create()
                    this.color.mul(color, particleColor)
                    type.draw(stack, particleTime, particleColor)
                    JomlPools.vec4f.sub(1)

                } catch (_: IndexOutOfBoundsException) {
                    onMissingResource("Particles computing", null)
                }
            }
        }
    }

    fun step(simulationStep: Double, forces: List<ForceField>, aliveParticles: List<Particle>) {

        val oldState = states.last()
        val force = Vector3f()
        val time = states.size * simulationStep + birthTime
        for (field in forces) {
            val subForce = field.getForce(oldState, time, aliveParticles)
            val forceLength = subForce.length()
            if (forceLength.isFinite()) {
                force.add(
                    if (forceLength < 1000f) {
                        subForce
                    } else {
                        subForce * (1000f / forceLength)
                    }
                )
            }
        }
        val ddPosition = Vector3f(force).div(mass)
        val dt = simulationStep.toFloat()
        val dPosition = oldState.dPosition + ddPosition * dt
        val position = oldState.position + dPosition * dt
        val newState =
            ParticleState(position, dPosition, oldState.rotation + oldState.dRotation * dt, oldState.dRotation)
        states.add(newState)

    }

}