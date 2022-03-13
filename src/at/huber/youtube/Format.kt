package at.huber.youtube

data class Format(
    /** file extension and container format like "mp4" */
    val ext: String,
    /** pixel height of the video stream or -1 for audio files */
    val height: Int,
    /** frames per second */
    val fps: Int,
    val videoCodec: VideoCodec,
    val audioCodec: AudioCodec,
    /** Audio bitrate in kBit/s or -1 if there is no audio track */
    val audioBitrate: Int,
    val isDashContainer: Boolean, val isHlsContent: Boolean
) {

    constructor(
        ext: String,
        height: Int,
        videoCodec: VideoCodec,
        audioCodec: AudioCodec,
        isDashContainer: Boolean
    ) : this(ext, height, 30, videoCodec, audioCodec, -1, isDashContainer, false)

    constructor(
        ext: String,
        videoCodec: VideoCodec,
        audioCodec: AudioCodec,
        audioBitrate: Int,
        isDashContainer: Boolean
    ) : this(ext, -1, 30, videoCodec, audioCodec, audioBitrate, isDashContainer, false)

    constructor(
        ext: String, height: Int, videoCodec: VideoCodec,
        audioCodec: AudioCodec, audioBitrate: Int, isDashContainer: Boolean
    ) : this(ext, height, 30, videoCodec, audioCodec, audioBitrate, isDashContainer, false)

    internal constructor(
        ext: String,
        height: Int,
        videoCodec: VideoCodec,
        audioCodec: AudioCodec,
        audioBitrate: Int,
        isDashContainer: Boolean,
        isHlsContent: Boolean
    ) : this(ext, height, 30, videoCodec, audioCodec, audioBitrate, isDashContainer, isHlsContent)

    constructor(
        ext: String,
        height: Int,
        videoCodec: VideoCodec,
        fps: Int,
        audioCodec: AudioCodec,
        isDashContainer: Boolean
    ) : this(ext, height, fps, videoCodec, audioCodec, -1, isDashContainer, false)

}