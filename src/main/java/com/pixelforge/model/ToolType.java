package com.pixelforge.model;

/**
 * 工具类型枚举。
 *
 * <p>每种工具持有显示名与快捷键；界面按钮、提示气泡、状态栏都从这里取值，
 * 保证显示文案唯一来源。</p>
 */
public enum ToolType {

    BRUSH("画笔", "B"),
    ERASER("橡皮擦", "E"),
    LINE("直线", "L"),
    RECTANGLE("矩形", "R"),
    ELLIPSE("椭圆", "O"),
    POLYGON("多边形", "P"),
    TEXT("文字", "T"),
    FILL("油漆桶", "G"),
    PICKER("取色器", "I"),
    SELECT("选框", "M"),
    LASSO("套索", "S"),
    MOVE("移动", "V"),
    HAND("抓手", "H"),
    ZOOM("缩放", "Z");

    private final String displayName;
    private final String shortcut;

    ToolType(String displayName, String shortcut) {
        this.displayName = displayName;
        this.shortcut = shortcut;
    }

    public String displayName() {
        return displayName;
    }

    public String shortcut() {
        return shortcut;
    }

    /** 提示文案，例如"画笔 (B)"。 */
    public String tooltip() {
        return displayName + " (" + shortcut + ")";
    }

    @Override
    public String toString() {
        return displayName;
    }
}
