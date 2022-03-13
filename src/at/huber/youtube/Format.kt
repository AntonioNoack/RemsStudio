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

    companion object {

        private val FORMATS = arrayOfNulls<Format>(316)

        init {

            // http://en.wikipedia.org/wiki/YouTube#Quality_and_formats

            // Video and Audio
            FORMATS[17] = Format("3gp", 144, VideoCodec.MPEG4, AudioCodec.AAC, 24, false)
            FORMATS[36] = Format("3gp", 240, VideoCodec.MPEG4, AudioCodec.AAC, 32, false)
            FORMATS[5] = Format("flv", 240, VideoCodec.H263, AudioCodec.MP3, 64, false)
            FORMATS[43] = Format("webm", 360, VideoCodec.VP8, AudioCodec.VORBIS, 128, false)
            FORMATS[18] = Format("mp4", 360, VideoCodec.H264, AudioCodec.AAC, 96, false)
            FORMATS[22] = Format("mp4", 720, VideoCodec.H264, AudioCodec.AAC, 192, false)

            // Dash Video
            FORMATS[160] = Format("mp4", 144, VideoCodec.H264, AudioCodec.NONE, true)
            FORMATS[133] = Format("mp4", 240, VideoCodec.H264, AudioCodec.NONE, true)
            FORMATS[134] = Format("mp4", 360, VideoCodec.H264, AudioCodec.NONE, true)
            FORMATS[135] = Format("mp4", 480, VideoCodec.H264, AudioCodec.NONE, true)
            FORMATS[136] = Format("mp4", 720, VideoCodec.H264, AudioCodec.NONE, true)
            FORMATS[137] = Format("mp4", 1080, VideoCodec.H264, AudioCodec.NONE, true)
            FORMATS[264] = Format("mp4", 1440, VideoCodec.H264, AudioCodec.NONE, true)
            FORMATS[266] = Format("mp4", 2160, VideoCodec.H264, AudioCodec.NONE, true)
            FORMATS[298] = Format("mp4", 720, VideoCodec.H264, 60, AudioCodec.NONE, true)
            FORMATS[299] = Format("mp4", 1080, VideoCodec.H264, 60, AudioCodec.NONE, true)

            // Dash Audio
            FORMATS[140] = Format("m4a", VideoCodec.NONE, AudioCodec.AAC, 128, true)
            FORMATS[141] = Format("m4a", VideoCodec.NONE, AudioCodec.AAC, 256, true)
            FORMATS[256] = Format("m4a", VideoCodec.NONE, AudioCodec.AAC, 192, true)
            FORMATS[258] = Format("m4a", VideoCodec.NONE, AudioCodec.AAC, 384, true)

            // WEBM Dash Video
            FORMATS[278] = Format("webm", 144, VideoCodec.VP9, AudioCodec.NONE, true)
            FORMATS[242] = Format("webm", 240, VideoCodec.VP9, AudioCodec.NONE, true)
            FORMATS[243] = Format("webm", 360, VideoCodec.VP9, AudioCodec.NONE, true)
            FORMATS[244] = Format("webm", 480, VideoCodec.VP9, AudioCodec.NONE, true)
            FORMATS[247] = Format("webm", 720, VideoCodec.VP9, AudioCodec.NONE, true)
            FORMATS[248] = Format("webm", 1080, VideoCodec.VP9, AudioCodec.NONE, true)
            FORMATS[271] = Format("webm", 1440, VideoCodec.VP9, AudioCodec.NONE, true)
            FORMATS[313] = Format("webm", 2160, VideoCodec.VP9, AudioCodec.NONE, true)
            FORMATS[302] = Format("webm", 720, VideoCodec.VP9, 60, AudioCodec.NONE, true)
            FORMATS[308] = Format("webm", 1440, VideoCodec.VP9, 60, AudioCodec.NONE, true)
            FORMATS[303] = Format("webm", 1080, VideoCodec.VP9, 60, AudioCodec.NONE, true)
            FORMATS[315] = Format("webm", 2160, VideoCodec.VP9, 60, AudioCodec.NONE, true)

            // WEBM Dash Audio
            FORMATS[171] = Format("webm", VideoCodec.NONE, AudioCodec.VORBIS, 128, true)
            FORMATS[249] = Format("webm", VideoCodec.NONE, AudioCodec.OPUS, 48, true)
            FORMATS[250] = Format("webm", VideoCodec.NONE, AudioCodec.OPUS, 64, true)
            FORMATS[251] = Format("webm", VideoCodec.NONE, AudioCodec.OPUS, 160, true)

            // HLS Live Stream
            FORMATS[91] = Format("mp4", 144, VideoCodec.H264, AudioCodec.AAC, 48, false, isHlsContent = true)
            FORMATS[92] = Format("mp4", 240, VideoCodec.H264, AudioCodec.AAC, 48, false, isHlsContent = true)
            FORMATS[93] = Format("mp4", 360, VideoCodec.H264, AudioCodec.AAC, 128, false, isHlsContent = true)
            FORMATS[94] = Format("mp4", 480, VideoCodec.H264, AudioCodec.AAC, 128, false, isHlsContent = true)
            FORMATS[95] = Format("mp4", 720, VideoCodec.H264, AudioCodec.AAC, 256, false, isHlsContent = true)
            FORMATS[96] = Format("mp4", 1080, VideoCodec.H264, AudioCodec.AAC, 256, false, isHlsContent = true)
        }

        fun findFormat(id: Int) = FORMATS.getOrNull(id)

    }

}