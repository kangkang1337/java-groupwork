package com.pixelforge.command;

import com.pixelforge.model.Document;
import com.pixelforge.model.Layer;

/**
 * 像素修改命令：只保存被改动矩形区域的前后像素，而不是整层快照。
 *
 * <p>画笔一笔通常只覆盖很小的区域，这样一次撤销记录只有几十 KB，
 * 几十步历史也不会撑爆内存。</p>
 */
public class PixelEditCommand implements Command {

    private final Layer layer;
    private final String actionName;
    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final int[] before;
    private final int[] after;

    private PixelEditCommand(Layer layer, String actionName, int x, int y, int width, int height,
                             int[] before, int[] after) {
        this.layer = layer;
        this.actionName = actionName;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.before = before;
        this.after = after;
    }

    /**
     * 通过"改动前快照 + 当前像素"生成命令；没有变化时返回 {@code null}。
     *
     * @param layer      被修改的图层
     * @param snapshot   操作开始前该图层像素的拷贝
     * @param actionName 命令名
     */
    public static PixelEditCommand capture(Layer layer, int[] snapshot, String actionName) {
        if (layer == null || snapshot == null) {
            return null;
        }
        int[] current = layer.getPixels();
        if (snapshot.length != current.length) {
            return null;
        }
        int layerWidth = layer.getWidth();
        int layerHeight = layer.getHeight();

        int minX = layerWidth;
        int minY = layerHeight;
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < layerHeight; y++) {
            int row = y * layerWidth;
            for (int x = 0; x < layerWidth; x++) {
                if (snapshot[row + x] != current[row + x]) {
                    if (x < minX) {
                        minX = x;
                    }
                    if (x > maxX) {
                        maxX = x;
                    }
                    if (y < minY) {
                        minY = y;
                    }
                    if (y > maxY) {
                        maxY = y;
                    }
                }
            }
        }
        if (maxX < 0) {
            return null;
        }

        int w = maxX - minX + 1;
        int h = maxY - minY + 1;
        int[] beforeRect = new int[w * h];
        int[] afterRect = new int[w * h];
        for (int y = 0; y < h; y++) {
            int srcRow = (minY + y) * layerWidth + minX;
            System.arraycopy(snapshot, srcRow, beforeRect, y * w, w);
            System.arraycopy(current, srcRow, afterRect, y * w, w);
        }
        return new PixelEditCommand(layer, actionName, minX, minY, w, h, beforeRect, afterRect);
    }

    @Override
    public String name() {
        return actionName;
    }

    @Override
    public void undo(Document document) {
        apply(before);
    }

    @Override
    public void redo(Document document) {
        apply(after);
    }

    private void apply(int[] source) {
        int[] dst = layer.getPixels();
        int layerWidth = layer.getWidth();
        for (int row = 0; row < height; row++) {
            int dstRow = (y + row) * layerWidth + x;
            System.arraycopy(source, row * width, dst, dstRow, width);
        }
    }

    @Override
    public long weight() {
        return (long) before.length * 8L;
    }
}
