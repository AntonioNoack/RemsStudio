package bench

import me.anno.io.files.FileReference
import me.anno.remsstudio.objects.video.Video
import me.anno.utils.OS
import me.anno.utils.Sleep
import me.anno.video.VideoCache
import org.apache.logging.log4j.LogManager

private val LOGGER = LogManager.getLogger("VideoDecodingBench")
fun main() {

    // create a graph of how long it takes to decode 1 .. 1024 frames of a video file
    // check at the start and at the end of the video: what is the overhead
    // short vs long video? idk...

    // doesn't matter, still 1frame/40s for 1.6GB file, and 9 min + 23 s length, 35211 frames (60 fps)
    // val folderExternal = File("E:\\Videos\\")
    val folderInternal = OS.documents

    val file = folderInternal.getChild("Large World.mp4")
    val meta = Video(file).forcedMeta!!
    val frameCount = meta.videoFrameCount
    val fps = meta.videoFPS

    LOGGER.info("Frames: $frameCount")

    val lengths = (1 until 10) + (10 until 100 step 10) + (100 until 1000 step 100) + 1000
    // 200/300 causes OutOfMemory
    // from 7 (1) to 91 (300) frames / second
    /*for (i in lengths) {
        decode(file, 0, i, fps)
    }*/

    for (i in lengths) {
        decode(file, frameCount - i, frameCount, fps)
    }

}

fun decode(file: FileReference, start: Int, end: Int, fps: Double) {
    val t0 = System.nanoTime()
    val index = end - 1
    val bufferLength = end - start
    val bufferIndex = index / bufferLength
    while (true) {
        val frame = VideoCache.getVideoFrame(
            file, 1, index,
            bufferIndex, bufferLength, fps,
            1, false
        ).waitFor()
        if (frame != null) break
        Sleep.sleepShortly(true)
    }
    val t1 = System.nanoTime()
    VideoCache.clear()
    val dt = (t1 - t0) * 1e-9
    LOGGER.info("$bufferLength, $start: ${dt}s, frames/s: ${bufferLength / dt}")
}