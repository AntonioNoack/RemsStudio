package me.anno.remsstudio.objects.models

import me.anno.ecs.components.mesh.Mesh
import me.anno.gpu.GFX
import me.anno.gpu.buffer.DrawMode
import me.anno.maths.Maths
import me.anno.maths.Maths.PIf
import me.anno.ui.editor.sceneView.Grid
import org.joml.Matrix4fArrayList
import org.joml.Vector4f
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

object SpeakerModel {

    private const val speakerEdges = 64
    private val speakerModel = Mesh().apply {

        val vertexCount = speakerEdges * 3 * 2 + 4 * 2 * 2

        var i = 0
        val positions = FloatArray(vertexCount * 3)

        fun addLine(r0: Float, d0: Float, r1: Float, d1: Float, dx: Int, dy: Int) {
            positions[i++] = r0 * dx
            positions[i++] = r0 * dy
            positions[i++] = d0
            positions[i++] = r1 * dx
            positions[i++] = r1 * dy
            positions[i++] = d1
        }

        fun addRing(radius: Float, depth: Float, edges: Int) {
            val dr = (Math.PI * 2 / edges).toFloat()
            fun putPoint(k: Int) {
                val angle1 = dr * k
                positions[i++] = sin(angle1) * radius
                positions[i++] = cos(angle1) * radius
                positions[i++] = depth
            }
            putPoint(0)
            for (k in 1 until edges) {
                putPoint(k)
                putPoint(k)
            }
            putPoint(0)
        }

        val scale = 0.5f

        addRing(0.45f * scale, 0.02f * scale, speakerEdges)
        addRing(0.50f * scale, 0.01f * scale, speakerEdges)
        addRing(0.80f * scale, 0.30f * scale, speakerEdges)

        val dx = listOf(0, 0, 1, -1)
        val dy = listOf(1, -1, 0, 0)
        for (k in 0 until 4) {
            addLine(0.45f * scale, 0.02f * scale, 0.50f * scale, 0.01f * scale, dx[k], dy[k])
            addLine(0.50f * scale, 0.01f * scale, 0.80f * scale, 0.30f * scale, dx[k], dy[k])
        }

        drawMode = DrawMode.LINES
        this.positions = positions

    }

    fun drawSpeakers(
        stack: Matrix4fArrayList,
        color: Vector4f,
        is3D: Boolean,
        amplitude: Float
    ) {
        if (GFX.isFinalRendering) return
        color.w = Maths.clamp(color.w * 0.5f * abs(amplitude), 0f, 1f)
        if (is3D) {
            val r = 0.85f
            stack.translate(r, 0f, 0f)
            Grid.drawLineMesh(stack, color, speakerModel)
            stack.translate(-2 * r, 0f, 0f)
            Grid.drawLineMesh(stack, color, speakerModel)
        } else {
            // mark the speaker with yellow,
            // and let it face upwards (+y) to symbolize, that it's global
            color.z *= 0.8f // yellow
            stack.rotateX(-PIf * 0.5f)
            Grid.drawLineMesh(stack, color, speakerModel)
        }
    }
}