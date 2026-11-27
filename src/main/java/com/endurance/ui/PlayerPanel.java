package com.endurance.ui;

import com.endurance.data.GameData;
import com.endurance.model.Car;
import com.endurance.model.TireCompound;
import com.endurance.sim.PitDecision;
import com.endurance.strategy.PlayerStrategy;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 玩家指令条：选轮胎、拖动加油量、点"本圈进站"。
 *
 * <p>所有校验都在这里做，出错时给一句人话提示，而不是抛异常。</p>
 */
public class PlayerPanel extends HBox {

    private final Label carLabel = new Label("—");
    private final Label stateLabel = new Label("");
    private final ToggleGroup tireGroup = new ToggleGroup();
    private final Map<String, ToggleButton> tireButtons = new LinkedHashMap<>();
    private final Slider fuelSlider = new Slider(0, 60, 30);
    private final Label fuelValue = new Label("30 kg");
    private final Button pitButton = new Button("本圈进站");
    private final Label hint = new Label("暂停比赛后从容下指令");

    private PlayerStrategy strategy;
    private Car playerCar;
    private boolean raceFinished;

    public PlayerPanel(GameData data) {
        setSpacing(14);
        setAlignment(Pos.CENTER_LEFT);
        getStyleClass().add("player-bar");

        Label caption = new Label("你的指令");
        caption.getStyleClass().add("player-caption");

        carLabel.getStyleClass().add("player-car");
        stateLabel.getStyleClass().add("hint");

        Region divider = new Region();
        divider.getStyleClass().add("divider");
        divider.setPrefWidth(1);

        HBox tires = new HBox(4);
        // 「不换胎」：只加油，省掉换胎耗时，但旧胎的磨损会继续累积
        ToggleButton keep = new ToggleButton("不换胎");
        keep.getStyleClass().add("tire-option");
        keep.setToggleGroup(tireGroup);
        keep.setUserData(null);
        keep.setFocusTraversable(false);
        keep.setTooltip(new javafx.scene.control.Tooltip(
                "只加油不换胎：省掉换胎耗时，但轮胎磨损继续累积"));
        tireButtons.put("none", keep);
        tires.getChildren().add(keep);

        for (TireCompound tire : data.tires()) {
            ToggleButton button = new ToggleButton(tire.displayName());
            button.getStyleClass().add("tire-option");
            button.setToggleGroup(tireGroup);
            button.setUserData(tire);
            button.setFocusTraversable(false);
            Circle dot = new Circle(4, safeColor(tire.colorHex()));
            button.setGraphic(dot);
            tireButtons.put(tire.id(), button);
            tires.getChildren().add(button);
        }
        // 默认选中第一个真实配方，而不是「不换胎」——避免误操作成只加油
        tires.getChildren().stream()
                .filter(node -> node instanceof ToggleButton button && button.getUserData() != null)
                .map(node -> (ToggleButton) node)
                .findFirst()
                .ifPresent(button -> button.setSelected(true));

        fuelSlider.setPrefWidth(150);
        fuelSlider.valueProperty().addListener((obs, oldValue, newValue) ->
                fuelValue.setText(Math.round(newValue.doubleValue()) + " kg"));

        pitButton.getStyleClass().add("pit-button");
        pitButton.setOnAction(e -> submitCommand());

        hint.getStyleClass().add("player-hint");

        getChildren().addAll(caption, carLabel, stateLabel, divider,
                new Label("换胎"), tires,
                new Label("加油"), fuelSlider, fuelValue,
                pitButton, hint);
    }

    /** 绑定玩家车与指令通道。 */
    public void bind(Car playerCar, PlayerStrategy strategy) {
        this.playerCar = playerCar;
        this.strategy = strategy;
        if (strategy != null) {
            strategy.setOnApplied(() -> hint.setText("指令已执行"));
        }
        refresh();
    }

    /** 每帧刷新（油量上限、轮胎状态、按钮可用性）。 */
    public void refresh() {
        if (playerCar == null) {
            carLabel.setText("配置里没有标记玩家车");
            pitButton.setDisable(true);
            return;
        }
        carLabel.setText("#" + playerCar.number() + " " + playerCar.driverName());
        stateLabel.setText("当前 " + playerCar.compound().displayName()
                + " · 油量 " + Math.round(playerCar.fuelKg()) + " kg"
                + (playerCar.isRetired() ? " · 已退赛" : ""));

        double space = Math.max(0, Car.MAX_FUEL_KG - playerCar.fuelKg());
        fuelSlider.setMax(Math.max(1, space));
        if (fuelSlider.getValue() > space) {
            fuelSlider.setValue(space);
        }
        pitButton.setDisable(playerCar.isRetired() || raceFinished);
        if (strategy != null && strategy.hasPending()) {
            pitButton.setText("已排队");
        } else {
            pitButton.setText("本圈进站");
        }
    }

    /** 比赛是否已结束（结束后禁用指令）。 */
    public void setRaceFinished(boolean finished) {
        this.raceFinished = finished;
        refresh();
    }

    // ------------------------------------------------------------------

    private void submitCommand() {
        if (playerCar == null) {
            hint.setText("配置里没有标记玩家车");
            return;
        }
        if (playerCar.isRetired()) {
            hint.setText("你的车已经退赛了");
            return;
        }
        if (raceFinished) {
            hint.setText("比赛已经结束，点「再来一场」重开");
            return;
        }

        // tire == null 表示选了「不换胎」：只加油，省掉换胎的耗时
        TireCompound tire = selectedTire();

        double space = Math.max(0, Car.MAX_FUEL_KG - playerCar.fuelKg());
        double fuel = Math.min(fuelSlider.getValue(), space);
        if (fuelSlider.getValue() > space + 0.01) {
            hint.setText(String.format("油箱最多还能加 %.0f kg，已自动调整", space));
        }
        if (fuel < 0.5 && (tire == null || tire.equals(playerCar.compound()))) {
            hint.setText("既不加油也不换胎，这条指令没有意义");
            return;
        }
        if (fuel < 0.5 && tire != null) {
            // 只换胎不加油也允许
        }

        PitDecision decision = tire == null
                ? PitDecision.fuelOnly(Math.max(0, fuel))
                : new PitDecision(tire, Math.max(0, fuel));
        strategy.queue(decision);
        hint.setText("已排队：" + describe(decision) + "，下一圈执行");
        refresh();
    }

    private String describe(PitDecision decision) {
        String tires = decision.compound().equals(playerCar.compound())
                ? "不换胎" : "换 " + decision.compound().displayName();
        String fuel = decision.fuelToAdd() > 0.5
                ? " + 加 " + Math.round(decision.fuelToAdd()) + " kg" : "";
        return tires + fuel;
    }

    /**
     * 按当前分站限制可选配方（官方规则：普通站两种干胎，勒芒三种）。
     * 不可用的按钮直接禁用，避免玩家选了本站根本没有的胎。
     */
    public void applyTrackRestrictions(com.endurance.model.Track track) {
        if (track == null) {
            return;
        }
        for (var entry : tireButtons.entrySet()) {
            if ("none".equals(entry.getKey())) {
                continue; // 「不换胎」任何分站都能选
            }
            boolean allowed = track.allowsCompound(entry.getKey());
            ToggleButton button = entry.getValue();
            button.setDisable(!allowed);
            button.setTooltip(allowed ? null
                    : new javafx.scene.control.Tooltip(
                    "本站不提供这个配方——WEC 每站只给两种干胎（勒芒三种）"));
        }
        ToggleButton selected = (ToggleButton) tireGroup.getSelectedToggle();
        if (selected == null || selected.isDisabled()) {
            tireButtons.values().stream()
                    .filter(button -> !button.isDisabled())
                    .findFirst()
                    .ifPresent(button -> button.setSelected(true));
        }
    }

    /** 某个配方按钮是否可选（自检用）。 */
    public boolean isTireEnabled(String tireId) {
        ToggleButton button = tireButtons.get(tireId);
        return button != null && !button.isDisabled();
    }

    private TireCompound selectedTire() {
        var toggle = tireGroup.getSelectedToggle();
        return toggle == null ? null : (TireCompound) toggle.getUserData();
    }

    private static Color safeColor(String hex) {
        try {
            return Color.web(hex);
        } catch (IllegalArgumentException e) {
            return Color.web("#9AA4B2");
        }
    }

    /** 便于自测：直接给提示文本。 */
    public String hintText() {
        return hint.getText();
    }

    /** 便于自测：是否已有排队指令。 */
    public boolean hasQueuedCommand() {
        return strategy != null && strategy.hasPending();
    }
}
