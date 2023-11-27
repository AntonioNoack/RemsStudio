package me.anno.remsstudio.objects.modes

import me.anno.language.translation.NameDesc

// todo translations for these
enum class VideoType(val displayName: NameDesc) {
    UNKNOWN(NameDesc("Unknown")),
    IMAGE(NameDesc("Image")),
    VIDEO(NameDesc("Video")),
    AUDIO(NameDesc("Audio")),
    IMAGE_SEQUENCE(NameDesc("Image Sequence"))
}