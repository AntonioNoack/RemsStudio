package me.anno.gpu

import me.anno.gpu.shader.Shader
import me.anno.gpu.shader.ShaderPlus
import me.anno.gpu.texture.FilteringMode
import me.anno.objects.effects.MaskType
import me.anno.objects.modes.UVProjection
import org.lwjgl.opengl.GL20
import kotlin.math.PI

object ShaderLib {

    lateinit var flatShader: Shader
    lateinit var flatShaderGradient: Shader
    lateinit var flatShaderTexture: Shader
    lateinit var subpixelCorrectTextShader: Shader
    lateinit var shader3D: ShaderPlus
    lateinit var shader3DPolygon: ShaderPlus
    lateinit var shader3DYUV: ShaderPlus
    lateinit var shader3DARGB: ShaderPlus
    lateinit var shader3DBGRA: ShaderPlus
    lateinit var shader3DCircle: Shader
    lateinit var shader3DSVG: ShaderPlus
    lateinit var lineShader3D: Shader
    lateinit var shader3DMasked: ShaderPlus
    lateinit var shaderObjMtl: ShaderPlus

    fun init(){

        // make this customizable?

        // color only for a rectangle
        // (can work on more complex shapes)
        flatShader = Shader("flatShader",
            "" +
                    "a2 attr0;\n" +
                    "u2 pos, size;\n" +
                    "void main(){\n" +
                    "   gl_Position = vec4((pos + attr0 * size)*2.-1., 0.0, 1.0);\n" +
                    "}", "", "" +
                    "u4 color;\n" +
                    "void main(){\n" +
                    "   gl_FragColor = color;\n" +
                    "}"
        )

        flatShaderGradient = Shader("flatShaderGradient",
            "" +
                    "a2 attr0;\n" +
                    "u2 pos, size;\n" +
                    "u4 lColor, rColor;\n" +
                    "void main(){\n" +
                    "   gl_Position = vec4((pos + attr0 * size)*2.-1., 0.0, 1.0);\n" +
                    "   color = attr0.x < 0.5 ? lColor : rColor;\n" +
                    "}", "" + // mixing is done by varying
                    "varying vec4 color;\n", "" +
                    "void main(){\n" +
                    "   gl_FragColor = color;\n" +
                    "}"
        )

        flatShaderTexture = Shader("flatShaderTexture",
            "" +
                    "a2 attr0;\n" +
                    "u2 pos, size;\n" +
                    "u4 tiling;\n" +
                    "void main(){\n" +
                    "   gl_Position = vec4((pos + attr0 * size)*2.-1., 0.0, 1.0);\n" +
                    "   uv = (attr0-0.5) * tiling.xy + 0.5 + tiling.zw;\n" +
                    "}", "" +
                    "varying vec2 uv;\n", "" +
                    "uniform sampler2D tex;\n" +
                    "u4 color;\n" +
                    "void main(){\n" +
                    "   gl_FragColor = color * texture(tex, uv);\n" +
                    "}"
        )

        // with texture
        subpixelCorrectTextShader = Shader("subpixelCorrectTextShader",
            "" +
                    "a2 attr0;\n" +
                    "u2 pos, size;\n" +
                    "void main(){\n" +
                    "   gl_Position = vec4((pos + attr0 * size)*2.-1., 0.0, 1.0);\n" +
                    "   uv = attr0;\n" +
                    "}", "" +
                    "varying v2 uv;\n", "" +
                    "uniform vec4 textColor;" +
                    "uniform vec3 backgroundColor;\n" +
                    "uniform sampler2D tex;\n" +
                    "float brightness(vec3 color){" +
                    "   return dot(color, vec3(1.));\n" +
                    "}" +
                    "void main(){\n" +
                    "   vec3 textMask = texture(tex, uv).rgb;\n" +
                    "   vec3 mixing = brightness(textColor.rgb) > brightness(backgroundColor) ? textMask.rgb : textMask.bgr;\n" +
                    "   vec3 color = vec3(\n" +
                    "       mix(backgroundColor.r, textColor.r, mixing.r),\n" +
                    "       mix(backgroundColor.g, textColor.g, mixing.g),\n" +
                    "       mix(backgroundColor.b, textColor.b, mixing.b));\n" +
                    "   gl_FragColor = vec4(color, textColor.a);\n" +
                    "}"
        )

        subpixelCorrectTextShader.use()
        GL20.glUniform1i(subpixelCorrectTextShader["tex"], 0)

        val bicubicInterpolation = "" +
                // https://www.paulinternet.nl/?page=bicubic
                "vec4 cubicInterpolation(vec4 p0, vec4 p1, vec4 p2, vec4 p3, float x){\n" +
                "   return p1 + 0.5 * x*(p2 - p0 + x*(2.0*p0 - 5.0*p1 + 4.0*p2 - p3 + x*(3.0*(p1 - p2) + p3 - p0)));\n" +
                "}\n" +
                "vec4 cubicInterpolation(sampler2D tex, vec2 uv, float du, float x){\n" +
                "   vec4 p0 = texture(tex, vec2(uv.x - du, uv.y));\n" +
                "   vec4 p1 = texture(tex, vec2(uv.x     , uv.y));\n" +
                "   vec4 p2 = texture(tex, vec2(uv.x + du, uv.y));\n" +
                "   vec4 p3 = texture(tex, vec2(uv.x+2*du, uv.y));\n" +
                "   return cubicInterpolation(p0, p1, p2, p3, x);\n" +
                "}\n" +
                "vec4 bicubicInterpolation(sampler2D tex, vec2 uv, vec2 duv){\n" +
                "   uv -= 0.5*duv;\n" +
                "   vec2 xy = fract(uv / duv);\n" +
                "   vec4 p0 = cubicInterpolation(tex, vec2(uv.x, uv.y - duv.y), duv.x, xy.x);\n" +
                "   vec4 p1 = cubicInterpolation(tex, vec2(uv.x, uv.y        ), duv.x, xy.x);\n" +
                "   vec4 p2 = cubicInterpolation(tex, vec2(uv.x, uv.y + duv.y), duv.x, xy.x);\n" +
                "   vec4 p3 = cubicInterpolation(tex, vec2(uv.x, uv.y+2*duv.y), duv.x, xy.x);\n" +
                "   return cubicInterpolation(p0, p1, p2, p3, xy.y);\n" +
                "}\n"

        val getTextureLib = "" +
                bicubicInterpolation +
                "uniform vec2 textureDeltaUV;\n" +
                "uniform int filtering, uvProjection;\n" +
                "vec2 getProjectedUVs(vec2 uv, vec3 uvw){\n" +
                // the uvs correspond to the used mesh
                // used meshes are flat01 and cubemapBuffer
                "   switch(uvProjection){\n" +
                "       case ${UVProjection.Equirectangular.id}:\n" +
                "           float u = atan(uvw.z, uvw.x)*${0.5/ PI}+0.5;\n " +
                "           float v = atan(uvw.y, length(uvw.xz))*${1.0/PI}+0.5;\n" +
                "           return vec2(u, v);\n" +
                "       case ${UVProjection.TiledCubemap.id}:\n" +
                "           return uv;\n" + // correct???
                "       default:\n" +
                "           return uv;\n" +
                "   }\n" +
                "}\n" +
                "vec4 getTexture(sampler2D tex, vec2 uv, vec2 duv){" +
                "   switch(filtering){" +
                "       case ${FilteringMode.NEAREST.id}:\n" +
                "       case ${FilteringMode.LINEAR.id}:\n" +
                "           return texture(tex, uv);\n" +
                "       case ${FilteringMode.CUBIC.id}:\n" +
                "           return bicubicInterpolation(tex, uv, duv);\n" +
                "   }\n" +
                "}\n" +
                "vec4 getTexture(sampler2D tex, vec2 uv){" +
                "   switch(filtering){" +
                "       case ${FilteringMode.NEAREST.id}:\n" +
                "       case ${FilteringMode.LINEAR.id}:\n" +
                "           return texture(tex, uv);\n" +
                "       case ${FilteringMode.CUBIC.id}:\n" +
                "           return bicubicInterpolation(tex, uv, textureDeltaUV);\n" +
                "   }\n" +
                "}\n"

        val positionPostProcessing = "" +
                "zDistance = gl_Position.w;\n"

        val colorPostProcessing = "" +
                "   gl_FragColor.rgb *= gl_FragColor.rgb;\n"

        val colorProcessing = "" +
                "   if(color.a <= 0.0) discard;\n"

        val v3DBase = "" +
                "uniform mat4 transform;\n" +
                "" +
                "vec4 transform3D(vec2 betterUV){\n" +
                "   return transform * vec4(betterUV, 0.0, 1.0);\n" +
                "}\n"

        val v3D = v3DBase +
                "a3 attr0;\n" +
                "a2 attr1;\n" +
                "u4 tiling;\n" +
                "void main(){\n" +
                "   gl_Position = transform * vec4(attr0, 1.0);\n" +
                positionPostProcessing +
                "   uv = (attr1-0.5) * tiling.xy + 0.5 + tiling.zw;\n" +
                "   uvw = attr0;\n" +
                "}"

        val v3DMasked = v3DBase +
                "a2 attr0;\n" +
                "void main(){\n" +
                "   vec2 betterUV = attr0*2.-1.;\n" +
                "   gl_Position = transform3D(betterUV);\n" +
                positionPostProcessing +
                "   uv = gl_Position.xyw;\n" +
                "}"

        val v3DPolygon = v3DBase +
                "a3 attr0;\n" +
                "in vec2 attr1;\n" +
                "uniform float inset;\n" +
                "void main(){\n" +
                "   vec2 betterUV = attr0.xy*2.-1.;\n" +
                "   betterUV *= mix(1.0, attr1.r, inset);\n" +
                "   gl_Position = transform * vec4(betterUV, attr0.z, 1.0);\n" +
                positionPostProcessing +
                "   uv = attr1.yx;\n" +
                "}"

        val v3DSVG = v3DBase +
                "a3 attr0;\n" +
                "a4 attr1;\n" +
                "void main(){\n" +
                "   vec2 betterUV = attr0.xy*2.-1.;\n" +
                "   gl_Position = transform * vec4(betterUV, attr0.z, 1.0);\n" +
                positionPostProcessing +
                "   uv = attr0.xy;\n" +
                "   color = attr1;\n" +
                "}"

        val y3D = "" +
                "varying v2 uv;\n" +
                "varying v3 uvw;\n" +
                "varying float zDistance;\n"

        val y3DSVG = y3D +
                "varying v4 color;\n"

        val f3D = "" +
                "uniform vec4 tint;" +
                "uniform sampler2D tex;\n" +
                getTextureLib +
                "void main(){\n" +
                "   vec4 color = getTexture(tex, getProjectedUVs(uv, uvw));\n" +
                colorProcessing +
                "   gl_FragColor = tint * color;\n" +
                colorPostProcessing +
                "}"

        val y3DMasked = "" +
                "varying v3 uv;\n" +
                "varying float zDistance;\n"

        val f3DMasked = "" +
                "uniform vec4 tint;" +
                "uniform sampler2D tex, mask;\n" +
                "uniform vec4 offsetColor;\n" +
                "uniform float useMaskColor;\n" +
                "uniform float invertMask;\n" +
                "uniform vec2 pixelating;\n" +
                "uniform vec2 blurDeltaUV;\n" +
                "uniform int maskType;\n" +
                "uniform float maxSteps;\n" +
                "void main(){\n" +
                "   vec2 uv2 = uv.xy/uv.z * 0.5 + 0.5;\n" +
                "   vec4 mask = texture(mask, uv2);\n" +
                "   vec4 color;\n" +
                "   float effect, sum;\n" +
                "   switch(maskType){\n" +
                "       case ${MaskType.MASKING.id}:\n" +
                "           vec4 maskColor = vec4(" +
                "               mix(vec3(1.0), mask.rgb, useMaskColor)," +
                "               mix(mask.a, 1.0-mask.a, invertMask));\n" +
                "           color = texture(tex, uv2);\n" +
                "           gl_FragColor = offsetColor + tint * color * maskColor;\n" +
                "           break;\n" +
                "       case ${MaskType.PIXELATING.id}:\n" +
                // use the average instead for more stable results?
                "           effect = mix(mask.a, dot(vec3(0.3), mask.rgb), useMaskColor);\n" +
                "           effect = mix(effect, 1.0 - effect, invertMask);\n" +
                "           color = mix(" +
                "               texture(tex, uv2), " +
                "               texture(tex, round(uv2 / pixelating) * pixelating)," +
                "               effect);\n" +
                "           gl_FragColor = offsetColor + tint * color;\n" +
                "           break;\n" +
                "       case ${MaskType.GAUSSIAN_BLUR.id}:\n" +
                "           effect = mix(mask.a, dot(vec3(0.3), mask.rgb), useMaskColor);\n" +
                "           effect = mix(effect, 1.0 - effect, invertMask);\n" +
                "           sum = 0.0;\n" +
                // test all steps for -pixelating*2 .. pixelating*2, then average
                "           float steps = effect * maxSteps;\n" +
                "           int pixelSize = max(0, int(2.7 * steps));\n" +
                "           if(pixelSize == 0){\n" +
                "               color = texture(tex, uv2);\n" +
                "           } else {\n" +
                "               color = vec4(0.0);\n" +
                "               for(int i=-pixelSize;i<=pixelSize;i++){\n" +
                "                   float relativeX = float(i)/steps;\n" +
                "                   float weight = i == 0 ? 1.0 : exp(-relativeX*relativeX);\n" +
                "                   sum += weight;\n" +
                "                   color += texture(tex, uv2 + relativeX * blurDeltaUV) * weight;\n" +
                "               }\n" +
                "               color /= sum;\n" +
                "           }\n" +
                "           gl_FragColor = offsetColor + tint * color;\n" +
                "           break;\n" +
                "   }\n" +
                colorProcessing +
                "   if(gl_FragColor.a <= 0.0) discard;\n" +
                "   gl_FragColor.a = min(gl_FragColor.a, 1.0);\n" +
                // no postprocessing, because it was already applied
                "}"

        val f3DSVG = "" +
                "uniform vec4 tint;" +
                "uniform sampler2D tex;\n" +
                getTextureLib +
                "void main(){\n" +
                "   gl_FragColor = tint * color * getTexture(tex, uv);\n" +
                colorPostProcessing +
                "}"


        val v3DCircle = v3DBase +
                "a2 attr0;\n" + // angle, inner/outer
                "u3 circleParams;\n" + // 1 - inner r, start, end
                "void main(){\n" +
                "   float angle = mix(circleParams.y, circleParams.z, attr0.x);\n" +
                "   vec2 betterUV = vec2(cos(angle), -sin(angle)) * (1.0 - circleParams.x * attr0.y);\n" +
                "   gl_Position = transform * vec4(betterUV, 0.0, 1.0);\n" +
                positionPostProcessing +
                "}"

        val f3DCircle = "" +
                "u4 tint;\n" + // rgba
                "void main(){\n" +
                "   gl_FragColor = tint;\n" +
                colorPostProcessing +
                /*"   vec2 d0 = uv*2.-1.;\n" +
                "   float dst = dot(d0,d0);\n" +
                "   if(dst > 1.0 || dst < circleParams.r) discard;\n" +
                "   else {" +
                "       float angle = atan(d0.y,d0.x);\n" +
                "       if(circleParams.g < circleParams.b){" +
                "           if(angle < circleParams.g || angle > circleParams.b) discard;" +
                "       } else {" +
                "           if(angle > circleParams.b && angle < circleParams.g) discard;" +
                "       }" +
                "   }" +*/
                "}"

        shader3D = createShaderPlus("3d", v3D, y3D, f3D, listOf("tex"))
        shader3DPolygon = createShaderPlus("3d-polygon", v3DPolygon, y3D, f3D, listOf("tex"))

        // create the obj+mtl shader
        shaderObjMtl = createShaderPlus("obj/mtl",
            v3DBase +
                    "a3 coords;\n" +
                    "a2 uvs;\n" +
                    "a3 normals;\n" +
                    "void main(){\n" +
                    "   gl_Position = transform * vec4(coords, 1.0);\n" +
                    "   uv = uvs;\n" +
                    "   normal = normals;\n" +
                    positionPostProcessing +
                    "}", y3D + "" +
                    "varying vec3 normal;\n", "" +
                    "uniform vec4 tint;" +
                    "uniform sampler2D tex;\n" +
                    getTextureLib +
                    "void main(){\n" +
                    "   vec4 color = getTexture(tex, uv);\n" +
                    "   color.rgb *= 0.5 + 0.5 * dot(vec3(1.0, 0.0, 0.0), normal);\n" +
                    colorProcessing +
                    "   gl_FragColor = tint * color;\n" +
                    colorPostProcessing +
                    "}", listOf()
        )

        shader3DCircle = Shader("3dCircle", v3DCircle, y3D, f3DCircle)
        shader3DMasked = createShaderPlus("3d-masked", v3DMasked, y3DMasked, f3DMasked, listOf("mask", "tex"))

        shader3DSVG = createShaderPlus("3d-svg", v3DSVG, y3DSVG, f3DSVG, listOf("tex"))

        shader3DYUV = createShaderPlus("3d-yuv",
            v3D, y3D, "" +
                    "uniform vec4 tint;" +
                    "uniform sampler2D texY, texU, texV;\n" +
                    "uniform vec2 uvCorrection;\n" +
                    getTextureLib +
                    "void main(){\n" +
                    "   vec2 uv2 = getProjectedUVs(uv, uvw);\n" +
                    "   vec2 correctedUV = uv2*uvCorrection;\n" +
                    "   vec2 correctedDUV = textureDeltaUV*uvCorrection;\n" +
                    "   vec3 yuv = vec3(" +
                    "       getTexture(texY, uv2).r, " +
                    "       getTexture(texU, correctedUV, correctedDUV).r, " +
                    "       getTexture(texV, correctedUV, correctedDUV).r);\n" +
                    "   yuv -= vec3(${16f / 255f}, 0.5, 0.5);\n" +
                    "   vec3 rgb = vec3(" +
                    "       dot(yuv, vec3( 1.164,  0.000,  1.596))," +
                    "       dot(yuv, vec3( 1.164, -0.392, -0.813))," +
                    "       dot(yuv, vec3( 1.164,  2.017,  0.000)));\n" +
                    "   gl_FragColor = vec4(tint.rgb * rgb, tint.a);\n" +
                    colorPostProcessing +
                    "}", listOf("texY", "texU", "texV")
        )

        shader3DARGB = createShaderPlus("3d-argb",
            v3D, y3D, "" +
                    "uniform vec4 tint;" +
                    "uniform sampler2D tex;\n" +
                    getTextureLib +
                    "void main(){\n" +
                    "   vec4 color = getTexture(tex, getProjectedUVs(uv, uvw)).gbar;\n" +
                    colorProcessing +
                    "   gl_FragColor = tint * color;\n" +
                    colorPostProcessing +
                    "}", listOf("tex")
        )

        shader3DBGRA = createShaderPlus("3d-bgra",
            v3D, y3D, "" +
                    "uniform vec4 tint;" +
                    "uniform sampler2D tex;\n" +
                    getTextureLib +
                    "void main(){\n" +
                    "   vec4 color = getTexture(tex, getProjectedUVs(uv, uvw)).bgra;\n" +
                    colorProcessing +
                    "   gl_FragColor = tint * color;\n" +
                    colorPostProcessing +
                    "}", listOf("tex")
        )

        lineShader3D = Shader("3d-lines",
            "in vec3 attr0;\n" +
                    "uniform mat4 transform;\n" +
                    "void main(){" +
                    "   gl_Position = transform * vec4(attr0, 1.0);\n" +
                    positionPostProcessing +
                    "}", "" +
                    "varying float zDistance;\n", "" +
                    "uniform vec4 color;\n" +
                    "void main(){" +
                    "   gl_FragColor = color;\n" +
                    colorPostProcessing +
                    "}"

        )

    }


    fun createShaderNoShorts(shaderName: String, v3D: String, y3D: String, fragmentShader: String, textures: List<String>): Shader {
        val shader = Shader(shaderName, v3D, y3D, fragmentShader, true)
        shader.use()
        textures.forEachIndexed { index, name ->
            GL20.glUniform1i(shader[name], index)
        }
        return shader
    }

    fun createShaderPlus(shaderName: String, v3D: String, y3D: String, fragmentShader: String, textures: List<String>): ShaderPlus {
        val shader = ShaderPlus(shaderName, v3D, y3D, fragmentShader)
        for(shader2 in listOf(shader.shader)){
            shader2.use()
            textures.forEachIndexed { index, name ->
                GL20.glUniform1i(shader2[name], index)
            }
        }
        return shader
    }

    fun createShader(shaderName: String, v3D: String, y3D: String, fragmentShader: String, textures: List<String>): Shader {
        val shader = Shader(shaderName, v3D, y3D, fragmentShader)
        shader.use()
        textures.forEachIndexed { index, name ->
            GL20.glUniform1i(shader[name], index)
        }
        return shader
    }


}