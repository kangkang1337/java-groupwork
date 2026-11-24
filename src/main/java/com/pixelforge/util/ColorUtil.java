package com.pixelforge.util;

import javafx.scene.paint.Color;

/**
 * 颜色转换小工具。
 *
 * <p>界面层用 JavaFX 的 {@link Color}，像素层统一用 {@code int}（0xAARRGGBB），
 * 转换全部集中在这里，避免各处重复写位运算。</p>
 */
public final class ColorUtil {

    private ColorUtil() {
        throw new AssertionError("工具类不允许实例化");
    }

    /** JavaFX 颜色 → ARGB（使用其自带 alpha）。 */
    public static int toArgb(Color color) {
        return toArgb(color, color.getOpacity());
    }

    /** JavaFX 颜色 → ARGB，并覆写整体透明度。 */
    public static int toArgb(Color color, double alpha) {
        int a = clamp((int) Math.round(alpha * 255));
        int r = clamp((int) Math.round(color.getRed() * 255));
        int g = clamp((int) Math.round(color.getGreen() * 255));
        int b = clamp((int) Math.round(color.getBlue() * 255));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** 替换 alpha 通道。 */
    public static int withAlpha(int argb, int alpha) {
        return (clamp(alpha) << 24) | (argb & 0x00FFFFFF);
    }

    public static int alpha(int argb) {
        return (argb >>> 24) & 0xFF;
    }

    public static int red(int argb) {
        return (argb >> 16) & 0xFF;
    }

    public static int green(int argb) {
        return (argb >> 8) & 0xFF;
    }

    public static int blue(int argb) {
        return argb & 0xFF;
    }

    /** ARGB → JavaFX 颜色。 */
    public static Color toFxColor(int argb) {
        return Color.rgb(red(argb), green(argb), blue(argb), alpha(argb) / 255.0);
    }

    /** 形如 #RRGGBB 或 #AARRGGBB 的十六进制字符串。 */
    public static String toHex(Color color) {
        return String.format("#%02X%02X%02X",
                (int) Math.round(color.getRed() * 255),
                (int) Math.round(color.getGreen() * 255),
                (int) Math.round(color.getBlue() * 255));
    }

    /** 两色之间的线性插值（t=0 取 c1，t=1 取 c2）。 */
    public static Color mix(Color c1, Color c2, double t) {
        double k = Math.max(0, Math.min(1, t));
        return Color.color(
                c1.getRed() + (c2.getRed() - c1.getRed()) * k,
                c1.getGreen() + (c2.getGreen() - c1.getGreen()) * k,
                c1.getBlue() + (c2.getBlue() - c1.getBlue()) * k,
                c1.getOpacity() + (c2.getOpacity() - c1.getOpacity()) * k);
    }

    private static int clamp(int value) {
        return value < 0 ? 0 : Math.min(value, 255);
    }
}
