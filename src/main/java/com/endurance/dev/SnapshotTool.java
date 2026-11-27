package com.endurance.dev;

import com.endurance.data.DataLoader;
import com.endurance.data.GameData;
import com.endurance.model.Track;
import com.endurance.ui.MainWindow;
import com.endurance.ui.Theme;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.concurrent.CountDownLatch;

/**
 * 开发期自检工具：把主窗口离屏渲染成 PNG，用于自动核对界面布局。
 *
 * <p>用法：{@code SnapshotTool 输出.png [宽] [高] [推进圈数] [赛道id]}</p>
 */
public final class SnapshotTool {

    private SnapshotTool() {
    }

    public static void main(String[] args) throws Exception {
        com.endurance.util.FxEnv.configure();
        String output = args.length > 0 ? args[0] : "snapshot.png";
        int width = args.length > 1 ? Integer.parseInt(args[1]) : 1360;
        int height = args.length > 2 ? Integer.parseInt(args[2]) : 820;
        int laps = args.length > 3 ? Integer.parseInt(args[3]) : 26;
        String trackId = args.length > 4 ? args[4] : null;

        CountDownLatch latch = new CountDownLatch(1);
        Throwable[] failure = new Throwable[1];

        Platform.startup(() -> {
            try {
                GameData data = DataLoader.loadDefault();
                Track track = trackId == null ? data.defaultTrack() : data.track(trackId);
                MainWindow window = new MainWindow(data, track);
                window.getSession().pause();
                if (laps < 0) {
                    // 负数 = 一直推进到有重大事件（安全车 / 红旗）为止
                    for (int i = 0; i < 80 && !window.getSimulator().isSafetyCarActive()
                            && window.getSimulator().redFlagLaps() == 0; i++) {
                        topUpPlayer(window, data);
                        window.getSession().advance(1);
                    }
                } else {
                    // 逐圈推进并顺手替玩家车安排进站：
                    // 否则演示图里的玩家车会一路把油烧干退赛，画面很难看
                    for (int i = 0; i < laps; i++) {
                        topUpPlayer(window, data);
                        window.getSession().advance(1);
                    }
                }
                if (window.getSimulator().liveStandings().size() > 0) {
                    window.selectCar(window.getSimulator().liveStandings().get(0).number());
                }
                // 演示"已排队一条进站指令"的状态
                if (window.getLineup().playerStrategy() != null) {
                    window.getLineup().playerStrategy().queue(
                            new com.endurance.sim.PitDecision(data.hard(), 45));
                    window.getPlayerPanel().refresh();
                }

                Scene scene = new Scene(window.getRoot(), width, height);
                Theme.apply(scene);

                window.getRoot().applyCss();
                window.getRoot().resize(width, height);
                window.getRoot().layout();
                window.refresh();
                // 清掉浮动通告：它们会盖在赛道图上，截图很难看
                window.clearToasts();

                WritableImage image = window.getRoot()
                        .snapshot(new SnapshotParameters(), new WritableImage(width, height));
                writePng(image, new File(output));
                System.out.println("SNAPSHOT_OK " + new File(output).getAbsolutePath());
            } catch (Throwable t) {
                failure[0] = t;
                t.printStackTrace();
            } finally {
                latch.countDown();
            }
        });

        latch.await();
        Platform.exit();
        System.exit(failure[0] == null ? 0 : 2);
    }

    /** 演示用：玩家车油量见底前替它排一条进站指令，免得截图里总是"退赛"。 */
    private static void topUpPlayer(MainWindow window, GameData data) {
        var lineup = window.getLineup();
        if (lineup == null || lineup.playerCar() == null || lineup.playerStrategy() == null) {
            return;
        }
        var car = lineup.playerCar();
        if (car.isRetired() || lineup.playerStrategy().hasPending()) {
            return;
        }
        // 只剩约 12 圈的油就补满，整场比赛因此会自然出现两次进站
        double reserve = window.getSimulator().config().track().fuelPerLap() * 12 + 5;
        if (car.fuelKg() < reserve) {
            lineup.playerStrategy().queue(new com.endurance.sim.PitDecision(data.hard(), 70));
        }
    }

    private static void writePng(WritableImage image, File target) throws Exception {
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        BufferedImage buffered = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        PixelReader reader = image.getPixelReader();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                buffered.setRGB(x, y, reader.getArgb(x, y));
            }
        }
        File parent = target.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        ImageIO.write(buffered, "png", target);
    }
}
