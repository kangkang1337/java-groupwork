package com.pixelforge.tool;

import com.pixelforge.model.Document;
import com.pixelforge.model.Layer;
import com.pixelforge.ui.CanvasView;
import javafx.scene.paint.Color;

/**
 * 工具运行所需的环境。
 *
 * <p>工具只依赖这个接口，不直接引用主窗口，因此工具可以脱离界面单独测试，
 * 也便于后续把这些参数换成属性绑定。</p>
 */
public interface ToolContext {

    /** 当前文档，可能为 null。 */
    Document getDocument();

    /** 当前活动图层，可能为 null。 */
    Layer getActiveLayer();

    /** 前景色。 */
    Color getForegroundColor();

    /** 设置前景色（取色器会用到）。 */
    void setForegroundColor(Color color);

    /** 笔刷直径 / 线宽（像素）。 */
    double getBrushSize();

    /** 工具不透明度 0..1。 */
    double getOpacity();

    /** 形状工具是否使用实心填充。 */
    boolean isFilled();

    /** 在状态栏显示一条消息。 */
    void setStatusMessage(String message);

    /** 画布视图。 */
    CanvasView getCanvasView();

    /** 开始一次可撤销的编辑。 */
    void beginEdit(String actionName);

    /** 结束一次编辑（提交撤销记录）。 */
    void endEdit();

    /** 请求重绘画布。 */
    void requestRender();

    /**
     * 弹出文本输入框（文字工具用）。
     *
     * @return 用户输入的文本；取消时返回 {@code null}
     */
    String askText();

    /** 选区与浮动内容管理器。 */
    SelectionManager getSelectionManager();
}
