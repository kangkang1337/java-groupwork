package com.endurance.ui;

import com.endurance.analysis.RaceLog;
import com.endurance.sim.RaceResult;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;

/**
 * 比赛数据表窗口：每一圈、每台车的完整账本，可筛选、可导出 CSV。
 */
public final class RaceLogWindow {

    private RaceLogWindow() {
    }

    public static void show(RaceResult result, int playerNumber, Window owner) {
        BorderPane content = createView(result, playerNumber);
        Stage stage = new Stage();
        if (owner != null) {
            stage.initOwner(owner);
            stage.initModality(Modality.NONE);
        }
        Scene scene = new Scene(content, 1180, 720);
        Theme.apply(scene);
        stage.setScene(scene);
        stage.setTitle("比赛数据表 · " + result.track().name());
        stage.show();
    }

    /** 构建数据表界面（自检工具直接渲染它）。 */
    public static BorderPane createView(RaceResult result, int playerNumber) {
        List<RaceLog.Row> rows = RaceLog.build(result, playerNumber);
        ObservableList<RaceLog.Row> data = FXCollections.observableArrayList(rows);
        FilteredList<RaceLog.Row> filtered = new FilteredList<>(data, row -> true);

        TableView<RaceLog.Row> table = new TableView<>(filtered);
        table.getStyleClass().add("log-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getColumns().addAll(
                column("圈", 56, row -> String.valueOf(row.lap())),
                column("名次", 56, row -> String.valueOf(row.position())),
                column("组别", 58, RaceLog.Row::category),
                column("组内", 52, row -> String.valueOf(row.classPosition())),
                column("车号", 56, row -> "#" + row.carNumber()),
                column("车手", 120, RaceLog.Row::driver),
                column("车队", 90, RaceLog.Row::team),
                column("圈速", 96, row -> Format.lap(row.lapTime())),
                column("轮胎", 80, RaceLog.Row::tire),
                column("磨损", 70, row -> Math.round(row.wear() * 100) + "%"),
                column("油量", 76, row -> String.format(Locale.ROOT, "%.1f", row.fuel())),
                column("落后领先", 100, row -> row.gapToLeader() < 0.001 ? "—"
                        : String.format(Locale.ROOT, "+%.1fs", row.gapToLeader())),
                column("标记", 120, row -> {
                    StringBuilder sb = new StringBuilder();
                    if (row.pit()) {
                        sb.append("[进站]");
                    }
                    if (row.safetyCar()) {
                        sb.append("[FCY]");
                    }
                    if (row.isPlayer()) {
                        sb.append("[我]");
                    }
                    return sb.toString();
                }));

        // 筛选：只看我 / 只看某台车
        CheckBox onlyMe = new CheckBox("只看我的车");
        onlyMe.getStyleClass().add("filter-check");
        onlyMe.setDisable(playerNumber < 0);

        ComboBox<String> carBox = new ComboBox<>();
        carBox.getStyleClass().add("review-car-box");
        carBox.getItems().add("全部车辆");
        for (RaceResult.Entry entry : result.standings()) {
            carBox.getItems().add("#" + entry.car().number() + " " + entry.car().driverName());
        }
        carBox.getSelectionModel().selectFirst();

        Runnable applyFilter = () -> {
            String car = carBox.getSelectionModel().getSelectedItem();
            filtered.setPredicate(row -> {
                if (onlyMe.isSelected() && !row.isPlayer()) {
                    return false;
                }
                if (car == null || car.startsWith("全部")) {
                    return true;
                }
                int number = Integer.parseInt(car.substring(1, car.indexOf(' ')));
                return row.carNumber() == number;
            });
        };
        onlyMe.selectedProperty().addListener((obs, oldValue, newValue) -> applyFilter.run());
        carBox.valueProperty().addListener((obs, oldValue, newValue) -> applyFilter.run());

        Label count = new Label();
        count.getStyleClass().add("review-subtitle");
        Runnable updateCount = () -> count.setText("显示 " + filtered.size() + " / " + rows.size() + " 行");
        filtered.predicateProperty().addListener((obs, oldValue, newValue) -> updateCount.run());
        updateCount.run();

        Button export = new Button("导出 CSV");
        export.getStyleClass().add("primary-button");
        export.setOnAction(e -> exportCsv(table.getScene().getWindow(), result, rows));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox toolbar = new HBox(12, carBox, onlyMe, spacer, count, export);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(10));
        toolbar.getStyleClass().add("log-toolbar");

        // 底部：每台车的决策摘要
        VBox summary = new VBox(3);
        summary.setPadding(new Insets(8, 12, 10, 12));
        summary.getStyleClass().add("log-summary");
        Label summaryTitle = new Label("决策摘要");
        summaryTitle.getStyleClass().add("panel-title");
        summary.getChildren().add(summaryTitle);
        for (String line : RaceLog.decisionSummary(result)) {
            Label label = new Label(line);
            label.getStyleClass().add("log-summary-line");
            summary.getChildren().add(label);
        }

        Label title = new Label("比赛数据表 · " + result.track().name() + " · " + result.totalLaps() + " 圈");
        title.getStyleClass().add("review-title");
        title.setPadding(new Insets(12, 12, 0, 12));

        BorderPane root = new BorderPane();
        root.getStyleClass().add("review-root");
        root.setTop(new VBox(title, toolbar));
        root.setCenter(table);
        root.setBottom(summary);
        return root;
    }

    private static void exportCsv(Window owner, RaceResult result, List<RaceLog.Row> rows) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("导出比赛数据表");
        chooser.setInitialFileName("race-log-" + result.track().id() + ".csv");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV 文件", "*.csv"));
        File file = chooser.showSaveDialog(owner);
        if (file == null) {
            return;
        }
        try {
            Files.writeString(file.toPath(), RaceLog.toCsv(rows), StandardCharsets.UTF_8);
        } catch (Exception e) {
            Label error = new Label("导出失败：" + e.getMessage());
            error.getStyleClass().add("player-hint");
            AppLogHolder.warn("导出数据表失败", e);
        }
    }

    private static TableColumn<RaceLog.Row, String> column(
            String title, double width, java.util.function.Function<RaceLog.Row, String> value) {
        TableColumn<RaceLog.Row, String> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        column.setCellValueFactory(cell -> new SimpleStringProperty(value.apply(cell.getValue())));
        column.setSortable(false);
        return column;
    }

    /** 隔离对 AppLog 的依赖，避免 UI 包直接抓 util。 */
    private static final class AppLogHolder {
        static void warn(String message, Throwable error) {
            com.endurance.util.AppLog.error(message, error);
        }
    }
}
