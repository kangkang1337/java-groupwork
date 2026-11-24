package com.pixelforge.ui.dialogs;

import com.pixelforge.ui.Style;
import com.pixelforge.util.Log;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/**
 * 统一的提示框工厂：保证所有对话框都套用深色主题，并把错误写进日志。
 */
public final class Alerts {

    private Alerts() {
        throw new AssertionError("工具类不允许实例化");
    }

    /** 错误提示（附异常详情）。 */
    public static void error(Window owner, String header, Throwable error) {
        Log.error("界面错误: " + header, error);

        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("出错了");
        alert.setHeaderText(header);
        alert.setContentText(error == null ? "未知错误" : describe(error));
        alert.initOwner(owner);
        Style.apply(alert.getDialogPane());
        alert.showAndWait();
    }

    /** 错误提示（纯文本）。 */
    public static void error(Window owner, String header, String message) {
        Log.error("界面错误: " + header + " - " + message);

        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("出错了");
        alert.setHeaderText(header);
        alert.setContentText(message);
        alert.initOwner(owner);
        Style.apply(alert.getDialogPane());
        alert.showAndWait();
    }

    /** 普通信息提示。 */
    public static void info(Window owner, String header, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("提示");
        alert.setHeaderText(header);
        alert.setContentText(message);
        alert.initOwner(owner);
        Style.apply(alert.getDialogPane());
        alert.showAndWait();
    }

    /** 是 / 否确认框。 */
    public static boolean confirm(Window owner, String header, String message) {
        ButtonType yes = new ButtonType("确定", ButtonBar.ButtonData.YES);
        ButtonType no = new ButtonType("取消", ButtonBar.ButtonData.CANCEL_CLOSE);

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("请确认");
        alert.setHeaderText(header);
        alert.setContentText(message);
        alert.getButtonTypes().setAll(yes, no);
        alert.initOwner(owner);
        Style.apply(alert.getDialogPane());
        return alert.showAndWait().orElse(no) == yes;
    }

    /**
     * 询问"是否保存当前改动"。
     *
     * @return 选择结果；关闭对话框视为 CANCEL
     */
    public static Choice askToSave(Window owner, String documentTitle) {
        ButtonType save = new ButtonType("保存", ButtonBar.ButtonData.YES);
        ButtonType discard = new ButtonType("不保存", ButtonBar.ButtonData.NO);
        ButtonType cancel = new ButtonType("取消", ButtonBar.ButtonData.CANCEL_CLOSE);

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("尚未保存");
        alert.setHeaderText("「" + documentTitle + "」有未保存的修改");
        alert.setContentText("是否在继续之前保存？");

        Label detail = new Label();
        detail.getStyleClass().add("hint-label");
        VBox box = new VBox(6, new Label(alert.getContentText()));
        box.getChildren().add(detail);
        alert.getDialogPane().setContent(box);

        alert.getButtonTypes().setAll(save, discard, cancel);
        alert.initOwner(owner);
        Style.apply(alert.getDialogPane());

        ButtonType result = alert.showAndWait().orElse(cancel);
        if (result == save) {
            return Choice.SAVE;
        }
        if (result == discard) {
            return Choice.DISCARD;
        }
        return Choice.CANCEL;
    }

    private static String describe(Throwable error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            return error.getClass().getSimpleName();
        }
        return message;
    }

    /** 未保存询问的结果。 */
    public enum Choice {
        SAVE, DISCARD, CANCEL
    }
}
