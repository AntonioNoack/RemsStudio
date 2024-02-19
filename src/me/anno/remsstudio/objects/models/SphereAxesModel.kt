package me.anno.remsstudio.objects.models

import me.anno.ecs.components.mesh.Mesh
import me.anno.gpu.buffer.DrawMode
import me.anno.maths.Maths.pow
import org.joml.Vector3f
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object SphereAxesModel {
    val sphereAxesModels = Array(5) { lazy { createLineModel(it) } }
    private fun createLineModel(sides0: Int): Mesh {

        val sideCount = pow(2f, sides0 + 3f).toInt()
        val vertexCount = sideCount * 9 * 2

        val positions = FloatArray(vertexCount * 3)

        var i = 0
        fun put(v: Vector3f) {
            positions[i++] = v.x
            positions[i++] = v.y
            positions[i++] = v.z
        }

        fun addAxis(func: (x: Float, y: Float) -> Vector3f) {
            val zero = func(1f, 0f)
            put(zero)
            for (j in 1 until sideCount) {
                val angle = j * 6.2830f / sideCount
                val v = func(cos(angle), sin(angle))
                put(v)
                put(v)
            }
            put(zero)
        }

        val s = sqrt(0.5f)

        // x y z
        addAxis { x, y -> Vector3f(x, y, 0f) }
        addAxis { x, y -> Vector3f(0f, x, y) }
        addAxis { x, y -> Vector3f(x, 0f, y) }

        // xy yz zx
        addAxis { x, y -> Vector3f(x, y * s, +y * s) }
        addAxis { x, y -> Vector3f(x, y * s, -y * s) }
        addAxis { x, y -> Vector3f(y * s, x, +y * s) }
        addAxis { x, y -> Vector3f(y * s, x, -y * s) }
        addAxis { x, y -> Vector3f(y * s, +y * s, x) }
        addAxis { x, y -> Vector3f(y * s, -y * s, x) }

        val buffer = Mesh()
        buffer.positions = positions
        buffer.drawMode = DrawMode.LINES
        return buffer
    }
}