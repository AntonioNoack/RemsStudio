package me.anno.remsstudio.gpu

import me.anno.ecs.components.mesh.Mesh
import me.anno.gpu.GFX
import me.anno.gpu.GFXState
import me.anno.gpu.buffer.SimpleBuffer
import me.anno.gpu.buffer.StaticBuffer
import me.anno.gpu.drawing.GFXx3D
import me.anno.gpu.drawing.GFXx3D.circleParams
import me.anno.gpu.drawing.GFXx3D.shader3DCircle
import me.anno.gpu.drawing.GFXx3D.shader3DText
import me.anno.gpu.drawing.UVProjection
import me.anno.gpu.shader.Shader
import me.anno.gpu.shader.ShaderLib
import me.anno.gpu.texture.*
import me.anno.remsstudio.RemsStudio
import me.anno.remsstudio.objects.GFXTransform
import me.anno.remsstudio.objects.Video
import me.anno.remsstudio.objects.geometric.Polygon
import me.anno.utils.Color.white4
import me.anno.video.formats.gpu.GPUFrame
import ofx.mio.OpticalFlow
import org.joml.Matrix4fArrayList
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f
import kotlin.math.min

object GFXx3Dv2 {

    fun getScale(w: Int, h: Int): Float = getScale(w.toFloat(), h.toFloat())
    fun getScale(w: Float, h: Float): Float {
        return if (w * RemsStudio.targetHeight > h * RemsStudio.targetWidth) RemsStudio.targetWidth / (w * RemsStudio.targetHeight) else 1f / h
    }

    fun shader3DUniforms(
        shader: Shader, stack: Matrix4fArrayList,
        w: Int, h: Int,
        tiling: Vector4f?, filtering: Filtering,
        uvProjection: UVProjection?
    ) {

        stack.pushMatrix()

        val doScale2 = (uvProjection?.doScale ?: true) && w != h
        if (doScale2) {
            val scale = getScale(w, h)
            val sx = w * scale
            val sy = h * scale
            stack.scale(sx, -sy, 1f)
        } else {
            stack.scale(1f, -1f, 1f)
        }

        GFXx3D.transformUniform(shader, stack)
        shader.v1i("filtering", filtering.id)
        shader.v2f("textureDeltaUV", 1f / w, 1f / h)

        stack.popMatrix()

        if (tiling != null) shader.v4f("tiling", tiling)
        else shader.v4f("tiling", 1f, 1f, 0f, 0f)
        shader.v1i("uvProjection", uvProjection?.id ?: UVProjection.Planar.id)

    }

    fun shader3DUniforms(
        shader: Shader, stack: Matrix4fArrayList,
        w: Int, h: Int, color: Vector4f?,
        tiling: Vector4f?, filtering: Filtering,
        uvProjection: UVProjection?
    ) {
        shader3DUniforms(shader, stack, w, h, tiling, filtering, uvProjection)
        shader.v4f("tint", color ?: white4)
    }

    fun shader3DUniforms(
        shader: Shader, stack: Matrix4fArrayList,
        w: Int, h: Int, color: Int,
        tiling: Vector4f?, filtering: Filtering,
        uvProjection: UVProjection?
    ) {
        shader3DUniforms(shader, stack, w, h, tiling, filtering, uvProjection)
        shader.v4f("tint", color)
    }

    fun draw3DText(
        that: GFXTransform, time: Double, offset: Vector3f,
        stack: Matrix4fArrayList, buffer: StaticBuffer, color: Vector4f
    ) {
        val shader = shader3DText.value
        shader.use()
        GFXx3D.shader3DUniforms(shader, stack, color)
        shader.v3f("offset", offset)
        GFXTransform.uploadAttractors(that, shader, time)
        buffer.draw(shader)
        GFX.check()
    }

    fun draw3DVideo(
        video: GFXTransform, time: Double,
        stack: Matrix4fArrayList, texture: ITexture2D, color: Vector4f,
        filtering: Filtering, clamping: Clamping, tiling: Vector4f?, uvProjection: UVProjection
    ) {
        val shader = ShaderLib.shader3DRGBA.value
        shader.use()
        GFXx2Dv2.defineAdvancedGraphicalFeatures(shader, video, time)
        shader3DUniforms(shader, stack, texture.width, texture.height, color, tiling, filtering, uvProjection)
        texture.bind(0, filtering, clamping)
        uvProjection.getBuffer().draw(shader)
        GFX.check()
    }

    fun draw3DVideo(
        video: GFXTransform, time: Double,
        stack: Matrix4fArrayList, v0: GPUFrame, v1: GPUFrame, interpolation: Float, color: Vector4f,
        filtering: Filtering, clamping: Clamping, tiling: Vector4f?, uvProjection: UVProjection
    ) {

        if (!v0.isCreated || !v1.isCreated) throw RuntimeException("Frame must be loaded to be rendered!")

        val t0 = v0.getTextures()
        val t1 = v1.getTextures()

        val lambda = 0.01f
        val blurAmount = 0.05f

        GFXState.renderPurely {
            // interpolate all textures
            val interpolated = t0.zip(t1).map { (x0, x1) -> OpticalFlow.run(lambda, blurAmount, interpolation, x0, x1) }
            // bind them
            v0.bind2(0, filtering, clamping, interpolated)
        }

        val shader0 = v0.get3DShader()
        val shader = shader0.value
        shader.use()
        GFXx2Dv2.defineAdvancedGraphicalFeatures(shader, video, time)
        shader3DUniforms(shader, stack, v0.width, v0.height, color, tiling, filtering, uvProjection)
        v0.bindUVCorrection(shader)
        uvProjection.getBuffer().draw(shader)
        GFX.check()

    }

    fun draw3DVideo(
        video: GFXTransform, time: Double,
        stack: Matrix4fArrayList, texture: GPUFrame, color: Vector4f,
        filtering: Filtering, clamping: Clamping, tiling: Vector4f?, uvProjection: UVProjection
    ) {
        if (!texture.isCreated) throw RuntimeException("Frame must be loaded to be rendered!")
        val shader0 = texture.get3DShader()
        val shader = shader0.value
        shader.use()
        GFXx2Dv2.defineAdvancedGraphicalFeatures(shader, video, time)
        shader3DUniforms(shader, stack, texture.width, texture.height, color, tiling, filtering, uvProjection)
        texture.bind(0, filtering, clamping)
        texture.bindUVCorrection(shader)
        uvProjection.getBuffer().draw(shader)
        GFX.check()
    }

    fun draw3DPolygon(
        polygon: Polygon, time: Double,
        stack: Matrix4fArrayList, buffer: StaticBuffer,
        texture: Texture2D, color: Vector4f,
        inset: Float,
        filtering: Filtering, clamping: Clamping
    ) {
        val shader = ShaderLib.shader3DPolygon.value
        shader.use()
        shader.v4f("gfxId", polygon.clickId)
        polygon.uploadAttractors(shader, time)
        shader3DUniforms(shader, stack, texture.width, texture.height, color, null, filtering, null)
        shader.v1f("inset", inset)
        texture.bind(0, filtering, clamping)
        buffer.draw(shader)
        GFX.check()
    }

    fun drawOutlinedText(
        that: GFXTransform,
        time: Double,
        stack: Matrix4fArrayList,
        offset: Vector2f,
        scale: Vector2f,
        texture: Texture2D,
        color: Vector4f,
        colorCount: Int,
        colors: Array<Vector4f>,
        distances: FloatArray,
        smoothness: FloatArray,
        depth: Float,
        hasUVAttractors: Boolean
    ) {

        // why ever this would be drawn...
        if (colors.all { it.w <= 0f }) {
            return
        }

        val shader = ShaderLib.shaderSDFText.value
        shader.use()

        GFXx2Dv2.defineAdvancedGraphicalFeatures(shader, that, time)

        shader.v4f("tint", color)

        val cc = min(colorCount, ShaderLib.maxOutlineColors)

        /**
         * u4[ maxColors ] colors
         * u2[ maxColors ] distSmooth
         * uniform int colorCount
         * */
        val buffer = GFXx3D.outlineStatsBuffer
        buffer.position(0)
        for (i in 0 until cc) {
            val colorI = colors[i]
            buffer.put(colorI.x)
            buffer.put(colorI.y)
            buffer.put(colorI.z)
            buffer.put(colorI.w)
        }
        buffer.position(0)
        shader.v4Array("colors", buffer)
        buffer.position(0)
        for (i in 0 until cc) {
            buffer.put(distances[i])
            buffer.put(smoothness[i])
        }
        buffer.position(0)
        shader.v2Array("distSmoothness", buffer)
        shader.v1i("colorCount", cc)
        shader.v1f("depth", depth * 0.00001f)

        drawOutlinedText(stack, offset, scale, texture, hasUVAttractors)

    }

    private fun drawOutlinedText(
        stack: Matrix4fArrayList,
        offset: Vector2f,
        scale: Vector2f,
        texture: Texture2D,
        hasUVAttractors: Boolean
    ) {
        val shader = ShaderLib.shaderSDFText.value
        shader.use()
        GFXx3D.transformUniform(shader, stack)
        shader.v2f("offset", offset)
        shader.v2f("scale", scale)
        texture.bind(0, GPUFiltering.LINEAR, Clamping.CLAMP)
        // if we have a force field applied, subdivide the geometry
        val buffer = if (hasUVAttractors) SimpleBuffer.flat01CubeX10 else SimpleBuffer.flat01Cube
        buffer.draw(shader)
        GFX.check()
    }

    private val circleData = Mesh().apply {

        val slices = 128
        val positions = FloatArray((slices + 1) * 3 * 2)
        val indices = IntArray(slices * 6)

        for (i in 0..slices) {

            val angle = i.toFloat() / slices

            val i6 = i * 6
            positions[i6 + 0] = angle
            positions[i6 + 1] = 0f
            positions[i6 + 3] = angle
            positions[i6 + 4] = 1f

            if (i < slices) {
                val i2 = i * 2
                indices[i6 + 0] = i2 + 0
                indices[i6 + 1] = i2 + 1
                indices[i6 + 2] = i2 + 2
                indices[i6 + 3] = i2 + 2
                indices[i6 + 4] = i2 + 1
                indices[i6 + 5] = i2 + 3
            }
        }

        this.positions = positions
        this.indices = indices

    }

    fun draw3DCircle(
        that: GFXTransform, time: Double,
        stack: Matrix4fArrayList,
        innerRadius: Float,
        startDegrees: Float,
        endDegrees: Float,
        color: Vector4f
    ) {
        val shader = shader3DCircle.value
        shader.use()
        GFXx2Dv2.defineAdvancedGraphicalFeatures(shader, that, time)
        shader3DUniforms(shader, stack, 1, 1, color, null, Filtering.NEAREST, null)
        circleParams(innerRadius, startDegrees, endDegrees, shader)
        circleData.draw(shader, 0)
        GFX.check()
    }

    fun draw3DMasked(
        stack: Matrix4fArrayList,
        color: Vector4f,
        maskType: Int,
        useMaskColor: Float,
        pixelSize: Float,
        offset: Vector2f,
        isInverted1: Boolean,
        isInverted2: Boolean,
        isFullscreen: Boolean,
        settings: Vector4f
    ) {
        val shader = ShaderLibV2.shader3DMasked.value
        shader.use()
        GFXx3D.shader3DUniforms(shader, stack, color)
        shader.v1f("useMaskColor", useMaskColor)
        shader.v1b("invertMask1", isInverted1)
        shader.v1b("invertMask2", isInverted2)
        shader.v1i("maskType", maskType)
        shader.v2f("pixelating", pixelSize * GFX.viewportHeight / GFX.viewportWidth, pixelSize)
        shader.v4f("settings", settings)
        shader.v2f("offset", offset)
        shader.v2f("windowSize", GFX.viewportWidth.toFloat(), GFX.viewportHeight.toFloat())
        val buffer = if (isFullscreen) SimpleBuffer.flatLarge else SimpleBuffer.flat11
        buffer.draw(shader)
        GFX.check()
    }

    private val tmp0 = Vector3f()
    private val tmp1 = Vector3f()
    private val tmp2 = Vector4f()

    fun colorGradingUniforms(video: Video?, time: Double, shader: Shader) {
        if (video != null) {
            tmp0.set(video.cgOffsetAdd[time, tmp0])
            tmp1.set(video.cgOffsetSub[time, tmp1])
            shader.v3f("cgOffset", tmp0.sub(tmp1))
            shader.v3X("cgSlope", video.cgSlope[time, tmp2])
            shader.v3X("cgPower", video.cgPower[time, tmp2])
            shader.v1f("cgSaturation", video.cgSaturation[time])
        } else {
            GFXx3D.colorGradingUniforms(shader)
        }
    }

}