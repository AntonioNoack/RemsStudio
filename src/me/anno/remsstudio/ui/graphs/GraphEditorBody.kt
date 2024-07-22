package me.anno.remsstudio.ui.graphs

import me.anno.Time
import me.anno.animation.Interpolation
import me.anno.engine.EngineBase.Companion.workspace
import me.anno.gpu.drawing.DrawCurves
import me.anno.gpu.drawing.DrawRectangles.drawBorder
import me.anno.gpu.drawing.DrawRectangles.drawRect
import me.anno.gpu.drawing.DrawTexts
import me.anno.gpu.drawing.DrawTextures.drawTexture
import me.anno.gpu.texture.TextureLib.colorShowTexture
import me.anno.input.Input.isControlDown
import me.anno.input.Input.isShiftDown
import me.anno.input.Input.mouseKeysDown
import me.anno.input.Key
import me.anno.io.files.InvalidRef
import me.anno.io.json.saveable.JsonStringReader
import me.anno.io.json.saveable.JsonStringWriter
import me.anno.language.translation.NameDesc
import me.anno.maths.Maths.clamp
import me.anno.maths.Maths.dtTo10
import me.anno.maths.Maths.length
import me.anno.maths.Maths.mix
import me.anno.maths.Maths.pow
import me.anno.remsstudio.RemsStudio
import me.anno.remsstudio.RemsStudio.editorTime
import me.anno.remsstudio.RemsStudio.isPaused
import me.anno.remsstudio.RemsStudio.updateAudio
import me.anno.remsstudio.Selection.selectedProperties
import me.anno.remsstudio.animation.AnimatedProperty
import me.anno.remsstudio.animation.Keyframe
import me.anno.remsstudio.ui.IsSelectedWrapper
import me.anno.remsstudio.ui.MenuUtils.askNumber
import me.anno.remsstudio.ui.MenuUtils.drawTypeInCorner
import me.anno.remsstudio.ui.editor.TimelinePanel
import me.anno.ui.Style
import me.anno.ui.base.components.AxisAlignment
import me.anno.ui.base.menu.Menu.openMenu
import me.anno.ui.base.menu.MenuOption
import me.anno.ui.editor.sceneView.Grid.drawSmoothLine
import me.anno.ui.input.NumberType
import me.anno.utils.Color.black
import me.anno.utils.Color.mulAlpha
import me.anno.utils.Color.toARGB
import me.anno.utils.Color.white
import me.anno.utils.Color.withAlpha
import me.anno.utils.structures.Collections.filterIsInstance2
import me.anno.utils.types.AnyToFloat.getFloat
import me.anno.utils.types.Booleans.hasFlag
import me.anno.utils.types.Booleans.toInt
import org.apache.logging.log4j.LogManager
import org.joml.*
import java.util.*
import kotlin.math.*

// todo list all (animated) properties of this object (abbreviated)

@Suppress("MemberVisibilityCanBePrivate")
class GraphEditorBody(val editor: GraphEditor, style: Style) : TimelinePanel(style.getChild("deep")) {

    var draggedKeyframe: Keyframe<*>? = null
    var draggedChannel = 0

    var lastUnitScale = 1f

    // style
    var dotSize = style.getSize("dotSize", 8)

    val selectedKeyframes = HashSet<Keyframe<*>>()

    var isSelecting = false
    val select0 = Vector2f()

    val activeChannels: Int
        get() {
            val comp = selectedProperties.firstOrNull()
            val type = comp?.type
            return if (type != null) {
                var sum = 0
                for (i in 0 until type.numComponents) {
                    sum += editor.channelMasks[i].value.toInt(1 shl i)
                }
                if (sum == 0) -1 else sum
            } else -1
        }

    override fun getVisualState() = Triple(super.getVisualState(), centralValue, dvHalfHeight)

    fun normValue01(value: Float) = 0.5 - (value - centralValue) / dvHalfHeight * 0.5

    fun getValueAt(my: Float) = centralValue - dvHalfHeight * normAxis11(my, y, height)
    fun getYAt(value: Float) = y + height * normValue01(value)

    private fun getValueString(value: Float, step: Float) =
        getValueString(abs(value), step, if (value < 0) '-' else '+')

    private fun getValueString(value: Float, step: Float, sign: Char): String {
        val int = value.toInt()
        if (step >= 1f) return "$sign$int"
        val float = value % 1
        if (step >= 0.1f) return "$sign$int.${(float * 10).roundToInt()}"
        if (step >= 0.01f) return "$sign$int.${get0XString((float * 100).roundToInt())}"
        return "$sign$int.${get00XString((float * 1000).roundToInt())}"
    }

    private fun getValueStep(value: Float): Float {
        return valueFractions.minByOrNull { abs(it - value) }!!
    }

    override val canDrawOverBorders get() = true

    @Suppress("unused_parameter")
    private fun drawValueAxis(x0: Int, y0: Int, x1: Int, y1: Int) {

        val font = DrawTexts.monospaceFont
        val fontHeight = font.size
        val yOffset = fontHeight.toInt() / 2

        val minValue = centralValue - dvHalfHeight
        val maxValue = centralValue + dvHalfHeight

        val deltaValue = 2 * dvHalfHeight

        val textLines = clamp(height * 0.7f / fontHeight, 2f, 5f)
        val valueStep = getValueStep((deltaValue / textLines).toFloat())

        val minStepIndex = (minValue / valueStep).toInt() - 1
        val maxStepIndex = (maxValue / valueStep).toInt() + 1

        val fontColor = fontColor
        val backgroundColor = backgroundColor and 0xffffff // transparent background

        for (stepIndex in maxStepIndex downTo minStepIndex) {

            val value = stepIndex * valueStep
            val y = getYAt(value).roundToInt()

            val text = getValueString(value, valueStep)
            val width = font.sampleWidth * text.length

            if (y + yOffset >= y0 && y - yOffset < y1) {
                if (y in y0 until y1) {
                    drawRect(x + width + 2, y, this.width - width - 2, 1, fontColor and 0x3fffffff)
                }
                DrawTexts.drawSimpleTextCharByChar(
                    x + 2, y - yOffset, 0,
                    text, fontColor, backgroundColor, AxisAlignment.MIN
                )
            }

        }

    }

    private fun autoResize(property: AnimatedProperty<*>) {

        val t0 = centralTime - dtHalfLength
        val t1 = centralTime + dtHalfLength

        var minValue = Float.POSITIVE_INFINITY
        var maxValue = Float.NEGATIVE_INFINITY

        fun add(value: Float) {
            minValue = min(value, minValue)
            maxValue = max(value, maxValue)
        }

        fun add(value: Any?) {
            value ?: return
            for (i in 0 until property.type.numComponents) {
                add(getFloat(value, i, 0f))
            }
        }
        if (property.isAnimated) {
            add(property[t0])
            add(property[t1])
            for (kf in property.keyframes) {
                if (kf.time in t0..t1) {
                    add(kf.value)
                }
            }
        } else add(property.defaultValue)

        centralValue = (maxValue + minValue) * 0.5
        dvHalfHeight = max(property.type.unitScale * 0.5, (maxValue - minValue) * 0.5) * 1.2

    }

    var lastProperty: Any? = null
    override fun onDraw(x0: Int, y0: Int, x1: Int, y1: Int) {

        val dotSize = dotSize

        drawnStrings.clear()

        drawBackground(x0, y0, x1, y1)
        drawTypeInCorner("Keyframe Editor", fontColor)

        val property = selectedProperties.firstOrNull()
        val targetUnitScale = property?.type?.unitScale ?: lastUnitScale
        if (lastUnitScale != targetUnitScale) {
            val scale = targetUnitScale / lastUnitScale
            centralValue *= scale
            dvHalfHeight *= scale
            lastUnitScale = targetUnitScale
            clampValues()
        }

        drawCurrentTime()

        drawTimeAxis(x0, y0, x1, y1, true)

        updateLocalTime()

        if (property == null) {
            selectionStrength = 1f
            return
        }

        if (property !== lastProperty) {
            selectionStrength = 1f
            lastProperty = property
            autoResize(property)
        }

        // only required, if there are values
        drawValueAxis(x0, y0, x1, y1)

        val type = property.type
        val halfSize = dotSize / 2

        val blueish = 0x7799ff or black
        val red = 0xfa5157 or black
        val green = 0x1fa34a or black
        val blue = 0x4a8cf8 or black

        val channelCount = property.type.numComponents
        val valueColors = intArrayOf(
            if (type.defaultValue is Float || type.defaultValue is Double)
                blueish
            else if (channelCount == 1)
                white
            else red,
            green, blue, white
        )

        fun drawDot(x: Int, y: Int, color: Int, willBeSelected: Boolean) {
            if (willBeSelected) {// draw outline, if point is selected
                drawRect(x - halfSize - 1, clamp(y - halfSize - 1, y0 - 1, y1), dotSize + 2, dotSize + 2, -1)
            }
            drawRect(x - halfSize, clamp(y - halfSize, y0 - 1, y1), dotSize, dotSize, color)
        }

        val window = window!!
        val minSelectX = min(window.mouseDownX, window.mouseX).toInt()
        val maxSelectX = max(window.mouseDownX, window.mouseX).toInt()
        val minSelectY = min(window.mouseDownY, window.mouseY).toInt()
        val maxSelectY = max(window.mouseDownY, window.mouseY).toInt()
        val selectX = minSelectX - halfSize..maxSelectX + halfSize
        val selectY = minSelectY - halfSize..maxSelectY + halfSize
        val activeChannels = activeChannels

        // draw selection box
        if (isSelecting) {
            // draw border
            drawBorder(minSelectX, minSelectY, maxSelectX * minSelectX, maxSelectY - minSelectY, black, 1)
            // draw inner
            if (minSelectX + 1 < maxSelectX && minSelectY + 1 < maxSelectY) {
                drawRect(
                    minSelectX + 1, minSelectY + 1,
                    maxSelectX - minSelectX - 2, maxSelectY - minSelectY - 2,
                    black and 0x77000000
                )
            }
        }

        // draw all data points
        val yValues = IntArray(type.numComponents)
        val prevYValues = IntArray(type.numComponents)
        val kfs = property.keyframes

        // draw colored stripes to show the color...
        if (property.type == NumberType.COLOR || property.type == NumberType.COLOR3) {
            drawColoredStripes(x0, x1, y0, y1, property)
        }

        // draw all values
        if (kfs.isNotEmpty() || property.drivers.any { it != null }) {
            val backgroundColor = backgroundColor.withAlpha(0)
            val batch = DrawCurves.lineBatch.start()
            for (i in valueColors.indices) {
                val alpha = if (i.isChannelActive(activeChannels)) 0.3f else 0.1f
                valueColors[i] = valueColors[i].withAlpha(alpha)
            }
            var v0 = property[global2Kf(getTimeAt(x0 + 0f))]
            for (x in x0 until x1) {
                val xf0 = x.toFloat()
                val xf1 = xf0 + 1f
                val v1 = property[global2Kf(getTimeAt(xf1))]
                for (i in 0 until channelCount) {
                    val vy0 = getYAt(getFloat(v0, i, 0f)).toFloat()
                    val vy1 = getYAt(getFloat(v1, i, 0f)).toFloat()
                    // skip it, if we don't need it
                    if (min(vy0, vy1) < y0 || max(vy0, vy1) > y1) continue
                    DrawCurves.drawLine(
                        xf0, vy0, xf1, vy1, 0.5f,
                        valueColors[i],
                        backgroundColor,
                        false
                    )
                }
                v0 = v1
            }
            for (i in valueColors.indices) {
                valueColors[i] = valueColors[i].withAlpha(1f)
            }
            DrawCurves.lineBatch.finish(batch)
        } else {
            val value = property.defaultValue
            for (i in 0 until channelCount) {
                val y = getYAt(getFloat(value, i, 0f)).toFloat()
                drawSmoothLine(
                    x0.toFloat(), y, x1.toFloat(), y,
                    this.x, this.y, this.width, this.height,
                    valueColors[i],
                    0.5f
                )
            }
        }

        // set channel alpha for keyframes depending on whether their channel is active
        for (i in valueColors.indices) {
            valueColors[i] = valueColors[i]
                .withAlpha(if (i.isChannelActive(activeChannels)) 1f else 0.5f)
        }

        // draw keyframes
        for ((j, kf) in kfs.withIndex()) {

            val tGlobal = kf2Global(kf.time)
            val keyValue = kf.value
            val x = getXAt(tGlobal).roundToInt()

            if (j > 0) {
                System.arraycopy(yValues, 0, prevYValues, 0, yValues.size)
            }

            for (i in 0 until channelCount) {
                val value = getFloat(keyValue, i, 0f)
                yValues[i] = getYAt(value).roundToInt()
            }

            var willBeSelected = kf in selectedKeyframes
            if (!willBeSelected && isSelecting && x in selectX) {
                for (i in 0 until channelCount) {
                    if (yValues[i] in selectY && i.isChannelActive(activeChannels)) {
                        willBeSelected = true
                        break
                    }
                }
            }

            for (i in 0 until channelCount) {
                drawDot(
                    x, yValues[i], valueColors[i],
                    willBeSelected
                )
            }
        }

        // only draw if we have something to animate
        drawBorder(x, y, width, height, selectionColor.withAlpha(selectionStrength), 1)
        if (!property.isAnimated) {
            if (selectionStrength > 0.01f) invalidateDrawing()
            selectionStrength *= dtTo10(IsSelectedWrapper.decaySpeed * Time.deltaTime).toFloat()
        }
    }

    var selectionColor = IsSelectedWrapper.getSelectionColor(style)
    var selectionStrength = 0f

    private fun drawColoredStripes(
        x0: Int, x1: Int, y0: Int, y1: Int,
        property: AnimatedProperty<*>
    ) {

        val dotSize = dotSize
        val width = dotSize
        val halfWidth = (width + 1) / 2
        val kfs = property.keyframes
        val stripeMultiplier = 0.33f // just to make it calmer
        val tiling = Vector4f(1f, (y1 - y0).toFloat() * stripeMultiplier / dotSize, 0f, 0f)
        val h = y1 - y0

        synchronized(property) {
            for (kf in kfs) {
                val tGlobal = kf2Global(kf.time)
                val x = getXAt(tGlobal).roundToInt() - halfWidth
                if (x < x1 || x + width >= x0) {// visible
                    val colorVector =
                        if (property.type == NumberType.COLOR3)
                            Vector4f(kf.value as Vector3f, 1f)
                        else kf.value as Vector4f
                    val color = colorVector.toARGB()
                    val color2 = color.mulAlpha(0.25f)
                    if (h > dotSize * 4) {
                        val border = dotSize
                        drawRect(x, y0, width, border, color2)
                        drawTexture(x, y0 + border, width, h - border * 2, colorShowTexture, color, tiling)
                        drawRect(x, y1 - border, width, border, color2)
                    } else {
                        drawTexture(x, y0, width, h, colorShowTexture, color, tiling)
                    }
                }
            }
        }

    }

    // todo draw curve of animation-drivers :)
    // todo input (animated values) and output (calculated values)?

    fun Int.isChannelActive(activeChannels: Int) = activeChannels.hasFlag(1 shl this)

    fun getKeyframeAt(x: Float, y: Float): Pair<Keyframe<*>, Int>? {
        val selectedProperty = selectedProperties.firstOrNull()
        val property = selectedProperty ?: return null
        var bestDragged: Keyframe<*>? = null
        var bestChannel = 0
        val maxMargin = dotSize * 2.0 / 3.0 + 1.0
        var bestDistance = maxMargin
        val activeChannels = activeChannels
        for (kf in property.keyframes) {
            val globalT = mix(0.0, 1.0, kf2Global(kf.time))
            val dx = x - getXAt(globalT)
            if (abs(dx) < maxMargin) {
                for (channel in 0 until property.type.numComponents) {
                    if (channel.isChannelActive(activeChannels)) {
                        val dy = y - getYAt(kf.getChannelAsFloat(channel))
                        if (abs(dy) < maxMargin) {
                            val distance = length(dx, dy)
                            if (distance < bestDistance) {
                                bestDragged = kf
                                bestChannel = channel
                                bestDistance = distance
                            }
                        }
                    }
                }
            }
        }
        return bestDragged?.to(bestChannel)
    }

    // todo right-click option to remove linear sections from keyframe panel;
    // todo right-click option to thin out sections from keyframe panel;
    // done right-click option to select by specific channel only (e.g. to rect-select all y over 0.5);, kind of
    // todo scale a group of selected keyframes
    // todo move a group of selected keyframes
    // todo select full keyframes, or partial keyframes?
    private fun getAllKeyframes(minX: Float, maxX: Float, minY: Float, maxY: Float): List<Keyframe<*>> {
        if (minX > maxX || minY > maxY) return getAllKeyframes(
            min(minX, maxX),
            max(minX, maxX),
            min(minY, maxY),
            max(minY, maxY)
        )
        val halfSize = dotSize / 2
        val property = selectedProperties.firstOrNull() ?: return emptyList()
        val keyframes = ArrayList<Keyframe<*>>()
        val activeChannels = activeChannels
        keyframes@ for (kf in property.keyframes) {
            val globalT = mix(0.0, 1.0, kf2Global(kf.time))
            if (getXAt(globalT) in minX - halfSize..maxX + halfSize) {
                for (channel in 0 until property.type.numComponents) {
                    if (channel.isChannelActive(activeChannels)) {
                        if (getYAt(kf.getChannelAsFloat(channel)) in minY - halfSize..maxY + halfSize) {
                            keyframes += kf
                            continue@keyframes
                        }
                    }
                }
            }
        }
        return keyframes
    }

    override fun onKeyDown(x: Float, y: Float, key: Key) {
        // find the dragged element
        if (!isHovered || (key != Key.BUTTON_LEFT && key != Key.BUTTON_RIGHT)) {
            super.onKeyDown(x, y, key)
            return
        }
        invalidateDrawing()
        val atCursor = getKeyframeAt(x, y)
        if (atCursor != null && selectedKeyframes.size > 1 && atCursor.first in selectedKeyframes) {
            draggedKeyframe = atCursor.first
            draggedChannel = activeChannels
        } else {
            draggedKeyframe = null
            if (key == Key.BUTTON_LEFT) {
                isSelecting = isShiftDown
                if (!isSelecting) {
                    selectedKeyframes.clear()
                }
                val keyframeChannel = getKeyframeAt(x, y)
                if (keyframeChannel != null) {
                    val (keyframe, channel) = keyframeChannel
                    draggedKeyframe = keyframe
                    draggedChannel = channel
                    selectedKeyframes.add(keyframe) // was not found -> add it
                } else {
                    select0.x = x
                    select0.y = y
                }
            }
        }
        invalidateDrawing()
    }

    // todo always show the other properties, too???
    // todo maybe add a list of all animated properties?
    override fun onKeyUp(x: Float, y: Float, key: Key) {
        if (key == Key.BUTTON_LEFT || key == Key.BUTTON_RIGHT) {
            draggedKeyframe = null
            if (isSelecting) {
                // add all keyframes in that area
                selectedKeyframes += getAllKeyframes(select0.x, x, select0.y, y)
                isSelecting = false
            }
            invalidateDrawing()
        } else super.onKeyUp(x, y, key)
    }

    override fun onDeleteKey(x: Float, y: Float) {
        RemsStudio.largeChange("Deleted Keyframes") {
            val selectedProperty = selectedProperties.firstOrNull()
            for (kf in selectedKeyframes) {
                selectedProperty?.remove(kf)
            }
            if (selectedProperty == null) {
                selectedKeyframes.clear()
            }
            selectedProperty?.checkIsAnimated()
        }
    }

    fun moveUp(sign: Float) {
        val delta = sign * dvHalfHeight * movementSpeed / height
        centralValue += delta
        clampTime()
    }

    override fun onGotAction(x: Float, y: Float, dx: Float, dy: Float, action: String, isContinuous: Boolean): Boolean {
        when (action) {
            "MoveUp" -> moveUp(1f)
            "MoveDown" -> moveUp(-1f)
            "StartScale" -> {
                val sf = selectedKeyframes
                when (sf.size) {
                    0 -> LOGGER.warn("You need to select keyframes first!")
                    1 -> LOGGER.warn("You need to select at least two keyframes!")
                    else -> {
                        // todo it would be nice if we had a live preview
                        askNumber(windowStack, NameDesc("Scale Time"), 1.0, NameDesc("Scale")) { scale ->
                            if (scale != 1.0 && scale.isFinite()) {
                                val avg = selectedKeyframes.sumOf { it.time } / selectedKeyframes.size
                                RemsStudio.largeChange("Scale keyframes by $scale") {
                                    for (it in selectedKeyframes) {
                                        it.time = (it.time - avg) * scale + avg
                                    }
                                    val selectedProperty = selectedProperties.firstOrNull()
                                    selectedProperty?.sort()
                                }
                            }
                        }
                    }
                }
            }

            else -> return super.onGotAction(x, y, dx, dy, action, isContinuous)
        }
        return true
    }

    private val draggingRemainder = WeakHashMap<Keyframe<*>, Double>()
    private fun snap(time: Double, snapping: Double, offset: Double): Double {
        return round((time - offset) * snapping) / snapping + offset
    }

    fun incTime(keyframe: Keyframe<*>, deltaTime: Double, shouldSnap: Boolean) {
        val snapping = RemsStudio.timelineSnapping
        val offset = RemsStudio.timelineSnappingOffset
        if (snapping > 0.0 && shouldSnap) {
            // is bpm global? yes
            // apply snapping, if possible
            val oldTime = kf2Global(keyframe.time)
            val newTime = oldTime + kf2Global(deltaTime) - kf2Global(0.0) + (draggingRemainder[keyframe] ?: 0.0)
            val snappedTime = snap(newTime, snapping, offset)
            draggingRemainder[keyframe] = snappedTime - newTime
            val localTime = global2Kf(snappedTime)
            keyframe.time = localTime
        } else {
            keyframe.time += deltaTime
        }
    }

    fun shouldSnap(time: Double): Boolean {
        // only apply for small deltas
        //  - find the closest bpm position
        //  - compare them
        val snapping = RemsStudio.timelineSnapping
        val offset = RemsStudio.timelineSnappingOffset
        val radius = RemsStudio.timelineSnappingRadius
        if (snapping <= 0.0) return false
        val delta = snap(time, snapping, offset) - time
        return abs(getXAt(delta) - getXAt(0.0)) < radius // snap if within +/- radius px
    }

    override fun onMouseMoved(x: Float, y: Float, dx: Float, dy: Float) {
        val draggedKeyframe = draggedKeyframe
        val selectedProperty = selectedProperties.firstOrNull()
        if (isSelecting) {
            // select new elements, update the selected keyframes?
            invalidateDrawing()
        } else if (draggedKeyframe != null && selectedProperty != null) {
            // dragging
            val time = getTimeAt(x)
            // if there are multiples selected, allow them to be moved via shift key
            val moveValues = isShiftDown || selectedKeyframes.size < 2
            val property = selectedProperties.firstOrNull()
            RemsStudio.incrementalChange("Dragging keyframe") {
                val timeHere = global2Kf(time) // global -> local
                val deltaTime = timeHere - draggedKeyframe.time
                val shouldSnap = shouldSnap(time)
                for (keyframe in selectedKeyframes) {
                    incTime(keyframe, deltaTime, shouldSnap)
                }
                editorTime = kf2Global(draggedKeyframe.time)
                updateAudio()
                if (moveValues) {
                    if (when (draggedKeyframe.value) {
                            is Number,
                            is Vector2f, is Vector3f, is Vector4f,
                            is Vector2d, is Vector3d, is Vector4d,
                            is Quaternionf, is Quaterniond -> true
                            else -> false
                        }
                    ) {
                        if (selectedKeyframes.size == 1) {
                            val newValue = getValueAt(y).toFloat()
                            draggedKeyframe.setValue(draggedChannel, newValue, selectedProperty.type)
                        } else if (property != null) {
                            val dv = (getValueAt(dy) - getValueAt(0f)).toFloat()
                            for (ch in 0 until property.type.numComponents) {
                                if (draggedChannel.hasFlag(1 shl ch)) {
                                    for (kf in selectedKeyframes) {
                                        val oldValue = getFloat(kf.value, ch, 0f)
                                        val newValue = oldValue + dv
                                        kf.setValue(ch, newValue, selectedProperty.type)
                                    }
                                }
                            }
                        }
                    }
                }
                selectedProperty.sort()
            }
            invalidateDrawing()
        } else {
            if (shouldScrub()) {
                // scrubbing
                editorTime = getTimeAt(x)
            } else if (shouldMove()) {
                // move left/right/up/down
                centralTime -= dx * dtHalfLength / (width / 2f)
                centralValue += dy * dvHalfHeight / (height / 2f)
                clampTime()
                clampValues()
            }
            invalidateDrawing()
        }
    }

    override fun onDoubleClick(x: Float, y: Float, button: Key) {
        val selectedProperty = selectedProperties.firstOrNull()
        if (selectedProperty != null) {
            val time = global2Kf(getTimeAt(x))
            RemsStudio.largeChange("Created keyframe at ${time}s") {
                selectedProperty.isAnimated = true
                selectedProperty.addKeyframe(time, selectedProperty[time], keyframeSnappingDt)
                selectedProperty.checkIsAnimated()
            }
        } else LOGGER.info("Please select a property first!")
    }

    fun clampValues() {
        dvHalfHeight = clamp(dvHalfHeight, 0.001 * lastUnitScale, 1000.0 * lastUnitScale)
    }

    override fun onPaste(x: Float, y: Float, data: String, type: String) {
        // paste keyframes
        // done convert the values, if required
        // move them? :)
        // paste float/vector values at the mouse position?
        try {
            val time0 = getTimeAt(x)
            val selectedProperty = selectedProperties.firstOrNull()
            val target = selectedProperty ?: return super.onPaste(x, y, data, type)
            val targetType = target.type
            val parsedKeyframes = JsonStringReader.read(data, workspace, true)
                .filterIsInstance2(Keyframe::class)
            if (parsedKeyframes.isNotEmpty()) {
                RemsStudio.largeChange("Pasted Keyframes") {
                    for (kf in parsedKeyframes) {
                        val castValue = targetType.acceptOrNull(kf.value)
                        if (castValue != null) {
                            target.addKeyframe(kf.time + time0, castValue)
                        } else LOGGER.warn("$targetType doesn't accept ${kf.value}")
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            super.onPaste(x, y, data, type)
        }
    }

    override fun onCopyRequested(x: Float, y: Float): String {
        // copy keyframes
        // left anker or center? left for now
        val time0 = selectedKeyframes.minByOrNull { it.time }?.time ?: 0.0
        return JsonStringWriter.toText(
            selectedKeyframes
                .map { Keyframe(it.time - time0, it.value) }
                .toList(),
            InvalidRef
        )
    }

    override fun onMouseWheel(x: Float, y: Float, dx: Float, dy: Float, byMouse: Boolean) {
        val selectedProperty = selectedProperties.firstOrNull()
        if (selectedProperty == null || isShiftDown) {
            super.onMouseWheel(x, y, dx, dy, byMouse)
        } else {
            val scale = pow(1.05f, dx)
            dvHalfHeight *= scale
            clampValues()
            if (dy != 0f) {
                super.onMouseWheel(x, y, 0f, dy, byMouse)
            }
        }
    }

    override fun onSelectAll(x: Float, y: Float) {
        val selectedProperty = selectedProperties.firstOrNull()
        val kf = selectedProperty?.keyframes
        if (kf != null) {
            selectedKeyframes.clear()
            selectedKeyframes.addAll(kf)
        }
        invalidateDrawing()
    }

    override fun onMouseClicked(x: Float, y: Float, button: Key, long: Boolean) {
        when (button) {
            Key.BUTTON_RIGHT -> {
                if (selectedKeyframes.isEmpty()) {
                    super.onMouseClicked(x, y, button, long)
                } else {
                    openMenu(windowStack,
                        NameDesc("Interpolation", "", "ui.graphEditor.interpolation.title"),
                        Interpolation.entries.map { mode ->
                            MenuOption(NameDesc(mode.displayName, mode.description, "")) {
                                RemsStudio.incrementalChange("Change interpolation type") {
                                    for (kf in selectedKeyframes) {
                                        kf.interpolation = mode
                                    }
                                    invalidateDrawing()
                                }
                            }
                        })
                }
            }
            else -> super.onMouseClicked(x, y, button, long)
        }
    }

    override val className get() = "GraphEditorBody"

    companion object {
        private val LOGGER = LogManager.getLogger(GraphEditorBody::class)
        val valueFractions = listOf(
            0.1f, 0.2f, 0.5f, 1f,
            2f, 5f, 10f, 15f, 30f, 45f,
            90f, 120f, 180f, 360f, 720f
        )

        fun shouldScrub(): Boolean {
            return (Key.BUTTON_LEFT in mouseKeysDown || Key.BUTTON_RIGHT in mouseKeysDown) &&
                    ((isShiftDown || isControlDown) == (Key.BUTTON_LEFT in mouseKeysDown)) && isPaused
        }

        fun shouldMove(): Boolean {
            return (Key.BUTTON_LEFT in mouseKeysDown || Key.BUTTON_RIGHT in mouseKeysDown) && !shouldScrub()
        }
    }

}