package com.pixelforge.command;

import com.pixelforge.model.BlendMode;
import com.pixelforge.model.Document;
import com.pixelforge.model.Layer;

/**
 * 图层属性命令：名称 / 可见性 / 不透明度 / 混合模式的变化。
 */
public class LayerPropertyCommand implements Command {

    /** 图层属性的值快照。 */
    public record State(String name, boolean visible, double opacity, BlendMode blendMode) {
    }

    private final Layer layer;
    private final String actionName;
    private final State before;
    private final State after;

    private LayerPropertyCommand(Layer layer, String actionName, State before, State after) {
        this.layer = layer;
        this.actionName = actionName;
        this.before = before;
        this.after = after;
    }

    /** 取当前属性快照。 */
    public static State snapshot(Layer layer) {
        return new State(layer.getName(), layer.isVisible(), layer.getOpacity(), layer.getBlendMode());
    }

    /**
     * 用"改动前的快照 + 现在的状态"生成命令；没有变化返回 {@code null}。
     */
    public static LayerPropertyCommand capture(Layer layer, State before, String actionName) {
        if (layer == null || before == null) {
            return null;
        }
        State after = snapshot(layer);
        if (before.equals(after)) {
            return null;
        }
        return new LayerPropertyCommand(layer, actionName, before, after);
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

    private void apply(State state) {
        layer.setName(state.name());
        layer.setVisible(state.visible());
        layer.setOpacity(state.opacity());
        layer.setBlendMode(state.blendMode());
    }
}
