package com.pixelforge.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

/**
 * 底部状态栏。
 *
 * <p>左侧显示提示消息，右侧显示画布尺寸、缩放比例、当前工具与光标坐标。</p>
 */
public class StatusBar {

    private final HBox root = new HBox(8);
    private final Label message = new Label("就绪");
    private final Label canvasSize = new Label("—");
    private final Label zoom = new Label("—");
    private final Label tool = new Label("—");
    private final Label cursor = new Label("—");

    public StatusBar() {
        root.getStyleClass().add("status-bar");
        root.setAlignment(Pos.CENTER_LEFT);
        root.setPadding(new Insets(4, 12, 4, 12));

        message.getStyleClass().add("status-message");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        for (Label label : new Label[]{canvasSize, zoom, tool, cursor}) {
            label.getStyleClass().add("status-label");
        }

        root.getChildren().addAll(message, spacer,
                canvasSize, divider(), zoom, divider(), tool, divider(), cursor);
    }

    private static Label divider() {
        Label label = new Label("│");
        label.getStyleClass().add("status-divider");
        return label;
    }

    public Node getRoot() {
        return root;
    }

    /** 显示一条临时提示。 */
    public void setMessage(String text) {
        message.setText(text == null ? "" : text);
    }

    /** 画布尺寸，例如 "1920 × 1080 px"。 */
    public void setCanvasSize(String text) {
        canvasSize.setText(text);
    }

    /** 缩放比例，例如 "125%"；传入 0 表示无文档。 */
    public void setZoom(double ratio) {
        zoom.setText(ratio <= 0 ? "—" : Math.round(ratio * 100) + "%");
    }

    public void setTool(String name) {
        tool.setText(name == null ? "—" : name);
    }

    /** 光标在画布坐标系中的位置。 */
    public void setCursorPosition(double x, double y) {
        cursor.setText(Math.round(x) + ", " + Math.round(y));
    }

    public void clearCursorPosition() {
        cursor.setText("—");
    }
}
