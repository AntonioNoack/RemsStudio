package test

import me.anno.utils.OS.videos
import me.anno.io.MediaMetadata.Companion.getMeta

fun main() {
    // getting stuck when running the engine...
    // some PDF stuff, too...
    println(getMeta(videos.getChild("2022-12-08 11-13-34.mkv"), false))
}