package me.anno.ecs.components.mesh

import me.anno.ecs.Component
import me.anno.ecs.Entity
import me.anno.ecs.annotations.Type
import me.anno.ecs.components.cache.MeshCache
import me.anno.ecs.prefab.PrefabSaveable
import me.anno.gpu.shader.Shader
import me.anno.io.files.FileReference
import me.anno.io.files.InvalidRef
import me.anno.io.serialization.SerializedProperty
import me.anno.utils.types.AABBs.transformUnion
import org.apache.logging.log4j.LogManager
import org.joml.AABBd
import org.joml.Matrix4x3d


// done (file) references to meshes and animations inside mesh files
//      - bird.fbx:anim:walk
//      - bird.fbx:mesh:wings
// todo static storage of things, e.g. for retargeting
// todo skeleton identity shall be defined by same names-array (for finding retargetings)

// todo drag item into inspector, and when clicking on something, let it show up the the last recently used? would allow to drag things from one to the other :3

// todo packages are mods/plugins, which are just installed with dependencies?

/////////////////////////////////////////////////////////////////////////////////////////


open class MeshComponent() : Component() {

    constructor(mesh: FileReference) : this() {
        this.mesh = mesh
    }

    @SerializedProperty
    @Type("Mesh/Reference")
    var mesh: FileReference = InvalidRef

    var isInstanced = false

    // todo respect this property
    @SerializedProperty
    var receiveShadows = true

    var collisionMask: Int = 1

    fun canCollide(collisionMask: Int): Boolean {
        return this.collisionMask.and(collisionMask) != 0
    }

    fun invalidate() {}

    // far into the future:
    // todo instanced animations for hundreds of humans:
    // todo bake animations into textures, and use indices + weights

    override fun fillSpace(globalTransform: Matrix4x3d, aabb: AABBd): Boolean {
        val mesh = MeshCache[mesh]
        if (mesh != null) {
            // add aabb of that mesh with the transform
            mesh.ensureBuffer()
            mesh.aabb.transformUnion(globalTransform, aabb)
        }
        return true
    }

    fun draw(shader: Shader, materialIndex: Int) {
        MeshCache[mesh]?.draw(shader, materialIndex)
    }

    open fun defineVertexTransform(shader: Shader, entity: Entity, mesh: Mesh) {
        shader.v1("hasAnimation", false)
    }

    // on destroy we should maybe destroy the mesh:
    // only if it is unique, and owned by ourselves

    override fun clone(): MeshComponent {
        val clone = MeshComponent()
        copy(clone)
        return clone
    }

    override fun copy(clone: PrefabSaveable) {
        super.copy(clone)
        clone as MeshComponent
        clone.collisionMask = collisionMask
        clone.isInstanced = isInstanced
        clone.mesh = mesh
    }

    override val className get() = "MeshComponent"

    companion object {

        private val LOGGER = LogManager.getLogger(MeshComponent::class)

    }

}