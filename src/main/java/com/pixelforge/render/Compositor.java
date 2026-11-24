package com.pixelforge.render;

import com.pixelforge.model.BlendMode;
import com.pixelforge.model.Document;
import com.pixelforge.model.Layer;

import java.util.Arrays;

/**
 * 图层合成器：把自下而上的多个图层合成为一张 ARGB 图像。
 *
 * <p>采用标准的"source-over + 可替换混合函数"公式：
 * <pre>
 *   aOut = aS + aD * (1 - aS)
 *   cOut = (blend(cS, cD) * aS + cD * aD * (1 - aS)) / aOut
 * </pre>
 * 其中 aS 已乘上图层的整体不透明度。这样各混合模式都能复用同一套透明度公式。</p>
 */
public final class Compositor {

    private Compositor() {
        throw new AssertionError("工具类不允许实例化");
    }

    /**
     * 把文档的可见图层合成为 ARGB 像素数组。
     *
     * @param document 文档
     * @param target   目标数组，长度必须为 width*height
     */
    public static void composite(Document document, int[] target) {
        int expected = document.getWidth() * document.getHeight();
        if (target.length != expected) {
            throw new IllegalArgumentException("目标像素数组长度不符: " + target.length + " != " + expected);
        }
        Arrays.fill(target, 0);
        for (Layer layer : document.getLayers()) {
            if (!layer.isVisible() || layer.getOpacity() <= 0.0) {
                continue;
            }
            blendLayer(target, layer.getPixels(), layer.getOpacity(), layer.getBlendMode());
        }
    }

    /** 把一层像素叠到目标缓冲上。 */
    public static void blendLayer(int[] dest, int[] source, double opacity, BlendMode mode) {
        if (opacity >= 0.999 && mode == BlendMode.NORMAL) {
            blendOpaqueNormal(dest, source);
            return;
        }
        for (int i = 0; i < dest.length; i++) {
            dest[i] = blendPixel(dest[i], source[i], opacity, mode);
        }
    }

    /** 整层不透明的"正常"模式快路径：源像素直接覆盖。 */
    private static void blendOpaqueNormal(int[] dest, int[] source) {
        for (int i = 0; i < dest.length; i++) {
            int s = source[i];
            if ((s >>> 24) == 255) {
                dest[i] = s;
            } else if ((s >>> 24) != 0) {
                dest[i] = blendPixel(dest[i], s, 1.0, BlendMode.NORMAL);
            }
        }
    }

    /**
     * 单个像素的合成。
     *
     * @param dest    底层像素（ARGB）
     * @param source  上层像素（ARGB）
     * @param opacity 图层不透明度 0..1
     * @param mode    混合模式
     * @return 合成结果（ARGB）
     */
    public static int blendPixel(int dest, int source, double opacity, BlendMode mode) {
        int sourceAlphaByte = (source >>> 24) & 0xFF;
        if (sourceAlphaByte == 0) {
            return dest;
        }
        double aS = (sourceAlphaByte / 255.0) * opacity;
        if (aS <= 0.0) {
            return dest;
        }

        int destAlphaByte = (dest >>> 24) & 0xFF;
        double aD = destAlphaByte / 255.0;

        if (aS >= 0.999 && mode == BlendMode.NORMAL) {
            return source;
        }

        double sr = ((source >> 16) & 0xFF) / 255.0;
        double sg = ((source >> 8) & 0xFF) / 255.0;
        double sb = (source & 0xFF) / 255.0;

        if (aD <= 0.0) {
            // 底层全透明：直接取上层颜色与透明度
            return pack((int) Math.round(aS * 255), sr, sg, sb);
        }

        double dr = ((dest >> 16) & 0xFF) / 255.0;
        double dg = ((dest >> 8) & 0xFF) / 255.0;
        double db = (dest & 0xFF) / 255.0;

        double br = mode.blend(sr, dr);
        double bg = mode.blend(sg, dg);
        double bb = mode.blend(sb, db);

        double aOut = aS + aD * (1 - aS);
        if (aOut <= 0.0) {
            return 0;
        }
        double outR = (br * aS + dr * aD * (1 - aS)) / aOut;
        double outG = (bg * aS + dg * aD * (1 - aS)) / aOut;
        double outB = (bb * aS + db * aD * (1 - aS)) / aOut;

        return pack((int) Math.round(aOut * 255), outR, outG, outB);
    }

    private static int pack(int alpha, double r, double g, double b) {
        int a = clamp(alpha);
        int rr = clamp((int) Math.round(r * 255));
        int gg = clamp((int) Math.round(g * 255));
        int bb = clamp((int) Math.round(b * 255));
        return (a << 24) | (rr << 16) | (gg << 8) | bb;
    }

    private static int clamp(int value) {
        return value < 0 ? 0 : Math.min(value, 255);
    }
}
