package com.endurance.ui;

import javafx.scene.Scene;

/**
 * 全局样式装载。
 */
public final class Theme {

    private static final String CSS = "/css/dark.css";

    private Theme() {
        throw new AssertionError("工具类不允许实例化");
    }

    public static void apply(Scene scene) {
        var url = Theme.class.getResource(CSS);
        if (url != null) {
            scene.getStylesheets().add(url.toExternalForm());
        } else {
            System.err.println("样式表缺失: " + CSS);
        }
    }
}
