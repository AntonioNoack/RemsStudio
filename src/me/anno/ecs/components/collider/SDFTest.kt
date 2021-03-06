package me.anno.ecs.components.collider

import me.anno.utils.image.ImageWriter
import org.joml.Vector3d

fun main() {

    val shapes = listOf(
        BoxCollider(),
        BoxCollider().apply {
            name = "BoxWithBorder"
            cornerRoundness = 0.5
        },
        SphereCollider(),
        CapsuleCollider().apply {
            radius = 0.8
            halfExtends = 0.8
        }
    )

    for (shape in shapes) {
        var name = shape.name
        if (name.isEmpty()) name = shape.className
        renderSDF(shape, "$name.png")
    }

}

fun renderSDF(collider: Collider, name: String) {

    val size = 4.0
    val res = 512

    val scale = Vector3d(res / size)
    val offset = Vector3d(size * 0.5)

    val dir = Vector3d()
    ImageWriter.writeImageFloat(res, res, name, 512) { x, y, _ ->
        val pos = Vector3d(x.toDouble(), y.toDouble(), 0.0)
        pos.mul(scale)
        pos.sub(offset)
        collider.getSignedDistance(pos, dir).toFloat()
    }

}