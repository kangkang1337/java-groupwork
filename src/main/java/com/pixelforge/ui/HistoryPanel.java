package com.pixelforge.ui;

import com.pixelforge.command.UndoManager;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.VBox;

/**
 * 历史记录面板：列出所有操作步骤，点击任意一步可以直接跳回去。
 */
public class HistoryPanel {

    private static final String BASELINE = "初始状态";

    private final VBox root = new VBox(6);
    private final ListView<String> list = new ListView<>();
    private final Label hint = new Label("点击任意一步可回到该状态");

    private UndoManager undoManager;
    private boolean updating;

    public HistoryPanel() {
        hint.getStyleClass().add("hint-label");
        list.setPrefHeight(130);
        list.setMinHeight(80);
        list.getStyleClass().add("history-list");
        list.setFixedCellSize(24);

        list.getSelectionModel().selectedIndexProperty().addListener((obs, oldIndex, newIndex) -> {
            if (updating || undoManager == null || newIndex == null) {
                return;
            }
            int target = newIndex.intValue() - 1;
            if (target != undoManager.getCursor()) {
                undoManager.jumpTo(target);
            }
        });

        root.getChildren().addAll(list, hint);
        root.setPadding(new Insets(2, 0, 0, 0));
    }

    public void setUndoManager(UndoManager manager) {
        this.undoManager = manager;
        refresh();
    }

    /** 刷新条目与当前高亮。 */
    public void refresh() {
        updating = true;
        try {
            list.getItems().clear();
            if (undoManager == null) {
                return;
            }
            list.getItems().add(BASELINE);
            for (var command : undoManager.getHistory()) {
                list.getItems().add(command.name());
            }
            int index = undoManager.getCursor() + 1;
            if (index >= 0 && index < list.getItems().size()) {
                list.getSelectionModel().select(index);
                list.scrollTo(index);
            } else {
                list.getSelectionModel().clearSelection();
            }
        } finally {
            updating = false;
        }
    }

    public Node getRoot() {
        return root;
    }

    /** 当前条目数（自检用）。 */
    public int getEntryCount() {
        return list.getItems().size();
    }
}
