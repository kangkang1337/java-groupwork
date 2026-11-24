package com.pixelforge.model;

/**
 * 图层：一块与文档等大的 ARGB 像素缓冲，外加显示属性。
 *
 * <p>像素用 {@code int[]} 存储（非预乘 ARGB，格式 0xAARRGGBB），
 * 既方便逐像素的绘制与滤镜运算，也能一次性推给 JavaFX 的 {@code PixelWriter} 显示。
 * 数组直接暴露给内部绘制代码以避免复制开销。</p>
 */
public class Layer {

    private String name;
    private final int width;
    private final int height;
    private final int[] pixels;

    private boolean visible = true;
    private double opacity = 1.0;
    private BlendMode blendMode = BlendMode.NORMAL;

    public Layer(String name, int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("图层尺寸必须为正数: " + width + "x" + height);
        }
        this.name = name;
        this.width = width;
        this.height = height;
        this.pixels = new int[width * height];
    }

    private Layer(String name, int width, int height, int[] pixels) {
        this.name = name;
        this.width = width;
        this.height = height;
        this.pixels = pixels;
    }

    // ------------------------------------------------------------------
    // 像素操作
    // ------------------------------------------------------------------

    public int getPixel(int x, int y) {
        return pixels[y * width + x];
    }

    /** 直接写入像素，不做边界检查（调用方需保证坐标合法）。 */
    public void setPixel(int x, int y, int argb) {
        pixels[y * width + x] = argb;
    }

    /** 带边界检查的写入，坐标越界时静默忽略。 */
    public void setPixelSafe(int x, int y, int argb) {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            return;
        }
        pixels[y * width + x] = argb;
    }

    public boolean contains(int x, int y) {
        return x >= 0 && y >= 0 && x < width && y < height;
    }

    /** 整层填充为同一颜色。 */
    public void fill(int argb) {
        java.util.Arrays.fill(pixels, argb);
    }

    /** 清空为全透明。 */
    public void clear() {
        java.util.Arrays.fill(pixels, 0);
    }

    /** 深拷贝（含显示属性，名称追加"副本"）。 */
    public Layer duplicate() {
        Layer copy = new Layer(name + " 副本", width, height, pixels.clone());
        copy.visible = visible;
        copy.opacity = opacity;
        copy.blendMode = blendMode;
        return copy;
    }

    // ------------------------------------------------------------------

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    /** 像素缓冲本体（长度 = width * height，格式 0xAARRGGBB）。 */
    public int[] getPixels() {
        return pixels;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public double getOpacity() {
        return opacity;
    }

    public void setOpacity(double opacity) {
        this.opacity = Math.max(0, Math.min(1, opacity));
    }

    public BlendMode getBlendMode() {
        return blendMode;
    }

    public void setBlendMode(BlendMode blendMode) {
        this.blendMode = blendMode == null ? BlendMode.NORMAL : blendMode;
    }

    @Override
    public String toString() {
        return "Layer[" + name + " " + width + "x" + height + " visible=" + visible + "]";
    }
}
