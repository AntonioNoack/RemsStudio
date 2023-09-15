package me.anno.remsstudio.objects

import me.anno.animation.Type
import me.anno.config.DefaultConfig
import me.anno.ecs.Entity
import me.anno.ecs.components.anim.AnimRenderer
import me.anno.ecs.components.anim.Animation
import me.anno.ecs.components.anim.AnimationCache
import me.anno.ecs.components.anim.SkeletonCache
import me.anno.ecs.prefab.PrefabCache
import me.anno.gpu.GFX
import me.anno.gpu.drawing.GFXx3D
import me.anno.gpu.shader.ShaderLib
import me.anno.io.ISaveable
import me.anno.io.base.BaseWriter
import me.anno.io.files.FileReference
import me.anno.io.files.InvalidRef
import me.anno.language.translation.Dict
import me.anno.language.translation.NameDesc
import me.anno.maths.Maths
import me.anno.mesh.MeshUtils
import me.anno.mesh.assimp.AnimGameItem
import me.anno.remsstudio.RemsStudio
import me.anno.remsstudio.animation.AnimatedProperty
import me.anno.studio.Inspectable
import me.anno.ui.Panel
import me.anno.ui.base.groups.PanelListY
import me.anno.ui.base.groups.UpdatingContainer
import me.anno.ui.base.text.TextPanel
import me.anno.ui.editor.SettingCategory
import me.anno.ui.input.EnumInput
import me.anno.ui.style.Style
import me.anno.utils.files.LocalFile.toGlobalFile
import org.joml.*

class MeshTransform(var file: FileReference, parent: Transform?) : GFXTransform(parent) {

    // todo lerp animations

    // todo types of lights
    // todo shadows, ...
    // todo types of shading/rendering?

    // todo info field with the amount of vertices, triangles, and such :)

    val animation = AnimatedProperty.string()

    var centerMesh = true
    var normalizeScale = true

    // for the start it is nice to be able to import meshes like a torus into the engine :)

    constructor() : this(InvalidRef, null)

    var powerOf10Correction = 0

    override fun onDraw(stack: Matrix4fArrayList, time: Double, color: Vector4f) {
        val file = file
        val data = PrefabCache[file]?.getSampleInstance()
        if (data is Entity) {
            lastWarning = null
            lastModel = draw(data, stack, time, color)
        } else {
            lastWarning = if (file.exists) data?.className ?: "Model loading?" else "File missing"
            super.onDraw(stack, time, color)
        }
    }

    private fun draw(data: Entity, stack: Matrix4fArrayList, time: Double, color: Vector4f): AnimGameItem {
        return stack.next {

            if (powerOf10Correction != 0)
                stack.scale(Maths.pow(10f, powerOf10Correction.toFloat()))

            // todo option to center the mesh
            // todo option to normalize its size
            // (see thumbnail generator)

            val drawSkeletons = GFX.isFinalRendering
            draw(
                data, stack, time, color, animation[time],
                centerMesh, normalizeScale, drawSkeletons
            )
        }
    }

    private fun findAnimations(entity: Entity): HashMap<String, Animation> {
        val result = HashMap<String, Animation>()
        entity.forAll {
            if (it is AnimRenderer) {
                val skeleton = SkeletonCache[it.skeleton]
                if (skeleton != null) {
                    for ((name, anim) in skeleton.animations) {
                        result[name] = AnimationCache[anim] ?: continue
                    }
                }
            }
        }
        return result
    }

    private fun draw(
        entity: Entity,
        cameraMatrix: Matrix4fArrayList,
        time: Double,
        color: Vector4f,
        animationName: String,
        centerMesh: Boolean,
        normalizeScale: Boolean,
        drawSkeletons: Boolean
    ): AnimGameItem {

        val baseShader = ShaderLib.shaderAssimp
        val shader = baseShader.value
        shader.use()
        GFXx3D.shader3DUniforms(shader, cameraMatrix, color)
        uploadAttractors(shader, time)

        val model0 = AnimGameItem(entity, findAnimations(entity))
        val animation = model0.animations[animationName]
        val skinningMatrices = if (animation != null) {
            model0.uploadJointMatrices(shader, animation, time)
        } else null
        shader.v1b("hasAnimation", skinningMatrices != null)

        val localTransform = Matrix4x3fArrayList()

        if (normalizeScale) {
            val scale = AnimGameItem.getScaleFromAABB(model0.staticAABB.value)
            localTransform.scale(scale)
        }

        if (centerMesh) {
            MeshUtils.centerMesh(this, cameraMatrix, localTransform, model0)
        }

        GFXx3D.transformUniform(shader, cameraMatrix)

        val cameraXPreGlobal = Matrix4f()
        cameraXPreGlobal.set(cameraMatrix)
            .mul(localTransform)

        val localTransform0 = Matrix4x3f(localTransform)
        val useMaterials = true
        model0.drawHierarchy(
            shader,
            cameraMatrix,
            cameraXPreGlobal,
            localTransform,
            localTransform0,
            skinningMatrices,
            color,
            model0.hierarchy,
            useMaterials,
            drawSkeletons
        )

        // todo line mode: draw every mesh as lines
        // todo draw non-indexed as lines: use an index buffer
        // todo draw indexed as lines: use a geometry shader, which converts 3 vertices into 3 lines
        return model0

    }

    var lastModel: AnimGameItem? = null

    override fun createInspector(
        inspected: List<Inspectable>,
        list: PanelListY,
        style: Style,
        getGroup: (title: String, description: String, dictSubPath: String) -> SettingCategory
    ) {

        super.createInspector(inspected, list, style, getGroup)
        val c = inspected.filterIsInstance<MeshTransform>()

        list += vi(inspected, "File", "", null, file, style) { for (x in c) x.file = it }
        list += vi(
            inspected, "Scale Correction, 10^N",
            "Often file formats are incorrect in size by a factor of 100. Use +/- 2 to correct this issue easily",
            Type.INT, powerOf10Correction, style
        ) { for (x in c) x.powerOf10Correction = it }

        list += vi(
            inspected, "Normalize Scale", "A quicker fix than manually finding the correct scale",
            null, normalizeScale, style
        ) { for (x in c) x.normalizeScale = it }
        list += vi(
            inspected, "Center Mesh", "If your mesh is off-center, this corrects it",
            null, centerMesh, style
        ) { for (x in c) x.centerMesh = it }

        // the list of available animations depends on the model
        // but still, it's like an enum: only a certain set of animations is available
        // and the user wouldn't know perfectly which
        val map = HashMap<AnimGameItem?, Panel>()
        list += UpdatingContainer(100, {
            map.getOrPut(lastModel) {
                val model = lastModel
                val animations = model?.animations
                if (!animations.isNullOrEmpty()) {
                    var currentValue = animation[lastLocalTime]
                    val noAnimName = "No animation"
                    if (currentValue !in animations.keys) {
                        currentValue = noAnimName
                    }
                    val options = listOf(NameDesc(noAnimName)) + animations.map { NameDesc(it.key) }
                    EnumInput(
                        NameDesc("Animation"),
                        NameDesc(currentValue),
                        options, style
                    ).setChangeListener { value, _, _ ->
                        RemsStudio.incrementalChange("Change MeshTransform.animation Value") {
                            for (x in c) x.putValue(x.animation, value, false)
                        }
                    }
                } else TextPanel("No animations found!", style)
            }
        }, style)

    }

    override fun save(writer: BaseWriter) {
        super.save(writer)
        writer.writeFile("file", file)
        writer.writeInt("powerOf10", powerOf10Correction)
        writer.writeObject(this, "animation", animation)
        writer.writeBoolean("normalizeScale", normalizeScale, true)
        writer.writeBoolean("centerMesh", centerMesh, true)
    }

    override fun readBoolean(name: String, value: Boolean) {
        when (name) {
            "normalizeScale" -> normalizeScale = value
            "centerMesh" -> centerMesh = value
            else -> super.readBoolean(name, value)
        }
    }

    override fun readObject(name: String, value: ISaveable?) {
        when (name) {
            "animation" -> animation.copyFrom(value)
            else -> super.readObject(name, value)
        }
    }

    override fun readString(name: String, value: String?) {
        when (name) {
            "file" -> file = value?.toGlobalFile() ?: InvalidRef
            else -> super.readString(name, value)
        }
    }

    override fun readFile(name: String, value: FileReference) {
        when (name) {
            "file" -> file = value
            else -> super.readFile(name, value)
        }
    }

    override fun readInt(name: String, value: Int) {
        when (name) {
            "powerOf10" -> powerOf10Correction = value
            else -> super.readInt(name, value)
        }
    }

    // was Mesh before
    // mmh, but the game engine is kinda more important...
    // and the mesh support was very limited before anyway -> we shouldn't worry too much,
    // because we don't have users at the moment anyway
    override val className get() = "MeshTransform"

    override val defaultDisplayName get() = Dict["Mesh", "obj.mesh"]
    override val symbol get() = DefaultConfig["ui.symbol.mesh", "\uD83D\uDC69"]

}