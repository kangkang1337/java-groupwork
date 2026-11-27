package com.endurance.ui;

import com.endurance.data.GameData;
import com.endurance.model.Car;
import com.endurance.model.Track;
import com.endurance.analysis.RaceReport;
import com.endurance.data.RaceHistory;
import com.endurance.data.RaceSave;
import com.endurance.setup.RaceSetup;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import com.endurance.model.TireCompound;
import com.endurance.sim.RaceSimulator;
import javafx.animation.FadeTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * 主窗口：标题栏 + 赛道俯视图 + 右侧排名与车况 + 底部控制条与玩家指令条。
 *
 * <p>中央区域是个 StackPane，赛道上可以盖两层东西：
 * 顶部的**事件横幅**（安全车 / 天气突变）和中央的**赛后结算面板**。</p>
 */
public class MainWindow {

    private final BorderPane root = new BorderPane();
    private final TrackView trackView = new TrackView();
    private final StandingsPanel standingsPanel = new StandingsPanel();
    private final CarStatusPanel carStatusPanel = new CarStatusPanel();
    private final PlayerPanel playerPanel;
    /** 右下角天气面板。 */
    private final WeatherPanel weatherPanel;

    private final StackPane centerArea = new StackPane();
    private final VBox toastLayer = new VBox(6);
    private final VBox resultOverlay = new VBox(10);
    /** 左上角赛事播报（旗语下面）。 */
    private final EventFeed eventFeed = new EventFeed();

    /** 左上角旗语指示。 */
    private final HBox flagBadge = new HBox(10);
    private final Region flagBar = new Region();
    private final Label flagName = new Label();
    private final Label flagMeaning = new Label();
    /** 重大事件横幅（安全车 / 撞车 / 红旗）。 */
    /** 暖胎圈覆盖层。 */
    private final VBox formationOverlay = new VBox(10);
    private final ProgressBar formationBar = new ProgressBar(0);
    private final Label formationHint = new Label("赛车正在编队，比赛即将开始");
    private final VBox majorBanner = new VBox(6);
    private final Label majorText = new Label();
    private boolean majorActive;
    private double speedBeforeMajor = 1.0;

    private final Label trackLabel = new Label();
    private final Label lapLabel = new Label();
    private final Label weatherLabel = new Label();
    /** 顶部赛历条：R1~R8，点一下就换站。 */
    private final HBox roundStrip = new HBox(4);
    private final Label safetyCarLabel = new Label("安全车");
    private final Label statusLabel = new Label("准备发车");
    private final ProgressBar raceProgress = new ProgressBar(0);
    private final Button playButton = new Button("暂停");

    /** 原始配置（不含玩家改名），设置改完要拿它重新套用。 */
    private final GameData rawData;
    private GameData data;
    private Track track;
    private RaceSimulator simulator;
    private RaceSession session;
    private RaceSetup.RaceLineup lineup;
    private Integer selectedCarNumber;
    private int toastedEvents;
    private boolean resultShown;

    public MainWindow(GameData data, Track track) {
        this.rawData = data;
        this.data = com.endurance.data.PlayerProfile.load().applyTo(data);
        this.track = track;
        this.playerPanel = new PlayerPanel(this.data);
        this.playerPanel.applyTrackRestrictions(track);
        this.weatherPanel = new WeatherPanel(
                com.endurance.data.WeatherProfile.load());

        root.getStyleClass().add("app-root");
        buildCenter();
        root.setTop(buildHeader());
        root.setCenter(centerArea);
        root.setRight(buildSidebar());
        root.setBottom(buildBottom());

        standingsPanel.setOnSelect(car -> {
            selectedCarNumber = car.number();
            refresh();
        });
        trackView.setOnCarClick(this::selectCar);

        startRace(track, 20260101L);
    }

    // ------------------------------------------------------------------
    // 布局
    // ------------------------------------------------------------------

    private void buildCenter() {
        toastLayer.setAlignment(Pos.TOP_CENTER);
        toastLayer.setPadding(new Insets(18));
        toastLayer.setMouseTransparent(true);
        toastLayer.setPickOnBounds(false);

        resultOverlay.setAlignment(Pos.CENTER);
        resultOverlay.getStyleClass().add("result-panel");
        resultOverlay.setVisible(false);
        resultOverlay.setManaged(false);
        StackPane.setAlignment(resultOverlay, Pos.CENTER);

        buildFormationOverlay();
        buildFlagBadge();
        buildMajorBanner();
        // 左上角一列：旗语 + 播报，眼睛扫一下就知道战况
        VBox leftColumn = new VBox(10, flagBadge, eventFeed);
        leftColumn.setAlignment(Pos.TOP_LEFT);
        leftColumn.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        StackPane.setAlignment(leftColumn, Pos.TOP_LEFT);
        StackPane.setMargin(leftColumn, new Insets(16));
        centerArea.getChildren().addAll(trackView, leftColumn, toastLayer, majorBanner,
                formationOverlay, resultOverlay);
    }

    /** 暖胎圈：进游戏先给玩家看清楚局面，再按开始。 */
    private void buildFormationOverlay() {
        Label title = new Label("暖胎圈");
        title.getStyleClass().add("formation-title");
        formationHint.getStyleClass().add("formation-hint");
        formationBar.setPrefWidth(320);
        formationBar.getStyleClass().add("formation-bar");

        Button skip = new Button("跳过暖胎圈");
        skip.getStyleClass().add("primary-button");
        skip.setOnAction(e -> {
            session.skipFormation();
            showToast("绿旗 · 比赛开始", "toast-weather");
            refresh();
        });

        formationOverlay.getChildren().addAll(title, formationHint, formationBar, skip);
        formationOverlay.setAlignment(Pos.CENTER);
        formationOverlay.getStyleClass().add("formation-overlay");
        formationOverlay.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        StackPane.setAlignment(formationOverlay, Pos.CENTER);
    }

    private void updateFormationOverlay() {
        boolean warming = session.isFormationLap();
        formationOverlay.setVisible(warming);
        formationOverlay.setManaged(warming);
        if (warming) {
            formationBar.setProgress(session.formationProgress());
            formationHint.setText(String.format(java.util.Locale.ROOT,
                    "赛车正在编队 · 还有 %.0f%% 出发", (1 - session.formationProgress()) * 100));
        }
    }

    private void buildFlagBadge() {
        flagBar.setPrefSize(7, 38);
        flagBar.setMinSize(7, 38);
        flagBar.setStyle("-fx-background-color: #22C55E;");
        flagName.getStyleClass().add("flag-name");
        flagMeaning.getStyleClass().add("flag-meaning");
        VBox text = new VBox(1, flagName, flagMeaning);
        flagBadge.getChildren().addAll(flagBar, text);
        flagBadge.setAlignment(Pos.CENTER_LEFT);
        flagBadge.getStyleClass().add("flag-badge");
        flagBadge.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        flagBadge.setMaxWidth(Region.USE_PREF_SIZE);
    }

    private void buildMajorBanner() {
        Label title = new Label("赛会通告");
        title.getStyleClass().add("major-title");
        majorText.getStyleClass().add("major-text");
        majorText.setWrapText(true);

        Button go = new Button("继续比赛");
        go.getStyleClass().add("primary-button");
        go.setOnAction(e -> dismissMajor());

        Button pit = new Button("去下指令");
        pit.getStyleClass().add("ghost-button");
        pit.setOnAction(e -> {
            dismissMajor();
            statusLabel.setText("已暂停 · 在下方指令条决定这一圈要不要进站");
            session.pause();
            refresh();
        });

        HBox actions = new HBox(10, go, pit);
        majorBanner.getChildren().addAll(title, majorText, actions);
        majorBanner.getStyleClass().add("major-banner");
        majorBanner.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        majorBanner.setVisible(false);
        majorBanner.setManaged(false);
        StackPane.setAlignment(majorBanner, Pos.TOP_CENTER);
        StackPane.setMargin(majorBanner, new Insets(96, 0, 0, 0));
    }

    /** 出大事了：自动把速度降到 0.25x，等玩家看完再决定。 */
    private void raiseMajor(String message) {
        if (majorActive) {
            majorText.setText(message);
            return;
        }
        majorActive = true;
        speedBeforeMajor = session.getSpeed();
        session.setSpeed(0.25);
        majorText.setText(message);
        majorBanner.setVisible(true);
        majorBanner.setManaged(true);
    }

    private void dismissMajor() {
        if (!majorActive) {
            return;
        }
        majorActive = false;
        session.setSpeed(speedBeforeMajor);
        majorBanner.setVisible(false);
        majorBanner.setManaged(false);
    }

    private Node buildHeader() {
        Label brand = new Label("ENDURANCE STRATEGY");
        brand.getStyleClass().add("brand");

        Label brandSub = new Label("耐力赛策略");
        brandSub.getStyleClass().add("brand-sub");

        Region divider = new Region();
        divider.getStyleClass().add("divider");
        divider.setPrefWidth(1);

        trackLabel.getStyleClass().add("header-strong");
        lapLabel.getStyleClass().add("header-strong");
        weatherLabel.getStyleClass().add("weather-chip");

        safetyCarLabel.getStyleClass().add("safety-chip");
        safetyCarLabel.setVisible(false);
        safetyCarLabel.setManaged(false);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        buildRoundStrip();

        HBox header = new HBox(12,
                new VBox(0, brand, brandSub),
                divider, trackLabel, lapLabel,
                spacer, roundStrip, safetyCarLabel, weatherLabel);
        header.getStyleClass().add("header");
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(12, 18, 12, 18));
        return header;
    }

    private Node buildSidebar() {
        standingsPanel.getStyleClass().add("side-panel");
        carStatusPanel.getStyleClass().add("side-panel");

        // 排名表放滚动容器里：车队多的时候不会把车况面板挤出屏幕
        ScrollPane standingsScroll = new ScrollPane(standingsPanel);
        standingsScroll.setFitToWidth(true);
        standingsScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        standingsScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        standingsScroll.getStyleClass().add("sidebar-scroll");
        VBox.setVgrow(standingsScroll, Priority.ALWAYS);
        carStatusPanel.setMinHeight(Region.USE_PREF_SIZE);
        VBox sidebar = new VBox(10, weatherPanel, standingsScroll, carStatusPanel);
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPadding(new Insets(12));
        sidebar.setPrefWidth(432);
        sidebar.setMinWidth(392);
        sidebar.setMaxWidth(480);
        VBox.setVgrow(standingsPanel, Priority.ALWAYS);
        return sidebar;
    }

    private static String speedLabel(double speed) {
        if (Math.abs(speed - 0.5) < 1e-6) {
            return "0.5x";
        }
        if (Math.abs(speed - 1.25) < 1e-6) {
            return "1.25x";
        }
        return "1x";
    }

    private Node buildBottom() {
        Button stepButton = new Button("下一圈");
        stepButton.getStyleClass().add("ghost-button");
        stepButton.setOnAction(e -> session.stepOnce());

        playButton.getStyleClass().add("primary-button");
        playButton.setOnAction(e -> togglePlay());

        ToggleGroup speedGroup = new ToggleGroup();
        HBox speedBox = new HBox(4);
        // 只有 0.5 / 1 / 1.25 三档：再快就看不清车在干什么了
        for (double speed : new double[]{0.5, 1, 1.25}) {
            ToggleButton button = new ToggleButton(speedLabel(speed));
            button.getStyleClass().add("speed-button");
            button.setToggleGroup(speedGroup);
            button.setUserData(speed);
            button.setFocusTraversable(false);
            if (speed == 1) {
                button.setSelected(true);
            }
            speedBox.getChildren().add(button);
        }
        speedGroup.selectedToggleProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue != null && session != null) {
                session.setSpeed((double) newValue.getUserData());
            }
        });

        raceProgress.setMaxWidth(Double.MAX_VALUE);
        raceProgress.getStyleClass().add("race-progress");
        HBox.setHgrow(raceProgress, Priority.ALWAYS);

        statusLabel.getStyleClass().add("status");
        statusLabel.setMinWidth(240);

        Button saveButton = new Button("存档");
        saveButton.getStyleClass().add("ghost-button");
        saveButton.setOnAction(e -> saveRace());

        Button loadButton = new Button("读档");
        loadButton.getStyleClass().add("ghost-button");
        loadButton.setOnAction(e -> loadRace());

        Button calendarButton = new Button("赛历");
        calendarButton.getStyleClass().add("ghost-button");
        calendarButton.setOnAction(e -> openCalendar());

        Button teamButton = new Button("我的车队");
        teamButton.getStyleClass().add("ghost-button");
        teamButton.setOnAction(e -> openTeamSettings());

        Button historyButton = new Button("战绩");
        historyButton.getStyleClass().add("ghost-button");
        historyButton.setOnAction(e -> HistoryWindow.show(getRoot().getScene().getWindow()));

        HBox controls = new HBox(12, playButton, stepButton, speedBox, calendarButton, teamButton, saveButton,
                loadButton, historyButton, raceProgress, statusLabel);
        controls.getStyleClass().add("controls");
        controls.setAlignment(Pos.CENTER_LEFT);
        controls.setPadding(new Insets(10, 18, 10, 18));

        VBox bottom = new VBox(controls, playerPanel);
        bottom.getStyleClass().add("bottom-area");
        return bottom;
    }

    // ------------------------------------------------------------------
    // 比赛生命周期
    // ------------------------------------------------------------------

    /** 开始一场新比赛。 */
    public void startRace(Track track, long seed) {
        this.track = track;
        if (session != null) {
            session.stop();
        }
        // 旧比赛的 AI 线程池必须关掉，否则每切一次赛道就漏一个池子
        if (lineup != null) {
            lineup.close();
        }
        this.lineup = RaceSetup.build(data, track, track.defaultLaps(), seed);
        this.simulator = new RaceSimulator(lineup.config(), lineup.strategies());
        this.session = new RaceSession(simulator);
        this.selectedCarNumber = null;
        this.toastedEvents = 0;
        this.resultShown = false;

        toastLayer.getChildren().clear();
        hideResultOverlay();
        playerPanel.bind(lineup.playerCar(), lineup.playerStrategy());
        if (lineup.playerCar() != null) {
            selectedCarNumber = lineup.playerCar().number();
        }

        session.setOnUpdate(this::refresh);
        session.start();
        refresh();
    }

    private void togglePlay() {
        if (session == null) {
            return;
        }
        if (session.isFinished()) {
            startRace(track, System.currentTimeMillis());
            return;
        }
        if (session.isRunning()) {
            session.pause();
            statusLabel.setText("已暂停 · 下指令后点「继续」");
        } else {
            session.resume();
        }
        refresh();
    }

    // ------------------------------------------------------------------
    // 刷新
    // ------------------------------------------------------------------

    /** 把仿真状态同步到界面。 */
    public void refresh() {
        if (simulator == null) {
            return;
        }
        trackView.update(simulator, session.phase(), selectedCarNumber);
        standingsPanel.update(simulator, selectedCarNumber);
        carStatusPanel.update(resolveSelectedCar());
        playerPanel.refresh();

        trackLabel.setText(track.calendarLabel());
        lapLabel.setText("第 " + simulator.completedLaps() + " / " + simulator.totalLaps() + " 圈");
        weatherLabel.setText(simulator.currentWeather().displayName());

        updateFlagBadge();
        updateFormationOverlay();
        weatherPanel.update(simulator);
        boolean safetyCar = simulator.isSafetyCarActive();
        safetyCarLabel.setVisible(safetyCar);
        safetyCarLabel.setManaged(safetyCar);

        double progress = simulator.totalLaps() == 0 ? 0
                : simulator.completedLaps() / (double) simulator.totalLaps();
        raceProgress.setProgress(progress);

        playButton.setText(simulator.isFinished() ? "再来一场"
                : session.isRunning() ? "暂停" : "继续");

        playerPanel.setRaceFinished(simulator.isFinished());
        toastNewEvents();

        if (simulator.isFinished()) {
            var winner = simulator.result().winner();
            statusLabel.setText(winner == null ? "比赛结束"
                    : "比赛结束 · 冠军 " + Format.carLabel(winner.car()));
            if (!resultShown) {
                showResultOverlay();
            }
        } else if (simulator.isSafetyCarActive()) {
            statusLabel.setText("安全车 · 还有 " + simulator.safetyCarRemaining()
                    + " 圈，现在进站更划算");
        } else {
            var latest = simulator.latestEvent();
            statusLabel.setText(latest == null ? "比赛进行中" : latest.message());
        }

        warnIfFuelLow();
    }

    /** 玩家油量见底时给个显眼提醒，避免莫名其妙的退赛。 */
/** 把当前旗语同步到左上角指示牌。 */
    private void updateFlagBadge() {
        if (simulator == null) {
            return;
        }
        com.endurance.model.RaceFlag flag = simulator.currentFlag();
        flagBar.setStyle("-fx-background-color: " + flag.colorHex() + ";");
        flagName.setText(flag.displayName());
        flagName.setStyle("-fx-text-fill: " + flag.colorHex() + ";");
        String extra = flag == com.endurance.model.RaceFlag.RED && simulator.redFlagLaps() > 0
                ? " · 还剩 " + simulator.redFlagLaps() + " 圈"
                : simulator.isSafetyCarActive() ? " · 还剩 " + simulator.safetyCarRemaining() + " 圈" : "";
        flagMeaning.setText(flag.meaning() + extra);
    }

    private void warnIfFuelLow() {
        Car player = lineup.playerCar();
        if (player == null || player.isRetired() || simulator.isFinished()) {
            return;
        }
        if (player.fuelKg() > 15 || lineup.playerStrategy().hasPending()) {
            return;
        }
        statusLabel.setText("油量只剩 " + Math.round(player.fuelKg()) + " kg，再不进站就要退赛了！");
    }

    /** 当前选中的车号，测试用。 */
    public Integer selectedCar() {
        return selectedCarNumber;
    }

    /** 指令面板，测试用。 */
    public PlayerPanel playerPanel() {
        return playerPanel;
    }

    /** 排名面板，测试用。 */
    public StandingsPanel standingsPanel() {
        return standingsPanel;
    }

    /** 车况面板当前显示的车号，测试用。 */
    public String carStatusTitle() {
        return carStatusPanel.getTitleText();
    }

    private Car resolveSelectedCar() {
        if (simulator == null) {
            return null;
        }
        for (Car car : simulator.cars()) {
            if (selectedCarNumber != null && car.number() == selectedCarNumber) {
                return car;
            }
        }
        var standings = simulator.liveStandings();
        return standings.isEmpty() ? null : standings.get(0);
    }

    // ------------------------------------------------------------------
    // 事件横幅
    // ------------------------------------------------------------------

    private void toastNewEvents() {
        var events = simulator.events();
        int currentLap = simulator.completedLaps();
        // 一帧可能跨好几圈（倍速/长帧），补上最近 20 条，别把事件丢了
        int start = Math.max(toastedEvents, events.size() - 20);
        for (int i = start; i < events.size(); i++) {
            var event = events.get(i);
            boolean isCurrentLap = event.lap() == currentLap;
            // 所有事件都进左上角播报条，重大事件另外弹横幅
            String tone = switch (event.type()) {
                case SAFETY_CAR, CRASH -> "safety";
                case PENALTY -> "penalty";
                case WEATHER_CHANGE -> "weather";
                case PIT_STOP -> "pit";
                default -> "normal";
            };
            eventFeed.push(event.lap(), event.message(), tone);
            if (!isCurrentLap) {
                // 补记的历史事件只进播报条，不再弹横幅打扰玩家
                continue;
            }

            if (event.type() == com.endurance.sim.RaceEvent.Type.WEATHER_CHANGE) {
                showToast("天气变化 · " + event.message(), "toast-weather");
                raiseMajor("天气突变：" + event.message() + "。要不要换胎？");
            } else if (event.type() == com.endurance.sim.RaceEvent.Type.SAFETY_CAR) {
                showToast("安全车 · " + event.message(), "toast-safety");
                raiseMajor("安全车出动：" + event.message() + "。现在进站最划算，要不要下去下指令？");
            } else if (event.type() == com.endurance.sim.RaceEvent.Type.CRASH) {
                showToast("事故 · " + event.message(), "toast-safety");
                raiseMajor("赛道事故：" + event.message());
            } else if (event.type() == com.endurance.sim.RaceEvent.Type.FLAG
                    && simulator.currentFlag() == com.endurance.model.RaceFlag.RED) {
                showToast("红旗 · " + event.message(), "toast-safety");
                raiseMajor("红旗：" + event.message() + "，比赛暂停，正好可以调整策略。");
            }
        }
        toastedEvents = events.size();
    }

    /** 在赛道上方弹出一条会淡出的横幅。 */
    public void showToast(String text, String styleClass) {
        Label label = new Label(text);
        label.getStyleClass().addAll("toast", styleClass);
        toastLayer.getChildren().add(label);
        while (toastLayer.getChildren().size() > 3) {
            toastLayer.getChildren().remove(0);
        }
        FadeTransition fade = new FadeTransition(Duration.seconds(0.9), label);
        fade.setDelay(Duration.seconds(2.6));
        fade.setFromValue(1);
        fade.setToValue(0);
        fade.setOnFinished(e -> toastLayer.getChildren().remove(label));
        fade.play();
    }

    // ------------------------------------------------------------------
    // 赛后结算
    // ------------------------------------------------------------------

    private void showResultOverlay() {
        resultShown = true;
        resultOverlay.getChildren().clear();

        Label title = new Label("比赛结束");
        title.getStyleClass().add("result-title");

        var standings = simulator.result().standings();
        VBox rows = new VBox(4);
        for (int i = 0; i < Math.min(6, standings.size()); i++) {
            var entry = standings.get(i);
            Label row = new Label(String.format("%d.  #%d %s   %s",
                    entry.position(),
                    entry.car().number(),
                    entry.car().driverName(),
                    entry.retired() ? "退赛" : Format.total(entry.totalTime())));
            row.getStyleClass().add(entry.retired() ? "result-row-retired" : "result-row");
            if (lineup.playerCar() != null && entry.car().number() == lineup.playerCar().number()) {
                row.getStyleClass().add("result-row-player");
            }
            rows.getChildren().add(row);
        }

        String verdict = "比赛结束";
        if (lineup.playerCar() != null) {
            for (var entry : standings) {
                if (entry.car().number() == lineup.playerCar().number()) {
                    verdict = entry.retired()
                            ? "你的车退赛了"
                            : "你的名次：第 " + entry.position() + " 名";
                    break;
                }
            }
        }
        Label summary = new Label(verdict);
        summary.getStyleClass().add("result-summary");

        Button again = new Button("再来一场");
        again.getStyleClass().add("primary-button");
        again.setOnAction(e -> startRace(track, System.currentTimeMillis()));

        Button review = new Button("查看复盘");
        review.getStyleClass().add("ghost-button");
        review.setOnAction(e -> openReview());

        Button logButton = new Button("数据表");
        logButton.getStyleClass().add("ghost-button");
        logButton.setOnAction(e -> openRaceLog());

        HBox actions = new HBox(10, again, review, logButton);
        actions.setAlignment(Pos.CENTER_LEFT);

        resultOverlay.getChildren().addAll(title, summary, rows, actions);
        resultOverlay.setVisible(true);
        resultOverlay.setManaged(true);
    }

    /** 顶部赛历条：8 个轮次按钮，当前站高亮。 */
    private void buildRoundStrip() {
        roundStrip.setAlignment(Pos.CENTER_RIGHT);
        roundStrip.getStyleClass().add("round-strip");
        for (Track item : data.tracks()) {
            Button button = new Button(item.round() > 0 ? "R" + item.round() : "—");
            button.getStyleClass().add("round-button");
            button.setFocusTraversable(false);
            button.setTooltip(new javafx.scene.control.Tooltip(
                    item.event() + "\n" + item.name() + "\n"
                            + item.defaultLaps() + " 圈 · " + item.categories()));
            button.setUserData(item);
            button.setOnAction(e -> switchTrack(item));
            button.setDisable(item.round() == track.round());
            roundStrip.getChildren().add(button);
        }
    }

    /** 供自检脚本切站：按轮次选择。 */
    public void selectRound(int round) {
        for (Track item : data.tracks()) {
            if (item.round() == round) {
                switchTrack(item);
                return;
            }
        }
    }

    /** 当前站轮次。 */
    public int currentRound() {
        return track.round();
    }

    /** 换站：按新赛道的参数与形状重开一场。 */
    private void switchTrack(Track target) {
        if (target == null || target.round() == track.round()) {
            return;
        }
        for (Node node : roundStrip.getChildren()) {
            node.setDisable(node.getUserData() == target);
        }
        startRace(target, System.currentTimeMillis());
        statusLabel.setText("已切换到 " + target.calendarLabel());
    }

    /** 打开完整赛历窗口。 */
    private void openCalendar() {
        CalendarWindow.show(getRoot().getScene() == null ? null
                        : getRoot().getScene().getWindow(),
                data.tracks(), track.round(),
                this::switchTrack);
    }
    /** 打开"我的车队"设置：改车队名与车手名，保存后重开一场。 */
    private void openTeamSettings() {
        TeamSettingsWindow.show(getRoot().getScene() == null ? null
                        : getRoot().getScene().getWindow(),
                com.endurance.data.PlayerProfile.load(),
                profile -> {
                    this.data = profile.applyTo(rawData);
                    startRace(track, System.currentTimeMillis());
                    statusLabel.setText("车队已更新：" + profile);
                });
    }

    /** 打开数据表：每一圈每台车的完整账本。 */
    private void openRaceLog() {
        if (simulator == null || simulator.result() == null) {
            return;
        }
        int carNumber = lineup.playerCar() != null ? lineup.playerCar().number() : -1;
        RaceLogWindow.show(simulator.result(), carNumber,
                getRoot().getScene() == null ? null : getRoot().getScene().getWindow());
    }

    /** 打开复盘窗口：圈速曲线 + 胎况变化 + 决策评分。 */
    private void openReview() {
        if (simulator == null || simulator.result() == null) {
            return;
        }
        int carNumber = lineup.playerCar() != null ? lineup.playerCar().number()
                : simulator.result().standings().get(0).car().number();
        ReviewWindow.show(data, simulator.result(), carNumber, lineup.config(),
                getRoot().getScene() == null ? null : getRoot().getScene().getWindow());
    }

    // ------------------------------------------------------------------
    // 存档 / 读档 / 战绩
    // ------------------------------------------------------------------

    private void saveRace() {
        if (simulator == null) {
            return;
        }
        try {
            // 存档要把"是否还在暖胎圈"一起记下，读档后直接进比赛
            RaceSave.save(simulator, session.isFormationLap(), RaceSave.defaultFile());
            statusLabel.setText("已存档 · 第 " + simulator.completedLaps() + " / "
                    + simulator.totalLaps() + " 圈（saves/race.json）");
        } catch (Exception e) {
            showError("存档失败", e.getMessage());
        }
    }

    private void loadRace() {
        Path file = RaceSave.defaultFile();
        if (!Files.isRegularFile(file)) {
            statusLabel.setText("没有找到存档（saves/race.json）");
            return;
        }
        try {
            RaceSave.Meta meta = RaceSave.peek(file.toFile());
            Track savedTrack = data.track(meta.trackId());
            startRace(savedTrack, meta.seed());
            session.pause();

            Map<String, TireCompound> tires = new LinkedHashMap<>();
            for (TireCompound tire : data.tires()) {
                tires.put(tire.id(), tire);
            }
            RaceSave.restore(file.toFile(), simulator, tires);

            resultShown = false;
            hideResultOverlay();
            toastedEvents = simulator.events().size();
            refresh();
            statusLabel.setText("已读档 · 从第 " + simulator.completedLaps() + " 圈继续");
        } catch (Exception e) {
            showError("读档失败", e.getMessage());
        }
    }

    /** 比赛结束时把成绩写进历史战绩。 */
    private void recordHistory() {
        if (simulator == null || simulator.result() == null) {
            return;
        }
        try {
            Car player = lineup.playerCar();
            int position = 0;
            boolean retired = false;
            double playerTime = 0;
            double bestLap = 0;
            for (var entry : simulator.result().standings()) {
                if (player != null && entry.car().number() == player.number()) {
                    position = entry.position();
                    retired = entry.retired();
                    playerTime = entry.totalTime();
                    bestLap = entry.car().bestLapTime() == Double.MAX_VALUE
                            ? 0 : entry.car().bestLapTime();
                }
            }
            int score = player == null ? 0
                    : RaceReport.analyze(simulator.result(), player.number(), data,
                    lineup.config()).score();
            var winner = simulator.result().winner();
            RaceHistory.append(RaceHistory.record(
                    track.name(), simulator.totalLaps(), position, retired, playerTime,
                    player == null ? 0 : player.pitStops(), bestLap, score,
                    winner == null ? "—" : winner.car().driverName()));
        } catch (Exception e) {
            System.err.println("写入历史战绩失败: " + e.getMessage());
        }
    }

    private void showError(String title, String message) {
        javafx.scene.control.Alert alert =
                new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void hideResultOverlay() {
        resultOverlay.setVisible(false);
        resultOverlay.setManaged(false);
    }

    // ------------------------------------------------------------------

    public BorderPane getRoot() {
        return root;
    }

    public RaceSimulator getSimulator() {
        return simulator;
    }

    public RaceSession getSession() {
        return session;
    }

    public PlayerPanel getPlayerPanel() {
        return playerPanel;
    }

    public RaceSetup.RaceLineup getLineup() {
        return lineup;
    }

    public Track getTrack() {
        return track;
    }

    public boolean isResultShown() {
        return resultShown;
    }

    /** 供自检脚本强制刷新一帧。 */
    public void refreshNow() {
        refresh();
    }

    /** 供自检脚本选中某辆车。 */
    public void selectCar(int number) {
        this.selectedCarNumber = number;
        refresh();
    }
}
