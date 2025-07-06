package me.anno.remsstudio.objects

import me.anno.config.DefaultConfig
import me.anno.ecs.Entity
import me.anno.ecs.EntityQuery.forAllComponents
import me.anno.ecs.EntityQuery.forAllComponentsInChildren
import me.anno.ecs.EntityQuery.hasComponent
import me.anno.ecs.components.anim.*
import me.anno.ecs.components.anim.BoneData.uploadJointMatrices
import me.anno.ecs.components.mesh.Mesh
import me.anno.ecs.components.mesh.MeshComponent
import me.anno.ecs.components.mesh.MeshComponentBase
import me.anno.ecs.components.mesh.material.Materials
import me.anno.ecs.prefab.PrefabCache
import me.anno.engine.inspector.Inspectable
import me.anno.engine.ui.render.ECSShaderLib
import me.anno.engine.ui.render.Renderers.previewRenderer
import me.anno.gpu.DitherMode
import me.anno.gpu.FinalRendering.isFinalRendering
import me.anno.gpu.FinalRendering.onMissingResource
import me.anno.gpu.GFXState
import me.anno.gpu.shader.Shader
import me.anno.gpu.shader.renderer.Renderer
import me.anno.gpu.texture.ITexture2D
import me.anno.gpu.texture.TextureLib.whiteTexture
import me.anno.io.base.BaseWriter
import me.anno.io.files.FileReference
import me.anno.io.files.InvalidRef
import me.anno.language.translation.Dict
import me.anno.language.translation.NameDesc
import me.anno.maths.Maths.fract
import me.anno.mesh.MeshUtils
import me.anno.mesh.MeshUtils.getScaleFromAABB
import me.anno.remsstudio.RemsStudio
import me.anno.remsstudio.animation.AnimatedProperty
import me.anno.remsstudio.gpu.GFXx3Dv2
import me.anno.ui.Panel
import me.anno.ui.Style
import me.anno.ui.base.groups.PanelListY
import me.anno.ui.base.groups.UpdatingContainer
import me.anno.ui.base.text.TextPanel
import me.anno.ui.editor.SettingCategory
import me.anno.ui.input.EnumInput
import me.anno.utils.files.LocalFile.toGlobalFile
import me.anno.utils.pooling.JomlPools
import me.anno.utils.structures.Collections.filterIsInstance2
import me.anno.utils.types.AnyToBool
import org.apache.logging.log4j.LogManager
import org.joml.Matrix4f
import org.joml.Matrix4fArrayList
import org.joml.Matrix4x3fArrayList
import org.joml.Vector4f

@Suppress("MemberVisibilityCanBePrivate")
class MeshTransform(var file: FileReference, parent: Transform?) : GFXTransform(parent) {

    companion object {
        private val LOGGER = LogManager.getLogger(MeshTransform::class)
        private val localTransformTmp = Matrix4x3fArrayList()
    }

    // todo lerp animations

    // todo types of lights
    // todo shadows, ...
    // todo types of shading/rendering?

    // todo info field with the amount of vertices, triangles, and such :)

    // todo it looks like some animations get forgotten, only first is shown

    val animation = AnimatedProperty.string()

    var centerMesh = true
    var normalizeScale = true
    var drawAsLines = false

    // for the start it is nice to be able to import meshes like a torus into the engine :)

    constructor() : this(InvalidRef, null)

    override fun onDraw(stack: Matrix4fArrayList, time: Double, color: Vector4f) {
        val file = file
        val data = PrefabCache[file].value?.sample
        if (data is Entity) {
            val ditherMode =
                if (GFXState.blendMode.currentValue == null) DitherMode.DITHER2X2
                else DitherMode.DRAW_EVERYTHING
            GFXState.ditherMode.use(ditherMode) {
                if (GFXState.currentRenderer != Renderer.idRenderer && GFXState.currentRenderer != Renderer.randomIdRenderer) {
                    GFXState.useFrame(previewRenderer) {
                        GFXState.animated.use(true) {
                            lastWarning = null
                            draw(data, stack, time, color)
                        }
                    }
                } else {
                    GFXState.animated.use(true) {
                        lastWarning = null
                        draw(data, stack, time, color)
                    }
                }
            }
        } else {
            lastWarning = if (file.exists) data?.className ?: "Model loading?" else "File missing"
            super.onDraw(stack, time, color)
        }
    }

    private fun getAnimName(source: FileReference): String {
        return "${source.getParent().name}/${source.name}"
    }

    private fun findCurrentAnimation(entity: Entity, animationName: String): Animation? {
        if ('/' !in animationName) return null
        var result: Animation? = null
        entity.forAllComponentsInChildren(AnimMeshComponent::class) { animMeshComponent ->
            if (result == null) {
                val mesh = animMeshComponent.getMesh()
                val skeleton = mesh?.skeleton ?: InvalidRef
                if (skeleton != InvalidRef) {
                    val skeletonFolder = skeleton.getParent()
                    val animationFolder = skeletonFolder.getSibling("animations")
                    val animSource = animationFolder.getChild(animationName)
                    val animation = AnimationCache.getEntry(animSource)
                    if (isFinalRendering && !animation.hasValue) {
                        onMissingResource("Animation missing", animSource)
                    } else {
                        result = animation.value
                    }
                }
            }
        }
        return result
    }

    private fun findAllAnimations(entity: Entity?): HashSet<FileReference> {
        // todo this is bugged, no animations are found for azeria
        val result = HashSet<FileReference>()
        entity?.forAllComponentsInChildren(AnimMeshComponent::class) { animMeshComponent ->
            val mesh = animMeshComponent.getMesh()
            val skeleton = mesh?.skeleton ?: InvalidRef
            if (skeleton != InvalidRef) {
                val skeletonFolder = skeleton.getParent()
                val animationFolder = skeletonFolder.getSibling("animations")
                val allAnimations = animationFolder.listChildren().flatMap { it.listChildren() }
                result.addAll(allAnimations)
            }
        }
        return result
    }

    private fun draw(
        entity: Entity,
        cameraMatrix: Matrix4fArrayList,
        time: Double,
        color: Vector4f
    ) {

        val animationName = animation[time]

        val drawSkeletons = !isFinalRendering
        val shader = ECSShaderLib.pbrModelShader.value
        shader.use()
        whiteTexture.bindTrulyNearest(shader, "reflectionPlane")
        GFXx3Dv2.shader3DUniforms(shader, cameraMatrix, color)

        // attractors aren't supported anymore
        uploadAttractors0(shader)

        val animation = findCurrentAnimation(entity, animationName)
        var animTexture: ITexture2D? = null
        if (animation != null) {
            if (AnimTexture.useAnimTextures) {

                val skeleton = SkeletonCache[animation.skeleton]
                val animTexture1 = if (skeleton != null) AnimationCache[skeleton] else null
                val frameIndex = fract(time.toFloat() / animation.duration) * animation.numFrames
                val internalIndex = animTexture1?.getIndex(animation, frameIndex)

                val animTexture2 = animTexture1?.texture
                if (skeleton == null) {
                    lastWarning = "Skeleton is invalid"
                    shader.v1b("hasAnimation", false)
                } else if (animTexture2 == null) {
                    lastWarning = "AnimTexture is invalid"
                    shader.v1b("hasAnimation", false)
                } else {
                    shader.v4f("animWeights", 1f, 0f, 0f, 0f)
                    shader.v4f("animIndices", internalIndex!!, 0f, 0f, 0f)
                    animTexture2.bindTrulyNearest(shader, "animTexture")
                    animTexture = animTexture2
                    shader.v1b("hasAnimation", true)
                }
            } else {
                val skinningMatrices = uploadJointMatrices(shader, animation, time)
                shader.v1b("hasAnimation", skinningMatrices != null)
            }
        } else {
            shader.v1b("hasAnimation", false)
        }

        val localTransform = localTransformTmp.clear()

        if (normalizeScale) {
            val scale = getScaleFromAABB(entity.getGlobalBounds())
            localTransform.scale(scale)
        }

        if (centerMesh) {
            MeshUtils.centerStackFromAABB(localTransform, entity.getGlobalBounds())
        }

        GFXState.drawLines.use(drawAsLines) {
            drawHierarchy(
                shader, cameraMatrix, localTransform,
                color, entity, drawSkeletons, animTexture
            )
        }
    }

    @Suppress("MemberVisibilityCanBePrivate")
    fun drawHierarchy(
        shader: Shader,
        cameraTransform: Matrix4f,
        localTransform: Matrix4x3fArrayList,
        color: Vector4f,
        entity: Entity,
        drawSkeletons: Boolean,
        animTexture: ITexture2D?
    ) {

        localTransform.pushMatrix()

        val transform = entity.transform
        val local = transform.getLocalTransform(JomlPools.mat4x3m.borrow())
        // this moves the engine parts correctly, but ruins the rotation of the ghost
        // and scales it totally incorrectly
        localTransform.mul(
            JomlPools.mat4x3f.borrow().set(
                local.m00, local.m01, local.m02,
                local.m10, local.m11, local.m12,
                local.m20, local.m21, local.m22,
                local.m30.toFloat(), local.m31.toFloat(), local.m32.toFloat(),
            )
        )

        if (entity.hasComponent(MeshComponentBase::class)) {

            shader.use()
            shader.m4x4("transform", cameraTransform)
            shader.m4x3("localTransform", localTransform)

            if (shader["invLocalTransform"] >= 0) {
                val tmp = JomlPools.mat4x3f.borrow()
                tmp.set(localTransform).invert()
                shader.m4x3("invLocalTransform", tmp)
            }

            shader.v1f("worldScale", 1f) // correct?
            shader.v4f("finalId", clickId)

            entity.forAllComponents(MeshComponentBase::class) { comp ->
                val mesh = comp.getMesh() as? Mesh
                if (mesh?.positions != null) {
                    mesh.ensureBuffer()
                    val materialOverrides = comp.materials
                    val materials = mesh.materials
                    for (index in 0 until mesh.numMaterials) {
                        val material = Materials.getMaterial(materialOverrides, materials, index)
                        shader.v1i("hasVertexColors", if (material.enableVertexColors) mesh.hasVertexColors else 0)
                        material.bind(shader)
                        shader.v4f("diffuseBase", material.diffuseBase * color)
                        animTexture?.bindTrulyNearest(shader, "animTexture")
                        mesh.draw(null, shader, index)
                    }
                } else LOGGER.warn("Missing mesh ${(comp as? MeshComponent)?.meshFile}")
            }
        }

        // todo implement
        /*if (drawSkeletons) {
            val animMeshRenderer = entity.getComponent(AnimRenderer::class, false)
            if (animMeshRenderer != null) {
                val skinningMatrices = uploadJointMatrices(shader, animation, time)
                SkeletonCache[animMeshRenderer.skeleton]
                    ?.draw(null, shader, localTransform, skinningMatrices)
            }
        }*/

        val children = entity.children
        for (i in children.indices) {
            drawHierarchy(
                shader, cameraTransform, localTransform,
                color, children[i], drawSkeletons, animTexture
            )
        }

        localTransform.popMatrix()
    }


    override fun createInspector(
        inspected: List<Inspectable>, list: PanelListY, style: Style,
        getGroup: (NameDesc) -> SettingCategory
    ) {

        super.createInspector(inspected, list, style, getGroup)
        val c = inspected.filterIsInstance2(MeshTransform::class)

        list += vi(
            inspected, "File", "Source file to be loaded", "mesh.file", null, file, style
        ) { it, _ -> for (x in c) x.file = it }

        list += vi(
            inspected, "Normalize Scale",
            "A quicker fix than manually finding the correct scale",
            "mesh.normalizeScale",
            null, normalizeScale, style
        ) { it, _ -> for (x in c) x.normalizeScale = it }

        list += vi(
            inspected, "Center Mesh",
            "If your mesh is off-center, this corrects it",
            "mesh.centerMesh",
            null, centerMesh, style
        ) { it, _ -> for (x in c) x.centerMesh = it }

        list += vi(
            inspected, "Draw As Lines",
            "Draws your mesh as lines instead of triangles",
            "mesh.drawAsLines",
            null, drawAsLines, style
        ) { it, _ -> for (x in c) x.drawAsLines = it }

        // the list of available animations depends on the model
        // but still, it's like an enum: only a certain set of animations is available,
        // and the user wouldn't know perfectly, which
        val map = HashMap<Set<FileReference>, Panel>()
        list += UpdatingContainer(100, {
            val entity = PrefabCache[file].value?.sample as? Entity
            val animations = findAllAnimations(entity)
            map.getOrPut(animations) {
                if (animations.isNotEmpty()) {

                    val animationNames = animations
                        .map { source -> getAnimName(source) }
                        .sorted()

                    var currentValue = animation[lastLocalTime]
                    val noAnimName = "No animation"
                    if (currentValue !in animationNames) {
                        currentValue = noAnimName
                    }

                    val options = listOf(NameDesc(noAnimName)) +
                            animationNames.map { name -> NameDesc(name) }

                    EnumInput(
                        NameDesc("Animation"),
                        NameDesc(currentValue),
                        options, style
                    ).setChangeListener { value, index, _ ->
                        RemsStudio.largeChange("Change MeshTransform.animation Value") {
                            val newValue = if (index == 0) "" else value.name
                            for (x in c) x.putValue(x.animation, newValue, false)
                        }
                    }
                } else TextPanel("No animations found!", style)
            }
        }, style)
    }

    override fun save(writer: BaseWriter) {
        super.save(writer)
        writer.writeFile("file", file)
        writer.writeObject(this, "animation", animation)
        writer.writeBoolean("normalizeScale", normalizeScale, true)
        writer.writeBoolean("centerMesh", centerMesh, true)
        writer.writeBoolean("drawAsLines", drawAsLines, true)
    }

    override fun setProperty(name: String, value: Any?) {
        when (name) {
            "normalizeScale" -> normalizeScale = value == true
            "centerMesh" -> centerMesh = value == true
            "animation" -> animation.copyFrom(value)
            "file" -> file = (value as? String)?.toGlobalFile() ?: (value as? FileReference) ?: InvalidRef
            "drawAsLines" -> drawAsLines = AnyToBool.anyToBool(value)
            else -> super.setProperty(name, value)
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