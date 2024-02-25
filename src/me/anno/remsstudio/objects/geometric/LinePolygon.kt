package me.anno.remsstudio.objects.geometric

import me.anno.engine.inspector.Inspectable
import me.anno.gpu.GFX
import me.anno.gpu.drawing.GFXx3D
import me.anno.gpu.drawing.UVProjection
import me.anno.gpu.shader.Shader
import me.anno.io.base.BaseWriter
import me.anno.maths.Maths
import me.anno.maths.Maths.clamp
import me.anno.remsstudio.RemsStudio
import me.anno.remsstudio.animation.AnimatedProperty
import me.anno.remsstudio.gpu.ShaderLibV2.linePolygonShader
import me.anno.remsstudio.objects.GFXTransform
import me.anno.remsstudio.objects.Transform
import me.anno.remsstudio.objects.attractors.EffectColoring
import me.anno.remsstudio.objects.attractors.EffectMorphing
import me.anno.ui.Style
import me.anno.ui.base.buttons.TextButton
import me.anno.ui.base.groups.PanelListY
import me.anno.ui.editor.SettingCategory
import org.joml.Matrix4f
import org.joml.Matrix4fArrayList
import org.joml.Vector3f
import org.joml.Vector4f
import java.net.URL
import kotlin.math.floor

/**
 * children should be circles or polygons,
 * creates an outline shape, which can be animated
 * like https://youtu.be/ToTWaZtGOj8?t=87 (1:27, Linus Tech Tips, Upload date Apr 28, 2021)
 * */
class LinePolygon(parent: Transform? = null) : GFXTransform(parent) {

    // todo if closed, modulo positions make sense, so a line could swirl around multiple times

    override val className get() = "LinePolygon"
    override val defaultDisplayName get() = "Line"

    override fun getDocumentationURL() = "https://remsstudio.phychi.com/?s=learn/lines"

    val segmentStart = AnimatedProperty.float(0f)
    val segmentLength = AnimatedProperty.float(1f)
    val lineStrength = AnimatedProperty.floatPlus(1f)
    var fadingOnEnd = AnimatedProperty.floatPlus(0.1f)

    var isClosed = AnimatedProperty.float01(0f)

    override fun save(writer: BaseWriter) {
        super.save(writer)
        writer.writeObject(this, "segmentStart", segmentStart)
        writer.writeObject(this, "segmentLength", segmentLength)
        writer.writeObject(this, "lineStrength", lineStrength)
        writer.writeObject(this, "fadingOnEnd", fadingOnEnd)
        writer.writeObject(this, "isClosed", isClosed)
    }

    override fun setProperty(name: String, value: Any?) {
        when (name) {
            "startOffset", "segmentStart" -> segmentStart.copyFrom(value)
            "endOffset", "segmentLength" -> segmentLength.copyFrom(value)
            "lineStrength" -> lineStrength.copyFrom(value)
            "fadingOnEnd" -> fadingOnEnd.copyFrom(value)
            "isClosed" -> isClosed.copyFrom(value)
            else -> super.setProperty(name, value)
        }
    }

    override fun createInspector(
        inspected: List<Inspectable>,
        list: PanelListY,
        style: Style,
        getGroup: (title: String, description: String, dictSubPath: String) -> SettingCategory
    ) {
        super.createInspector(inspected, list, style, getGroup)
        val c = inspected.filterIsInstance<LinePolygon>()
        val group = getGroup("Line", "Properties of the line", "line")
        group += vis(c, "Segment Start", "", c.map { it.segmentStart }, style)
        group += vis(c, "Segment Length", "", c.map { it.segmentLength }, style)
        group += vis(c, "Line Strength", "", c.map { it.lineStrength }, style)
        group += vis(c, "Is Closed", "", c.map { it.isClosed }, style)
        group += vis(
            c, "Fading", "How much the last points fade, if the offsets exclude everything", c.map { it.fadingOnEnd },
            style
        )
        group += TextButton("Copy 1st child's scale to all", false, style).addLeftClickListener {
            RemsStudio.largeChange("Copy first's scale") {
                for (ci in c) {
                    val cis = ci.children
                    if (cis.size > 1) {
                        val first = cis.first().scale
                        for (i in 1 until cis.size) {
                            val cii = cis[i]
                            cii.scale.clear()
                            for (kf in first.keyframes) {
                                cii.scale.addKeyframe(kf.time, Vector3f(kf.value))
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDraw(stack: Matrix4fArrayList, time: Double, color: Vector4f) {

        // todo coloring and morphing needs to be applied to the children
        // todo line isn't clickable???

        val points = children.filter { it !is EffectMorphing && it !is EffectColoring }
        if (points.isEmpty()) {
            super.onDraw(stack, time, color)
        } else if (points.size == 1) {
            drawChild(stack, time, color, children[0])
        } else {

            val transforms = points.map { it.getLocalTransform(time, this) }
            val positions = transforms.map { getPosition(it) }

            val times = points.map { it.getLocalTime(time) }
            val colors = points.mapIndexed { index, it -> it.getLocalColor(color, times[index]) }

            val lineStrength = lineStrength[time] * 0.5f

            val scales = points.mapIndexed { index, it ->
                val s = it.scale[times[index]]
                (s.x + s.y) * lineStrength
            }

            fun getPosition(i0: Int) = positions[i0]
            fun getPosition(f0: Float): Vector3f {
                val i0 = clamp(floor(f0).toInt(), 0, positions.size - 2)
                return Vector3f(getPosition(i0)).lerp(getPosition(i0 + 1), f0 - i0)
            }

            fun getScale(i0: Int) = scales[i0]
            fun getScale(f0: Float): Float {
                val i0 = clamp(floor(f0).toInt(), 0, scales.size - 2)
                return Maths.mix(getScale(i0), getScale(i0 + 1), f0 - i0)
            }

            fun getColor(i0: Int) = colors[i0]
            fun getColor(f0: Float): Vector4f {
                val i0 = clamp(floor(f0).toInt(), 0, colors.size - 2)
                return Vector4f(getColor(i0)).lerp(getColor(i0 + 1), f0 - i0)
            }

            fun drawChild(index: Int, fraction: Float) {
                // draw child at fraction
                val localTransform = mix(transforms[index], transforms[index + 1], fraction)
                val c0 = children[index]
                // get interpolated color
                stack.next {
                    stack.mul(localTransform)
                    c0.drawDirectly(stack, time, color, getColor(index + fraction))
                }
            }

            fun drawChild(index: Int) {
                drawChild(stack, time, color, children[index])
            }

            val lengths = FloatArray(points.size)
            var length = 0f
            for (i in 1 until points.size) {
                length += positions[i - 1].distance(positions[i])
                lengths[i] = length
            }

            // todo if start is negative or end is negative, extrapolate (on request)
            val start = segmentStart[time] * length
            val end = start + segmentLength[time] * length

            val mappedIndices = ArrayList<Float>()
            var hasStart = false
            for (i in lengths.indices) {
                val pos = lengths[i]
                if (pos >= start) {
                    if (!hasStart && i > 0) {
                        val len0 = lengths[i - 1]
                        mappedIndices.add(i - 1 + (start - len0) / (pos - len0))
                        hasStart = true
                    }
                    if (pos > end) {
                        if (end > start) {
                            val len0 = lengths[i - 1]
                            mappedIndices.add(i - 1 + (end - len0) / (pos - len0))
                        }
                        break
                    }
                    mappedIndices.add(i.toFloat())
                }
            }

            // todo if angle < 90°, use the projected, correct positions

            // todo use correct forward direction
            val forward = Vector3f(0f, 0f, 1f)

            val shader = linePolygonShader.value

            fun drawSegment(i0: Float, i1: Float, alpha: Float) {
                val p0 = getPosition(i0)
                val p1 = getPosition(i1)
                val delta = p1 - p0
                val deltaNorm = Vector3f(delta).normalize()
                val dx = deltaNorm.cross(forward)
                val d0 = dx * (getScale(i0) * alpha)
                val d1 = dx * (getScale(i1) * alpha)
                drawSegment(
                    shader,
                    Vector3f(p0).add(d0), Vector3f(p0).sub(d0),
                    Vector3f(p1).add(d1), Vector3f(p1).sub(d1),
                    getColor(i0).mulAlpha(alpha),
                    getColor(i1).mulAlpha(alpha),
                    stack
                )
            }

            shader.use()
            uploadAttractors(shader, time, false)
            for (i in 1 until mappedIndices.size) {
                val i0 = mappedIndices[i - 1]
                val i1 = mappedIndices[i]
                drawSegment(i0, i1, 1f)
            }

            val isClosed = isClosed[time]
            if (isClosed > 0f && mappedIndices.size > 2) {
                drawSegment(mappedIndices.first(), mappedIndices.last(), isClosed)
            }

            for (i in mappedIndices) {
                val ii = clamp(i.toInt(), 0, positions.size - 1)
                if (ii.toFloat() == i) {
                    drawChild(ii)
                } else {
                    drawChild(ii, i - ii)
                }
            }

        }

    }

    fun mix(m0: Matrix4f, m1: Matrix4f, f: Float): Matrix4f {
        return m0.lerp(m1, f, Matrix4f())
    }

    private fun drawSegment(
        shader: Shader,
        a0: Vector3f, a1: Vector3f,
        b0: Vector3f, b1: Vector3f,
        c0: Vector4f, c1: Vector4f,
        stack: Matrix4fArrayList
    ) {
        GFX.check()
        shader.use()
        GFXx3D.shader3DUniforms(shader, stack, -1)
        shader.v3f("pos0", a0)
        shader.v3f("pos1", a1)
        shader.v3f("pos2", b0)
        shader.v3f("pos3", b1)
        shader.v4f("col0", c0)
        shader.v4f("col1", c1)
        shader.v4f("finalId", clickId)
        UVProjection.Planar.mesh.draw(shader, 0)
        GFX.check()
    }

    private fun getPosition(m0: Matrix4f) = m0.getTranslation(Vector3f())

    override fun drawChildrenAutomatically(): Boolean = false
}