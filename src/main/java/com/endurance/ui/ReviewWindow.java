package com.endurance.ui;

import com.endurance.data.GameData;
import com.endurance.model.RaceConfig;
import com.endurance.sim.RaceResult;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * 复盘窗口：把 {@link ReviewView} 包成一个独立窗口。
 */
public final class ReviewWindow {

    private ReviewWindow() {
    }

    public static void show(GameData data, RaceResult result, int carNumber,
                            RaceConfig config, Window owner) {
        Stage stage = new Stage();
        if (owner != null) {
            stage.initOwner(owner);
            stage.initModality(Modality.NONE);
        }
        ReviewView view = new ReviewView(data);
        view.load(result, carNumber, config);

        Scene scene = new Scene(view, 1180, 700);
        Theme.apply(scene);
        stage.setScene(scene);
        stage.setTitle("赛后复盘 · " + result.track().name());
        stage.show();
    }
}
