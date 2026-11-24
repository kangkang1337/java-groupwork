package com.pixelforge.model;

import java.util.function.BiFunction;

/**
 * 图层混合模式。
 *
 * <p>每个枚举值自带一个"颜色混合函数"：输入底层颜色 d 与上层颜色 s（均为 0..1 的
 * 直线分量），输出混合后的颜色。透明度合成由 {@code Compositor} 统一处理，
 * 这里只关心颜色怎么混。</p>
 */
public enum BlendMode {

    NORMAL("正常", (s, d) -> s),
    MULTIPLY("正片叠底", (s, d) -> s * d),
    SCREEN("滤色", (s, d) -> s + d - s * d),
    OVERLAY("叠加", (s, d) -> d <= 0.5 ? 2 * s * d : 1 - 2 * (1 - s) * (1 - d)),
    DARKEN("变暗", (s, d) -> Math.min(s, d)),
    LIGHTEN("变亮", (s, d) -> Math.max(s, d));

    private final String displayName;
    private final BiFunction<Double, Double, Double> fn;

    BlendMode(String displayName, BiFunction<Double, Double, Double> fn) {
        this.displayName = displayName;
        this.fn = fn;
    }

    public String displayName() {
        return displayName;
    }

    /**
     * 混合两个通道值。
     *
     * @param source 上层颜色分量（0..1）
     * @param dest   底层颜色分量（0..1）
     * @return 混合结果（0..1）
     */
    public double blend(double source, double dest) {
        return fn.apply(source, dest);
    }

    @Override
    public String toString() {
        return displayName;
    }
}
