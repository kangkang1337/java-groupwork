package com.pixelforge.render;

import com.pixelforge.model.Layer;
import com.pixelforge.util.ColorUtil;

import java.awt.AlphaComposite;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * 像素级绘制引擎。
 *
 * <p>所有工具最终都落到这里：圆头笔刷用"点到线段距离"算覆盖率实现抗锯齿，
 * 形状工具复用线段绘制，填充用扫描线算法。写像素时统一走 source-over 合成，
 * 因此半透明颜色、软边缘都能正确叠加。</p>
 */
public final class PixelPainter {

    private PixelPainter() {
        throw new AssertionError("工具类不允许实例化");
    }

    // ------------------------------------------------------------------
    // 基础合成
    // ------------------------------------------------------------------

    /**
     * 把一个颜色按覆盖率叠到目标像素上（source-over）。
     */
    private static void blendPixel(Layer layer, int x, int y, int argb, double coverage) {
        if (!layer.contains(x, y)) {
            return;
        }
        double sa = ColorUtil.alpha(argb) / 255.0 * coverage;
        if (sa <= 0.0) {
            return;
        }
        int index = y * layer.getWidth() + x;
        int dst = layer.getPixels()[index];

        if (sa >= 0.999) {
            layer.getPixels()[index] = argb;
            return;
        }

        double da = ColorUtil.alpha(dst) / 255.0;
        double outA = sa + da * (1 - sa);
        if (outA <= 0.0) {
            layer.getPixels()[index] = 0;
            return;
        }

        double sr = ColorUtil.red(argb) / 255.0;
        double sg = ColorUtil.green(argb) / 255.0;
        double sb = ColorUtil.blue(argb) / 255.0;
        double dr = ColorUtil.red(dst) / 255.0;
        double dg = ColorUtil.green(dst) / 255.0;
        double db = ColorUtil.blue(dst) / 255.0;

        double r = (sr * sa + dr * da * (1 - sa)) / outA;
        double g = (sg * sa + dg * da * (1 - sa)) / outA;
        double b = (sb * sa + db * da * (1 - sa)) / outA;

        layer.getPixels()[index] = ((int) Math.round(outA * 255) << 24)
                | ((int) Math.round(r * 255) << 16)
                | ((int) Math.round(g * 255) << 8)
                | (int) Math.round(b * 255);
    }

    /**
     * 纯函数式合成：把 src 按自身 alpha 叠到 dst 上，返回结果 ARGB。
     * 供选区提交、粘贴等"整块像素搬运"的场景使用。
     */
    public static int blendArgb(int dst, int src) {
        int sa = ColorUtil.alpha(src);
        if (sa == 0) {
            return dst;
        }
        if (sa == 255) {
            return src;
        }
        double aS = sa / 255.0;
        int da = ColorUtil.alpha(dst);
        double aD = da / 255.0;
        double outA = aS + aD * (1 - aS);
        if (outA <= 0) {
            return 0;
        }
        double r = (ColorUtil.red(src) / 255.0 * aS
                + ColorUtil.red(dst) / 255.0 * aD * (1 - aS)) / outA;
        double g = (ColorUtil.green(src) / 255.0 * aS
                + ColorUtil.green(dst) / 255.0 * aD * (1 - aS)) / outA;
        double b = (ColorUtil.blue(src) / 255.0 * aS
                + ColorUtil.blue(dst) / 255.0 * aD * (1 - aS)) / outA;
        return ((int) Math.round(outA * 255) << 24)
                | ((int) Math.round(r * 255) << 16)
                | ((int) Math.round(g * 255) << 8)
                | (int) Math.round(b * 255);
    }

    /** 擦除：按覆盖率削弱目标像素的 alpha。 */
    private static void erasePixel(Layer layer, int x, int y, double coverage) {        if (!layer.contains(x, y)) {
            return;
        }
        int index = y * layer.getWidth() + x;
        int dst = layer.getPixels()[index];
        int alpha = ColorUtil.alpha(dst);
        if (alpha == 0) {
            return;
        }
        int newAlpha = (int) Math.round(alpha * (1.0 - Math.min(1.0, coverage)));
        layer.getPixels()[index] = (newAlpha << 24) | (dst & 0x00FFFFFF);
    }

    // ------------------------------------------------------------------
    // 笔刷
    // ------------------------------------------------------------------

    /**
     * 画一段圆头画笔（两点之间的胶囊体）。
     *
     * @param radius  笔刷半径（像素）
     * @param argb    颜色
     * @param opacity 工具不透明度 0..1
     */
    public static void strokeSegment(Layer layer, double x0, double y0, double x1, double y1,
                                     double radius, int argb, double opacity) {
        double r = Math.max(0.5, radius);
        int minX = (int) Math.floor(Math.min(x0, x1) - r - 1);
        int maxX = (int) Math.ceil(Math.max(x0, x1) + r + 1);
        int minY = (int) Math.floor(Math.min(y0, y1) - r - 1);
        int maxY = (int) Math.ceil(Math.max(y0, y1) + r + 1);

        minX = Math.max(0, minX);
        minY = Math.max(0, minY);
        maxX = Math.min(layer.getWidth() - 1, maxX);
        maxY = Math.min(layer.getHeight() - 1, maxY);

        double dx = x1 - x0;
        double dy = y1 - y0;
        double len2 = dx * dx + dy * dy;

        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                double px = x + 0.5;
                double py = y + 0.5;
                double t = len2 == 0 ? 0 : ((px - x0) * dx + (py - y0) * dy) / len2;
                t = t < 0 ? 0 : (t > 1 ? 1 : t);
                double cx = x0 + t * dx;
                double cy = y0 + t * dy;
                double dist = Math.hypot(px - cx, py - cy);
                double coverage = r + 0.5 - dist;
                if (coverage <= 0) {
                    continue;
                }
                blendPixel(layer, x, y, argb, Math.min(1.0, coverage) * opacity);
            }
        }
    }

    /** 擦除一段（圆头橡皮）。 */
    public static void eraseSegment(Layer layer, double x0, double y0, double x1, double y1,
                                    double radius, double strength) {
        double r = Math.max(0.5, radius);
        int minX = Math.max(0, (int) Math.floor(Math.min(x0, x1) - r - 1));
        int maxX = Math.min(layer.getWidth() - 1, (int) Math.ceil(Math.max(x0, x1) + r + 1));
        int minY = Math.max(0, (int) Math.floor(Math.min(y0, y1) - r - 1));
        int maxY = Math.min(layer.getHeight() - 1, (int) Math.ceil(Math.max(y0, y1) + r + 1));

        double dx = x1 - x0;
        double dy = y1 - y0;
        double len2 = dx * dx + dy * dy;

        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                double px = x + 0.5;
                double py = y + 0.5;
                double t = len2 == 0 ? 0 : ((px - x0) * dx + (py - y0) * dy) / len2;
                t = t < 0 ? 0 : (t > 1 ? 1 : t);
                double dist = Math.hypot(px - (x0 + t * dx), py - (y0 + t * dy));
                double coverage = r + 0.5 - dist;
                if (coverage <= 0) {
                    continue;
                }
                erasePixel(layer, x, y, Math.min(1.0, coverage) * strength);
            }
        }
    }

    // ------------------------------------------------------------------
    // 基本形状
    // ------------------------------------------------------------------

    public static void fillRect(Layer layer, double x0, double y0, double x1, double y1,
                                int argb, double opacity) {
        int left = (int) Math.floor(Math.min(x0, x1));
        int top = (int) Math.floor(Math.min(y0, y1));
        int right = (int) Math.ceil(Math.max(x0, x1));
        int bottom = (int) Math.ceil(Math.max(y0, y1));
        left = Math.max(0, left);
        top = Math.max(0, top);
        right = Math.min(layer.getWidth(), right);
        bottom = Math.min(layer.getHeight(), bottom);
        for (int y = top; y < bottom; y++) {
            for (int x = left; x < right; x++) {
                blendPixel(layer, x, y, argb, opacity);
            }
        }
    }

    public static void strokeRect(Layer layer, double x0, double y0, double x1, double y1,
                                  double thickness, int argb, double opacity) {
        double radius = Math.max(0.5, thickness / 2.0);
        strokeSegment(layer, x0, y0, x1, y0, radius, argb, opacity);
        strokeSegment(layer, x1, y0, x1, y1, radius, argb, opacity);
        strokeSegment(layer, x1, y1, x0, y1, radius, argb, opacity);
        strokeSegment(layer, x0, y1, x0, y0, radius, argb, opacity);
    }

    public static void fillEllipse(Layer layer, double cx, double cy, double rx, double ry,
                                   int argb, double opacity) {
        if (rx <= 0 || ry <= 0) {
            return;
        }
        int minX = Math.max(0, (int) Math.floor(cx - rx - 1));
        int maxX = Math.min(layer.getWidth() - 1, (int) Math.ceil(cx + rx + 1));
        int minY = Math.max(0, (int) Math.floor(cy - ry - 1));
        int maxY = Math.min(layer.getHeight() - 1, (int) Math.ceil(cy + ry + 1));
        double scale = Math.min(rx, ry);

        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                double nx = (x + 0.5 - cx) / rx;
                double ny = (y + 0.5 - cy) / ry;
                double d = Math.sqrt(nx * nx + ny * ny);
                double coverage = (1.0 - d) * scale + 0.5;
                if (coverage <= 0) {
                    continue;
                }
                blendPixel(layer, x, y, argb, Math.min(1.0, coverage) * opacity);
            }
        }
    }

    public static void strokeEllipse(Layer layer, double cx, double cy, double rx, double ry,
                                     double thickness, int argb, double opacity) {
        if (rx <= 0 || ry <= 0) {
            return;
        }
        double radius = Math.max(0.5, thickness / 2.0);
        int steps = Math.max(48, (int) Math.round((rx + ry) * 2));
        double prevX = cx + rx;
        double prevY = cy;
        for (int i = 1; i <= steps; i++) {
            double angle = 2 * Math.PI * i / steps;
            double x = cx + rx * Math.cos(angle);
            double y = cy + ry * Math.sin(angle);
            strokeSegment(layer, prevX, prevY, x, y, radius, argb, opacity);
            prevX = x;
            prevY = y;
        }
    }

    public static void fillPolygon(Layer layer, double[] xs, double[] ys, int argb, double opacity) {
        int count = Math.min(xs.length, ys.length);
        if (count < 3) {
            return;
        }
        double minY = Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        for (int i = 0; i < count; i++) {
            minY = Math.min(minY, ys[i]);
            maxY = Math.max(maxY, ys[i]);
        }
        int top = Math.max(0, (int) Math.floor(minY));
        int bottom = Math.min(layer.getHeight() - 1, (int) Math.ceil(maxY));

        double[] nodes = new double[count];
        for (int y = top; y <= bottom; y++) {
            double scanY = y + 0.5;
            int nodeCount = 0;
            for (int i = 0, j = count - 1; i < count; j = i++) {
                if ((ys[i] < scanY && ys[j] >= scanY) || (ys[j] < scanY && ys[i] >= scanY)) {
                    double t = (scanY - ys[i]) / (ys[j] - ys[i]);
                    nodes[nodeCount++] = xs[i] + t * (xs[j] - xs[i]);
                }
            }
            java.util.Arrays.sort(nodes, 0, nodeCount);
            for (int i = 0; i + 1 < nodeCount; i += 2) {
                int left = Math.max(0, (int) Math.round(nodes[i]));
                int right = Math.min(layer.getWidth() - 1, (int) Math.round(nodes[i + 1]));
                for (int x = left; x <= right; x++) {
                    blendPixel(layer, x, y, argb, opacity);
                }
            }
        }
    }

    public static void strokePolyline(Layer layer, double[] xs, double[] ys,
                                      double thickness, int argb, double opacity, boolean close) {
        int count = Math.min(xs.length, ys.length);
        if (count < 2) {
            return;
        }
        double radius = Math.max(0.5, thickness / 2.0);
        for (int i = 1; i < count; i++) {
            strokeSegment(layer, xs[i - 1], ys[i - 1], xs[i], ys[i], radius, argb, opacity);
        }
        if (close) {
            strokeSegment(layer, xs[count - 1], ys[count - 1], xs[0], ys[0], radius, argb, opacity);
        }
    }

    // ------------------------------------------------------------------
    // 油漆桶
    // ------------------------------------------------------------------

    /**
     * 扫描线洪水填充。
     *
     * @param tolerance 颜色容差 0..255（按 RGBA 四通道最大差判断）
     * @return 实际填充的像素数
     */
    public static int floodFill(Layer layer, int startX, int startY, int argb,
                                double opacity, int tolerance) {
        int width = layer.getWidth();
        int height = layer.getHeight();
        if (startX < 0 || startY < 0 || startX >= width || startY >= height) {
            return 0;
        }
        int[] pixels = layer.getPixels();
        int target = pixels[startY * width + startX];
        if (colorDistance(target, argb) == 0 && opacity >= 1.0) {
            return 0;
        }

        boolean[] visited = new boolean[width * height];
        java.util.ArrayDeque<int[]> stack = new java.util.ArrayDeque<>();
        stack.push(new int[]{startX, startY});
        int filled = 0;

        while (!stack.isEmpty()) {
            int[] point = stack.pop();
            int x = point[0];
            int y = point[1];
            int rowStart = y * width;
            if (visited[rowStart + x] || !isSimilar(pixels[rowStart + x], target, tolerance)) {
                continue;
            }
            int left = x;
            while (left > 0 && !visited[rowStart + left - 1]
                    && isSimilar(pixels[rowStart + left - 1], target, tolerance)) {
                left--;
            }
            int right = x;
            while (right < width - 1 && !visited[rowStart + right + 1]
                    && isSimilar(pixels[rowStart + right + 1], target, tolerance)) {
                right++;
            }
            for (int px = left; px <= right; px++) {
                visited[rowStart + px] = true;
                blendPixel(layer, px, y, argb, opacity);
                filled++;
                if (y > 0 && !visited[rowStart - width + px]
                        && isSimilar(pixels[rowStart - width + px], target, tolerance)) {
                    stack.push(new int[]{px, y - 1});
                }
                if (y < height - 1 && !visited[rowStart + width + px]
                        && isSimilar(pixels[rowStart + width + px], target, tolerance)) {
                    stack.push(new int[]{px, y + 1});
                }
            }
        }
        return filled;
    }

    private static boolean isSimilar(int a, int b, int tolerance) {
        return colorDistance(a, b) <= tolerance;
    }

    /** 四通道最大差值。 */
    public static int colorDistance(int a, int b) {
        return Math.max(
                Math.max(Math.abs(ColorUtil.alpha(a) - ColorUtil.alpha(b)),
                        Math.abs(ColorUtil.red(a) - ColorUtil.red(b))),
                Math.max(Math.abs(ColorUtil.green(a) - ColorUtil.green(b)),
                        Math.abs(ColorUtil.blue(a) - ColorUtil.blue(b))));
    }

    // ------------------------------------------------------------------
    // 文字
    // ------------------------------------------------------------------

    /**
     * 用 AWT 字体渲染文字并合成到图层上。
     *
     * @param x 文字左上角横坐标
     * @param y 文字基线纵坐标
     */
    public static void drawText(Layer layer, String text, String fontFamily, int fontSize,
                                boolean bold, int argb, int x, int y) {
        if (text == null || text.isEmpty()) {
            return;
        }
        BufferedImage canvas = new BufferedImage(layer.getWidth(), layer.getHeight(),
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = canvas.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);
            g.setComposite(AlphaComposite.SrcOver);
            g.setFont(new Font(fontFamily, bold ? Font.BOLD : Font.PLAIN, fontSize));
            g.setColor(new java.awt.Color(
                    ColorUtil.red(argb), ColorUtil.green(argb), ColorUtil.blue(argb),
                    ColorUtil.alpha(argb)));
            g.drawString(text, x, y);
        } finally {
            g.dispose();
        }
        int[] rendered = new int[layer.getWidth() * layer.getHeight()];
        canvas.getRGB(0, 0, layer.getWidth(), layer.getHeight(), rendered, 0, layer.getWidth());
        int[] target = layer.getPixels();
        for (int i = 0; i < target.length; i++) {
            int src = rendered[i];
            int alpha = ColorUtil.alpha(src);
            if (alpha == 0) {
                continue;
            }
            int tx = i % layer.getWidth();
            int ty = i / layer.getWidth();
            blendPixel(layer, tx, ty, src, alpha / 255.0);
        }
    }
}
