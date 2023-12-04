package me.anno.remsstudio.gpu

import me.anno.gpu.texture.Filtering
import me.anno.language.translation.NameDesc

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
}