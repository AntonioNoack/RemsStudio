package opencv

import kotlin.math.pow
import kotlin.math.roundToInt

/*object OpticalFlowFarneback {

    enum class Interpolation {
        INTER_AREA,
        INTER_LINEAR
    }

    class Image(var w: Int, var h: Int, var channels: Int = 1) {
        var data: FloatArray? = null
        fun create(h: Int, w: Int, channels: Int = 1) {
            this.w = w
            this.h = h
            this.channels = channels
            data = FloatArray(w * h * channels)
        }

        fun ptr(y: Int) = w * y * channels
        fun set(other: Image) {
            // todo link or copy data?
        }

        fun copy(other: Image) {
            create(other.w, other.h)
            System.arraycopy(other.data!!, 0, data!!, 0, w * h * channels)
        }

        fun scale(f: Float) {
            val data = data
            if (data != null) {
                for (i in data.indices) {
                    data[i] *= f
                }
            }
        }

        fun scale(f: Double) = scale(f.toFloat())
    }

    fun resize(src: Image, dst: Image, w: Int, h: Int, interpolation: Interpolation) {
        // todo
    }

    fun gaussianBlur(src: Image, dst: Image, dx: Int, dy: Int, sx: Double, sy: Double) {
        gaussianBlur(src, dst, dx, dy, sx.toFloat(), sy.toFloat())
    }

    fun gaussianBlur(src: Image, dst: Image, dx: Int, dy: Int, sx: Float, sy: Float) {
        // todo
    }

    fun farnebackPolyExp(src: Image, dst: Image, n: Int, sigma: Float) {

        val width = src.w
        val height = src.h

        val kbuf = FloatArray(n * 6 + 3)
        val _row = FloatArray((width + n * 2) * 3)

        val g = n // kbuf
        val xg = g + n * 2 + 1
        val xxg = xg + n * 2 + 1

        val row = n * 3 // _row

        farnebackPrepareGaussian(n, sigma, g, xg, xxg, ig11, ig03, ig33, ig55)

        dst.create(width, height, 5)

        val src1 = src.data!!
        val dst1 = dst.data!!

        for (y in 0 until height) {
            val g0 = kbuf[g]

            val srow0 = src.ptr(y) // src
            val drow = dst.ptr(y) // dst

            // vertical part of convolution
            for (x in 0 until width) {
                _row[row + x * 3 + 0] = src1[srow0 + x] * g0
                _row[row + x * 3 + 1] = 0f
                _row[row + x * 3 + 2] = 0f
            }

            for(k in 1 .. n){

            }

        }

        /*
    for( y = 0; y < height; y++ )
    {
        float g0 = g[0], g1, g2;
        const float *srow0 = src.ptr<float>(y), *srow1 = 0;
        float *drow = dst.ptr<float>(y);

        // vertical part of convolution
        for( x = 0; x < width; x++ )
        {
            row[x*3] = srow0[x]*g0;
            row[x*3+1] = row[x*3+2] = 0.f;
        }

        for( k = 1; k <= n; k++ )
        {
            g0 = g[k]; g1 = xg[k]; g2 = xxg[k];
            srow0 = src.ptr<float>(std::max(y-k,0));
            srow1 = src.ptr<float>(std::min(y+k,height-1));

            for( x = 0; x < width; x++ )
            {
                float p = srow0[x] + srow1[x];
                float t0 = row[x*3] + g0*p;
                float t1 = row[x*3+1] + g1*(srow1[x] - srow0[x]);
                float t2 = row[x*3+2] + g2*p;

                row[x*3] = t0;
                row[x*3+1] = t1;
                row[x*3+2] = t2;
            }
        }

        // horizontal part of convolution
        for( x = 0; x < n*3; x++ )
        {
            row[-1-x] = row[2-x];
            row[width*3+x] = row[width*3+x-3];
        }

        for( x = 0; x < width; x++ )
        {
            g0 = g[0];
            // r1 ~ 1, r2 ~ x, r3 ~ y, r4 ~ x^2, r5 ~ y^2, r6 ~ xy
            double b1 = row[x*3]*g0, b2 = 0, b3 = row[x*3+1]*g0,
                b4 = 0, b5 = row[x*3+2]*g0, b6 = 0;

            for( k = 1; k <= n; k++ )
            {
                double tg = row[(x+k)*3] + row[(x-k)*3];
                g0 = g[k];
                b1 += tg*g0;
                b4 += tg*xxg[k];
                b2 += (row[(x+k)*3] - row[(x-k)*3])*xg[k];
                b3 += (row[(x+k)*3+1] + row[(x-k)*3+1])*g0;
                b6 += (row[(x+k)*3+1] - row[(x-k)*3+1])*xg[k];
                b5 += (row[(x+k)*3+2] + row[(x-k)*3+2])*g0;
            }

            // do not store r1
            drow[x*5+1] = (float)(b2*ig11);
            drow[x*5] = (float)(b3*ig11);
            drow[x*5+3] = (float)(b1*ig03 + b4*ig33);
            drow[x*5+2] = (float)(b1*ig03 + b5*ig33);
            drow[x*5+4] = (float)(b6*ig55);
        }
    }

    row -= n*3;
}
        * */
    }

    fun getLevels(maxLevels: Int, pyrScale: Double, size: Int, minSize: Int): Int {
        var k = 0
        var scale = 1.0
        while (k < maxLevels) {
            scale *= pyrScale
            if (size * scale < minSize) return k
            k++
        }
        return maxLevels
    }

    fun updateFlowGaussian(
        r0: Image,
        r1: Image,
        flowX: Int,
        flowY: Int,
        m: Image,
        bufM: Image,
        blockSize: Int,
        updateMatrices: Boolean
    ): Boolean {
        if (!gaussianBlur50cl(m, blockSize / 2, bufM)) return false
        if (!updateFlowOcl(bufM, flowX, flowY)) return false
        if (updateMatrices) {
            if (!updateMatricesOcl(flowX, flowY, R0, R1, bufM))
                return false
        }
        return true
    }

    fun updateFlowBlur(r0: Image, r1: Image, flow: Image, matM: Image, blockSize: Int, updateMatrices: Boolean) {
        val width = flow.w
        val height = flow.h
        val m = blockSize / 2
        val y0 = 0
        val y1: Int
        val minUpdateStripe = kotlin.math.max(1024 / width, blockSize)
        val scale = 1.0 / (blockSize * blockSize)
        val vSum = FloatArray((width + m * 2 + 2) * 5)
        val vSumOffset = (m + 1) * 5
        // init vsum
        val srow0 = matM.data!!
        for (x in 0 until width * 5) {
            vSum[x + vSumOffset] = srow0[x] * (m + 2)
        }
        for (y in 1 until m) {
            val srowOffset = kotlin.math.min(y, height - 1)
            for (x in 0 until width * 5) {
                vSum[x + vSumOffset] += srow0[x + srowOffset]
            }
        }

        // compute blur(G)*flow = blur(h)

    }

    fun calc(
        prev: Image, next: Image, initialFlow: Image?, pyrScale: Double, numLevels: Int, windowSize: Int,
        iterations: Int, polyN: Int, polySigma: Double, flags: Int
    ): Image {

        val minSize = 32
        val images = arrayOf(prev, next)

        val useInitialFlow = initialFlow != null
        val useFarnebackGaussian = true

        val levels = getLevels(numLevels, pyrScale, kotlin.math.min(prev.w, prev.h), minSize)

        var flow = initialFlow ?: Image(prev.w, prev.h)

        var prevFlow: Image? = null

        for (k in levels downTo 0) {
            val scale = pyrScale.pow(k.toDouble())
            val sigma = (1.0 / scale - 1.0) * 0.5
            val smoothSZ = kotlin.math.max(3, (sigma * 5).roundToInt())
            val width = (prev.w * scale).roundToInt()
            val height = (prev.h * scale).roundToInt()
            if (k > 0) flow.create(height, width)
            else flow.set(initialFlow ?: Image(width, height))

            if (prevFlow == null) {
                if (useInitialFlow) {
                    resize(initialFlow!!, flow, width, height, Interpolation.INTER_AREA)
                    flow.scale(scale)
                } else {
                    flow = Image(width, height)
                }
            } else {
                resize(prevFlow, flow, width, height, Interpolation.INTER_LINEAR)
                flow.scale(1.0 / pyrScale)
            }

            val fImg = Image(prev.w, prev.h)
            val I = Image(width, height)
            val r = Array(2) { Image(prev.w, prev.h) }
            for (i in 0 until 2) {
                fImg.copy(images[i])
                gaussianBlur(fImg, fImg, smoothSZ, smoothSZ, sigma, sigma)
                resize(fImg, I, width, height, Interpolation.INTER_LINEAR)
                farnebackPolyExp(I, r[i], polyN, polySigma.toFloat())
            }

            val M = Image(width, height)
            for (i in 0 until iterations) {
                if (useFarnebackGaussian) {
                    updateFlowGaussian(r[0], r[1], flow, M, windowSize, i < iterations - 1)
                } else {
                    updateFlowBlur(r[0], r[1], flow, windowSize, i < iterations - 1)
                }
            }

            prevFlow = flow

        }

    }

    @JvmStatic
    fun main(args: Array<String>) {

    }

}
*/