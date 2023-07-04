package me.anno.remsstudio.gpu

import me.anno.gpu.buffer.StaticBuffer
import me.anno.gpu.drawing.GFXx3D
import me.anno.gpu.drawing.SVGxGFX
import me.anno.gpu.shader.Shader
import me.anno.gpu.shader.ShaderLib
import me.anno.gpu.texture.Clamping
import me.anno.gpu.texture.Filtering
import me.anno.gpu.texture.Texture2D
import me.anno.remsstudio.objects.GFXTransform
import org.joml.Matrix4fArrayList
import org.joml.Vector4f

object GFXxSVGv2 {

    fun draw3DSVG(
        video: GFXTransform, time: Double,
        stack: Matrix4fArrayList, buffer: StaticBuffer, texture: Texture2D, color: Vector4f,
        filtering: Filtering, clamping: Clamping, tiling: Vector4f?
    ) {
        val shader = init(video, time, stack, texture, color, filtering, clamping, tiling)
        SVGxGFX.draw(stack, buffer, clamping, tiling, shader)
    }

    fun init(
        video: GFXTransform, time: Double,
        stack: Matrix4fArrayList, texture: Texture2D, color: Vector4f, filtering: Filtering, clamping: Clamping,
        tiling: Vector4f?
    ): Shader {
        val shader = ShaderLib.shader3DSVG.value
        shader.use()
        GFXx3D.shader3DUniforms(shader, stack, texture.width, texture.height, color, null, filtering, null)
        texture.bind(0, filtering, clamping)
        if (tiling == null) {
            GFXx2Dv2.defineAdvancedGraphicalFeatures(shader, video, time)
        } else {
            GFXx2Dv2.defineAdvancedGraphicalFeatures(shader, video, time)
        }
        return shader
    }

}