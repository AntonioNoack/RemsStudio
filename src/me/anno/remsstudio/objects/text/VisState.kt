package me.anno.remsstudio.objects.text

import me.anno.fonts.Font
import me.anno.remsstudio.objects.modes.TextRenderMode

data class VisState(
    val rm: TextRenderMode,
    val rsdf: Boolean,
    val cs: Float,
    val text: String,
    val font: Font,
    val sc: Boolean,
    val lbw: Float,
    val rts: Float
)