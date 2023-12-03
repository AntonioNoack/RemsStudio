package me.anno.remsstudio.objects.particles.distributions

import me.anno.language.translation.NameDesc
import me.anno.remsstudio.Scene
import me.anno.remsstudio.objects.Transform.Companion.drawUICircle
import me.anno.remsstudio.objects.inspectable.InspectableVector
import me.anno.utils.Color.white4
import org.joml.Matrix4fArrayList
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f

abstract class CenterDistribution(
    nameDesc: NameDesc,
    val center: Vector4f,
) : Distribution(nameDesc) {

    override fun nextV1(): Float = center.x
    override fun maxV1(): Float = center.x

    override fun nextV2(): Vector2f {
        return Vector2f(center.x, center.y)
    }

    override fun nextV3(): Vector3f {
        return Vector3f(center.x, center.y, center.z)
    }

    override fun nextV4(): Vector4f {
        return Vector4f(center)
    }

    override fun draw(stack: Matrix4fArrayList, color: Vector4f) {
        stack.next {
            stack.translate(center.x, center.y, center.z)
            onDraw(stack, color)
        }
    }

    override fun onDraw(stack: Matrix4fArrayList, color: Vector4f) {
       Scene.drawSelectionRing(stack)
    }

    override fun listProperties(): List<InspectableVector> {
        return listOf(InspectableVector(center, "Center", InspectableVector.PType.DEFAULT))
    }
}