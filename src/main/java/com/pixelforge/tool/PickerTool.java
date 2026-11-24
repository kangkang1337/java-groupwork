package com.pixelforge.tool;

import com.pixelforge.model.ToolType;
import com.pixelforge.util.ColorUtil;
import javafx.scene.input.MouseEvent;

/**
 * 取色器：从画布合成结果里取颜色（所见即所得），设成前景色。
 */
public class PickerTool extends Tool {

    public PickerTool(ToolContext ctx) {
        super(ctx);
    }

    @Override
    public ToolType type() {
        return ToolType.PICKER;
    }

    @Override
    public void onPressed(double x, double y, MouseEvent event) {
        int argb = ctx.getCanvasView().getCompositeArgb((int) Math.floor(x), (int) Math.floor(y));
        if (ColorUtil.alpha(argb) == 0) {
            ctx.setStatusMessage("该位置是透明的，没有取到颜色");
            return;
        }
        ctx.setForegroundColor(ColorUtil.toFxColor(argb));
        ctx.setStatusMessage("取色 " + ColorUtil.toHex(ColorUtil.toFxColor(argb)));
    }
}
