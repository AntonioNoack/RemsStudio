package ofx.mio

import me.anno.gpu.GFXState.useFrame
import me.anno.gpu.buffer.SimpleBuffer.Companion.flat01
import me.anno.gpu.framebuffer.DepthBufferType
import me.anno.gpu.framebuffer.FBStack
import me.anno.gpu.framebuffer.IFramebuffer
import me.anno.gpu.shader.GLSLType
import me.anno.gpu.shader.ShaderLib.coordsList
import me.anno.gpu.shader.ShaderLib.coordsUVVertexShader
import me.anno.gpu.shader.ShaderLib.createShader
import me.anno.gpu.shader.ShaderLib.uvList
import me.anno.gpu.shader.builder.Variable
import me.anno.gpu.shader.renderer.Renderer
import me.anno.gpu.texture.Clamping
import me.anno.gpu.texture.Filtering
import me.anno.gpu.texture.Texture2D

object OpticalFlow {

    fun run(lambda: Float, blurAmount: Float, displacement: Float, t0: Texture2D, t1: Texture2D): IFramebuffer {

        val w = t0.width
        val h = t0.height

        val flowT = FBStack["flow", w, h, 4, false, 1, DepthBufferType.NONE]

        // flow process

        useFrame(flowT, Renderer.colorRenderer) {
            val flow = flowShader.value.value
            flow.use()
            flow.v2f("scale", 1f, 1f)
            flow.v2f("offset", 1f / w, 1f / h)
            flow.v1f("lambda", lambda)
            t0.bind(0, Filtering.LINEAR, Clamping.CLAMP)
            t1.bind(1, Filtering.LINEAR, Clamping.CLAMP)
            flat01.draw(flow)
        }

        // blur process

        val blur = blurShader.value.value
        blur.use()
        blur.v1f("blurSize", blurAmount)
        blur.v1f("sigma", blurAmount * 0.5f)
        blur.v2f("texOffset", 2f, 2f)

        val blurH = FBStack["blurH", w, h, 4, false, 1, DepthBufferType.NONE]
        useFrame(blurH, Renderer.colorRenderer) {
            flowT.bindTexture0(0, Filtering.TRULY_NEAREST, Clamping.CLAMP)
            blur.v1f("horizontalPass", 1f)
            flat01.draw(blur)
        }

        val blurV = FBStack["blurV", w, h, 4, false, 1, DepthBufferType.NONE]
        useFrame(blurV, Renderer.colorRenderer) {
            blurH.bindTexture0(0, Filtering.LINEAR, Clamping.CLAMP)
            blur.v1f("horizontalPass", 0f)
            flat01.draw(blur)
        }

        val result = FBStack["reposition", w, h, 4, false, 1, DepthBufferType.NONE]
        useFrame(result, Renderer.colorRenderer) {

            // reposition
            val repos = repositionShader.value.value
            repos.use()
            repos.v2f("amt", displacement * 0.25f)

            t0.bind(0, Filtering.LINEAR, Clamping.CLAMP)
            blurV.bindTextures(1, Filtering.LINEAR, Clamping.CLAMP)
            flat01.draw(repos)

        }

        return result

    }

    val flowShader = lazy {
        createShader(
            "flow", coordsList, coordsUVVertexShader, uvList, listOf(
                Variable(GLSLType.S2D, "tex0"),
                Variable(GLSLType.S2D, "tex1"),
                Variable(GLSLType.V2F, "scale"),
                Variable(GLSLType.V2F, "offset"),
                Variable(GLSLType.V1F, "lambda"),
            ), "" +
                    "vec4 getColorCoded(float x, float y, vec2 scale) {\n" +
                    "   vec2 xOut = vec2(max(x,0.),max(-x,0.))*scale.x;\n" +
                    "   vec2 yOut = vec2(max(y,0.),max(-y,0.))*scale.y;\n" +
                    "   float dirY = 1;\n" +
                    "   if (yOut.x > yOut.y) dirY = 0.90;\n" +
                    "   return vec4(xOut.xy, max(yOut.x, yOut.y), dirY);\n" +
                    "}\n" +
                    "void main(){\n" +

                    "   vec4 a = texture(tex0, uv);\n" +
                    "   vec4 b = texture(tex1, uv);\n" +
                    "   vec2 x1 = vec2(offset.x,0.);\n" +
                    "   vec2 y1 = vec2(0.,offset.y);\n" +

                    "   // get the difference\n" +
                    "   vec4 diffByTime = b-a;\n" +

                    "   // calculate the gradient\n" +
                    "   // for X\n" +
                    "   float gradX = texture(tex1, uv+x1).r-texture(tex1, uv-x1).r;\n" +
                    "   gradX += texture(tex0, uv+x1).r-texture(tex0, uv-x1).r;\n" +

                    "   // for Y\n" +
                    "   float gradY = texture(tex1, uv+y1).r-texture(tex1, uv-y1).r;\n" +
                    "   gradY += texture(tex0, uv+y1).r-texture(tex0, uv-y1).r;\n" +

                    "   vec2 grad = vec2(gradX, gradY);\n" +
                    "   float gradMagnitude = sqrt(dot(grad,grad) + lambda);\n" +
                    "   vec2 vxy = diffByTime.rg * (grad / gradMagnitude);\n" +
                    "   gl_FragColor = getColorCoded(vxy.r, vxy.g, scale);\n" +

                    "}  " +
                    "", listOf("tex0", "tex0")
        )
    }

    val blurShader = lazy {
        createShader(
            "blur", coordsList, coordsUVVertexShader, uvList, listOf(
                Variable(GLSLType.S2D, "tex"),
                Variable(GLSLType.V2F, "texOffset"),
                Variable(GLSLType.V1F, "blurSize"),
                Variable(GLSLType.V1F, "horizontalPass"),// 0 or 1 to indicate vertical or horizontal pass
                // The sigma value for the gaussian function: higher value means more blur
                Variable(GLSLType.V1F, "sigma"),
            ), "" +
                    "// A good value for 9x9 is around 3 to 5\n" +
                    "// A good value for 7x7 is around 2.5 to 4\n" +
                    "// A good value for 5x5 is around 2 to 3.5\n" +
                    "// ... play around with this based on what you need :)\n" +
                    "const float pi = 3.14159265;\n" +
                    "vec4 get2DOff(sampler2D tex, vec2 coord) {\n" +
                    "   vec4 col = texture(tex, coord);\n" +
                    "   if (col.w >0.95) col.z = -col.z;\n" +
                    "   return vec4(col.y-col.x, col.z, 1, 1);\n" +
                    "}\n" +
                    "vec4 getColorCoded(float x, float y, vec2 scale) {\n" +
                    "   vec2 xOut = vec2(max(x,0.),max(-x,0.))*scale.x;\n" +
                    "   vec2 yOut = vec2(max(y,0.),max(-y,0.))*scale.y;\n" +
                    "   float dirY = 1;\n" +
                    "   if (yOut.x > yOut.y) dirY = 0.90;\n" +
                    "   return vec4(xOut.yx,max(yOut.x,yOut.y),dirY);\n" +
                    "}\n" +
                    "void main() {  \n" +

                    "   float numBlurPixelsPerSide = float(blurSize / 2); \n" +
                    "   vec2 blurMultiplyVec = 0 < horizontalPass ? vec2(1.0, 0.0) : vec2(0.0, 1.0);\n" +

                    // Incremental Gaussian Coefficent Calculation (See GPU Gems 3 pp. 877 - 889)
                    "   vec3 incrementalGaussian;\n" +
                    "   incrementalGaussian.x = 1.0 / (sqrt(2.0 * pi) * sigma);\n" +
                    "   incrementalGaussian.y = exp(-0.5 / (sigma * sigma));\n" +
                    "   incrementalGaussian.z = incrementalGaussian.y * incrementalGaussian.y;\n" +

                    "   vec4 avgValue = vec4(0.0, 0.0, 0.0, 0.0);\n" +
                    "   float coefficientSum = 0.0;\n" +

                    "   // Take the central sample first...\n" +
                    "   avgValue += get2DOff(tex, uv.st) * incrementalGaussian.x;\n" +
                    "   coefficientSum += incrementalGaussian.x;\n" +
                    "   incrementalGaussian.xy *= incrementalGaussian.yz;\n" +

                    // Go through the remaining 8 vertical samples (4 on each side of the center)
                    "       for (float i = 1.0; i <= numBlurPixelsPerSide; i++) { \n" +
                    "       avgValue += get2DOff(tex, uv.st - i * texOffset * \n" +
                    "           blurMultiplyVec) * incrementalGaussian.x;         \n" +
                    "       avgValue += get2DOff(tex, uv.st + i * texOffset * \n" +
                    "           blurMultiplyVec) * incrementalGaussian.x;         \n" +
                    "       coefficientSum += 2.0 * incrementalGaussian.x;\n" +
                    "       incrementalGaussian.xy *= incrementalGaussian.yz;\n" +
                    "   }\n" +
                    "   vec4 finColor = avgValue / coefficientSum;\n" +
                    "   gl_FragColor = getColorCoded(finColor.x, finColor.y, vec2(1,1));\n" +
                    "}", listOf("tex")
        )
    }

    val repositionShader = lazy {
        createShader(
            "reposition", coordsList, coordsUVVertexShader, uvList, listOf(
                Variable(GLSLType.V2F, "amt"),
                Variable(GLSLType.S2D, "tex0"),
                Variable(GLSLType.S2D, "tex1")
            ), "" +
                    "vec2 get2DOff(sampler2D tex, vec2 coord) {\n" +
                    "   vec4 col = texture(tex, coord);\n" +
                    "   if (col.w > 0.95) col.z = -col.z;\n" +
                    "   return vec2(col.x-col.y, col.z);\n" +
                    "}\n" +
                    "void main(){\n" +
                    "   vec2 coord = get2DOff(tex1, uv) * amt + uv;// relative coordinates\n" +
                    "   vec4 repos = texture(tex0, coord);\n" +
                    "   gl_FragColor = repos;\n" +
                    "}", listOf("tex0", "tex1")
        )
    }

}