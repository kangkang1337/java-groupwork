package com.pixelforge.tool;

import com.pixelforge.model.ToolType;
import javafx.scene.input.MouseEvent;

/**
 * 画布输入处理器：画布把鼠标事件翻译后交给它，由它分发给当前工具。
 */
public interface CanvasInputHandler {

    void onPressed(double x, double y, MouseEvent event);

    void onDragged(double x, double y, MouseEvent event);

    void onReleased(double x, double y, MouseEvent event);

    void onMoved(double x, double y, MouseEvent event);

    void onDoubleClick(double x, double y, MouseEvent event);

    /** 当前工具类型，用于切换鼠标指针。 */
    ToolType currentToolType();
}
