package me.anno.utils.test

import me.anno.ecs.Entity
import me.anno.ecs.components.cache.MeshCache
import me.anno.ecs.components.mesh.MeshComponent
import me.anno.ecs.prefab.PrefabCache
import me.anno.engine.ECSRegistry
import me.anno.engine.ui.render.ECSShaderLib.pbrModelShader
import me.anno.gpu.shader.BaseShader
import me.anno.io.files.FileReference.Companion.getReference
import me.anno.utils.OS.downloads

fun main() {

    pbrModelShader = BaseShader()
    ECSRegistry.init()

    val file = getReference(downloads, "San_Miguel/san-miguel.obj")
    val obj = PrefabCache.loadPrefab(file)
    val entity = obj!!.getSampleInstance(hashSetOf()) as Entity
    var sum = 0L
    entity.listOfAll {
        if (it is Entity) {
            val meshes = it.getComponents(MeshComponent::class)
            for (mesh in meshes) {
                val mesh2 = MeshCache[mesh.mesh]!!
                sum += if (mesh2.indices != null) {
                    mesh2.indices!!.size / 3
                } else {
                    mesh2.positions!!.size / 9
                }

            }
        }
        false
    }
    println(sum)

}