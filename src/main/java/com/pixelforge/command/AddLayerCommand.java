package com.pixelforge.command;

import com.pixelforge.model.Document;
import com.pixelforge.model.Layer;

/**
 * 新增图层（撤销时删除，重做时按原位置插回）。
 */
public class AddLayerCommand implements Command {

    private final Layer layer;
    private final int index;

    public AddLayerCommand(Layer layer, int index) {
        this.layer = layer;
        this.index = index;
    }

    @Override
    public String name() {
        return "新建图层";
    }

    @Override
    public void undo(Document document) {
        document.removeLayer(index);
    }

    @Override
    public void redo(Document document) {
        document.addLayer(layer, index);
    }
}
