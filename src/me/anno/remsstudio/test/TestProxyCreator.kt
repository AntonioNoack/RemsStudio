package me.anno.remsstudio.test

import me.anno.io.files.FileReference.Companion.getReference
import me.anno.remsstudio.RemsConfig
import me.anno.remsstudio.RemsRegistry
import me.anno.remsstudio.RemsStudio
import me.anno.utils.OS
import me.anno.utils.OS.downloads
import me.anno.video.VideoProxyCreator.getProxyFile

fun main() {
    // test for a video file
    RemsStudio.setupNames()
    RemsRegistry.init()
    RemsConfig.init()
    getProxyFile(getReference(OS.videos, "GodRays.mp4"), 0)
    getProxyFile(getReference(downloads.getChild("Sonic Frontiers is an absolute mess.mp4")), 0)
}