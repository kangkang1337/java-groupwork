package com.pixelforge.tool;

import com.pixelforge.model.ToolType;
import com.pixelforge.render.PixelPainter;
import javafx.scene.input.MouseEvent;

/**
 * 橡皮擦：把像素的 alpha 按笔刷覆盖率削弱，擦成透明而不是涂白。
 */
public class EraserTool extends Tool {

    private double lastX;
    private double lastY;
    private boolean erasing;

    public EraserTool(ToolContext ctx) {
        super(ctx);
    }

    @Override
    public ToolType type() {
        return ToolType.ERASER;
    }

    @Override
    public void onPressed(double x, double y, MouseEvent event) {
        if (!hasLayer()) {
            return;
        }
        erasing = true;
        lastX = x;
        lastY = y;
        ctx.beginEdit("橡皮擦");
        PixelPainter.eraseSegment(layer(), x, y, x, y, radius(), opacity());
        requestRender();
    }

    @Override
    public void onDragged(double x, double y, MouseEvent event) {
        if (!erasing) {
            return;
        }
        PixelPainter.eraseSegment(layer(), lastX, lastY, x, y, radius(), opacity());
        lastX = x;
        lastY = y;
        requestRender();
    }

    @Override
    public void onReleased(double x, double y, MouseEvent event) {
        if (!erasing) {
            return;
        }
        PixelPainter.eraseSegment(layer(), lastX, lastY, x, y, radius(), opacity());
        erasing = false;
        ctx.endEdit();
        requestRender();
    }

    @Override
    public void cancel() {
        erasing = false;
        super.cancel();
    }
}
