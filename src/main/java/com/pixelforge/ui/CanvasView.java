package com.pixelforge.ui;

import com.pixelforge.model.Document;
import com.pixelforge.render.Compositor;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;

import java.util.function.BiConsumer;

/**
 * 画布视图：负责把 {@link Document} 渲染出来，并处理缩放、平移与坐标换算。
 *
 * <p>节点结构：
 * <pre>
 *   root(StackPane) → ScrollPane → 居中容器 → Group → artboard(StackPane)
 *                                                     ├─ 透明棋格背景
 *                                                     ├─ ImageView（合成结果）
 *                                                     └─ overlay（工具预览 / 选区 / 网格）
 * </pre>
 * 缩放通过 artboard 的 scale 变换实现，再由外层 Group 把变换后的包围盒反映给
 * 滚动条，因此滚动条长度与缩放比例始终一致。</p>
 */
public class CanvasView {

    /** 缩放范围。 */
    public static final double MIN_ZOOM = 0.05;
    public static final double MAX_ZOOM = 32.0;

    private static final int CHECKER_SIZE = 8;
    /** 参考网格间距（像素）。 */
    private static final int GRID_STEP = 50;
    private boolean gridVisible;

    private final StackPane root = new StackPane();
    private final ScrollPane scroll = new ScrollPane();
    private final StackPane viewportContent = new StackPane();
    private final Group artboardGroup = new Group();
    private final StackPane artboard = new StackPane();
    private final Canvas checker = new Canvas(1, 1);
    private final ImageView imageView = new ImageView();
    private final Canvas selectionCanvas = new Canvas(1, 1);
    private final Canvas gridCanvas = new Canvas(1, 1);
    private final Canvas overlay = new Canvas(1, 1);

    private Runnable selectionPainter;

    private final DoubleProperty zoom = new SimpleDoubleProperty(1.0);

    private Document document;
    private WritableImage image;
    private int[] composite;

    private BiConsumer<Double, Double> cursorListener;
    private double panAnchorX;
    private double panAnchorY;
    private double panScrollX;
    private double panScrollY;
    private boolean panning;
    private boolean spacePressed;
    private boolean handMode;
    private com.pixelforge.tool.CanvasInputHandler inputHandler;

    public CanvasView() {
        artboard.getStyleClass().add("artboard");
        artboard.setAlignment(Pos.TOP_LEFT);

        imageView.setSmooth(false);
        imageView.setMouseTransparent(true);
        checker.setMouseTransparent(true);
        selectionCanvas.setMouseTransparent(true);
        gridCanvas.setMouseTransparent(true);
        overlay.setMouseTransparent(true);

        artboard.getChildren().addAll(checker, imageView, selectionCanvas, gridCanvas, overlay);
        artboardGroup.getChildren().add(artboard);

        viewportContent.setPadding(new Insets(40));
        viewportContent.setAlignment(Pos.CENTER);
        viewportContent.getChildren().add(artboardGroup);
        viewportContent.getStyleClass().add("canvas-holder");

        scroll.setContent(viewportContent);
        scroll.setPannable(false);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);
        scroll.getStyleClass().add("canvas-scroll");

        root.getStyleClass().add("canvas-backdrop");
        root.getChildren().add(scroll);

        zoom.addListener((obs, oldValue, newValue) -> applyZoom(newValue.doubleValue()));

        artboard.addEventHandler(MouseEvent.MOUSE_MOVED, this::handleMouseMoved);
        artboard.addEventHandler(MouseEvent.MOUSE_DRAGGED, this::handleMouseMoved);
        artboard.addEventHandler(MouseEvent.MOUSE_EXITED, e -> {
            if (cursorListener != null) {
                cursorListener.accept(Double.NaN, Double.NaN);
            }
        });
        artboard.addEventHandler(MouseEvent.MOUSE_PRESSED, event -> {
            if (inputHandler != null && event.isPrimaryButtonDown()) {
                inputHandler.onPressed(event.getX(), event.getY(), event);
            }
        });
        artboard.addEventHandler(MouseEvent.MOUSE_DRAGGED, event -> {
            if (inputHandler != null && event.isPrimaryButtonDown()) {
                inputHandler.onDragged(event.getX(), event.getY(), event);
            }
        });
        artboard.addEventHandler(MouseEvent.MOUSE_RELEASED, event -> {
            if (inputHandler != null && event.getButton() == MouseButton.PRIMARY) {
                inputHandler.onReleased(event.getX(), event.getY(), event);
            }
        });
        artboard.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
            if (inputHandler != null && event.getClickCount() == 2) {
                inputHandler.onDoubleClick(event.getX(), event.getY(), event);
            }
        });

        installPanning();

        setDocument(null);
    }

    // ------------------------------------------------------------------
    // 文档
    // ------------------------------------------------------------------

    /** 绑定文档；传 null 表示"没有打开的文档"。 */
    public void setDocument(Document newDocument) {
        this.document = newDocument;
        if (newDocument == null) {
            imageView.setImage(null);
            image = null;
            composite = null;
            artboard.setVisible(false);
            return;
        }

        int width = newDocument.getWidth();
        int height = newDocument.getHeight();

        image = new WritableImage(width, height);
        imageView.setImage(image);
        imageView.setFitWidth(width);
        imageView.setFitHeight(height);

        checker.setWidth(width);
        checker.setHeight(height);
        selectionCanvas.setWidth(width);
        selectionCanvas.setHeight(height);
        gridCanvas.setWidth(width);
        gridCanvas.setHeight(height);
        overlay.setWidth(width);
        overlay.setHeight(height);

        artboard.setMinSize(width, height);
        artboard.setPrefSize(width, height);
        artboard.setMaxSize(width, height);
        artboard.setVisible(true);

        drawCheckerboard(width, height);

        composite = new int[width * height];
        newDocument.addChangeListener(this::render);
        render();
    }

    /** 重新合成并刷新画面。 */
    public void render() {
        if (document == null || image == null) {
            return;
        }
        int width = document.getWidth();
        int height = document.getHeight();
        if (composite == null || composite.length != width * height) {
            composite = new int[width * height];
        }
        Compositor.composite(document, composite);
        image.getPixelWriter().setPixels(0, 0, width, height,
                PixelFormat.getIntArgbInstance(), composite, 0, width);
        repaintSelection();
    }

    /** 设置选区层重绘回调（由主窗口注入，用于绘制蚂蚁线与变换控制点）。 */
    public void setSelectionPainter(Runnable painter) {
        this.selectionPainter = painter;
        repaintSelection();
    }

    private void repaintSelection() {
        if (selectionPainter != null) {
            selectionPainter.run();
        }
    }

    private void drawCheckerboard(int width, int height) {
        GraphicsContext g = checker.getGraphicsContext2D();
        g.clearRect(0, 0, width, height);
        g.setFill(Color.web("#FFFFFF"));
        g.fillRect(0, 0, width, height);
        g.setFill(Color.web("#DCDCDC"));
        for (int y = 0; y < height; y += CHECKER_SIZE) {
            for (int x = 0; x < width; x += CHECKER_SIZE) {
                if (((x / CHECKER_SIZE) + (y / CHECKER_SIZE)) % 2 == 0) {
                    g.fillRect(x, y, CHECKER_SIZE, CHECKER_SIZE);
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // 缩放 / 平移
    // ------------------------------------------------------------------

    private void applyZoom(double value) {
        double clamped = clampZoom(value);
        artboard.setScaleX(clamped);
        artboard.setScaleY(clamped);
        repaintSelection();
        redrawGrid();
    }

    public static double clampZoom(double value) {
        return Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, value));
    }

    public void zoomIn() {
        setZoom(zoom.get() * 1.25);
    }

    public void zoomOut() {
        setZoom(zoom.get() / 1.25);
    }

    public void setZoom(double value) {
        zoom.set(clampZoom(value));
    }

    public double getZoom() {
        return zoom.get();
    }

    public DoubleProperty zoomProperty() {
        return zoom;
    }

    /** 让整张画布完整显示在可视区域内。 */
    public void fitToViewport() {
        if (document == null) {
            return;
        }
        // 预留：四周 padding(80) + 画板投影(约 45) + 滚动条(16) 的余量
        double availableWidth = scroll.getViewportBounds().getWidth() - 150;
        double availableHeight = scroll.getViewportBounds().getHeight() - 150;
        if (availableWidth <= 0 || availableHeight <= 0) {
            return;
        }
        double ratio = Math.min(availableWidth / document.getWidth(),
                availableHeight / document.getHeight());
        setZoom(ratio);
    }

    private void installPanning() {
        // 滚轮缩放（图片编辑器的通用习惯）
        root.addEventFilter(ScrollEvent.SCROLL, e -> {
            if (document == null || e.getDeltaY() == 0) {
                return;
            }
            setZoom(zoom.get() * Math.pow(1.0025, e.getDeltaY()));
            e.consume();
        });

        root.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            boolean wantPan = e.getButton() == MouseButton.MIDDLE
                    || ((spacePressed || handMode) && e.getButton() == MouseButton.PRIMARY);
            if (wantPan) {
                panning = true;
                panAnchorX = e.getSceneX();
                panAnchorY = e.getSceneY();
                panScrollX = scroll.getHvalue();
                panScrollY = scroll.getVvalue();
                root.setCursor(javafx.scene.Cursor.CLOSED_HAND);
                e.consume();
            }
        });
        root.addEventFilter(MouseEvent.MOUSE_DRAGGED, e -> {
            if (!panning) {
                return;
            }
            double viewportWidth = Math.max(1, scroll.getViewportBounds().getWidth());
            double viewportHeight = Math.max(1, scroll.getViewportBounds().getHeight());
            double contentWidth = Math.max(1, viewportContent.getBoundsInLocal().getWidth());
            double contentHeight = Math.max(1, viewportContent.getBoundsInLocal().getHeight());

            double dx = e.getSceneX() - panAnchorX;
            double dy = e.getSceneY() - panAnchorY;
            scroll.setHvalue(clamp01(panScrollX - dx / Math.max(1, contentWidth - viewportWidth)));
            scroll.setVvalue(clamp01(panScrollY - dy / Math.max(1, contentHeight - viewportHeight)));
            e.consume();
        });
        root.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> {
            if (panning) {
                panning = false;
                root.setCursor((spacePressed || handMode) ? javafx.scene.Cursor.OPEN_HAND : javafx.scene.Cursor.DEFAULT);
            }
        });
    }

    /** 空格键按下时切换为抓手模式（按住空格拖动平移）。 */
    public void setSpacePressed(boolean pressed) {
        if (spacePressed == pressed) {
            return;
        }
        spacePressed = pressed;
        if (!panning) {
            root.setCursor((pressed || handMode) ? javafx.scene.Cursor.OPEN_HAND : javafx.scene.Cursor.DEFAULT);
        }
    }

    private static double clamp01(double value) {
        return Math.max(0, Math.min(1, value));
    }

    // ------------------------------------------------------------------

    private void handleMouseMoved(MouseEvent event) {
        if (document == null) {
            return;
        }
        double x = event.getX();
        double y = event.getY();
        if (cursorListener != null) {
            if (x < 0 || y < 0 || x >= document.getWidth() || y >= document.getHeight()) {
                cursorListener.accept(Double.NaN, Double.NaN);
            } else {
                cursorListener.accept(x, y);
            }
        }
        if (inputHandler != null) {
            inputHandler.onMoved(x, y, event);
        }
    }

    /** 设置画布输入处理器（工具管理器）。 */
    public void setInputHandler(com.pixelforge.tool.CanvasInputHandler handler) {
        this.inputHandler = handler;
    }

    /** 抓手模式：左键拖动即平移画布。 */
    public void setHandMode(boolean handMode) {
        this.handMode = handMode;
        if (!panning) {
            root.setCursor(handMode ? javafx.scene.Cursor.OPEN_HAND : javafx.scene.Cursor.DEFAULT);
        }
    }

    /** 取合成图像中某个点的 ARGB（取色器用）。 */
    public int getCompositeArgb(int x, int y) {
        if (document == null || composite == null) {
            return 0;
        }
        if (x < 0 || y < 0 || x >= document.getWidth() || y >= document.getHeight()) {
            return 0;
        }
        return composite[y * document.getWidth() + x];
    }

    public void setCursorListener(BiConsumer<Double, Double> listener) {
        this.cursorListener = listener;
    }

    // ------------------------------------------------------------------
    // 网格
    // ------------------------------------------------------------------

    /** 是否显示参考网格。 */
    public void setGridVisible(boolean visible) {
        this.gridVisible = visible;
        redrawGrid();
    }

    public boolean isGridVisible() {
        return gridVisible;
    }

    private void redrawGrid() {
        if (document == null) {
            return;
        }
        int width = document.getWidth();
        int height = document.getHeight();
        GraphicsContext g = gridCanvas.getGraphicsContext2D();
        g.clearRect(0, 0, width, height);
        if (!gridVisible) {
            return;
        }
        double lineWidth = 1.0 / Math.max(0.01, zoom.get());
        g.setLineWidth(lineWidth);
        g.setStroke(Color.web("#000000", 0.22));
        for (int x = GRID_STEP; x < width; x += GRID_STEP) {
            g.strokeLine(x, 0, x, height);
        }
        for (int y = GRID_STEP; y < height; y += GRID_STEP) {
            g.strokeLine(0, y, width, y);
        }
    }

    public Node getRoot() {
        return root;
    }

    /** 覆盖层画布，供工具绘制临时预览。 */
    public Canvas getOverlay() {
        return overlay;
    }

    /** 选区层画布（蚂蚁线与变换控制点）。 */
    public Canvas getSelectionCanvas() {
        return selectionCanvas;
    }

    public ImageView getImageView() {
        return imageView;
    }

    public Document getDocument() {
        return document;
    }

    public Region getArtboard() {
        return artboard;
    }

    /** 触发一次布局，便于截图或计算视口尺寸。 */
    public void refreshViewport() {
        scroll.applyCss();
        scroll.layout();
    }
}
