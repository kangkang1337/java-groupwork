package com.pixelforge.tool;

import com.pixelforge.model.ToolType;
import javafx.scene.Cursor;

/**
 * 抓手：按住左键拖动平移画布（平移逻辑由画布视图统一处理）。
 */
public class HandTool extends Tool {

    public HandTool(ToolContext ctx) {
        super(ctx);
    }

    @Override
    public ToolType type() {
        return ToolType.HAND;
    }

    @Override
    public Cursor cursor() {
        return Cursor.OPEN_HAND;
    }
}
