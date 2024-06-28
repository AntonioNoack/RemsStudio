package me.anno.remsstudio.objects.models

import me.anno.ecs.components.mesh.Mesh
import me.anno.gpu.buffer.DrawMode
import me.anno.remsstudio.RemsStudio
import me.anno.remsstudio.gpu.ShaderLibV2
import me.anno.utils.types.Floats.toRadians
import org.joml.Matrix4fArrayList
import org.joml.Vector3f
import org.joml.Vector4f
import kotlin.math.tan

object CameraModel {

    fun drawCamera(
        stack: Matrix4fArrayList,
        offset: Float,
        color: Vector4f,
        fov: Float, near: Float, far: Float
    ) {

        stack.translate(0f, 0f, offset)

        val scaleZ = 1f
        val scaleY = scaleZ * tan(fov.toRadians() / 2f)
        val scaleX = scaleY * RemsStudio.targetWidth / RemsStudio.targetHeight
        stack.scale(scaleX, scaleY, scaleZ)
        val shader = ShaderLibV2.lineShader3D.value

        // todo show the standard level only on user request, or when DOF is enabled
        // todo render the intersections instead
        shader.use()
        shader.m4x4("transform", stack)
        shader.v4f("color", color)
        shader.v4f("tint", -1)
        cameraModel.draw(null, shader, 0)

        stack.scale(near)
        shader.m4x4("transform", stack)
        cameraModel.draw(null, shader, 0)

        stack.scale(far / near)
        shader.m4x4("transform", stack)
        cameraModel.draw(null, shader, 0)

    }

    private val cameraModel = Mesh().apply {

        val vertexCount = 16
        val positions = FloatArray(vertexCount * 3)

        // points
        val zero = Vector3f()
        val p00 = Vector3f(-1f, -1f, -1f)
        val p01 = Vector3f(-1f, +1f, -1f)
        val p10 = Vector3f(+1f, -1f, -1f)
        val p11 = Vector3f(+1f, +1f, -1f)

        var i = 0
        fun put(v: Vector3f) {
            positions[i++] = v.x
            positions[i++] = v.y
            positions[i++] = v.z
        }

        // lines to frame
        put(zero)
        put(p00)

        put(zero)
        put(p01)

        put(zero)
        put(p10)

        put(zero)
        put(p11)

        // frame
        put(p00)
        put(p01)

        put(p01)
        put(p11)

        put(p11)
        put(p10)

        put(p10)
        put(p00)

        this.positions = positions
        drawMode = DrawMode.LINES
    }
}