package com.pixelforge.render.filter;

/**
 * 图像滤镜：输入一份 ARGB 像素副本，输出处理后的新数组。
 *
 * <p>具体滤镜用 {@link Filters} 里的工厂方法拼装，方便菜单直接引用，
 * 也方便单元测试。</p>
 */
public interface ImageFilter {

    /** 滤镜显示名（显示在历史记录里）。 */
    String name();

    /**
     * 执行滤镜。
     *
     * @param source 输入像素（调用方给的副本，可安全读取）
     * @param width  图像宽度
     * @param height 图像高度
     * @return 输出像素，长度必须为 width*height
     */
    int[] apply(int[] source, int width, int height);
}
