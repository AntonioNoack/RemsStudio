package me.anno.remsstudio.objects.modes

import me.anno.gpu.FinalRendering.isFinalRendering
import me.anno.language.translation.NameDesc

enum class TransformVisibility(val id: Int, val nameDesc: NameDesc) {
    VISIBLE(0, NameDesc("Visible")),
    EDITOR_ONLY(1, NameDesc("Editor Only")),
    VIDEO_ONLY(2, NameDesc("Video Only")),
    HIDDEN(3, NameDesc("Hidden"));

    val isVisible: Boolean
        get() = when (this) {
            VISIBLE -> true
            HIDDEN -> false
            EDITOR_ONLY -> !isFinalRendering
            VIDEO_ONLY -> isFinalRendering
        }

    companion object {
        operator fun get(id: Int) = entries.firstOrNull { id == it.id } ?: VISIBLE
    }
}