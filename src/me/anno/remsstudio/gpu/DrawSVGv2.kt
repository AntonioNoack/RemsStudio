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
        val shader = ShaderLibV2.shader3DSVG.value
        shader.use()
        shader3DUniforms(shader, stack, texture.width, texture.height, color, filtering, null)
        texture.bind(0, filtering.convert(), clamping)
        defineAdvancedGraphicalFeatures(shader, video, time, false)
        DrawSVGs.draw(stack, buffer, clamping, tiling, shader)
    }
}