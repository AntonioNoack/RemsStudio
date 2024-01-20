package me.anno.remsstudio.ui.input

import me.anno.engine.EngineBase.Companion.workspace
import me.anno.input.Key
import me.anno.io.json.saveable.JsonStringReader
import me.anno.remsstudio.RemsStudio
import me.anno.remsstudio.Selection
import me.anno.remsstudio.animation.AnimatedProperty
import me.anno.ui.Style
import me.anno.ui.input.FloatVectorInput
import me.anno.ui.input.NumberType
import org.apache.logging.log4j.LogManager
import org.joml.Quaternionf
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f

class FloatVectorInputV2(
    title: String,
    visibilityKey: String,
    type: NumberType,
    private val owningProperty: AnimatedProperty<*>,
    style: Style
) : FloatVectorInput(
    title, visibilityKey, type, style,
    { FloatInputV2(style, "", visibilityKey, type, owningProperty) }
) {

    companion object {
        private val LOGGER = LogManager.getLogger(FloatVectorInputV2::class)
    }

    constructor(title: String, visibilityKey: String, property: AnimatedProperty<*>, time: Double, style: Style) :
            this(title, visibilityKey, property.type, property, style) {
        when (val value = property[time]) {
            is Vector2f -> setValue(value, false)
            is Vector3f -> setValue(value, false)
            is Vector4f -> setValue(value, false)
            is Quaternionf -> setValue(value, false)
            else -> throw RuntimeException("Type $value not yet supported!")
        }
    }

    override fun onPaste(x: Float, y: Float, data: String, type: String) {
        pasteVector(data)
            ?: pasteScalar(data)
            ?: pasteColor(data)
            ?: pasteAnimated(data)
            ?: super.onPaste(x, y, data, type)
    }

    override fun onDraw(x0: Int, y0: Int, x1: Int, y1: Int) {
        val focused1 = titleView?.isInFocus == true
        if (RemsStudio.hideUnusedProperties) {
            val focused2 = focused1 || owningProperty in (Selection.selectedProperties ?: emptyList())
            valueList.isVisible = focused2
        }
        super.onDraw(x0, y0, x1, y1)
    }

    override fun onCopyRequested(x: Float, y: Float) = owningProperty.toString()

    private fun pasteAnimated(data: String): Unit? {
        return try {
            val editorTime = RemsStudio.editorTime
            val animProperty = JsonStringReader.read(data, workspace, true)
                .firstOrNull() as? AnimatedProperty<*>
            if (animProperty != null) {
                owningProperty.copyFrom(animProperty)
                when (val value = owningProperty[editorTime]) {
                    is Vector2f -> setValue(value, true)
                    is Vector3f -> setValue(value, true)
                    is Vector4f -> setValue(value, true)
                    is Quaternionf -> setValue(value, true)
                    else -> LOGGER.warn("Unknown pasted data type $value")
                }
            }
            Unit
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override fun onEmpty(x: Float, y: Float) {
        onEmpty2(owningProperty.defaultValue ?: type.defaultValue)
    }

    override fun clone(): FloatVectorInputV2 {
        val clone = FloatVectorInputV2(title, visibilityKey, type, owningProperty, style)
        copyInto(clone)
        return clone
    }

    override fun onKeyTyped(x: Float, y: Float, key: Key) {
        if (key == Key.KEY_ENTER || key == Key.KEY_KP_ENTER) return
        super.onKeyTyped(x, y, key)
    }

    override fun onEnterKey(x: Float, y: Float) {}

    override val className: String
        get() = "FloatVectorInputV2"

}