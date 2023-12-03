package me.anno.remsstudio.objects.particles.distributions

import me.anno.language.translation.NameDesc
import org.joml.Matrix4fArrayList
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f
import kotlin.math.abs

class SphereHullDistribution(center: Vector4f, size: Vector4f, rotation: Vector4f = Vector4f()) :
    CenterSizeDistribution(
        NameDesc(
            "Sphere Hull",
            "Distributes points from the hull of a sphere or circle, not from the volume",
            "obj.dist.sphere.hull"
        ),
        center, size, rotation
    ) {

    constructor() : this(0f, 1f)
    constructor(center: Vector3f, size: Float) : this(Vector4f(center, 0f), Vector4f(size))
    constructor(center: Float, size: Float) : this(Vector3f(center), size)

    override fun nextV1(): Float {
        val x = random.nextFloat()
        return transform(if (x > 0.5f) 1f else -1f)
    }

    override fun maxV1(): Float {
        return abs(scale.x) + center.x
    }

    override fun nextV2(): Vector2f {
        return transform(
            Vector2f(
                random.nextFloat() - 0.5f,
                random.nextFloat() - 0.5f
            ).mul(scale.x, scale.y).normalize()
        )
    }

    override fun nextV3(): Vector3f {
        return transform(
            Vector3f(
                random.nextFloat() - 0.5f,
                random.nextFloat() - 0.5f,
                random.nextFloat() - 0.5f
            ).mul(scale.x, scale.y, scale.z).normalize()
        )
    }

    override fun nextV4(): Vector4f {
        return transform(
            Vector4f(
                random.nextFloat() - 0.5f,
                random.nextFloat() - 0.5f,
                random.nextFloat() - 0.5f,
                random.nextFloat() - 0.5f
            ).mul(scale).normalize()
        )
    }

    override fun drawTransformed(stack: Matrix4fArrayList, color: Vector4f) {
        drawSphere(stack, color, 1f)
    }

    override val className get() = "SphereHullDistribution"

}