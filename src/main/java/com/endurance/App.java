package com.endurance;

import com.endurance.data.DataLoader;
import com.endurance.data.GameData;
import com.endurance.model.Track;
import com.endurance.ui.MainWindow;
import com.endurance.ui.Theme;
import com.endurance.util.AppLog;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

import java.io.File;

/**
 * 程序入口：装好全局异常兜底 → 加载配置 → 构建主窗口 → 开赛。
 */
public class App extends Application {

    private static final double WIDTH = 1360;
    private static final double HEIGHT = 820;

    private static volatile boolean errorDialogShown;

    @Override
    public void start(Stage stage) {
        installGlobalExceptionHandlers();
        AppLog.info("程序启动，工作目录 " + System.getProperty("user.dir"));

        GameData data;
        try {
            data = DataLoader.loadRaw();
        } catch (Exception e) {
            AppLog.error("加载配置失败", e);
            showStartupError(e);
            return;
        }

        Track track = data.defaultTrack();
        MainWindow window = new MainWindow(data, track);

        Scene scene = new Scene(window.getRoot(), WIDTH, HEIGHT);
        Theme.apply(scene);

        stage.setTitle("Endurance Strategy · " + track.name());
        stage.setScene(scene);
        stage.setMinWidth(1120);
        stage.setMinHeight(700);
        stage.show();
        AppLog.info("主窗口已打开，赛道 " + track.name());
    }

    /**
     * 全局兜底：任何线程里没被捕获的异常都写进日志，界面线程上的还会弹一次提示。
     *
     * <p>JavaFX 的事件处理器抛异常不会让程序退出，但默认只会把堆栈打到控制台——
     * 用户打包成 exe 之后控制台就看不见了，所以必须落盘。</p>
     */
    private void installGlobalExceptionHandlers() {
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> handleUnexpected(thread.getName(), error));
        Thread.currentThread().setUncaughtExceptionHandler(
                (thread, error) -> handleUnexpected("JavaFX Application Thread", error));
        Platform.runLater(() -> Thread.currentThread().setUncaughtExceptionHandler(
                (thread, error) -> handleUnexpected("JavaFX Application Thread", error)));
    }

    private void handleUnexpected(String threadName, Throwable error) {
        AppLog.error("未捕获异常（线程：" + threadName + "）", error);
        Platform.runLater(this::showRuntimeErrorOnce);
    }

    /** 同一个会话里只弹一次，避免连环报错刷屏。 */
    private void showRuntimeErrorOnce() {
        if (errorDialogShown) {
            return;
        }
        errorDialogShown = true;
        try {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("出了点问题");
            alert.setHeaderText("程序遇到了一个错误，但比赛还在继续");
            alert.setContentText("详细信息已记录到：\n" + AppLog.logFile()
                    + "\n\n如果界面卡住，可以关掉重开。");
            alert.showAndWait();
        } catch (Throwable ignored) {
            // 弹窗本身失败就算了，日志已经写了
        }
    }

    private void showStartupError(Exception error) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("启动失败");
        alert.setHeaderText("读不到 data/ 下的配置文件");
        alert.setContentText("请确认程序是在项目根目录下启动的，"
                + "并且 data/tracks.json、data/teams.json、data/tires.json 都存在。\n\n"
                + "当前目录：" + new File(System.getProperty("user.dir")).getAbsolutePath()
                + "\n错误信息：" + error.getMessage()
                + "\n日志：" + AppLog.logFile());
        alert.showAndWait();
        Platform.exit();
    }

    @Override
    public void stop() {
        AppLog.info("程序退出");
        Platform.exit();
    }

    public static void main(String[] args) {
        com.endurance.util.FxEnv.configure();
        try {
            launch(args);
        } catch (Throwable error) {
            AppLog.error("启动阶段异常", error);
            System.exit(1);
        }
    }
}
