package me.anno.remsstudio.ui.editor.cutting

import me.anno.maths.Maths.max
import me.anno.maths.Maths.mix
import me.anno.remsstudio.ui.editor.cutting.LayerView.Companion.minAlphaInt
import me.anno.remsstudio.ui.editor.cutting.LayerView.Companion.minDistSq
import me.anno.utils.Color.a
import me.anno.utils.Color.a01
import me.anno.utils.Color.b01
import me.anno.utils.Color.g01
import me.anno.utils.Color.r01
import me.anno.utils.Color.toARGB
import me.anno.utils.Color.toHexColor
import org.joml.Vector4f

class LayerViewGradient(
    val owner: Any?,
    val x0: Int, var x1: Int,
    val c0: Int, var c1: Int
) {

    override fun toString() = if(c0 == c1){
        "[$x0-$x1, ${c0.toHexColor()}]"
    } else {
        "[$x0-$x1, ${c0.toHexColor()} -> ${c1.toHexColor()}]"
    }

    constructor(owner: Any?, x0: Int, x1: Int, c0: Vector4f, c1: Vector4f) :
            this(owner, x0, x1, c0.toARGB(), c1.toARGB())

    // must be saved, so the gradient difference doesn't grow
    // if it's not saved, small errors can accumulate and become large
    // (I had this issue, and at first didn't know where it's coming from)
    private var firstC1 = c1
    private var firstX1 = x1

    var w = x1 - x0 + 1

    fun needsDrawn() = max(c0.a(), c1.a()) >= minAlphaInt

    fun isLinear(x3: Int, step: Int, c3: Vector4f): Boolean {
        // the total width is just one step
        // -> one stripe
        // -> can be assumed to be constant
        if (x1 < x0 + step) return true
        // calculate the would-be color values here in a linear case
        val f = (x3 - x0 + 1).toFloat() / (firstX1 - x0)
        val r0 = mix(c0.r01(), firstC1.r01(), f)
        val g0 = mix(c0.g01(), firstC1.g01(), f)
        val b0 = mix(c0.b01(), firstC1.b01(), f)
        val a0 = mix(c0.a01(), firstC1.a01(), f)
        // if (abs(a0 - c3.w) > 1e-4f) LOGGER.info("approx. $a0 from mix(${c0.w}, ${firstC1.w}, $f) = ($x3-$x0)/($firstX1-$x0) for ${c3.w}")
        // compare to the actual color
        val distSq = sq(c3.x - r0, c3.y - g0, c3.z - b0, c3.w - a0)
        // if the error is small enough, we can use this linear approximation
        return distSq < minDistSq
    }

    private fun sq(r: Float, g: Float, b: Float, a: Float) = r * r + g * g + b * b + a * a

    fun setEnd(x: Int, step: Int, c: Vector4f) =
        setEnd(x, step, c.toARGB())

    private fun setEnd(x: Int, step: Int, c: Int) {
        x1 = x
        c1 = c
        w = x - x0 + 1
        if (w < step || firstX1 == x0) {
            firstC1 = c
            firstX1 = x
        }
    }


}