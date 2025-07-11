package me.anno.remsstudio.objects.text

import me.anno.cache.CacheSection
import me.anno.config.DefaultConfig
import me.anno.engine.inspector.Inspectable
import me.anno.fonts.Font
import me.anno.fonts.FontManager
import me.anno.fonts.LineSplitter
import me.anno.fonts.PartResult
import me.anno.fonts.mesh.TextMesh.Companion.DEFAULT_LINE_HEIGHT
import me.anno.fonts.mesh.TextMeshGroup
import me.anno.fonts.signeddistfields.TextSDFGroup
import me.anno.io.base.BaseWriter
import me.anno.language.translation.Dict
import me.anno.language.translation.NameDesc
import me.anno.maths.Maths.mix
import me.anno.remsstudio.animation.AnimatedProperty
import me.anno.remsstudio.objects.GFXTransform
import me.anno.remsstudio.objects.Transform
import me.anno.remsstudio.objects.modes.TextRenderMode
import me.anno.ui.Style
import me.anno.ui.base.components.AxisAlignment
import me.anno.ui.base.groups.PanelListY
import me.anno.ui.editor.SettingCategory
import me.anno.ui.input.NumberType
import me.anno.utils.types.Strings.smallCaps
import org.joml.Matrix4fArrayList
import org.joml.Vector3f
import org.joml.Vector4f

// todo background "color" in the shape of a plane? for selections and such

@Suppress("MemberVisibilityCanBePrivate")
open class Text(parent: Transform? = null) : GFXTransform(parent) {

    companion object {

        val DEFAULT_FONT_HEIGHT = 32

        /**
         * MeshScale in Rem's Engine was changed around Christmas 2024.
         * */
        val EXTRA_SCALE = 1f / 5f

        val tabSpaceType = NumberType.FLOAT_PLUS.withDefaultValue(4f)
        val lineBreakType = NumberType.FLOAT_PLUS.withDefaultValue(0f)

        val textMeshTimeout = 5000L

        private val textSegmentCache = CacheSection<TextSegmentKey, TextMeshGroup>("TextSegmentCache")
        private val textSDFCache = CacheSection<TextSegmentKey, TextSDFGroup>("TextSDFCache")
        val textVisCache = CacheSection<VisState, Pair<PartResult, List<TextSegmentKey>>>("TextVisCache")

    }

    constructor(text: String) : this(null) {
        this.text.set(text)
    }

    constructor(text: String, parent: Transform?) : this(parent) {
        this.text.set(text)
    }

    override fun getDocumentationURL() = "https://remsstudio.phychi.com/?s=learn/text"

    var text = AnimatedProperty.string()

    var renderingMode = TextRenderMode.MESH
    var roundSDFCorners = false

    var textAlignment = AnimatedProperty.alignment()
    var blockAlignmentX = AnimatedProperty.alignment()
    var blockAlignmentY = AnimatedProperty.alignment()

    val outlineColor0 = AnimatedProperty.color(Vector4f(0f))
    val outlineColor1 = AnimatedProperty.color(Vector4f(0f))
    val outlineColor2 = AnimatedProperty.color(Vector4f(0f))
    val outlineWidths = AnimatedProperty.vec4(Vector4f(0f, 1f, 1f, 1f))
    val outlineSmoothness = AnimatedProperty(NumberType.VEC4_PLUS, Vector4f(0f))
    var outlineDepth = AnimatedProperty.float(0f)

    val shadowColor = AnimatedProperty.color(Vector4f(0f))
    val shadowOffset = AnimatedProperty.pos(Vector3f(0.2f, -0.2f, -0.1f))
    val shadowSmoothness = AnimatedProperty.floatPlus(0f)

    val startCursor = AnimatedProperty.int(-1)
    val endCursor = AnimatedProperty.int(-1)

    // todo support lines around text like https://www.youtube.com/watch?v=iydG-e1dQGA
    // todo this will influence the outline :)
    // todo -> parametrize sdf ... second color channel
    // val startDegrees = AnimatedProperty.float(0f)
    // val endDegrees = AnimatedProperty.float(360f)

    // automatic line break after length x
    var lineBreakWidth = 0f

    // todo allow style by HTML/.md? :D
    // var textMode = TextMode.RAW

    var relativeLineSpacing = AnimatedProperty.float(1f)

    var relativeCharSpacing = 0f
    var relativeTabSize = 4f

    var font = Font("Verdana", DEFAULT_FONT_HEIGHT, isBold = false, isItalic = false)
    var smallCaps = false
    val charSpacing get() = font.size * relativeCharSpacing
    var forceVariableBuffer = false

    fun createKeys(lineSegmentsWithStyle: PartResult) =
        lineSegmentsWithStyle.parts.map {
            TextSegmentKey(
                it.font,
                font.isBold,
                font.isItalic,
                it.text,
                charSpacing
            )
        }

    open fun splitSegments(text: String): PartResult {
        if (text.isEmpty()) return PartResult(emptyList(), 0f, font.size, 1)
        val awtFont = FontManager.getFont(font) as LineSplitter<*>
        val absoluteLineBreakWidth = lineBreakWidth * font.size * 2f / DEFAULT_LINE_HEIGHT
        val text2 = if (smallCaps) text.smallCaps() else text
        return awtFont.splitParts(text2, font.size, relativeTabSize, relativeCharSpacing, absoluteLineBreakWidth, -1f)
    }

    fun getVisualState(text: String) = VisState(
        renderingMode, roundSDFCorners, charSpacing,
        text, font, smallCaps, lineBreakWidth, relativeTabSize
    )

    private val shallLoadAsync get() = !forceVariableBuffer
    fun getTextMesh(key: TextSegmentKey): TextMeshGroup? {
        return textSegmentCache.getEntry(key, textMeshTimeout) { keyInstance, result ->
            result.value = TextMeshGroup(
                keyInstance.font.fontKey.toFont(),
                keyInstance.text,
                keyInstance.charSpacing,
                forceVariableBuffer
            )
        }.waitFor(shallLoadAsync)
    }

    fun getSDFTexture(key: TextSegmentKey): TextSDFGroup? {
        return textSDFCache.getEntry(key, textMeshTimeout) { keyInstance, result ->
            result.value = TextSDFGroup(
                keyInstance.font.fontKey.toFont(), keyInstance.text,
                keyInstance.charSpacing.toDouble()
            )
        }.value
    }

    fun getDrawDX(width: Float, time: Double): Float {
        val blockAlignmentX01 = blockAlignmentX[time] * .5f + .5f
        return (blockAlignmentX01 - 1f) * width
    }

    fun getDrawDY(lineOffset: Float, totalHeight: Float, time: Double): Float {
        val dy0 = lineOffset * 0.57f // text touches top
        val dy1 = -totalHeight * 0.5f + lineOffset * 0.75f // center line, height of horizontal in e
        val dy2 = -totalHeight + lineOffset // exactly baseline
        val blockAlignmentY11 = blockAlignmentY[time]
        return if (blockAlignmentY11 < 0f) {
            mix(dy0, dy1, blockAlignmentY11 + 1f)
        } else {
            mix(dy1, dy2, blockAlignmentY11)
        }
    }

    fun getSegments(text: String): Pair<PartResult, List<TextSegmentKey>> {
        return textVisCache.getEntry(getVisualState(text), 1000L) { key, result ->
            val segments = splitSegments(text)
            val keys = createKeys(segments)
            result.value = segments to keys
        }.waitFor()!!
    }

    override fun onDraw(stack: Matrix4fArrayList, time: Double, color: Vector4f) {
        if (color.w >= 1f / 255f) {
            stack.scale(EXTRA_SCALE)
            TextRenderer.draw(this, stack, time, color) {
                super.onDraw(stack, time, color)
            }
        }
    }

    override fun save(writer: BaseWriter) {
        super.save(writer)
        saveWithoutSuper(writer)
    }

    fun saveWithoutSuper(writer: BaseWriter) {

        // basic settings
        writer.writeObject(this, "text", text)

        // font
        writer.writeString("font", font.name)
        writer.writeBoolean("isItalic", font.isItalic)
        writer.writeBoolean("isBold", font.isBold)
        writer.writeBoolean("smallCaps", smallCaps)

        // alignment
        writer.writeObject(this, "textAlignment", textAlignment)
        writer.writeObject(this, "blockAlignmentX", blockAlignmentX)
        writer.writeObject(this, "blockAlignmentY", blockAlignmentY)

        // spacing
        writer.writeObject(this, "relativeLineSpacing", relativeLineSpacing)
        writer.writeFloat("relativeTabSize", relativeTabSize, true)
        writer.writeFloat("lineBreakWidth", lineBreakWidth)
        writer.writeFloat("relativeCharSpacing", relativeCharSpacing)

        // outlines
        writer.writeInt("renderingMode", renderingMode.id)
        writer.writeBoolean("roundSDFCorners", roundSDFCorners)
        writer.writeObject(this, "outlineColor0", outlineColor0)
        writer.writeObject(this, "outlineColor1", outlineColor1)
        writer.writeObject(this, "outlineColor2", outlineColor2)
        writer.writeObject(this, "outlineWidths", outlineWidths)
        writer.writeObject(this, "outlineSmoothness", outlineSmoothness)
        writer.writeObject(this, "outlineDepth", outlineDepth)

        // shadows
        writer.writeObject(this, "shadowColor", shadowColor)
        writer.writeObject(this, "shadowOffset", shadowOffset)
        writer.writeObject(this, "shadowSmoothness", shadowSmoothness)

        // rpg cursor animation
        // todo append cursor symbol at the end
        // todo blinking cursor
        writer.writeObject(this, "startCursor", startCursor)
        writer.writeObject(this, "endCursor", endCursor)

    }

    override fun setProperty(name: String, value: Any?) {
        when (name) {
            "textAlignment" -> {
                if (value is Int) textAlignment.set(AxisAlignment.find(value)?.id?.toFloat() ?: return)
                else textAlignment.copyFrom(value)
            }
            "blockAlignmentX" -> {
                if (value is Int) blockAlignmentX.set(AxisAlignment.find(value)?.id?.toFloat() ?: return)
                else blockAlignmentX.copyFrom(value)
            }
            "blockAlignmentY" -> {
                if (value is Int) blockAlignmentY.set(AxisAlignment.find(value)?.id?.toFloat() ?: return)
                else blockAlignmentY.copyFrom(value)
            }
            "renderingMode" -> renderingMode = TextRenderMode.entries.firstOrNull { it.id == value } ?: renderingMode
            "relativeTabSize" -> relativeTabSize = value as? Float ?: return
            "relativeCharSpacing" -> relativeCharSpacing = value as? Float ?: return
            "lineBreakWidth" -> lineBreakWidth = value as? Float ?: return
            "text" -> {
                if (value is String) text.set(value)
                else text.copyFrom(value)
            }
            "font" -> font = font.withName(value as? String ?: "")
            "isBold" -> font = font.withBold(value == true)
            "isItalic" -> font = font.withItalic(value == true)
            "roundSDFCorners" -> roundSDFCorners = value == true
            "smallCaps" -> smallCaps = value == true
            "shadowOffset" -> shadowOffset.copyFrom(value)
            "shadowColor" -> shadowColor.copyFrom(value)
            "shadowSmoothness" -> shadowSmoothness.copyFrom(value)
            "relativeLineSpacing" -> relativeLineSpacing.copyFrom(value)
            "outlineColor0" -> outlineColor0.copyFrom(value)
            "outlineColor1" -> outlineColor1.copyFrom(value)
            "outlineColor2" -> outlineColor2.copyFrom(value)
            "outlineWidths" -> outlineWidths.copyFrom(value)
            "outlineDepth" -> outlineDepth.copyFrom(value)
            "outlineSmoothness" -> outlineSmoothness.copyFrom(value)
            "startCursor" -> startCursor.copyFrom(value)
            "endCursor" -> endCursor.copyFrom(value)
            else -> super.setProperty(name, value)
        }
    }

    override fun createInspector(
        inspected: List<Inspectable>, list: PanelListY, style: Style,
        getGroup: (NameDesc) -> SettingCategory
    ) {
        super.createInspector(inspected, list, style, getGroup)
        createInspectorWithoutSuper(inspected, list, style, getGroup)
    }

    fun createInspectorWithoutSuper(
        inspected: List<Inspectable>, list: PanelListY, style: Style,
        getGroup: (NameDesc) -> SettingCategory
    ) = createInspectorWithoutSuperImpl(inspected, list, style, getGroup)

    fun getSelfWithShadows() = getShadows() + this

    @Suppress("UNCHECKED_CAST")
    fun getShadows() = children.filter { it.name.contains("shadow", true) && it is Text } as List<Text>

    override fun passesOnColor() = false // otherwise white shadows of black text wont work

    override val className get() = "Text"
    override val defaultDisplayName = // text can be null!!!
        if (text == null) Dict["Text", "obj.text"]
        else (text.keyframes.maxByOrNull { it.value.length }?.value
            ?: text.defaultValue).ifBlank { Dict["Text", "obj.text"] }

    override val symbol get() = DefaultConfig["ui.symbol.text", "\uD83D\uDCC4"]

}