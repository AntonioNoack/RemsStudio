package me.anno.remsstudio.gpu

import me.anno.ecs.components.mesh.Mesh
import me.anno.gpu.GFX
import me.anno.gpu.buffer.SimpleBuffer
import me.anno.gpu.drawing.GFXx3D
import me.anno.gpu.drawing.GFXx3D.circleParams
import me.anno.gpu.shader.BaseShader
import me.anno.gpu.shader.GLSLType
import me.anno.gpu.shader.Shader
import me.anno.gpu.shader.ShaderLib
import me.anno.gpu.shader.builder.ShaderStage
import me.anno.gpu.shader.builder.Variable
import me.anno.gpu.shader.builder.VariableMode
import me.anno.gpu.texture.Clamping
import me.anno.gpu.texture.Filtering
import me.anno.gpu.texture.ITexture2D
import me.anno.gpu.texture.Texture2D
import me.anno.remsstudio.RemsStudio
import me.anno.remsstudio.gpu.ShaderLibV2.colorGrading
import me.anno.remsstudio.gpu.ShaderLibV2.getForceFieldColor
import me.anno.remsstudio.gpu.ShaderLibV2.getForceFieldColorUniforms
import me.anno.remsstudio.gpu.ShaderLibV2.getTextureLib
import me.anno.remsstudio.gpu.ShaderLibV2.getTextureLibUniforms
import me.anno.remsstudio.gpu.ShaderLibV2.hasForceFieldColor
import me.anno.remsstudio.gpu.ShaderLibV2.shader3DCircle
import me.anno.remsstudio.gpu.ShaderLibV2.shader3DText
import me.anno.remsstudio.objects.GFXTransform
import me.anno.remsstudio.objects.GFXTransform.Companion.uploadAttractors
import me.anno.remsstudio.objects.GFXTransform.Companion.uploadAttractors0
import me.anno.remsstudio.objects.Transform
import me.anno.remsstudio.objects.effects.MaskType
import me.anno.remsstudio.objects.geometric.Polygon
import me.anno.remsstudio.objects.video.Video
import me.anno.remsstudio.video.UVProjection
import me.anno.utils.Color.white4
import me.anno.video.formats.gpu.GPUFrame
import org.joml.*
import org.lwjgl.BufferUtils
import java.nio.FloatBuffer
import kotlin.math.min

@Suppress("MemberVisibilityCanBePrivate")
object GFXx3Dv2 {

    fun defineAdvancedGraphicalFeatures(
        shader: Shader, transform: Transform?, time: Double, is3D: Boolean,
        flipUVAttractors: Boolean, flipColorAttractors: Boolean
    ) {
        (transform as? GFXTransform)?.uploadAttractors(shader, time, is3D, flipUVAttractors, flipColorAttractors)
            ?: uploadAttractors0(shader)
        shader.v4f("finalId", transform?.clickId ?: -1)
        colorGradingUniforms(transform as? Video, time, shader)
    }

    fun getScale(w: Int, h: Int): Float = getScale(w.toFloat(), h.toFloat())
    private fun getScale(w: Float, h: Float): Float {
        return if (w * RemsStudio.targetHeight > h * RemsStudio.targetWidth) RemsStudio.targetWidth / (w * RemsStudio.targetHeight) else 1f / h
    }

    fun transformUniform(shader: Shader, transform: Matrix4f?) {
        GFX.check()
        shader.m4x4("transform", transform)
    }

    fun shader3DUniforms(shader: Shader, transform: Matrix4f, color: Vector4f) {
        transformUniform(shader, transform)
        shader.v4f("tint", color)
        shader.v4f("tiling", 1f, 1f, 0f, 0f)
    }

    fun shader3DUniforms(
        shader: Shader, stack: Matrix4fArrayList,
        w: Int, h: Int, tiling: Vector4f?, filtering: TexFiltering,
        uvProjection: UVProjection?
    ) {

        val shallScale = (uvProjection?.doScale ?: true) && w != h
        if (shallScale) {
            stack.pushMatrix()
            val scale = getScale(w, h)
            val sx = w * scale
            val sy = h * scale
            stack.scale(sx, sy, 1f)
        }

        GFXx3D.transformUniform(shader, stack)
        shader.v1i("filtering", filtering.id)
        shader.v2f("textureDeltaUV", 1f / w, 1f / h)

        if (shallScale) stack.popMatrix()

        if (tiling != null) shader.v4f("tiling", tiling)
        else shader.v4f("tiling", 1f, 1f, 0f, 0f)
        shader.v1i("uvProjection", uvProjection?.id ?: UVProjection.Planar.id)

    }

    private fun shader3DUniforms(
        shader: Shader, stack: Matrix4fArrayList,
        w: Int, h: Int, color: Vector4f?,
        tiling: Vector4f?, filtering: TexFiltering,
        uvProjection: UVProjection?
    ) {
        shader3DUniforms(shader, stack, w, h, tiling, filtering, uvProjection)
        shader.v4f("tint", color ?: white4)
    }

    fun draw3DText(
        that: GFXTransform, time: Double, offset: Vector2f,
        stack: Matrix4fArrayList, mesh: Mesh, color: Vector4f
    ) {
        val shader = shader3DText.value
        shader.use()
        shader3DUniforms(shader, stack, color)
        shader.v3f("offset", offset.x, offset.y, 0f)
        uploadAttractors(
            that, shader, time, false,
            false, false
        ) // flipping is correct
        mesh.draw(null, shader, 0)
        GFX.check()
    }

    fun draw3DTextWithOffset(mesh: Mesh, offset: Vector2f) {
        val shader = shader3DText.value
        shader.use()
        shader.v3f("offset", offset.x, offset.y, 0f)
        mesh.draw(null, shader, 0)
    }

    fun draw3DVideo(
        video: GFXTransform, time: Double,
        stack: Matrix4fArrayList, texture: ITexture2D, color: Vector4f,
        filtering: TexFiltering, clamping: Clamping, tiling: Vector4f?, uvProjection: UVProjection,
        cornerRadius: Vector4f
    ) {
        val shader = get3DShader(GPUFrame.swizzleStage0).value
        shader.use()
        defineAdvancedGraphicalFeatures(
            shader, video, time, uvProjection != UVProjection.Planar,
            true, true
        ) // flipping is correct
        shader3DUniforms(shader, stack, texture.width, texture.height, color, tiling, filtering, uvProjection)
        cornerRadius(shader, cornerRadius, texture.width, texture.height)
        texture.bind(0, filtering.convert(), clamping)
        uvProjection.mesh.draw(null, shader, 0)
        GFX.check()
    }

    private fun cornerRadius(shader: Shader, cornerRadius: Vector4f, w: Int, h: Int) {
        shader.v4f("cornerRadius", cornerRadius)
        shader.v2f("cornerSize", 2f * w.toFloat() / h.toFloat(), 2f)
    }

    fun draw3DVideo(
        video: GFXTransform, time: Double,
        stack: Matrix4fArrayList, texture: GPUFrame, color: Vector4f,
        filtering: TexFiltering, clamping: Clamping, tiling: Vector4f?, uvProjection: UVProjection,
        cornerRadius: Vector4f
    ) {
        if (!texture.isCreated) throw RuntimeException("Frame must be loaded to be rendered!")
        val shader0 = get3DShader(texture)
        val shader = shader0.value
        shader.use()
        defineAdvancedGraphicalFeatures(
            shader, video, time, uvProjection != UVProjection.Planar,
            false, true
        ) // flipping is correct
        shader3DUniforms(shader, stack, texture.width, texture.height, color, tiling, filtering, uvProjection)
        colorGradingUniforms(video as? Video, time, shader)
        cornerRadius(shader, cornerRadius, texture.width, texture.height)
        texture.bind(0, filtering.convert(), clamping)
        texture.bindUVCorrection(shader)
        uvProjection.mesh.draw(null, shader, 0)
        GFX.check()
    }

    fun draw3DPolygon(
        polygon: Polygon, time: Double,
        stack: Matrix4fArrayList, buffer: Mesh,
        texture: ITexture2D, color: Vector4f,
        inset: Float,
        filtering: TexFiltering, clamping: Clamping
    ) {
        val shader = ShaderLibV2.shader3DPolygon.value
        shader.use()
        shader.v4f("finalId", polygon.clickId)
        polygon.uploadAttractors(shader, time, false, false, true) // flipping is correct
        shader3DUniforms(shader, stack, texture.width, texture.height, color, null, filtering, null)
        shader.v1f("inset", inset)
        texture.bind(0, filtering.convert(), clamping)
        buffer.draw(null, shader, 0)
        GFX.check()
    }

    val outlineStatsBuffer: FloatBuffer = BufferUtils.createFloatBuffer(ShaderLibV2.maxOutlineColors * 4)
    fun drawOutlinedText(
        that: GFXTransform, time: Double,
        stack: Matrix4fArrayList,
        offset: Vector2f, scale: Vector2f,
        texture: ITexture2D,
        color: Vector4f, colorCount: Int, colors: Array<Vector4f>,
        distances: FloatArray, smoothness: FloatArray, depth: Float,
        hasUVAttractors: Boolean
    ) {

        // why ever this would be drawn...
        if (colors.all { it.w <= 0f }) {
            return
        }

        val shader = ShaderLibV2.shaderSDFText.value
        shader.use()

        defineAdvancedGraphicalFeatures(
            shader, that, time, false,
            false, false
        )

        shader.v4f("tint", color)

        val cc = min(colorCount, ShaderLibV2.maxOutlineColors)

        /**
         * u4[ maxColors ] colors
         * u2[ maxColors ] distSmooth
         * uniform int colorCount
         * */
        val buffer = outlineStatsBuffer
        buffer.position(0)
        for (i in 0 until cc) {
            val colorI = colors[i]
            buffer.put(colorI.x)
            buffer.put(colorI.y)
            buffer.put(colorI.z)
            buffer.put(colorI.w)
        }
        buffer.position(0)
        shader.v4fs("colors", buffer)
        buffer.position(0)
        for (i in 0 until cc) {
            buffer.put(distances[i])
            buffer.put(smoothness[i])
        }
        buffer.position(0)
        shader.v2fs("distSmoothness", buffer)
        shader.v1i("colorCount", cc)
        shader.v1f("depth", depth * 0.00001f)

        drawOutlinedText(stack, offset, scale, texture, hasUVAttractors)
    }

    fun drawOutlinedText(
        stack: Matrix4fArrayList,
        offset: Vector2f,
        scale: Vector2f,
        texture: ITexture2D,
        hasUVAttractors: Boolean
    ) {
        val shader = ShaderLibV2.shaderSDFText.value
        shader.use()
        GFXx3D.transformUniform(shader, stack)
        shader.v2f("offset", offset)
        shader.v2f("scale", scale)
        texture.bind(0, Filtering.LINEAR, Clamping.CLAMP)
        // if we have a force field applied, subdivide the geometry
        val buffer = if (hasUVAttractors) SimpleBuffer.flat01CubeX10 else SimpleBuffer.flat01Mesh
        buffer.draw(null, shader, 0)
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
        defineAdvancedGraphicalFeatures(
            shader, that, time, false,
            false, true
        ) // flipping is correct, todo fix: uv-attractors aren't supported
        shader3DUniforms(shader, stack, 1, 1, color, null, TexFiltering.NEAREST, null)
        circleParams(innerRadius, startDegrees, endDegrees, shader)
        circleData.draw(null, shader, 0)
        GFX.check()
    }

    fun draw3DMasked(
        stack: Matrix4fArrayList,
        color: Vector4f,
        maskType: MaskType,
        useMaskColor: Float,
        pixelSize: Float,
        offset: Vector2f,
        isInverted1: Boolean,
        isInverted2: Boolean,
        isFullscreen: Boolean,
        settings: Vector4f
    ) {
        val shader = ShaderLibV2.shader3DMasked[maskType].value
        shader.use()
        shader3DUniforms(shader, stack, color)
        shader.v1f("useMaskColor", useMaskColor)
        shader.v1b("invertMask1", isInverted1)
        shader.v1b("invertMask2", isInverted2)
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

    private fun colorGradingUniforms(video: Video?, time: Double, shader: Shader) {
        if (video != null) {
            tmp0.set(video.cgOffsetAdd[time, tmp0])
            tmp1.set(video.cgOffsetSub[time, tmp1])
            shader.v3f("cgOffset", tmp0.sub(tmp1))
            shader.v3X("cgSlope", video.cgSlope[time, tmp2])
            shader.v3X("cgPower", video.cgPower[time, tmp2])
            shader.v1f("cgSaturation", video.cgSaturation[time])
        } else {
            shader.v3f("cgOffset", 0f)
            shader.v3f("cgSlope", 1f)
            shader.v3f("cgPower", 1f)
            shader.v1f("cgSaturation", 1f)
        }
    }

    fun draw3D(
        stack: Matrix4fArrayList, texture: Texture2D, w: Int, h: Int, color: Vector4f,
        filtering: TexFiltering, clamping: Clamping, tiling: Vector4f?, uvProjection: UVProjection
    ) {
        val shader = get3DShader(GPUFrame.swizzleStage0).value
        shader.use()
        shader3DUniforms(shader, stack, w, h, color, tiling, filtering, uvProjection)
        texture.bind(0, filtering.convert(), clamping)
        uvProjection.mesh.draw(null, shader, 0)
        GFX.check()
    }

    val shaderMap3d = HashMap<ShaderStage, BaseShader>()
    fun get3DShader(self: GPUFrame): BaseShader {
        val key = self.getShaderStage()
        return get3DShader(key)
    }

    fun get3DShader(key: ShaderStage): BaseShader {
        return shaderMap3d.getOrPut(key) {
            ShaderLib.createShader(
                "3dx-GFXx3Dv2", ShaderLib.v3Dl, ShaderLib.v3D, ShaderLib.y3D,
                key.variables.filter { !it.isOutput } +
                        getForceFieldColorUniforms + getTextureLibUniforms + listOf(
                    Variable(GLSLType.V4F, "cornerRadius"),
                    Variable(GLSLType.V2F, "cornerSize"),
                    Variable(GLSLType.V3F, "finalColor", VariableMode.OUT),
                    Variable(GLSLType.V1F, "finalAlpha", VariableMode.OUT),
                ), "" +
                        getTextureLib +
                        getForceFieldColor +
                        ShaderLib.brightness +
                        colorGrading +
                        "float sdRoundedBox(vec2 p, vec2 b, vec4 r){\n" +
                        "   vec2 rxy = (p.x>0.0) ? r.xy : r.zw;\n" +
                        "   float rx = (p.y>0.0) ? rxy.x  : rxy.y;\n" +
                        "   vec2 q = abs(p)-b+rx;\n" +
                        "   return min(max(q.x,q.y),0.0) + length(max(q,0.0)) - rx;\n" +
                        "}\n" +
                        "void main(){\n" +
                        "   vec2 uvR = getProjectedUVs(uv, uvw, -1.0);\n" +
                        "   vec2 uvG = getProjectedUVs(uv, uvw,  0.0);\n" +
                        "   vec2 uvB = getProjectedUVs(uv, uvw, +1.0);\n" +
                        "   vec2 finalUV;\n" +
                        "   vec4 color; vec3 result = vec3(1.0);\n" +
                        "   {\n finalUV = uvR;\n" + key.body + "}\n" +
                        "   result.r = color.r;\n" +
                        "   {\n finalUV = uvB;\n" + key.body + "}\n" +
                        "   result.b = color.b;\n" +
                        "   {\n finalUV = uvG;\n" + key.body + "}\n" + // + alpha
                        "   result.g = color.g;\n" +
                        "   color.rgb = colorGrading(result.rgb);\n" +
                        "   if($hasForceFieldColor) color *= getForceFieldColor(finalPosition);\n" +
                        "   vec2 cornerUV = (uv-0.5) * cornerSize;\n" +
                        "   float cornerSDF = sdRoundedBox(cornerUV, cornerSize * 0.5, cornerRadius);\n" +
                        "   finalColor = color.rgb;\n" +
                        "   finalAlpha = color.a * clamp(0.5 - cornerSDF / length(vec4(dFdx(cornerUV),dFdy(cornerUV))), 0.0, 1.0);\n" +
                        "}", listOf("tex")
            )
        }
    }


}