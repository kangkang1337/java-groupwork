package com.pixelforge.tool;

import com.pixelforge.model.ToolType;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;

/**
 * 矩形选框：拖出一个矩形选区，松手后生效。
 */
public class MarqueeTool extends Tool {

    private double startX;
    private double startY;
    private double currentX;
    private double currentY;
    private boolean dragging;

    public MarqueeTool(ToolContext ctx) {
        super(ctx);
    }

    @Override
    public ToolType type() {
        return ToolType.SELECT;
    }

    @Override
    public void onPressed(double x, double y, MouseEvent event) {
        if (ctx.getDocument() == null) {
            return;
        }
        dragging = true;
        startX = x;
        startY = y;
        currentX = x;
        currentY = y;
        clearOverlay();
    }

    @Override
    public void onDragged(double x, double y, MouseEvent event) {
        if (!dragging) {
            return;
        }
        currentX = x;
        currentY = y;
        drawPreview(event.isShiftDown());
    }

    @Override
    public void onReleased(double x, double y, MouseEvent event) {
        if (!dragging) {
            return;
        }
        dragging = false;
        clearOverlay();
        currentX = x;
        currentY = y;

        double w = Math.abs(currentX - startX);
        double h = Math.abs(currentY - startY);
        if (w < 1 || h < 1) {
            // 单击（没有拖动）视为取消选择
            ctx.getSelectionManager().clearSelection();
            ctx.setStatusMessage("已取消选择");
            return;
        }
        double x1 = currentX;
        double y1 = currentY;
        if (event.isShiftDown()) {
            double side = Math.max(w, h);
            x1 = startX + (currentX >= startX ? side : -side);
            y1 = startY + (currentY >= startY ? side : -side);
        }
        ctx.getSelectionManager().selectRect(startX, startY, x1, y1);
        ctx.setStatusMessage("已建立选区 "
                + ctx.getSelectionManager().getSelection().getWidth() + " × "
                + ctx.getSelectionManager().getSelection().getHeight());
    }

    @Override
    public void onMoved(double x, double y, MouseEvent event) {
        if (dragging) {
            drawPreview(event.isShiftDown());
        }
    }

    private void drawPreview(boolean square) {
        GraphicsContext g = overlay();
        clearOverlay();
        double w = currentX - startX;
        double h = currentY - startY;
        if (square) {
            double side = Math.max(Math.abs(w), Math.abs(h));
            w = w >= 0 ? side : -side;
            h = h >= 0 ? side : -side;
        }
        g.setLineWidth(1.0 / Math.max(0.2, ctx.getCanvasView().getZoom()));
        g.setLineDashes(5, 4);
        g.setStroke(Color.web("#FFFFFF", 0.95));
        g.strokeRect(Math.min(startX, startX + w), Math.min(startY, startY + h),
                Math.abs(w), Math.abs(h));
        g.setStroke(Color.web("#000000", 0.6));
        g.setLineDashOffset(4);
        g.strokeRect(Math.min(startX, startX + w), Math.min(startY, startY + h),
                Math.abs(w), Math.abs(h));
        g.setLineDashes();
        g.setLineDashOffset(0);
    }

    @Override
    public void cancel() {
        dragging = false;
        super.cancel();
    }
}
