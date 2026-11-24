package com.pixelforge.command;

import com.pixelforge.model.Document;

import java.util.ArrayList;
import java.util.List;

/**
 * 组合命令：把若干命令打包成一步撤销。
 */
public class CompositeCommand implements Command {

    private final String actionName;
    private final List<Command> children;

    public CompositeCommand(String actionName, List<Command> children) {
        this.actionName = actionName;
        this.children = new ArrayList<>(children);
    }

    public List<Command> getChildren() {
        return children;
    }

    @Override
    public String name() {
        return actionName;
    }

    @Override
    public void undo(Document document) {
        for (int i = children.size() - 1; i >= 0; i--) {
            children.get(i).undo(document);
        }
    }

    @Override
    public void redo(Document document) {
        for (Command child : children) {
            child.redo(document);
        }
    }

    @Override
    public long weight() {
        long total = 0;
        for (Command child : children) {
            total += child.weight();
        }
        return total;
    }
}
