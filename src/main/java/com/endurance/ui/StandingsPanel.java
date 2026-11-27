package com.endurance.ui;

import com.endurance.model.Car;
import com.endurance.model.RaceCategory;
import com.endurance.sim.RaceSimulator;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 实时排名列表。
 *
 * <p><b>为什么这里不用"每帧清空重建"</b>：点击事件要求按下和松开落在同一个节点上，
 * 而 AnimationTimer 每帧都会刷新列表——重建一次，玩家按下的那一行就没了，
 * 于是 {@code setOnMouseClicked} 永远不触发，表现为"点了没反应"。
 * 所以行节点一次建好、之后只改文字，排序用 {@code setAll} 调位置。</p>
 */
public class StandingsPanel extends VBox {

    private final VBox rows = new VBox(2);
    private final Map<Integer, RowView> views = new LinkedHashMap<>();
    private Consumer<Car> onSelect;

    public StandingsPanel() {
        setSpacing(8);
        getStyleClass().add("panel");
        Label title = new Label("实时排名");
        title.getStyleClass().add("panel-title");
        Label hint = new Label("点击任一行查看该车状态");
        hint.getStyleClass().add("panel-hint");
        getChildren().addAll(title, rows, hint);
    }

    public void setOnSelect(Consumer<Car> onSelect) {
        this.onSelect = onSelect;
    }

    public void update(RaceSimulator simulator, Integer selectedCarNumber) {
        if (simulator == null) {
            return;
        }
        List<Car> standings = simulator.liveStandings();
        double leaderTime = standings.isEmpty() ? 0 : standings.get(0).totalTime();

        List<HBox> order = new ArrayList<>(standings.size());
        for (int i = 0; i < standings.size(); i++) {
            Car car = standings.get(i);
            RowView view = views.get(car.number());
            if (view == null) {
                view = new RowView(car);
                views.put(car.number(), view);
            }
            view.car = car;
            view.bind(i + 1, car, leaderTime, selectedCarNumber);
            order.add(view.box);
        }

        // 只有顺序真的变了才重排，避免每帧无谓的布局
        if (!rows.getChildren().equals(order)) {
            rows.getChildren().setAll(order);
        }
    }

    /** 当前选中号码，测试用。 */
    public Integer selectedNumber() {
        return views.values().stream()
                .filter(view -> view.box.getStyleClass().contains("standing-row-selected"))
                .map(view -> view.car.number())
                .findFirst()
                .orElse(null);
    }

    /** 模拟点击某一行，测试用。 */
    public boolean clickRow(int carNumber) {
        RowView view = views.get(carNumber);
        if (view == null) {
            return false;
        }
        if (onSelect != null) {
            onSelect.accept(view.car);
        }
        return true;
    }

    /** 一行用到的控件，建一次就复用。 */
    private final class RowView {
        private final HBox box = new HBox(8);
        private final Label pos = new Label();
        private final Region dot = new Region();
        private final Label name = new Label();
        private final Label category = new Label();
        private final Label compound = new Label();
        private final Label gap = new Label();
        private Car car;

        RowView(Car car) {
            this.car = car;
            box.getStyleClass().add("standing-row");
            box.setAlignment(Pos.CENTER_LEFT);
            box.setCursor(javafx.scene.Cursor.HAND);

            pos.getStyleClass().add("standing-pos");
            pos.setMinWidth(22);

            dot.getStyleClass().add("standing-dot");
            dot.setPrefSize(9, 9);
            dot.setMinSize(9, 9);

            name.getStyleClass().add("standing-name");

            category.getStyleClass().add("class-chip");
            if (car.category() == RaceCategory.HYPERCAR) {
                category.getStyleClass().add("class-chip-hyp");
            } else {
                category.getStyleClass().add("class-chip-gt");
            }

            compound.getStyleClass().add("standing-tire");
            compound.setMinWidth(26);

            gap.getStyleClass().add("standing-gap");
            gap.setMinWidth(54);
            gap.setAlignment(Pos.CENTER_RIGHT);

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            box.getChildren().addAll(pos, dot, name, spacer, category, compound, gap);
            box.setOnMouseClicked(event -> {
                if (onSelect != null) {
                    onSelect.accept(car);
                }
            });
        }

        void bind(int position, Car car, double leaderTime, Integer selectedCarNumber) {
            this.car = car;
            pos.setText(String.valueOf(position));
            dot.setStyle("-fx-background-color: " + car.team().colorHex() + ";");
            name.setText("#" + car.number() + " " + car.driverName());
            category.setText(car.category().shortCode());
            compound.setText(shortTire(car.compound().displayName()));
            gap.setText(car.isRetired() ? "退赛" : Format.gap(car.totalTime() - leaderTime));

            boolean selected = selectedCarNumber != null && selectedCarNumber == car.number();
            box.getStyleClass().remove("standing-row-selected");
            if (selected) {
                box.getStyleClass().add("standing-row-selected");
            }
            box.setOpacity(car.isRetired() ? 0.55 : 1.0);
        }
    }

    /** 排名列表空间紧张，轮胎名取短写法：软胎→软、中性胎→中、全雨胎→全雨。 */
    private static String shortTire(String displayName) {
        return switch (displayName) {
            case "软胎" -> "软";
            case "中性胎" -> "中";
            case "硬胎" -> "硬";
            case "半雨胎" -> "半雨";
            case "全雨胎" -> "全雨";
            default -> displayName;
        };
    }
}
