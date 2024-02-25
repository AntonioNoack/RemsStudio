package me.anno.remsstudio.objects.geometric

import me.anno.config.DefaultConfig
import me.anno.engine.inspector.Inspectable
import me.anno.io.base.BaseWriter
import me.anno.language.translation.Dict
import me.anno.remsstudio.animation.AnimatedProperty
import me.anno.remsstudio.gpu.GFXx3Dv2
import me.anno.remsstudio.objects.GFXTransform
import me.anno.remsstudio.objects.Transform
import me.anno.ui.Style
import me.anno.ui.base.groups.PanelListY
import me.anno.ui.editor.SettingCategory
import me.anno.ui.input.NumberType
import org.joml.Matrix4fArrayList
import org.joml.Vector3f
import org.joml.Vector4f

open class Circle(parent: Transform? = null) : GFXTransform(parent) {

    val innerRadius = AnimatedProperty.float01(0f)
    val startDegrees = AnimatedProperty(NumberType.ANGLE, 0f)
    val endDegrees = AnimatedProperty(NumberType.ANGLE, 360f)

    override fun onDraw(stack: Matrix4fArrayList, time: Double, color: Vector4f) {
        GFXx3Dv2.draw3DCircle(this, time, stack, innerRadius[time], startDegrees[time], endDegrees[time], color)
    }

    override fun transformLocally(pos: Vector3f, time: Double): Vector3f {
        return Vector3f(pos.x, -pos.y, pos.z) // why ever y needs to be mirrored...
    }

    override fun createInspector(
        inspected: List<Inspectable>,
        list: PanelListY,
        style: Style,
        getGroup: (title: String, description: String, dictSubPath: String) -> SettingCategory
    ) {
        super.createInspector(inspected, list, style, getGroup)
        val c = inspected.filterIsInstance<Circle>()
        val geo = getGroup("Geometry", "", "geometry")
        geo += vis(c, "Inner Radius", "Relative size of hole in the middle", c.map { it.innerRadius }, style)
        geo += vis(c, "Start Degrees", "To cut a piece out of the circle", c.map { it.startDegrees }, style)
        geo += vis(c, "End Degrees", "To cut a piece out of the circle", c.map { it.endDegrees }, style)
    }

    override fun save(writer: BaseWriter) {
        super.save(writer)
        writer.writeObject(this, "innerRadius", innerRadius)
        writer.writeObject(this, "startDegrees", startDegrees)
        writer.writeObject(this, "endDegrees", endDegrees)
    }

    override fun setProperty(name: String, value: Any?) {
        when (name) {
            "innerRadius" -> innerRadius.copyFrom(value)
            "startDegrees" -> startDegrees.copyFrom(value)
            "endDegrees" -> endDegrees.copyFrom(value)
            else -> super.setProperty(name, value)
        }
    }

    override val className get() = "Circle"
    override val defaultDisplayName get() = Dict["Circle", "obj.circle"]
    override val symbol get() = DefaultConfig["ui.style.circle", "◯"]

}