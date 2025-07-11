package me.anno.remsstudio.objects.geometric

import me.anno.config.DefaultConfig
import me.anno.ecs.components.mesh.Mesh
import me.anno.engine.inspector.Inspectable
import me.anno.gpu.FinalRendering.isFinalRendering
import me.anno.gpu.FinalRendering.onMissingResource
import me.anno.gpu.texture.Clamping
import me.anno.gpu.texture.TextureCache
import me.anno.gpu.texture.TextureLib.whiteTexture
import me.anno.io.base.BaseWriter
import me.anno.io.files.FileReference
import me.anno.io.files.InvalidRef
import me.anno.language.translation.Dict
import me.anno.language.translation.NameDesc
import me.anno.maths.Maths.TAUf
import me.anno.maths.Maths.clamp
import me.anno.remsstudio.animation.AnimatedProperty
import me.anno.remsstudio.gpu.GFXx3Dv2.draw3DPolygon
import me.anno.remsstudio.gpu.TexFiltering
import me.anno.remsstudio.objects.GFXTransform
import me.anno.remsstudio.objects.Transform
import me.anno.ui.Style
import me.anno.ui.base.groups.PanelListY
import me.anno.ui.editor.SettingCategory
import me.anno.ui.input.NumberType
import me.anno.utils.files.LocalFile.toGlobalFile
import me.anno.utils.pooling.JomlPools
import me.anno.utils.structures.Collections.filterIsInstance2
import me.anno.utils.structures.lists.LazyList
import me.anno.utils.types.Booleans.hasFlag
import me.anno.utils.types.Booleans.toInt
import me.anno.utils.types.Casting
import org.joml.Matrix4fArrayList
import org.joml.Vector3f
import org.joml.Vector4f
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

@Suppress("MemberVisibilityCanBePrivate")
open class Polygon(parent: Transform? = null) : GFXTransform(parent) {

    // todo round edges?
    // lines can be used temporarily, as long, as it's not implemented

    override fun getDocumentationURL() = "https://remsstudio.phychi.com/?s=learn/geometry"

    var texture: FileReference = InvalidRef
    var filtering = TexFiltering.LINEAR

    var is3D = false

    var vertexCount = AnimatedProperty(NUM_EDGES_TYPE, 5)
    var starNess = AnimatedProperty.float01(0f)

    override fun onDraw(stack: Matrix4fArrayList, time: Double, color: Vector4f) {
        val inset = clamp(starNess[time], 0f, 1f)
        val image = TextureCache[texture, 5000].value
        if (image == null && texture != InvalidRef && isFinalRendering) {
            onMissingResource("Missing texture", texture)
            return
        }

        val texture = image ?: whiteTexture
        val count = vertexCount[time]
        if (inset == 1f && count % 2 == 0) return // invisible

        val selfDepth = scale[time, JomlPools.vec3f.create()].z
        JomlPools.vec3f.sub(1)

        stack.next {
            if (!is3D) stack.scale(1f, 1f, 0f)
            draw3DPolygon(
                this, time, stack, getMesh(count, selfDepth > 0f), texture, color,
                inset, filtering, Clamping.CLAMP
            )
        }
        return
    }

    override fun transformLocally(pos: Vector3f, time: Double): Vector3f {
        val z = if (is3D) pos.z else 0f
        return Vector3f(pos.x, -pos.y, z)
    }

    override fun createInspector(
        inspected: List<Inspectable>, list: PanelListY, style: Style,
        getGroup: (NameDesc) -> SettingCategory
    ) {
        super.createInspector(inspected, list, style, getGroup)
        val c = inspected.filterIsInstance2(Polygon::class)

        val geo = getGroup(NameDesc("Geometry", "", "obj.geometry"))
        geo += vis(
            c, "Vertex Count", "Quads, Triangles, all possible", "polygon.vertexCount", c.map { it.vertexCount },
            style
        )
        geo += vis(
            c, "Star-ness", "Works best with even vertex count", "polygon.star-ness", c.map { it.starNess },
            style
        )
        geo += vi(
            inspected, "Extrude", "Makes it 3D", "polygon.extrusion", null, is3D, style
        ) { it, _ -> for (x in c) x.is3D = it }

        val tex = getGroup(NameDesc("Pattern", "", "obj.texture"))
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

        private const val MIN_EDGES = 3

        private val maxEdges = lazy {
            max(DefaultConfig["objects.polygon.maxEdges", 1000], MIN_EDGES)
        }

        private val meshes by lazy {
            LazyList((maxEdges.value + 1 - MIN_EDGES) * 2) { index ->
                val n = index ushr 1
                val hasDepth = n.hasFlag(1)
                createMesh(n, hasDepth)
            }
        }

        val NUM_EDGES_TYPE = NumberType(
            0, 1, 1f, hasLinear = true, hasExponential = true,
            {
                val asInt = Casting.castToInt2(it)
                if (maxEdges.isInitialized()) clamp(asInt, MIN_EDGES, maxEdges.value)
                else max(asInt, MIN_EDGES)
            }, Casting::castToInt
        )

        fun getMesh(n: Int, hasDepth: Boolean): Mesh {
            val n = clamp(n, MIN_EDGES, maxEdges.value)
            val index = (n - MIN_EDGES).shl(1) + hasDepth.toInt()
            return meshes[index]
        }

        private fun createMesh(n: Int, hasDepth: Boolean): Mesh {

            val frontCount = n * 3
            val sideCount = n * 6
            val vertexCount = if (hasDepth) frontCount * 2 + sideCount else frontCount

            val angle0 = if (n == 4) 0.5f else n * 0.5f

            val outline = ArrayList<Vector4f>()
            for (i in 0 until n) {
                val inset = if (i % 2 == 0) 1f else 0f
                val angle = (i + angle0) * TAUf / n
                val sin = +sin(angle)
                val cos = -cos(angle)
                outline.add(Vector4f(sin, cos, inset, 1f))
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

            val d1 = -1f
            val d2 = +1f

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