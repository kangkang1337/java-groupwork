package com.pixelforge.ui.dialogs;

import com.pixelforge.model.Document;
import com.pixelforge.ui.Style;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/**
 * 新建画布对话框：预设尺寸 + 自定义宽高 + 背景类型。
 */
public final class NewDocumentDialog {

    /** 常用尺寸预设。 */
    private static final int[][] PRESETS = {
            {1280, 720}, {1920, 1080}, {2560, 1440},
            {1024, 1024}, {800, 600}, {640, 480}
    };

    private NewDocumentDialog() {
    }

    /**
     * 弹出新建对话框。
     *
     * @return 新建的文档；用户取消时返回 {@code null}
     */
    public static Document show(Window owner) {
        Dialog<Document> dialog = new Dialog<>();
        dialog.setTitle("新建画布");
        dialog.setHeaderText("设置画布尺寸与背景");
        dialog.setResizable(false);
        if (owner != null) {
            dialog.initOwner(owner);
        }

        ButtonType createType = new ButtonType("创建", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(createType, ButtonType.CANCEL);

        ComboBox<String> presetBox = new ComboBox<>();
        for (int[] preset : PRESETS) {
            presetBox.getItems().add(preset[0] + " × " + preset[1]);
        }
        presetBox.getItems().add("自定义");
        presetBox.getSelectionModel().selectFirst();
        presetBox.setMaxWidth(Double.MAX_VALUE);

        TextField widthField = new TextField(String.valueOf(PRESETS[0][0]));
        TextField heightField = new TextField(String.valueOf(PRESETS[0][1]));
        widthField.setPrefColumnCount(6);
        heightField.setPrefColumnCount(6);

        RadioButton whiteRadio = new RadioButton("白色背景");
        whiteRadio.setSelected(true);
        RadioButton transparentRadio = new RadioButton("透明背景");
        ToggleGroup backgroundGroup = new ToggleGroup();
        whiteRadio.setToggleGroup(backgroundGroup);
        transparentRadio.setToggleGroup(backgroundGroup);

        Label errorLabel = new Label();
        errorLabel.getStyleClass().add("error-label");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);

        presetBox.valueProperty().addListener((obs, oldValue, newValue) -> {
            int index = presetBox.getItems().indexOf(newValue);
            if (index >= 0 && index < PRESETS.length) {
                widthField.setText(String.valueOf(PRESETS[index][0]));
                heightField.setText(String.valueOf(PRESETS[index][1]));
            }
        });

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(4, 0, 0, 0));
        grid.addRow(0, new Label("预设尺寸"), presetBox);
        grid.addRow(1, new Label("宽度 (px)"), widthField);
        grid.addRow(2, new Label("高度 (px)"), heightField);
        grid.addRow(3, new Label("背景"), new HBox(14, whiteRadio, transparentRadio));

        VBox content = new VBox(10, grid, errorLabel);
        dialog.getDialogPane().setContent(content);
        Style.apply(dialog.getDialogPane());

        var createButton = dialog.getDialogPane().lookupButton(createType);
        createButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            String message = validate(widthField.getText(), heightField.getText());
            if (message != null) {
                errorLabel.setText(message);
                errorLabel.setVisible(true);
                errorLabel.setManaged(true);
                event.consume();
            }
        });

        dialog.setResultConverter(button -> {
            if (button != createType) {
                return null;
            }
            int width = Integer.parseInt(widthField.getText().trim());
            int height = Integer.parseInt(heightField.getText().trim());
            return Document.blank(width, height, whiteRadio.isSelected());
        });

        return dialog.showAndWait().orElse(null);
    }

    private static String validate(String widthText, String heightText) {
        int width;
        int height;
        try {
            width = Integer.parseInt(widthText.trim());
            height = Integer.parseInt(heightText.trim());
        } catch (NumberFormatException e) {
            return "宽度和高度必须是整数";
        }
        if (width < Document.MIN_SIZE || height < Document.MIN_SIZE) {
            return "尺寸不能小于 " + Document.MIN_SIZE + " 像素";
        }
        if (width > Document.MAX_SIZE || height > Document.MAX_SIZE) {
            return "尺寸不能超过 " + Document.MAX_SIZE + " 像素";
        }
        return null;
    }
}
