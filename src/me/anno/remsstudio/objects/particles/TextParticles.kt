package me.anno.remsstudio.objects.particles

import me.anno.engine.inspector.Inspectable
import me.anno.fonts.Codepoints.codepoints
import me.anno.io.base.BaseWriter
import me.anno.io.files.InvalidRef
import me.anno.io.json.saveable.JsonStringWriter
import me.anno.language.translation.NameDesc
import me.anno.remsstudio.objects.Transform
import me.anno.remsstudio.objects.text.Text
import me.anno.remsstudio.objects.text.TextRenderer.getGlyphLayout
import me.anno.remsstudio.objects.text.createInspectorWithoutSuperImpl
import me.anno.ui.Style
import me.anno.ui.base.groups.PanelListY
import me.anno.ui.editor.SettingCategory
import me.anno.utils.structures.Collections.filterIsInstance2
import me.anno.utils.types.Strings.joinChars

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
        val transforms = inspected.filterIsInstance2(Transform::class)
        val toBeChanged = inspected.filterIsInstance2(TextParticles::class).map { it.text }
        createInspectorWithoutSuperImpl(text, list, style, getGroup, transforms, toBeChanged)
    }

    override fun createParticle(index: Int, time: Double): Particle? {

        // index is the glyphIndex

        val currText = text.text[time]
        val currCodepoints = currText.codepoints()

        val codepoint = currCodepoints.getOrNull(index) ?: return null
        val codepointAsString = codepoint.joinChars()
        val clone = text.clone() as Text
        clone.text.set(codepointAsString)

        val particle = super.createParticle(index, time)!!
        particle.type = clone

        val key = text.getVisualState(currText)
        val glyphLayout = getGlyphLayout(key) ?: return null

        val width = glyphLayout.width * glyphLayout.baseScale
        val height = glyphLayout.height * glyphLayout.baseScale

        val extraLineOffset = text.relativeLineSpacing[time] - 1f

        // min and max x are cached for long texts with thousands of lines (not really relevant)
        // actual text height vs baseline? for height

        val totalHeight = height + extraLineOffset * (glyphLayout.numLines - 1f)

        val dx = text.getDrawDX(width, time)
        val dy = text.getDrawDY(totalHeight, glyphLayout.numLines, time)

        text.forceVariableBuffer = true

        val textAlignment01 = text.textAlignment[time] * 0.5f + 0.5f
        val extraSpace = width - glyphLayout.getLineWidth(index)
        val offsetX = extraSpace * textAlignment01

        val px = dx + offsetX + (glyphLayout.getX0(index) + glyphLayout.getX1(index)) * 0.5f
        val py = dy + extraLineOffset * glyphLayout.getLineIndex(index) + glyphLayout.getY(index)

        particle.states.first().position.add(
            px * glyphLayout.baseScale,
            py * glyphLayout.baseScale, 0f
        )
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