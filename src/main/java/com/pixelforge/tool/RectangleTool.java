package com.pixelforge.tool;

import com.pixelforge.model.ToolType;
import com.pixelforge.render.PixelPainter;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;

/**
 * 矩形工具：按住 Shift 画正方形，可选描边或填充。
 */
public class RectangleTool extends DragShapeTool {

    public RectangleTool(ToolContext ctx) {
        super(ctx);
    }

    @Override
    public ToolType type() {
        return ToolType.RECTANGLE;
    }

    @Override
    protected String actionName() {
        return "矩形";
    }

    /** 归一化的左上 / 右下坐标（支持反向拖动与 Shift 约束）。 */
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
            g.fillRect(b[0], b[1], b[2] - b[0], b[3] - b[1]);
        }
        g.setStroke(Color.web("#FFFFFF", 0.85));
        g.setLineWidth(1.0);
        g.setLineDashes(4, 4);
        g.strokeRect(b[0], b[1], b[2] - b[0], b[3] - b[1]);
        g.setLineDashes();
    }

    @Override
    protected void commit() {
        double[] b = bounds();
        if (ctx.isFilled()) {
            PixelPainter.fillRect(layer(), b[0], b[1], b[2], b[3], color(), opacity());
        } else {
            PixelPainter.strokeRect(layer(), b[0], b[1], b[2], b[3], size(), color(), opacity());
        }
    }

    static void strokeCap(javafx.scene.canvas.GraphicsContext g) {
        g.setLineCap(StrokeLineCap.ROUND);
    }
}
