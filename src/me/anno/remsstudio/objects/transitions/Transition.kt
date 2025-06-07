package me.anno.remsstudio.objects.transitions

import me.anno.animation.Interpolation
import me.anno.config.DefaultConfig
import me.anno.engine.inspector.Inspectable
import me.anno.gpu.Blitting.copyColorAndDepth
import me.anno.gpu.Blitting.copyDepth
import me.anno.gpu.GFXState
import me.anno.gpu.GFXState.renderPurely
import me.anno.gpu.GFXState.useFrame
import me.anno.gpu.buffer.SimpleBuffer.Companion.flat01
import me.anno.gpu.framebuffer.FBStack
import me.anno.gpu.framebuffer.IFramebuffer
import me.anno.gpu.framebuffer.TargetType
import me.anno.gpu.shader.BaseShader
import me.anno.gpu.shader.GLSLType
import me.anno.gpu.shader.ShaderLib.brightness
import me.anno.gpu.shader.ShaderLib.coordsUVVertexShader
import me.anno.gpu.shader.ShaderLib.uvList
import me.anno.gpu.shader.builder.Variable
import me.anno.gpu.shader.builder.VariableMode
import me.anno.gpu.texture.ITexture2D
import me.anno.gpu.texture.TextureLib
import me.anno.io.base.BaseWriter
import me.anno.language.translation.NameDesc
import me.anno.maths.Maths.PIf
import me.anno.maths.Maths.TAU
import me.anno.remsstudio.animation.Keyframe
import me.anno.remsstudio.objects.Camera
import me.anno.remsstudio.objects.GFXTransform
import me.anno.remsstudio.objects.Transform
import me.anno.ui.Panel
import me.anno.ui.Style
import me.anno.ui.base.SpyPanel
import me.anno.ui.base.groups.PanelListY
import me.anno.ui.editor.SettingCategory
import me.anno.utils.structures.Collections.filterIsInstance2
import me.anno.utils.structures.lists.Lists.firstOrNull2
import me.anno.utils.structures.maps.LazyMap
import me.anno.utils.types.AnyToBool
import me.anno.utils.types.AnyToFloat
import org.joml.Matrix4fArrayList
import org.joml.Vector2f
import org.joml.Vector4f
import kotlin.math.*

class Transition(parent: Transform? = null) : GFXTransform(parent) {

    var type: TransitionType = TransitionType.CROSS_FADE
    var style: Interpolation = Interpolation.CIRCLE_SYM

    // todo make these animated
    var direction = 0f
    var fadeColor = Vector4f()
    var center = Vector2f()

    var fadeFirstTex = false
    var fadeBlackToWhite = true

    override fun getStartTime(): Double = 0.0
    override fun getEndTime(): Double = 1.0

    override val className: String get() = "Transition"

    init {
        timelineSlot.default = 1
    }

    fun render(tex0: ITexture2D, tex1: ITexture2D, progress: Float) {

        // finally blend them, and render their result
        val shader = transitionShaders[type].value
        shader.use()

        shader.v1f("progress", style.getIn(progress))
        shader.v4f("fadeColor", fadeColor)

        // direction also depends on aspect-ratio (like 45°)
        val direction = -direction * PIf * 0.5f
        val tmp = Vector2f(cos(direction), sin(direction))
            .mul(tex0.width.toFloat(), tex0.height.toFloat())
            .normalize()
        shader.v2f("direction", tmp)
        shader.v2f("aspect", tex0.width.toFloat() / tex0.height.toFloat(), 1f)
        shader.v2f("center", center)
        shader.v1b("fadeFirstTex", fadeFirstTex)
        shader.v1b("fadeBlackToWhite", fadeBlackToWhite)
        tex0.bindTrulyLinear(shader, "tex0")
        tex1.bindTrulyLinear(shader, "tex1")

        flat01.draw(shader)
    }

    override fun createInspector(
        inspected: List<Inspectable>, list: PanelListY, style: Style,
        getGroup: (NameDesc) -> SettingCategory
    ) {

        super.createInspector(inspected, list, style, getGroup)
        val c = inspected.filterIsInstance2(Transition::class)

        val transform = getGroup(NameDesc("Transition", "", "obj.transition"))
        transform += vi(
            inspected, "Type", "What kind of transition this is", "transition.type",
            null, type, style
        ) { it, _ -> for (x in c) x.type = it }

        transform += vi(
            inspected, "Style", "How quickly this animates", "transition.style",
            null, this.style, style
        ) { it, _ -> for (x in c) x.style = it }

        transform += vi(
            inspected, "Direction", "Rotation, if supported", "transition.direction",
            null, direction, style
        ) { it, _ -> for (x in c) x.direction = it }

        transform += vi(
            inspected, "Fade Color", "Fading color, if supported", "transition.fadeColor",
            null, fadeColor, style
        ) { it, _ -> for (x in c) x.fadeColor.set(it) }

        transform += vi(
            inspected,
            "Center", "Point around which transition is rotated or zoomed, if supported.\n" +
                    "Top-left: (0,1), top-right: (1,1), bottom-left: (0,0), bottom-right: (1,0), center: (0.5,0.5)",
            "transition.center",
            null, center, style
        ) { it, _ -> for (x in c) x.center.set(it) }

        val lumaFade = ArrayList<Panel>()
        lumaFade += vi(
            inspected,
            "LumaFade: First Texture", "Use first texture for luma-value.",
            "transition.fadeFirstTex",
            null, fadeFirstTex, style
        ) { it, _ -> for (x in c) x.fadeFirstTex = it }
        lumaFade += vi(
            inspected,
            "LumaFade: Black -> White", "Start blending black, blend white last.",
            "transition.fadeBlackToWhite",
            null, fadeBlackToWhite, style
        ) { it, _ -> for (x in c) x.fadeBlackToWhite = it }

        for (panel in lumaFade) transform += panel

        list += SpyPanel(style) {
            val visible = type == TransitionType.LUMA_FADE
            for (panel in lumaFade) panel.isVisible = visible
        }

    }

    override val symbol: String
        get() = DefaultConfig["ui.symbol.transition", "👉"]
    override val description: String
        get() = "Place this at the transitions from one clip to another"

    override fun save(writer: BaseWriter) {
        super.save(writer)
        writer.writeInt("type", type.id)
        writer.writeEnum("style", style)
        writer.writeFloat("direction", direction)
        writer.writeVector4f("fadeColor", fadeColor)
        writer.writeVector2f("center", center)
        writer.writeBoolean("fadeFirstTex", fadeFirstTex)
        writer.writeBoolean("fadeBlackToWhite", fadeBlackToWhite)
    }

    override fun setProperty(name: String, value: Any?) {
        when (name) {
            "type" -> type = TransitionType.entries.firstOrNull2 { it.id == value } ?: return
            "style" -> style = Interpolation.getType(value as? Int ?: return)
            "direction" -> direction = AnyToFloat.getFloat(value)
            "fadeColor" -> fadeColor.set(value as? Vector4f ?: return)
            "center" -> center.set(value as? Vector2f ?: return)
            "fadeFirstTex" -> fadeFirstTex = AnyToBool.anyToBool(value)
            "fadeBlackToWhite" -> fadeBlackToWhite = AnyToBool.anyToBool(value)
            else -> super.setProperty(name, value)
        }
    }

    companion object {

        private val transitionShaders = LazyMap { key: TransitionType ->
            BaseShader(
                key.nameDesc.englishName, emptyList(), coordsUVVertexShader, uvList,
                listOf(

                    Variable(GLSLType.V1F, "progress"),
                    Variable(GLSLType.V2F, "direction"),
                    Variable(GLSLType.V4F, "fadeColor"),
                    Variable(GLSLType.V2F, "center"),
                    Variable(GLSLType.V2F, "aspect"),
                    Variable(GLSLType.V1B, "fadeFirstTex"),
                    Variable(GLSLType.V1B, "fadeBlackToWhite"),

                    Variable(GLSLType.S2D, "tex0"),
                    Variable(GLSLType.S2D, "tex1"),

                    Variable(GLSLType.V3F, "finalColor", VariableMode.OUT),
                    Variable(GLSLType.V1F, "finalAlpha", VariableMode.OUT),
                ), "" +
                        "#define PI $PI\n" +
                        "#define TAU $TAU\n" +
                        "vec2 rotate(vec2 uv, vec2 cosSin){\n" +
                        "   return mat2(cosSin.x,cosSin.y,-cosSin.y,cosSin.x) * uv;" +
                        "}\n" +
                        "vec2 rotateInv(vec2 uv, vec2 cosSin){\n" +
                        "   return rotate(uv,vec2(cosSin.x,-cosSin.y));\n" +
                        "}\n" +
                        "bool isInside(vec2 uv){\n" +
                        "   return uv.x >= 0.0 && uv.x <= 1.0 && uv.y >= 0.0 && uv.y <= 1.0;\n" +
                        "}\n" +
                        "float smoothMix(float u, float bias){\n" +
                        "   vec2 du = vec2(dFdx(u), dFdy(u));\n" +
                        "   u = u / max(length(du),1e-6);\n" +
                        "   return clamp(u + bias, 0.0, 1.0);\n" +
                        "}\n" +
                        "float isInsideF(float u){\n" +
                        "   return smoothMix(0.5-abs(u-0.5), 0.5);\n" +
                        "}\n" +
                        "float isInsideF(vec2 uv){\n" +
                        "   return min(isInsideF(uv.x), isInsideF(uv.y));\n" +
                        "}\n" +
                        "vec4 mixColor(vec4 color0, vec4 color1, float f){\n" +
                        "   if(f <= 0.0) return color0;\n" +
                        "   if(f >= 1.0) return color1;\n" +
                        // sRGB-correct blending
                        "   color0.rgb *= color0.rgb;\n" +
                        "   color1.rgb *= color1.rgb;\n" +
                        "   vec4 result = mix(color0,color1,f);\n" +
                        "   result.rgb = sqrt(max(result.rgb,vec3(0.0)));\n" +
                        "   return result;\n" +
                        "}\n" +
                        "vec4 mixColor2(vec4 color0, vec4 color1, float f){\n" +
                        "   return mixColor(color0,color1,smoothMix(f,0.5));\n" +
                        "}\n" +
                        "vec4 getInRect(sampler2D s, vec2 uv) {\n" +
                        "   return mixColor(fadeColor, texture(s,uv), isInsideF(uv));\n" +
                        "}\n" +
                        brightness +
                        key.shaderString.first +
                        "void main(){" +
                        "   vec4 color = vec4(0.0,0.0,0.0,1.0);\n" +
                        key.shaderString.second + "\n" +
                        "   finalColor = color.rgb;\n" +
                        "   finalAlpha = color.a;\n" +
                        "}\n"
            )
        }

        fun getTime(keyframe: Keyframe<Vector4f>?, defaultTime: Double): Double {
            return if (keyframe != null && keyframe.value.w < 1f / 255f) keyframe.time
            else defaultTime
        }

        fun <V : Transform> getActiveRange(child: V?): TimeRange<V>? {
            if (child == null) return null

            var minTime = child.getStartTime()
            var maxTime = child.getEndTime()
            val keyframes = child.color.keyframes

            minTime = max(minTime, getTime(keyframes.firstOrNull(), minTime))
            maxTime = min(maxTime, getTime(keyframes.lastOrNull(), maxTime))

            minTime = child.toGlobalTime(minTime)
            maxTime = child.toGlobalTime(maxTime)

            minTime = max(minTime, -1e38)
            maxTime = min(maxTime, 1e38)

            return if (minTime.isFinite() && minTime < maxTime) {
                TimeRange(child, minTime, maxTime)
            } else null
        }

        /**
         * extend range to prevent small overlaps from appearing for an unwanted frame
         * */
        val extraLength = 0.25

        fun renderTransitions(parent: Transform, stack: Matrix4fArrayList, time: Double, color: Vector4f) {

            val children = parent.children
            parent.drawnChildCount = children.size

            val transition = children.mapNotNull { child ->
                if (child is Transition && child.visibility.isVisible) {
                    val range = getActiveRange(child)
                    if (range != null && time in range.min - extraLength..range.max + extraLength) range else null
                } else null
            }.minByOrNull { abs(time - it.center) }

            if (transition == null) {
                return parent.drawChildren2(stack, time, color)
            }

            val renderables = parent.children
                .filter { child -> child !is Transition && child !is Camera && child.visibility.isVisible }
                .mapNotNull { child -> getActiveRange(child) }
                .filter { range -> transition.overlaps(range) }

            if (renderables.isEmpty()) {
                return
            }

            val transitionTime = transition.center
            val before = renderables.filter { it.center < transitionTime }
            val after = renderables.filter { it.center >= transitionTime }

            val tex0 = render(parent, before, stack, time, color)
            val tex1 = render(parent, after, stack, time, color)

            val progress = transition.getProgress(time)
            val texI = if (progress > 0.5f) tex1 else tex0
            // render depth of what was drawn in the mean-time
            copyDepth(texI.depthTexture!!, texI.depthMask)
            renderPurely {
                GFXState.depthMask.use(false) {
                    transition.child.render(tex0.getTexture0(), tex1.getTexture0(), progress)
                }
            }
        }

        fun render(
            parent: Transform,
            children: List<TimeRange<*>>,
            stack: Matrix4fArrayList,
            time: Double,
            color: Vector4f
        ): IFramebuffer {
            val base = GFXState.currentBuffer
            val target = FBStack["transition", base.width, base.height, // todo is the target-type ok?
                TargetType.Float16x4, base.samples, base.depthBufferType]
            useFrame(target) {
                renderPurely { // restore what was drawn previously
                    val depth = base.depthTexture ?: TextureLib.depthTexture
                    copyColorAndDepth(base.getTexture0(), depth, base.depthMask, true)
                }
                for (i in children.indices) {
                    val child = children[i].child
                    child.indexInParent = parent.children.indexOf(child)
                    parent.drawChild(stack, time, color, child)
                }
            }
            return target
        }
    }
}