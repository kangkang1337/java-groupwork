package com.pixelforge.model;

/**
 * 应用内的全部菜单命令。
 *
 * <p>菜单栏、快捷键、右键菜单都翻译成同一种 {@code AppAction}，
 * 主窗口用一处 {@code switch} 分发，避免命令散落在各个界面类里。</p>
 */
public enum AppAction {

    // 文件
    FILE_NEW("新建"),
    FILE_OPEN("打开"),
    FILE_SAVE("保存"),
    FILE_SAVE_AS("另存为"),
    FILE_EXPORT_PNG("导出 PNG"),
    FILE_EXPORT_JPG("导出 JPG"),
    FILE_EXIT("退出"),

    // 编辑
    EDIT_UNDO("撤销"),
    EDIT_REDO("重做"),
    EDIT_CUT("剪切"),
    EDIT_COPY("复制"),
    EDIT_PASTE("粘贴"),
    EDIT_DELETE("删除"),
    EDIT_SELECT_ALL("全选"),
    EDIT_DESELECT("取消选择"),

    // 视图
    VIEW_ZOOM_IN("放大"),
    VIEW_ZOOM_OUT("缩小"),
    VIEW_ZOOM_FIT("适应窗口"),
    VIEW_ZOOM_ACTUAL("实际大小"),
    VIEW_TOGGLE_GRID("显示网格"),

    // 图层
    LAYER_NEW("新建图层"),
    LAYER_DUPLICATE("复制图层"),
    LAYER_DELETE("删除图层"),
    LAYER_MERGE_DOWN("向下合并"),

    // 滤镜与调整
    FILTER_GRAYSCALE("灰度"),
    FILTER_INVERT("反色"),
    FILTER_BLUR("高斯模糊"),
    FILTER_SHARPEN("锐化"),
    FILTER_EMBOSS("浮雕"),
    FILTER_EDGE("边缘检测"),
    FILTER_MOSAIC("马赛克"),
    FILTER_BRIGHTNESS("亮度/对比度"),

    // 帮助
    HELP_SHORTCUTS("快捷键说明"),
    HELP_ABOUT("关于 PixelForge");

    private final String label;

    AppAction(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
