package me.anno.remsstudio.objects.particles.distributions

import me.anno.ecs.components.mesh.shapes.CubemapModel
import me.anno.language.translation.NameDesc
import me.anno.maths.Maths.max
import me.anno.ui.editor.sceneView.Grid
import org.joml.Matrix4fArrayList
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f
import kotlin.math.abs

class CuboidHullDistribution(center: Vector4f, size: Vector4f, rotation: Vector4f = Vector4f()) :
    CenterSizeDistribution(
        NameDesc("Cuboid Hull", "Selects points from the cuboid hull randomly, uniformly", "obj.dist.cuboidHull"),
        center, size, rotation
    ) {

    constructor() : this(0f, 1f)
    constructor(center: Float, size: Float) : this(Vector4f(center), Vector4f(size))

    override fun nextV1(): Float {
        val x = random.nextFloat()
        return transform(if (x > 0.5f) 1f else -1f)
    }

    override fun maxV1(): Float {
        return abs(scale.x) + center.x
    }

    override fun nextV2(): Vector2f {
        val maxScale = max(abs(scale.x), abs(scale.y))
        var x = (random.nextFloat() * 2f - 1f) * scale.x / maxScale
        var y = (random.nextFloat() * 2f - 1f) * scale.y / maxScale
        if (abs(x) > abs(y)) {
            x = if (x > 0f) +1f else -1f
            y *= maxScale / scale.y // undo scaling
        } else {
            y = if (y > 0f) +1f else -1f
            x *= maxScale / scale.x // undo scaling
        }
        if (x.isNaN()) x = 0f
        if (y.isNaN()) y = 0f
        return transform(Vector2f(x, y))
    }

    override fun nextV3(): Vector3f {
        val maxScale = max(abs(scale.x), abs(scale.y), abs(scale.z), 1e-16f)
        var x = (random.nextFloat() * 2f - 1f) * scale.x / maxScale
        var y = (random.nextFloat() * 2f - 1f) * scale.y / maxScale
        var z = (random.nextFloat() * 2f - 1f) * scale.z / maxScale
        val ax = abs(x)
        val ay = abs(y)
        val az = abs(z)
        when (max(ax, max(ay, az))) {
            ax -> {
                x = if (x > 0f) +1f else -1f
                y *= maxScale / scale.y
                z *= maxScale / scale.z
            }

            ay -> {
                y = if (y > 0f) +1f else -1f
                x *= maxScale / scale.x
                z *= maxScale / scale.z
            }

            else -> {
                z = if (z > 0f) +1f else -1f
                x *= maxScale / scale.x
                y *= maxScale / scale.y
            }
        }
        if (x.isNaN()) x = 0f
        if (y.isNaN()) y = 0f
        if (z.isNaN()) z = 0f
        return transform(Vector3f(x, y, z))
    }

    override fun nextV4(): Vector4f {
        val maxScale = max(abs(scale.x), abs(scale.y), abs(scale.z), abs(scale.w), 1e-16f)
        var x = (random.nextFloat() * 2f - 1f) * scale.x / maxScale
        var y = (random.nextFloat() * 2f - 1f) * scale.y / maxScale
        var z = (random.nextFloat() * 2f - 1f) * scale.z / maxScale
        var w = (random.nextFloat() * 2f - 1f) * scale.w / maxScale
        val ax = abs(x)
        val ay = abs(y)
        val az = abs(z)
        val aw = abs(w)
        when (max(ax, ay, az, aw)) {
            ax -> {
                x = if (x > 0f) +1f else -1f
                y *= maxScale / scale.y
                z *= maxScale / scale.z
                w *= maxScale / scale.w
            }
            ay -> {
                y = if (y > 0f) +1f else -1f
                x *= maxScale / scale.x
                z *= maxScale / scale.z
                w *= maxScale / scale.w
            }
            az -> {
                z = if (z > 0f) +1f else -1f
                x *= maxScale / scale.x
                y *= maxScale / scale.y
                w *= maxScale / scale.w
            }
            else -> {
                w = if (w > 0f) +1f else -1f
                x *= maxScale / scale.x
                y *= maxScale / scale.y
                z *= maxScale / scale.z
            }
        }
        if (x.isNaN()) x = 0f
        if (y.isNaN()) y = 0f
        if (z.isNaN()) z = 0f
        if (w.isNaN()) w = 0f
        return transform(Vector4f(x, y, z, w))
    }

    override fun drawTransformed(stack: Matrix4fArrayList, color: Vector4f) {
        // draw cube out of lines
        Grid.drawLineMesh(null, stack, color, CubemapModel.model.front)
    }

    override val className get() = "CuboidHullDistribution"

}