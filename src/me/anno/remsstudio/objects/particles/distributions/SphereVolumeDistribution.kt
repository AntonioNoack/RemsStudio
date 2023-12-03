package me.anno.remsstudio.objects.particles.distributions

import me.anno.language.translation.NameDesc
import org.joml.Matrix4fArrayList
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f
import kotlin.math.abs

class SphereVolumeDistribution(center: Vector4f, size: Vector4f, rotation: Vector4f = Vector4f()) :
    CenterSizeDistribution(
        NameDesc("Sphere", "Points from the inside of the sphere", "obj.dist.sphere"),
        center, size, rotation
    ) {

    constructor() : this(0f, 1f)
    constructor(center: Vector3f, size: Float) : this(Vector4f(center, 0f), Vector4f(size))
    constructor(center: Float, size: Float) : this(Vector3f(center), size)

    override fun nextV1(): Float {
        return transform(random.nextFloat() * 2f - 1f)
    }

    override fun maxV1(): Float {
        return abs(scale.x) + center.x
    }

    override fun nextV2(): Vector2f {
        var x: Float
        var y: Float
        do {
            x = random.nextFloat() * 2f - 1f
            y = random.nextFloat() * 2f - 1f
        } while (x * x + y * y > 1f)
        return transform(Vector2f(x, y))
    }

    override fun nextV3(): Vector3f {
        var x: Float
        var y: Float
        var z: Float
        do {
            x = random.nextFloat() * 2f - 1f
            y = random.nextFloat() * 2f - 1f
            z = random.nextFloat() * 2f - 1f
        } while (x * x + y * y + z * z > 1f)
        return transform(Vector3f(x, y, z))
    }

    override fun nextV4(): Vector4f {
        var x: Float
        var y: Float
        var z: Float
        var w: Float
        do {
            x = random.nextFloat() * 2f - 1f
            y = random.nextFloat() * 2f - 1f
            z = random.nextFloat() * 2f - 1f
            w = random.nextFloat() * 2f - 1f
        } while (x * x + y * y + z * z + w * w > 1f)
        return transform(Vector4f(x, y, z, w))
    }

    override fun drawTransformed(stack: Matrix4fArrayList, color: Vector4f) {
        drawSphere(stack, color, 1f)
    }

    override val className get() = "SphereDistribution"

}