package com.pixelforge.tool;

import com.pixelforge.model.ToolType;
import com.pixelforge.render.PixelPainter;
import javafx.scene.input.MouseEvent;

/**
 * 油漆桶：从点击处向外做扫描线洪水填充，带颜色容差以处理抗锯齿边缘。
 */
public class FillTool extends Tool {

    /** 默认颜色容差。 */
    private static final int TOLERANCE = 32;

    public FillTool(ToolContext ctx) {
        super(ctx);
    }

    @Override
    public ToolType type() {
        return ToolType.FILL;
    }

    @Override
    public void onPressed(double x, double y, MouseEvent event) {
        if (!hasLayer()) {
            return;
        }
        ctx.beginEdit("油漆桶");
        int filled = PixelPainter.floodFill(layer(), (int) Math.floor(x), (int) Math.floor(y),
                color(), opacity(), TOLERANCE);
        ctx.endEdit();
        requestRender();
        ctx.setStatusMessage(filled > 0 ? "已填充 " + filled + " 个像素" : "该区域已是目标颜色");
    }
}
