package me.anno.remsstudio.objects

import me.anno.config.DefaultConfig
import me.anno.remsstudio.gpu.TexFiltering
import java.util.*

fun DefaultConfig.getFiltering(key: String, default: TexFiltering): TexFiltering {
    return when (val value = this[key]) {
        is Boolean -> if (value) TexFiltering.NEAREST else TexFiltering.LINEAR
        is Int -> default.find(value)
        is String -> {
            when (value.lowercase(Locale.ENGLISH)) {
                "true", "t", "nearest" -> TexFiltering.NEAREST
                "false", "f", "linear" -> TexFiltering.LINEAR
                "cubic", "bicubic" -> TexFiltering.CUBIC
                else -> default
            }
        }
        else -> {
            set(key, default)
            default
        }
    }
}