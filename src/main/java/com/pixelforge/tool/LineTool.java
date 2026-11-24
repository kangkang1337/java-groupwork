package com.pixelforge.tool;

import com.pixelforge.model.ToolType;
import com.pixelforge.render.PixelPainter;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;

/**
 * 直线工具：拖动时在覆盖层预览，松手写入图层。
 */
public class LineTool extends DragShapeTool {

    public LineTool(ToolContext ctx) {
        super(ctx);
    }

    @Override
    public ToolType type() {
        return ToolType.LINE;
    }

    @Override
    protected String actionName() {
        return "直线";
    }

    @Override
    protected void drawPreview() {
        GraphicsContext g = overlay();
        g.setStroke(ctx.getForegroundColor());
        g.setLineWidth(Math.max(1, size()));
        g.setLineCap(StrokeLineCap.ROUND);
        g.strokeLine(startX, startY, currentX, currentY);
    }

    @Override
    protected void commit() {
        PixelPainter.strokeSegment(layer(), startX, startY, currentX, currentY,
                radius(), color(), opacity());
    }

    @Override
    public void onDragged(double x, double y, javafx.scene.input.MouseEvent event) {
        super.onDragged(x, y, event);
        if (isShiftDown()) {
            // Shift 约束到水平 / 垂直 / 45°
            double dx = currentX - startX;
            double dy = currentY - startY;
            double angle = Math.round(Math.atan2(dy, dx) / (Math.PI / 4)) * (Math.PI / 4);
            double length = Math.hypot(dx, dy);
            currentX = startX + Math.cos(angle) * length;
            currentY = startY + Math.sin(angle) * length;
            clearOverlay();
            drawPreview();
        }
    }

    /** 预览用的半透明颜色。 */
    static Color previewColor(Color base, double alpha) {
        return new Color(base.getRed(), base.getGreen(), base.getBlue(),
                Math.max(0.15, Math.min(1.0, alpha)));
    }
}
