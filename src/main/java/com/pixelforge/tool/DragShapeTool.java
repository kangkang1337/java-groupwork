package com.pixelforge.tool;

import javafx.scene.input.MouseEvent;

/**
 * 拖拽式形状工具的公共骨架：按下 → 拖动预览 → 松开提交。
 *
 * <p>预览画在覆盖层画布上，不污染图层像素；只有松手时才真正写进图层。</p>
 */
public abstract class DragShapeTool extends Tool {

    protected double startX;
    protected double startY;
    protected double currentX;
    protected double currentY;
    protected boolean dragging;
    protected boolean shiftDown;

    protected DragShapeTool(ToolContext ctx) {
        super(ctx);
    }

    /** 撤销记录里显示的操作名。 */
    protected abstract String actionName();

    /** 在覆盖层上画预览。 */
    protected abstract void drawPreview();

    /** 把图形提交到图层。 */
    protected abstract void commit();

    @Override
    public void onPressed(double x, double y, MouseEvent event) {
        if (!hasLayer()) {
            return;
        }
        dragging = true;
        shiftDown = event.isShiftDown();
        startX = x;
        startY = y;
        currentX = x;
        currentY = y;
        ctx.beginEdit(actionName());
        clearOverlay();
    }

    @Override
    public void onDragged(double x, double y, MouseEvent event) {
        if (!dragging) {
            return;
        }
        shiftDown = event.isShiftDown();
        currentX = x;
        currentY = y;
        clearOverlay();
        drawPreview();
    }

    @Override
    public void onReleased(double x, double y, MouseEvent event) {
        if (!dragging) {
            return;
        }
        currentX = x;
        currentY = y;
        dragging = false;
        clearOverlay();
        commit();
        ctx.endEdit();
        requestRender();
    }

    @Override
    public void cancel() {
        dragging = false;
        super.cancel();
    }

    /** 预览时是否按住 Shift（用于画正方形 / 正圆）。 */
    protected boolean isShiftDown() {
        return shiftDown;
    }
}
