package me.anno.remsstudio.objects.geometric

import me.anno.engine.inspector.Inspectable
import me.anno.gpu.GFX
import me.anno.gpu.drawing.GFXx3D
import me.anno.gpu.shader.Shader
import me.anno.io.base.BaseWriter
import me.anno.language.translation.NameDesc
import me.anno.maths.Maths
import me.anno.maths.Maths.clamp
import me.anno.remsstudio.RemsStudio
import me.anno.remsstudio.animation.AnimatedProperty
import me.anno.remsstudio.gpu.ShaderLibV2.linePolygonShader
import me.anno.remsstudio.objects.GFXTransform
import me.anno.remsstudio.objects.Transform
import me.anno.remsstudio.objects.attractors.EffectColoring
import me.anno.remsstudio.objects.attractors.EffectMorphing
import me.anno.remsstudio.video.UVProjection
import me.anno.ui.Style
import me.anno.ui.base.buttons.TextButton
import me.anno.ui.base.groups.PanelListY
import me.anno.ui.editor.SettingCategory
import me.anno.utils.pooling.JomlPools
import me.anno.utils.structures.Collections.filterIsInstance2
import org.joml.Matrix4f
import org.joml.Matrix4fArrayList
import org.joml.Vector3f
import org.joml.Vector4f
import kotlin.math.floor

/**
 * children should be circles or polygons,
 * creates an outline shape, which can be animated
 * like https://youtu.be/ToTWaZtGOj8?t=87 (1:27, Linus Tech Tips, Upload date Apr 28, 2021)
 * */
@Suppress("MemberVisibilityCanBePrivate")
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
        inspected: List<Inspectable>, list: PanelListY, style: Style,
        getGroup: (NameDesc) -> SettingCategory
    ) {
        super.createInspector(inspected, list, style, getGroup)
        val c = inspected.filterIsInstance2(LinePolygon::class)
        val group = getGroup(NameDesc("Line", "Properties of the line", "obj.line"))
        group += vis(
            c, "Segment Start", "", "line.segmentStart",
            c.map { it.segmentStart }, style
        )
        group += vis(
            c, "Segment Length", "", "line.segmentLength",
            c.map { it.segmentLength }, style
        )
        group += vis(
            c, "Line Strength", "Thickness of the line", "line.lineStrength",
            c.map { it.lineStrength }, style
        )
        group += vis(
            c, "Is Closed", "Whether the start shall be connected to the end automatically", "line.isClosed",
            c.map { it.isClosed }, style
        )
        group += vis(
            c, "Fading", "How much the last points fade, if the offsets exclude everything", "line.fading",
            c.map { it.fadingOnEnd }, style
        )
        group += TextButton(NameDesc("Copy 1st child's scale to all"), false, style).addLeftClickListener {
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

            val transforms = points.map { point -> point.getLocalTransform(time, this, JomlPools.mat4f.create()) }
            val positions = transforms.map { transform -> getPosition(transform, JomlPools.vec3f.create()) }
            val times = points.map { point -> point.getLocalTime(time) }
            val colors = points.mapIndexed { index, point ->
                point.getLocalColor(color, times[index], JomlPools.vec4f.create())
            }

            val lineStrength = lineStrength[time] * 0.5f

            val tmpScale = JomlPools.vec3f.create()
            val scales = points.mapIndexed { index, point ->
                val s = point.scale[times[index], tmpScale]
                (s.x + s.y) * lineStrength
            }

            fun getPosition(f0: Float, dst: Vector3f): Vector3f {
                val i0 = clamp(floor(f0).toInt(), 0, positions.size - 2)
                return positions[i0].lerp(positions[i0 + 1], f0 - i0, dst)
            }

            fun getScale(f0: Float): Float {
                val i0 = clamp(floor(f0).toInt(), 0, scales.size - 2)
                return Maths.mix(scales[i0], scales[i0 + 1], f0 - i0)
            }

            fun getColor(f0: Float, dst: Vector4f): Vector4f {
                val i0 = clamp(floor(f0).toInt(), 0, colors.size - 2)
                return colors[i0].lerp(colors[i0 + 1], f0 - i0, dst)
            }

            fun drawChild(index: Int, fraction: Float) {
                // draw child at fraction
                val localTransform = mix(transforms[index], transforms[index + 1], fraction)
                val c0 = children[index]
                // get interpolated color
                stack.next {
                    stack.mul(localTransform)
                    val color1 = getColor(index + fraction, JomlPools.vec4f.create())
                    c0.drawWithParentTransformAndColor(stack, time, color, color1)
                    JomlPools.vec4f.sub(1)
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
            val forward = JomlPools.vec3f.create()
                .set(0f, 0f, 1f)

            val shader = linePolygonShader.value

            val c0 = JomlPools.vec4f.create()
            val c1 = JomlPools.vec4f.create()

            val p0 = JomlPools.vec3f.create()
            val p1 = JomlPools.vec3f.create()
            val dx = JomlPools.vec3f.create()
            val d0 = JomlPools.vec3f.create()
            val d1 = JomlPools.vec3f.create()
            val a0 = JomlPools.vec3f.create()
            val a1 = JomlPools.vec3f.create()
            val b0 = JomlPools.vec3f.create()
            val b1 = JomlPools.vec3f.create()

            fun drawSegment(i0: Float, i1: Float, alpha: Float) {
                getPosition(i0, p0)
                getPosition(i1, p1)
                p1.sub(p0, dx) // dx = normalize(p1-p0) x forward
                    .cross(forward)
                    .safeNormalize()
                dx.mul(getScale(i0) * alpha, d0)
                dx.mul(getScale(i1) * alpha, d1)
                getColor(i0, c0).mulAlpha(alpha, c0)
                getColor(i1, c1).mulAlpha(alpha, c1)
                drawSegment(
                    shader,
                    p0.add(d0, a0), p0.sub(d0, a1),
                    p1.add(d1, b0), p1.sub(d1, b1),
                    c0, c1, stack
                )
            }

            shader.use()
            uploadAttractors(
                shader, time, false,
                false, true
            ) // todo check if flipY is correct

            for (i in 1 until mappedIndices.size) {
                val i0 = mappedIndices[i - 1]
                val i1 = mappedIndices[i]
                drawSegment(i0, i1, 1f)
            }

            val isClosed = isClosed[time]
            if (isClosed > 0f && mappedIndices.size > 2) {
                drawSegment(mappedIndices.first(), mappedIndices.last(), isClosed)
            }

            JomlPools.vec4f.sub(colors.size + 2) // colors, tmp0, tmp1
            JomlPools.vec3f.sub(positions.size + 10 + 1) // positions, p0,p1,d,d0,d1,a0,a1,b0,b1, tmpScale
            JomlPools.mat4f.sub(transforms.size) // transforms

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
        UVProjection.Planar.mesh.draw(null, shader, 0)
        GFX.check()
    }

    private fun getPosition(matrix: Matrix4f, dst: Vector3f) = matrix.getTranslation(dst)

    override fun drawChildrenAutomatically(): Boolean = false
}