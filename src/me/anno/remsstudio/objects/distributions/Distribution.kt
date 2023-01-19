package me.anno.remsstudio.objects.distributions

import me.anno.io.Saveable
import me.anno.language.translation.Dict
import me.anno.remsstudio.objects.Transform
import me.anno.remsstudio.objects.inspectable.InspectableAttribute
import me.anno.remsstudio.objects.inspectable.InspectableVector
import me.anno.remsstudio.objects.models.SphereAxesModel.sphereAxesModels
import me.anno.ui.base.groups.PanelList
import me.anno.ui.editor.sceneView.Grid
import me.anno.ui.style.Style
import me.anno.utils.types.Vectors.mulAlpha
import org.joml.Matrix4fArrayList
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f
import java.util.*

abstract class Distribution(val displayName: String, val description: String) :
    Saveable(), InspectableAttribute {

    constructor(displayName: String, description: String, dictPath: String) :
            this(Dict[displayName, dictPath], Dict[description, "$dictPath.desc"])

    /**
     * used by nearly all distributions anyway
     * */
    val random = Random()

    open fun nextV1(): Float {
        throw RuntimeException("Single component is not supported in ${javaClass.simpleName}")
    }

    open fun nextV2(): Vector2f {
        throw RuntimeException("Two components are not supported in ${javaClass.simpleName}")
    }

    open fun nextV3(): Vector3f {
        throw RuntimeException("Three components are not supported in ${javaClass.simpleName}")
    }

    open fun nextV4(): Vector4f {
        throw RuntimeException("Four components are not supported in ${javaClass.simpleName}")
    }

    open fun listProperties(): List<InspectableVector> = emptyList()

    override fun createInspector(list: PanelList, actor: Transform, style: Style) {
        val properties = listProperties()
        for (property in properties) {
            list += actor.vi(
                listOf(actor),
                property.title,
                property.description,
                property.pType.type,
                property.value,
                style
            ) { property.value.set(it) }
        }
    }

    override fun isDefaultValue() = false
    override val approxSize get() = 20

    fun Vector2f.mul(size: Vector4f) = mul(size.x, size.y)
    fun Vector2f.add(delta: Vector4f) = add(delta.x, delta.y)

    fun Vector3f.mul(size: Vector4f) = mul(size.x, size.y, size.z)
    fun Vector3f.add(delta: Vector4f) = add(delta.x, delta.y, delta.z)

    open fun draw(stack: Matrix4fArrayList, color: Vector4f) {
        onDraw(stack, color)
    }

    abstract fun onDraw(stack: Matrix4fArrayList, color: Vector4f)

    fun drawSphere(stack: Matrix4fArrayList, color: Vector4f, alpha: Float = 1f) {
        Grid.drawBuffer(
            stack,
            if (alpha == 1f) color
            else color.mulAlpha(alpha),
            sphereAxesModels[sphereSubDivision].value
        )
    }

    override fun equals(other: Any?): Boolean {
        return other?.javaClass === javaClass && other.toString() == toString()
    }

    override fun hashCode(): Int {
        return System.identityHashCode(this)
    }

    override fun toString(): String {
        return "${javaClass.name}@${System.identityHashCode(this)}"
    }

    companion object {
        const val sphereSubDivision = 4
    }

}