package com.pixelforge.ui;

import com.pixelforge.model.ToolType;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.EnumMap;
import java.util.Map;

/**
 * 左侧工具栏。
 *
 * <p>一组互斥的 {@link ToggleButton}，选中状态通过 {@link #selectedToolProperty()}
 * 对外广播，画布区域监听它来切换当前交互行为。工具按"绘制类 / 导航类"分组，
 * 中间用分隔条隔开。</p>
 */
public class ToolPalette {

    private static final double BUTTON_SIZE = 34;
    private static final double ICON_SIZE = 20;

    private final VBox root = new VBox(3);
    private final ToggleGroup group = new ToggleGroup();
    private final ObjectProperty<ToolType> selectedTool = new SimpleObjectProperty<>(ToolType.BRUSH);
    private final Map<ToolType, ToggleButton> buttons = new EnumMap<>(ToolType.class);

    public ToolPalette() {
        root.getStyleClass().add("tool-palette");
        root.setAlignment(Pos.TOP_CENTER);
        root.setFillWidth(false);
        root.setPadding(new Insets(8, 6, 8, 6));

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        for (ToolType type : ToolType.values()) {
            if (type == ToolType.SELECT) {
                root.getChildren().add(separator());
            }
            root.getChildren().add(createButton(type));
        }
        root.getChildren().add(spacer);

        group.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle == null) {
                // 不允许"一个都不选"：把上一次的选择恢复回去
                if (oldToggle != null) {
                    oldToggle.setSelected(true);
                }
                return;
            }
            selectedTool.set((ToolType) newToggle.getUserData());
        });

        buttons.get(ToolType.BRUSH).setSelected(true);
    }

    private ToggleButton createButton(ToolType type) {
        ToggleButton button = new ToggleButton();
        button.setGraphic(IconFactory.icon(type, ICON_SIZE));
        button.setUserData(type);
        button.setToggleGroup(group);
        button.setFocusTraversable(false);
        button.setPrefSize(BUTTON_SIZE, BUTTON_SIZE);
        button.setMinSize(BUTTON_SIZE, BUTTON_SIZE);
        button.getStyleClass().add("tool-button");

        Tooltip tip = new Tooltip(type.tooltip());
        tip.setShowDelay(Duration.millis(350));
        Tooltip.install(button, tip);

        buttons.put(type, button);
        return button;
    }

    private static Region separator() {
        Region line = new Region();
        line.getStyleClass().add("tool-palette-separator");
        line.setMinHeight(1);
        line.setPrefHeight(1);
        line.setMaxHeight(1);
        line.setMinWidth(22);
        line.setPrefWidth(22);
        line.setMaxWidth(22);
        VBox.setMargin(line, new Insets(5, 0, 5, 0));
        return line;
    }

    /** 工具栏根节点。 */
    public Node getRoot() {
        return root;
    }

    /** 当前选中的工具，可双向绑定。 */
    public ObjectProperty<ToolType> selectedToolProperty() {
        return selectedTool;
    }

    public ToolType getSelectedTool() {
        return selectedTool.get();
    }

    /** 外部（如快捷键）切换工具。 */
    public void selectTool(ToolType type) {
        ToggleButton button = buttons.get(type);
        if (button != null) {
            button.setSelected(true);
        }
    }
}
