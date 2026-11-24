package com.pixelforge.ui.dialogs;

import com.pixelforge.ui.Style;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.GridPane;
import javafx.stage.Window;

/**
 * 亮度 / 对比度调整对话框。
 */
public final class AdjustmentDialog {

    /** 调整参数。 */
    public record Result(double brightness, double contrast) {
    }

    private AdjustmentDialog() {
    }

    /**
     * @return 调整参数，取消时返回 {@code null}
     */
    public static Result show(Window owner, double initialBrightness, double initialContrast) {
        Dialog<Result> dialog = new Dialog<>();
        dialog.setTitle("亮度 / 对比度");
        dialog.setHeaderText("调整当前图层（或选区）的明暗与反差");
        dialog.setResizable(false);
        if (owner != null) {
            dialog.initOwner(owner);
        }

        ButtonType applyType = new ButtonType("应用", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(applyType, ButtonType.CANCEL);

        Slider brightness = new Slider(-100, 100, initialBrightness);
        Slider contrast = new Slider(-100, 100, initialContrast);
        brightness.setPrefWidth(240);
        contrast.setPrefWidth(240);

        Label brightnessValue = new Label();
        brightnessValue.getStyleClass().add("value-label");
        brightnessValue.textProperty().bind(brightness.valueProperty().asString("%.0f"));
        Label contrastValue = new Label();
        contrastValue.getStyleClass().add("value-label");
        contrastValue.textProperty().bind(contrast.valueProperty().asString("%.0f"));

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(12);
        grid.setPadding(new Insets(4, 0, 0, 0));
        grid.addRow(0, new Label("亮度"), brightness, brightnessValue);
        grid.addRow(1, new Label("对比度"), contrast, contrastValue);

        dialog.getDialogPane().setContent(grid);
        Style.apply(dialog.getDialogPane());

        dialog.setResultConverter(button -> button == applyType
                ? new Result(brightness.getValue(), contrast.getValue()) : null);

        return dialog.showAndWait().orElse(null);
    }
}
