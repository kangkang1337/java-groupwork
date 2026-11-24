package com.pixelforge.command;

import com.pixelforge.model.Document;

import java.util.ArrayList;
import java.util.List;

/**
 * 撤销 / 重做管理器。
 *
 * <p>历史用"线性列表 + 游标"表示：{@code cursor} 指向最后一次生效的命令，
 * {@code -1} 表示回到初始状态。这样历史面板可以直接列出全部步骤并支持跳转。</p>
 */
public class UndoManager {

    /** 最多保留的步骤数。 */
    private static final int MAX_STEPS = 80;
    /** 历史记录占用的内存上限（约 192MB）。 */
    private static final long MAX_WEIGHT = 192L * 1024 * 1024;

    private final List<Command> history = new ArrayList<>();
    private final Document document;
    private int cursor = -1;
    private Runnable changeListener;
    private boolean applying;

    public UndoManager(Document document) {
        this.document = document;
    }

    /** 绑定文档（新建/打开新文档时重设历史）。 */
    public void reset(Document newDocument) {
        history.clear();
        cursor = -1;
        notifyChanged();
    }

    public void setChangeListener(Runnable listener) {
        this.changeListener = listener;
    }

    private void notifyChanged() {
        if (changeListener != null) {
            changeListener.run();
        }
    }

    /** 记录一步操作，会丢弃游标之后的重做分支。 */
    public void push(Command command) {
        if (command == null || applying) {
            return;
        }
        while (history.size() > cursor + 1) {
            history.remove(history.size() - 1);
        }
        history.add(command);
        cursor = history.size() - 1;
        trim();
        notifyChanged();
    }

    private void trim() {
        while (history.size() > MAX_STEPS) {
            history.remove(0);
            cursor--;
        }
        long total = 0;
        for (Command command : history) {
            total += command.weight();
        }
        while (total > MAX_WEIGHT && history.size() > 1) {
            total -= history.get(0).weight();
            history.remove(0);
            cursor--;
        }
    }

    public boolean canUndo() {
        return cursor >= 0;
    }

    public boolean canRedo() {
        return cursor < history.size() - 1;
    }

    /** 撤销一步。 */
    public boolean undo() {
        if (!canUndo()) {
            return false;
        }
        applying = true;
        try {
            history.get(cursor).undo(document);
            cursor--;
        } finally {
            applying = false;
        }
        document.markDirty();
        document.notifyChanged();
        notifyChanged();
        return true;
    }

    /** 重做一步。 */
    public boolean redo() {
        if (!canRedo()) {
            return false;
        }
        applying = true;
        try {
            cursor++;
            history.get(cursor).redo(document);
        } finally {
            applying = false;
        }
        document.markDirty();
        document.notifyChanged();
        notifyChanged();
        return true;
    }

    /**
     * 直接跳到某个历史位置。
     *
     * @param targetCursor -1 表示初始状态，其余为命令下标
     */
    public void jumpTo(int targetCursor) {
        int target = Math.max(-1, Math.min(history.size() - 1, targetCursor));
        while (cursor > target) {
            undo();
        }
        while (cursor < target) {
            redo();
        }
    }

    /** 当前游标位置（-1 表示初始状态）。 */
    public int getCursor() {
        return cursor;
    }

    public List<Command> getHistory() {
        return java.util.Collections.unmodifiableList(history);
    }

    public String peekUndoName() {
        return canUndo() ? history.get(cursor).name() : null;
    }

    public String peekRedoName() {
        return canRedo() ? history.get(cursor + 1).name() : null;
    }
}
