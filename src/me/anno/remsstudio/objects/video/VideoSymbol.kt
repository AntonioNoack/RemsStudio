package me.anno.remsstudio.objects.video

import me.anno.config.DefaultConfig
import me.anno.remsstudio.objects.modes.VideoType

object VideoSymbol {
    fun Video.getVideoSymbol(): String {
        return when (if (file.hasValidName()) type else VideoType.VIDEO) {
            VideoType.AUDIO -> DefaultConfig["ui.symbol.audio", "\uD83D\uDD09"]
            VideoType.IMAGE -> DefaultConfig["ui.symbol.image", "\uD83D\uDDBC️️"]
            VideoType.VIDEO -> DefaultConfig["ui.symbol.video", "\uD83C\uDF9E️"]
            VideoType.IMAGE_SEQUENCE -> DefaultConfig["ui.symbol.imageSequence", "\uD83C\uDF9E️"]
            VideoType.UNKNOWN -> "?"
        }
    }
}