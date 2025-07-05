package me.anno.remsstudio.objects.particles

import me.anno.engine.inspector.Inspectable
import me.anno.fonts.FontManager
import me.anno.fonts.mesh.TextMesh
import me.anno.io.base.BaseWriter
import me.anno.io.files.InvalidRef
import me.anno.io.json.saveable.JsonStringWriter
import me.anno.jvm.fonts.AWTFont
import me.anno.language.translation.NameDesc
import me.anno.remsstudio.objects.text.Text
import me.anno.remsstudio.objects.text.Text.Companion.textVisCache
import me.anno.ui.Style
import me.anno.ui.base.groups.PanelListY
import me.anno.ui.editor.SettingCategory
import me.anno.utils.structures.Collections.filterIsInstance2
import me.anno.utils.types.Strings.joinChars
import org.joml.Vector3f
import kotlin.streams.toList

class TextParticles : ParticleSystem() {

    val text = object : Text() {
        override val approxSize get() = this@TextParticles.approxSize
    }

    override fun needsChildren() = false

    override fun createInspector(
        inspected: List<Inspectable>, list: PanelListY, style: Style,
        getGroup: (NameDesc) -> SettingCategory
    ) {
        super.createInspector(inspected, list, style, getGroup)
        val inspected2 = inspected.filterIsInstance2(TextParticles::class).map { it.text }
        text.createInspectorWithoutSuper(inspected2, list, style, getGroup)
    }

    override fun createParticle(index: Int, time: Double): Particle? {

        val text2 = text.text[time]

        val char = text2.codePoints().toList().getOrNull(index) ?: return null

        val str = listOf(char).joinChars().toString()
        val clone = text.clone() as Text
        clone.text.set(str)

        val particle = super.createParticle(index, time)!!
        particle.type = clone

        // get position and add to original particle
        var px = 0f
        var py = 0f

        val visualState = text.getVisualState(text2)
        val data = textVisCache.getEntry(visualState, 1000L) { key, result ->
            val segments = text.splitSegments(text2)
            val keys = text.createKeys(segments)
            result.value = segments to keys
        }.waitFor()
        val dataValue = data!!

        val keys = dataValue.second
        val lineSegmentsWithStyle = dataValue.first

        val font2 = FontManager.getFont(text.font) as AWTFont
        val exampleLayout = font2.exampleLayout
        val scaleX = TextMesh.DEFAULT_LINE_HEIGHT / (exampleLayout.ascent + exampleLayout.descent)
        val scaleY = 1f / (exampleLayout.ascent + exampleLayout.descent)
        val width = lineSegmentsWithStyle.width * scaleX
        val height = lineSegmentsWithStyle.height * scaleY

        val lineOffset = -TextMesh.DEFAULT_LINE_HEIGHT * text.relativeLineSpacing[time]

        // min and max x are cached for long texts with thousands of lines (not really relevant)
        // actual text height vs baseline? for height

        val totalHeight = lineOffset * height

        val dx = text.getDrawDX(width, time)
        val dy = text.getDrawDY(lineOffset, totalHeight, time)

        var charIndex = 0

        text.forceVariableBuffer = true

        val textAlignment01 = text.textAlignment[time] * .5f + .5f
        partSearch@ for ((partIndex, part) in lineSegmentsWithStyle.parts.withIndex()) {

            val startIndex = charIndex
            val partLength = part.codepointLength
            val endIndex = charIndex + partLength

            if (index in startIndex until endIndex) {

                val offsetX = (width - part.lineWidth * scaleX) * textAlignment01

                val lineDeltaX = dx + part.xPos * scaleX + offsetX
                val lineDeltaY = dy + part.yPos * scaleY * lineOffset

                val key = keys[partIndex]
                val textMesh = text.getTextMesh(key)!!

                val di = index - startIndex
                val xOffset = (textMesh.offsets[di] + textMesh.offsets[di + 1]).toFloat() * 0.5f
                val offset = Vector3f(lineDeltaX + xOffset, lineDeltaY, 0f)
                px = offset.x / 100f
                py = offset.y

                break@partSearch

            }

            charIndex = endIndex + 1 // '\n'

        }

        particle.states.first().position.add(px, py, 0f)
        return particle

    }

    override fun save(writer: BaseWriter) {
        super.save(writer)
        text.saveWithoutSuper(writer)
    }

    override fun getSystemState() = super.getSystemState() to JsonStringWriter.toText(text, InvalidRef)

    override fun setProperty(name: String, value: Any?) {
        when (name) {
            "text", "textAlignment", "blockAlignmentX", "blockAlignmentY",
            "shadowOffset", "shadowColor", "shadowSmoothness", "relativeLineSpacing",
            "outlineColor0", "outlineColor1", "outlineColor2", "outlineWidths", "outlineDepth", "outlineSmoothness",
            "startCursor", "endCursor", "attractorBaseColor", "isItalic", "isBold", "roundSDFCorners", "smallCaps",
            "renderingMode", "font", "relativeTabSize", "relativeCharSpacing", "lineBreakWidth" ->
                text.setProperty(name, value)
            else -> super.setProperty(name, value)
        }
    }

    override val defaultDisplayName get() = "Text Particles"
    override val className get() = "TextParticles"

}