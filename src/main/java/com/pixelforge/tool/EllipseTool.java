package com.pixelforge.tool;

import com.pixelforge.model.ToolType;
import com.pixelforge.render.PixelPainter;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

/**
 * 椭圆工具：按住 Shift 画正圆，可选描边或填充。
 */
public class EllipseTool extends DragShapeTool {

    public EllipseTool(ToolContext ctx) {
        super(ctx);
    }

    @Override
    public ToolType type() {
        return ToolType.ELLIPSE;
    }

    @Override
    protected String actionName() {
        return "椭圆";
    }

    private double[] bounds() {
        double x1 = currentX;
        double y1 = currentY;
        if (isShiftDown()) {
            double w = x1 - startX;
            double h = y1 - startY;
            double side = Math.max(Math.abs(w), Math.abs(h));
            x1 = startX + (w >= 0 ? side : -side);
            y1 = startY + (h >= 0 ? side : -side);
        }
        return new double[]{
                Math.min(startX, x1), Math.min(startY, y1),
                Math.max(startX, x1), Math.max(startY, y1)
        };
    }

    @Override
    protected void drawPreview() {
        double[] b = bounds();
        GraphicsContext g = overlay();
        Color color = ctx.getForegroundColor();
        if (ctx.isFilled()) {
            g.setFill(new Color(color.getRed(), color.getGreen(), color.getBlue(),
                    Math.max(0.2, opacity() * 0.6)));
            g.fillOval(b[0], b[1], b[2] - b[0], b[3] - b[1]);
        }
        g.setStroke(Color.web("#FFFFFF", 0.85));
        g.setLineWidth(1.0);
        g.setLineDashes(4, 4);
        g.strokeOval(b[0], b[1], b[2] - b[0], b[3] - b[1]);
        g.setLineDashes();
    }

    @Override
    protected void commit() {
        double[] b = bounds();
        double cx = (b[0] + b[2]) / 2.0;
        double cy = (b[1] + b[3]) / 2.0;
        double rx = (b[2] - b[0]) / 2.0;
        double ry = (b[3] - b[1]) / 2.0;
        if (ctx.isFilled()) {
            PixelPainter.fillEllipse(layer(), cx, cy, rx, ry, color(), opacity());
        } else {
            PixelPainter.strokeEllipse(layer(), cx, cy, rx, ry, size(), color(), opacity());
        }
    }
}
