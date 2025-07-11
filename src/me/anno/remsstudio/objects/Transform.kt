package me.anno.remsstudio.objects

import me.anno.cache.ICacheData
import me.anno.config.DefaultConfig
import me.anno.engine.EngineBase.Companion.workspace
import me.anno.engine.inspector.Inspectable
import me.anno.gpu.FinalRendering.isFinalRendering
import me.anno.gpu.GFXState
import me.anno.gpu.blending.BlendMode
import me.anno.gpu.drawing.GFXx3D
import me.anno.gpu.shader.renderer.Renderer
import me.anno.io.base.BaseWriter
import me.anno.io.base.InvalidFormatException
import me.anno.io.files.FileReference
import me.anno.io.files.InvalidRef
import me.anno.io.json.saveable.JsonStringReader
import me.anno.io.json.saveable.JsonStringWriter
import me.anno.io.saveable.Saveable
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
import me.anno.remsstudio.objects.transitions.Transition
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
import me.anno.utils.pooling.JomlPools
import me.anno.utils.structures.Collections.filterIsInstance2
import me.anno.utils.structures.Hierarchical
import me.anno.utils.structures.ValueWithDefault
import me.anno.utils.structures.ValueWithDefault.Companion.writeMaybe
import me.anno.utils.structures.ValueWithDefaultFunc
import me.anno.utils.structures.lists.Lists.any2
import me.anno.utils.types.AnyToBool
import me.anno.utils.types.AnyToDouble
import me.anno.utils.types.AnyToFloat
import me.anno.utils.types.AnyToInt
import me.anno.utils.types.Booleans.hasFlag
import me.anno.utils.types.Casting.castToDouble
import me.anno.utils.types.Casting.castToDouble2
import me.anno.utils.types.Floats.toRadians
import me.anno.utils.types.Strings.isBlank2
import org.apache.logging.log4j.LogManager
import org.joml.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min

@Suppress("MemberVisibilityCanBePrivate")
open class Transform() : Saveable(),
    Inspectable, Hierarchical<Transform>, ICacheData {

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
    var lockTransform = false
    override var isEnabled = true

    var position = AnimatedProperty.pos()
    var scale = AnimatedProperty.scale()
    var rotationYXZ = AnimatedProperty.rotYXZ()

    var skew = AnimatedProperty.skew()
    var alignWithCamera = AnimatedProperty.float01(0f)
    var color = AnimatedProperty.color(Vector4f(1f))
    var colorMultiplier = AnimatedProperty.floatPlus(1f)

    val fadeIn = ValueWithDefault(0.1f)
    val fadeOut = ValueWithDefault(0.1f)

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
        select(selves, anim ?: emptyList())
    }

    private val tmp0 = Vector4f()
    open fun claimResources(pTime0: Double, pTime1: Double, pMaxAlpha: Float) {
        // todo we should get min and max time, not just the border values
        val lTime0 = getLocalTime(pTime0)
        val lTime1 = getLocalTime(pTime1)
        val lMaxAlpha = getMaxAlpha(lTime0, lTime1, pMaxAlpha)
        claimLocalResources(lTime0, lTime1, lMaxAlpha)
        val children = children
        for (i in children.indices) {
            val child = children[i]
            child.claimResources(lTime0, lTime1, lMaxAlpha)
        }
    }

    private fun getMaxAlpha(lTime0: Double, lTime1: Double, parentAlpha: Float): Float {
        // todo we should get min and max alpha, not just the border values
        var alpha = getLocalAlpha(parentAlpha, lTime0)
        alpha = max(alpha, getLocalAlpha(parentAlpha, lTime1))
        return alpha
    }

    open fun claimLocalResources(lTime0: Double, lTime1: Double, lMaxAlpha: Float) {
        // here is nothing to claim
        // only for things using video textures
    }

    open fun usesFadingDifferently() = false

    override fun createInspector(
        inspected: List<Inspectable>, list: PanelListY, style: Style,
        getGroup: (nameDesc: NameDesc) -> SettingCategory
    ) {

        val c = inspected.filterIsInstance2(Transform::class)

        list += TextInput(NameDesc("Name ($className)"), "", name, style)
            .addChangeListener { for (x in c) name = it.ifEmpty { "-" } }
            .setIsSelectedListener { show(c, null) }
            .apply { alignmentX = AxisAlignment.FILL }
        list += TextInputML(NameDesc("Comment"), comment, style)
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
        list += TextInput(NameDesc("Tags"), "", tags, style)
            .addChangeListener { for (x in c) x.tags = it }
            .setIsSelectedListener { show(c, null) }
            .setTooltip("For Search | Not implemented yet")

        // transforms
        val transform = getGroup(NameDesc("Transform", "Translation, Scale, Rotation, Skewing", "obj.transform"))
        transform += vis(
            c,
            "Position",
            "Location of this object",
            "transform.position",
            c.map { it.position },
            style
        )
        transform += vis(c, "Scale", "Makes it bigger/smaller", "transform.scale", c.map { it.scale }, style)
        transform += vis(c, "Rotation", "Pitch,Yaw,Roll", "transform.rotation", c.map { it.rotationYXZ }, style)
        transform += vis(c, "Skew", "Transform it similar to a shear", "transform.skew", c.map { it.skew }, style)
        transform += vis(
            c,
            "Alignment with Camera",
            "0 = in 3D, 1 = looking towards the camera; billboards",
            "transform.alignWithCamera",
            c.map { it.alignWithCamera }, style
        )
        transform += vi(
            c, "Lock Transform", "So you don't accidentally move them", "transform.lockTransform",
            null, lockTransform, style
        ) { value, _ ->
            for (ci in c) ci.lockTransform = value
        }

        // color
        val colorGroup = getGroup(NameDesc("Color", "", "obj.color"))
        colorGroup += vis(c, "Color", "Tint, applied to this & children", "color.tint", c.map { it.color }, style)
        colorGroup += vis(
            c, "Brightness Multiplier", "To make things brighter than usually possible", "color.multiplier",
            c.map { it.colorMultiplier }, style
        )

        // kind of color...
        colorGroup += vi(
            inspected, "Blend Mode", "How this' element color is combined with what was rendered before that.",
            "color.blendMode",
            null, blendMode, style
        ) { it, _ -> for (x in c) x.blendMode = it }

        // time
        val timeGroup = getGroup(NameDesc("Time", "", "obj.time"))
        timeGroup += vis(
            inspected, "Start Time", "Delay the animation", "time.startTime", null,
            c.map { it.timeOffset }, style
        )
        timeGroup += vis(
            inspected, "Time Multiplier", "Speed up the animation", "time.timeMultiplier",
            dilationType, c.map { it.timeDilation }, style
        )
        timeGroup += vis(
            c, "Advanced Time", "Add acceleration/deceleration to your elements", "time.advanced",
            c.map { it.timeAnimated }, style
        )

        val ufd = usesFadingDifferently()
        if (ufd || getStartTime().isFinite()) {
            timeGroup += vis(
                c, "Fade In",
                "Transparency at the start, in seconds",
                "time.fadeIn",
                NumberType.FLOAT_PLUS,
                c.map { it.fadeIn },
                style
            )
            timeGroup += vis(
                c, "Fade Out",
                "Transparency at the end, in seconds",
                "time.fadeOut",
                NumberType.FLOAT_PLUS,
                c.map { it.fadeOut },
                style
            )
        }

        val editorGroup = getGroup(NameDesc("Editor", "", "obj.editor"))
        editorGroup += vi(
            inspected, "Timeline Slot", "< 1 means invisible", "editor.timelineSlot",
            NumberType.INT_PLUS, timelineSlot.value, style
        ) { it, _ -> for (x in c) x.timelineSlot.value = it }
        // todo warn of invisible elements somehow!...
        editorGroup += vi(
            inspected, "Visibility", "Whether the object is visible when rendering and/or when editing",
            "visibility", null, visibility, style
        ) { it, _ -> for (x in c) x.visibility = it }

        if (parent?.acceptsWeight() == true) {
            val psGroup = getGroup(NameDesc("Particle System Child", "", "obj.particles"))
            psGroup += vi(
                inspected, "Weight", "For particle systems", "particles.weight",
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

    open fun getLocalTime(parentTime: Double): Double {
        var localTime0 = (parentTime - timeOffset.value) * timeDilation.value
        localTime0 += timeAnimated[localTime0]
        return localTime0
    }

    fun toGlobalTime(localTime: Double): Double {
        // todo try to support animated time
        return localTime / timeDilation.value + timeOffset.value
    }

    fun getLocalColor(dst: Vector4f): Vector4f {
        val parentColor = JomlPools.vec4f.create().set(1f)
        parent?.getLocalColor(parentColor)
        getLocalColor(parentColor, lastLocalTime, dst)
        JomlPools.vec4f.sub(1)
        return dst
    }

    fun updateLocalColor(parentColor: Vector4f, localTime: Double) {
        lastLocalColor = getLocalColor(parentColor, localTime, lastLocalColor)
    }

    fun getLocalColor(parentColor: Vector4f?, localTime: Double, dst: Vector4f): Vector4f {
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
        val fadeIn = fadeIn.value
        val fadeOut = fadeOut.value
        val m1 = clamp((localTime - getStartTime()) / fadeIn, 0.0, 1.0)
        val m2 = clamp((getEndTime() - localTime) / fadeOut, 0.0, 1.0)
        val fading = (m1 * m2).toFloat()
        if (dst !== col) dst.set(col)
        if (parentColor != null) dst.mul(px, py, pz, pw)
        dst.mul(mul, mul, mul, fading)
        return dst
    }

    fun getLocalAlpha(parentAlpha: Float, localTime: Double): Float {
        val pw = parentAlpha
        val tmp = JomlPools.vec4f.create()
        val col = color.getValueAt(localTime, tmp).w
        JomlPools.vec4f.sub(1)
        val fadeIn = fadeIn.value
        val fadeOut = fadeOut.value
        val m1 = clamp((localTime - getStartTime()) / fadeIn, 0.0, 1.0)
        val m2 = clamp((getEndTime() - localTime) / fadeOut, 0.0, 1.0)
        val fading = (m1 * m2).toFloat()
        return fading * pw * col
    }

    fun applyTransform(transform: Matrix4f, time: Double) {

        val position = position[time, JomlPools.vec3f.create()]
        val scale = scale[time, JomlPools.vec3f.create()]
        val euler = rotationYXZ[time, JomlPools.vec3f.create()]
        val skew = skew[time, JomlPools.vec2f.create()]
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

        JomlPools.vec3f.sub(3)
        JomlPools.vec2f.sub(1)
    }

    fun Matrix4f.alignWithCamera(alignWithCamera: Float) {
        // lerp rotation instead of full transform?
        if (alignWithCamera != 0f) {
            val local = Scene.lastGlobalCameraTransformInverted
            val up = local.transformDirection(Vector3f(0f, 1f, 0f))
            val forward = local.transformDirection(Vector3f(0f, 0f, -1f))
            if (alignWithCamera == 1f) {
                lookAlong(forward, up)
            } else {
                lerp(Matrix4f(this).lookAlong(forward, up), alignWithCamera)
            }
        }
    }

    /**
     * stack with camera already included
     * */
    fun draw(stack: Matrix4fArrayList, parentTime: Double, parentColor: Vector4f) {

        val time = getLocalTime(parentTime)
        val color = getLocalColor(parentColor, time, tmp0)
        if (color.w <= minAlpha || !visibility.isVisible) return

        applyTransform(stack, time)
        drawWithParentTransformAndColor(stack, time, parentColor, color)
    }

    fun drawWithParentTransformAndColor(
        stack: Matrix4fArrayList, time: Double,
        parentColor: Vector4f, color: Vector4f
    ) {

        val allowBlending = when (GFXState.currentRenderer) {
            Renderer.colorRenderer, Renderer.colorSqRenderer -> true
            else -> false
        }

        val blendMode = if (!allowBlending || blendMode == NO_BLENDING) null else blendMode
        if (GFXState.blendMode != blendMode) {
            GFXState.blendMode.use(blendMode) {
                drawWithParentTransformAndColor2(stack, time, parentColor, color)
            }
        } else {
            drawWithParentTransformAndColor2(stack, time, parentColor, color)
        }
    }

    fun drawWithParentTransformAndColor2(
        stack: Matrix4fArrayList, time: Double,
        parentColor: Vector4f, color: Vector4f
    ) {
        onDraw(stack, time, color)
        drawChildren(stack, time, color, parentColor)
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
        val children = children
        if (children.any2 { it is Transition }) Transition.renderTransitions(this, stack, time, color)
        else drawChildren2(stack, time, color)
    }

    fun drawChildren2(stack: Matrix4fArrayList, time: Double, color: Vector4f) {
        val children = children
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
        writer.writeFloat("fadeIn", fadeIn.value)
        writer.writeFloat("fadeOut", fadeOut.value)
        if (blendMode !== BlendMode.INHERIT) writer.writeString("blendMode", blendMode.id)
        writer.writeObjectList(this, "children", children)
        writer.writeMaybe(this, "timelineSlot", timelineSlot)
        writer.writeInt("visibility", visibility.id, false)
        writer.writeBoolean("lockTransform", lockTransform)
    }

    override fun setProperty(name: String, value: Any?) {
        when (name) {
            "collapsed" -> isCollapsed = AnyToBool.anyToBool(value)
            "timelineSlot" -> timelineSlot.value = AnyToInt.getInt(value, 0)
            "visibility" -> visibility = TransformVisibility[value as? Int ?: return]
            "uuid" -> Unit// hide the warning
            "weight" -> weight = AnyToFloat.getFloat(value, 0f)
            "timeDilation" -> timeDilation.value = AnyToDouble.getDouble(value, 1.0)
            "timeOffset" -> timeOffset.value = AnyToDouble.getDouble(value, 0.0)
            "lockTransform" -> lockTransform = AnyToBool.anyToBool(value)
            "fadeIn" -> {
                var value = value
                if (value is AnimatedProperty<*>) value = value[0.0]
                fadeIn.value = AnyToFloat.getFloat(value, fadeIn.default)
            }
            "fadeOut" -> {
                var value = value
                if (value is AnimatedProperty<*>) value = value[0.0]
                fadeOut.value = AnyToFloat.getFloat(value, fadeOut.default)
            }
            "name" -> this.name = value as? String ?: ""
            "comment" -> comment = value as? String ?: ""
            "tags" -> tags = value as? String ?: ""
            "blendMode" -> blendMode = BlendMode[value as? String ?: ""]
            "children" -> {
                when (value) {
                    is List<*> -> value.filterIsInstance2(Transform::class).forEach(::addChild)
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

    fun getLocalTransform(globalTime: Double, reference: Transform?, dst: Matrix4f): Matrix4f {
        val parent = parent
        if (parent != null && parent !== reference) {
            val (parentTransform, parentTime) = parent.getGlobalTransformTime(globalTime, dst)
            val localTime = getLocalTime(parentTime)
            applyTransform(parentTransform, localTime)
            return parentTransform
        } else {
            val localTime = getLocalTime(globalTime)
            applyTransform(dst.identity(), localTime)
            return dst
        }
    }

    fun getGlobalTransform(globalTime: Double, dst: Matrix4f): Matrix4f {
        val parent = parent
        if (parent != null) {
            val (parentTransform, parentTime) = parent.getGlobalTransformTime(globalTime, dst)
            val localTime = getLocalTime(parentTime)
            applyTransform(parentTransform, localTime)
            return parentTransform
        } else {
            val localTime = getLocalTime(globalTime)
            applyTransform(dst.identity(), localTime)
            return dst
        }
    }

    fun getGlobalTime(globalTime: Double): Double {
        val parentTime = parent?.getGlobalTime(globalTime) ?: globalTime
        return getLocalTime(parentTime)
    }

    fun getGlobalTransformTime(globalTime: Double, dst: Matrix4f): TransformTime {
        val (parentTransform, parentTime) = parent?.getGlobalTransformTime(globalTime, dst)
            ?: TransformTime(dst, globalTime)
        val localTime = getLocalTime(parentTime)
        applyTransform(parentTransform, localTime)
        return TransformTime(parentTransform, localTime)
    }

    override fun isDefaultValue() = false

    override fun clone() = clone(InvalidRef)

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

    fun <V> vis(
        inspected: List<Inspectable>,
        title: String, ttt: String, dictPath: String, type: NumberType?, visibilityKey: String,
        values: List<ValueWithDefault<V>>, style: Style
    ) = vis(
        inspected, Dict[title, "obj.$dictPath"], Dict[ttt, "obj.$dictPath.desc"],
        visibilityKey, type, values, style
    )

    fun <V> clone(that: V): Any? {
        return when (that) {
            is Vector2f -> Vector2f(that)
            is Vector3f -> Vector3f(that)
            is Vector4f -> Vector4f(that)
            is Vector2d -> Vector2d(that)
            is Vector3d -> Vector3d(that)
            is Vector4d -> Vector4d(that)
            else -> null
        }
    }

    fun <V> setViaMask(old: V, new: V, mask: Int): V {
        if (mask == -1 || old !is Vector || new !is Vector) return new
        val clone = clone(old) as? Vector ?: return new
        for (i in 0 until old.numComponents) {
            if (mask.hasFlag(1 shl i)) {
                clone.setComp(i, new.getComp(i))
            }
        }
        @Suppress("UNCHECKED_CAST")
        return clone as V
    }

    fun <V> vis(
        inspected: List<Inspectable>,
        title: String, ttt: String, dictSubPath: String, type: NumberType?,
        values: List<ValueWithDefault<V>>, style: Style
    ): Panel {
        return vi(inspected, title, ttt, dictSubPath, type, values[0].value, style) { newValue, mask ->
            for (x in values) {
                x.value = setViaMask(x.value, newValue, mask)
            }
        }
    }

    /**
     * creates a panel with the correct input for the type, and sets the default values:
     * title, tool tip text, type, start value
     * callback is used to adjust the value
     * */
    fun <V> vi(
        inspected: List<Inspectable>,
        title: String, ttt: String, dictSubPath: String,
        type: NumberType?, value: V,
        style: Style, setValue: (value: V, mask: Int) -> Unit
    ): Panel {
        return ComponentUIV2.vi(
            inspected, this,
            Dict[title, "obj.$dictSubPath"],
            Dict[ttt, "obj.$dictSubPath.desc"],
            dictSubPath, type, value, style, setValue
        )
    }

    /**
     * creates a panel with the correct input for the type, and sets the default values:
     * title, tool tip text, type, start value
     * modifies the AnimatedProperty-Object, so no callback is needed
     * */
    fun vi(
        title: String, ttt: String, dictSubPath: String,
        values: AnimatedProperty<*>,
        style: Style
    ): IsAnimatedWrapper {
        return ComponentUIV2.vi(
            this,
            Dict[title, "obj.$dictSubPath"],
            Dict[ttt, "obj.$dictSubPath.desc"],
            dictSubPath, values, style
        )
    }

    fun vis(
        selves: List<Transform>,
        title: String, ttt: String, dictSubPath: String,
        values: List<AnimatedProperty<*>>, style: Style
    ): Panel {
        return ComponentUIV2.vis(
            selves,
            Dict[title, "obj.$dictSubPath"],
            Dict[ttt, "obj.$dictSubPath.desc"],
            dictSubPath, values, style
        )
    }

    override fun destroy() {
        removeFromParent()
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

    open fun getAdditionalChildrenOptions(): List<Option<Transform>> = emptyList()

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
            JsonStringReader.readFirstOrNull(this, workspace, Transform::class)
        } catch (_: InvalidFormatException) {
            null
        }

        const val minAlpha = 0.5f / 255f
        private val LOGGER = LogManager.getLogger(Transform::class)

        val dilationType = NumberType(1.0, 1, 1f, true, true, ::castToDouble2, ::castToDouble)

    }
}