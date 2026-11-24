package com.pixelforge.tool;

import com.pixelforge.model.ToolType;
import com.pixelforge.render.PixelPainter;
import javafx.scene.input.MouseEvent;

/**
 * 画笔：按住拖动连续落笔，笔迹为圆头胶囊体，边缘带抗锯齿。
 */
public class BrushTool extends Tool {

    private double lastX;
    private double lastY;
    private boolean painting;

    public BrushTool(ToolContext ctx) {
        super(ctx);
    }

    @Override
    public ToolType type() {
        return ToolType.BRUSH;
    }

    @Override
    public void onPressed(double x, double y, MouseEvent event) {
        if (!hasLayer()) {
            return;
        }
        painting = true;
        lastX = x;
        lastY = y;
        ctx.beginEdit("画笔");
        PixelPainter.strokeSegment(layer(), x, y, x, y, radius(), color(), opacity());
        requestRender();
    }

    @Override
    public void onDragged(double x, double y, MouseEvent event) {
        if (!painting) {
            return;
        }
        PixelPainter.strokeSegment(layer(), lastX, lastY, x, y, radius(), color(), opacity());
        lastX = x;
        lastY = y;
        requestRender();
    }

    @Override
    public void onReleased(double x, double y, MouseEvent event) {
        if (!painting) {
            return;
        }
        PixelPainter.strokeSegment(layer(), lastX, lastY, x, y, radius(), color(), opacity());
        painting = false;
        ctx.endEdit();
        requestRender();
    }

    @Override
    public void cancel() {
        painting = false;
        super.cancel();
    }
}
