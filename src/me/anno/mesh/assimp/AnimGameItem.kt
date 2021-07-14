package me.anno.mesh.assimp

import me.anno.ecs.Entity
import me.anno.ecs.components.mesh.Mesh
import me.anno.gpu.GFX
import me.anno.gpu.shader.Shader
import me.anno.utils.Maths
import org.apache.logging.log4j.LogManager
import org.joml.Matrix4f
import org.joml.Matrix4x3f
import org.lwjgl.opengl.GL21
import org.lwjgl.system.MemoryUtil
import java.nio.FloatBuffer
import kotlin.math.min

class AnimGameItem(
    val hierarchy: Entity,
    val meshes: List<Mesh>,
    val bones: List<Bone>,
    val animations: Map<String, Animation>
) {

    fun uploadJointMatrices(shader: Shader, animation: Animation, time: Double) {
        val location = shader.getUniformLocation("jointTransforms")
        if (location < 0) return
        // most times the duration is specified in milli seconds
        val frames = animation.frames
        val frameCount = frames.size
        var frameIndexFloat = ((time * frameCount / animation.duration) % frameCount).toFloat()
        if (frameIndexFloat < 0) frameIndexFloat += frameCount
        val frameIndex0 = frameIndexFloat.toInt() % frameCount
        val frameIndex1 = (frameIndex0 + 1) % frameCount
        val frame0 = frames[frameIndex0]
        val frame1 = frames[frameIndex1]
        val fraction = frameIndexFloat - frameIndex0
        val invFraction = 1f - fraction
        val matrices0 = frame0.matrices
        val matrices1 = frame1.matrices
        shader.use()
        val boneCount = min(matrices0.size, maxBones)
        matrixBuffer.limit(matrixSize * boneCount)
        for (index in 0 until boneCount) {
            val matrix0 = matrices0[index]
            val matrix1 = matrices1[index]
            tmpBuffer.position(0)
            val offset = index * matrixSize
            matrixBuffer.position(offset)
            get(matrix0, matrixBuffer)
            get(matrix1, tmpBuffer)
            // matrix interpolation
            for (i in 0 until matrixSize) {
                val j = offset + i
                matrixBuffer.put(j, matrixBuffer[j] * invFraction + fraction * tmpBuffer[i])
            }
        }
        matrixBuffer.position(0)
        GL21.glUniformMatrix4x3fv(location, false, matrixBuffer)
    }

    fun get(src: Matrix4x3f, dst: FloatBuffer) {
        src.get(dst)
    }


    fun get(src: Matrix4f, dst: FloatBuffer) {

        dst.put(src.m00())
        dst.put(src.m01())
        dst.put(src.m02())

        dst.put(src.m10())
        dst.put(src.m11())
        dst.put(src.m12())

        dst.put(src.m20())
        dst.put(src.m21())
        dst.put(src.m22())

        dst.put(src.m30())
        dst.put(src.m31())
        dst.put(src.m32())

    }

    companion object {
        val matrixSize = 12
        val maxBones = Maths.clamp((GFX.maxVertexUniforms - (matrixSize * 3)) / matrixSize, 4, 256)
        val matrixBuffer = MemoryUtil.memAllocFloat(matrixSize * maxBones)
        val tmpBuffer = MemoryUtil.memAllocFloat(matrixSize)
        private val LOGGER = LogManager.getLogger(AnimGameItem::class)
    }

    // todo number input: cannot enter 0.01 from left to right, because the 0 is removed instantly
    // todo cubemap from 6 images...

}
