package me.anno.remsstudio.objects.lists

import me.anno.config.DefaultConfig
import me.anno.engine.inspector.Inspectable
import me.anno.io.base.BaseWriter
import me.anno.language.translation.Dict
import me.anno.remsstudio.animation.AnimatedProperty
import me.anno.remsstudio.objects.GFXTransform
import me.anno.remsstudio.objects.Transform
import me.anno.remsstudio.objects.modes.ArraySelectionMode
import me.anno.ui.Style
import me.anno.ui.base.groups.PanelListY
import me.anno.ui.editor.SettingCategory
import me.anno.utils.types.Floats.toRadians
import org.joml.Matrix4fArrayList
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f
import java.util.*


// todo make it flexible enough, so it can be used for the particle system
// todo to make everything very flexible <3

// todo different types of lists (x list, y list, grid, linear particle system, random particle system, ...)
// todo different types of iterators (pdf pages, parts of images, images (from a sequence or video), letters, sentences, lines)
// todo generally reordering operation?

// todo re-project UV textures onto stuff to animate an image exploding (gets UVs from first frame, then just is a particle system or sth else)

class RegularList(parent: Transform? = null) : GFXTransform(parent) {

    val perChildTranslation = AnimatedProperty.pos()
    val perChildRotation = AnimatedProperty.rotYXZ()
    val perChildScale = AnimatedProperty.scale()
    val perChildSkew = AnimatedProperty.skew()
    var perChildDelay = AnimatedProperty.double(0.0)

    // val perChildTimeDilation = FloatArray(MAX_ARRAY_DIMENSION) // useful?, power vs linear

    // per child skew?

    override val symbol get() = DefaultConfig["ui.symbol.array", "[[["]

    val instanceCount = AnimatedProperty.intPlus(10)
    var selectionSeed = AnimatedProperty.long(0)
    var selectionMode = ArraySelectionMode.ROUND_ROBIN

    override fun acceptsWeight(): Boolean = true

    override fun save(writer: BaseWriter) {
        super.save(writer)
        writer.writeObject(this, "instanceCount", instanceCount, true)
        writer.writeObject(this, "perChildTranslation", perChildTranslation)
        writer.writeObject(this, "perChildRotation", perChildRotation)
        writer.writeObject(this, "perChildScale", perChildScale)
        writer.writeObject(this, "perChildSkew", perChildSkew)
        writer.writeObject(this, "perChildDelay", perChildDelay)
        writer.writeObject(this, "selectionSeed", selectionSeed)
    }

    override fun setProperty(name: String, value: Any?) {
        when (name) {
            "instanceCount" -> instanceCount.copyFrom(value)
            "perChildTranslation" -> perChildTranslation.copyFrom(value)
            "perChildRotation" -> perChildRotation.copyFrom(value)
            "perChildScale" -> perChildScale.copyFrom(value)
            "perChildSkew" -> perChildSkew.copyFrom(value)
            "perChildDelay" -> {
                if (value is Double) perChildDelay.set(value)
                else perChildDelay.copyFrom(value)
            }
            "selectionSeed" -> selectionSeed.copyFrom(value)
            else -> super.setProperty(name, value)
        }
    }

    override fun onDraw(stack: Matrix4fArrayList, time: Double, color: Vector4f) {
        super.onDraw(stack, time, color)

        // todo make text replacement simpler???
        val instanceCount = instanceCount[time]
        if (instanceCount > 0 && children.isNotEmpty()) {
            val seed = selectionSeed[time]
            val random = Random(seed)
            random.nextInt() // first one otherwise is always 1 (with two elements)
            val perChildDelay = perChildDelay[time]
            drawArrayChild(
                stack, time, perChildDelay, color, 0, instanceCount, random,
                perChildTranslation[time], perChildRotation[time], perChildScale[time], perChildSkew[time]
            )
        }

    }

    fun drawArrayChild(
        transform: Matrix4fArrayList, time: Double, perChildDelay: Double, color: Vector4f,
        index: Int, instanceCount: Int, random: Random,
        position: Vector3f, euler: Vector3f, scale: Vector3f, skew: Vector2f
    ) {

        val childIndex = selectionMode[index, children.size, random]
        drawChild(transform, time, color, children[childIndex])

        if (index + 1 < instanceCount) {

            //val position = perChildTranslation[time]
            if (position.x != 0f || position.y != 0f || position.z != 0f) {
                transform.translate(position)
            }

            //val euler = perChildRotation[time]
            if (euler.y != 0f) transform.rotateY(euler.y.toRadians())
            if (euler.x != 0f) transform.rotateX(euler.x.toRadians())
            if (euler.z != 0f) transform.rotateZ(euler.z.toRadians())

            //val scale = perChildScale[time]
            if (scale.x != 1f || scale.y != 1f || scale.z != 1f) transform.scale(scale)

            // val skew = perChildSkew[time]
            if (skew.x != 0f || skew.y != 0f) transform.mul3x3(// works
                1f, skew.y, 0f,
                skew.x, 1f, 0f,
                0f, 0f, 1f
            )

            drawArrayChild(
                transform,
                time + perChildDelay, perChildDelay, color, index + 1, instanceCount, random,
                position, euler, scale, skew
            )

        }
    }

    override fun drawChildrenAutomatically() = false

    override fun createInspector(
        inspected: List<Inspectable>,
        list: PanelListY,
        style: Style,
        getGroup: (title: String, description: String, dictSubPath: String) -> SettingCategory
    ) {
        super.createInspector(inspected, list, style, getGroup)
        val c = inspected.filterIsInstance<RegularList>()
        // todo create apply button?
        // todo we need to be able to insert properties...
        // todo replace? :D, # String Array

        val child = getGroup("Per-Child Transform", "For the n-th child, it is applied (n-1) times.", "per-child")
        child += vis(
            c, "Offset/Child", "", "array.offset", c.map { it.perChildTranslation },
            style
        )
        child += vis(c, "Rotation/Child", "", "array.rotation", c.map { it.perChildRotation }, style)
        child += vis(c, "Scale/Child", "", "array.scale", c.map { it.perChildScale }, style)
        child += vis(
            c, "Delay/Child", "Temporal delay between each child", "array.delay", c.map { it.perChildDelay },
            style
        )

        val instances = getGroup("Instances", "", "children")
        instances += vis(c, "Instance Count", "", "array.instanceCount", c.map { it.instanceCount }, style)
        instances += vi(
            inspected, "Selection Mode", "", "array.selectionMode",
            null, selectionMode, style
        ) { it, _ -> for (x in c) selectionMode = it }
        instances += vis(
            c,
            "Selection Seed",
            "Only for randomized selection mode; change it, if you have bad luck, or copies of this array, which shall look different",
            "array.selectionSeed",
            c.map { it.selectionSeed },
            style
        )

    }

    override val className get() = "RegularList"
    override val defaultDisplayName get() = Dict["List", "obj.list"]

}