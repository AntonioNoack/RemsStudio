package me.anno.remsstudio.gpu

import me.anno.gpu.buffer.StaticBuffer
import me.anno.gpu.texture.Clamping
import me.anno.gpu.texture.Texture2D
import me.anno.image.svg.DrawSVGs
import me.anno.remsstudio.gpu.GFXx3Dv2.defineAdvancedGraphicalFeatures
import me.anno.remsstudio.gpu.GFXx3Dv2.shader3DUniforms
import me.anno.remsstudio.objects.GFXTransform
import org.joml.Matrix4fArrayList
import org.joml.Vector4f

object DrawSVGv2 {
    fun draw3DSVG(
        video: GFXTransform, time: Double,
        stack: Matrix4fArrayList, buffer: StaticBuffer, texture: Texture2D, color: Vector4f,
        filtering: TexFiltering, clamping: Clamping, tiling: Vector4f?
    ) {
        stack.next {
            // flip Y
            stack.scale(1f, -1f, 1f)
            // todo how/where can we support color???
            // todo support tiling...
            val shader = ShaderLibV2.shader3DSVG.value
            shader.use()
            shader3DUniforms(shader, stack, texture.width, texture.height, tiling, filtering, null)
            texture.bind(0, filtering.convert(), clamping)
            defineAdvancedGraphicalFeatures(
                shader, video, time, false,
                false /* todo not used / affecting the mesh */, false /* is correct */
            )
            DrawSVGs.draw(stack, buffer, clamping, tiling, shader)
        }
    }
}