package com.pixelforge.ui;

import javafx.scene.Scene;
import javafx.scene.control.DialogPane;

/**
 * 全局样式装载。
 */
public final class Style {

    private static final String DARK_CSS = "/css/dark.css";

    private Style() {
        throw new AssertionError("工具类不允许实例化");
    }

    /** 样式表 URL，未找到时为 {@code null}。 */
    public static String stylesheetUrl() {
        var url = Style.class.getResource(DARK_CSS);
        return url == null ? null : url.toExternalForm();
    }

    /** 给场景套上深色主题。 */
    public static void apply(Scene scene) {
        String url = stylesheetUrl();
        if (url != null) {
            scene.getStylesheets().add(url);
        } else {
            System.err.println("样式表缺失: " + DARK_CSS);
        }
    }

    /** 给对话框套上深色主题（对话框有独立场景，不会继承主窗口样式）。 */
    public static void apply(DialogPane pane) {
        String url = stylesheetUrl();
        if (url != null) {
            pane.getStylesheets().add(url);
        }
    }
}
