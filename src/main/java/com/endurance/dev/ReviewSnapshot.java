package com.endurance.dev;

import com.endurance.data.GameData;
import com.endurance.model.Track;
import com.endurance.sim.RaceResult;
import com.endurance.sim.RaceSimulator;
import com.endurance.ui.ReviewView;
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
 * 复盘界面自检：跑完一场比赛 → 渲染复盘窗口 → 存成 PNG。
 *
 * <p>用法：{@code ReviewSnapshot 输出.png [宽] [高] [赛道id] [车号]}</p>
 */
public final class ReviewSnapshot {

    private ReviewSnapshot() {
    }

    public static void main(String[] args) throws Exception {
        com.endurance.util.FxEnv.configure();
        String output = args.length > 0 ? args[0] : "review.png";
        int width = args.length > 1 ? Integer.parseInt(args[1]) : 1180;
        int height = args.length > 2 ? Integer.parseInt(args[2]) : 700;
        String trackId = args.length > 3 ? args[3] : null;
        int carNumber = args.length > 4 ? Integer.parseInt(args[4]) : -1;

        CountDownLatch latch = new CountDownLatch(1);
        Throwable[] failure = new Throwable[1];

        Platform.startup(() -> {
            try {
                GameData data = DataLoaderHolder.load();
                Track track = trackId == null ? data.defaultTrack() : data.track(trackId);

                var lineup = com.endurance.setup.RaceSetup.build(
                        data, track, track.defaultLaps(), 20260101L, false, false);
                RaceSimulator simulator = new RaceSimulator(lineup.config(), lineup.strategies());
                RaceResult result = simulator.run();
                lineup.close();

                int number = carNumber > 0 ? carNumber
                        : result.standings().get(0).car().number();

                ReviewView view = new ReviewView(data);
                view.load(result, number, simulator.config());

                Scene scene = new Scene(view, width, height);
                Theme.apply(scene);
                view.applyCss();
                view.resize(width, height);
                view.layout();

                WritableImage image = view.snapshot(new SnapshotParameters(),
                        new WritableImage(width, height));
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

    /** 延迟加载配置，避免在 JavaFX 线程外抛异常。 */
    private static final class DataLoaderHolder {
        static GameData load() {
            try {
                return com.endurance.data.DataLoader.loadDefault();
            } catch (java.io.IOException e) {
                throw new IllegalStateException("读取配置失败: " + e.getMessage(), e);
            }
        }
    }
}
