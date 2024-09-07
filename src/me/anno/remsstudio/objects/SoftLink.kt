package me.anno.remsstudio.objects

import me.anno.config.DefaultConfig
import me.anno.engine.inspector.Inspectable
import me.anno.gpu.GFX.isFinalRendering
import me.anno.gpu.GFXState.useFrame
import me.anno.gpu.framebuffer.DepthBufferType
import me.anno.gpu.framebuffer.FBStack
import me.anno.gpu.shader.renderer.Renderer
import me.anno.gpu.texture.Clamping
import me.anno.io.base.BaseWriter
import me.anno.io.files.FileReference
import me.anno.io.files.InvalidRef
import me.anno.language.translation.Dict
import me.anno.language.translation.NameDesc
import me.anno.remsstudio.RemsStudio
import me.anno.remsstudio.Scene
import me.anno.remsstudio.animation.AnimatedProperty
import me.anno.remsstudio.gpu.GFXx3Dv2.draw3DVideo
import me.anno.remsstudio.gpu.TexFiltering
import me.anno.remsstudio.gpu.TexFiltering.Companion.getFiltering
import me.anno.remsstudio.objects.text.Text
import me.anno.remsstudio.ui.StudioFileImporter.addChildFromFile
import me.anno.remsstudio.video.UVProjection
import me.anno.ui.Style
import me.anno.ui.base.groups.PanelListY
import me.anno.ui.editor.SettingCategory
import me.anno.ui.editor.files.FileContentImporter
import me.anno.ui.editor.frames.FrameSizeInput
import me.anno.ui.input.NumberType
import me.anno.utils.files.LocalFile.toGlobalFile
import me.anno.utils.structures.Collections.filterIsInstance2
import me.anno.utils.structures.ValueWithDefault
import me.anno.utils.structures.ValueWithDefault.Companion.writeMaybe
import me.anno.utils.structures.ValueWithDefaultFunc
import org.joml.Matrix4f
import org.joml.Matrix4fArrayList
import org.joml.Vector2f
import org.joml.Vector4f
import kotlin.math.roundToInt

@Suppress("MemberVisibilityCanBePrivate")
class SoftLink(var file: FileReference) : GFXTransform(null) {

    constructor() : this(InvalidRef)

    init {
        timelineSlot.setDefault(0)
    }

    var softChild = Transform()

    /**
     * which camera is chosen from the scene
     * */
    var cameraIndex = 0

    // uv
    val tiling = AnimatedProperty.tiling()
    val uvProjection = ValueWithDefault(UVProjection.Planar)
    val clampMode = ValueWithDefault(Clamping.MIRRORED_REPEAT)

    // filtering
    val filtering = ValueWithDefaultFunc { DefaultConfig.getFiltering("default.link.filtering", TexFiltering.LINEAR) }

    val resolution = AnimatedProperty.vec2(Vector2f(1920f, 1080f))
    val cornerRadius = AnimatedProperty.vec4(Vector4f(0f))

    /**
     * to apply LUTs, effects and such
     * */
    var renderToTexture = false

    init {
        isCollapsedI.setDefault(true)
    }

    private var lastModified: Any? = null
    private var lastCamera: Camera? = null

    override fun onDraw(stack: Matrix4fArrayList, time: Double, color: Vector4f) {
        super.onDraw(stack, time, color)
        if (renderToTexture) {
            // render to texture to keep all post-processing settings
            val resolution = resolution[time]
            val rx = resolution.x.roundToInt()
            val ry = resolution.y.roundToInt()
            if (rx > 0 && ry > 0 && rx * ry < 16e6) {
                val fb = FBStack["SoftLink", rx, ry, 4, false, 1, DepthBufferType.INTERNAL]
                useFrame(fb) {
                    fb.clearColor(0, true)
                    drawSceneWithPostProcessing(time)
                }
                draw3DVideo(
                    this, time, stack, fb.getTexture0(), color, filtering.value, clampMode.value,
                    tiling[time], uvProjection.value, cornerRadius[time]
                )
            }
        } else {
            drawScene(stack, time, color)
        }
    }

    fun updateCache() {
        val lm = file.lastModified to cameraIndex
        if (lm != lastModified) {
            lastModified = lm
            load()
        }
    }

    private val tmpMatrix0 = Matrix4f()
    fun drawScene(stack: Matrix4fArrayList, time: Double, color: Vector4f) {
        updateCache()
        val camera = lastCamera
        if (camera != null) {
            val cameraTransform = camera.getLocalTransform(time, this)
            val inv = tmpMatrix0.set(cameraTransform).invert()
            stack.next {
                stack.mul(inv)
                drawChild(stack, time, color, softChild)
            }
        } else {
            drawChild(stack, time, color, softChild)
        }
    }

    fun drawSceneWithPostProcessing(time: Double) {
        updateCache()
        val camera = lastCamera ?: Camera()
        val size = resolution[time]
        val w = StrictMath.max(size.x.roundToInt(), 4)
        val h = StrictMath.max(size.y.roundToInt(), 4)
        val wasFinalRendering = isFinalRendering
        isFinalRendering = true // todo isn't this crash-prone???
        Scene.draw(
            camera, softChild,
            0, 0, w, h,
            time, true,
            Renderer.colorRenderer, null
        )
        isFinalRendering = wasFinalRendering
    }

    override fun drawChildrenAutomatically(): Boolean = false

    fun load() {
        children.clear()
        if (listOfInheritance.count { it is SoftLink } > maxDepth) {// preventing loops
            softChild = Text("Too many links!")
        } else {
            if (file.exists) {
                if (file.isDirectory) {
                    softChild = Text("Use scene files!")
                } else {
                    addChildFromFile(
                        Transform(),
                        file,
                        FileContentImporter.SoftLinkMode.COPY_CONTENT,
                        false
                    ) { transform ->
                        softChild = transform
                        lastCamera = transform.listOfAll
                            .filterIsInstance2(Camera::class)
                            .getOrNull(cameraIndex - 1)// 1 = first, 0 = none
                    }
                }
            } else {
                softChild = Text("File Not Found!")
            }
        }
    }

    override fun claimResources(pTime0: Double, pTime1: Double, pAlpha0: Float, pAlpha1: Float) {
        super.claimResources(pTime0, pTime1, pAlpha0, pAlpha1)
        softChild.claimResources(pTime0, pTime1, pAlpha0, pAlpha1)
    }

    override fun createInspector(
        inspected: List<Inspectable>, list: PanelListY, style: Style,
        getGroup: (NameDesc) -> SettingCategory
    ) {
        super.createInspector(inspected, list, style, getGroup)
        val t = inspected.filterIsInstance2(Transform::class)
        val c = inspected.filterIsInstance2(SoftLink::class)
        val colorGroup = getGroup(NameDesc("Color", "", "obj.color"))
        colorGroup += vis(
            c,
            "Corner Radius",
            "Makes the corners round",
            "cornerRadius",
            c.map { it.cornerRadius },
            style
        )
        val link = getGroup(NameDesc("Link Data", "", "obj.softLink"))
        link += vi(
            inspected, "File", "Where the data is to be loaded from", "", null, file, style
        ) { it, _ -> for (x in c) x.file = it }
        link += vi(
            inspected, "Camera Index", "Which camera should be chosen, 0 = none, 1 = first, ...", "",
            NumberType.INT_PLUS, cameraIndex, style
        ) { it, _ -> for (x in c) x.cameraIndex = it }
        list += FrameSizeInput(
            NameDesc("Resolution"),
            resolution[lastLocalTime].run { "${x.roundToInt()} x ${y.roundToInt()}" }, style
        )
            .setChangeListener { w, h ->
                RemsStudio.incrementalChange("Change Resolution") {
                    // share vector?
                    for (x in c) x.putValue(x.resolution, Vector2f(w.toFloat(), h.toFloat()), false)
                }
            }
            .setIsSelectedListener { show(t, t.map { (it as? SoftLink)?.resolution }) }
        list += vis(
            c, "Tiling", "(tile count x, tile count y, offset x, offset y)", "softLink.tiling",
            c.map { it.tiling }, style
        )
        list += vi(
            inspected, "UV-Projection",
            "Can be used for 360°-Videos",
            "texture.uvProjection",
            null, uvProjection.value, style
        ) { it, _ -> for (x in c) x.uvProjection.value = it }
        list += vi(
            inspected, "Filtering",
            "Pixelated look?",
            "texture.filtering",
            null, filtering.value, style
        ) { it, _ -> for (x in c) x.filtering.value = it }
        list += vi(
            inspected, "Clamping",
            "For tiled images",
            "texture.clamping", null, clampMode.value, style
        ) { it, _ -> for (x in c) x.clampMode.value = it }
        // not ready yet
        link += vi(
            inspected, "Enable Post-Processing",
            "", "", null, renderToTexture, style
        ) { it, _ -> for (x in c) x.renderToTexture = it }
    }

    override fun save(writer: BaseWriter) {
        synchronized(this) { children.clear() }
        super.save(writer)
        writer.writeObject(this, "resolution", resolution)
        writer.writeFile("file", file)
        writer.writeInt("cameraIndex", cameraIndex)
        writer.writeBoolean("renderToTexture", renderToTexture)
        writer.writeObject(this, "tiling", tiling)
        writer.writeMaybe(this, "filtering", filtering)
        writer.writeMaybe(this, "clamping", clampMode)
        writer.writeMaybe(this, "uvProjection", uvProjection)
        writer.writeObject(this, "cornerRadius", cornerRadius)
    }

    override fun setProperty(name: String, value: Any?) {
        when (name) {
            "resolution" -> resolution.copyFrom(value)
            "tiling" -> tiling.copyFrom(value)
            "renderToTexture" -> renderToTexture = value == true
            "cameraIndex" -> cameraIndex = value as? Int ?: return
            "filtering" -> filtering.value = filtering.value.find(value as? Int ?: return)
            "clamping" -> clampMode.value = Clamping.entries.firstOrNull { it.id == value } ?: return
            "uvProjection" -> uvProjection.value = UVProjection.entries.firstOrNull { it.id == value } ?: return
            "file" -> file = (value as? String)?.toGlobalFile() ?: (value as? FileReference) ?: InvalidRef
            "cornerRadius" -> cornerRadius.copyFrom(value)
            else -> super.setProperty(name, value)
        }
    }

    override val areChildrenImmutable get() = true
    override val defaultDisplayName get() = Dict["Linked Object", "obj.softLink"]
    override val className get() = "SoftLink"

    companion object {
        const val maxDepth = 5
    }
}