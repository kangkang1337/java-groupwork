package com.endurance.ui;

import com.endurance.analysis.RaceReport;
import com.endurance.data.GameData;
import com.endurance.model.Car;
import com.endurance.model.RaceConfig;
import com.endurance.model.TireCompound;
import com.endurance.sim.LapResult;
import com.endurance.sim.RaceResult;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.List;

/**
 * 赛后复盘：圈速曲线 + 胎况变化 + 决策评分。
 *
 * <p>两张图都是手绘的（Canvas），没有引入图表库——这个项目的原则是
 * "能用 JDK 和 JavaFX 自带的东西解决，就不加第三方依赖"。</p>
 */
public class ReviewView extends BorderPane {

    private final Chart lapChart = new Chart(this::paintLapChart);
    private final Chart tireChart = new Chart(this::paintTireChart);
    private final ComboBox<String> carBox = new ComboBox<>();
    private final VBox scorePanel = new VBox(6);
    private final Label subtitle = new Label();

    private final GameData data;
    private RaceResult result;
    private RaceConfig config;
    private int selectedCar;

    public ReviewView(GameData data) {
        this.data = data;
        getStyleClass().add("review-root");
        setPadding(new Insets(16));
        setTop(buildHeader());
        setCenter(buildCharts());
        setRight(buildScorePanel());

        carBox.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue != null && result != null) {
                int number = Integer.parseInt(newValue.substring(1, newValue.indexOf(' ')));
                selectedCar = number;
                refresh();
            }
        });
    }

    /** 载入一场比赛的结果。 */
    public void load(RaceResult result, int carNumber, RaceConfig config) {
        this.result = result;
        this.config = config;
        this.selectedCar = carNumber;

        carBox.getItems().clear();
        for (RaceResult.Entry entry : result.standings()) {
            carBox.getItems().add("#" + entry.car().number() + " " + entry.car().driverName());
        }
        carBox.getSelectionModel().select(indexOf(carNumber));
        refresh();
    }

    private int indexOf(int number) {
        for (int i = 0; i < carBox.getItems().size(); i++) {
            if (carBox.getItems().get(i).startsWith("#" + number + " ")) {
                return i;
            }
        }
        return 0;
    }

    // ------------------------------------------------------------------

    private Region buildHeader() {
        Label title = new Label("赛后复盘");
        title.getStyleClass().add("review-title");
        subtitle.getStyleClass().add("review-subtitle");

        carBox.getStyleClass().add("review-car-box");
        carBox.setPrefWidth(180);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox header = new HBox(14, new VBox(2, title, subtitle), spacer,
                new Label("查看车辆"), carBox);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(0, 0, 12, 0));
        return header;
    }

    private Region buildCharts() {
        lapChart.setPrefHeight(240);
        tireChart.setPrefHeight(200);
        VBox box = new VBox(12, lapChart, tireChart);
        VBox.setVgrow(lapChart, Priority.ALWAYS);
        VBox.setVgrow(tireChart, Priority.ALWAYS);
        return box;
    }

    private Region buildScorePanel() {
        scorePanel.getStyleClass().add("review-score-panel");
        scorePanel.setPadding(new Insets(14));

        ScrollPane pane = new ScrollPane(scorePanel);
        pane.setFitToWidth(true);
        pane.getStyleClass().add("review-scroll");
        pane.setPrefWidth(320);
        pane.setMinWidth(300);
        BorderPane.setMargin(pane, new Insets(0, 0, 0, 14));
        return pane;
    }

    private void refresh() {
        if (result == null) {
            return;
        }
        lapChart.redraw();
        tireChart.redraw();
        paintScorePanel();
    }

    // ------------------------------------------------------------------
    // 圈速曲线
    // ------------------------------------------------------------------

    private void paintLapChart(GraphicsContext g, double width, double height) {
        List<LapResult> laps = result == null ? List.of() : result.lapsOf(selectedCar);
        drawChartBackground(g, width, height, "圈速曲线", "圈数", "单圈用时（秒）");
        if (laps.size() < 2) {
            return;
        }

        double minX = laps.get(0).lap();
        double maxX = laps.get(laps.size() - 1).lap();
        double minY = Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        // 纵轴只按正常圈算：安全车圈是全场慢，算进去会把曲线压成一条直线
        for (LapResult lap : laps) {
            if (lap.safetyCar()) {
                continue;
            }
            minY = Math.min(minY, lap.lapTime());
            maxY = Math.max(maxY, lap.lapTime());
        }
        if (minY > maxY) {
            minY = 0;
            maxY = 1;
        }
        double pad = Math.max(0.6, (maxY - minY) * 0.18);
        minY -= pad;
        maxY += pad;

        double left = 64;
        double right = width - 20;
        double top = 44;
        double bottom = height - 34;

        g.setStroke(Color.web("#2a3038"));
        g.setLineWidth(1);
        for (int i = 0; i <= 4; i++) {
            double y = top + (bottom - top) * i / 4.0;
            g.strokeLine(left, y, right, y);
            g.setFill(Color.web("#6d7683"));
            g.setFont(Font.font("Consolas", 10));
            double value = maxY - (maxY - minY) * i / 4.0;
            g.fillText(String.format("%.1f", value), 10, y + 3.5);
        }

        // 折线
        g.setStroke(Color.web("#9ad1ff", 0.55));
        g.setLineWidth(1.4);
        g.beginPath();
        for (int i = 0; i < laps.size(); i++) {
            double x = left + (right - left) * (laps.get(i).lap() - minX) / Math.max(1, maxX - minX);
            double y = bottom - (bottom - top)
                    * (Math.min(maxY, laps.get(i).lapTime()) - minY) / (maxY - minY);
            if (i == 0) {
                g.moveTo(x, y);
            } else {
                g.lineTo(x, y);
            }
        }
        g.stroke();

        // 每圈按轮胎配色点
        for (LapResult lap : laps) {
            double x = left + (right - left) * (lap.lap() - minX) / Math.max(1, maxX - minX);
            double y = bottom - (bottom - top)
                    * (Math.min(maxY, lap.lapTime()) - minY) / (maxY - minY);
            if (lap.safetyCar()) {
                // 安全车圈单独标成方块，一眼能认出来这段不能拿来比较圈速
                g.setFill(Color.web("#f59e0b"));
                g.fillRect(x - 3, y - 3, 6, 6);
            } else {
                g.setFill(safeColor(lap.compound().colorHex()));
                g.fillOval(x - 2.6, y - 2.6, 5.2, 5.2);
            }
        }

        // 圈速突然变慢的圈（进站）标一下
        for (int i = 1; i < laps.size(); i++) {
            if (laps.get(i).stint() != laps.get(i - 1).stint()) {
                double x = left + (right - left) * (laps.get(i).lap() - minX) / Math.max(1, maxX - minX);
                g.setStroke(Color.web("#f59e0b", 0.75));
                g.setLineWidth(1.5);
                g.setLineDashes(4, 5);
                g.strokeLine(x, top, x, bottom);
                g.setLineDashes();
            }
        }

        g.setFill(Color.web("#6d7683"));
        g.setFont(Font.font("Consolas", 10));
        g.fillText("L" + (int) minX, left - 6, height - 16);
        g.fillText("L" + (int) maxX, right - 16, height - 16);
        g.fillText("虚线 = 进站", right - 78, top - 8);
    }

    // ------------------------------------------------------------------
    // 胎况变化
    // ------------------------------------------------------------------

    private void paintTireChart(GraphicsContext g, double width, double height) {
        List<LapResult> laps = result == null ? List.of() : result.lapsOf(selectedCar);
        drawChartBackground(g, width, height, "轮胎磨损与油量", "圈数", "百分比");
        if (laps.size() < 2) {
            return;
        }

        double left = 64;
        double right = width - 20;
        double top = 44;
        double bottom = height - 34;

        g.setStroke(Color.web("#2a3038"));
        g.setLineWidth(1);
        for (int i = 0; i <= 4; i++) {
            double y = top + (bottom - top) * i / 4.0;
            g.strokeLine(left, y, right, y);
            g.setFill(Color.web("#6d7683"));
            g.setFont(Font.font("Consolas", 10));
            g.fillText((100 - i * 25) + "%", 26, y + 3.5);
        }

        // 80% 警戒线
        double warnY = bottom - (bottom - top) * 0.80;
        g.setStroke(Color.web("#f59e0b", 0.5));
        g.setLineDashes(5, 6);
        g.strokeLine(left, warnY, right, warnY);
        g.setLineDashes();
        g.setFill(Color.web("#f59e0b", 0.8));
        g.fillText("磨损警戒线 80%", left + 6, warnY - 5);

        drawSeries(g, laps, left, right, top, bottom, true);
        drawSeries(g, laps, left, right, top, bottom, false);

        g.setFill(Color.web("#9ad1ff"));
        g.fillText("磨损", right - 90, top - 8);
        g.setFill(Color.web("#22d3ee"));
        g.fillText("油量", right - 44, top - 8);
    }

    private void drawSeries(GraphicsContext g, List<LapResult> laps, double left, double right,
                            double top, double bottom, boolean wear) {
        double minX = laps.get(0).lap();
        double maxX = laps.get(laps.size() - 1).lap();
        double fuelMax = 130;

        g.setStroke(wear ? Color.web("#9ad1ff") : Color.web("#22d3ee"));
        g.setLineWidth(wear ? 2.0 : 1.4);
        g.setLineDashes(wear ? new double[0] : new double[]{5, 4});
        g.beginPath();
        for (int i = 0; i < laps.size(); i++) {
            double value = wear ? Math.min(100, laps.get(i).tireWear() * 100)
                    : Math.min(100, laps.get(i).fuelKg() / fuelMax * 100);
            double x = left + (right - left) * (laps.get(i).lap() - minX) / Math.max(1, maxX - minX);
            double y = bottom - (bottom - top) * value / 100.0;
            if (i == 0) {
                g.moveTo(x, y);
            } else {
                g.lineTo(x, y);
            }
        }
        g.stroke();
        g.setLineDashes();
    }

    // ------------------------------------------------------------------

    private void drawChartBackground(GraphicsContext g, double width, double height,
                                     String title, String xLabel, String yLabel) {
        g.setFill(Color.web("#171b21"));
        g.fillRect(0, 0, width, height);
        g.setFill(Color.web("#eaf0f7"));
        g.setFont(Font.font("Microsoft YaHei UI", FontWeight.BOLD, 13));
        g.fillText(title, 14, 22);
        g.setFill(Color.web("#6d7683"));
        g.setFont(Font.font("Microsoft YaHei UI", 10));
        g.fillText("横轴 " + xLabel + " · 纵轴 " + yLabel, 14 + title.length() * 13.5 + 10, 22);
    }

    private void paintScorePanel() {
        scorePanel.getChildren().clear();
        Car car = findCar(selectedCar);
        if (car == null || config == null) {
            return;
        }

        RaceReport.Score score = RaceReport.analyze(result, selectedCar, data, config);

        Label title = new Label("决策评分");
        title.getStyleClass().add("panel-title");

        Label value = new Label(String.valueOf(score.score()));
        value.getStyleClass().add("score-value");
        Label grade = new Label(score.grade());
        grade.getStyleClass().add("score-grade");

        HBox head = new HBox(10, value, grade);
        head.setAlignment(Pos.BASELINE_LEFT);

        subtitle.setText(car.displayName() + " · " + score.notes().size() + " 条复盘意见");

        scorePanel.getChildren().addAll(title, head,
                metric("组别", score.className() + " 第 " + score.classPosition() + " 名"),
                metric("理论最优", format(score.optimalTime())),
                metric("实际用时", format(score.playerTime())),
                metric("进站次数", score.stops() + " 次"),
                metric("进站损失", String.format("%.1f 秒", score.pitCost())),
                metric("圈速累计损失", String.format("%.0f 秒", score.lapLoss())));

        Label notesTitle = new Label("关键决策");
        notesTitle.getStyleClass().add("panel-title");
        VBox.setMargin(notesTitle, new Insets(10, 0, 0, 0));
        scorePanel.getChildren().add(notesTitle);

        for (String note : score.notes()) {
            Label line = new Label("· " + note);
            line.getStyleClass().add("score-note");
            line.setWrapText(true);
            scorePanel.getChildren().add(line);
        }
    }

    private HBox metric(String caption, String value) {
        Label captionLabel = new Label(caption);
        captionLabel.getStyleClass().add("stat-caption");
        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("stat-value");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(captionLabel, spacer, valueLabel);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private Car findCar(int number) {
        if (result == null) {
            return null;
        }
        for (RaceResult.Entry entry : result.standings()) {
            if (entry.car().number() == number) {
                return entry.car();
            }
        }
        return null;
    }

    private static String format(double seconds) {
        if (seconds <= 0) {
            return "—";
        }
        int total = (int) Math.round(seconds * 1000);
        int ms = total % 1000;
        total /= 1000;
        return String.format(java.util.Locale.ROOT, "%d:%02d.%03d", total / 60, total % 60, ms);
    }

    private static Color safeColor(String hex) {
        try {
            return Color.web(hex);
        } catch (IllegalArgumentException e) {
            return Color.web("#9AA4B2");
        }
    }

    // ------------------------------------------------------------------

    /** 一个会自动重绘的图表区域。 */
    private static final class Chart extends Region {

        interface Painter {
            void paint(GraphicsContext g, double width, double height);
        }

        private final Canvas canvas = new Canvas(10, 10);
        private final Painter painter;

        Chart(Painter painter) {
            this.painter = painter;
            getChildren().add(canvas);
            setStyle("-fx-background-color: #171b21; -fx-background-radius: 10;");
            widthProperty().addListener((obs, oldValue, newValue) -> redraw());
            heightProperty().addListener((obs, oldValue, newValue) -> redraw());
        }

        void redraw() {
            canvas.setWidth(Math.max(10, getWidth()));
            canvas.setHeight(Math.max(10, getHeight()));
            GraphicsContext g = canvas.getGraphicsContext2D();
            g.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
            painter.paint(g, canvas.getWidth(), canvas.getHeight());
        }

        @Override
        protected void layoutChildren() {
            redraw();
        }
    }
}
