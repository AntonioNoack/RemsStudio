package me.anno.remsstudio.objects.modes

import me.anno.language.translation.NameDesc

enum class VideoType(val displayName: NameDesc) {
    UNKNOWN(NameDesc("Unknown", "", "ui.videoType.unknown")),
    IMAGE(NameDesc("Image", "", "ui.videoType.image")),
    VIDEO(NameDesc("Video", "", "ui.videoType.video")),
    AUDIO(NameDesc("Audio", "", "ui.videoType.audio")),
    IMAGE_SEQUENCE(NameDesc("Image Sequence", "", "ui.videoType.imgSequence"))
}