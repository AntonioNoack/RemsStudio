package me.anno.remsstudio.ui

import me.anno.animation.Type
import me.anno.gpu.blending.BlendMode
import me.anno.gpu.blending.BlendMode.Companion.blendModes
import me.anno.io.files.FileReference
import me.anno.remsstudio.RemsStudio
import me.anno.remsstudio.animation.AnimatedProperty
import me.anno.remsstudio.objects.Transform
import me.anno.remsstudio.ui.input.ColorInputV2
import me.anno.remsstudio.ui.input.FloatInputV2
import me.anno.remsstudio.ui.input.FloatVectorInputV2
import me.anno.remsstudio.ui.input.IntInputV2
import me.anno.studio.Inspectable
import me.anno.ui.Panel
import me.anno.ui.Style
import me.anno.ui.base.components.AxisAlignment
import me.anno.ui.input.*
import me.anno.utils.Color.toHexColor
import me.anno.utils.Color.toVecRGBA
import me.anno.utils.structures.ValueWithDefault
import me.anno.utils.structures.ValueWithDefaultFunc
import org.joml.Quaternionf
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f

object ComponentUIV2 {

    /**
     * creates a panel with the correct input for the type, and sets the default values:
     * title, tool tip text, type, start value
     * callback is used to adjust the value
     * */
    @Suppress("unchecked_cast") // all casts are checked in all known use-cases ;)
    fun <V> vi(
        inspected: List<Inspectable>,
        self: Transform,
        title: String, ttt: String,
        type: Type?, value: V,
        style: Style, setValue: (V) -> Unit
    ): Panel {
        val t = inspected.filterIsInstance<Transform>()
        return when (value) {
            is Boolean -> BooleanInput(title, value, type?.defaultValue as? Boolean ?: false, style)
                .setChangeListener {
                    RemsStudio.largeChange("Set $title to $it") {
                        setValue(it as V)
                    }
                }
                .setIsSelectedListener { self.show(t, null) }
                .setTooltip(ttt)
            is Int -> IntInput(title, title, value, type ?: Type.INT, style)
                .setChangeListener {
                    RemsStudio.incrementalChange("Set $title to $it", title) {
                        setValue(it.toInt() as V)
                    }
                }
                .setIsSelectedListener { self.show(t, null) }
                .setTooltip(ttt)
            is Long -> IntInput(title, title, value, type ?: Type.LONG, style)
                .setChangeListener {
                    RemsStudio.incrementalChange("Set $title to $it", title) {
                        setValue(it as V)
                    }
                }
                .setIsSelectedListener { self.show(t, null) }
                .setTooltip(ttt)
            is Float -> FloatInput(title, title, value, type ?: Type.FLOAT, style)
                .setChangeListener {
                    RemsStudio.incrementalChange("Set $title to $it", title) {
                        setValue(it.toFloat() as V)
                    }
                }
                .setIsSelectedListener { self.show(t, null) }
                .setTooltip(ttt)
            is Double -> FloatInput(title, title, value, type ?: Type.DOUBLE, style)
                .setChangeListener {
                    RemsStudio.incrementalChange("Set $title to $it", title) {
                        setValue(it as V)
                    }
                }
                .setIsSelectedListener { self.show(t, null) }
                .setTooltip(ttt)
            is Vector2f -> FloatVectorInput(title, title, value, type ?: Type.VEC2, style)
                .addChangeListener { x, y, _, _, _ ->
                    RemsStudio.incrementalChange("Set $title to ($x,$y)", title) {
                        setValue(Vector2f(x.toFloat(), y.toFloat()) as V)
                    }
                }
                .setIsSelectedListener { self.show(t, null) }
                .setTooltip(ttt)
            is Vector3f ->
                if (type == Type.COLOR3) {
                    ColorInput(style, title, title, Vector4f(value, 1f), false)
                        .setChangeListener { r, g, b, _, _ ->
                            RemsStudio.incrementalChange("Set $title to ${Vector3f(r, g, b).toHexColor()}", title) {
                                setValue(Vector3f(r, g, b) as V)
                            }
                        }
                        .setIsSelectedListener { self.show(t, null) }
                        .setTooltip(ttt)
                } else {
                    FloatVectorInput(title, title, value, type ?: Type.VEC3, style)
                        .addChangeListener { x, y, z, _, _ ->
                            RemsStudio.incrementalChange("Set $title to ($x,$y,$z)", title) {
                                setValue(Vector3f(x.toFloat(), y.toFloat(), z.toFloat()) as V)
                            }
                        }
                        .setIsSelectedListener { self.show(t, null) }
                        .setTooltip(ttt)
                }
            is Vector4f -> {
                if (type == null || type == Type.COLOR) {
                    ColorInput(style, title, title, value, true)
                        .setChangeListener { r, g, b, a, _ ->
                            RemsStudio.incrementalChange("Set $title to ${Vector4f(r, g, b, a).toHexColor()}", title) {
                                setValue(Vector4f(r, g, b, a) as V)
                            }
                        }
                        .setIsSelectedListener { self.show(t, null) }
                        .setTooltip(ttt)
                } else {
                    FloatVectorInput(title, title, value, type, style)
                        .addChangeListener { x, y, z, w, _ ->
                            RemsStudio.incrementalChange("Set $title to ($x,$y,$z,$w)", title) {
                                setValue(Vector4f(x.toFloat(), y.toFloat(), z.toFloat(), w.toFloat()) as V)
                            }
                        }
                        .setIsSelectedListener { self.show(t, null) }
                        .setTooltip(ttt)
                }
            }
            is Quaternionf -> FloatVectorInput(title, title, value, type ?: Type.QUATERNION, style)
                .addChangeListener { x, y, z, w, _ ->
                    RemsStudio.incrementalChange(title) {
                        setValue(Quaternionf(x, y, z, w) as V)
                    }
                }
                .setIsSelectedListener { self.show(t, null) }
                .setTooltip(ttt)
            is String -> TextInputML(title, value, style)
                .addChangeListener {
                    RemsStudio.incrementalChange("Set $title to \"$it\"", title) {
                        setValue(it as V)
                    }
                }
                .setIsSelectedListener { self.show(t, null) }
                .setTooltip(ttt)
            is FileReference -> FileInput(title, style, value, emptyList())
                .setChangeListener {
                    RemsStudio.incrementalChange("Set $title to \"$it\"", title) {
                        setValue(it as V)
                    }
                }
                .setIsSelectedListener { self.show(t, null) }
                .setTooltip(ttt)
            is BlendMode -> {
                val values = blendModes.values
                val valueNames = values.map { it to it.naming }
                EnumInput(
                    title, true, valueNames.first { it.first == value }.second.name,
                    valueNames.map { it.second }, style
                )
                    .setChangeListener { name, index, _ ->
                        RemsStudio.incrementalChange("Set $title to $name", title) {
                            setValue(valueNames[index].first as V)
                        }
                    }
                    .setIsSelectedListener { self.show(t, null) }
                    .setTooltip(ttt)
            }
            is Enum<*> -> {
                val values = EnumInput.getEnumConstants(value.javaClass)
                EnumInput.createInput(title, value, style)
                    .setChangeListener { name, index, _ ->
                        RemsStudio.incrementalChange("Set $title to $name") {
                            setValue(values[index] as V)
                        }
                    }
                    .setIsSelectedListener { self.show(t, null) }
                    .setTooltip(ttt)
            }
            is ValueWithDefaultFunc<*>, is ValueWithDefault<*> -> throw IllegalArgumentException("Must pass value, not ValueWithDefault(Func)!")
            else -> throw RuntimeException("Type $value not yet implemented!")
        }
    }

    private fun toColor(v: Any?): Vector4f {
        return when (v) {
            is Vector4f -> v
            is Vector3f -> Vector4f(v, 1f)
            is Int -> v.toVecRGBA()
            is Long -> v.toInt().toVecRGBA()
            else -> Vector4f(0f, 0f, 0f, 1f)
        }
    }

    /**
     * creates a panel with the correct input for the type, and sets the default values:
     * title, tool tip text, type, start value
     * modifies the AnimatedProperty-Object, so no callback is needed
     * */
    fun vi(
        self: Transform,
        title: String,
        ttt: String, values: AnimatedProperty<*>, style: Style
    ): Panel {
        val time = self.lastLocalTime
        val sl = { self.show(listOf(self), listOf(values)) }
        val panel = when (val value = values[time]) {
            is Int -> IntInputV2(title, title, values, time, style)
                .setChangeListener {
                    RemsStudio.incrementalChange("Set $title to $it", title) {
                        self.putValue(values, it.toInt(), false)
                    }
                }
                .setIsSelectedListener(sl)
                .setTooltip(ttt)
            is Long -> IntInputV2(title, title, values, time, style)
                .setChangeListener {
                    RemsStudio.incrementalChange("Set $title to $it", title) {
                        self.putValue(values, it, false)
                    }
                }
                .setIsSelectedListener(sl)
                .setTooltip(ttt)
            is Float -> FloatInputV2(title, title, values, time, style)
                .setChangeListener {
                    RemsStudio.incrementalChange("Set $title to $it", title) {
                        self.putValue(values, it.toFloat(), false)
                    }
                }
                .setIsSelectedListener(sl)
                .setTooltip(ttt)
            is Double -> FloatInputV2(title, title, values, time, style)
                .setChangeListener {
                    RemsStudio.incrementalChange("Set $title to $it", title) {
                        self.putValue(values, it, false)
                    }
                }
                .setIsSelectedListener(sl)
                .setTooltip(ttt)
            is Vector2f -> FloatVectorInputV2(title, values, time, style)
                .addChangeListener { x, y, _, _, _ ->
                    RemsStudio.incrementalChange("Set $title to ($x,$y)", title) {
                        self.putValue(values, Vector2f(x.toFloat(), y.toFloat()), false)
                    }
                }
                .setIsSelectedListener(sl)
                .setTooltip(ttt)
            is Vector3f ->
                if (values.type == Type.COLOR3) {
                    ColorInputV2(style, title, title, Vector4f(value, 1f), false, values)
                        .setChangeListener { r, g, b, _, _ ->
                            RemsStudio.incrementalChange("Set $title to ${Vector3f(r, g, b).toHexColor()}", title) {
                                self.putValue(values, Vector3f(r, g, b), false)
                            }
                        }
                        .setResetListener { toColor(values.defaultValue) }
                        .setIsSelectedListener(sl)
                        .setTooltip(ttt)
                } else {
                    FloatVectorInputV2(title, values, time, style)
                        .addChangeListener { x, y, z, _, _ ->
                            RemsStudio.incrementalChange("Set $title to ($x,$y,$z)", title) {
                                self.putValue(values, Vector3f(x.toFloat(), y.toFloat(), z.toFloat()), false)
                            }
                        }
                        .setIsSelectedListener(sl)
                        .setTooltip(ttt)
                }
            is Vector4f -> {
                if (values.type == Type.COLOR) {
                    ColorInputV2(style, title, title, value, true, values)
                        .setChangeListener { r, g, b, a, _ ->
                            RemsStudio.incrementalChange("Set $title to ${Vector4f(r, g, b, a).toHexColor()}", title) {
                                self.putValue(values, Vector4f(r, g, b, a), false)
                            }
                        }
                        .setResetListener { toColor(values.defaultValue) }
                        .setIsSelectedListener(sl)
                        .setTooltip(ttt)
                } else {
                    FloatVectorInputV2(title, values, time, style)
                        .addChangeListener { x, y, z, w, _ ->
                            RemsStudio.incrementalChange("Set $title to ($x,$y,$z,$w)", title) {
                                self.putValue(
                                    values,
                                    Vector4f(x.toFloat(), y.toFloat(), z.toFloat(), w.toFloat()),
                                    false
                                )
                            }
                        }
                        .setIsSelectedListener(sl)
                        .setTooltip(ttt)
                }
            }
            is String -> TextInputML(title, value, style)
                .addChangeListener {
                    RemsStudio.incrementalChange("Set $title to $it") {
                        self.putValue(values, it, false)
                    }
                }
                .setIsSelectedListener(sl)
                .setTooltip(ttt)
            is Quaternionf -> FloatVectorInputV2(title, values, time, style)
                .addChangeListener { x, y, z, w, _ ->
                    RemsStudio.incrementalChange("Set $title to ($x,$y,$z,$w)", title) {
                        self.putValue(values, Quaternionf(x, y, z, w), false)
                    }
                }
                .setIsSelectedListener(sl)
                .setTooltip(ttt)
            else -> throw RuntimeException("Type $value not yet implemented!")
        }
        panel.alignmentX = AxisAlignment.FILL
        return panel
    }

    /**
     * creates a panel with the correct input for the type, and sets the default values:
     * title, tool tip text, type, start value
     * modifies the AnimatedProperty-Object, so no callback is needed
     * */
    fun vis(
        transforms: List<Transform>,
        title: String, ttt: String,
        values: List<AnimatedProperty<*>?>,
        style: Style
    ): Panel {
        val sampleValues = values[0]!!
        val self = transforms[0]
        val time = self.lastLocalTime
        val panel = when (val value = sampleValues[time]) {
            is Int -> IntInputV2(title, title, sampleValues, time, style)
                .setChangeListener {
                    RemsStudio.incrementalChange("Set $title to $it", title) {
                        val ii = it.toInt()
                        for (i in values.indices) {
                            transforms[i].putValue(values[i], ii, false)
                        }
                    }
                }
            is Long -> IntInputV2(title, title, sampleValues, time, style)
                .setChangeListener {
                    RemsStudio.incrementalChange("Set $title to $it", title) {
                        for (i in values.indices) {
                            transforms[i].putValue(values[i], it, false)
                        }
                    }
                }
            is Float -> FloatInputV2(title, title, sampleValues, time, style)
                .setChangeListener {
                    RemsStudio.incrementalChange("Set $title to $it", title) {
                        val ii = it.toFloat()
                        for (i in values.indices) {
                            transforms[i].putValue(values[i], ii, false)
                        }
                    }
                }
            is Double -> FloatInputV2(title, title, sampleValues, time, style)
                .setChangeListener {
                    RemsStudio.incrementalChange("Set $title to $it", title) {
                        for (i in values.indices) {
                            transforms[i].putValue(values[i], it, false)
                        }
                    }
                }
            is Vector2f -> FloatVectorInputV2(title, sampleValues, time, style)
                .addChangeListener { x, y, _, _, _ ->
                    RemsStudio.incrementalChange("Set $title to ($x,$y)", title) {
                        // multiple instances?
                        for (i in values.indices) {
                            transforms[i].putValue(values[i], Vector2f(x.toFloat(), y.toFloat()), false)
                        }
                    }
                }
            is Vector3f ->
                if (sampleValues.type == Type.COLOR3) {
                    ColorInputV2(style, title, title, Vector4f(value, 1f), false, sampleValues)
                        .setChangeListener { r, g, b, _, _ ->
                            RemsStudio.incrementalChange("Set $title to ${Vector3f(r, g, b).toHexColor()}", title) {
                                // multiple instances?
                                for (i in values.indices) {
                                    transforms[i].putValue(values[i], Vector3f(r, g, b), false)
                                }
                            }
                        }
                        .setResetListener { toColor(sampleValues.defaultValue) }
                } else {
                    FloatVectorInputV2(title, sampleValues, time, style)
                        .addChangeListener { x, y, z, _, _ ->
                            RemsStudio.incrementalChange("Set $title to ($x,$y,$z)", title) {
                                // multiple instances?
                                for (i in values.indices) {
                                    transforms[i].putValue(
                                        values[i],
                                        Vector3f(x.toFloat(), y.toFloat(), z.toFloat()),
                                        false
                                    )
                                }
                            }
                        }
                }
            is Vector4f -> {
                if (sampleValues.type == Type.COLOR) {
                    ColorInputV2(style, title, title, value, true, sampleValues)
                        .setChangeListener { r, g, b, a, _ ->
                            RemsStudio.incrementalChange("Set $title to ${Vector4f(r, g, b, a).toHexColor()}", title) {
                                // multiple instances?
                                for (i in values.indices) {
                                    transforms[i].putValue(values[i], Vector4f(r, g, b, a), false)
                                }
                            }
                        }
                        .setResetListener { toColor(sampleValues.defaultValue) }
                } else {
                    FloatVectorInputV2(title, sampleValues, time, style)
                        .addChangeListener { x, y, z, w, _ ->
                            RemsStudio.incrementalChange("Set $title to ($x,$y,$z,$w)", title) {
                                // multiple instances?
                                for (i in values.indices) {
                                    transforms[i].putValue(
                                        values[i],
                                        Vector4f(x.toFloat(), y.toFloat(), z.toFloat(), w.toFloat()),
                                        false
                                    )
                                }
                            }
                        }
                }
            }
            is String -> TextInputML(title, value, style)
                .addChangeListener {
                    RemsStudio.incrementalChange("Set $title to $it") {
                        for (i in values.indices) {
                            transforms[i].putValue(values[i], it, false)
                        }
                    }
                }
            is Quaternionf -> FloatVectorInputV2(title, sampleValues, time, style)
                .addChangeListener { x, y, z, w, _ ->
                    RemsStudio.incrementalChange("Set $title to ($x,$y,$z,$w)", title) {
                        for (i in values.indices) {
                            transforms[i].putValue(values[i], Quaternionf(x, y, z, w), false)
                        }
                    }
                }
            else -> throw RuntimeException("Type $value not yet implemented!")
        }

        panel.apply {
            val sl = { self.show(transforms, values) }
            when (this) {
                is NumberInput<*> -> setIsSelectedListener(sl)
                is FloatVectorInputV2 -> setIsSelectedListener(sl)
                is ColorInputV2 -> setIsSelectedListener(sl)
                is TextInputML -> setIsSelectedListener(sl)
            }
            setTooltip(ttt)
        }

        panel.alignmentX = AxisAlignment.FILL
        return panel
    }

}