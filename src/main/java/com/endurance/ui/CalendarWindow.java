package com.endurance.ui;

import com.endurance.model.RaceCategory;
import com.endurance.model.Track;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * 分站日历：把官方赛历 8 站排成卡片，点一站就开跑。
 */
public final class CalendarWindow {

    private CalendarWindow() {
    }

    public static void show(Window owner, List<Track> tracks, int currentRound,
                            Consumer<Track> onSelect) {
        Stage stage = new Stage();
        BorderPane content = createView(tracks, currentRound, track -> {
            stage.close();
            if (onSelect != null) {
                onSelect.accept(track);
            }
        });
        if (owner != null) {
            stage.initOwner(owner);
            stage.initModality(Modality.WINDOW_MODAL);
        }
        Scene scene = new Scene(content, 760, 620);
        Theme.apply(scene);
        stage.setScene(scene);
        stage.setTitle("赛历 · FIA WEC 2026");
        stage.show();
    }

    /** 构建日历界面（截图工具直接渲染它）。 */
    public static BorderPane createView(List<Track> tracks, int currentRound,
                                        Consumer<Track> onSelect) {
        Label title = new Label("赛历 · FIA WEC 2026");
        title.getStyleClass().add("review-title");
        Label hint = new Label("点任意一站直接开跑；当前站用蓝框标出。圈数按「约 1 小时 45 分游戏时长」折算。");
        hint.getStyleClass().add("review-subtitle");

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        int index = 0;
        for (Track track : tracks) {
            Node card = card(track, track.round() == currentRound, onSelect);
            grid.add(card, index % 2, index / 2);
            index++;
        }

        ScrollPane scroll = new ScrollPane(grid);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("review-scroll");
        scroll.setPadding(new Insets(4));

        VBox header = new VBox(4, title, hint);
        header.setPadding(new Insets(0, 0, 12, 0));

        BorderPane root = new BorderPane();
        root.getStyleClass().add("review-root");
        root.setPadding(new Insets(16));
        root.setTop(header);
        root.setCenter(scroll);
        return root;
    }

    // ------------------------------------------------------------------

    private static Node card(Track track, boolean active, Consumer<Track> onSelect) {
        Label round = new Label(track.round() > 0 ? "R" + track.round() : "—");
        round.getStyleClass().add("calendar-round");

        Label country = new Label(track.country());
        country.getStyleClass().add("calendar-country");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox top = new HBox(8, round, spacer, country);
        top.setAlignment(Pos.CENTER_LEFT);

        Label event = new Label(track.event());
        event.getStyleClass().add("calendar-event");
        event.setWrapText(true);

        Label circuit = new Label(track.name());
        circuit.getStyleClass().add("calendar-circuit");

        Label stats = new Label(String.format(Locale.ROOT, "%.3f km · %d 弯 · %d 圈 · 基准 %.0f s",
                track.lengthMeters() / 1000.0, track.cornerCount(), track.defaultLaps(),
                track.baseLapSeconds()));
        stats.getStyleClass().add("calendar-stats");

        HBox categories = new HBox(4);
        for (RaceCategory category : track.categories()) {
            Label chip = new Label(category.shortCode());
            chip.getStyleClass().add("calendar-chip");
            categories.getChildren().add(chip);
        }

        VBox box = new VBox(6, top, event, circuit, stats, categories);
        box.getStyleClass().add("calendar-card");
        if (active) {
            box.getStyleClass().add("calendar-card-active");
        }
        box.setPrefWidth(348);
        box.setOnMouseClicked(e -> {
            if (onSelect != null) {
                onSelect.accept(track);
            }
        });
        return box;
    }
}
