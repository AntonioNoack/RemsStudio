package me.anno.remsstudio.ui.input.components

import me.anno.input.Key
import me.anno.remsstudio.RemsStudio
import me.anno.remsstudio.Selection
import me.anno.remsstudio.animation.AnimatedProperty
import me.anno.remsstudio.animation.drivers.AnimationDriver
import me.anno.ui.Style
import me.anno.ui.input.FloatInput
import me.anno.ui.input.IntInput
import me.anno.ui.input.NumberInput
import me.anno.ui.input.components.NumberInputComponent
import me.anno.utils.types.AnyToDouble

class NumberInputComponentV2(
    private val owningProperty: AnimatedProperty<*>,
    visibilityKey: String,
    style: Style
) : NumberInputComponent(visibilityKey, style) {

    init {
        setResetListener { AnyToDouble.getDouble(owningProperty.type.defaultValue, indexInProperty, 0.0).toString() }
    }

    val numberInput get() = parent as NumberInput<*>

    var lastTime = RemsStudio.editorTime

    val driver get() = owningProperty.drivers.getOrNull(indexInProperty)
    val hasDriver get() = driver != null

    override fun onKeyDown(x: Float, y: Float, key: Key) {
        if (key == Key.BUTTON_LEFT) {
            if (!hasDriver) {
                super.onKeyDown(x, y, key)
                numberInput.onKeyDown(x, y, key)
            }
        } else super.onKeyDown(x, y, key)
    }

    override fun onKeyUp(x: Float, y: Float, key: Key) {
        if (key == Key.BUTTON_LEFT) {
            if (!hasDriver) numberInput.onKeyUp(x, y, key)
        } else super.onKeyUp(x, y, key)
    }

    override fun onMouseMoved(x: Float, y: Float, dx: Float, dy: Float) {
        if (!hasDriver) numberInput.onMouseMoved(x, y, dx, dy)
        super.onMouseMoved(x, y, dx, dy)
    }

    override fun onDraw(x0: Int, y0: Int, x1: Int, y1: Int) {
        val editorTime = RemsStudio.editorTime
        if (lastTime != editorTime && owningProperty.isAnimated) {
            lastTime = editorTime
            val valueVec = owningProperty[editorTime]
            val value = AnyToDouble.getDouble(valueVec, indexInProperty, 0.0)
            when (val numberInput = numberInput) {
                is IntInput -> numberInput.setValue(value.toLong(), false)
                is FloatInput -> numberInput.setValue(value, false)
                else -> throw RuntimeException("Unknown number input type")
            }
        }
        val driver = driver
        if (driver != null) {
            val driverName = driver.getDisplayName()
            if (value != driverName) {
                setText(value, true)
            }
        }
        super.onDraw(x0, y0, x1, y1)
    }

    override fun onMouseClicked(x: Float, y: Float, button: Key, long: Boolean) {
        if (button != Key.BUTTON_LEFT || long) {
            val oldDriver = owningProperty.drivers[indexInProperty]
            AnimationDriver.openDriverSelectionMenu(windowStack, oldDriver) { driver ->
                RemsStudio.largeChange("Changed driver to ${driver?.className}") {
                    owningProperty.drivers[indexInProperty] = driver
                    if (driver != null) Selection.selectProperty(listOf(driver))
                    else {
                        setValue(
                            when (val numberInput = numberInput) {
                                is IntInput -> numberInput.stringify(numberInput.value)
                                is FloatInput -> numberInput.stringify(numberInput.value)
                                else -> throw RuntimeException()
                            }, true // todo notify?
                        )
                    }
                }
            }
        } else super.onMouseClicked(x, y, button, false)
    }

    override fun onEmpty(x: Float, y: Float) {
        if (hasDriver) {
            owningProperty.drivers[indexInProperty] = null
        } else super.onEmpty(x, y)
    }

}