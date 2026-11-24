package com.pixelforge.ui.controls;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * 右侧面板的通用分组容器：一行标题 + 内容区。
 *
 * <p>标题行右侧可放一个附加控件（例如"+ 新建"按钮）。</p>
 */
public class SectionBox extends VBox {

    private final VBox content = new VBox(8);
    private final HBox header = new HBox(6);

    public SectionBox(String title) {
        getStyleClass().add("section-box");
        setSpacing(8);

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("section-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        header.setAlignment(Pos.CENTER_LEFT);
        header.getChildren().addAll(titleLabel, spacer);

        content.setPadding(new Insets(2, 0, 0, 0));
        getChildren().addAll(header, content);
    }

    /** 在标题行右侧追加控件。 */
    public void addHeaderAction(Node node) {
        header.getChildren().add(node);
    }

    /** 把控件加入内容区。 */
    public void addContent(Node... nodes) {
        content.getChildren().addAll(nodes);
    }

    /** 清空内容区，便于后续步骤替换实现。 */
    public void clearContent() {
        content.getChildren().clear();
    }

    public VBox getContent() {
        return content;
    }
}
