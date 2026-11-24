package com.pixelforge.tool;

import com.pixelforge.model.Layer;
import com.pixelforge.model.ToolType;
import com.pixelforge.util.ColorUtil;
import javafx.scene.Cursor;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseEvent;

/**
 * 工具基类。
 *
 * <p>画布把鼠标事件翻译成"文档坐标 + 原始事件"再交给工具，工具只关心
 * 自己怎么画。默认实现全部为空，子类按需覆盖。</p>
 */
public abstract class Tool {

    protected final ToolContext ctx;

    protected Tool(ToolContext ctx) {
        this.ctx = ctx;
    }

    /** 对应的工具类型。 */
    public abstract ToolType type();

    public void onPressed(double x, double y, MouseEvent event) {
    }

    public void onDragged(double x, double y, MouseEvent event) {
    }

    public void onReleased(double x, double y, MouseEvent event) {
    }

    public void onMoved(double x, double y, MouseEvent event) {
    }

    public void onDoubleClick(double x, double y, MouseEvent event) {
    }

    /** 切换工具 / 取消操作时清理临时状态。 */
    public void cancel() {
        clearOverlay();
    }

    /** 该工具使用的鼠标指针。 */
    public Cursor cursor() {
        return Cursor.CROSSHAIR;
    }

    // ------------------------------------------------------------------
    // 便捷方法
    // ------------------------------------------------------------------

    protected Layer layer() {
        return ctx.getActiveLayer();
    }

    protected boolean hasLayer() {
        return ctx.getDocument() != null && ctx.getActiveLayer() != null;
    }

    protected int color() {
        return ColorUtil.toArgb(ctx.getForegroundColor());
    }

    protected double opacity() {
        return ctx.getOpacity();
    }

    protected double size() {
        return ctx.getBrushSize();
    }

    protected double radius() {
        return Math.max(0.5, size() / 2.0);
    }

    protected GraphicsContext overlay() {
        return ctx.getCanvasView().getOverlay().getGraphicsContext2D();
    }

    protected void clearOverlay() {
        var canvas = ctx.getCanvasView().getOverlay();
        overlay().clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
    }

    protected void requestRender() {
        ctx.requestRender();
    }
}
