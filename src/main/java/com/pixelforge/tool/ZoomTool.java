package com.pixelforge.tool;

import com.pixelforge.model.ToolType;
import javafx.scene.Cursor;
import javafx.scene.input.MouseEvent;

/**
 * 缩放工具：左键点击放大，按住 Alt 点击缩小。
 */
public class ZoomTool extends Tool {

    public ZoomTool(ToolContext ctx) {
        super(ctx);
    }

    @Override
    public ToolType type() {
        return ToolType.ZOOM;
    }

    @Override
    public Cursor cursor() {
        return Cursor.CROSSHAIR;
    }

    @Override
    public void onPressed(double x, double y, MouseEvent event) {
        if (event.isAltDown()) {
            ctx.getCanvasView().zoomOut();
        } else {
            ctx.getCanvasView().zoomIn();
        }
        ctx.setStatusMessage("缩放 " + Math.round(ctx.getCanvasView().getZoom() * 100) + "%");
    }
}
