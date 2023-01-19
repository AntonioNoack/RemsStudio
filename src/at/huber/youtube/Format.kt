package at.huber.youtube

class Format(
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
    val isDashContainer: Boolean,
    val isHlsContent: Boolean
) {

    var id = 0

    fun toAudioString(): String {
        return "$audioCodec@${audioBitrate}kb/s ($ext)"
    }

    fun toVideoString(): String {
        return "${height}p@$fps, $videoCodec ($ext)"
    }

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

        private val FORMATS = arrayOfNulls<Format>(403)

        init {

            // https://web.archive.org/web/20200516070826/https://gist.github.com/Marco01809/34d47c65b1d28829bb17c24c04a0096f
            val hfr = booleanArrayOf(true, false, true, true, false, true, false)
            val res = intArrayOf(4320, 2160, 1440, 1080, 720, 480, 360, 240, 144)
            val ext = arrayOf("av1", "av1", "webm", "webm", "webm", "mp4", "mp4")
            val codecs = arrayOf(
                VideoCodec.AV1,
                VideoCodec.AV1,
                VideoCodec.VP9,
                VideoCodec.VP9,
                VideoCodec.VP9,
                VideoCodec.H264,
                VideoCodec.H264
            )
            val dashTable = intArrayOf(
                402, 0, 0, 272, 0, 0, 138,
                401, 0, 337, 315, 313, 0, 266,
                400, 0, 336, 308, 271, 0, 264,
                399, 0, 335, 303, 248, 299, 137,
                398, 0, 334, 302, 247, 298, 136,
                0, 397, 333, 0, 244, 0, 135,
                0, 396, 332, 0, 243, 0, 134,
                0, 395, 331, 0, 242, 0, 133,
                0, 394, 330, 0, 278, 0, 160,
            )
            val w = hfr.size
            for (i in dashTable.indices) {
                val c = dashTable[i]
                if (c == 0) continue
                val x = i % w
                val y = i / w
                FORMATS[c] = Format(ext[x], res[y], codecs[x], AudioCodec.NONE, if (hfr[x]) 60 else 30, true)
            }

            // http://en.wikipedia.org/wiki/YouTube#Quality_and_formats
            // https://gist.github.com/sidneys/7095afe4da4ae58694d128b1034e01e2

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
            FORMATS[139] = Format("mp4", VideoCodec.NONE, AudioCodec.AAC, 48, true)
            FORMATS[140] = Format("m4a", VideoCodec.NONE, AudioCodec.AAC, 128, true)
            FORMATS[141] = Format("m4a", VideoCodec.NONE, AudioCodec.AAC, 256, true)
            FORMATS[256] = Format("m4a", VideoCodec.NONE, AudioCodec.AAC, 192, true)
            FORMATS[258] = Format("m4a", VideoCodec.NONE, AudioCodec.AAC, 384, true)

            // WEBM Dash Video
            FORMATS[272] = Format("webm", 2880, VideoCodec.VP9, AudioCodec.NONE, true)
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
            FORMATS[300] = Format("mp4", 720, 60, VideoCodec.H264, AudioCodec.AAC, 128, false, isHlsContent = true)
            FORMATS[301] = Format("mp4", 1080, 60, VideoCodec.H264, AudioCodec.AAC, 128, false, isHlsContent = true)

            for (i in FORMATS.indices) FORMATS[i]?.id = i

        }

        fun findFormat(id: Int) = FORMATS.getOrNull(id)

    }

}