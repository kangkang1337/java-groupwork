package com.pixelforge.tool;

import com.pixelforge.model.ToolType;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;

/**
 * 套索：按住拖出任意形状，松手后把轨迹围成的区域变成选区。
 */
public class LassoTool extends Tool {

    private final List<double[]> points = new ArrayList<>();
    private boolean drawing;

    public LassoTool(ToolContext ctx) {
        super(ctx);
    }

    @Override
    public ToolType type() {
        return ToolType.LASSO;
    }

    @Override
    public void onPressed(double x, double y, MouseEvent event) {
        if (ctx.getDocument() == null) {
            return;
        }
        drawing = true;
        points.clear();
        points.add(new double[]{x, y});
        clearOverlay();
    }

    @Override
    public void onDragged(double x, double y, MouseEvent event) {
        if (!drawing) {
            return;
        }
        double[] last = points.get(points.size() - 1);
        if (Math.hypot(x - last[0], y - last[1]) < 1.5) {
            return;
        }
        points.add(new double[]{x, y});
        drawPreview();
    }

    @Override
    public void onReleased(double x, double y, MouseEvent event) {
        if (!drawing) {
            return;
        }
        drawing = false;
        clearOverlay();
        points.add(new double[]{x, y});

        if (points.size() < 3) {
            ctx.getSelectionManager().clearSelection();
            return;
        }
        int n = points.size();
        double[] xs = new double[n];
        double[] ys = new double[n];
        for (int i = 0; i < n; i++) {
            xs[i] = points.get(i)[0];
            ys[i] = points.get(i)[1];
        }
        ctx.getSelectionManager().selectPolygon(xs, ys);
        ctx.setStatusMessage("套索选区 "
                + ctx.getSelectionManager().getSelection().getWidth() + " × "
                + ctx.getSelectionManager().getSelection().getHeight());
        points.clear();
    }

    private void drawPreview() {
        if (points.size() < 2) {
            return;
        }
        GraphicsContext g = overlay();
        clearOverlay();
        g.setLineWidth(1.2 / Math.max(0.2, ctx.getCanvasView().getZoom()));
        g.setStroke(Color.web("#FFFFFF", 0.95));
        g.beginPath();
        g.moveTo(points.get(0)[0], points.get(0)[1]);
        for (int i = 1; i < points.size(); i++) {
            g.lineTo(points.get(i)[0], points.get(i)[1]);
        }
        g.stroke();
        g.setStroke(Color.web("#000000", 0.55));
        g.setLineDashes(4, 4);
        g.beginPath();
        g.moveTo(points.get(0)[0], points.get(0)[1]);
        for (int i = 1; i < points.size(); i++) {
            g.lineTo(points.get(i)[0], points.get(i)[1]);
        }
        g.stroke();
        g.setLineDashes();
    }

    @Override
    public void cancel() {
        drawing = false;
        points.clear();
        super.cancel();
    }
}
