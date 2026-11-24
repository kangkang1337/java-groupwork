package com.pixelforge.tool;

import com.pixelforge.model.ToolType;
import com.pixelforge.render.PixelPainter;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;

/**
 * 多边形工具：逐点单击定顶点，双击闭合。
 *
 * <p>顶点会实时以虚线预览，双击后一次性写入图层。</p>
 */
public class PolygonTool extends Tool {

    private final List<double[]> points = new ArrayList<>();
    private double cursorX;
    private double cursorY;
    private boolean active;

    public PolygonTool(ToolContext ctx) {
        super(ctx);
    }

    @Override
    public ToolType type() {
        return ToolType.POLYGON;
    }

    @Override
    public void onPressed(double x, double y, MouseEvent event) {
        if (!hasLayer()) {
            return;
        }
        if (event.getClickCount() >= 2) {
            finish();
            return;
        }
        if (!active) {
            active = true;
            points.clear();
            clearOverlay();
            ctx.beginEdit("多边形");
        }
        points.add(new double[]{x, y});
        cursorX = x;
        cursorY = y;
        drawPreview();
    }

    @Override
    public void onMoved(double x, double y, MouseEvent event) {
        if (!active) {
            return;
        }
        cursorX = x;
        cursorY = y;
        drawPreview();
    }

    @Override
    public void onReleased(double x, double y, MouseEvent event) {
        // 多边形靠单击累积顶点，松手无需处理
    }

    @Override
    public void cancel() {
        points.clear();
        active = false;
        super.cancel();
    }

    private void drawPreview() {
        if (points.isEmpty()) {
            return;
        }
        GraphicsContext g = overlay();
        clearOverlay();
        g.setStroke(Color.web("#FFFFFF", 0.9));
        g.setLineWidth(1.0);
        g.setLineDashes(4, 4);
        for (int i = 1; i < points.size(); i++) {
            g.strokeLine(points.get(i - 1)[0], points.get(i - 1)[1],
                    points.get(i)[0], points.get(i)[1]);
        }
        double[] last = points.get(points.size() - 1);
        g.strokeLine(last[0], last[1], cursorX, cursorY);
        if (points.size() >= 3) {
            double[] first = points.get(0);
            g.strokeLine(cursorX, cursorY, first[0], first[1]);
        }
        g.setLineDashes();
        g.setFill(ctx.getForegroundColor());
        for (double[] point : points) {
            g.fillOval(point[0] - 2.5, point[1] - 2.5, 5, 5);
        }
    }

    private void finish() {
        if (!active) {
            return;
        }
        int count = points.size();
        if (count >= 3) {
            double[] xs = new double[count];
            double[] ys = new double[count];
            for (int i = 0; i < count; i++) {
                xs[i] = points.get(i)[0];
                ys[i] = points.get(i)[1];
            }
            if (ctx.isFilled()) {
                PixelPainter.fillPolygon(layer(), xs, ys, color(), opacity());
            } else {
                PixelPainter.strokePolyline(layer(), xs, ys, size(), color(), opacity(), true);
            }
        }
        points.clear();
        active = false;
        clearOverlay();
        ctx.endEdit();
        requestRender();
    }
}
