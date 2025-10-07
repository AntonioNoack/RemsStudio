package me.anno.remsstudio.objects.text

import me.anno.fonts.Font
import me.anno.remsstudio.objects.modes.TextRenderMode

data class TextState(
    val textRenderMode: TextRenderMode,
    val roundSDFCorners: Boolean,
    val text: String,
    val font: Font,
    val smallCaps: Boolean,
    val relativeWidthLimit: Float,
)