package com.pixelforge.dev;

import com.pixelforge.io.DocumentIo;
import com.pixelforge.ui.MainWindow;
import com.pixelforge.ui.Style;
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
 * <p>用法：{@code java ... SnapshotTool 输出.png [宽] [高] [要打开的工程/图片]}</p>
 */
public final class SnapshotTool {

    private SnapshotTool() {
    }

    public static void main(String[] args) throws Exception {
        com.pixelforge.util.Log.init();
        String output = args.length > 0 ? args[0] : "snapshot.png";
        int width = args.length > 1 ? Integer.parseInt(args[1]) : 1440;
        int height = args.length > 2 ? Integer.parseInt(args[2]) : 900;
        File toOpen = args.length > 3 ? new File(args[3]) : null;

        CountDownLatch latch = new CountDownLatch(1);
        Throwable[] failure = new Throwable[1];

        Platform.startup(() -> {
            try {
                MainWindow window = new MainWindow();
                if (toOpen != null && toOpen.isFile()) {
                    window.loadDocument(DocumentIo.open(toOpen));
                }
                if (args.length >= 8) {
                    // 演示模式：建一个选区并把内容浮动起来，顺便转个角度
                    double x0 = Double.parseDouble(args[4]);
                    double y0 = Double.parseDouble(args[5]);
                    double x1 = Double.parseDouble(args[6]);
                    double y1 = Double.parseDouble(args[7]);
                    var sm = window.getSelectionManager();
                    sm.selectRect(x0, y0, x1, y1);
                    window.selectTool(com.pixelforge.model.ToolType.MOVE);
                    sm.beginFloating();
                    sm.rotateTo(Math.toRadians(10));
                    sm.moveBy(30, 16);
                } else if (args.length >= 5 && "actions".equals(args[4])) {
                    // 演示模式：跑几步操作，让历史面板有内容
                    runScriptedActions(window);
                } else if (args.length >= 5 && "filter".equals(args[4])) {
                    // 演示模式：对当前图层应用滤镜
                    window.performAction(com.pixelforge.model.AppAction.FILTER_INVERT);
                }
                Scene scene = new Scene(window.getRoot(), width, height);
                Style.apply(scene);

                window.getRoot().applyCss();
                window.getRoot().resize(width, height);
                window.getRoot().layout();

                if (toOpen != null && toOpen.isFile()) {
                    window.getCanvasView().refreshViewport();
                    window.getCanvasView().fitToViewport();
                    window.getRoot().resize(width, height);
                    window.getRoot().layout();
                }

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

    private static void runScriptedActions(MainWindow window) {
        var tools = window.getToolManager();
        window.setForegroundColor(javafx.scene.paint.Color.web("#3B82F6"));
        tools.select(com.pixelforge.model.ToolType.BRUSH);
        sim(tools, javafx.scene.input.MouseEvent.MOUSE_PRESSED, 120, 120);
        sim(tools, javafx.scene.input.MouseEvent.MOUSE_DRAGGED, 220, 160);
        sim(tools, javafx.scene.input.MouseEvent.MOUSE_RELEASED, 300, 200);

        window.setForegroundColor(javafx.scene.paint.Color.web("#F59E0B"));
        window.getRightPanel().filledProperty().set(true);
        tools.select(com.pixelforge.model.ToolType.RECTANGLE);
        sim(tools, javafx.scene.input.MouseEvent.MOUSE_PRESSED, 420, 140);
        sim(tools, javafx.scene.input.MouseEvent.MOUSE_DRAGGED, 560, 220);
        sim(tools, javafx.scene.input.MouseEvent.MOUSE_RELEASED, 620, 280);

        window.performAction(com.pixelforge.model.AppAction.LAYER_NEW);
        window.getRightPanel().getLayerPanel().refresh();

        // 最后摆一个选区 + 浮动变换，让截图同时体现选区与控制点
        var sm = window.getSelectionManager();
        sm.selectRect(430, 130, 640, 290);
        window.selectTool(com.pixelforge.model.ToolType.MOVE);
        sm.beginFloating();
        sm.rotateTo(Math.toRadians(8));
        sm.moveBy(18, 10);
    }

    private static void sim(com.pixelforge.tool.ToolManager tools,
                            javafx.event.EventType<javafx.scene.input.MouseEvent> type,
                            double x, double y) {
        var event = new javafx.scene.input.MouseEvent(type, x, y, x, y,
                javafx.scene.input.MouseButton.PRIMARY, 1,
                false, false, false, false, true, false, false, false, false, true, null);
        if (type == javafx.scene.input.MouseEvent.MOUSE_PRESSED) {
            tools.onPressed(x, y, event);
        } else if (type == javafx.scene.input.MouseEvent.MOUSE_DRAGGED) {
            tools.onDragged(x, y, event);
        } else {
            tools.onReleased(x, y, event);
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
