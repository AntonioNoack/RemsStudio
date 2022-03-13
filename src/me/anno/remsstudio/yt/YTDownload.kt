package me.anno.remsstudio.yt

import at.huber.youtube.YouTubeMeta

fun main() {

    // todo download a video from youtube, so we can use it as a source
    // todo maybe we could even download splices from it in the future for perfect playback of 10h videos without full download :3
    val videoId = "EaIXKubMCRQ"
    val extractor = YouTubeMeta(videoId)
    println(extractor)
    for ((key, value) in extractor.links) {
        println("$key: $value")
    }

}