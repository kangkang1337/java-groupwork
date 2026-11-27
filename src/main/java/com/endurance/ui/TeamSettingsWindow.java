package com.endurance.ui;

import com.endurance.data.PlayerProfile;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.function.Consumer;

/**
 * 我的车队设置：改车队名和两位车手的名字。
 *
 * <p>默认是「1号 / 2号」，组里谁用就换成谁的名字。改完存到
 * {@code saves/player.json}，下次启动自动带出来。</p>
 */
public final class TeamSettingsWindow {

    private TeamSettingsWindow() {
    }

    public static void show(Window owner, PlayerProfile current, Consumer<PlayerProfile> onSaved) {
        Stage stage = new Stage();
        VBox content = createView(current, profile -> {
            stage.close();
            if (onSaved != null) {
                onSaved.accept(profile);
            }
        });
        if (owner != null) {
            stage.initOwner(owner);
            stage.initModality(Modality.WINDOW_MODAL);
        }
        Scene scene = new Scene(content, 460, 340);
        Theme.apply(scene);
        stage.setScene(scene);
        stage.setTitle("我的车队");
        stage.show();
    }

    /** 构建设置界面（截图工具直接渲染它）。 */
    public static VBox createView(PlayerProfile current, Consumer<PlayerProfile> onSaved) {
        TextField teamField = field(current.teamName(), "车队全名，例如 Apex Racing");
        TextField shortField = field(current.shortName(), "简称，排名列表里显示用");
        TextField driverOneField = field(current.driverOne(), "一号车手名字");
        TextField driverTwoField = field(current.driverTwo(), "二号车手名字");

        Label title = new Label("我的车队");
        title.getStyleClass().add("review-title");
        Label hint = new Label("默认是「1号 / 2号」，改成你自己的名字即可。只影响你的车队，真实车队不动。");
        hint.getStyleClass().add("review-subtitle");
        hint.setWrapText(true);

        VBox form = new VBox(10,
                row("车队全名", teamField),
                row("短名", shortField),
                row("一号车手", driverOneField),
                row("二号车手", driverTwoField));

        Label error = new Label();
        error.getStyleClass().add("player-hint");

        Button save = new Button("保存并重开");
        save.getStyleClass().add("primary-button");
        Button reset = new Button("恢复默认");
        reset.getStyleClass().add("ghost-button");
        Button cancel = new Button("取消");
        cancel.getStyleClass().add("ghost-button");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actions = new HBox(10, save, reset, spacer, cancel);
        actions.setAlignment(Pos.CENTER_LEFT);

        Stage stage = new Stage();

        save.setOnAction(e -> {
            try {
                PlayerProfile profile = new PlayerProfile(
                        teamField.getText(), shortField.getText(),
                        driverOneField.getText(), driverTwoField.getText());
                profile.save();
                if (onSaved != null) {
                    onSaved.accept(profile);
                }
            } catch (Exception ex) {
                error.setText("保存失败：" + ex.getMessage());
            }
        });

        reset.setOnAction(e -> {
            PlayerProfile defaults = PlayerProfile.defaults();
            teamField.setText(defaults.teamName());
            shortField.setText(defaults.shortName());
            driverOneField.setText(defaults.driverOne());
            driverTwoField.setText(defaults.driverTwo());
            error.setText("已填回默认值，点「保存并重开」生效");
        });

        cancel.setOnAction(e -> stage.close());

        VBox root = new VBox(14, title, hint, form, error, actions);
        root.setPadding(new Insets(18));
        root.getStyleClass().add("review-root");
        return root;
    }

    private static TextField field(String value, String prompt) {
        TextField field = new TextField(value);
        field.setPromptText(prompt);
        field.getStyleClass().add("settings-field");
        field.setMaxWidth(Double.MAX_VALUE);
        return field;
    }

    private static HBox row(String caption, TextField field) {
        Label label = new Label(caption);
        label.getStyleClass().add("stat-caption");
        label.setMinWidth(76);
        HBox row = new HBox(10, label, field);
        row.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(field, Priority.ALWAYS);
        return row;
    }
}
