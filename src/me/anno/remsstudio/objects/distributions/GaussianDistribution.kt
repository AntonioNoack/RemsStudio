package me.anno.remsstudio.objects.distributions

import org.joml.*
import kotlin.math.sqrt

class GaussianDistribution(center: Vector4f, size: Vector4f, rotation: Vector4f = Vector4f()) : CenterSizeDistribution(
    "Gaussian",
    "Gaussian- or Normal Distribution; sum of many small effects", "obj.dist.gaussian",
    center, size, rotation
) {

    constructor() : this(Vector4f(), Vector4f())

    override fun nextV1(): Float {
        return transform(
            random.nextGaussian().toFloat() * gaussianScale
        )
    }

    override fun nextV2(): Vector2f {
        return transform(
            Vector2f(
                random.nextGaussian().toFloat(),
                random.nextGaussian().toFloat()
            ).mul(gaussianScale)
        )
    }

    override fun nextV3(): Vector3f {
        return transform(
            Vector3f(
                random.nextGaussian().toFloat(),
                random.nextGaussian().toFloat(),
                random.nextGaussian().toFloat()
            ).mul(gaussianScale)
        )
    }

    override fun nextV4(): Vector4f {
        return transform(
            Vector4f(
                random.nextGaussian().toFloat(),
                random.nextGaussian().toFloat(),
                random.nextGaussian().toFloat(),
                random.nextGaussian().toFloat()
            ).mul(gaussianScale)
        )
    }

    override fun drawTransformed(stack: Matrix4fArrayList, color: Vector4f) {
        val i0 = 0.68f
        val i1 = 0.95f
        val i2 = 0.99f
        stack.scale(gaussianScale)
        drawSphere(stack, color, 1f)
        stack.scale(2f)
        drawSphere(stack, color, sqrt((i1 - i0) / i0))
        stack.scale(3f / 2f)
        drawSphere(stack, color, sqrt((i2 - i1) / i0))
    }

    override val className get() = "GaussianDistribution"

    companion object {
        const val gaussianScale = 0.5f // to be better comparable to sphere hull and sphere volume
    }

}