package me.anno.remsstudio.objects.particles.distributions

import me.anno.io.Saveable
import me.anno.language.translation.NameDesc
import me.anno.remsstudio.objects.Transform
import me.anno.remsstudio.objects.inspectable.InspectableAttribute
import me.anno.remsstudio.objects.inspectable.InspectableVector
import me.anno.remsstudio.objects.models.SphereAxesModel.sphereAxesModels
import me.anno.ui.Style
import me.anno.ui.base.groups.PanelList
import me.anno.ui.editor.sceneView.Grid
import org.joml.Matrix4fArrayList
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f
import java.util.*

abstract class Distribution(val nameDesc: NameDesc) : Saveable(), InspectableAttribute {

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

    open fun maxV1(): Float {
        throw RuntimeException("Single component is not supported in ${javaClass.simpleName}")
    }

    open fun listProperties(): List<InspectableVector> = emptyList()

    override fun createInspector(list: PanelList, actor: Transform, style: Style) {
        val properties = listProperties()
        for (property in properties) {
            list += actor.vi(
                listOf(actor), property.title, property.description,
                property.pType.type, property.value, style
            ) { it, _ -> property.value.set(it) }
        }
    }

    override fun isDefaultValue() = false
    override val approxSize get() = 20

    open fun draw(stack: Matrix4fArrayList, color: Vector4f) {
        onDraw(stack, color)
    }

    abstract fun onDraw(stack: Matrix4fArrayList, color: Vector4f)

    fun drawSphere(stack: Matrix4fArrayList, color: Vector4f, alpha: Float = 1f) {
        Grid.drawLineMesh(
            stack,
            if (alpha == 1f) color
            else color.mulAlpha(alpha, Vector4f()),
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