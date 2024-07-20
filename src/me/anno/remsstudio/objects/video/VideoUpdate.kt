package me.anno.remsstudio.objects.video

import me.anno.io.files.FileReference
import me.anno.io.files.WebRef
import me.anno.remsstudio.objects.modes.VideoType
import me.anno.utils.types.Strings.getImportTypeByExtension
import me.anno.video.ImageSequenceMeta
import me.anno.video.ImageSequenceMeta.Companion.imageSequenceIdentifier

object VideoUpdate {
    fun Video.videoUpdate(file: FileReference, hasValidName: Boolean) {
        if (!hasValidName) return
        val meta = meta
        if (file !== lastFile || meta !== lastMeta) {
            lastFile = file
            lastMeta = meta
            type = if (file !is WebRef && file.name.contains(imageSequenceIdentifier)) {
                // async in the future?
                val imageSequenceMeta = ImageSequenceMeta(file)
                this.imageSequenceMeta = imageSequenceMeta
                VideoType.IMAGE_SEQUENCE
            } else if (meta == null) {
                when (getImportTypeByExtension(file.lcExtension)) {
                    "Video" -> VideoType.VIDEO
                    "Audio" -> VideoType.AUDIO
                    else -> VideoType.IMAGE
                }
            } else {
                if (meta.hasVideo) {
                    if (meta.videoFrameCount > 1) VideoType.VIDEO
                    else VideoType.IMAGE
                } else if (meta.hasAudio) VideoType.AUDIO
                else VideoType.UNKNOWN
            }
            lastWarning = null
            when (type) {
                VideoType.VIDEO, VideoType.AUDIO, VideoType.UNKNOWN -> {
                    if (meta != null && meta.hasVideo) {
                        if (file != lastAddedEndKeyframesFile) {
                            lastAddedEndKeyframesFile = file
                        }
                        lastDuration = meta.duration
                    }
                    type = if (meta == null) {
                        lastWarning = if (file.exists) {
                            "Video file is invalid"
                        } else {
                            "File does not exist"
                        }
                        VideoType.UNKNOWN
                    } else {
                        when (getImportTypeByExtension(file.lcExtension)) {
                            "Video" -> VideoType.VIDEO
                            "Audio" -> VideoType.AUDIO
                            else -> VideoType.IMAGE
                        }
                    }
                }
                VideoType.IMAGE_SEQUENCE -> {
                    val meta2 = imageSequenceMeta!!
                    if (!meta2.isValid) lastWarning = "No image sequence matches found"
                }
                VideoType.IMAGE -> {
                    // todo check if the image is valid...
                }
            }
        }
    }
}