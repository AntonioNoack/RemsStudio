package me.anno.remsstudio.objects.text

import me.anno.fonts.FontManager
import me.anno.fonts.PartResult
import me.anno.fonts.mesh.TextMesh
import me.anno.fonts.signeddistfields.TextSDF
import me.anno.fonts.signeddistfields.algorithm.SignedDistanceField
import me.anno.gpu.FinalRendering.isFinalRendering
import me.anno.gpu.FinalRendering.onMissingResource
import me.anno.remsstudio.Selection
import me.anno.remsstudio.gpu.GFXx3Dv2
import me.anno.remsstudio.objects.attractors.EffectMorphing
import me.anno.remsstudio.objects.modes.TextRenderMode
import me.anno.remsstudio.objects.text.Text.Companion.DEFAULT_FONT_HEIGHT
import me.anno.ui.editor.sceneView.Grid
import me.anno.utils.pooling.JomlPools
import me.anno.utils.types.Strings.isBlank2
import org.joml.Matrix4fArrayList
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f
import kotlin.math.max
import kotlin.math.min

@Suppress("MemberVisibilityCanBePrivate")
object TextRenderer {

    fun draw(
        element: Text, stack: Matrix4fArrayList, time: Double, color: Vector4f,
        superCall: () -> Unit
    ) {

        val text = element.text[time]
        if (text.isBlank2()) {
            // element.super.onDraw(stack, time, color)
            superCall()
            return
        }

        val (lineSegmentsWithStyle, keys) = element.getSegments(text)

        val font2 = FontManager.getFont(element.font)
        val actualFontHeight = font2.getLineHeight()
        val scaleX = TextMesh.DEFAULT_LINE_HEIGHT / actualFontHeight
        val scaleY = 1f / actualFontHeight
        val width = lineSegmentsWithStyle.width * scaleX
        val height = lineSegmentsWithStyle.height * scaleY

        val lineOffset = -TextMesh.DEFAULT_LINE_HEIGHT * element.relativeLineSpacing[time]

        // min and max x are cached for long texts with thousands of lines (not really relevant)
        // actual text height vs baseline? for height

        val totalHeight = lineOffset * height

        val dx = element.getDrawDX(width, time)
        val dy = element.getDrawDY(lineOffset, totalHeight, time)

        val renderingMode = element.renderingMode
        val drawMeshes = renderingMode == TextRenderMode.MESH
        val drawSDFTextures = !drawMeshes

        // limit the characters
        // cursorLimit1 .. cursorLimit2
        val textLength = text.codePointCount(0, text.length)
        val startCursor = max(0, element.startCursor[time])
        var endCursor = min(textLength, element.endCursor[time])
        if (endCursor < 0) endCursor = textLength

        if (startCursor > endCursor) {
            // invisible
            // element.super.onDraw(stack, time, color)
            superCall()
            return
        }

        val lineBreakWidth = element.lineBreakWidth
        if (lineBreakWidth > 0f && !isFinalRendering && element in Selection.selectedTransforms) {
            // draw the borders
            // why 0.81? correct x-scale? (off by ca ~ x0.9)
            val x0 = dx + width * 0.5f
            val minX = x0 - 0.81f * lineBreakWidth
            val maxX = x0 + 0.81f * lineBreakWidth
            val y0 = dy - lineOffset * 0.75f
            val y1 = y0 + totalHeight
            Grid.drawLine(stack, color, Vector3f(minX, y0, 0f), Vector3f(minX, y1, 0f))
            Grid.drawLine(stack, color, Vector3f(maxX, y0, 0f), Vector3f(maxX, y1, 0f))
        }

        val shadowColor = element.shadowColor[time, Vector4f()]
        if (shadowColor.w >= 1f / 255f) {
            val shadowSmoothness = element.shadowSmoothness[time]
            val shadowOffset = element.shadowOffset[time, Vector3f()] * (1f / DEFAULT_FONT_HEIGHT)
            stack.next {
                stack.translate(shadowOffset)
                val tintedShadowColor = JomlPools.vec4f.create()
                    .set(color).mul(shadowColor)
                if (tintedShadowColor.w >= 1f / 255f) draw(
                    element, stack, time, tintedShadowColor,
                    lineSegmentsWithStyle, drawMeshes, drawSDFTextures,
                    keys, actualFontHeight,
                    width, lineOffset,
                    dx, dy, scaleX, scaleY,
                    startCursor, endCursor,
                    false, shadowSmoothness
                )
                JomlPools.vec4f.sub(1)
            }
        }

        if (color.w >= 1f / 255f) draw(
            element, stack, time, color,
            lineSegmentsWithStyle, drawMeshes, drawSDFTextures,
            keys, actualFontHeight,
            width, lineOffset,
            dx, dy, scaleX, scaleY,
            startCursor, endCursor,
            true, 0f
        )

    }

    private val oc0 = Vector4f()
    private val oc1 = Vector4f()
    private val oc2 = Vector4f()

    private fun correctColor(c0: Vector4f, c1: Vector4f) {
        if (c1.w < 1f / 255f) {
            c1.set(c0.x, c0.y, c0.z, c1.w)
        }
    }

    /**
     * if the alpha of a color is zero, it should be assigned the weighted sum if the neighbor colors,
     * or the shader needs to be improved;
     * let's just try the easiest way to correct the issue in 99% of all cases
     * */
    private fun correctColors(color: Vector4f, oc0: Vector4f, oc1: Vector4f, oc2: Vector4f) {
        correctColor(color, oc0)
        correctColor(oc0, oc1)
        correctColor(oc1, oc2)
    }

    private fun getOutlineColors(
        element: Text,
        useExtraColors: Boolean, drawSDFTexture: Boolean, time: Double, color: Vector4f,
        oc0: Vector4f, oc1: Vector4f, oc2: Vector4f
    ) {
        if (useExtraColors && drawSDFTexture) {
            val parentColor = element.parent?.getLocalColor(tmp0)
            if (parentColor != null) {
                element.outlineColor0[time, oc0].mul(parentColor)
                element.outlineColor1[time, oc1].mul(parentColor)
                element.outlineColor2[time, oc2].mul(parentColor)
            } else {
                element.outlineColor0[time, oc0]
                element.outlineColor1[time, oc1]
                element.outlineColor2[time, oc2]
            }
        } else {
            color.mulAlpha(element.outlineColor0[time, oc0].w, oc0)
            color.mulAlpha(element.outlineColor1[time, oc1].w, oc1)
            color.mulAlpha(element.outlineColor2[time, oc2].w, oc2)
        }

        correctColors(color, oc0, oc1, oc2)

    }

    private val tmp0 = Vector4f()
    private fun draw(
        element: Text,
        stack: Matrix4fArrayList, time: Double, color: Vector4f,
        lineSegmentsWithStyle: PartResult,
        drawMeshes: Boolean, drawSDFTexture: Boolean,
        keys: List<TextSegmentKey>, actualFontHeight: Float,
        width: Float, lineOffset: Float,
        dx: Float, dy: Float, scaleX: Float, scaleY: Float,
        startCursor: Int, endCursor: Int,
        useExtraColors: Boolean,
        extraSmoothness: Float
    ) {

        val oc0 = oc0
        val oc1 = oc1
        val oc2 = oc2

        getOutlineColors(element, useExtraColors, drawSDFTexture, time, color, oc0, oc1, oc2)

        var charIndex = 0

        firstTimeDrawing = true

        val textAlignment01 = element.textAlignment[time] * .5f + .5f
        for ((index, part) in lineSegmentsWithStyle.parts.withIndex()) {

            val startIndex = charIndex
            val partLength = part.codepointLength
            val endIndex = charIndex + partLength

            val localMin = max(0, startCursor - startIndex)
            val localMax = min(partLength, endCursor - startIndex)

            if (localMin < localMax) {

                val key = keys[index]
                val offsetX = (width - part.lineWidth * scaleX) * textAlignment01
                val lineDeltaX = dx + part.xPos * scaleX + offsetX
                val lineDeltaY = dy + part.yPos * scaleY * lineOffset

                if (drawMeshes) {
                    drawMesh(
                        element, key, time, stack, color,
                        lineDeltaX, lineDeltaY,
                        localMin, localMax
                    )
                }

                if (drawSDFTexture) {
                    drawSDFTexture(
                        element, key, time, stack, color,
                        lineDeltaX, lineDeltaY,
                        localMin, localMax,
                        actualFontHeight,
                        extraSmoothness,
                        oc0, oc1, oc2
                    )
                }

            }

            charIndex = endIndex

        }
    }

    var firstTimeDrawing = false

    private val tmpScale = Vector2f()
    private val tmpOffset = Vector2f()

    private fun drawSDFTexture(
        element: Text,
        key: TextSegmentKey, time: Double, stack: Matrix4fArrayList,
        color: Vector4f, lineDeltaX: Float, lineDeltaY: Float,
        startIndex: Int, endIndex: Int,
        actualFontHeight: Float,
        extraSmoothness: Float,
        oc1: Vector4f, oc2: Vector4f, oc3: Vector4f
    ) {

        if (color.w + oc1.w + oc2.w + oc3.w <= 0f) return
        val sdf2 = element.getSDFTexture(key)
        if (sdf2 == null) {
            if (isFinalRendering) {
                onMissingResource("Text-Texture ${element.font}", element.text)
            }
            return
        }

        val sdfResolution = SignedDistanceField.sdfResolution

        sdf2.charByChar = element.renderingMode != TextRenderMode.SDF_JOINED
        sdf2.roundCorners = element.roundSDFCorners

        val hasUVAttractors = element.children.any { it is EffectMorphing }

        sdf2.draw(startIndex, endIndex) { _, sdf, xOffset ->

            val texture = sdf?.texture
            if (texture != null && texture.isCreated()) {

                val baseScale =
                    TextMesh.DEFAULT_LINE_HEIGHT / (sdfResolution * actualFontHeight)

                val scaleX = 0.5f * texture.width * baseScale
                val scaleY = 0.5f * texture.height * baseScale

                val sdfOffset = sdf.offset

                /**
                 * character- and alignment offset
                 * */
                val charAlignOffsetX = lineDeltaX + xOffset
                val charAlignOffsetY = lineDeltaY + 0f

                /**
                 * offset, because the textures are always centered; don't start from the bottom left
                 * (text mesh does)
                 * */
                val sdfX = sdfOffset.x * scaleX
                val sdfY = sdfOffset.y * scaleY

                val tmpOffset = tmpOffset.set(charAlignOffsetX + sdfX, charAlignOffsetY + sdfY)
                val tmpScale = tmpScale.set(scaleX, scaleY)

                if (firstTimeDrawing) {

                    val outline = element.outlineWidths[time, Vector4f()].mul(sdfResolution)
                    outline.y = max(0f, outline.y) + outline.x
                    outline.z = max(0f, outline.z) + outline.y
                    outline.w = max(0f, outline.w) + outline.z

                    val smoothness = element.outlineSmoothness[time, Vector4f()]
                        .add(extraSmoothness, extraSmoothness, extraSmoothness, extraSmoothness)
                        .mul(sdfResolution)

                    val outlineDepth = element.outlineDepth[time]

                    GFXx3Dv2.drawOutlinedText(
                        element, time,
                        stack, tmpOffset, tmpScale,
                        texture, color, 5,
                        arrayOf(// is only created once -> don't worry too much about it
                            color, oc1, oc2, oc3,
                            Vector4f(0f)
                        ),
                        floatArrayOf(-1e3f, outline.x, outline.y, outline.z, outline.w),
                        floatArrayOf(0f, smoothness.x, smoothness.y, smoothness.z, smoothness.w),
                        outlineDepth,
                        hasUVAttractors,
                    )

                    firstTimeDrawing = false

                } else GFXx3Dv2.drawOutlinedText(stack, tmpOffset, tmpScale, texture, hasUVAttractors)
            } else if (sdf == null || texture == null || !texture.isCreated()) {
                if (isFinalRendering && sdf !== TextSDF.empty) {
                    onMissingResource("Text-Texture I '${element.font}'", key.text)
                }
            }
            false
        }
    }

    private val offset = Vector2f()

    private fun drawMesh(
        element: Text,
        key: TextSegmentKey, time: Double, stack: Matrix4fArrayList,
        color: Vector4f, lineDeltaX: Float, lineDeltaY: Float,
        startIndex: Int, endIndex: Int
    ) {

        val textMesh = element.getTextMesh(key)
        if (textMesh == null) {
            if (isFinalRendering) {
                onMissingResource("Text-Mesh II '${element.font}'", key.text)
            }
            return
        }

        textMesh.draw(startIndex, endIndex) { buffer, _, xOffset ->
            buffer!!
            val offset = offset.set(lineDeltaX + xOffset, lineDeltaY)
            if (firstTimeDrawing) {
                GFXx3Dv2.draw3DText(element, time, offset, stack, buffer, color)
                firstTimeDrawing = false
            } else {
                GFXx3Dv2.draw3DTextWithOffset(buffer, offset)
            }
            false
        }
    }
}