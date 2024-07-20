package me.anno.remsstudio.objects.text

import me.anno.fonts.TextGenerator

@Suppress("unused")
data class TextSegmentKey(
    val font: TextGenerator,
    val isBold: Boolean, val isItalic: Boolean,
    val text: CharSequence, val charSpacing: Float
) {

    fun equals(isBold: Boolean, isItalic: Boolean, text: CharSequence, charSpacing: Float) =
        isBold == this.isBold && isItalic == this.isItalic && text == this.text && charSpacing == this.charSpacing

    private val _hashCode = run {
        var result = font.hashCode()
        result = 31 * result + isBold.hashCode()
        result = 31 * result + isItalic.hashCode()
        result = 31 * result + text.hashCode()
        31 * result + charSpacing.hashCode()
    }

    override fun hashCode(): Int = _hashCode
    override fun equals(other: Any?): Boolean {
        return (this === other) || (other is TextSegmentKey &&
                _hashCode == other._hashCode &&
                font == other.font &&
                isBold == other.isBold &&
                isItalic == other.isItalic &&
                text == other.text &&
                charSpacing == other.charSpacing)
    }
}