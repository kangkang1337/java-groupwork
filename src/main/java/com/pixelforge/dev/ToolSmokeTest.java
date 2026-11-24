package com.pixelforge.dev;

import com.pixelforge.model.Document;
import com.pixelforge.model.ToolType;
import com.pixelforge.tool.ToolManager;
import com.pixelforge.ui.MainWindow;
import com.pixelforge.util.ColorUtil;
import javafx.application.Platform;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;

import java.util.concurrent.CountDownLatch;

/**
 * 工具链路自检：不依赖真人操作，直接构造鼠标事件驱动 {@link ToolManager}，
 * 检查"鼠标事件 → 工具 → 图层像素"整条链路是否通畅。
 */
public final class ToolSmokeTest {

    private static int passed;
    private static int failed;

    private ToolSmokeTest() {
    }

    public static void main(String[] args) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Throwable[] failure = new Throwable[1];

        Platform.startup(() -> {
            try {
                runAll();
            } catch (Throwable t) {
                failure[0] = t;
                t.printStackTrace();
            } finally {
                latch.countDown();
            }
        });
        latch.await();
        Platform.exit();

        System.out.println();
        System.out.println("==== 工具链路: " + passed + " 通过 / " + failed + " 失败 ====");
        System.exit(failed > 0 || failure[0] != null ? 1 : 0);
    }

    private static void runAll() {
        MainWindow window = new MainWindow();
        ToolManager tools = window.getToolManager();
        Color foreground = window.getForegroundColor();
        int fg = ColorUtil.toArgb(foreground);

        // 1) 画笔
        Document doc = freshDocument(window);
        tools.select(ToolType.BRUSH);
        press(tools, 20, 20);
        drag(tools, 60, 20);
        release(tools, 80, 20);
        check("画笔留下笔迹", doc.getActiveLayer().getPixel(50, 20) == fg);
        check("画笔不污染远处", doc.getActiveLayer().getPixel(50, 100) == 0xFFFFFFFF);
        check("画笔宽度受控（8px，±10px 外不画）",
                doc.getActiveLayer().getPixel(50, 31) == 0xFFFFFFFF);

        // 2) 橡皮擦
        tools.select(ToolType.ERASER);
        press(tools, 40, 20);
        drag(tools, 60, 20);
        release(tools, 60, 20);
        int erased = doc.getActiveLayer().getPixel(50, 20) >>> 24;
        check("橡皮把像素擦成透明 (alpha=" + erased + ")", erased < 40);

        // 3) 矩形填充
        doc = freshDocument(window);
        setFilled(window, true);
        tools.select(ToolType.RECTANGLE);
        press(tools, 10, 100);
        drag(tools, 40, 120);
        release(tools, 60, 140);
        check("矩形内部被填充", doc.getActiveLayer().getPixel(30, 120) == fg);
        check("矩形外部未被填充", doc.getActiveLayer().getPixel(120, 120) == 0xFFFFFFFF);

        // 4) 椭圆描边
        doc = freshDocument(window);
        setFilled(window, false);
        tools.select(ToolType.ELLIPSE);
        press(tools, 100, 100);
        drag(tools, 140, 130);
        release(tools, 180, 160);
        int edgeAlpha = doc.getActiveLayer().getPixel(140, 100) >>> 24;
        check("椭圆边缘有描边 (alpha=" + edgeAlpha + ")", edgeAlpha > 200);
        check("椭圆中心保持透明", (doc.getActiveLayer().getPixel(140, 130) >>> 24) == 255
                && doc.getActiveLayer().getPixel(140, 130) == 0xFFFFFFFF);

        // 5) 直线
        doc = freshDocument(window);
        tools.select(ToolType.LINE);
        press(tools, 20, 180);
        drag(tools, 100, 180);
        release(tools, 180, 180);
        check("直线已绘制", doc.getActiveLayer().getPixel(100, 180) == fg);

        // 6) 油漆桶
        doc = freshDocument(window);
        window.setForegroundColor(Color.web("#22AA55"));
        int green = ColorUtil.toArgb(Color.web("#22AA55"));
        tools.select(ToolType.FILL);
        press(tools, 100, 100);
        release(tools, 100, 100);
        check("油漆桶填满背景", doc.getActiveLayer().getPixel(5, 5) == green);
        check("油漆桶填满远处", doc.getActiveLayer().getPixel(195, 195) == green);

        // 7) 取色器
        window.setForegroundColor(Color.WHITE);
        tools.select(ToolType.PICKER);
        press(tools, 100, 100);
        release(tools, 100, 100);
        check("取色器取到绿色",
                ColorUtil.toArgb(window.getForegroundColor()) == green);

        // 8) 多边形
        doc = freshDocument(window);
        window.setForegroundColor(Color.web("#FF8800"));
        int orange = ColorUtil.toArgb(Color.web("#FF8800"));
        setFilled(window, true);
        tools.select(ToolType.POLYGON);
        press(tools, 40, 40);
        press(tools, 160, 40);
        press(tools, 100, 160);
        click(tools, 100, 160, 2);
        check("多边形已填充", doc.getActiveLayer().getPixel(100, 80) == orange);

        // 9) 缩放
        double before = window.getCanvasView().getZoom();
        tools.select(ToolType.ZOOM);
        press(tools, 50, 50);
        release(tools, 50, 50);
        check("缩放工具放大", window.getCanvasView().getZoom() > before);

        // 10) 矩形选区
        doc = freshDocument(window);
        drawRedSquare(window, tools);
        tools.select(ToolType.SELECT);
        press(tools, 40, 40);
        drag(tools, 70, 70);
        release(tools, 100, 100);
        var selection = window.getSelectionManager().getSelection();
        check("选区已建立", !selection.isEmpty());
        check("选区尺寸正确 (" + selection.getWidth() + "x" + selection.getHeight() + ")",
                selection.getWidth() == 60 && selection.getHeight() == 60);

        // 11) 移动选区内容
        tools.select(ToolType.MOVE);
        press(tools, 70, 70);
        drag(tools, 95, 95);
        drag(tools, 120, 120);
        release(tools, 120, 120);
        check("内容已浮动", window.getSelectionManager().hasFloating());
        window.getSelectionManager().commitFloating();
        check("移动后新位置有内容", doc.getActiveLayer().getPixel(120, 120) == 0xFFFF0000);
        check("移动后原位被抠空（透明）", (doc.getActiveLayer().getPixel(70, 70) >>> 24) == 0);

        // 12) 复制粘贴
        window.getSelectionManager().selectRect(110, 110, 130, 130);
        boolean copied = window.getSelectionManager().copy();
        boolean pasted = window.getSelectionManager().paste();
        window.getSelectionManager().commitFloating();
        check("复制成功", copied);
        check("粘贴成功", pasted);
        check("粘贴后有内容", (doc.getActiveLayer().getPixel(120, 120) >>> 24) == 255);

        // 13) 删除选区内容
        window.getSelectionManager().selectRect(110, 110, 130, 130);
        window.getSelectionManager().deleteSelection();
        check("删除后选区变透明", (doc.getActiveLayer().getPixel(120, 120) >>> 24) == 0);

        // 14) 旋转
        doc = freshDocument(window);
        drawRedSquare(window, tools);
        window.getSelectionManager().selectRect(40, 40, 100, 100);
        tools.select(ToolType.MOVE);
        press(tools, 70, 70);
        drag(tools, 80, 70);
        release(tools, 80, 70);
        window.getSelectionManager().rotateTo(Math.toRadians(45));
        window.getSelectionManager().commitFloating();
        check("旋转后内容仍在画布内（未崩溃）", doc.getActiveLayer().getPixel(70, 70) != 0);

        // 15) 套索选区
        doc = freshDocument(window);
        tools.select(ToolType.LASSO);
        press(tools, 30, 30);
        drag(tools, 120, 40);
        drag(tools, 110, 120);
        release(tools, 30, 110);
        var lasso = window.getSelectionManager().getSelection();
        check("套索建立了非空选区", !lasso.isEmpty() && lasso.getWidth() > 40);

        // 16) 缩放 + 倾斜后提交
        doc = freshDocument(window);
        drawRedSquare(window, tools);
        window.getSelectionManager().selectRect(40, 40, 100, 100);
        tools.select(ToolType.MOVE);
        press(tools, 70, 70);
        release(tools, 70, 70);
        window.getSelectionManager().scaleTo(1.5, 1.5);
        window.getSelectionManager().skewTo(0.25);
        window.getSelectionManager().commitFloating();
        boolean grew = doc.getActiveLayer().getPixel(96, 96) == 0xFFFF0000
                || doc.getActiveLayer().getPixel(96, 90) == 0xFFFF0000;
        check("缩放后红色区域变大", grew);

        // 17) 图层面板
        doc = freshDocument(window);
        var panel = window.getRightPanel().getLayerPanel();
        check("图层面板初始一行", panel.getRowCount() == 1);
        doc.addLayer("图层 2");
        check("新增图层后面板两行", panel.getRowCount() == 2);
        check("新增图层成为活动图层", doc.getActiveIndex() == 1);
        doc.setActiveIndex(0);
        check("切换活动图层生效", doc.getActiveIndex() == 0);
        doc.getLayer(0).setOpacity(0.5);
        doc.notifyChanged();
        doc.mergeDown(1);
        check("向下合并后只剩一层", doc.getLayerCount() == 1 && panel.getRowCount() == 1);

        doc.addLayer("L2");
        doc.addLayer("L3");
        doc.moveLayer(2, 0);
        check("图层重排生效", "L3".equals(doc.getLayer(0).getName()) && panel.getRowCount() == 3);
        check("重排后面板顺序同步", doc.getActiveIndex() == 0);

        // 18) 撤销 / 重做
        doc = freshDocument(window);
        tools.select(ToolType.BRUSH);
        press(tools, 30, 30);
        drag(tools, 60, 30);
        release(tools, 60, 30);
        int painted = doc.getActiveLayer().getPixel(45, 30);
        check("画笔已落笔", painted != 0xFFFFFFFF);
        check("历史记录有一步", window.getRightPanel().getHistoryPanel().getEntryCount() == 2);
        window.getUndoManager().undo();
        check("撤销后恢复原状", doc.getActiveLayer().getPixel(45, 30) == 0xFFFFFFFF);
        window.getUndoManager().redo();
        check("重做后笔迹恢复", doc.getActiveLayer().getPixel(45, 30) == painted);

        // 19) 图层新建也能撤销
        int layerCountBefore = doc.getLayerCount();
        window.performAction(com.pixelforge.model.AppAction.LAYER_NEW);
        check("新建图层", doc.getLayerCount() == layerCountBefore + 1);
        window.getUndoManager().undo();
        check("撤销新建图层", doc.getLayerCount() == layerCountBefore);
        window.getUndoManager().redo();
        check("重做新建图层", doc.getLayerCount() == layerCountBefore + 1);

        // 20) 历史跳转
        window.getUndoManager().jumpTo(-1);
        check("跳回初始状态", doc.getLayerCount() == layerCountBefore
                && doc.getActiveLayer().getPixel(45, 30) == 0xFFFFFFFF);
    }

    private static void drawRedSquare(MainWindow window, ToolManager tools) {
        window.setForegroundColor(Color.web("#FF0000"));
        window.getRightPanel().filledProperty().set(true);
        tools.select(ToolType.RECTANGLE);
        press(tools, 50, 50);
        drag(tools, 70, 70);
        release(tools, 90, 90);
    }

    // ------------------------------------------------------------------

    private static Document freshDocument(MainWindow window) {
        Document doc = Document.blank(200, 200, true);
        window.loadDocument(doc);
        return doc;
    }

    private static void setFilled(MainWindow window, boolean filled) {
        window.getRightPanel().filledProperty().set(filled);
    }

    private static void press(ToolManager tools, double x, double y) {
        tools.onPressed(x, y, event(MouseEvent.MOUSE_PRESSED, x, y, 1));
    }

    private static void drag(ToolManager tools, double x, double y) {
        tools.onDragged(x, y, event(MouseEvent.MOUSE_DRAGGED, x, y, 1));
    }

    private static void release(ToolManager tools, double x, double y) {
        tools.onReleased(x, y, event(MouseEvent.MOUSE_RELEASED, x, y, 1));
    }

    private static void click(ToolManager tools, double x, double y, int clickCount) {
        tools.onPressed(x, y, event(MouseEvent.MOUSE_PRESSED, x, y, clickCount));
        tools.onReleased(x, y, event(MouseEvent.MOUSE_RELEASED, x, y, clickCount));
    }

    private static MouseEvent event(javafx.event.EventType<MouseEvent> type,
                                   double x, double y, int clickCount) {
        return new MouseEvent(type, x, y, x, y, MouseButton.PRIMARY, clickCount,
                false, false, false, false,
                true, false, false, false, false, true, null);
    }

    private static void check(String name, boolean condition) {
        if (condition) {
            passed++;
            System.out.println("[PASS] " + name);
        } else {
            failed++;
            System.out.println("[FAIL] " + name);
        }
    }
}
