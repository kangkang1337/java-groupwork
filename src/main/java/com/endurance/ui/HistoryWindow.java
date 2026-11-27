package com.endurance.ui;

import com.endurance.data.RaceHistory;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.List;

/**
 * 历史战绩窗口。
 */
public final class HistoryWindow {

    private HistoryWindow() {
    }

    public static void show(Window owner) {
        List<RaceHistory.RaceRecord> records = RaceHistory.load();

        VBox list = new VBox(4);
        Label title = new Label("历史战绩");
        title.getStyleClass().add("review-title");

        Label hint = new Label(records.isEmpty()
                ? "还没有战绩，跑完一场比赛就会自动记录"
                : "共 " + records.size() + " 场 · 存档位置 saves/history.json");
        hint.getStyleClass().add("review-subtitle");

        list.getChildren().addAll(title, hint, new Region() {{ setMinHeight(8); }});

        if (!records.isEmpty()) {
            list.getChildren().add(headerRow());
            List<RaceHistory.RaceRecord> ordered = new java.util.ArrayList<>(records);
            java.util.Collections.reverse(ordered);
            for (RaceHistory.RaceRecord record : ordered) {
                list.getChildren().add(row(record));
            }
        }

        ScrollPane pane = new ScrollPane(list);
        pane.setFitToWidth(true);
        pane.getStyleClass().add("review-scroll");
        pane.setPadding(new Insets(16));

        Stage stage = new Stage();
        if (owner != null) {
            stage.initOwner(owner);
            stage.initModality(Modality.NONE);
        }
        Scene scene = new Scene(pane, 720, 520);
        Theme.apply(scene);
        stage.setScene(scene);
        stage.setTitle("历史战绩");
        stage.show();
    }

    private static HBox headerRow() {
        HBox row = rowBase();
        row.getChildren().addAll(
                cell("时间", 130, "history-head"),
                cell("赛道", 150, "history-head"),
                cell("圈数", 56, "history-head"),
                cell("名次", 56, "history-head"),
                cell("用时", 110, "history-head"),
                cell("进站", 48, "history-head"),
                cell("评分", 48, "history-head"));
        return row;
    }

    private static HBox row(RaceHistory.RaceRecord record) {
        HBox row = rowBase();
        String position = record.retired() ? "退赛" : "第 " + record.position() + " 名";
        row.getChildren().addAll(
                cell(record.playedAt(), 130, "history-cell"),
                cell(record.trackName(), 150, "history-cell"),
                cell(String.valueOf(record.laps()), 56, "history-cell"),
                cell(position, 56, record.retired() ? "history-cell-dim" : "history-cell"),
                cell(format(record.playerTime()), 110, "history-cell"),
                cell(String.valueOf(record.stops()), 48, "history-cell"),
                cell(record.retired() ? "—" : String.valueOf(record.score()), 48, "history-score"));
        return row;
    }

    private static HBox rowBase() {
        HBox row = new HBox(6);
        row.getStyleClass().add("history-row");
        return row;
    }

    private static Label cell(String text, double width, String styleClass) {
        Label label = new Label(text);
        label.getStyleClass().add(styleClass);
        label.setMinWidth(width);
        label.setPrefWidth(width);
        label.setMaxWidth(width);
        if (width >= 110) {
            HBox.setHgrow(label, Priority.NEVER);
        }
        return label;
    }

    private static String format(double seconds) {
        if (seconds <= 0) {
            return "—";
        }
        int total = (int) Math.round(seconds);
        return String.format(java.util.Locale.ROOT, "%d:%02d:%02d",
                total / 3600, (total % 3600) / 60, total % 60);
    }
}
