package me.anno.remsstudio.objects

import me.anno.config.DefaultConfig
import me.anno.gpu.texture.Filtering
import java.util.*

fun DefaultConfig.getFiltering(key: String, default: Filtering): Filtering {
    return when (val value = this[key]) {
        is Boolean -> if (value) Filtering.NEAREST else Filtering.LINEAR
        is Int -> default.find(value)
        is String -> {
            when (value.lowercase(Locale.ENGLISH)) {
                "true", "t", "nearest" -> Filtering.NEAREST
                "false", "f", "linear" -> Filtering.LINEAR
                "cubic", "bicubic" -> Filtering.CUBIC
                else -> default
            }
        }
        else -> {
            set(key, default)
            default
        }
    }
}