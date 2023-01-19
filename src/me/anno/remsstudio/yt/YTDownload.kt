package me.anno.remsstudio.yt

import at.huber.youtube.YouTubeMeta
import at.huber.youtube.YouTubeMeta.Companion.loadDataFromURL2
import me.anno.cache.data.VideoData
import me.anno.gpu.GFXState.useFrame
import me.anno.gpu.drawing.DrawTextures.drawTexture
import me.anno.gpu.framebuffer.DepthBufferType
import me.anno.gpu.framebuffer.Framebuffer
import me.anno.gpu.hidden.HiddenOpenGLContext
import me.anno.io.files.FileReference
import me.anno.io.files.FileReference.Companion.getReference
import me.anno.io.files.WebRef
import me.anno.remsstudio.yt.YTCache.getId
import me.anno.remsstudio.yt.YTCache.getYTMeta
import me.anno.utils.OS.desktop
import me.anno.utils.Sleep.waitForGFXThreadUntilDefined
import me.anno.video.ffmpeg.FFMPEGMetaParser

object YTCache {

    fun getId(file: FileReference): String? {
        if (file !is WebRef) return null
        val path = file.path
        val id = if (path.startsWith("https://www.youtube.com/watch")) {
            file.arguments["v"]
        } else if (path.startsWith("https://youtu.be/")) {
            path.substring("https://youtu.be/".length)
        } else return null
        return id
    }

    fun getYTMeta(file: FileReference): YouTubeMeta? {
        return YouTubeMeta(getId(file) ?: return null)
    }

    fun getYTMeta(id: String) = YouTubeMeta(id)

}

fun main() {
    // todo download a video from youtube, so we can use it as a source
    // todo maybe we could even download splices from it in the future for perfect playback of 10h videos without full download :3
    val ref = getReference("https://www.youtube.com/watch?v=7r6ctZ0TRIU")
    val extractor = getYTMeta(ref)!!
    println(extractor)
    for ((key, value) in extractor.links) {
        println("$key: $value")
    }
    HiddenOpenGLContext.createOpenGL()
    FFMPEGMetaParser.debug = true
    val bv = extractor.bestVideo!!
    println(bv.value.absolutePath)
    // val frame = VideoCache.getVideoFrame(bv.value, 1, 10, 11, bv.key.fps.toDouble(), 0L, false)!!
    val data = VideoData(
        bv.value, "media", 1, 1, 1,
        1, 16, bv.key.fps.toDouble(),
        0, bv.key.fps.toDouble(),
        100
    )
    val frame = waitForGFXThreadUntilDefined(true) {
        data.getFrame(15, true)
    }
    val fb = Framebuffer("tmp", frame.w, frame.h, 1, 1, false, DepthBufferType.NONE)
    useFrame(fb) {
        drawTexture(frame)
    }
    fb.getTexture0().write(desktop.getChild(getId(ref) + ".png"))
    desktop.getChild("test.mp4").writeBytes(loadDataFromURL2(bv.value.absolutePath).readBytes())
}