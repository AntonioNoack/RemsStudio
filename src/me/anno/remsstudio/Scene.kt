package me.anno.remsstudio

import me.anno.config.DefaultConfig
import me.anno.gpu.DepthMode
import me.anno.gpu.GFX
import me.anno.gpu.GFX.isFinalRendering
import me.anno.gpu.GFXState
import me.anno.gpu.GFXState.blendMode
import me.anno.gpu.GFXState.depthMask
import me.anno.gpu.GFXState.depthMode
import me.anno.gpu.GFXState.renderDefault
import me.anno.gpu.GFXState.renderPurely
import me.anno.gpu.GFXState.useFrame
import me.anno.gpu.blending.BlendMode
import me.anno.gpu.buffer.SimpleBuffer
import me.anno.gpu.drawing.DrawRectangles.drawRect
import me.anno.gpu.framebuffer.DepthBufferType
import me.anno.gpu.framebuffer.FBStack
import me.anno.gpu.framebuffer.Framebuffer
import me.anno.gpu.framebuffer.IFramebuffer
import me.anno.gpu.shader.BaseShader
import me.anno.gpu.shader.GLSLType
import me.anno.gpu.shader.Shader
import me.anno.gpu.shader.ShaderFuncLib.acesToneMapping
import me.anno.gpu.shader.ShaderFuncLib.randomGLSL
import me.anno.gpu.shader.ShaderFuncLib.reinhardToneMapping
import me.anno.gpu.shader.ShaderFuncLib.uchimuraToneMapping
import me.anno.gpu.shader.ShaderLib.brightness
import me.anno.gpu.shader.ShaderLib.coordsList
import me.anno.gpu.shader.ShaderLib.coordsUVVertexShader
import me.anno.gpu.shader.ShaderLib.createShader
import me.anno.gpu.shader.ShaderLib.uvList
import me.anno.gpu.shader.builder.Variable
import me.anno.gpu.shader.builder.VariableMode
import me.anno.gpu.shader.renderer.Renderer
import me.anno.gpu.texture.Clamping
import me.anno.gpu.texture.Filtering
import me.anno.gpu.texture.Texture3D
import me.anno.gpu.texture.TextureCache.getLUT
import me.anno.image.utils.GaussianBlur
import me.anno.maths.Maths.PIf
import me.anno.remsstudio.RemsStudio.currentCamera
import me.anno.remsstudio.RemsStudio.gfxSettings
import me.anno.remsstudio.RemsStudio.nullCamera
import me.anno.remsstudio.Selection.selectedTransforms
import me.anno.remsstudio.gpu.ShaderLibV2.colorGrading
import me.anno.remsstudio.objects.Camera
import me.anno.remsstudio.objects.Camera.Companion.DEFAULT_VIGNETTE_STRENGTH
import me.anno.remsstudio.objects.Transform
import me.anno.remsstudio.objects.Transform.Companion.drawUICircle
import me.anno.remsstudio.objects.effects.ToneMappers
import me.anno.remsstudio.ui.editor.ISceneView
import me.anno.ui.editor.sceneView.Gizmos.drawGizmo
import me.anno.ui.editor.sceneView.Grid
import me.anno.video.MissingFrameException
import org.joml.Matrix4f
import org.joml.Matrix4fArrayList
import org.joml.Vector4f
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Suppress("MemberVisibilityCanBePrivate")
object Scene {

    var nearZ = 0.001f
    var farZ = 1000f

    // use a framebuffer, where we draw sq(color)
    // then we use a shader to draw sqrt(sq(color))
    // this should give correct color mixing <3
    // (color gamma correction, 2.2 is close to 2.0; shouldn't matter in my opinion)
    // can't remove the heart after this talk: https://www.youtube.com/watch?v=SzoquBerhUc ;)
    // (Oh The Humanity! - Kate Gregory [C++ on Sea 2019])

    lateinit var sqrtToneMappingShader: BaseShader
    lateinit var lutShader: BaseShader
    lateinit var addBloomShader: BaseShader

    private var isInited = false
    private fun init() {

        // add randomness against banding

        sqrtToneMappingShader = BaseShader("sqrt/tone-mapping",
            listOf(
                Variable(GLSLType.V2F, "coords", VariableMode.ATTR),
                Variable(GLSLType.V1F, "ySign"),
            ),
            "" +
                    "void main(){" +
                    "   vec2 coords1 = coords*2.0-1.0;\n" +
                    "   gl_Position = vec4(coords1.x, coords1.y * ySign, 0.0, 1.0);\n" +
                    "   uv = coords;\n" +
                    "}", uvList, listOf(
                Variable(GLSLType.S2D, "tex"),
                Variable(GLSLType.V2F, "chromaticAberration"),
                Variable(GLSLType.V2F, "chromaticOffset"),
                Variable(GLSLType.V2F, "distortion"),
                Variable(GLSLType.V2F, "distortionOffset"),
                Variable(GLSLType.V3F, "fxScale"),
                Variable(GLSLType.V1F, "vignetteStrength"),
                Variable(GLSLType.V3F, "vignetteColor"),
                Variable(GLSLType.V1I, "toneMapper"),
                Variable(GLSLType.V1F, "minValue"),
                Variable(GLSLType.V3F, "finalColor", VariableMode.OUT),
                Variable(GLSLType.V1F, "finalAlpha", VariableMode.OUT)
            ), "" +
                    randomGLSL +
                    reinhardToneMapping +
                    acesToneMapping +
                    uchimuraToneMapping +
                    brightness +
                    colorGrading +
                    "vec2 distort(vec2 uv, vec2 nuv, vec2 duv){" +
                    "   vec2 nuv2 = nuv + duv;\n" +
                    "   float r2 = dot(nuv2,nuv2), r4 = r2*r2;\n" +
                    "   vec2 uv2 = uv + duv + ((nuv2 - distortionOffset) * dot(distortion, vec2(r2, r4)))/fxScale.xy;\n" +
                    "   return uv2;\n" +
                    "}" +
                    "vec4 getColor(vec2 uv){" +
                    "   return texture(tex, uv);\n" +
                    "}\n" +
                    "float softMin(float a, float b, float k){\n" +
                    "   return -(log(exp(k*-a)+exp(k*-b))/k);\n" +
                    "}" +
                    "void main(){" +
                    "   vec2 uv2 = (uv-0.5)*fxScale.z+0.5;\n" +
                    "   vec2 nuv = (uv2-0.5)*fxScale.xy;\n" +
                    "   vec2 duv = chromaticAberration * nuv + chromaticOffset;\n" +
                    "   vec2 uvG = distort(uv2, nuv, vec2(0.0));\n" +
                    "   vec4 col0 = getColor(uvG);\n" +
                    "   if(minValue < 0.0) {\n" +
                    "       finalColor = col0.rgb;\n" +
                    "       finalAlpha = col0.a;\n" +
                    "   } else {\n" +
                    "       vec2 uvR = distort(uv2, nuv, duv), uvB = distort(uv2, nuv, -duv);\n" +
                    "       float r = getColor(uvR).r;\n" +
                    "       vec2 ga = col0.ga;\n" +
                    "       float b = getColor(uvB).b;\n" +
                    "       vec3 raw = vec3(r, ga.x, b);\n" +
                    "       vec3 toneMapped;\n" +
                    "       switch(toneMapper){\n" +
                    ToneMappers.entries.joinToString("") {
                        "case ${it.id}: toneMapped = ${it.glslFuncName}(raw);break;\n"
                    } + "default: toneMapped = vec3(1.0, 0.0, 1.0);\n" +
                    "       }" +
                    "       vec3 colorGraded = colorGrading(toneMapped);\n" +
                    // todo the grid is drawn with ^2 in raw8 mode, the rest is fine...
                    "       vec4 color = vec4(toneMapper == ${ToneMappers.RAW8.id} ? colorGraded : sqrt(colorGraded), ga.y);\n" +
                    "       float rSq = dot(nuv,nuv);\n" + // nuv nuv 😂 (hedgehog sounds for German children)
                    "       color = mix(vec4(vignetteColor, 1.0), color, 1.0/(1.0 + vignetteStrength*rSq));\n" +
                    "       color += random(uv) * minValue;\n" +
                    "       finalColor = color.rgb;\n" +
                    "       finalAlpha = color.a;\n" +
                    "   }" +
                    "}"
        )

        lutShader = createShader(
            "lut", coordsList, coordsUVVertexShader, uvList, listOf(
                Variable(GLSLType.S2D, "tex"),
                Variable(GLSLType.S3D, "lut"),
            ), "" +
                    randomGLSL +
                    "void main(){" +
                    "   vec4 c0 = texture(tex, uv);\n" +
                    "   vec3 color = clamp(c0.rgb, 0.0, 1.0);\n" +
                    "   gl_FragColor = vec4(texture(lut, color.rbg).rgb, c0.a);\n" +
                    "}", listOf("tex", "lut")
        )

        addBloomShader = createShader(
            "addBloom", coordsList, coordsUVVertexShader, uvList, listOf(
                Variable(GLSLType.S2D, "original"),
                Variable(GLSLType.S2D, "blurred"),
                Variable(GLSLType.V1F, "intensity")
            ), "" +
                    "void main(){" +
                    "   gl_FragColor = texture(original, uv) + intensity * texture(blurred, uv);\n" +
                    "   gl_FragColor.a = clamp(gl_FragColor.a, 0.0, 1.0);\n" +
                    "}", listOf("original", "blurred")
        )

        isInited = true
    }

    fun getNextBuffer(
        name: String,
        previous: IFramebuffer,
        offset: Int,
        nearest: Filtering,
        samples: Int?
    ): IFramebuffer {
        val next = FBStack[name, previous.width, previous.height, 4, usesFPBuffers, samples
            ?: previous.samples, DepthBufferType.INTERNAL]
        previous.bindTextures(offset, nearest, Clamping.CLAMP)
        return next
    }

    var lastCameraTransform = Matrix4f()
    var lastGlobalCameraTransform = Matrix4f()
    var lGCTInverted = Matrix4f()
    var usesFPBuffers = false

    val mayUseMSAA
        get() = if (isFinalRendering) DefaultConfig["rendering.useMSAA", true]
        else DefaultConfig["ui.editor.useMSAA", gfxSettings.data["ui.editor.useMSAA", true]]

    // rendering must be done in sync with the rendering thread (OpenGL limitation) anyway, so one object is enough
    val stack = Matrix4fArrayList()
    fun draw(
        camera: Camera, scene: Transform,
        x: Int, y: Int, w: Int, h: Int,
        time: Double,
        flipY: Boolean, renderer: Renderer, sceneView: ISceneView?
    ) {
        currentCamera = camera
        useFrame(x, y, w, h, renderer) {
            usesFPBuffers = sceneView?.usesFPBuffers ?: (camera.toneMapping != ToneMappers.RAW8)
            val isFakeColorRendering = renderer != Renderer.colorRenderer && renderer != Renderer.colorSqRenderer
            drawScene(scene, camera, time, x, y, w, h, flipY, isFakeColorRendering, sceneView)
        }
    }

    fun drawScene(
        scene: Transform, camera: Camera,
        time: Double,
        x0: Int, y0: Int, w: Int, h: Int, flipY: Boolean,
        isFakeColorRendering: Boolean, sceneView: ISceneView?
    ) {

        stack.identity()

        val mayUseMSAA = mayUseMSAA
        val samples = if (mayUseMSAA && !isFakeColorRendering) RemsStudio.targetSamples else 1

        GFX.check()

        if (!isInited) init()

        RemsStudio.currentlyDrawnCamera = camera

        val (cameraTransform, cameraTime) = camera.getGlobalTransformTime(time)
        lastGlobalCameraTransform.set(cameraTransform)
        lGCTInverted.set(cameraTransform).invert()

        val distortion = camera.distortion[cameraTime]
        val vignetteStrength = camera.vignetteStrength[cameraTime]
        val chromaticAberration = camera.chromaticAberration[cameraTime]
        val toneMapping = camera.toneMapping

        val cgOffset = camera.cgOffsetAdd[cameraTime] - camera.cgOffsetSub[cameraTime]
        val cgSlope = camera.cgSlope[cameraTime]
        val cgPower = camera.cgPower[cameraTime]
        val cgSaturation = camera.cgSaturation[cameraTime]

        val bloomIntensity = camera.bloomIntensity[cameraTime]
        val bloomSize = camera.bloomSize[cameraTime]
        val bloomThreshold = camera.bloomThreshold[cameraTime]

        val needsCG = !cgOffset.is000() || !cgSlope.is1111() || !cgPower.is1111() || cgSaturation != 1f
        val needsBloom = bloomIntensity > 0f && bloomSize > 0f

        // optimize to use target directly, if no buffer in-between is required
        // (for low-performance devices)
        var needsTemporaryBuffer = !isFakeColorRendering
        if (needsTemporaryBuffer) {
            val window = GFX.someWindow
            needsTemporaryBuffer = // issues are resolved: clipping was missing maybe...
                flipY ||
                        samples > 1 ||
                        !distortion.is000() ||
                        vignetteStrength > 0f ||
                        chromaticAberration > 0f ||
                        toneMapping != ToneMappers.RAW8 ||
                        needsCG || needsBloom ||
                        w > window.width || h > window.height
        }

        var buffer: IFramebuffer =
            if (needsTemporaryBuffer) FBStack["Scene-Main", w, h, 4, usesFPBuffers, samples,
                if (camera.useDepth) DepthBufferType.INTERNAL else DepthBufferType.NONE]
            else GFXState.currentBuffer

        val x = if (needsTemporaryBuffer) 0 else x0
        val y = if (needsTemporaryBuffer) 0 else y0

        blendMode.use(if (isFakeColorRendering) null else BlendMode.DEFAULT) {
            depthMode.use(if (camera.useDepth) DepthMode.CLOSE else DepthMode.ALWAYS) {

                useFrame(x, y, w, h, buffer) {

                    val color = camera.backgroundColor.getValueAt(RemsStudio.editorTime, Vector4f())
                    buffer.clearDepth()
                    blendMode.use(null) {
                        depthMask.use(false) {
                            depthMode.use(DepthMode.ALWAYS) {
                                drawRect(x, y, w, h, color)
                            }
                        }
                    }

                    // draw the 3D stuff
                    nearZ = camera.nearZ[cameraTime]
                    farZ = camera.farZ[cameraTime]

                    camera.applyTransform(cameraTime, cameraTransform, stack)

                    // val white = Vector4f(1f, 1f, 1f, 1f)
                    val white = Vector4f(camera.color[cameraTime])
                    val whiteMultiplier = camera.colorMultiplier[cameraTime]
                    val whiteAlpha = white.w
                    white.mul(whiteMultiplier)
                    white.w = whiteAlpha

                    // use different settings for white balance etc...
                    // remember the transform for later use
                    lastCameraTransform.set(stack)

                    if (!isFinalRendering && camera != nullCamera) {
                        stack.next { nullCamera?.draw(stack, time, white) }
                    }

                    stack.next { scene.draw(stack, time, white) }

                    if (!isFakeColorRendering && !isFinalRendering && sceneView != null) {
                        depthMask.use(false) {
                            blendMode.use(BlendMode.ADD) {
                                drawGrid(cameraTransform, sceneView)
                            }
                        }
                    }

                    GFX.check()

                    if (!isFinalRendering && !isFakeColorRendering) {
                        drawGizmo(cameraTransform, x, y, w, h)
                        GFX.check()
                    }

                    drawSelectionRing(isFakeColorRendering, camera, time)

                }
            }

        }

        /*val enableCircularDOF = isKeyDown('o') && isKeyDown('f')
        if(enableCircularDOF){
            // todo render dof instead of bokeh blur only
            // make bokeh blur an additional camera effect?
            val srcBuffer = buffer.msBuffer ?: buffer
            srcBuffer.ensure()
            val src = srcBuffer.textures[0]
            buffer = getNextBuffer("Scene-Bokeh", buffer, 0, true, samples = 1)
            Frame(buffer){
                BokehBlur.draw(src, Framebuffer.stack.peek()!!, 0.02f)
            }
        }*/

        val lutFile = camera.lut
        val needsLUT = !isFakeColorRendering && lutFile.exists && !lutFile.isDirectory
        val lut = if (needsLUT) getLUT(lutFile, true, 20_000) else null

        if (lut == null && needsLUT && isFinalRendering) throw MissingFrameException(lutFile.absolutePath)

        if (buffer is Framebuffer && needsTemporaryBuffer) {
            renderPurely {
                if (needsBloom) {
                    buffer = applyBloom(buffer, w, h, bloomSize, bloomIntensity, bloomThreshold)
                }
                if (lut != null) {
                    drawWithLUT(buffer, isFakeColorRendering, camera, cameraTime, w, h, flipY, lut)
                } else {
                    drawWithoutLUT(buffer, isFakeColorRendering, camera, cameraTime, w, h, flipY)
                }
            }
        }
    }

    private fun drawWithLUT(
        buffer: IFramebuffer,
        isFakeColorRendering: Boolean,
        camera: Camera,
        cameraTime: Double,
        w: Int,
        h: Int,
        flipY: Boolean,
        lut: Texture3D
    ) {

        val lutBuffer = getNextBuffer("Scene-LUT", buffer, 0, Filtering.LINEAR, 1)
        useFrame(lutBuffer) {
            drawColors(isFakeColorRendering, camera, cameraTime, w, h, flipY)
        }

        /**
         * apply the LUT for sepia looks, cold looks, general color correction, ...
         * uses the Unreal Engine "format" of a 256x16 image (or 1024x32)
         * */
        val lutShader = lutShader.value
        lutShader.use()
        lut.bind(1, Filtering.LINEAR, Clamping.CLAMP)
        lutBuffer.bindTextures(0, Filtering.TRULY_NEAREST, Clamping.CLAMP)
        SimpleBuffer.flat01.draw(lutShader)
        GFX.check()

    }

    private fun drawWithoutLUT(
        buffer: IFramebuffer,
        isFakeColorRendering: Boolean,
        camera: Camera,
        cameraTime: Double,
        w: Int,
        h: Int,
        flipY: Boolean
    ) {
        buffer.bindTextures(0, Filtering.TRULY_NEAREST, Clamping.CLAMP)
        drawColors(isFakeColorRendering, camera, cameraTime, w, h, flipY)
    }

    private fun applyBloom(
        buffer: IFramebuffer, w: Int, h: Int,
        bloomSize: Float, bloomIntensity: Float, bloomThreshold: Float
    ): IFramebuffer {

        // create blurred version
        GaussianBlur.draw(buffer, bloomSize, w, h, 1, bloomThreshold, false, Matrix4fArrayList())
        val bloomed = getNextBuffer("Scene-Bloom", buffer, 0, Filtering.TRULY_NEAREST, 1)

        // add it on top
        useFrame(bloomed) {
            val shader = addBloomShader.value
            shader.use()
            shader.v1f("intensity", bloomIntensity)
            SimpleBuffer.flat01.draw(shader)
        }

        return bloomed

    }

    private fun drawColors(
        isFakeColorRendering: Boolean,
        camera: Camera, cameraTime: Double,
        w: Int, h: Int, flipY: Boolean
    ) {
        /**
         * Tone Mapping, Distortion, and applying the sqrt operation (reverse pow(, 2.2))
         * */

        // todo render at higher resolution for extreme distortion?
        // msaa should help, too
        // add camera pseudo effects (red-blue-shift)
        // then apply tonemapping
        val shader = sqrtToneMappingShader.value
        shader.use()
        shader.v1f("ySign", if (flipY) -1f else 1f)
        val colorDepth = DefaultConfig["gpu.display.colorDepth", 8]

        val minValue = if (isFakeColorRendering) -1f else 1f / (1 shl colorDepth)
        shader.v1f("minValue", minValue)

        uploadCameraUniforms(shader, isFakeColorRendering, camera, cameraTime, w, h)

        // draw it!
        SimpleBuffer.flat01.draw(shader)
        GFX.check()
    }

    private fun uploadCameraUniforms(
        shader: Shader, isFakeColorRendering: Boolean,
        camera: Camera, cameraTime: Double,
        w: Int, h: Int
    ) {

        val distortion = camera.distortion[cameraTime]
        val distortionOffset = camera.distortionOffset[cameraTime]
        val vignetteStrength = camera.vignetteStrength[cameraTime]
        val chromaticAberration = camera.chromaticAberration[cameraTime]
        val toneMapping = camera.toneMapping

        val cgOffset = camera.cgOffsetAdd[cameraTime] - camera.cgOffsetSub[cameraTime]
        val cgSlope = camera.cgSlope[cameraTime]
        val cgPower = camera.cgPower[cameraTime]
        val cgSaturation = camera.cgSaturation[cameraTime]

        val rel = sqrt(w * h.toFloat())
        val fxScaleX = 1f * w / rel
        val fxScaleY = 1f * h / rel

        // artistic scale
        val chromaticScale = 0.01f
        if (!isFakeColorRendering) {
            val ca = chromaticAberration * chromaticScale
            val cao = camera.chromaticOffset[cameraTime] * chromaticScale
            val angle = (camera.chromaticAngle[cameraTime] * 2 * Math.PI).toFloat()
            shader.v2f("chromaticAberration", cos(angle) * ca / fxScaleX, sin(angle) * ca / fxScaleY)
            shader.v2f("chromaticOffset", cao)
        }

        // avg brightness: exp avg(log (luminance + offset4black)) (Reinhard tone mapping paper)
        // middle gray = 0.18?

        // distortion
        shader.v3f("fxScale", fxScaleX, fxScaleY, 1f + distortion.z)
        shader.v2f("distortion", distortion.x, distortion.y)
        shader.v2f("distortionOffset", distortionOffset)
        if (!isFakeColorRendering) {
            // vignette
            shader.v1f("vignetteStrength", DEFAULT_VIGNETTE_STRENGTH * vignetteStrength)
            shader.v3f("vignetteColor", camera.vignetteColor[cameraTime])
            // randomness against banding
            // tone mapping
            shader.v1i("toneMapper", toneMapping.id)
            // color grading
            shader.v3f("cgOffset", cgOffset)
            shader.v3X("cgSlope", cgSlope)
            shader.v3X("cgPower", cgPower)
            shader.v1f("cgSaturation", cgSaturation)
        }

    }

    private fun drawGrid(cameraTransform: Matrix4f, sceneView: ISceneView) {
        stack.next {
            if (sceneView.isLocked2D) {
                stack.rotateX(PIf / 2)
            }
            Grid.draw(stack, cameraTransform)
        }
    }

    private fun drawSelectionRing(isFakeColorRendering: Boolean, camera: Camera, time: Double) {
        /**
         * draw the selection ring for selected objects
         * draw it after everything else and without depth
         * */
        for (selectedTransform in selectedTransforms) {
            if (!isFinalRendering && !isFakeColorRendering && selectedTransform != camera) { // seeing the own camera is irritating xD
                val stack = stack
                renderDefault {
                    val (transform, _) = selectedTransform.getGlobalTransformTime(time)
                    stack.next {
                        stack.mul(transform)
                        drawSelectionRing(stack)
                    }
                }
            }
        }
    }

    fun drawSelectionRing(stack: Matrix4fArrayList) {
        stack.scale(0.02f)
        drawUICircle(stack, 1f, 0.700f, c0)
        stack.scale(1.2f)
        drawUICircle(stack, 1f, 0.833f, c1)
    }

    val c0 = Vector4f(1f, 0.9f, 0.5f, 1f)
    val c1 = Vector4f(0f, 0f, 0f, 1f)

}