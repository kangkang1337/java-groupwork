package com.pixelforge;

/**
 * 应用元信息。
 *
 * <p>集中存放作品名称、版本号等常量，避免在界面上各处硬编码字符串。</p>
 */
public final class AppInfo {

    /** 作品中文名，用于窗口标题与关于对话框。 */
    public static final String NAME = "PixelForge 像素工坊";

    /** 作品英文名，用于窗口标题与关于对话框。 */
    public static final String EN_NAME = "PixelForge";

    /** 版本号。 */
    public static final String VERSION = "1.0.0";

    private AppInfo() {
        throw new AssertionError("工具类不允许实例化");
    }
}
