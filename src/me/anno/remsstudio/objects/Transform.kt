package me.anno.remsstudio.objects

import me.anno.config.DefaultConfig
import me.anno.engine.EngineBase.Companion.workspace
import me.anno.engine.inspector.Inspectable
import me.anno.gpu.GFX.isFinalRendering
import me.anno.gpu.GFXState
import me.anno.gpu.blending.BlendMode
import me.anno.gpu.drawing.GFXx3D
import me.anno.gpu.shader.renderer.Renderer
import me.anno.io.Saveable
import me.anno.io.base.BaseWriter
import me.anno.io.base.InvalidFormatException
import me.anno.io.files.FileReference
import me.anno.io.files.InvalidRef
import me.anno.io.json.saveable.JsonStringReader
import me.anno.io.json.saveable.JsonStringWriter
import me.anno.language.translation.Dict
import me.anno.language.translation.NameDesc
import me.anno.maths.Maths.clamp
import me.anno.maths.Maths.max
import me.anno.remsstudio.RemsStudio
import me.anno.remsstudio.RemsStudio.editorTime
import me.anno.remsstudio.Scene
import me.anno.remsstudio.Selection.select
import me.anno.remsstudio.animation.AnimatedProperty
import me.anno.remsstudio.objects.modes.TransformVisibility
import me.anno.remsstudio.objects.particles.ParticleSystem
import me.anno.remsstudio.ui.ComponentUIV2
import me.anno.remsstudio.ui.IsAnimatedWrapper
import me.anno.remsstudio.ui.editor.TimelinePanel
import me.anno.remsstudio.ui.editor.TimelinePanel.Companion.global2Kf
import me.anno.ui.Panel
import me.anno.ui.Style
import me.anno.ui.base.components.AxisAlignment
import me.anno.ui.base.groups.PanelListY
import me.anno.ui.base.text.LinkPanel
import me.anno.ui.base.text.UpdatingTextPanel
import me.anno.ui.editor.SettingCategory
import me.anno.ui.editor.stacked.Option
import me.anno.ui.input.NumberType
import me.anno.ui.input.TextInput
import me.anno.ui.input.TextInputML
import me.anno.utils.Color.mulARGB
import me.anno.utils.structures.Hierarchical
import me.anno.utils.structures.ValueWithDefault
import me.anno.utils.structures.ValueWithDefault.Companion.writeMaybe
import me.anno.utils.structures.ValueWithDefaultFunc
import me.anno.utils.types.Casting.castToDouble
import me.anno.utils.types.Casting.castToDouble2
import me.anno.utils.types.Floats.toRadians
import me.anno.utils.types.Strings.isBlank2
import me.anno.video.MissingFrameException
import org.apache.logging.log4j.LogManager
import org.joml.Matrix4f
import org.joml.Matrix4fArrayList
import org.joml.Vector3f
import org.joml.Vector4f
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger

open class Transform() : Saveable(),
    Inspectable, Hierarchical<Transform> {

    // todo generally "play" the animation of a single transform for testing purposes?
    // todo maybe only for video or audio? for audio it would be simple :)
    // useful for audio, video, particle systems, generally animations
    // only available if the rest is stopped? yes.

    final override var parent: Transform? = null

    constructor(parent: Transform?) : this() {
        this.parent = parent
        parent?.children?.add(this)
    }

    override val symbol: String
        get() = DefaultConfig["ui.symbol.folder", "\uD83D\uDCC1"]
    override val description: String
        get() = ""
    override val defaultDisplayName: String
        get() = if (className == "Transform") Dict["Folder", "obj.folder"] else className

    val clickId = nextClickId.incrementAndGet()

    val timelineSlot = ValueWithDefault(-1)

    var visibility = TransformVisibility.VISIBLE
    override var isEnabled = true

    var position = AnimatedProperty.pos()
    var scale = AnimatedProperty.scale()
    var rotationYXZ = AnimatedProperty.rotYXZ()

    var skew = AnimatedProperty.skew()
    var alignWithCamera = AnimatedProperty.float01(0f)
    var color = AnimatedProperty.color(Vector4f(1f))
    var colorMultiplier = AnimatedProperty.floatPlus(1f)

    val fadeIn = AnimatedProperty.floatPlus(0.1f)
    val fadeOut = AnimatedProperty.floatPlus(0.1f)

    open fun getStartTime(): Double = Double.NEGATIVE_INFINITY
    open fun getEndTime(): Double = Double.POSITIVE_INFINITY

    var blendMode = BlendMode.INHERIT

    var timeOffset = ValueWithDefault(0.0)
    var timeDilation = ValueWithDefault(1.0)

    var timeAnimated = AnimatedProperty.double(0.0)

    // update this value before drawing everything
    override var indexInParent = 0
    var drawnChildCount = 0

    var nameI = ValueWithDefaultFunc { defaultDisplayName }
    override var name: String
        get() = nameI.value
        set(value) {
            val v = value.trim()
            if (v.isBlank2()) {
                nameI.reset()
            } else {
                nameI.value = v
            }
        }

    var comment = ""
    var tags = ""

    // todo display warnings, if applicable
    var lastWarning: String? = null

    open fun getDocumentationURL(): String? = null

    open fun isVisible(localTime: Double) = true

    val rightPointingTriangle = "▶"
    val bottomPointingTriangle = "▼"
    val folder = "\uD83D\uDCC1"

    override fun addChild(index: Int, child: Transform) {
        children.add(max(index, 0), child)
        child.parent = this
    }

    override fun addChild(child: Transform) {
        children.add(child)
        child.parent = this
    }

    override fun deleteChild(child: Transform) {
        children.remove(child)
    }

    override val children = ArrayList<Transform>()

    val isCollapsedI = ValueWithDefault(false)
    override var isCollapsed: Boolean
        get() = isCollapsedI.value
        set(value) {
            isCollapsedI.value = value
        }

    var lastLocalColor = Vector4f()
    var lastLocalTime = 0.0

    private val weightI = ValueWithDefault(1f)
    var weight: Float
        get() = weightI.value
        set(value) {
            weightI.value = value
        }

    fun putValue(list: AnimatedProperty<*>?, value: Any, updateHistory: Boolean) {
        list ?: return
        val time = global2Kf(editorTime)
        if (updateHistory) {
            RemsStudio.incrementalChange("Change Keyframe Value") {
                list.addKeyframe(time, value, TimelinePanel.keyframeSnappingDt)
            }
        } else {
            list.addKeyframe(time, value, TimelinePanel.keyframeSnappingDt)
        }
    }

    open fun clearCache() {}

    fun setChildAt(child: Transform, index: Int) {
        if (this in child.listOfAll) throw RuntimeException()
        if (index >= children.size) {
            children.add(child)
        } else children[index] = child
        child.parent = this
    }

    fun show(selves: List<Transform>, anim: List<AnimatedProperty<*>?>?) {
        select(selves, anim)
    }

    private val tmp0 = Vector4f()
    private val tmp1 = Vector4f()
    open fun claimResources(pTime0: Double, pTime1: Double, pAlpha0: Float, pAlpha1: Float) {
        val lTime0 = getLocalTime(pTime0)
        val lAlpha0 = getLocalColor(tmp0.set(0f, 0f, 0f, pAlpha0), lTime0, tmp0).w
        val lTime1 = getLocalTime(pTime1)
        val lAlpha1 = getLocalColor(tmp1.set(0f, 0f, 0f, pAlpha1), lTime0, tmp1).w
        if (lAlpha0 > minAlpha || lAlpha1 > minAlpha) {
            claimLocalResources(lTime0, lTime1)
            children.forEach {
                it.claimResources(lTime0, lTime1, lAlpha0, lAlpha1)
            }
        }
    }

    open fun claimLocalResources(lTime0: Double, lTime1: Double) {
        // here is nothing to claim
        // only for things using video textures
    }

    open fun usesFadingDifferently() = false

    override fun createInspector(
        inspected: List<Inspectable>,
        list: PanelListY,
        style: Style,
        getGroup: (title: String, description: String, dictSubPath: String) -> SettingCategory
    ) {

        val c = inspected.filterIsInstance<Transform>()

        list += TextInput("Name ($className)", "", name, style)
            .addChangeListener { for (x in c) name = it.ifEmpty { "-" } }
            .setIsSelectedListener { show(c, null) }
            .apply { alignmentX = AxisAlignment.FILL }
        list += TextInputML("Comment", comment, style)
            .addChangeListener { for (x in c) comment = it }
            .setIsSelectedListener { show(c, null) }
            .apply { alignmentX = AxisAlignment.FILL }

        val warningPanel = UpdatingTextPanel(500, style) { lastWarning }
        warningPanel.textColor = warningPanel.textColor.mulARGB(0xffff3333.toInt())
        list += warningPanel

        val docs = getDocumentationURL()
        if (docs != null) {
            val docsPanel = LinkPanel(docs, style)
            docsPanel.setTooltip("Learn more")
            list += docsPanel
        }

        // todo dedicated tags-input field
        // todo sort for tags
        // - crosses to remove tags
        // - sort them?
        // - a field to add new ones

        // todo if tags are the same, show same tags,
        // todo else show sth like ---
        list += TextInput("Tags", "", tags, style)
            .addChangeListener { for (x in c) x.tags = it }
            .setIsSelectedListener { show(c, null) }
            .setTooltip("For Search | Not implemented yet")

        // transforms
        val transform = getGroup("Transform", "Translation Scale, Rotation, Skewing", "transform")
        transform += vis(c, "Position", "Location of this object", c.map { it.position }, style)
        transform += vis(c, "Scale", "Makes it bigger/smaller", c.map { it.scale }, style)
        transform += vis(c, "Rotation", "Pitch,Yaw,Roll", c.map { it.rotationYXZ }, style)
        transform += vis(c, "Skew", "Transform it similar to a shear", c.map { it.skew }, style)
        transform += vis(
            c,
            "Alignment with Camera",
            "0 = in 3D, 1 = looking towards the camera; billboards",
            c.map { it.alignWithCamera },
            style
        )

        // color
        val colorGroup = getGroup("Color", "", "color")
        colorGroup += vis(c, "Color", "Tint, applied to this & children", c.map { it.color }, style)
        colorGroup += vis(
            c, "Color Multiplier", "To make things brighter than usually possible", c.map { it.colorMultiplier },
            style
        )

        // kind of color...
        colorGroup += vi(
            inspected, "Blend Mode", "How this' element color is combined with what was rendered before that.",
            null, blendMode, style
        ) { it, _ -> for (x in c) x.blendMode = it }

        // time
        val timeGroup = getGroup("Time", "", "time")
        timeGroup += vis(
            inspected, "Start Time", "Delay the animation", null,
            c.map { it.timeOffset }, style
        )
        timeGroup += vis(
            inspected, "Time Multiplier", "Speed up the animation",
            dilationType, c.map { it.timeDilation }, style
        )
        timeGroup += vis(
            c, "Advanced Time", "Add acceleration/deceleration to your elements", c.map { it.timeAnimated },
            style
        )

        val ufd = usesFadingDifferently()
        if (ufd || getStartTime().isFinite()) {
            timeGroup += vis(c, "Fade In", "Transparency at the start, in seconds", c.map { it.fadeIn }, style)
            timeGroup += vis(c, "Fade Out", "Transparency at the end, in seconds", c.map { it.fadeOut }, style)
        }

        val editorGroup = getGroup("Editor", "", "editor")
        editorGroup += vi(
            inspected, "Timeline Slot", "< 1 means invisible", NumberType.INT_PLUS, timelineSlot.value, style
        ) { it, _ -> for (x in c) x.timelineSlot.value = it }
        // todo warn of invisible elements somehow!...
        editorGroup += vi(
            inspected, "Visibility", "", null, visibility, style
        ) { it, _ -> for (x in c) x.visibility = it }

        if (parent?.acceptsWeight() == true) {
            val psGroup = getGroup("Particle System Child", "", "particles")
            psGroup += vi(
                inspected, "Weight", "For particle systems",
                NumberType.FLOAT_PLUS, weight, style
            ) { it, _ ->
                for (x in c) {
                    x.weight = it
                    (x.parent as? ParticleSystem)?.apply {
                        if (children.size > 1) clearCache()
                    }
                }
            }
        }
    }

    override fun createInspector(
        list: PanelListY,
        style: Style,
        getGroup: (title: String, description: String, dictSubPath: String) -> SettingCategory
    ) {
        createInspector(listOf(this), list, style, getGroup)
    }

    open fun getLocalTime(parentTime: Double): Double {
        var localTime0 = (parentTime - timeOffset.value) * timeDilation.value
        localTime0 += timeAnimated[localTime0]
        return localTime0
    }

    fun getLocalColor(dst: Vector4f = Vector4f()): Vector4f {
        return getLocalColor(
            parent?.getLocalColor(dst),
            lastLocalTime, dst
        )
    }

    fun updateLocalColor(parentColor: Vector4f, localTime: Double) {
        lastLocalColor = getLocalColor(parentColor, localTime, lastLocalColor)
    }

    fun getLocalAlpha(parentAlpha: Float, localTime: Double, tmp: Vector4f): Float {
        var col = color.getValueAt(localTime, tmp).w
        val fadeIn = fadeIn[localTime]
        val fadeOut = fadeOut[localTime]
        val m1 = clamp((localTime - getStartTime()) / fadeIn, 0.0, 1.0)
        val m2 = clamp((getEndTime() - localTime) / fadeOut, 0.0, 1.0)
        val fading = (m1 * m2).toFloat()
        col *= parentAlpha * fading
        return col
    }

    fun getLocalColor(parentColor: Vector4f?, localTime: Double, dst: Vector4f = Vector4f()): Vector4f {
        // we would need a temporary value for the parent color, as may be parentColor == dst
        var px = 1f
        var py = 1f
        var pz = 1f
        var pw = 1f
        if (parentColor != null) {
            px = parentColor.x
            py = parentColor.y
            pz = parentColor.z
            pw = parentColor.w
        }
        val col = color.getValueAt(localTime, dst)
        val mul = colorMultiplier[localTime]
        val fadeIn = fadeIn[localTime]
        val fadeOut = fadeOut[localTime]
        val m1 = clamp((localTime - getStartTime()) / fadeIn, 0.0, 1.0)
        val m2 = clamp((getEndTime() - localTime) / fadeOut, 0.0, 1.0)
        val fading = (m1 * m2).toFloat()
        if (dst !== col) dst.set(col)
        if (parentColor != null) dst.mul(px, py, pz, pw)
        dst.mul(mul, mul, mul, fading)
        return dst
    }

    fun applyTransformLT(transform: Matrix4f, time: Double) {

        val position = position[time]
        val scale = scale[time]
        val euler = rotationYXZ[time]
        val skew = skew[time]
        val alignWithCamera = alignWithCamera[time]

        if (position.x != 0f || position.y != 0f || position.z != 0f) {
            transform.translate(position)
        }

        if (euler.y != 0f) transform.rotateY(euler.y.toRadians())
        if (euler.x != 0f) transform.rotateX(euler.x.toRadians())
        if (euler.z != 0f) transform.rotateZ(euler.z.toRadians())

        if (scale.x != 1f || scale.y != 1f || scale.z != 1f) transform.scale(scale)

        if (skew.x != 0f || skew.y != 0f) transform.skew(skew)

        if (alignWithCamera != 0f) {
            transform.alignWithCamera(alignWithCamera)
        }

    }

    fun Matrix4f.alignWithCamera(alignWithCamera: Float) {
        // lerp rotation instead of full transform?
        if (alignWithCamera != 0f) {
            val local = Scene.lGCTInverted
            val up = local.transformDirection(Vector3f(0f, 1f, 0f))
            val forward = local.transformDirection(Vector3f(0f, 0f, -1f))
            if (alignWithCamera == 1f) {
                lookAlong(forward, up)
            } else {
                lerp(Matrix4f(this).lookAlong(forward, up), alignWithCamera)
            }
        }
    }

    fun applyTransformPT(transform: Matrix4f, parentTime: Double) =
        applyTransformLT(transform, getLocalTime(parentTime))

    /**
     * stack with camera already included
     * */
    fun draw(stack: Matrix4fArrayList, parentTime: Double, parentColor: Vector4f) {
        val time = getLocalTime(parentTime)
        val color = getLocalColor(parentColor, time, tmp0)
        draw(stack, time, parentColor, color)
    }

    fun draw(stack: Matrix4fArrayList, time: Double, parentColor: Vector4f, color: Vector4f) {
        if (color.w > minAlpha && visibility.isVisible) {
            applyTransformLT(stack, time)
            drawDirectly(stack, time, parentColor, color)
        }
    }

    fun drawDirectly(stack: Matrix4fArrayList, time: Double, parentColor: Vector4f, color: Vector4f) {

        val doBlending = when (GFXState.currentRenderer) {
            Renderer.colorRenderer, Renderer.colorSqRenderer -> true
            else -> false
        }

        if (doBlending) {
            GFXState.blendMode.use(if (blendMode == NO_BLENDING) null else blendMode) {
                onDraw(stack, time, color)
                drawChildren(stack, time, color, parentColor)
            }
        } else {
            onDraw(stack, time, color)
            drawChildren(stack, time, color, parentColor)
        }
    }

    fun drawChildren(stack: Matrix4fArrayList, time: Double, color: Vector4f, parentColor: Vector4f) {
        val passesOnColor = passesOnColor()
        val childColor = if (passesOnColor) color else parentColor
        if (drawChildrenAutomatically()) {
            drawChildren(stack, time, childColor)
        }
    }

    open fun drawChildrenAutomatically() = true

    fun drawChildren(stack: Matrix4fArrayList, time: Double, color: Vector4f) {
        val size = children.size
        drawnChildCount = size
        for (i in 0 until size) {
            val child = children[i]
            child.indexInParent = i
            drawChild(stack, time, color, child)
        }
    }

    fun drawChild(stack: Matrix4fArrayList, time: Double, color: Vector4f, child: Transform?) {
        if (child != null) {
            stack.next {
                child.draw(stack, time, color)
            }
        }
    }

    open fun onDraw(stack: Matrix4fArrayList, time: Double, color: Vector4f) {
        drawUICircle(stack, 0.02f, 0.7f, color)
    }

    override fun save(writer: BaseWriter) {
        super.save(writer)
        // many properties are only written if they changed; to reduce file sizes and make things clearer
        // when copy-pasting stuff
        // writer.writeObject(this, "parent", parent) // no longer required, as far I can see...
        writer.writeMaybe(this, "name", nameI)
        writer.writeString("comment", comment)
        writer.writeString("tags", tags)
        writer.writeMaybe(this, "collapsed", isCollapsedI)
        writer.writeMaybe(this, "weight", weightI)
        writer.writeObject(this, "position", position)
        writer.writeObject(this, "scale", scale)
        writer.writeObject(this, "rotationYXZ", rotationYXZ)
        writer.writeObject(this, "skew", skew)
        writer.writeObject(this, "alignWithCamera", alignWithCamera)
        writer.writeMaybe(this, "timeOffset", timeOffset)
        writer.writeMaybe(this, "timeDilation", timeDilation)
        writer.writeObject(this, "timeAnimated", timeAnimated)
        writer.writeObject(this, "color", color)
        writer.writeObject(this, "colorMultiplier", colorMultiplier)
        writer.writeObject(this, "fadeIn", fadeIn)
        writer.writeObject(this, "fadeOut", fadeOut)
        if (blendMode !== BlendMode.INHERIT) writer.writeString("blendMode", blendMode.id)
        writer.writeObjectList(this, "children", children)
        writer.writeMaybe(this, "timelineSlot", timelineSlot)
        writer.writeInt("visibility", visibility.id, false)
    }

    override fun setProperty(name: String, value: Any?) {
        when (name) {
            "collapsed" -> isCollapsed = value == true
            "timelineSlot" -> timelineSlot.value = value as? Int ?: return
            "visibility" -> visibility = TransformVisibility[value as? Int ?: return]
            "uuid" -> Unit// hide the warning
            "weight" -> weight = value as? Float ?: return
            "timeDilation" -> timeDilation.value = value as? Double ?: return
            "timeOffset" -> timeOffset.value = value as? Double ?: return
            "fadeIn" -> {
                if (value is Double && value >= 0.0) fadeIn.set(value.toFloat())
                else fadeIn.copyFrom(value)
            }
            "fadeOut" -> {
                if (value is Double && value >= 0.0) fadeOut.set(value.toFloat())
                else fadeOut.copyFrom(value)
            }
            "name" -> this.name = value as? String ?: ""
            "comment" -> comment = value as? String ?: ""
            "tags" -> tags = value as? String ?: ""
            "blendMode" -> blendMode = BlendMode[value as? String ?: ""]
            "children" -> {
                when (value) {
                    is Array<*> -> value.filterIsInstance<Transform>().forEach(::addChild)
                    is Transform -> addChild(value)
                }
            }
            "parent" -> {
                if (value is Transform) {
                    try {
                        value.addChild(this)
                    } catch (e: RuntimeException) {
                        LOGGER.warn(e.message.toString())
                    }
                }
            }
            "position" -> position.copyFrom(value)
            "scale" -> scale.copyFrom(value)
            "rotationYXZ" -> rotationYXZ.copyFrom(value)
            "skew" -> skew.copyFrom(value)
            "alignWithCamera" -> alignWithCamera.copyFrom(value)
            "timeAnimated" -> timeAnimated.copyFrom(value)
            "color" -> color.copyFrom(value)
            "colorMultiplier" -> colorMultiplier.copyFrom(value)
            else -> super.setProperty(name, value)
        }
    }

    override val className get() = "Transform"
    override val approxSize get() = 1024 + listOfAll.count()

    fun setName(name: String): Transform {
        this.name = name
        return this
    }

    fun getLocalTransform(globalTime: Double, reference: Transform?): Matrix4f {
        val (parentTransform, parentTime) =
            if (reference === parent) Matrix4f() to globalTime
            else parent?.getGlobalTransformTime(globalTime) ?: (Matrix4f() to globalTime)
        val localTime = getLocalTime(parentTime)
        applyTransformLT(parentTransform, localTime)
        return parentTransform
    }

    fun getLocalTime(globalTime: Double, reference: Transform?): Double {
        val parentTime =
            if (reference === parent) globalTime
            else parent?.getGlobalTime(globalTime) ?: globalTime
        return getLocalTime(parentTime)
    }

    fun getLocalTransformTime(globalTime: Double, reference: Transform?): Pair<Matrix4f, Double> {
        val (parentTransform, parentTime) =
            if (reference === parent) Matrix4f() to globalTime
            else parent?.getGlobalTransformTime(globalTime) ?: (Matrix4f() to globalTime)
        val localTime = getLocalTime(parentTime)
        applyTransformLT(parentTransform, localTime)
        return parentTransform to localTime
    }

    fun getGlobalTransform(globalTime: Double): Matrix4f {
        val (parentTransform, parentTime) = parent?.getGlobalTransformTime(globalTime) ?: (Matrix4f() to globalTime)
        val localTime = getLocalTime(parentTime)
        applyTransformLT(parentTransform, localTime)
        return parentTransform
    }

    fun getGlobalTime(globalTime: Double): Double {
        val parentTime = parent?.getGlobalTime(globalTime) ?: globalTime
        return getLocalTime(parentTime)
    }

    fun getGlobalTransformTime(globalTime: Double, dst: Matrix4f = Matrix4f()): Pair<Matrix4f, Double> {
        val (parentTransform, parentTime) = parent?.getGlobalTransformTime(globalTime, dst) ?: (dst to globalTime)
        val localTime = getLocalTime(parentTime)
        applyTransformLT(parentTransform, localTime)
        return parentTransform to localTime
    }

    override fun isDefaultValue() = false

    fun clone() = clone(InvalidRef)
    open fun clone(workspace: FileReference): Transform {
        val asString = try {
            JsonStringWriter.toText(this, workspace)
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
        try {
            return asString.toTransform()!!
        } catch (e: Exception) {
            LOGGER.warn(asString)
            e.printStackTrace()
            throw RuntimeException("Failed to parse '$asString'!")
        }
    }

    open fun acceptsWeight() = false
    open fun passesOnColor() = true

    fun <V> vi(
        inspected: List<Inspectable>,
        title: String, ttt: String, dictPath: String, visibilityKey: String,
        type: NumberType?, value: V,
        style: Style, setValue: (value: V, mask: Int) -> Unit
    ): Panel {
        return vi(
            inspected,
            Dict[title, "obj.$dictPath"],
            Dict[ttt, "obj.$dictPath.desc"],
            visibilityKey, type, value, style, setValue
        )
    }

    fun <V> vi(
        inspected: List<Inspectable>,
        title: String, ttt: String, dictPath: String, visibilityKey: String,
        type: NumberType?, value: ValueWithDefault<V>,
        style: Style
    ) = vi(inspected, Dict[title, "obj.$dictPath"], Dict[ttt, "obj.$dictPath.desc"], visibilityKey, type, value, style)

    fun <V> vis(
        inspected: List<Inspectable>,
        title: String, ttt: String, dictPath: String, type: NumberType?, visibilityKey: String,
        values: List<ValueWithDefault<V>>, style: Style
    ) = vis(
        inspected, Dict[title, "obj.$dictPath"], Dict[ttt, "obj.$dictPath.desc"],
        visibilityKey, type, values, style
    )

    fun <V> vi(
        inspected: List<Inspectable>,
        title: String, ttt: String, visibilityKey: String,
        type: NumberType?, value: ValueWithDefault<V>,
        style: Style
    ): Panel {
        return vi(inspected, title, ttt, visibilityKey, type, value.value, style) { newValue, mask ->
            // todo respect mask
            // todo assign to all???
            value.value = newValue
        }
    }

    fun <V> vi(
        inspected: List<Inspectable>,
        title: String, ttt: String,
        type: NumberType?, value: ValueWithDefault<V>,
        style: Style
    ): Panel {
        return vi(inspected, title, ttt, title, type, value, style)
    }

    fun <V> vis(
        inspected: List<Inspectable>,
        title: String, ttt: String, visibilityKey: String, type: NumberType?,
        values: List<ValueWithDefault<V>>, style: Style
    ): Panel {
        return vi(inspected, title, ttt, visibilityKey, type, values[0].value, style) { newValue, mask ->
            // todo respect mask
            for (x in values) {
                x.value = newValue
            }
        }
    }

    fun <V> vis(
        inspected: List<Inspectable>,
        title: String, ttt: String, type: NumberType?,
        values: List<ValueWithDefault<V>>, style: Style
    ): Panel {
        return vis(inspected, title, ttt, title, type, values, style)
    }

    /**
     * creates a panel with the correct input for the type, and sets the default values:
     * title, tool tip text, type, start value
     * callback is used to adjust the value
     * */
    fun <V> vi(
        inspected: List<Inspectable>,
        title: String, ttt: String, visibilityKey: String,
        type: NumberType?, value: V,
        style: Style, setValue: (value: V, mask: Int) -> Unit
    ): Panel {
        return ComponentUIV2.vi(inspected, this, title, ttt, visibilityKey, type, value, style, setValue)
    }

    fun <V> vi(
        inspected: List<Inspectable>,
        title: String, ttt: String,
        type: NumberType?, value: V,
        style: Style, setValue: (value: V, mask: Int) -> Unit
    ): Panel {
        return ComponentUIV2.vi(inspected, this, title, ttt, title, type, value, style, setValue)
    }

    fun vis(
        selves: List<Transform>,
        title: String,
        ttt: String,
        dictSubPath: String,
        visibilityKey: String,
        values: List<AnimatedProperty<*>>,
        style: Style
    ): Panel {
        return vis(
            selves,
            Dict[title, "obj.$dictSubPath"],
            Dict[ttt, "obj.$dictSubPath.desc"],
            visibilityKey,
            values,
            style
        )
    }

    fun vis(
        selves: List<Transform>,
        title: String, ttt: String,
        values: List<AnimatedProperty<*>>,
        style: Style
    ): Panel {
        return vis(selves, title, ttt, title, values, style)
    }

    /**
     * creates a panel with the correct input for the type, and sets the default values:
     * title, tool tip text, type, start value
     * modifies the AnimatedProperty-Object, so no callback is needed
     * */
    fun vi(
        title: String,
        ttt: String,
        visibilityKey: String,
        values: AnimatedProperty<*>,
        style: Style
    ): IsAnimatedWrapper {
        return ComponentUIV2.vi(this, title, ttt, visibilityKey, values, style)
    }

    fun vi(title: String, ttt: String, values: AnimatedProperty<*>, style: Style): IsAnimatedWrapper {
        return ComponentUIV2.vi(this, title, ttt, title, values, style)
    }

    fun vis(
        selves: List<Transform>,
        title: String,
        ttt: String,
        visibilityKey: String,
        values: List<AnimatedProperty<*>>,
        style: Style
    ): Panel {
        return ComponentUIV2.vis(selves, title, ttt, visibilityKey, values, style)
    }

    override fun onDestroy() {}

    override fun destroy() {
        removeFromParent()
        onDestroy()
    }

    /**
     * return from this to root all parents
     * */
    val listOfInheritance: Sequence<Transform>
        get() = sequence {
            yield(this@Transform)
            val parent = parent
            if (parent != null) {
                yieldAll(parent.listOfInheritance)
            }
        }

    /**
     * return from this to root all parents
     * */
    val listOfInheritanceReversed: Sequence<Transform>
        get() = sequence {
            val parent = parent
            if (parent != null) {
                yieldAll(parent.listOfInheritance)
            }
            yield(this@Transform)
        }

    fun getDepth(): Int {
        var element: Transform? = this
        var ctr = 0
        while (element != null) {
            element = element.parent
            ctr++
        }
        return ctr
    }

    private var tmpHierarchy: Array<Transform?>? = null

    /**
     * get the local time at that globalTime;
     * withAllocation = false may allocate as well ;p, but at least it's thread safe,
     * and it will allocate only, if the hierarchy changes
     * */
    fun getLocalTimeFromRoot(globalTime: Double, withAllocation: Boolean): Double {
        if (withAllocation) {
            val inh = listOfInheritance.toList().reversed()
            var localTime = globalTime
            for (e in inh) {
                localTime = e.getLocalTime(localTime)
            }
            return localTime
        } else {
            val depth = getDepth()
            var hierarchy = tmpHierarchy
            if (hierarchy == null || hierarchy.size < depth) {
                hierarchy = arrayOfNulls(depth)
                this.tmpHierarchy = hierarchy
            }
            var element: Transform? = this
            var ctr = 0
            while (element != null) {
                hierarchy[ctr++] = element
                element = element.parent
            }
            var localTime = globalTime
            for (i in ctr - 1 downTo 0) {
                localTime = hierarchy[i]!!
                    .getLocalTime(localTime)
            }
            return localTime
        }
    }

    fun checkFinalRendering() {
        if (isFinalRendering) throw MissingFrameException(toString())
    }

    open fun getAdditionalChildrenOptions(): List<Option> = emptyList()

    open val areChildrenImmutable: Boolean = false

    open fun getRelativeSize() = Vector3f(1f)

    companion object {

        val NO_BLENDING = BlendMode(NameDesc("No Blending", "", "gpu.blendMode.none"), "None")

        fun drawUICircle(stack: Matrix4fArrayList, scale: Float = 0.02f, inner: Float = 0.7f, color: Vector4f) {
            // draw a small symbol to indicate pivot
            if (!isFinalRendering) {
                stack.pushMatrix()
                stack.scale(scale)
                // doesn't need effects
                GFXx3D.draw3DCircle(stack, inner, 0f, 360f, color)
                stack.popMatrix()
            }
        }

        val nextClickId = AtomicInteger()

        fun String.toTransform() = try {
            JsonStringReader.readFirstOrNull<Transform>(this, workspace, true)
        } catch (e: InvalidFormatException) {
            null
        }

        const val minAlpha = 0.5f / 255f
        private val LOGGER = LogManager.getLogger(Transform::class)

        val dilationType = NumberType(1.0, 1, 1f, true, true, ::castToDouble2, ::castToDouble)

    }
}