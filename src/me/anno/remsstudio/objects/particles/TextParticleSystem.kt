package me.anno.remsstudio.objects.particles

import me.anno.engine.inspector.Inspectable
import me.anno.gpu.FinalRendering.isFinalRendering
import me.anno.io.base.BaseWriter
import me.anno.io.files.InvalidRef
import me.anno.io.json.saveable.JsonStringWriter
import me.anno.language.translation.NameDesc
import me.anno.maths.Maths.mix
import me.anno.remsstudio.Selection
import me.anno.remsstudio.objects.Transform
import me.anno.remsstudio.objects.text.Text
import me.anno.remsstudio.objects.text.Text.Companion.EXTRA_SCALE
import me.anno.remsstudio.objects.text.TextRenderer
import me.anno.remsstudio.objects.text.TextRenderer.getGlyphLayout
import me.anno.remsstudio.objects.text.createInspectorWithoutSuperImpl
import me.anno.ui.Style
import me.anno.ui.base.groups.PanelListY
import me.anno.ui.editor.SettingCategory
import me.anno.utils.structures.Collections.filterIsInstance2
import me.anno.utils.types.Strings.joinChars
import org.joml.Matrix4fArrayList
import org.joml.Vector4f

class TextParticleSystem : ParticleSystem() {

    val text = object : Text() {
        override val approxSize get() = this@TextParticleSystem.approxSize
    }

    override fun needsChildren() = false

    override fun createInspector(
        inspected: List<Inspectable>, list: PanelListY, style: Style,
        getGroup: (NameDesc) -> SettingCategory
    ) {
        super.createInspector(inspected, list, style, getGroup)
        val transforms = inspected.filterIsInstance2(Transform::class)
        val toBeChanged = inspected.filterIsInstance2(TextParticleSystem::class).map { it.text }
        createInspectorWithoutSuperImpl(text, list, style, getGroup, transforms, toBeChanged)
    }

    override fun createParticle(index: Int, time: Double): Particle? {

        // index is the glyphIndex

        val currText = text.text[time]

        val key = text.getVisualState(currText)
        val glyphLayout = getGlyphLayout(key) ?: return null

        if (index !in glyphLayout.indices) return null
        val codepoint = glyphLayout.getCodepoint(index)
        val codepointAsString = codepoint.joinChars()

        val clone = text.clone() as Text
        clone.text.set(codepointAsString)

        val particle = super.createParticle(index, time)!!
        particle.type = clone

        val baseScale = glyphLayout.baseScale
        val width = glyphLayout.width * baseScale
        val height = glyphLayout.height * baseScale

        val extraLineOffset = text.relativeLineSpacing[time] - 1f

        // min and max x are cached for long texts with thousands of lines (not really relevant)
        // actual text height vs baseline? for height

        val totalHeight = height + extraLineOffset * (glyphLayout.numLines - 1f)

        val blockDX = text.getDrawDX(width, time)
        val blockDY = text.getDrawDY(totalHeight, glyphLayout.numLines, time) -
                text.getDrawDY(glyphLayout.actualFontSize * baseScale, 1, time)

        text.forceVariableBuffer = true

        val textAlignment01 = text.textAlignment[time] * 0.5f + 0.5f
        val extraSpace = width - glyphLayout.getLineWidth(index) * baseScale
        val textAlignDX = extraSpace * textAlignment01

        // each character is moving individually, and we have to fight against it using this calculation
        val blockAlignmentX01 = text.blockAlignmentX[time] * .5f + .5f
        val letterDX = baseScale * mix(glyphLayout.getX1(index), glyphLayout.getX0(index), blockAlignmentX01)

        // ensure extraLineOffset is consistent with Text (key.font.size vs glyphLayout.actualFontSize)
        val lineDY = extraLineOffset * glyphLayout.getLineIndex(index) + baseScale * glyphLayout.getY(index)

        val px = blockDX + letterDX + textAlignDX
        val py = blockDY - lineDY

        val offsetScale = EXTRA_SCALE
        particle.states.first().position.add(px * offsetScale, py * offsetScale, 0f)
        return particle
    }

    override fun onDraw(stack: Matrix4fArrayList, time: Double, color: Vector4f) {
        super.onDraw(stack, time, color)
        showLineBreakWidth(stack, time, color)
    }

    fun showLineBreakWidth(stack: Matrix4fArrayList, time: Double, color: Vector4f) {
        val lineBreakWidth = text.relativeWidthLimit
        if (lineBreakWidth > 0f && !isFinalRendering && this in Selection.selectedTransforms) {
            val key = text.getVisualState(text.text[time])
            val glyphLayout = getGlyphLayout(key) ?: return
            stack.pushMatrix()
            stack.scale(EXTRA_SCALE)
            TextRenderer.showLineBreakWidth(text, stack, time, color, glyphLayout, lineBreakWidth)
            stack.popMatrix()
        }
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