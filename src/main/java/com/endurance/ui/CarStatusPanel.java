package com.endurance.ui;

import com.endurance.model.Car;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

/**
 * 单车状态面板：胎温 / 磨损 / 油量三根进度条，外加圈速与进站信息。
 */
public class CarStatusPanel extends VBox {

    private final Circle teamDot = new Circle(6);
    private final Label title = new Label("—");
    private final Label subtitle = new Label("");
    private final BarRow tempBar = new BarRow("胎温", 0, 130);
    private final BarRow wearBar = new BarRow("磨损", 0, 100);
    private final BarRow fuelBar = new BarRow("油量", 0, 130);
    private final Label lastLap = new Label("—");
    private final Label bestLap = new Label("—");
    private final Label pitLabel = new Label("—");
    private final Label penaltyLabel = new Label("—");

    public CarStatusPanel() {
        setSpacing(8);

        Label panelTitle = new Label("赛车状态");
        panelTitle.getStyleClass().add("panel-title");

        teamDot.setStroke(Color.web("#0F1115"));
        title.getStyleClass().add("car-title");
        subtitle.getStyleClass().add("hint");

        HBox head = new HBox(8, teamDot, new VBox(1, title, subtitle));
        head.setAlignment(Pos.CENTER_LEFT);

        HBox stats = new HBox(14,
                statBox("上圈", lastLap),
                statBox("最快", bestLap),
                statBox("进站", pitLabel),
                statBox("罚时", penaltyLabel));
        stats.setAlignment(Pos.CENTER_LEFT);

        getChildren().addAll(panelTitle, head, tempBar, wearBar, fuelBar, stats);
    }

    private VBox statBox(String caption, Label value) {
        Label captionLabel = new Label(caption);
        captionLabel.getStyleClass().add("stat-caption");
        value.getStyleClass().add("stat-value");
        VBox box = new VBox(1, captionLabel, value);
        HBox.setHgrow(box, Priority.ALWAYS);
        return box;
    }

    /** 刷新面板。 */
    /** 当前标题文字（形如 "#35 A. Félix da Costa"），测试用。 */
    public String getTitleText() {
        return title.getText();
    }

    public void update(Car car) {
        if (car == null) {
            title.setText("—");
            subtitle.setText("");
            tempBar.set(0);
            wearBar.set(0);
            fuelBar.set(0);
            return;
        }
        teamDot.setFill(safeColor(car.team().colorHex()));
        title.setText("#" + car.number() + " " + car.driverName());
        subtitle.setText(car.team().name() + " · " + car.compound().displayName()
                + (car.isRetired() ? " · 已退赛" : ""));

        tempBar.set(car.tireTemp());
        wearBar.set(car.tireWear() * 100);
        fuelBar.set(car.fuelKg());

        lastLap.setText(Format.lap(car.lastLapTime()));
        bestLap.setText(Format.lap(car.bestLapTime()));
        pitLabel.setText(car.pitStops() + " 次");
        penaltyLabel.setText(car.penaltySeconds() > 0.01
                ? String.format(java.util.Locale.ROOT, "+%.0fs", car.penaltySeconds()) : "—");
        penaltyLabel.setStyle(car.penaltySeconds() > 0.01 ? "-fx-text-fill: #f59e0b;" : "");
    }

    private static Color safeColor(String hex) {
        try {
            return Color.web(hex);
        } catch (IllegalArgumentException e) {
            return Color.web("#9AA4B2");
        }
    }

    /** 一行"名称 + 进度条 + 数值"。 */
    private static final class BarRow extends VBox {

        private final ProgressBar bar = new ProgressBar(0);
        private final Label valueLabel = new Label("—");
        private final double max;

        BarRow(String caption, double min, double max) {
            this.max = max;
            setSpacing(2);

            Label captionLabel = new Label(caption);
            captionLabel.getStyleClass().add("bar-caption");
            valueLabel.getStyleClass().add("bar-value");

            HBox head = new HBox(captionLabel, spacer(), valueLabel);
            head.setAlignment(Pos.CENTER_LEFT);

            bar.setMaxWidth(Double.MAX_VALUE);
            bar.getStyleClass().add("car-bar");

            getChildren().addAll(head, bar);
        }

        void set(double value) {
            double clamped = Math.max(0, Math.min(max, value));
            bar.setProgress(clamped / max);
            valueLabel.setText(String.format("%.0f", clamped));
        }

        private static javafx.scene.layout.Region spacer() {
            javafx.scene.layout.Region region = new javafx.scene.layout.Region();
            HBox.setHgrow(region, Priority.ALWAYS);
            return region;
        }
    }
}
