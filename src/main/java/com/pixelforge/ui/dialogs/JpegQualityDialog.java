package com.pixelforge.ui.dialogs;

import com.pixelforge.ui.Style;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/**
 * JPEG 质量选择对话框。
 */
public final class JpegQualityDialog {

    private JpegQualityDialog() {
    }

    /**
     * @return 质量 0..1；取消时返回 {@code null}
     */
    public static Float show(Window owner, float initialQuality) {
        Dialog<Float> dialog = new Dialog<>();
        dialog.setTitle("导出 JPEG");
        dialog.setHeaderText("选择压缩质量（越高越清晰，文件也越大）");
        dialog.setResizable(false);
        if (owner != null) {
            dialog.initOwner(owner);
        }

        ButtonType okType = new ButtonType("导出", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(okType, ButtonType.CANCEL);

        Slider slider = new Slider(5, 100, initialQuality * 100);
        slider.setBlockIncrement(5);
        HBox.setHgrow(slider, Priority.ALWAYS);

        Label valueLabel = new Label();
        valueLabel.getStyleClass().add("value-label");
        valueLabel.textProperty().bind(slider.valueProperty().asString("%.0f"));

        Label percent = new Label("%");
        percent.getStyleClass().add("value-label");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox row = new HBox(10, slider, spacer, percent, valueLabel);
        row.setPrefWidth(320);

        VBox content = new VBox(12, row);
        content.setPadding(new Insets(4, 0, 0, 0));
        dialog.getDialogPane().setContent(content);
        Style.apply(dialog.getDialogPane());

        dialog.setResultConverter(button ->
                button == okType ? (float) (slider.getValue() / 100.0) : null);

        return dialog.showAndWait().orElse(null);
    }
}
