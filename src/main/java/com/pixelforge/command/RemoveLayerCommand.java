package com.pixelforge.command;

import com.pixelforge.model.Document;
import com.pixelforge.model.Layer;

/**
 * 删除图层（撤销时按原位置插回）。
 */
public class RemoveLayerCommand implements Command {

    private final Layer layer;
    private final int index;

    public RemoveLayerCommand(Layer layer, int index) {
        this.layer = layer;
        this.index = index;
    }

    @Override
    public String name() {
        return "删除图层";
    }

    @Override
    public void undo(Document document) {
        document.addLayer(layer, index);
    }

    @Override
    public void redo(Document document) {
        document.removeLayer(document.getLayers().indexOf(layer));
    }
}
