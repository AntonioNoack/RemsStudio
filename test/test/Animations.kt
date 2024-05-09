package test

import me.anno.ecs.components.anim.SkeletonCache
import me.anno.engine.OfficialExtensions
import me.anno.utils.OS.downloads

fun main() {
    OfficialExtensions.initForTests()
    val src = downloads.getChild("3d/azeria/scene.gltf")
    println(SkeletonCache[src.getChild("Skeleton.json")]!!.animations.keys)
}