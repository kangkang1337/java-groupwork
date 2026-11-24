package com.pixelforge.tool;

import com.pixelforge.model.ToolType;
import com.pixelforge.render.PixelPainter;
import javafx.scene.input.MouseEvent;

/**
 * 文字工具：点击位置弹出输入框，确认后把文字栅格化进当前图层。
 *
 * <p>字号直接复用"粗细"参数，避免再加一个控件。</p>
 */
public class TextTool extends Tool {

    /** 渲染用的字体族，Windows 自带雅黑，缺省时 AWT 会自动回退。 */
    private static final String FONT_FAMILY = "Microsoft YaHei";

    public TextTool(ToolContext ctx) {
        super(ctx);
    }

    @Override
    public ToolType type() {
        return ToolType.TEXT;
    }

    @Override
    public void onPressed(double x, double y, MouseEvent event) {
        if (!hasLayer()) {
            return;
        }
        String text = ctx.askText();
        if (text == null || text.isBlank()) {
            return;
        }
        int fontSize = (int) Math.max(8, size());
        ctx.beginEdit("文字");
        PixelPainter.drawText(layer(), text, FONT_FAMILY, fontSize, false,
                color(), (int) Math.round(x), (int) Math.round(y));
        ctx.endEdit();
        requestRender();
        ctx.setStatusMessage("已写入文字（字号 " + fontSize + "）");
    }
}
