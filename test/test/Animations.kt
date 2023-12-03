package test

import me.anno.ecs.components.anim.SkeletonCache
import me.anno.engine.ECSRegistry
import me.anno.utils.OS.downloads

fun main() {
    ECSRegistry.initMeshes()
    val src = downloads.getChild("3d/azeria/scene.gltf")
    println(SkeletonCache[src.getChild("Skeleton.json")]!!.animations.keys)
}