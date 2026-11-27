package com.endurance.util;

import java.nio.file.Path;

/**
 * JavaFX 启动前的小设置。
 *
 * <p>JavaFX 默认把着色器缓存放到用户目录下的 {@code .openjfx}，在受限环境里
 * 建不了目录就会一直往 stderr 刷 "Can not create cache"，很吵。
 * 这里统一指到系统临时目录，必须在 JavaFX 初始化**之前**调用。</p>
 */
public final class FxEnv {

    private FxEnv() {
        throw new AssertionError("工具类不允许实例化");
    }

    public static void configure() {
        if (System.getProperty("javafx.cachedir") != null) {
            return;
        }
        String temp = System.getProperty("java.io.tmpdir");
        if (temp == null || temp.isBlank()) {
            temp = System.getProperty("user.dir");
        }
        try {
            System.setProperty("javafx.cachedir", Path.of(temp, "endurance-fx-cache").toString());
        } catch (RuntimeException ignored) {
            // 设不进去也无所谓，最多是控制台吵一点
        }
    }
}
