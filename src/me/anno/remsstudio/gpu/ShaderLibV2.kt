package me.anno.remsstudio.gpu

import me.anno.config.DefaultConfig
import me.anno.gpu.drawing.UVProjection
import me.anno.gpu.shader.BaseShader
import me.anno.gpu.shader.GLSLType
import me.anno.gpu.shader.ShaderFuncLib
import me.anno.gpu.shader.ShaderLib
import me.anno.gpu.shader.ShaderLib.v3DMasked
import me.anno.gpu.shader.ShaderLib.v3DlMasked
import me.anno.gpu.shader.ShaderLib.y3DMasked
import me.anno.gpu.shader.builder.Variable
import me.anno.gpu.shader.builder.VariableMode
import me.anno.io.files.Reference.getReference
import me.anno.remsstudio.objects.effects.MaskType
import me.anno.utils.pooling.ByteBufferPool
import java.nio.FloatBuffer
import kotlin.math.PI

object ShaderLibV2 {

    val v3DlPolygon = listOf(
        Variable(GLSLType.V3F, "coords", VariableMode.ATTR),
        Variable(GLSLType.V2F, "uvs", VariableMode.ATTR),
        Variable(GLSLType.V1F, "inset"),
        Variable(GLSLType.M4x4, "transform")
    )

    val v3DPolygon = "" +
            "void main(){\n" +
            "   vec2 betterUV = coords.xy;\n" +
            "   betterUV *= mix(1.0, uvs.r, inset);\n" +
            "   finalPosition = vec3(betterUV, coords.z);\n" +
            "   gl_Position = matMul(transform, vec4(finalPosition, 1.0));\n" +
            ShaderLib.flatNormal +
            "   uv = uvs.yx;\n" +
            "}"

    // https://en.wikipedia.org/wiki/ASC_CDL
    // color grading with asc cdl standard
    const val colorGrading = "" +
            "uniform vec3 cgSlope, cgOffset, cgPower;\n" +
            "uniform float cgSaturation;\n" +
            "vec3 colorGrading(vec3 raw){\n" +
            "   vec3 color = pow(max(vec3(0.0), raw * cgSlope + cgOffset), cgPower);\n" +
            "   float gray = brightness(color);\n" +
            "   return mix(vec3(gray), color, cgSaturation);\n" +
            "}\n"

    val maxColorForceFields = DefaultConfig["objects.attractors.color.maxCount", 12]
    val getForceFieldColor = "" +
// additional weights?...
            "uniform int forceFieldColorCount;\n" +
            "uniform vec4 forceFieldBaseColor;\n" +
            "uniform vec4[$maxColorForceFields] forceFieldColors;\n" +
            "uniform vec4[$maxColorForceFields] forceFieldPositionsNWeights;\n" +
            "uniform vec4[$maxColorForceFields] forceFieldColorPowerSizes;\n" +
            "vec4 getForceFieldColor(vec3 finalPosition){\n" +
            "   float sumWeight = 0.25;\n" +
            "   vec4 sumColor = sumWeight * forceFieldBaseColor;\n" +
            "   for(int i=0;i<forceFieldColorCount;i++){\n" +
            "       vec4 positionNWeight = forceFieldPositionsNWeights[i];\n" +
            "       vec3 positionDelta = finalPosition - positionNWeight.xyz;\n" +
            "       vec4 powerSize = forceFieldColorPowerSizes[i];\n" +
            "       float weight = positionNWeight.w / (1.0 + pow(dot(powerSize.xyz * positionDelta, positionDelta), powerSize.w));\n" +
            "       sumWeight += weight;\n" +
            "       vec4 localColor = forceFieldColors[i];\n" +
            "       sumColor += weight * localColor * localColor;\n" +
            "   }\n" +
            "   return sqrt(sumColor / sumWeight);\n" +
            "}\n"

    val colorForceFieldBuffer: FloatBuffer = ByteBufferPool
        .allocateDirect(4 * maxColorForceFields)
        .asFloatBuffer()

    val maxUVForceFields = DefaultConfig["objects.attractors.scale.maxCount", 12]
    val getForceFieldUVs = "" +
            "mat2 rot(float angle){\n" +
            "   float c = cos(angle), s = sin(angle);\n" +
            "   return mat2(c,-s,+s,c);\n" +
            "}\n" +
            "uniform int forceFieldUVCount;\n" +
            "uniform vec4[$maxUVForceFields] forceFieldUVs;\n" + // xyz, swirl
            "uniform vec4[$maxUVForceFields] forceFieldUVData0;\n" + // size, power
            "uniform float[$maxUVForceFields] forceFieldUVData1;\n" + // chromatic
            "vec3 getForceFieldUVs(vec3 uvw, float channel){\n" +
            "   vec3 sumUVs = uvw;\n" +
            "   for(int i=0;i<forceFieldUVCount;i++){\n" +
            "       vec3 position = forceFieldUVs[i].xyz;\n" +
            "       vec4 sizePower = forceFieldUVData0[i];\n" +
            // todo chroma, swirl?
            "       vec3 positionDelta = uvw - position;\n" +
            "       float lenSq = dot(positionDelta, positionDelta);\n" +
            "       float weight = sizePower.x / (1.0 + pow(sizePower.z * lenSq, sizePower.w));\n" +
            "       sumUVs += weight * positionDelta;\n" +
            "   }\n" +
            "   return sumUVs;\n" +
            "}\n" +
            "vec2 getForceFieldUVs(vec2 uv, float channel){\n" +
            "   vec2 sumUVs = uv;\n" +
            "   for(int i=0;i<forceFieldUVCount;i++){\n" +
            "       vec4 positionSwirl = forceFieldUVs[i];\n" +
            "       vec4 sizePower = forceFieldUVData0[i];\n" +
            "       float chromatic = forceFieldUVData1[i];\n" +
            "       vec2 position = positionSwirl.xy;\n" +
            "       vec2 swirl = positionSwirl.zw;\n" +
            "       vec2 positionDelta = (uv - position) * sizePower.xy;\n" +
            "       float lenSq = dot(positionDelta, positionDelta);\n" +
            "       float weight = exp(chromatic * channel) / (1.0 + pow(sizePower.z * lenSq, sizePower.w));\n" +
            "       float swirlAngle = swirl.x / (swirl.y + lenSq);\n" +
            "       sumUVs += rot(swirlAngle) * (weight * positionDelta);\n" +
            "   }\n" +
            "   return sumUVs;\n" +
            "}\n"

    val uvForceFieldBuffer = FloatArray(maxUVForceFields)

    const val hasForceFieldColor = "(forceFieldColorCount > 0)"
    const val hasForceFieldUVs = "(forceFieldUVCount > 0)"

    val getTextureLib = "" +
            ShaderLib.bicubicInterpolation +
            getForceFieldUVs +
            // the uvs correspond to the used mesh
            // used meshes are flat01 and cubemapBuffer
            "uniform vec2 textureDeltaUV;\n" +
            "uniform int filtering, uvProjection;\n" +
            "vec2 getProjectedUVs(vec2 uv){ return uv; }\n" +
            "vec2 getProjectedUVs(vec3 uvw){\n" +
            "   float u = atan(uvw.z, uvw.x)*${0.5 / PI}+0.5;\n " +
            "   float v = atan(uvw.y, length(uvw.xz))*${1.0 / PI}+0.5;\n" +
            "   return vec2(u, v);\n" +
            "}\n" +
            "vec2 getProjectedUVs(vec2 uv, vec3 uvw, float channel){\n" +
            "   return uvProjection == ${UVProjection.Equirectangular.id} ?\n" +
            "       ($hasForceFieldUVs ? getProjectedUVs(getForceFieldUVs(uvw, channel)) : getProjectedUVs(uvw)) :\n" +
            "       ($hasForceFieldUVs ? getProjectedUVs(getForceFieldUVs(uv, channel))  : getProjectedUVs(uv));\n" +
            "}\n" +
            "#define dot2(a) dot(a,a)\n" +
            "vec4 getTexture(sampler2D tex, vec2 uv, vec2 duv){\n" +
            "   if(filtering == ${TexFiltering.LINEAR.id}) return texture(tex, uv);\n" +
            "   if(filtering == ${TexFiltering.NEAREST.id}) {\n" +
            // edge smoothing, when zooming in far; not perfect, but quite good :)
            // todo if is axis-aligned, and zoom is integer, don't interpolate
            // (to prevent smoothed edges, where they are not necessary)
            // zoom-round(zoom)>0.02 && dFdx(uv).isSingleAxis && dFdy(uv).isSingleAxis
            "       float zoom = dot2(duv) / dot2(vec4(dFdx(uv),dFdy(uv)));\n" + // guess on zoom level
            "       if(zoom > 4.0) {\n" +
            "           zoom = 0.5 * sqrt(zoom);\n" +
            "           vec2 uvi = uv/duv, uvf = fract(uvi), uvf2 = uvf-0.5;\n" +
            "           float d = -zoom+1.0, a = zoom*2.0-1.0;\n" +
            "           float m = clamp(d+max(abs(uvf2.x),abs(uvf2.y))*a, 0.0, 0.5);\n" +
            "           return mix(texture(tex,uv), texture(tex,uv+(uvf2)*duv/zoom), m);\n" +
            "       }\n" +
            "       return texture(tex, uv);\n" +
            "   } else return bicubicInterpolation(tex, uv, duv);\n" +
            "}\n" +
            "vec4 getTexture(sampler2D tex, vec2 uv){ return getTexture(tex, uv, textureDeltaUV); }\n"


    /**
     * our code only uses 3, I think
     * */
    const val maxOutlineColors = 6

    private fun case(case: Int, path: String) = getReference("res://$path").readTextSync()
        .trim().run {
            replace("\r", "")
        }.run {
            "case $case:\n" + substring(indexOf('\n') + 1, lastIndexOf('\n')) + "\nbreak;\n"
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
                getForceFieldColor +
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
                "   if($hasForceFieldColor) color *= getForceFieldColor(finalPosition);\n" +
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

        init2()

    }

    val lineShader3D = BaseShader(
        "3d-lines", listOf(
            Variable(GLSLType.V3F, "coords", VariableMode.ATTR),
            Variable(GLSLType.M4x4, "transform")
        ),
        "" +
                "void main(){" +
                "   gl_Position = matMul(transform, vec4(coords, 1.0));\n" +
                "}", emptyList(),
        listOf(
            Variable(GLSLType.V4F, "color"),
            Variable(GLSLType.V3F, "finalColor", VariableMode.OUT),
            Variable(GLSLType.V1F, "finalAlpha", VariableMode.OUT)
        ), "" +
                "void main(){\n" +
                "   finalColor = color.rgb;\n" +
                "   finalAlpha = color.a;\n" +
                "}"
    )

    val f3D = "" +
            getTextureLib +
            getForceFieldColor +
            "void main(){\n" +
            // todo enable chroma separation for this?
            "   vec4 color = getTexture(tex, getProjectedUVs(uv, uvw, 0.0));\n" +
            "   if($hasForceFieldColor) color *= getForceFieldColor(finalPosition);\n" +
            "   finalColor = color.rgb;\n" +
            "   finalAlpha = color.a;\n" +
            "}"

    val shader3DPolygon =
        ShaderLib.createShader(
            "3d-polygon",
            v3DlPolygon, v3DPolygon, ShaderLib.y3D, ShaderLib.f3Dl, f3D,
            listOf("tex"),
            "tiling",
            "forceFieldUVCount"
        )

    val shader3DCircle = ShaderLib.createShader(
        "3dCircle", listOf(
            Variable(GLSLType.V2F, "coords", VariableMode.ATTR),// angle, inner/outer
            Variable(GLSLType.M4x4, "transform"),
            Variable(GLSLType.V3F, "circleParams"), // 1 - inner r, start, end
        ), "" +
                "void main(){\n" +
                "   float angle = mix(circleParams.y, circleParams.z, coords.x);\n" +
                "   vec2 betterUV = vec2(cos(angle), -sin(angle)) * (1.0 - circleParams.x * coords.y);\n" +
                "   finalPosition = vec3(betterUV, 0.0);\n" +
                "   gl_Position = matMul(transform, vec4(finalPosition, 1.0));\n" +
                ShaderLib.flatNormal +
                "}", ShaderLib.y3D, listOf(
            Variable(GLSLType.V3F, "finalColor", VariableMode.OUT),
            Variable(GLSLType.V1F, "finalAlpha", VariableMode.OUT)
        ), getForceFieldColor +
                "void main(){\n" +
                "   vec4 finalColor2 = ($hasForceFieldColor) ? getForceFieldColor(finalPosition) : vec4(1);\n" +
                "   finalColor = finalColor2.rgb;\n" +
                "   finalAlpha = finalColor2.a;\n" +
                "}", listOf(),
        "filtering", "textureDeltaUV", "tiling", "uvProjection", "forceFieldUVCount",
        "cgOffset", "cgSlope", "cgPower", "cgSaturation"
    )

    val shader3DText = ShaderLib.createShader(
        "3d-text", ShaderLib.v3Dl,
        "uniform vec3 offset;\n" +
                getForceFieldUVs +
                "void main(){\n" +
                "   vec3 localPos0 = coords + offset;\n" +
                // to do enable chroma separation for this? (probably impossible like that)
                "   vec2 pseudoUV2 = getForceFieldUVs(localPos0.xy*.5+.5, 0.0);\n" +
                "   finalPosition = $hasForceFieldUVs ? vec3(pseudoUV2*2.0-1.0, coords.z + offset.z) : localPos0;\n" +
                "   gl_Position = matMul(transform, vec4(finalPosition, 1.0));\n" +
                ShaderLib.flatNormal +
                "   vertexId = gl_VertexID;\n" +
                "}", ShaderLib.y3D + listOf(Variable(GLSLType.V1I, "vertexId").flat()), listOf(
            Variable(GLSLType.V3F, "finalColor", VariableMode.OUT),
            Variable(GLSLType.V1F, "finalAlpha", VariableMode.OUT)
        ), "" +
                getTextureLib +
                getForceFieldColor +
                "void main(){\n" +
                "   vec4 finalColor2 = ($hasForceFieldColor) ? getForceFieldColor(finalPosition) : vec4(1.0);\n" +
                "   finalColor = finalColor2.rgb;\n" +
                "   finalAlpha = finalColor2.a;\n" +
                "}", listOf(), "tiling", "forceFieldUVCount"
    )

    val shaderSDFText = ShaderLib.createShader(
        "3d-text-withOutline", listOf(
            Variable(GLSLType.V3F, "coords", VariableMode.ATTR),
            Variable(GLSLType.M4x4, "transform"),
            Variable(GLSLType.V2F, "offset"),
            Variable(GLSLType.V2F, "scale"),
        ),
        getForceFieldUVs +
                "void main(){\n" +
                "   uv = coords.xy * 0.5 + 0.5;\n" +
                "   vec2 localPos0 = coords.xy * scale + offset;\n" +
                // to do enable chroma separation for this? (probably impossible like that)
                "   vec2 pseudoUV2 = getForceFieldUVs(localPos0*.5+.5, 0.0);\n" +
                "   finalPosition = vec3($hasForceFieldUVs ? pseudoUV2*2.0-1.0 : localPos0, 0);\n" +
                "   gl_Position = matMul(transform, vec4(finalPosition, 1.0));\n" +
                "}", ShaderLib.y3D, listOf(
            Variable(GLSLType.S2D, "tex"),
            Variable(GLSLType.V4F, "colors", maxOutlineColors),
            Variable(GLSLType.V2F, "distSmoothness", maxOutlineColors),
            Variable(GLSLType.V1F, "depth"),
            Variable(GLSLType.V1I, "colorCount"),
            Variable(GLSLType.V4F, "tint"),
            Variable(GLSLType.V3F, "finalColor", VariableMode.OUT),
            Variable(GLSLType.V1F, "finalAlpha", VariableMode.OUT),
        ), "" +
                ShaderFuncLib.randomGLSL +
                getTextureLib +
                getForceFieldColor +
                "float smoothSign(float f){ return clamp(f,-1.0,1.0); }\n" +
                "void main(){\n" +
                "   float distance = texture(tex, uv).r - 0.5;\n" +
                "   float distDx = dFdx(distance);\n" +
                "   float distDy = dFdy(distance);\n" +
                "   float gradient = length(vec2(distDx, distDy));\n" +
                "#define IS_TINTED\n" +
                "   vec4 color = tint;\n" +
                "   for(int i=0;i<colorCount;i++){" +
                "       vec4 colorHere = colors[i];\n" +
                "       vec2 distSmooth = distSmoothness[i];\n" +
                "       float offset = distSmooth.x;\n" +
                "       float smoothness = distSmooth.y;\n" +
                "       float appliedGradient = max(smoothness, gradient);\n" +
                "       float mixingFactor0 = (distance-offset)*0.5/appliedGradient;\n" +
                "       float mixingFactor = clamp(mixingFactor0, 0.0, 1.0);\n" +
                "       color = mix(color, colorHere, mixingFactor);\n" +
                "   }\n" +
                "   #define CUSTOM_DEPTH\n" +
                "   gl_FragDepth = gl_FragCoord.z * (1.0 + distance * depth);\n" +
                "   if(color.a <= 0.001) discard;\n" +
                "   if($hasForceFieldColor) color *= getForceFieldColor(finalPosition);\n" +
                "   finalColor = color.rgb;\n" +
                "   finalAlpha = color.a;\n" +
                "}", listOf("tex"),
        "tiling",
        "filtering",
        "uvProjection",
        "forceFieldUVCount",
        "textureDeltaUV"
    )

    fun init2() {

        // with texture
        // somehow becomes dark for large |steps|-values

        val vSVGl = listOf(
            Variable(GLSLType.V3F, "aLocalPosition", VariableMode.ATTR),
            Variable(GLSLType.V2F, "aLocalPos2", VariableMode.ATTR),
            Variable(GLSLType.V4F, "aFormula0", VariableMode.ATTR),
            Variable(GLSLType.V1F, "aFormula1", VariableMode.ATTR),
            Variable(GLSLType.V4F, "aColor0", VariableMode.ATTR),
            Variable(GLSLType.V4F, "aColor1", VariableMode.ATTR),
            Variable(GLSLType.V4F, "aColor2", VariableMode.ATTR),
            Variable(GLSLType.V4F, "aColor3", VariableMode.ATTR),
            Variable(GLSLType.V4F, "aStops", VariableMode.ATTR),
            Variable(GLSLType.V1F, "aPadding", VariableMode.ATTR),
            Variable(GLSLType.M4x4, "transform")
        )

        val vSVG = "" +
                "void main(){\n" +
                "   finalPosition = aLocalPosition;\n" +
                "   gl_Position = matMul(transform, vec4(finalPosition, 1.0));\n" +
                ShaderLib.flatNormal +
                "   color0 = aColor0;\n" +
                "   color1 = aColor1;\n" +
                "   color2 = aColor2;\n" +
                "   color3 = aColor3;\n" +
                "   stops = aStops;\n" +
                "   padding = aPadding;\n" +
                "   localPos2 = aLocalPos2;\n" +
                "   formula0 = aFormula0;\n" +
                "   formula1 = aFormula1;\n" +
                "}"

        val ySVG = ShaderLib.y3D + listOf(
            Variable(GLSLType.V4F, "color0"),
            Variable(GLSLType.V4F, "color1"),
            Variable(GLSLType.V4F, "color2"),
            Variable(GLSLType.V4F, "color3"),
            Variable(GLSLType.V4F, "stops"),
            Variable(GLSLType.V4F, "formula0"), // pos, dir
            Variable(GLSLType.V1F, "formula1"), // is circle
            Variable(GLSLType.V1F, "padding"), // spread method / repetition type
            Variable(GLSLType.V2F, "localPos2"), // position for gradient
        )

        val fSVGl = listOf(
            Variable(GLSLType.V4F, "uvLimits"),
            Variable(GLSLType.S2D, "tex"),
            Variable(GLSLType.V3F, "finalColor", VariableMode.OUT),
            Variable(GLSLType.V1F, "finalAlpha", VariableMode.OUT)
        )

        val fSVG = "" +
                getTextureLib +
                getForceFieldColor +
                ShaderLib.brightness +
                colorGrading +
                "bool isInLimits(float value, vec2 minMax){\n" +
                "   return value >= minMax.x && value <= minMax.y;\n" +
                "}\n" + // sqrt and ² for better color mixing
                "vec4 mix2(vec4 a, vec4 b, float stop, vec2 stops){\n" +
                "   float f = clamp((stop-stops.x)/(stops.y-stops.x), 0.0, 1.0);\n" +
                "   return vec4(sqrt(mix(a.rgb*a.rgb, b.rgb*b.rgb, f)), mix(a.a, b.a, f));\n" +
                "}\n" +
                "void main(){\n" +
                // apply the formula; polynomial of 2nd degree
                "   vec2 delta = localPos2 - formula0.xy;\n" +
                "   vec2 dir = formula0.zw;\n" +
                "   float stopValue = formula1 > 0.5 ? length(delta * dir) : dot(dir, delta);\n" +

                "   if(padding < 0.5){\n" + // clamp
                "       stopValue = clamp(stopValue, 0.0, 1.0);\n" +
                "   } else if(padding < 1.5){\n" + // repeat mirrored, and yes, it looks like magic xD
                "       stopValue = 1.0 - abs(fract(stopValue*0.5)*2.0-1.0);\n" +
                "   } else {\n" + // repeat
                "       stopValue = fract(stopValue);\n" +
                "   }\n" +

                // find the correct color
                "   vec4 color = \n" +
                "       stopValue <= stops.x ? color0:\n" +
                "       stopValue >= stops.w ? color3:\n" +
                "       stopValue <  stops.y ? mix2(color0, color1, stopValue, stops.xy):\n" +
                "       stopValue <  stops.z ? mix2(color1, color2, stopValue, stops.yz):\n" +
                "                              mix2(color2, color3, stopValue, stops.zw);\n" +
                // "   color.rgb = fract(vec3(stopValue));\n" +
                "   color.rgb = colorGrading(color.rgb);\n" +
                "   if($hasForceFieldColor) color *= getForceFieldColor(finalPosition);\n" +
                "   if(isInLimits(uv.x, uvLimits.xz) && isInLimits(uv.y, uvLimits.yw)){" +
                "       vec4 color2 = color * getTexture(tex, uv * 0.5 + 0.5);\n" +
                "       finalColor = color2.rgb;\n" +
                "       finalAlpha = color2.a;\n" +
                "   } else {" +
                "       finalColor = vec3(0);\n" +
                "       finalAlpha = 0.0;\n" +
                "   }" +
                "}"

        shader3DSVG = ShaderLib.createShader("3d-svg", vSVGl, vSVG, ySVG, fSVGl, fSVG, listOf("tex"))
    }

    lateinit var shader3DSVG: BaseShader

    val linePolygonShader = BaseShader(
        // todo uniforms + attributes to variables
        "linePolygon", listOf(
            Variable(GLSLType.V3F, "coords", VariableMode.ATTR),
            Variable(GLSLType.V2F, "uvs", VariableMode.ATTR),
            Variable(GLSLType.M4x4, "transform"),
            Variable(GLSLType.V4F, "tiling"),
            Variable(GLSLType.V3F, "pos0"), Variable(GLSLType.V3F, "pos1"),
            Variable(GLSLType.V3F, "pos2"), Variable(GLSLType.V3F, "pos3"),
            Variable(GLSLType.V4F, "col0"), Variable(GLSLType.V4F, "col1"),
            Variable(GLSLType.V1F, "zDistance", VariableMode.OUT)
        ), "" +
                "void main(){\n" +
                "   vec2 att = coords.xy*0.5+0.5;\n" +
                "   vec3 localPosition = mix(mix(pos0, pos1, att.x), mix(pos2, pos3, att.x), att.y);\n" +
                "   gl_Position = transform * vec4(localPosition, 1.0);\n" +
                ShaderLib.flatNormal +
                "   uv = uvs;\n" +
                "   uvw = coords;\n" +
                "   colX = mix(col0, col1, att.y);\n" +
                "}", ShaderLib.y3D + Variable(GLSLType.V4F, "colX"), listOf(), "" +
                getTextureLib +
                getForceFieldColor +
                "void main(){\n" +
                "   vec4 color = colX;\n" +
                "   if($hasForceFieldColor) color *= getForceFieldColor(finalPosition);\n" +
                // does work, just the error should be cleaner...
                // "   gl_FragDepth += 0.01 * random(uv);\n" +
                "   gl_FragColor = color;\n" +
                "}"
    )

}