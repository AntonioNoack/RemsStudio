package me.anno.remsstudio.objects.geometric

import me.anno.cache.CacheSection
import me.anno.config.DefaultConfig
import me.anno.ecs.components.mesh.Mesh
import me.anno.engine.inspector.Inspectable
import me.anno.gpu.GFX
import me.anno.gpu.texture.Clamping
import me.anno.gpu.texture.TextureCache
import me.anno.gpu.texture.TextureLib.whiteTexture
import me.anno.io.Saveable
import me.anno.io.base.BaseWriter
import me.anno.io.files.FileReference
import me.anno.io.files.InvalidRef
import me.anno.language.translation.Dict
import me.anno.maths.Maths.clamp
import me.anno.remsstudio.animation.AnimatedProperty
import me.anno.remsstudio.gpu.GFXx3Dv2.draw3DPolygon
import me.anno.remsstudio.gpu.TexFiltering
import me.anno.remsstudio.objects.GFXTransform
import me.anno.remsstudio.objects.Transform
import me.anno.ui.Style
import me.anno.ui.base.groups.PanelListY
import me.anno.ui.editor.SettingCategory
import me.anno.utils.files.LocalFile.toGlobalFile
import me.anno.utils.types.Floats.toRadians
import me.anno.video.MissingFrameException
import org.joml.Matrix4fArrayList
import org.joml.Vector3f
import org.joml.Vector4f
import java.net.URL
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

open class Polygon(parent: Transform? = null) : GFXTransform(parent) {

    // todo round edges?
    // lines can be used temporarily, as long, as it's not implemented

    override fun getDocumentationURL() = "https://remsstudio.phychi.com/?s=learn/geometry"

    var texture: FileReference = InvalidRef
    var autoAlign = false
    var filtering = TexFiltering.LINEAR

    var is3D = false
    var vertexCount = AnimatedProperty.intPlus(5)
    var starNess = AnimatedProperty.float01(0f)

    override fun onDraw(stack: Matrix4fArrayList, time: Double, color: Vector4f) {
        val inset = clamp(starNess[time], 0f, 1f)
        val image = TextureCache[texture, 5000, true]
        if (image == null && texture.hasValidName() && GFX.isFinalRendering) {
            throw MissingFrameException(texture.toString())
        }
        val texture = image ?: whiteTexture
        val count = vertexCount[time]//.roundToInt()
        if (inset == 1f && count % 2 == 0) return// invisible
        val selfDepth = scale[time].z
        stack.next {
            if (autoAlign) {
                stack.rotateZ((if (count == 4) 45f else 90f).toRadians())
                stack.scale(sqrt2, sqrt2, if (is3D) 1f else 0f)
            } else if (!is3D) {
                stack.scale(1f, 1f, 0f)
            }
            draw3DPolygon(
                this, time, stack, getBuffer(count, selfDepth > 0f), texture, color,
                inset, filtering, Clamping.CLAMP
            )
        }
        return
    }

    override fun transformLocally(pos: Vector3f, time: Double): Vector3f {
        val count = vertexCount[time]
        val z = if (is3D) pos.z else 0f
        return if (autoAlign) {
            if (count == 4) {
                Vector3f(0.5f * (pos.x + pos.y), 0.5f * (pos.x - pos.y), z)
            } else {
                Vector3f(sqrt2, -sqrt2, z)
            }
        } else Vector3f(pos.x, -pos.y, z)
    }

    override fun createInspector(
        inspected: List<Inspectable>,
        list: PanelListY,
        style: Style,
        getGroup: (title: String, description: String, dictSubPath: String) -> SettingCategory
    ) {
        super.createInspector(inspected, list, style, getGroup)
        val c = inspected.filterIsInstance<Polygon>()


        val geo = getGroup("Geometry", "", "geometry")
        geo += vis(
            c, "Vertex Count", "Quads, Triangles, all possible", "polygon.vertexCount", c.map { it.vertexCount },
            style
        )
        geo += vis(
            c, "Star-ness", "Works best with even vertex count", "polygon.star-ness", c.map { it.starNess },
            style
        )
        geo += vi(
            inspected, "Auto-Align",
            "Rotate 45°/90, and scale a bit; for rectangles", "polygon.autoAlign",
            null, autoAlign, style
        ) { it, _ -> for (x in c) x.autoAlign = it }
        geo += vi(
            inspected, "Extrude", "Makes it 3D", "polygon.extrusion", null, is3D, style
        ) { it, _ -> for (x in c) x.is3D = it }

        val tex = getGroup("Pattern", "", "texture")
        tex += vi(
            inspected, "Pattern Texture",
            "For patterns like gradients radially; use a mask layer for images with polygon shape", "polygon.pattern",
            null, texture, style
        ) { it, _ -> for (x in c) x.texture = it }
        tex += vi(
            inspected, "Filtering",
            "Pixelated or soft look of pixels?",
            "texture.filtering",
            null, filtering, style
        ) { it, _ -> for (x in c) x.filtering = it }

    }

    override fun save(writer: BaseWriter) {
        super.save(writer)
        writer.writeObject(this, "vertexCount", vertexCount)
        writer.writeObject(this, "inset", starNess)
        writer.writeBoolean("autoAlign", autoAlign)
        writer.writeBoolean("is3D", is3D)
        writer.writeInt("filtering", filtering.id, true)
        writer.writeFile("texture", texture)
    }

    override fun setProperty(name: String, value: Any?) {
        when (name) {
            "texture" -> texture = (value as? String)?.toGlobalFile() ?: (value as? FileReference) ?: InvalidRef
            "vertexCount" -> vertexCount.copyFrom(value)
            "inset" -> starNess.copyFrom(value)
            "filtering" -> filtering = filtering.find(value as? Int ?: return)
            "autoAlign" -> autoAlign = value == true
            "is3D" -> is3D = value == true
            else -> super.setProperty(name, value)
        }
    }

    override val className get() = "Polygon"
    override val defaultDisplayName get() = Dict["Polygon", "obj.polygon"]
    override val symbol
        get() =
            if (vertexCount.isAnimated) DefaultConfig["ui.symbol.polygon.any", "⭐"]
            else when (vertexCount[0.0]) {
                3 -> DefaultConfig["ui.symbol.polygon.3", "△"]
                4 -> DefaultConfig["ui.symbol.polygon.4", "⬜"]
                5 -> DefaultConfig["ui.symbol.polygon.5", "⭐"]
                6 -> DefaultConfig["ui.symbol.polygon.6", "⬡"]
                in 30 until Integer.MAX_VALUE -> DefaultConfig["ui.symbol.polygon.circle", "◯"]
                else -> DefaultConfig["ui.symbol.polygon.any", "⭐"]
            }

    companion object {

        val PolygonCache = CacheSection("PolygonCache")

        val sqrt2 = sqrt(2f)
        val meshTimeout = 1000L
        private const val minEdges = 3
        private val maxEdges by lazy { DefaultConfig["objects.polygon.maxEdges", 1000] }

        fun getBuffer(n: Int, hasDepth: Boolean): Mesh {
            if (n < minEdges) return getBuffer(minEdges, hasDepth)
            if (n > maxEdges) return getBuffer(maxEdges, hasDepth)
            return PolygonCache.getEntry(
                n * 2 + (if (hasDepth) 1 else 0),
                meshTimeout, false
            ) { createBuffer(n, hasDepth) } as Mesh
        }

        private fun createBuffer(n: Int, hasDepth: Boolean): Mesh {

            val frontCount = n * 3
            val sideCount = n * 6
            val vertexCount = if (hasDepth) frontCount * 2 + sideCount else frontCount

            val angles = FloatArray(n + 1) { i -> (i * Math.PI * 2.0 / n).toFloat() }
            val sin = angles.map { +sin(it) }
            val cos = angles.map { -cos(it) }

            val d1 = -1f
            val d2 = +1f

            val outline = ArrayList<Vector4f>()
            for (i in 0 until n) {
                val inset = if (i % 2 == 0) 1f else 0f
                outline.add(Vector4f(sin[i], cos[i], inset, 1f))
            }

            outline.add(outline.first())

            val positions = FloatArray(vertexCount * 3)
            val uvs = FloatArray(vertexCount * 2)
            var i = 0
            var j = 0
            fun putCenter(depth: Float) {
                i += 2
                positions[i++] = depth
                j += 2
            }

            fun put(out: Vector4f, depth: Float) {
                positions[i++] = out.x
                positions[i++] = out.y
                positions[i++] = depth
                uvs[j++] = out.z
                uvs[j++] = out.w
            }

            fun putFront(depth: Float) {
                for (k in 0 until n) {
                    putCenter(depth)
                    put(outline[k], depth)
                    put(outline[k + 1], depth)
                }
            }

            if (hasDepth) {
                putFront(d1)
                putFront(d2)
            } else {
                putFront(0f)
            }

            if (hasDepth) {
                for (k in 0 until n) {

                    // 012
                    put(outline[k], d1)
                    put(outline[k + 1], d1)
                    put(outline[k + 1], d2)

                    // 023
                    put(outline[k], d1)
                    put(outline[k + 1], d2)
                    put(outline[k], d2)

                }
            }

            val buffer = Mesh()
            buffer.positions = positions
            buffer.uvs = uvs
            return buffer
        }
    }

}