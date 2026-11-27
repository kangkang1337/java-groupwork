package com.endurance.dev;

import com.endurance.data.PlayerProfile;
import com.endurance.ui.TeamSettingsWindow;
import com.endurance.ui.Theme;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.VBox;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.concurrent.CountDownLatch;

/**
 * 「我的车队」设置界面自检：渲染成 PNG。
 *
 * <p>用法：{@code SettingsSnapshot 输出.png [宽] [高]}</p>
 */
public final class SettingsSnapshot {

    private SettingsSnapshot() {
    }

    public static void main(String[] args) throws Exception {
        com.endurance.util.FxEnv.configure();
        String output = args.length > 0 ? args[0] : "team-settings.png";
        int width = args.length > 1 ? Integer.parseInt(args[1]) : 460;
        int height = args.length > 2 ? Integer.parseInt(args[2]) : 340;

        CountDownLatch latch = new CountDownLatch(1);
        Throwable[] failure = new Throwable[1];

        Platform.startup(() -> {
            try {
                VBox view = TeamSettingsWindow.createView(PlayerProfile.defaults(), null);

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
}
