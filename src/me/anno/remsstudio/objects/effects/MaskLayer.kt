package me.anno.remsstudio.objects.effects

import me.anno.config.DefaultConfig
import me.anno.engine.inspector.Inspectable
import me.anno.gpu.GFX
import me.anno.gpu.GFX.isFinalRendering
import me.anno.gpu.GFXState.renderDefault
import me.anno.gpu.GFXState.useFrame
import me.anno.gpu.framebuffer.DepthBufferType
import me.anno.gpu.framebuffer.FBStack
import me.anno.gpu.framebuffer.Framebuffer
import me.anno.gpu.framebuffer.IFramebuffer
import me.anno.gpu.shader.effects.BokehBlur
import me.anno.gpu.shader.renderer.Renderer
import me.anno.gpu.texture.Clamping
import me.anno.gpu.texture.Filtering
import me.anno.gpu.texture.Texture2D
import me.anno.image.utils.GaussianBlur
import me.anno.io.base.BaseWriter
import me.anno.remsstudio.Scene
import me.anno.remsstudio.Scene.mayUseMSAA
import me.anno.remsstudio.animation.AnimatedProperty
import me.anno.remsstudio.gpu.GFXx3Dv2.draw3DMasked
import me.anno.remsstudio.objects.GFXTransform
import me.anno.remsstudio.objects.Transform
import me.anno.remsstudio.objects.geometric.Circle
import me.anno.remsstudio.objects.geometric.Polygon
import me.anno.ui.Panel
import me.anno.ui.Style
import me.anno.ui.base.SpyPanel
import me.anno.ui.base.groups.PanelListY
import me.anno.ui.editor.SettingCategory
import org.joml.Matrix4fArrayList
import org.joml.Vector2f
import org.joml.Vector4f
import java.net.URL

open class MaskLayer(parent: Transform? = null) : GFXTransform(parent) {

    override fun getDocumentationURL() = "https://remsstudio.phychi.com/?s=learn/masks"

    val samples get() = if (useExperimentalMSAA && mayUseMSAA) 8 else 1

    // seems better, because it should not influence the alpha values
    override fun getStartTime(): Double = Double.NEGATIVE_INFINITY

    lateinit var mask: IFramebuffer
    lateinit var masked: IFramebuffer

    var useExperimentalMSAA = false

    /**
     * limit to [0,1]?
     * nice effects can be created with values outside of [0,1], so while [0,1] is the valid range,
     * numbers outside [0,1] give artists more control
     * */
    private val useMaskColor = AnimatedProperty.float(0f)
    private val blurThreshold = AnimatedProperty.float(0f)
    private val effectOffset = AnimatedProperty.pos2D()

    // transition mask???... idk... when would you try to blur between stuff, and can't do it on a normal transform object?
    private val transitionProgress = AnimatedProperty.float01(0.5f)
    private val transitionSmoothness = AnimatedProperty.float01exp(0.5f)
    private fun transitionSettings(time: Double) =
        Vector4f(transitionProgress[time], transitionSmoothness[time], 0f, 0f)

    private val greenScreenSimilarity = AnimatedProperty.float01(0.03f)
    private val greenScreenSmoothness = AnimatedProperty.float01(0.01f)
    private val greenScreenSpillValue = AnimatedProperty.float01(0.15f)
    private fun greenScreenSettings(time: Double) =
        Vector4f(greenScreenSimilarity[time], greenScreenSmoothness[time], greenScreenSpillValue[time], 0f)

    // not animated, because it's not meant to be transitioned, but instead to be a little helper
    var isInverted = false
    var isInverted2 = false

    // ignore the bounds of this objects xy-plane?
    var isFullscreen = false

    // for user-debugging
    var showMask = false
    var showMasked = false

    var type = MaskType.MASKING
    val effectSize = AnimatedProperty.float01exp(0.01f)

    override val symbol get() = DefaultConfig["ui.symbol.mask", "\uD83D\uDCA5"]

    override fun onDraw(stack: Matrix4fArrayList, time: Double, color: Vector4f) {

        val showResult = isFinalRendering || (!showMask && !showMasked)
        var needsDefault = false
        if (children.size >= 2 && showResult) {// else invisible

            val size = effectSize[time]

            if (size > 0f) {

                val w = GFX.viewportWidth
                val h = GFX.viewportHeight
                mask = FBStack["mask", w, h, 4, true, samples, DepthBufferType.INTERNAL]
                masked = FBStack["masked", w, h, 4, true, samples, DepthBufferType.INTERNAL]

                renderDefault {

                    // (low priority)
                    // to do calculate the size on screen to limit overhead
                    // to do this additionally requires us to recalculate the transform

                    // BlendMode.DEFAULT.apply()

                    drawMask(stack, time, color)

                    // BlendMode.DEFAULT.apply()

                    drawMasked(stack, time, color)

                }

                drawOnScreen(stack, time, color)

            } else {

                // draw default
                needsDefault = true

            }

        } else super.onDraw(stack, time, color)

        if (showMask && !isFinalRendering) drawChild(stack, time, color, children.getOrNull(0))
        if (needsDefault || (showMasked && !isFinalRendering)) drawChild(stack, time, color, children.getOrNull(1))

    }

    override fun save(writer: BaseWriter) {
        super.save(writer)
        // forced, because the default value might be true instead of false
        writer.writeBoolean("showMask", showMask, true)
        writer.writeBoolean("showMasked", showMasked, true)
        writer.writeBoolean("isFullscreen", isFullscreen, true)
        writer.writeBoolean("isInverted", isInverted, true)
        writer.writeBoolean("useMSAA", useExperimentalMSAA)
        writer.writeObject(this, "useMaskColor", useMaskColor)
        writer.writeObject(this, "blurThreshold", blurThreshold)
        writer.writeObject(this, "effectOffset", effectOffset)
        writer.writeInt("type", type.id)
        writer.writeObject(this, "pixelSize", effectSize)
        writer.writeObject(this, "greenScreenSimilarity", greenScreenSimilarity)
        writer.writeObject(this, "greenScreenSmoothness", greenScreenSmoothness)
        writer.writeObject(this, "greenScreenSpillValue", greenScreenSpillValue)
        writer.writeObject(this, "transitionProgress", transitionProgress)
        writer.writeObject(this, "transitionSmoothness", transitionSmoothness)
    }

    override fun setProperty(name: String, value: Any?) {
        when (name) {
            "showMask" -> showMask = value == true
            "showMasked" -> showMasked = value == true
            "isFullscreen" -> isFullscreen = value == true
            "isInverted" -> isInverted = value == true
            "useMSAA" -> useExperimentalMSAA = value == true
            "useMaskColor" -> useMaskColor.copyFrom(value)
            "blurThreshold" -> blurThreshold.copyFrom(value)
            "pixelSize" -> effectSize.copyFrom(value)
            "effectOffset" -> effectOffset.copyFrom(value)
            "greenScreenSimilarity" -> greenScreenSimilarity.copyFrom(value)
            "greenScreenSmoothness" -> greenScreenSmoothness.copyFrom(value)
            "greenScreenSpillValue" -> greenScreenSpillValue.copyFrom(value)
            "transitionProgress" -> transitionProgress.copyFrom(value)
            "transitionSmoothness" -> transitionSmoothness.copyFrom(value)
            "type" -> type = MaskType.entries.firstOrNull { it.id == value } ?: type
            else -> super.setProperty(name, value)
        }
    }

    override fun drawChildrenAutomatically() = false

    fun drawMask(stack: Matrix4fArrayList, time: Double, color: Vector4f) {

        useFrame(mask, Renderer.colorRenderer) {

            // alpha needs to be 0 for some masks like the green screen!!

            val child = children.getOrNull(0)
            if (child?.className == "Transform" && child.children.isEmpty()) {
                mask.clearColor(0xffffff, true)
            } else {
                mask.clearColor(0, true)
                drawChild(stack, time, color, child)
            }
        }

    }

    fun drawMasked(stack: Matrix4fArrayList, time: Double, color: Vector4f) {
        useFrame(masked, Renderer.colorRenderer) {
            // alpha muss auch hier 0 sein, für den greenscreen
            masked.clearColor(0, true)
            drawChild(stack, time, color, children.getOrNull(1))
        }
    }

    // mask = 0, tex = 1
    fun drawOnScreen(stack: Matrix4fArrayList, time: Double, color: Vector4f) {

        GFX.check()

        val type = type

        val pixelSize = effectSize[time]

        val w = GFX.viewportWidth
        val h = GFX.viewportHeight

        val offset0 = effectOffset[time]
        val offset = Vector2f(offset0)

        val settings = if (type == MaskType.GREEN_SCREEN) greenScreenSettings(time)
        else transitionSettings(time)

        when (type) {
            MaskType.GAUSSIAN_BLUR, MaskType.BLOOM -> {

                // done first blur everything, then mask
                // the artist could notice the fps going down, and act on his own (screenshot, rendering once, ...) ;)

                // val oldDrawMode = GFX.drawMode
                // if (oldDrawMode == ShaderPlus.DrawMode.COLOR_SQUARED) GFX.drawMode = ShaderPlus.DrawMode.COLOR

                val threshold = blurThreshold[time]
                GaussianBlur.draw(masked, pixelSize, w, h, 2, threshold, isFullscreen, stack)

                // GFX.drawMode = oldDrawMode
                masked.bindTexture0(1, Filtering.TRULY_NEAREST, Clamping.CLAMP)
                mask.bindTexture0(0, Filtering.TRULY_NEAREST, Clamping.CLAMP)

                GFX.check()

                draw3DMasked(
                    stack, color,
                    type.id, useMaskColor[time],
                    pixelSize, offset,
                    isInverted, isInverted2,
                    isFullscreen,
                    settings
                )

            }
            MaskType.BOKEH_BLUR -> {

                val temp = FBStack["mask-bokeh", w, h, 4, true, 1, DepthBufferType.NONE]

                val src0 = masked
                val src0Tex = src0.getTexture0() as Texture2D
                src0.bindTexture0(0, src0Tex.filtering, src0Tex.clamping!!)
                val srcBuffer = (src0 as Framebuffer).ssBuffer ?: src0
                BokehBlur.draw(srcBuffer.textures!![0], temp, pixelSize, Scene.usesFPBuffers)

                temp.bindTexture0(2, Filtering.TRULY_NEAREST, Clamping.CLAMP)
                masked.bindTexture0(1, Filtering.TRULY_NEAREST, Clamping.CLAMP)
                mask.bindTexture0(0, Filtering.TRULY_NEAREST, Clamping.CLAMP)

                draw3DMasked(
                    stack, color,
                    type.id, useMaskColor[time],
                    0f, offset,
                    isInverted, isInverted2,
                    isFullscreen,
                    settings
                )

            }
            // awkward brightness bug; not production-ready
            /*MaskType.PIXELATING -> {

                val ih = clamp((2f / pixelSize).toInt(), 4, h)
                val iw = clamp(w * ih / h, 4, w)

                BoxBlur.draw(masked, w, h, iw, ih, 2, stack)

                masked.bindTexture0(1, Filtering.TRULY_NEAREST, Clamping.CLAMP)
                mask.bindTexture0(0, Filtering.TRULY_NEAREST, Clamping.CLAMP)

                GFX.check()

                GFXx3D.draw3DMasked(
                    stack, color,
                    type, useMaskColor[time],
                    pixelSize, isInverted,
                    isFullscreen,
                    greenScreenSettings(time)
                )

            }*/
            else -> {

                masked.bindTextures(1, Filtering.TRULY_NEAREST, Clamping.MIRRORED_REPEAT)
                GFX.check()
                mask.bindTextures(0, Filtering.TRULY_NEAREST, Clamping.CLAMP)
                GFX.check()
                draw3DMasked(
                    stack, color,
                    type.id, useMaskColor[time],
                    pixelSize, offset,
                    isInverted, isInverted2,
                    isFullscreen,
                    settings
                )

            }
        }
    }

    override fun createInspector(
        inspected: List<Inspectable>,
        list: PanelListY,
        style: Style,
        getGroup: (title: String, description: String, dictSubPath: String) -> SettingCategory
    ) {
        super.createInspector(inspected, list, style, getGroup)
        val c = inspected.filterIsInstance<MaskLayer>()
        val mask = getGroup("Mask Settings", "Masks are multipurpose objects", "mask")
        mask += vi(
            inspected, "Type", "Specifies what kind of mask it is", null, type, style
        ) { it, _ -> for (x in c) x.type = it }

        fun typeSpecific(panel: Panel, isVisible: (MaskType) -> Boolean) {
            mask += panel
            mask += SpyPanel(style) {
                val types = c.map { it.type }.toSet()
                panel.isVisible = types.any { isVisible(it) }
            }
        }

        mask += vis(
            c,
            "Size",
            "How large pixelated pixels or blur should be",
            c.map { it.effectSize },
            style
        )
        mask += vi(
            inspected, "Invert Mask", "Changes transparency with opacity",
            null, isInverted, style
        ) { it, _ -> for (x in c) x.isInverted = it }
        mask += vis(
            c, "Use Color / Transparency", "Should the color influence the masked?",
            c.map { it.useMaskColor },
            style
        )
        typeSpecific(vis(c, "Effect Center", "", c.map { it.effectOffset }, style)) {
            when (it) {
                MaskType.RADIAL_BLUR_1, MaskType.RADIAL_BLUR_2 -> true
                else -> false
            }
        }
        typeSpecific(vis(c, "Blur Threshold", "", c.map { it.blurThreshold }, style)) {
            when (it) {
                MaskType.GAUSSIAN_BLUR, MaskType.BLOOM -> true
                else -> false
            }
        }
        mask += vi(
            inspected, "Make Huge", "Scales the mask, without affecting the children",
            null, isFullscreen, style
        ) { it, _ -> for (x in c) x.isFullscreen = it }
        mask += vi(
            inspected, "Use MSAA(!)",
            "MSAA is experimental, may not always work",
            null, useExperimentalMSAA, style
        ) { it, _ -> for (x in c) x.useExperimentalMSAA = it }

        val greenScreen =
            getGroup("Green Screen", "Type needs to be green-screen; cuts out a specific color", "greenScreen")
        greenScreen += vis(c, "Similarity", "", c.map { it.greenScreenSimilarity }, style)
        greenScreen += vis(c, "Smoothness", "", c.map { it.greenScreenSmoothness }, style)
        greenScreen += vis(c, "Spill Value", "", c.map { it.greenScreenSpillValue }, style)
        greenScreen += vi(
            inspected, "Invert Mask 2", "", null, isInverted2, style
        ) { it, _ -> for (x in c) x.isInverted2 = it }

        val transition = getGroup("Transition", "Type needs to be transition", "transition")
        transition += vis(c, "Progress", "", c.map { it.transitionProgress }, style)
        transition += vis(c, "Smoothness", "", c.map { it.transitionSmoothness }, style)
        val editor = getGroup("Editor", "", "editor")
        editor += vi(
            inspected, "Show Mask", "for debugging purposes; shows the stencil",
            null, showMask, style
        ) { it, _ -> for (x in c) x.showMask = it }
        editor += vi(
            inspected, "Show Masked", "for debugging purposes",
            null, showMasked, style
        ) { it, _ -> for (x in c) x.showMasked = it }

        list += SpyPanel(style) {
            greenScreen.isVisible = type == MaskType.GREEN_SCREEN
            transition.isVisible = type == MaskType.TRANSITION
        }

    }

    override val defaultDisplayName get() = "Mask Layer"
    override val className get() = "MaskLayer"

    companion object {
        fun create(mask: List<Transform>?, masked: List<Transform>?): MaskLayer {
            val maskLayer = MaskLayer(null)
            val maskFolder = Transform(maskLayer)
            maskFolder.name = "Mask Folder"
            if (mask == null) {
                Circle(maskFolder).innerRadius.set(0.5f)
            } else {
                for (child in mask) {
                    maskFolder.addChild(child)
                }
            }
            val maskedFolder = Transform(maskLayer)
            maskedFolder.name = "Masked Folder"
            if (masked == null) {
                Polygon(maskedFolder)
            } else {
                for (child in masked) {
                    maskedFolder.addChild(child)
                }
            }
            return maskLayer
        }
    }
}