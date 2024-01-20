package me.anno.remsstudio.gpu

import me.anno.config.DefaultConfig
import me.anno.gpu.texture.Filtering
import me.anno.language.translation.NameDesc
import java.util.*

/**
 * high-level texture filtering used in Rem's Studio
 * */
enum class TexFiltering(private val baseIsNearest: Boolean, val id: Int, val naming: NameDesc) {
    NEAREST(true, 0, NameDesc("Nearest")),
    LINEAR(false, 1, NameDesc("Linear")),
    CUBIC(false, 2, NameDesc("Cubic"));

    fun convert(): Filtering {
        return if (baseIsNearest) Filtering.NEAREST
        else Filtering.LINEAR
    }

    fun find(value: Int): TexFiltering {
        return entries.firstOrNull { it.id == value } ?: this
    }

    companion object {
        fun DefaultConfig.getFiltering(key: String, default: TexFiltering): TexFiltering {
            return when (val value = this[key]) {
                is Boolean -> if (value) NEAREST else LINEAR
                is Int -> default.find(value)
                is String -> {
                    when (value.lowercase(Locale.ENGLISH)) {
                        "true", "t", "nearest" -> NEAREST
                        "false", "f", "linear" -> LINEAR
                        "cubic", "bicubic" -> CUBIC
                        else -> default
                    }
                }
                else -> {
                    set(key, default)
                    default
                }
            }
        }
    }
}