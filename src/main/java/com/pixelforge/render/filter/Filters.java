package com.pixelforge.render.filter;

import com.pixelforge.render.PixelPainter;
import com.pixelforge.util.ColorUtil;

/**
 * 内置滤镜工厂。
 *
 * <p>卷积类滤镜共用一个 {@code convolve} 实现；模糊用"三次盒式模糊"逼近高斯，
 * 复杂度从 O(r²) 降到 O(1)/像素，大半径也不会卡。</p>
 */
public final class Filters {

    private Filters() {
        throw new AssertionError("工具类不允许实例化");
    }

    // ------------------------------------------------------------------
    // 点运算
    // ------------------------------------------------------------------

    /** 灰度：按人眼亮度权重转成灰阶，保留 alpha。 */
    public static ImageFilter grayscale() {
        return new ImageFilter() {
            @Override
            public String name() {
                return "灰度";
            }

            @Override
            public int[] apply(int[] source, int width, int height) {
                int[] out = new int[source.length];
                for (int i = 0; i < source.length; i++) {
                    int argb = source[i];
                    int a = ColorUtil.alpha(argb);
                    if (a == 0) {
                        continue;
                    }
                    int gray = (int) Math.round(ColorUtil.red(argb) * 0.299
                            + ColorUtil.green(argb) * 0.587
                            + ColorUtil.blue(argb) * 0.114);
                    out[i] = (a << 24) | (gray << 16) | (gray << 8) | gray;
                }
                return out;
            }
        };
    }

    /** 反色。 */
    public static ImageFilter invert() {
        return new ImageFilter() {
            @Override
            public String name() {
                return "反色";
            }

            @Override
            public int[] apply(int[] source, int width, int height) {
                int[] out = new int[source.length];
                for (int i = 0; i < source.length; i++) {
                    int argb = source[i];
                    int a = ColorUtil.alpha(argb);
                    if (a == 0) {
                        continue;
                    }
                    out[i] = (a << 24)
                            | ((255 - ColorUtil.red(argb)) << 16)
                            | ((255 - ColorUtil.green(argb)) << 8)
                            | (255 - ColorUtil.blue(argb));
                }
                return out;
            }
        };
    }

    /** 亮度 / 对比度调整。亮度范围 -100..100，对比度 -100..100。 */
    public static ImageFilter brightnessContrast(double brightness, double contrast) {
        double b = brightness / 100.0;
        double factor = (259.0 * (contrast + 255.0)) / (255.0 * (259.0 - contrast));
        return new ImageFilter() {
            @Override
            public String name() {
                return "亮度/对比度";
            }

            @Override
            public int[] apply(int[] source, int width, int height) {
                int[] out = new int[source.length];
                for (int i = 0; i < source.length; i++) {
                    int argb = source[i];
                    int a = ColorUtil.alpha(argb);
                    if (a == 0) {
                        continue;
                    }
                    int r = (int) Math.round(factor * (ColorUtil.red(argb) - 128) + 128 + b * 255);
                    int g = (int) Math.round(factor * (ColorUtil.green(argb) - 128) + 128 + b * 255);
                    int bl = (int) Math.round(factor * (ColorUtil.blue(argb) - 128) + 128 + b * 255);
                    out[i] = (a << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(bl);
                }
                return out;
            }
        };
    }

    /** 饱和度调整，factor > 1 更鲜艳，< 1 更灰。 */
    public static ImageFilter saturate(double factor) {
        return new ImageFilter() {
            @Override
            public String name() {
                return "饱和度";
            }

            @Override
            public int[] apply(int[] source, int width, int height) {
                int[] out = new int[source.length];
                for (int i = 0; i < source.length; i++) {
                    int argb = source[i];
                    int a = ColorUtil.alpha(argb);
                    if (a == 0) {
                        continue;
                    }
                    int r = ColorUtil.red(argb);
                    int g = ColorUtil.green(argb);
                    int b = ColorUtil.blue(argb);
                    int gray = (int) Math.round(r * 0.299 + g * 0.587 + b * 0.114);
                    out[i] = (a << 24)
                            | (clamp((int) Math.round(gray + (r - gray) * factor)) << 16)
                            | (clamp((int) Math.round(gray + (g - gray) * factor)) << 8)
                            | clamp((int) Math.round(gray + (b - gray) * factor));
                }
                return out;
            }
        };
    }

    // ------------------------------------------------------------------
    // 卷积类
    // ------------------------------------------------------------------

    /** 锐化。 */
    public static ImageFilter sharpen() {
        return convolution("锐化", new double[]{
                0, -1, 0,
                -1, 5, -1,
                0, -1, 0
        }, 1.0);
    }

    /** 浮雕。 */
    public static ImageFilter emboss() {
        return convolution("浮雕", new double[]{
                -2, -1, 0,
                -1, 1, 1,
                0, 1, 2
        }, 0.0);
    }

    /** Sobel 边缘检测。 */
    public static ImageFilter edgeDetect() {
        return new ImageFilter() {
            @Override
            public String name() {
                return "边缘检测";
            }

            @Override
            public int[] apply(int[] source, int width, int height) {
                int[] out = new int[source.length];
                int[][] gx = {{-1, 0, 1}, {-2, 0, 2}, {-1, 0, 1}};
                int[][] gy = {{-1, -2, -1}, {0, 0, 0}, {1, 2, 1}};
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        double sumX = 0;
                        double sumY = 0;
                        for (int ky = -1; ky <= 1; ky++) {
                            for (int kx = -1; kx <= 1; kx++) {
                                int argb = pixelAt(source, width, height, x + kx, y + ky);
                                int lum = luma(argb);
                                sumX += lum * gx[ky + 1][kx + 1];
                                sumY += lum * gy[ky + 1][kx + 1];
                            }
                        }
                        int magnitude = clamp((int) Math.round(Math.hypot(sumX, sumY)));
                        int alpha = ColorUtil.alpha(source[y * width + x]);
                        out[y * width + x] = (alpha << 24)
                                | (magnitude << 16) | (magnitude << 8) | magnitude;
                    }
                }
                return out;
            }
        };
    }

    /**
     * 通用 3×3 卷积。
     *
     * @param offset 卷积结果额外加上的常量（浮雕需要）
     */
    public static ImageFilter convolution(String name, double[] kernel, double offset) {
        return new ImageFilter() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public int[] apply(int[] source, int width, int height) {
                int[] out = new int[source.length];
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        double r = 0;
                        double g = 0;
                        double b = 0;
                        for (int ky = -1; ky <= 1; ky++) {
                            for (int kx = -1; kx <= 1; kx++) {
                                double k = kernel[(ky + 1) * 3 + (kx + 1)];
                                int argb = pixelAt(source, width, height, x + kx, y + ky);
                                r += ColorUtil.red(argb) * k;
                                g += ColorUtil.green(argb) * k;
                                b += ColorUtil.blue(argb) * k;
                            }
                        }
                        int alpha = ColorUtil.alpha(source[y * width + x]);
                        out[y * width + x] = (alpha << 24)
                                | (clamp((int) Math.round(r + offset * 255)) << 16)
                                | (clamp((int) Math.round(g + offset * 255)) << 8)
                                | clamp((int) Math.round(b + offset * 255));
                    }
                }
                return out;
            }
        };
    }

    // ------------------------------------------------------------------
    // 模糊 / 马赛克
    // ------------------------------------------------------------------

    /** 高斯模糊（三次盒式模糊逼近）。 */
    public static ImageFilter gaussianBlur(int radius) {
        int r = Math.max(1, radius);
        return new ImageFilter() {
            @Override
            public String name() {
                return "高斯模糊";
            }

            @Override
            public int[] apply(int[] source, int width, int height) {
                int[] current = source.clone();
                for (int pass = 0; pass < 3; pass++) {
                    current = boxBlurPass(current, width, height, r);
                }
                return current;
            }
        };
    }

    private static int[] boxBlurPass(int[] source, int width, int height, int radius) {
        int[] horizontal = new int[source.length];
        int[] out = new int[source.length];
        int window = radius * 2 + 1;

        for (int y = 0; y < height; y++) {
            int row = y * width;
            long sumA = 0;
            long sumR = 0;
            long sumG = 0;
            long sumB = 0;
            for (int x = -radius; x <= radius; x++) {
                int argb = source[row + clampIndex(x, width)];
                sumA += ColorUtil.alpha(argb);
                sumR += ColorUtil.red(argb);
                sumG += ColorUtil.green(argb);
                sumB += ColorUtil.blue(argb);
            }
            for (int x = 0; x < width; x++) {
                horizontal[row + x] = ((int) (sumA / window) << 24)
                        | ((int) (sumR / window) << 16)
                        | ((int) (sumG / window) << 8)
                        | (int) (sumB / window);
                int remove = source[row + clampIndex(x - radius, width)];
                int add = source[row + clampIndex(x + radius + 1, width)];
                sumA += ColorUtil.alpha(add) - ColorUtil.alpha(remove);
                sumR += ColorUtil.red(add) - ColorUtil.red(remove);
                sumG += ColorUtil.green(add) - ColorUtil.green(remove);
                sumB += ColorUtil.blue(add) - ColorUtil.blue(remove);
            }
        }

        for (int x = 0; x < width; x++) {
            long sumA = 0;
            long sumR = 0;
            long sumG = 0;
            long sumB = 0;
            for (int y = -radius; y <= radius; y++) {
                int argb = horizontal[clampIndex(y, height) * width + x];
                sumA += ColorUtil.alpha(argb);
                sumR += ColorUtil.red(argb);
                sumG += ColorUtil.green(argb);
                sumB += ColorUtil.blue(argb);
            }
            for (int y = 0; y < height; y++) {
                out[y * width + x] = ((int) (sumA / window) << 24)
                        | ((int) (sumR / window) << 16)
                        | ((int) (sumG / window) << 8)
                        | (int) (sumB / window);
                int remove = horizontal[clampIndex(y - radius, height) * width + x];
                int add = horizontal[clampIndex(y + radius + 1, height) * width + x];
                sumA += ColorUtil.alpha(add) - ColorUtil.alpha(remove);
                sumR += ColorUtil.red(add) - ColorUtil.red(remove);
                sumG += ColorUtil.green(add) - ColorUtil.green(remove);
                sumB += ColorUtil.blue(add) - ColorUtil.blue(remove);
            }
        }
        return out;
    }

    /** 马赛克：按 block 大小取块内平均色。 */
    public static ImageFilter mosaic(int block) {
        int size = Math.max(2, block);
        return new ImageFilter() {
            @Override
            public String name() {
                return "马赛克";
            }

            @Override
            public int[] apply(int[] source, int width, int height) {
                int[] out = new int[source.length];
                for (int by = 0; by < height; by += size) {
                    for (int bx = 0; bx < width; bx += size) {
                        int maxY = Math.min(height, by + size);
                        int maxX = Math.min(width, bx + size);
                        long sumA = 0;
                        long sumR = 0;
                        long sumG = 0;
                        long sumB = 0;
                        int count = 0;
                        for (int y = by; y < maxY; y++) {
                            for (int x = bx; x < maxX; x++) {
                                int argb = source[y * width + x];
                                sumA += ColorUtil.alpha(argb);
                                sumR += ColorUtil.red(argb);
                                sumG += ColorUtil.green(argb);
                                sumB += ColorUtil.blue(argb);
                                count++;
                            }
                        }
                        if (count == 0) {
                            continue;
                        }
                        int argb = ((int) (sumA / count) << 24)
                                | ((int) (sumR / count) << 16)
                                | ((int) (sumG / count) << 8)
                                | (int) (sumB / count);
                        for (int y = by; y < maxY; y++) {
                            for (int x = bx; x < maxX; x++) {
                                out[y * width + x] = argb;
                            }
                        }
                    }
                }
                return out;
            }
        };
    }

    // ------------------------------------------------------------------

    private static int pixelAt(int[] pixels, int width, int height, int x, int y) {
        int cx = x < 0 ? 0 : Math.min(width - 1, x);
        int cy = y < 0 ? 0 : Math.min(height - 1, y);
        return pixels[cy * width + cx];
    }

    private static int clampIndex(int value, int size) {
        return value < 0 ? 0 : Math.min(size - 1, value);
    }

    private static int luma(int argb) {
        return (int) Math.round(ColorUtil.red(argb) * 0.299
                + ColorUtil.green(argb) * 0.587
                + ColorUtil.blue(argb) * 0.114);
    }

    private static int clamp(int value) {
        return value < 0 ? 0 : Math.min(255, value);
    }

    /** 供外部判断是否可用（保留扩展点）。 */
    public static int blendPreview(int dst, int src) {
        return PixelPainter.blendArgb(dst, src);
    }
}
