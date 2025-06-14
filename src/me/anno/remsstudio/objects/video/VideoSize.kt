package me.anno.remsstudio.objects.video

import me.anno.gpu.GFX
import me.anno.gpu.GFXState
import me.anno.maths.Maths.pow
import me.anno.remsstudio.RemsStudio.targetHeight
import me.anno.remsstudio.RemsStudio.targetWidth
import me.anno.remsstudio.Scene
import me.anno.utils.Clipping
import me.anno.utils.pooling.JomlPools
import org.joml.Matrix4f
import org.joml.Vector4f
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

object VideoSize {

    fun calculateZoomLevel(matrix: Matrix4f, w: Int, h: Int): Int? {

        // gl_Position = transform * vec4(betterUV, 0.0, 1.0);

        // clamp points to edges of screens, if outside, clamp on the z edges
        // -> just generally clamp the polygon...
        // the most extreme cases should be on a quad always, because it's linear
        // -> clamp all axis separately

        val avgSize =
            if (w * targetHeight > h * targetWidth) w.toFloat() * targetHeight / targetWidth else h.toFloat()
        val sx = w / avgSize
        val sy = h / avgSize

        fun getPoint(x: Float, y: Float, dst: Vector4f): Vector4f {
            return matrix.transformProject(dst.set(x, y, 0f, 1f))
        }

        val v00 = getPoint(-sx, -sy, JomlPools.vec4f.create())
        val v01 = getPoint(-sx, +sy, JomlPools.vec4f.create())
        val v10 = getPoint(+sx, -sy, JomlPools.vec4f.create())
        val v11 = getPoint(+sx, +sy, JomlPools.vec4f.create())

        // check these points by drawing them on the screen
        // they were correct as of 12th July 2020, 9:18 am

        // still correct in June 2025
        /* for (pt in listOf(v00, v01, v10, v11)) {
            val panel = GFXState.currentBuffer
            val x = (+pt.x * 0.5f + 0.5f) * panel.width
            val y = (-pt.y * 0.5f + 0.5f) * panel.height
            drawRect(x.toInt() - 2, y.toInt() - 2, 5, 5, 0xff0000 or black)
        }*/

        val zRange = Clipping.getZ(v00, v01, v10, v11)
        JomlPools.vec4f.sub(4)
        zRange ?: return null

        val closestDistance = min(unmapZ(zRange.first), unmapZ(zRange.second))

        // calculate the zoom level based on the distance
        val pixelSize = GFX.viewportHeight * 1f / (closestDistance * h) // e.g., 0.1 for a window far away
        val zoomLevel = 1f / pixelSize // 0.1 zoom means that we only need every 10th pixel

        return max(1, zoomLevel.toInt())
    }

    // calculate the depth based on the z value
    private fun unmapZ(z: Float): Float {
        if (GFXState.depthMode.currentValue.reversedDepth) {
            val n = Scene.nearZWOffset
            return n / z
        } else {
            val n = Scene.nearZ
            val f = Scene.farZ
            val top = 2 * f * n
            val bottom = (z * (f - n) - (f + n))
            return -top / bottom // the usual z is negative -> invert it :)
        }
    }

    fun getCacheableZoomLevel(level: Int): Int {
        return when {
            level < 1 -> 1
            level <= 6 || level == 8 || level == 12 || level == 16 -> level
            else -> {
                val stepsIn2 = 3
                val log = log2(level.toFloat())
                val roundedLog = round(stepsIn2 * log) / stepsIn2
                pow(2f, roundedLog).toInt()
            }
        }
    }

}