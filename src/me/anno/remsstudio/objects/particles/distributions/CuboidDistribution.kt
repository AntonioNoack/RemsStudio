package me.anno.remsstudio.objects.particles.distributions

import me.anno.ecs.components.mesh.shapes.CubemapModel
import me.anno.language.translation.NameDesc
import me.anno.ui.editor.sceneView.Grid
import org.joml.Matrix4fArrayList
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f
import kotlin.math.abs

class CuboidDistribution(center: Vector4f, size: Vector4f, rotation: Vector4f = Vector4f()) :
    CenterSizeDistribution(
        NameDesc("Cuboid", "Selects points from the cuboid shape randomly, uniformly", "obj.dist.cuboid"),
        center, size, rotation
    ) {

    constructor() : this(0f, 1f)
    constructor(center: Float, size: Float) : this(Vector4f(center), Vector4f(size))

    override fun nextV1(): Float {
        return transform(random.nextFloat() * 2f - 1f)
    }

    override fun maxV1(): Float {
        return abs(scale.x) + center.x
    }

    override fun nextV2(): Vector2f {
        return transform(
            Vector2f(
                random.nextFloat() * 2f - 1f,
                random.nextFloat() * 2f - 1f
            )
        )
    }

    override fun nextV3(): Vector3f {
        return transform(
            Vector3f(
                random.nextFloat() * 2f - 1f,
                random.nextFloat() * 2f - 1f,
                random.nextFloat() * 2f - 1f
            )
        )
    }

    override fun nextV4(): Vector4f {
        return transform(
            Vector4f(
                random.nextFloat() * 2f - 1f,
                random.nextFloat() * 2f - 1f,
                random.nextFloat() * 2f - 1f,
                random.nextFloat() * 2f - 1f
            )
        )
    }

    override fun drawTransformed(stack: Matrix4fArrayList, color: Vector4f) {
        // draw cube out of lines
        Grid.drawLineMesh(null, stack, color, CubemapModel.model.front)
    }

    override val className get() = "CuboidDistribution"

}