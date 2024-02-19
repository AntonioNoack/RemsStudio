package me.anno.remsstudio.objects.models

import me.anno.ecs.components.mesh.Mesh
import me.anno.gpu.buffer.DrawMode
import org.joml.Vector2f

object ArrowModel {

    private const val smallHeight = 0.25f
    private val leftTop = Vector2f(-1f, smallHeight)
    private val leftBottom = Vector2f(-1f, -smallHeight)

    private const val center = 0.2f
    private val centerTop = Vector2f(center, smallHeight)
    private val centerBottom = Vector2f(center, -smallHeight)

    private const val arrow = 0.5f
    private val arrowTop = Vector2f(center, arrow)
    private val arrowBottom = Vector2f(center, -arrow)
    private val front = Vector2f(+1f, 0f)

    val arrowModel by lazy { createModel() }
    val arrowLineModel = createLineModel()

    private fun createModel(): Mesh {

        val triangleCount = 6
        val vertexCount = triangleCount * 3
        val positions = FloatArray(vertexCount * 3)

        var i = 0
        fun add(a: Vector2f) {
            positions[i++] = a.x
            positions[i++] = a.y
            positions[i++] = 0f
        }

        fun addTriangle(a: Vector2f, b: Vector2f, c: Vector2f) {
            // from both sides
            add(a)
            add(b)
            add(c)
            add(a)
            add(c)
            add(b)
        }

        addTriangle(leftTop, centerBottom, centerTop)
        addTriangle(leftBottom, leftTop, centerBottom)
        addTriangle(arrowTop, arrowBottom, front)

        val buffer = Mesh()
        buffer.positions = positions
        return buffer
    }

    private fun createLineModel(): Mesh {

        val vertexCount = 2 * 7
        val positions = FloatArray(vertexCount * 3)


        var i = 0
        fun addLine(a: Vector2f, b: Vector2f) {
            positions[i++] = a.x
            positions[i++] = a.y
            positions[i++] = 0f
            positions[i++] = b.x
            positions[i++] = b.y
            positions[i++] = 0f
        }

        addLine(leftBottom, leftTop)
        addLine(leftTop, centerTop)
        addLine(centerTop, arrowTop)
        addLine(arrowTop, front)
        addLine(front, arrowBottom)
        addLine(arrowBottom, centerBottom)
        addLine(centerBottom, leftBottom)

        val buffer = Mesh()
        buffer.positions = positions
        buffer.drawMode = DrawMode.LINES
        return buffer

    }
}