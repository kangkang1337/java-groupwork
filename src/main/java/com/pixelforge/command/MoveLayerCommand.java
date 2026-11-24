package com.pixelforge.command;

import com.pixelforge.model.Document;

/**
 * 图层顺序调整（拖拽排序）。
 */
public class MoveLayerCommand implements Command {

    private final int from;
    private final int to;

    public MoveLayerCommand(int from, int to) {
        this.from = from;
        this.to = to;
    }

    @Override
    public String name() {
        return "调整图层顺序";
    }

    @Override
    public void undo(Document document) {
        document.moveLayer(to, from);
    }

    @Override
    public void redo(Document document) {
        document.moveLayer(from, to);
    }
}
