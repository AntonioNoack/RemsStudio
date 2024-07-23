package me.anno.remsstudio.objects.particles.distributions

import me.anno.language.translation.NameDesc
import me.anno.remsstudio.objects.inspectable.InspectableVector
import me.anno.utils.types.Floats.toRadians
import org.joml.*
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

@Suppress("MemberVisibilityCanBePrivate")
abstract class CenterSizeDistribution(
    nameDesc: NameDesc,
    center: Vector4f,
    val scale: Vector4f,
    val rotation: Vector4f
) : CenterDistribution(nameDesc, center) {

    fun transform(v: Float): Float = v * scale.x + center.x

    fun transform(v: Vector2f): Vector2f =
        rotate(v.mul(Vector2f(scale.x, scale.y))).add(center.x, center.y)

    fun transform(v: Vector3f): Vector3f =
        rotate(v.mul(Vector3f(scale.x, scale.y, scale.z))).add(center.x, center.y, center.z)

    fun transform(v: Vector4f): Vector4f = rotate(v.mul(scale)).add(center)

    fun rotate(vector: Vector2f): Vector2f {
        val angleDegrees = rotation.x + rotation.y + rotation.z
        if (angleDegrees == 0f) return vector
        val angle = angleDegrees.toRadians()
        val cos = cos(angle)
        val sin = sin(angle)
        return Vector2f(
            cos * vector.x - sin * vector.y,
            sin * vector.x + cos * vector.y
        )
    }

    fun rotate(vector: Vector3f): Vector3f {
        if (rotation.x == 0f && rotation.y == 0f && rotation.z == 0f) return vector
        val quat = Quaternionf()
        if (rotation.y != 0f) quat.rotateY(rotation.y.toRadians())
        if (rotation.x != 0f) quat.rotateX(rotation.x.toRadians())
        if (rotation.z != 0f) quat.rotateZ(rotation.z.toRadians())
        return quat.transform(vector)
    }

    fun rotate(vector: Vector4f): Vector4f {
        return Vector4f(Vector3f(vector.x, vector.y, vector.z), vector.w)
    }

    override fun onDraw(stack: Matrix4fArrayList, color: Vector4f) {
        // draw a sphere
        stack.next {
            stack.translate(center.x, center.y, center.z)
            if (rotation.y != 0f) stack.rotateY(rotation.y.toRadians())
            if (rotation.x != 0f) stack.rotateX(rotation.x.toRadians())
            if (rotation.z != 0f) stack.rotateZ(rotation.z.toRadians())
            stack.scale(scale.x, scale.y, scale.z)
            drawTransformed(stack, color)
        }
    }

    abstract fun drawTransformed(stack: Matrix4fArrayList, color: Vector4f)

    override fun nextV1(): Float {
        return transform(random.nextFloat() - 0.5f)
    }

    override fun maxV1(): Float {
        return 0.5f * abs(scale.x) + center.x
    }

    override fun listProperties(): List<InspectableVector> {
        val prefix = "obj.distribution"
        return super.listProperties() + listOf(
            InspectableVector(scale, NameDesc("Radius / Size", "", "$prefix.radius"), InspectableVector.PType.SCALE),
            InspectableVector(rotation, NameDesc("Rotation", "", "$prefix.rotation"), InspectableVector.PType.ROTATION)
        )
    }

    companion object {
        const val POSITION_INDEX = 0
        const val SCALE_INDEX = 1
        const val ROTATION_INDEX = 2
    }

}