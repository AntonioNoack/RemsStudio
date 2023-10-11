package me.anno.remsstudio.objects.distributions

import me.anno.language.translation.Dict
import me.anno.remsstudio.objects.inspectable.InspectableVector
import me.anno.utils.types.Floats.toRadians
import org.joml.*
import kotlin.math.cos
import kotlin.math.sin

abstract class CenterSizeDistribution(
    displayName: String, description: String,
    val center: Vector4f, val scale: Vector4f,
    val rotation: Vector4f
) : Distribution(displayName, description) {

    constructor(
        displayName: String, description: String, dictPath: String,
        center: Vector4f, scale: Vector4f, rotation: Vector4f
    ) : this(
        Dict[displayName, dictPath], Dict[description, "$dictPath.desc"],
        center, scale, rotation
    )

    fun transform(v: Float) =
        v * scale.x + center.x

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

    override fun listProperties(): List<InspectableVector> {
        return listOf(
            InspectableVector(center, "Center", InspectableVector.PType.DEFAULT),
            InspectableVector(scale, "Radius / Size", InspectableVector.PType.SCALE),
            InspectableVector(rotation, "Rotation", InspectableVector.PType.ROTATION)
        )
    }

}