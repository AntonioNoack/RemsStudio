package me.anno.image;

import me.anno.gpu.texture.Texture2D;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.Raster;
import java.nio.ByteBuffer;

public abstract class Image {

    int numChannels;
    public boolean hasAlphaChannel;

    public Image(int numChannels, boolean hasAlphaChannel) {
        this.numChannels = numChannels;
        this.hasAlphaChannel = hasAlphaChannel;
    }

    public Image(int w, int h, int numChannels, boolean hasAlphaChannel) {
        this(numChannels, hasAlphaChannel);
        width = w;
        height = h;
    }

    public int width, height;

    public int getNumChannels() {
        return numChannels;
    }

    public void setHasAlphaChannel(boolean alpha) {
        hasAlphaChannel = alpha;
    }

    public boolean hasAlphaChannel() {
        return hasAlphaChannel;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public BufferedImage createBufferedImage() {
        BufferedImage image = new BufferedImage(width, height, hasAlphaChannel ? 2 : 1);
        int width = getWidth();
        int height = getHeight();
        for (int y = 0, i = 0; y < height; y++) {
            for (int x = 0; x < width; x++, i++) {
                image.setRGB(x, y, getRGB(i));
            }
        }
        return image;
    }

    public void createRGBFrom3StridedData(Texture2D texture, boolean checkRedundancy, byte[] data) {
        // add a padding for alpha, because OpenGL needs it that way
        ByteBuffer buffer = Texture2D.Companion.getByteBufferPool().get(width * height * 4, false);
        for (int j = 0, k = 0, l = width * height * 3; k < l; ) {
            buffer.put(j++, (byte) 255);// a
            buffer.put(j++, data[k++]);// r
            buffer.put(j++, data[k++]);// g
            buffer.put(j++, data[k++]);// b
        }
        texture.createRGB(buffer, checkRedundancy);
    }

    public abstract int getRGB(int index);

    public int getRGB(int x, int y) {
        return getRGB(x + y * width);
    }

    public void createTexture(Texture2D texture, boolean checkRedundancy) {
        texture.create(createBufferedImage(), true, true);
    }

    public BufferedImage createBufferedImage(int dstWidth, int dstHeight) {
        final int srcWidth = this.width;
        final int srcHeight = this.height;
        if (dstWidth > srcWidth) {
            dstHeight = dstHeight * srcWidth / dstWidth;
            dstWidth = srcWidth;
        }
        if (dstHeight > srcHeight) {
            dstWidth = dstWidth * srcHeight / dstHeight;
            dstHeight = srcHeight;
        }
        if (dstWidth == srcWidth && dstHeight == srcHeight) {
            return createBufferedImage();
        }
        final BufferedImage img = new BufferedImage(dstWidth, dstHeight, hasAlphaChannel ? 2 : 1);
        final DataBuffer buffer = img.getData().getDataBuffer();
        int srcY0 = 0;
        for (int dstY = 0, dstIndex = 0; dstY < dstHeight; dstY++) {
            final int srcY1 = ((dstY + 1) * srcHeight) / dstHeight;
            final int srcDY = srcY1 - srcY0;
            final int srcIndexY0 = srcY0 * srcWidth;
            for (int dstX = 0; dstX < dstWidth; dstX++, dstIndex++) {
                int srcX0 = (dstX * srcWidth) / dstWidth;
                int srcX1 = ((dstX + 1) * srcWidth) / dstWidth;
                // we could use better interpolation, but it shouldn't really matter
                int r = 0, g = 0, b = 0, a = 0;
                int srcIndexYI = srcIndexY0;
                // use interpolation
                for (int y0 = srcY0; y0 < srcY1; y0++) {
                    final int startIndex = srcX0 + srcIndexYI;
                    final int endIndex = srcX1 + srcIndexYI;
                    for (int i = startIndex; i < endIndex; i++) {
                        final int color = getRGB(i);
                        a += (color >> 24) & 255;
                        r += (color >> 16) & 255;
                        g += (color >> 8) & 255;
                        b += color & 255;
                    }
                    srcIndexYI += srcWidth;
                }
                final int count = (srcX1 - srcX0) * srcDY;
                if (count > 1) {
                    a /= count;
                    r /= count;
                    g /= count;
                    b /= count;
                }
                buffer.setElem(dstIndex, argb(a, r, g, b));
            }
            srcY0 = srcY1;
        }
        // update the image, otherwise the result is broken
        img.setData(Raster.createRaster(img.getRaster().getSampleModel(), buffer, new Point()));
        return img;
    }

    public static int argb(int a, int r, int g, int b) {
        return (a << 24) + (r << 16) + (g << 8) + b;
    }

}
