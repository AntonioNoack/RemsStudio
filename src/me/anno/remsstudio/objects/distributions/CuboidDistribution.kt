package me.anno.remsstudio.objects.distributions

import me.anno.ecs.components.mesh.shapes.CubemapModel.cubemapLineModel
import me.anno.ui.editor.sceneView.Grid
import org.joml.Matrix4fArrayList
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f

class CuboidDistribution(center: Vector4f, size: Vector4f, rotation: Vector4f = Vector4f()) : CenterSizeDistribution(
    "Cuboid",
    "Selects points from the cuboid shape randomly, uniformly", "obj.dist.cuboid",
    center, size, rotation
) {

    constructor() : this(0f, 1f)
    constructor(center: Float, size: Float) : this(Vector4f(center), Vector4f(size))
    constructor(center: Vector2f, size: Vector2f) : this(
        Vector4f(center.x, center.y, center.x, center.y),
        Vector4f(size.x, size.y, size.x, size.y)
    )

    constructor(center: Vector3f, size: Vector3f) : this(Vector4f(center, 0f), Vector4f(size, 0f))

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
        Grid.drawBuffer(stack, color, cubemapLineModel)
    }

    override val className get() = "CuboidDistribution"

}