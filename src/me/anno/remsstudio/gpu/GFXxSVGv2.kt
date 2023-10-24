package me.anno.remsstudio.gpu

import me.anno.gpu.buffer.StaticBuffer
import me.anno.gpu.drawing.GFXx3D
import me.anno.gpu.drawing.SVGxGFX
import me.anno.gpu.shader.Shader
import me.anno.gpu.shader.ShaderLib
import me.anno.gpu.texture.Clamping
import me.anno.gpu.texture.Filtering
import me.anno.gpu.texture.Texture2D
import me.anno.remsstudio.gpu.GFXx3Dv2.defineAdvancedGraphicalFeatures
import me.anno.remsstudio.objects.GFXTransform
import org.joml.Matrix4fArrayList
import org.joml.Vector4f

object GFXxSVGv2 {
    fun draw3DSVG(
        video: GFXTransform, time: Double,
        stack: Matrix4fArrayList, buffer: StaticBuffer, texture: Texture2D, color: Vector4f,
        filtering: Filtering, clamping: Clamping, tiling: Vector4f?
    ) {
        val shader = ShaderLib.shader3DSVG.value
        shader.use()
        GFXx3D.shader3DUniforms(shader, stack, texture.width, texture.height, color, null, filtering, null)
        texture.bind(0, filtering, clamping)
        defineAdvancedGraphicalFeatures(shader, video, time)
        SVGxGFX.draw(stack, buffer, clamping, tiling, shader)
    }
}