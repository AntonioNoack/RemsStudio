package test

import me.anno.Engine
import me.anno.engine.OfficialExtensions
import me.anno.jvm.HiddenOpenGLContext
import me.anno.remsstudio.objects.video.Video
import me.anno.utils.OS.videos
import me.anno.utils.Sleep

fun main() {
    OfficialExtensions.initForTests()
    HiddenOpenGLContext.createOpenGL()

    val video = Video()
    video.file = videos.getChild("Transitions.mkv")

    val frame = Sleep.waitUntilDefined(true) {
        video.getVideoFrame(4, 0, 30.0)
    }?.waitFor()

    println("Got frame ${frame?.width} x ${frame?.height}")

    Engine.requestShutdown()
}