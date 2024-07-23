package me.anno.remsstudio.objects

import me.anno.engine.inspector.Inspectable
import me.anno.gpu.shader.Shader
import me.anno.io.base.BaseWriter
import me.anno.language.translation.NameDesc
import me.anno.remsstudio.animation.AnimatedProperty
import me.anno.remsstudio.gpu.ShaderLibV2.colorForceFieldBuffer
import me.anno.remsstudio.gpu.ShaderLibV2.maxColorForceFields
import me.anno.remsstudio.gpu.ShaderLibV2.uvForceFieldBuffer
import me.anno.remsstudio.objects.attractors.EffectColoring
import me.anno.remsstudio.objects.attractors.EffectMorphing
import me.anno.remsstudio.objects.video.Video
import me.anno.ui.Style
import me.anno.ui.base.groups.PanelListY
import me.anno.ui.editor.SettingCategory
import me.anno.utils.structures.Collections.filterIsInstance2
import me.anno.utils.structures.lists.Lists.none2
import org.joml.Vector3f
import org.joml.Vector4f
import kotlin.math.abs
import kotlin.math.sqrt

@Suppress("MemberVisibilityCanBePrivate")
abstract class GFXTransform(parent: Transform?) : Transform(parent) {

    init {
        timelineSlot.setDefault(0)
    }

    val attractorBaseColor = AnimatedProperty.color(Vector4f(1f))

    // sure about that??...
    override fun getStartTime(): Double = 0.0

    override fun save(writer: BaseWriter) {
        super.save(writer)
        writer.writeObject(this, "attractorBaseColor", attractorBaseColor)
    }

    override fun setProperty(name: String, value: Any?) {
        when (name) {
            "attractorBaseColor" -> attractorBaseColor.copyFrom(value)
            else -> super.setProperty(name, value)
        }
    }

    override fun createInspector(
        inspected: List<Inspectable>, list: PanelListY, style: Style,
        getGroup: (NameDesc) -> SettingCategory
    ) {
        super.createInspector(inspected, list, style, getGroup)
        val c = inspected.filterIsInstance2(GFXTransform::class)
        val fx = getGroup(NameDesc("Effects", "Visual Effects Settings", "obj.effects"))
        fx += vis(
            c,
            "Coloring: Base Color",
            "Base color for coloring: What color is chosen when no circle is within the area",
            "effects.baseColor",
            c.map { it.attractorBaseColor },
            style
        )
    }

    open fun transformLocally(pos: Vector3f, time: Double): Vector3f {
        return pos
    }

    fun uploadAttractors(shader: Shader, time: Double, is3D: Boolean) {
        uploadUVAttractors(shader, time, is3D)
        uploadColorAttractors(shader, time)
    }

    fun uploadUVAttractors(shader: Shader, time: Double, is3D: Boolean) {

        // has no ability to display them
        if (shader["forceFieldUVCount"] < 0) return

        if (children.none { it is EffectMorphing }) {
            shader.v1i("forceFieldUVCount", 0)
            return
        }

        var morphings = children.filterIsInstance2(EffectMorphing::class)

        for (index in morphings.indices) {
            val attr = morphings[index]
            attr.lastLocalTime = attr.getLocalTime(time)
            attr.lastInfluence = attr.zooming[attr.lastLocalTime]
        }

        morphings = morphings.filter {
            it.lastInfluence != 0f
        }

        if (morphings.size > maxColorForceFields)
            morphings = morphings
                .sortedByDescending { it.lastInfluence }
                .subList(0, maxColorForceFields)

        shader.v1i("forceFieldUVCount", morphings.size)
        if (morphings.isNotEmpty()) {
            val buffer = uvForceFieldBuffer
            val loc1 = shader["forceFieldUVs"]
            if (loc1 > -1) {
                var bi = 0
                for (morphing in morphings) {
                    val localTime = morphing.lastLocalTime
                    val position = transformLocally(morphing.position[localTime], time)
                    buffer[bi++] = (position.x * 0.5f + 0.5f)
                    buffer[bi++] = (position.y * 0.5f + 0.5f)
                    if (is3D) {
                        buffer[bi++] = (position.z)
                        buffer[bi++] = (0f)
                    } else {
                        buffer[bi++] = (morphing.swirlStrength[localTime])
                        buffer[bi++] = (1f / morphing.swirlPower[localTime])
                    }
                }
                shader.v4fs(loc1, buffer)
            }
            val loc2 = shader["forceFieldUVData0"]
            if (loc2 > -1) {
                var bi = 0
                val sx = if (this is Video) 1f / lastW else 1f
                val sy = if (this is Video) 1f / lastH else 1f
                for (morphing in morphings) {
                    val localTime = morphing.lastLocalTime
                    val weight = morphing.lastInfluence
                    val sharpness = morphing.sharpness[localTime]
                    val scale = morphing.scale[localTime]
                    buffer[bi++] = (sqrt(sy / sx) * weight * scale.z / scale.x)
                    buffer[bi++] = (sqrt(sx / sy) * weight * scale.z / scale.y)
                    buffer[bi++] = (10f / (scale.z * weight * weight))
                    buffer[bi++] = (sharpness)
                }
                shader.v4fs(loc2, buffer)
            }
            val loc3 = shader["forceFieldUVData1"]
            if (loc3 > -1) {
                for (bi in morphings.indices) {
                    val morphing = morphings[bi]
                    val localTime = morphing.lastLocalTime
                    buffer[bi] = (morphing.chromatic[localTime])
                }
                shader.v1fs(loc3, buffer)
            }
        }

    }

    fun uploadColorAttractors(shader: Shader, time: Double) {

        // has no ability to display them
        if (shader["forceFieldColorCount"] < 0) return

        if (children.none2 { it is EffectColoring }) {
            shader.v1i("forceFieldColorCount", 0)
            return
        }

        var attractors = children.filterIsInstance2(EffectColoring::class)

        for (attractor in attractors) {
            attractor.lastLocalTime = attractor.getLocalTime(time)
            attractor.lastInfluence = attractor.influence[attractor.lastLocalTime]
        }

        if (attractors.size > maxColorForceFields) {
            attractors = attractors
                .sortedByDescending { it.lastInfluence }
                .subList(0, maxColorForceFields)
        }

        shader.v1i("forceFieldColorCount", attractors.size)
        if (attractors.isNotEmpty()) {
            shader.v4f("forceFieldBaseColor", attractorBaseColor[time])
            val buffer = colorForceFieldBuffer
            buffer.position(0)
            for (attractor in attractors) {
                val localTime = attractor.lastLocalTime
                val color = attractor.color[localTime]
                val colorM = attractor.colorMultiplier[localTime]
                buffer.put(color.x * colorM)
                buffer.put(color.y * colorM)
                buffer.put(color.z * colorM)
                buffer.put(color.w)
            }
            buffer.position(0)
            shader.v4fs("forceFieldColors", buffer)
            buffer.position(0)
            for (attractor in attractors) {
                val localTime = attractor.lastLocalTime
                val position = transformLocally(attractor.position[localTime], time)
                val weight = attractor.lastInfluence
                buffer.put(position.x).put(position.y).put(position.z).put(weight)
            }
            buffer.position(0)
            shader.v4fs("forceFieldPositionsNWeights", buffer)
            buffer.position(0)
            val sx = if (this is Video) 1f / lastW else 1f
            val sy = if (this is Video) 1f / lastH else 1f
            for (attractor in attractors) {
                val localTime = attractor.lastLocalTime
                val scale = attractor.scale[localTime]
                val power = attractor.sharpness[localTime]
                buffer.put(abs(sy / sx / scale.x))
                buffer.put(abs(sx / sy / scale.y))
                buffer.put(abs(1f / scale.z))
                buffer.put(power)
            }
            buffer.position(0)
            shader.v4fs("forceFieldColorPowerSizes", buffer)
        }

    }

    companion object {
        fun uploadAttractors(transform: GFXTransform?, shader: Shader, time: Double, is3D: Boolean) {
            transform?.uploadAttractors(shader, time, is3D) ?: uploadAttractors0(shader)
        }

        fun uploadAttractors0(shader: Shader) {
            shader.v1i("forceFieldUVCount", 0)
            shader.v1i("forceFieldUVCount", 0)
        }
    }
}