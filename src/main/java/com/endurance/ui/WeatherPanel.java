package com.endurance.ui;

import com.endurance.data.WeatherProfile;
import com.endurance.model.Weather;
import com.endurance.sim.RaceSimulator;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.Locale;
import java.util.Map;

/**
 * 天气面板（右下角独立框）：比"干燥 / 湿 / 雨"三个字详细得多。
 *
 * <p>显示当前天气、气温、赛道温度、湿度、未来 5 圈降雨概率，
 * 以及抓取到的官方天气实况作参考——像《勒芒终极版》里那块天气卡。</p>
 */
public class WeatherPanel extends VBox {

    private final Label condition = new Label("干燥");
    private final Label airTemp = new Label("—");
    private final Label trackTemp = new Label("—");
    private final Label humidity = new Label("—");
    private final Label rain = new Label("—");
    private final HBox forecastBar = new HBox(3);
    private final Label reference = new Label("");

    private final Map<String, WeatherProfile.Profile> profiles;

    public WeatherPanel(Map<String, WeatherProfile.Profile> profiles) {
        this.profiles = profiles;
        setSpacing(6);
        getStyleClass().add("weather-panel");
        setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        setMaxWidth(Double.MAX_VALUE);
        // 让面板不吃鼠标事件，底下还能点到车
        setPickOnBounds(false);

        Label title = new Label("天气");
        title.getStyleClass().add("weather-title");
        title.setMinWidth(Region.USE_PREF_SIZE);

        condition.getStyleClass().add("weather-condition");

        HBox head = new HBox(10, title, condition, spacer(), rain);
        head.setAlignment(Pos.CENTER_LEFT);
        title.setMaxWidth(Double.MAX_VALUE);

        // 紧凑排布：侧边栏宽度有限，四行塞下所有信息
        HBox row1 = metric("气温", airTemp, "赛道", trackTemp);
        HBox row2 = metric("湿度", humidity, "降雨", rain);

        forecastBar.setAlignment(Pos.CENTER_LEFT);
        // 五个格子一次建好，之后只改颜色——比赛界面按帧刷新，
        // 每帧都清空重建节点纯属浪费
        for (int i = 0; i < 5; i++) {
            Region cell = new Region();
            cell.getStyleClass().add("forecast-cell");
            forecastBar.getChildren().add(cell);
        }
        Label forecastLabel = new Label("未来 5 圈");
        forecastLabel.getStyleClass().add("weather-caption");
        HBox forecast = new HBox(8, forecastLabel, forecastBar);
        forecast.setAlignment(Pos.CENTER_LEFT);

        reference.getStyleClass().add("weather-caption");
        reference.setWrapText(true);

        getChildren().addAll(head, row1, row2, forecast, reference);
    }

    /** 每帧刷新。 */
    public void update(RaceSimulator simulator) {
        if (simulator == null) {
            return;
        }
        Weather weather = simulator.currentWeather();
        condition.setText(weather.displayName());
        condition.setStyle("-fx-text-fill: " + colorOf(weather) + ";");

        airTemp.setText(String.format(Locale.ROOT, "%.0f°C", simulator.airTemperature()));
        trackTemp.setText(String.format(Locale.ROOT, "%.0f°C",
                simulator.config().track().trackTempCelsius()));
        humidity.setText(simulator.humidity() + "%");

        double chance = simulator.rainChance(5);
        rain.setText(String.format(Locale.ROOT, "%.0f%%", chance * 100));
        rain.setStyle("-fx-text-fill: " + (chance > 0.35 ? "#9ad1ff" : "#cfd6df") + ";");

        for (int lap = 1; lap <= 5; lap++) {
            double single = 1 - Math.pow(1 - Math.max(0, Math.min(1, chance)), 1.0 / lap);
            forecastBar.getChildren().get(lap - 1)
                    .setStyle("-fx-background-color: " + rainColor(single) + ";");
        }

        WeatherProfile.Profile profile = profiles.get(simulator.config().track().id());
        reference.setText(profile == null
                ? "参考实况：暂无数据"
                : String.format(Locale.ROOT, "参考实况 %s：%s %.1f°C · 湿度 %d%% · 降雨概率 %d%%",
                profile.city(), profile.condition(), profile.airTemp(),
                profile.humidity(), profile.rainChance()));
    }

    /** 便于自检：当前显示的降雨概率文字。 */
    public String rainText() {
        return rain.getText();
    }

    private String colorOf(Weather weather) {
        return switch (weather) {
            case WET -> "#60a5fa";
            case DAMP -> "#93c5fd";
            default -> "#eaf0f7";
        };
    }

    private String rainColor(double chance) {
        if (chance > 0.30) {
            return "#3b82f6";
        }
        if (chance > 0.15) {
            return "#1d4ed8";
        }
        if (chance > 0.05) {
            return "#1e3a5f";
        }
        return "#232830";
    }

    private HBox metric(String captionA, Label valueA, String captionB, Label valueB) {
        Label labelA = new Label(captionA);
        labelA.getStyleClass().add("weather-caption");
        Label labelB = new Label(captionB);
        labelB.getStyleClass().add("weather-caption");
        valueA.getStyleClass().add("weather-value");
        valueB.getStyleClass().add("weather-value");
        HBox box = new HBox(6, labelA, valueA, spacer(), labelB, valueB);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private Region spacer() {
        Region region = new Region();
        HBox.setHgrow(region, Priority.ALWAYS);
        return region;
    }

    /** 让面板贴右下角用。 */
    public static Insets margin() {
        return new Insets(0, 16, 16, 0);
    }
}
