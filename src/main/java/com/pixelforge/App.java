package com.pixelforge;

import com.pixelforge.ui.MainWindow;
import com.pixelforge.ui.Style;
import com.pixelforge.util.Log;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * 程序入口。
 *
 * <p>只负责：初始化日志 → 构建主窗口 → 套主题 → 显示。业务逻辑全部在
 * {@link MainWindow} 及其协作对象里。</p>
 */
public class App extends Application {

    private static final double WINDOW_WIDTH = 1440;
    private static final double WINDOW_HEIGHT = 900;

    @Override
    public void start(Stage stage) {
        Log.init();
        Log.info("启动 " + AppInfo.NAME + " " + AppInfo.VERSION);
        installGlobalExceptionHandler();

        MainWindow mainWindow = new MainWindow();

        Scene scene = new Scene(mainWindow.getRoot(), WINDOW_WIDTH, WINDOW_HEIGHT);
        Style.apply(scene);
        mainWindow.installHandlers(scene);

        stage.setTitle(AppInfo.NAME + " " + AppInfo.VERSION);
        stage.setScene(scene);
        stage.setMinWidth(1060);
        stage.setMinHeight(680);
        stage.setOnCloseRequest(mainWindow::handleCloseRequest);
        stage.show();
        mainWindow.attachStage(stage);

        Platform.runLater(mainWindow::fitCanvasOnStartup);
        Platform.runLater(mainWindow::maybeRestoreAutosave);

        Log.info("主窗口已显示");
    }

    /**
     * 兜底异常处理：任何漏网的异常都写日志并提示用户，而不是让程序"啪"地消失。
     */
    private static void installGlobalExceptionHandler() {
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            Log.error("未捕获异常（线程 " + thread.getName() + "）", throwable);
            if (Platform.isFxApplicationThread()) {
                try {
                    com.pixelforge.ui.dialogs.Alerts.error(null,
                            "程序遇到一个未预期的问题，已记录到 logs/app.log", throwable);
                } catch (Throwable ignored) {
                    // 弹窗本身失败时不再连锁抛错
                }
            }
        });
    }

    @Override
    public void stop() {
        Log.info("程序退出");
    }

    public static void main(String[] args) {
        // JavaFX 默认把着色器缓存写进用户主目录，部分机器没有写权限会刷一屏告警；
        // 这里统一改到临时目录，必须在 launch 之前设置。
        if (System.getProperty("javafx.cachedir") == null) {
            String cacheDir = new java.io.File(System.getProperty("java.io.tmpdir"),
                    "PixelForge-cache").getAbsolutePath();
            System.setProperty("javafx.cachedir", cacheDir);
        }
        launch(args);
    }
}
