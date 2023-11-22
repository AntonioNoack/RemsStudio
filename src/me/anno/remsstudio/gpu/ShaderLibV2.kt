package me.anno.remsstudio.gpu

import me.anno.gpu.shader.BaseShader
import me.anno.gpu.shader.GLSLType
import me.anno.gpu.shader.ShaderLib
import me.anno.gpu.shader.ShaderLib.v3DMasked
import me.anno.gpu.shader.ShaderLib.v3DlMasked
import me.anno.gpu.shader.ShaderLib.y3DMasked
import me.anno.gpu.shader.builder.Variable
import me.anno.gpu.shader.builder.VariableMode
import me.anno.io.files.FileReference.Companion.getReference
import me.anno.remsstudio.objects.effects.MaskType

object ShaderLibV2 {
    
    private fun case(case: Int, path: String) = getReference("res://$path").readTextSync()
        .trim().run {
            replace("\r", "")
        }.run {
            "case $case:\n" + substring(indexOf('\n')+1, lastIndexOf('\n')) + "\nbreak;\n"
        }

    lateinit var shader3DMasked: BaseShader

    fun init() {

        val f3DMasked = "" +
                "precision highp float;\n" +
                "uniform sampler2D maskTex, tex, tex2;\n" +
                "uniform float useMaskColor;\n" +
                "uniform bool invertMask1, invertMask2;\n" +
                "uniform vec2 pixelating;\n" +
                "uniform vec2 windowSize, offset;\n" +
                "uniform int maskType;\n" +
                "uniform float maxSteps;\n" +
                "uniform vec4 settings;\n" +
                ShaderLib.brightness +
                ShaderLib.getColorForceFieldLib +
                ShaderLib.rgb2uv +
                "float maxV3(vec3 rgb){return max(rgb.r, max(rgb.g, rgb.b));}\n" +
                "float minV3(vec3 rgb){return min(rgb.r, min(rgb.g, rgb.b));}\n" +
                "void main(){\n" +
                "   vec2 uv1 = uv.xy/uv.z;\n" +
                "   vec2 uv2 = uv1 * 0.5 + 0.5, uv3, uv4;\n" +
                "   vec4 mask = texture(maskTex, uv2);\n" +
                "   vec4 color;\n" +
                "   float effect = 0.0, inverseEffect;\n" +
                "   switch(maskType){\n" +
                case(MaskType.MASKING.id, "shader/mask-effects/Masking.glsl") +
                case(MaskType.TRANSITION.id, "shader/mask-effects/Transition.glsl") +
                case(MaskType.QUAD_PIXELATION.id, "shader/mask-effects/QuadPixelating.glsl") +
                case(MaskType.TRI_PIXELATION.id, "shader/mask-effects/TriPixelating.glsl") +
                case(MaskType.HEX_PIXELATION.id, "shader/mask-effects/HexPixelating.glsl") +
                case(MaskType.VORONOI_PIXELATION.id, "shader/mask-effects/VoronoiPixelating.glsl") +
                case(MaskType.RADIAL_BLUR_1.id, "shader/mask-effects/RadialBlur1.glsl") +
                case(MaskType.RADIAL_BLUR_2.id, "shader/mask-effects/RadialBlur2.glsl") +
                case(MaskType.GREEN_SCREEN.id, "shader/mask-effects/GreenScreen.glsl") +
                "       case ${MaskType.GAUSSIAN_BLUR.id}:\n" +
                "       case ${MaskType.BOKEH_BLUR.id}:\n" +
                "       case ${MaskType.BLOOM.id}:\n" + // just mix two images
                "           effect = mix(mask.a, dot(vec3(0.3), mask.rgb), useMaskColor);\n" +
                "           if(invertMask1) effect = 1.0 - effect;\n" +
                "           color = mix(texture(tex, uv2), texture(tex2, uv2), effect);\n" +
                "           break;\n" +
                "       case ${MaskType.UV_OFFSET.id}:\n" +
                "           vec2 offset = (mask.rg-mask.gb) * pixelating;\n" +
                "           color = texture(tex, uv2 + offset);\n" +
                "           break;\n" +
                "       default:" +
                "           color = vec4(1.0,0.0,1.0,1.0);\n" +
                "           break;\n" +
                "   }\n" +
                "   if(color.a <= 0.001) discard;\n" +
                "   if(${ShaderLib.hasForceFieldColor}) color *= getForceFieldColor(finalPosition);\n" +
                "   finalColor = color.rgb;\n" +
                "   finalAlpha = min(color.a, 1.0);\n" +
                "}"
        shader3DMasked =
            BaseShader(
                "3d-masked", v3DlMasked, v3DMasked, y3DMasked, listOf(
                    Variable(GLSLType.V3F, "finalColor", VariableMode.OUT),
                    Variable(GLSLType.V1F, "finalAlpha", VariableMode.OUT)
                ), f3DMasked
            )
        shader3DMasked.setTextureIndices(listOf("maskTex", "tex", "tex2"))
        shader3DMasked.ignoreNameWarnings("tiling")

    }

}