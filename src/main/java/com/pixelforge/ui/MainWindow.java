package com.pixelforge.ui;

import com.pixelforge.AppInfo;
import com.pixelforge.io.DocumentIo;
import com.pixelforge.model.AppAction;
import com.pixelforge.model.Document;
import com.pixelforge.model.ToolType;
import com.pixelforge.ui.dialogs.Alerts;
import com.pixelforge.ui.dialogs.JpegQualityDialog;
import com.pixelforge.ui.dialogs.NewDocumentDialog;
import com.pixelforge.util.Log;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import javafx.stage.WindowEvent;

import java.io.File;

/**
 * 主窗口：装配菜单栏、工具栏、画布、属性面板与状态栏，并集中分发命令。
 */
public class MainWindow implements com.pixelforge.tool.ToolContext {

    private final BorderPane root = new BorderPane();
    private final ToolPalette toolPalette = new ToolPalette();
    private final RightPanel rightPanel = new RightPanel();
    private final StatusBar statusBar = new StatusBar();
    private final CanvasView canvasView = new CanvasView();
    private final Label documentLabel = new Label();

    private Document document;
    private float jpegQuality = 0.9f;
    private File lastDirectory;
    private javafx.stage.Stage stage;
    private com.pixelforge.tool.ToolManager toolManager;
    private com.pixelforge.tool.SelectionManager selectionManager;
    private com.pixelforge.command.UndoManager undoManager;
    private String currentEdit;
    private int[] editSnapshot;
    private com.pixelforge.model.Layer editLayer;

    public MainWindow() {
        root.getStyleClass().add("app-root");

        VBox top = new VBox(
                MenuBarFactory.create(this::dispatch),
                buildHeaderBar());

        root.setTop(top);
        root.setLeft(toolPalette.getRoot());
        root.setCenter(canvasView.getRoot());
        root.setRight(rightPanel.getRoot());
        root.setBottom(statusBar.getRoot());

        wireEvents();
        selectionManager = new com.pixelforge.tool.SelectionManager(this);
        toolManager = new com.pixelforge.tool.ToolManager(this, selectionManager);
        canvasView.setInputHandler(toolManager);
        canvasView.setSelectionPainter(this::paintSelection);
        loadDocument(Document.blank(1280, 720, true));
    }

    /** 把当前选区 / 浮动内容画到选区层。 */
    private void paintSelection() {
        if (document == null || selectionManager == null) {
            return;
        }
        var canvas = canvasView.getSelectionCanvas();
        if (canvas.getWidth() < 1 || canvas.getHeight() < 1) {
            return;
        }
        selectionManager.render(canvas.getGraphicsContext2D(), canvasView.getZoom(),
                canvas.getWidth(), canvas.getHeight());
    }

    private HBox buildHeaderBar() {
        Label title = new Label("PixelForge");
        title.getStyleClass().add("brand-title");

        Label subtitle = new Label("像素工坊");
        subtitle.getStyleClass().add("brand-sub");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        documentLabel.getStyleClass().add("header-doc");

        HBox bar = new HBox(6, title, subtitle, spacer, documentLabel);
        bar.getStyleClass().add("header-bar");
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private void wireEvents() {
        toolPalette.selectedToolProperty().addListener((obs, oldTool, newTool) -> {
            if (newTool == null) {
                return;
            }
            if (selectionManager != null) {
                selectionManager.commitFloating();
            }
            statusBar.setTool(newTool.displayName());
            if (toolManager != null) {
                if (!toolManager.select(newTool)) {
                    statusBar.setMessage("「" + newTool.displayName() + "」将在后续步骤接入");
                    toolPalette.selectTool(toolManager.currentToolType());
                    return;
                }
            }
            canvasView.setHandMode(newTool == ToolType.HAND);
            rightPanel.setStrokeToolsVisible(
                    newTool == ToolType.LINE || newTool == ToolType.RECTANGLE
                            || newTool == ToolType.ELLIPSE || newTool == ToolType.POLYGON);
        });

        canvasView.zoomProperty().addListener((obs, oldZoom, newZoom) ->
                statusBar.setZoom(newZoom.doubleValue()));

        canvasView.setCursorListener((x, y) -> {
            if (x == null || Double.isNaN(x) || Double.isNaN(y)) {
                statusBar.clearCursorPosition();
            } else {
                statusBar.setCursorPosition(x, y);
            }
        });
    }

    // ------------------------------------------------------------------
    // 文档
    // ------------------------------------------------------------------

    public void loadDocument(Document newDocument) {
        this.document = newDocument;
        canvasView.setDocument(newDocument);
        rightPanel.getLayerPanel().setDocument(newDocument);
        if (newDocument != null) {
            newDocument.addChangeListener(this::onDocumentChanged);
        }
        undoManager = new com.pixelforge.command.UndoManager(newDocument);
        undoManager.setChangeListener(() -> {
            rightPanel.getHistoryPanel().refresh();
            updateUndoMessage();
        });
        rightPanel.getHistoryPanel().setUndoManager(undoManager);
        rightPanel.getLayerPanel().setUndoManager(undoManager);
        rightPanel.getLayerPanel().setActionHandler(this::dispatch);
        onDocumentChanged();
    }

    private void updateUndoMessage() {
        if (undoManager == null) {
            return;
        }
        String undoName = undoManager.peekUndoName();
        statusBar.setMessage(undoName == null ? "没有可撤销的操作" : "可撤销：" + undoName);
    }

    private void onDocumentChanged() {
        if (document == null) {
            documentLabel.setText("未打开文档");
            statusBar.setCanvasSize("—");
            return;
        }
        documentLabel.setText(document.getDisplayTitle() + "  ·  "
                + document.getWidth() + " × " + document.getHeight());
        statusBar.setCanvasSize(document.getWidth() + " × " + document.getHeight() + " px");
        statusBar.setZoom(canvasView.getZoom());
        statusBar.setTool(toolPalette.getSelectedTool().displayName());
        rightPanel.getLayerPanel().onDocumentChanged();
        updateWindowTitle();
    }

    /** 窗口显示后调用：让画布自适应可视区域。 */
    public void fitCanvasOnStartup() {
        canvasView.fitToViewport();
        Log.info("初始缩放: " + Math.round(canvasView.getZoom() * 100) + "%");
    }

    /** 绑定主窗口，用于更新标题与启动自动保存。 */
    public void attachStage(javafx.stage.Stage stage) {
        this.stage = stage;
        updateWindowTitle();
        startAutoSave();
    }

    private void updateWindowTitle() {
        if (stage == null) {
            return;
        }
        String base = AppInfo.NAME + " " + AppInfo.VERSION;
        stage.setTitle(document == null ? base : document.getDisplayTitle() + " — " + base);
    }

    // ------------------------------------------------------------------
    // 自动保存 / 崩溃恢复
    // ------------------------------------------------------------------

    private static File autosaveFile() {
        File dir = Log.getLogDir();
        if (dir == null) {
            dir = new File(System.getProperty("user.dir"));
        }
        return new File(dir, "autosave.pfp");
    }

    private void startAutoSave() {
        javafx.animation.Timeline timer = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(javafx.util.Duration.minutes(2), e -> autoSaveDraft()));
        timer.setCycleCount(javafx.animation.Animation.INDEFINITE);
        timer.play();
    }

    /** 每两分钟把有改动的文档保存成草稿，防止意外退出丢内容。 */
    private void autoSaveDraft() {
        if (document == null || !document.isDirty()) {
            return;
        }
        try {
            File target = autosaveFile();
            DocumentIo.saveProject(document, target);
            statusBar.setMessage("已自动保存草稿 → " + target.getName());
            Log.info("自动保存草稿: " + target.getAbsolutePath());
        } catch (Exception e) {
            Log.warn("自动保存失败: " + e.getMessage());
        }
    }

    private void deleteAutosave() {
        File file = autosaveFile();
        if (file.isFile() && !file.delete()) {
            Log.warn("无法删除自动保存文件: " + file);
        }
    }

    /** 启动时若检测到上次留下的草稿，询问是否恢复。 */
    public void maybeRestoreAutosave() {
        File file = autosaveFile();
        if (!file.isFile() || file.length() == 0) {
            return;
        }
        boolean restore = Alerts.confirm(window(), "发现上次未保存的草稿",
                "检测到自动保存的文件（" + file.getName() + "）。要打开它继续编辑吗？");
        if (!restore) {
            deleteAutosave();
            return;
        }
        try {
            Document recovered = DocumentIo.open(file);
            recovered.setFile(null);
            recovered.setTitle("恢复的草稿");
            recovered.markDirty();
            loadDocument(recovered);
            canvasView.fitToViewport();
            statusBar.setMessage("已恢复自动保存的草稿");
            Log.info("恢复自动保存草稿");
        } catch (Exception e) {
            Alerts.error(window(), "草稿恢复失败", e);
        }
    }

    /** 注册场景级快捷键（空格平移、工具字母键）。 */
    public void installHandlers(javafx.scene.Scene scene) {
        scene.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
            if (scene.getFocusOwner() instanceof javafx.scene.control.TextInputControl) {
                return;
            }
            if (event.getCode() == javafx.scene.input.KeyCode.SPACE) {
                canvasView.setSpacePressed(true);
                event.consume();
                return;
            }
            if (event.getCode() == javafx.scene.input.KeyCode.ENTER) {
                selectionManager.commitFloating();
                statusBar.setMessage("已应用变换");
                event.consume();
                return;
            }
            if (event.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                selectionManager.clearSelection();
                statusBar.setMessage("已取消选择");
                event.consume();
                return;
            }
            if (event.isControlDown() || event.isAltDown() || event.isMetaDown()) {
                return;
            }
            ToolType tool = toolForShortcut(event.getCode());
            if (tool != null) {
                toolPalette.selectTool(tool);
                event.consume();
            }
        });
        scene.addEventFilter(javafx.scene.input.KeyEvent.KEY_RELEASED, event -> {
            if (event.getCode() == javafx.scene.input.KeyCode.SPACE) {
                canvasView.setSpacePressed(false);
            }
        });
    }

    private static ToolType toolForShortcut(javafx.scene.input.KeyCode code) {
        for (ToolType type : ToolType.values()) {
            if (type.shortcut().equals(code.getName())) {
                return type;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // 文件操作
    // ------------------------------------------------------------------

    private void newDocument() {
        if (!canDiscardChanges()) {
            return;
        }
        Document created = NewDocumentDialog.show(window());
        if (created == null) {
            return;
        }
        loadDocument(created);
        canvasView.fitToViewport();
        statusBar.setMessage("已新建 " + created.getWidth() + " × " + created.getHeight() + " 画布");
        Log.info("新建画布 " + created.getWidth() + "x" + created.getHeight());
    }

    private void openDocument() {
        if (!canDiscardChanges()) {
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("打开文件");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("全部支持的格式",
                        "*.pfp", "*.png", "*.jpg", "*.jpeg", "*.bmp", "*.gif"),
                new FileChooser.ExtensionFilter("PixelForge 工程 (*.pfp)", "*.pfp"),
                new FileChooser.ExtensionFilter("图片 (*.png, *.jpg, *.bmp, *.gif)",
                        "*.png", "*.jpg", "*.jpeg", "*.bmp", "*.gif"));
        if (lastDirectory != null && lastDirectory.isDirectory()) {
            chooser.setInitialDirectory(lastDirectory);
        }
        File file = chooser.showOpenDialog(window());
        if (file == null) {
            return;
        }
        try {
            Document loaded = DocumentIo.open(file);
            loadDocument(loaded);
            lastDirectory = file.getParentFile();
            canvasView.fitToViewport();
            statusBar.setMessage("已打开 " + file.getName());
            Log.info("打开文件: " + file.getAbsolutePath());
        } catch (Exception e) {
            Alerts.error(window(), "无法打开「" + file.getName() + "」", e);
        }
    }

    /**
     * 保存文档。
     *
     * @param saveAs 是否强制弹出"另存为"
     * @return 是否保存成功
     */
    private boolean saveDocument(boolean saveAs) {
        if (document == null) {
            return false;
        }
        selectionManager.commitFloating();
        File target = document.getFile();
        if (saveAs || target == null) {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("保存工程");
            chooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("PixelForge 工程 (*.pfp)", "*.pfp"));
            chooser.setInitialFileName(baseName(document.getTitle()) + ".pfp");
            if (lastDirectory != null && lastDirectory.isDirectory()) {
                chooser.setInitialDirectory(lastDirectory);
            }
            target = chooser.showSaveDialog(window());
            if (target == null) {
                return false;
            }
            if (!target.getName().toLowerCase().endsWith(".pfp")) {
                target = new File(target.getParentFile(), target.getName() + ".pfp");
            }
        }
        try {
            DocumentIo.saveProject(document, target);
            document.setFile(target);
            document.clearDirty();
            document.notifyChanged();
            lastDirectory = target.getParentFile();
            deleteAutosave();
            statusBar.setMessage("已保存到 " + target.getName());
            Log.info("保存工程: " + target.getAbsolutePath());
            return true;
        } catch (Exception e) {
            Alerts.error(window(), "保存失败", e);
            return false;
        }
    }

    private void exportPng() {
        if (document == null) {
            return;
        }
        selectionManager.commitFloating();
        File target = chooseExportFile("导出 PNG", "PNG 图片 (*.png)", "*.png", ".png");
        if (target == null) {
            return;
        }
        try {
            DocumentIo.exportPng(document, target);
            statusBar.setMessage("已导出 " + target.getName());
            Log.info("导出 PNG: " + target.getAbsolutePath());
        } catch (Exception e) {
            Alerts.error(window(), "导出 PNG 失败", e);
        }
    }

    private void exportJpg() {
        if (document == null) {
            return;
        }
        selectionManager.commitFloating();
        Float quality = JpegQualityDialog.show(window(), jpegQuality);
        if (quality == null) {
            return;
        }
        jpegQuality = quality;
        File target = chooseExportFile("导出 JPEG", "JPEG 图片 (*.jpg)", "*.jpg", ".jpg");
        if (target == null) {
            return;
        }
        try {
            DocumentIo.exportJpg(document, target, jpegQuality);
            statusBar.setMessage("已导出 " + target.getName());
            Log.info("导出 JPEG: " + target.getAbsolutePath() + " quality=" + jpegQuality);
        } catch (Exception e) {
            Alerts.error(window(), "导出 JPEG 失败", e);
        }
    }

    private File chooseExportFile(String title, String filterName, String filterPattern, String suffix) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(filterName, filterPattern));
        chooser.setInitialFileName(baseName(document.getTitle()) + suffix);
        if (lastDirectory != null && lastDirectory.isDirectory()) {
            chooser.setInitialDirectory(lastDirectory);
        }
        File file = chooser.showSaveDialog(window());
        if (file == null) {
            return null;
        }
        if (!file.getName().toLowerCase().endsWith(suffix)) {
            file = new File(file.getParentFile(), file.getName() + suffix);
        }
        lastDirectory = file.getParentFile();
        return file;
    }

    /**
     * 检查是否可以放弃当前文档（必要时提示保存）。
     *
     * @return true 表示可以继续（已保存或用户选择不保存）
     */
    private boolean canDiscardChanges() {
        if (document == null || !document.isDirty()) {
            return true;
        }
        Alerts.Choice choice = Alerts.askToSave(window(), document.getTitle());
        return switch (choice) {
            case SAVE -> saveDocument(false);
            case DISCARD -> true;
            case CANCEL -> false;
        };
    }

    private static String baseName(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    // ------------------------------------------------------------------
    // 命令分发
    // ------------------------------------------------------------------

    private void dispatch(AppAction action) {
        Log.info("执行命令: " + action.name());
        switch (action) {
            case FILE_NEW -> newDocument();
            case FILE_OPEN -> openDocument();
            case FILE_SAVE -> saveDocument(false);
            case FILE_SAVE_AS -> saveDocument(true);
            case FILE_EXPORT_PNG -> exportPng();
            case FILE_EXPORT_JPG -> exportJpg();
            case FILE_EXIT -> {
                if (canDiscardChanges()) {
                    Platform.exit();
                }
            }

            case EDIT_UNDO -> {
                String name = undoManager == null ? null : undoManager.peekUndoName();
                if (undoManager != null && undoManager.undo()) {
                    statusBar.setMessage("已撤销：" + name);
                } else {
                    statusBar.setMessage("没有可撤销的操作");
                }
            }
            case EDIT_REDO -> {
                String name = undoManager == null ? null : undoManager.peekRedoName();
                if (undoManager != null && undoManager.redo()) {
                    statusBar.setMessage("已重做：" + name);
                } else {
                    statusBar.setMessage("没有可重做的操作");
                }
            }
            case EDIT_COPY -> selectionManager.copy();
            case EDIT_CUT -> selectionManager.cut();
            case EDIT_PASTE -> selectionManager.paste();
            case EDIT_DELETE -> {
                selectionManager.deleteSelection();
                statusBar.setMessage("已删除选区内容");
            }
            case EDIT_SELECT_ALL -> {
                selectionManager.selectAll();
                statusBar.setMessage("已全选");
            }
            case EDIT_DESELECT -> {
                selectionManager.clearSelection();
                statusBar.setMessage("已取消选择");
            }

            case VIEW_ZOOM_IN -> canvasView.zoomIn();            case VIEW_ZOOM_OUT -> canvasView.zoomOut();
            case VIEW_ZOOM_FIT -> canvasView.fitToViewport();
            case VIEW_ZOOM_ACTUAL -> canvasView.setZoom(1.0);
            case VIEW_TOGGLE_GRID -> {
                boolean visible = !canvasView.isGridVisible();
                canvasView.setGridVisible(visible);
                statusBar.setMessage(visible ? "已显示参考网格" : "已隐藏参考网格");
            }

            case LAYER_NEW -> addLayer();
            case LAYER_DUPLICATE -> duplicateLayer();
            case LAYER_DELETE -> deleteLayer();
            case LAYER_MERGE_DOWN -> mergeLayerDown();

            case FILTER_GRAYSCALE -> applyFilter(com.pixelforge.render.filter.Filters.grayscale());
            case FILTER_INVERT -> applyFilter(com.pixelforge.render.filter.Filters.invert());
            case FILTER_BLUR -> applyFilter(com.pixelforge.render.filter.Filters.gaussianBlur(6));
            case FILTER_SHARPEN -> applyFilter(com.pixelforge.render.filter.Filters.sharpen());
            case FILTER_EMBOSS -> applyFilter(com.pixelforge.render.filter.Filters.emboss());
            case FILTER_EDGE -> applyFilter(com.pixelforge.render.filter.Filters.edgeDetect());
            case FILTER_MOSAIC -> applyFilter(com.pixelforge.render.filter.Filters.mosaic(12));
            case FILTER_BRIGHTNESS -> showAdjustmentDialog();

            case HELP_ABOUT -> showAbout();
            case HELP_SHORTCUTS -> showShortcuts();
            default -> notImplemented(action);
        }
    }

    private void addLayer() {
        if (document == null) {
            return;
        }
        var layer = document.addLayer("图层 " + (document.getLayerCount() + 1));
        if (undoManager != null) {
            undoManager.push(new com.pixelforge.command.AddLayerCommand(layer, document.getActiveIndex()));
        }
        statusBar.setMessage("已新建图层");
    }

    private void duplicateLayer() {
        if (document == null) {
            return;
        }
        int index = document.getActiveIndex();
        var copy = document.duplicateLayer(index);
        if (copy != null) {
            if (undoManager != null) {
                undoManager.push(new com.pixelforge.command.AddLayerCommand(copy, index + 1));
            }
            statusBar.setMessage("已复制图层");
        }
    }

    private void deleteLayer() {
        if (document == null) {
            return;
        }
        int index = document.getActiveIndex();
        var layer = document.getLayer(index);
        if (document.removeLayer(index)) {
            if (undoManager != null) {
                undoManager.push(new com.pixelforge.command.RemoveLayerCommand(layer, index));
            }
            statusBar.setMessage("已删除图层");
        } else {
            statusBar.setMessage("至少要保留一个图层");
        }
    }

    private void mergeLayerDown() {
        if (document == null) {
            return;
        }
        int index = document.getActiveIndex();
        if (index <= 0) {
            statusBar.setMessage("最底层无法向下合并");
            return;
        }
        var upper = document.getLayer(index);
        var lower = document.getLayer(index - 1);
        int[] before = lower.getPixels().clone();
        document.mergeDown(index);
        if (undoManager != null) {
            var pixel = com.pixelforge.command.PixelEditCommand.capture(lower, before, "向下合并");
            var remove = new com.pixelforge.command.RemoveLayerCommand(upper, index);
            java.util.List<com.pixelforge.command.Command> parts = new java.util.ArrayList<>();
            if (pixel != null) {
                parts.add(pixel);
            }
            parts.add(remove);
            undoManager.push(new com.pixelforge.command.CompositeCommand("向下合并", parts));
        }
        statusBar.setMessage("已向下合并图层");
    }

    // ------------------------------------------------------------------
    // 滤镜
    // ------------------------------------------------------------------

    /**
     * 把滤镜应用到当前图层；若存在选区则只处理选区内像素。
     */
    private void applyFilter(com.pixelforge.render.filter.ImageFilter filter) {
        var layer = getActiveLayer();
        if (layer == null) {
            statusBar.setMessage("没有可编辑的图层");
            return;
        }
        beginEdit(filter.name());
        int width = layer.getWidth();
        int height = layer.getHeight();
        int[] src = layer.getPixels().clone();
        int[] result = filter.apply(src, width, height);
        int[] dst = layer.getPixels();

        var selection = selectionManager.getSelection();
        boolean hasSelection = selection != null && !selection.isEmpty();
        if (!hasSelection) {
            System.arraycopy(result, 0, dst, 0, dst.length);
        } else {
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if (selection.contains(x, y)) {
                        int index = y * width + x;
                        dst[index] = result[index];
                    }
                }
            }
        }
        endEdit();
        requestRender();
        statusBar.setMessage("已应用滤镜：" + filter.name()
                + (hasSelection ? "（仅选区）" : "（整层）"));
        Log.info("应用滤镜: " + filter.name());
    }

    private void showAdjustmentDialog() {
        if (getActiveLayer() == null) {
            statusBar.setMessage("没有可编辑的图层");
            return;
        }
        var result = com.pixelforge.ui.dialogs.AdjustmentDialog.show(window(), 0, 0);
        if (result == null) {
            return;
        }
        if (Math.abs(result.brightness()) < 0.5 && Math.abs(result.contrast()) < 0.5) {
            statusBar.setMessage("参数没有变化，未应用");
            return;
        }
        applyFilter(com.pixelforge.render.filter.Filters.brightnessContrast(
                result.brightness(), result.contrast()));
    }

    private void notImplemented(AppAction action) {        statusBar.setMessage("「" + action.label() + "」尚未接入，将在后续步骤实现");
        Log.info("命令未实现: " + action.name());
    }

    // ------------------------------------------------------------------
    // 帮助
    // ------------------------------------------------------------------

    private void showAbout() {
        Alerts.info(window(), AppInfo.NAME + "  " + AppInfo.VERSION, """
                一个用 Java + JavaFX 从零实现的位图编辑器。
                面向对象程序设计课程大作业。

                主要特性：多图层、选区变换、矢量工具、卷积滤镜、完整撤销重做。""");
    }

    private void showShortcuts() {
        TextArea area = new TextArea("""
                文件     Ctrl+N 新建        Ctrl+O 打开        Ctrl+S 保存
                         Ctrl+Shift+S 另存为              Ctrl+Q 退出
                编辑     Ctrl+Z 撤销        Ctrl+Y 重做        Ctrl+X 剪切
                         Ctrl+C 复制        Ctrl+V 粘贴        Delete 删除
                         Ctrl+A 全选        Ctrl+D 取消选择
                视图     Ctrl+= 放大        Ctrl+- 缩小        Ctrl+0 适应窗口
                         Ctrl+1 实际大小                     Ctrl+' 网格
                图层     Ctrl+Shift+N 新建图层
                工具     B 画笔  E 橡皮  L 直线  R 矩形  O 椭圆
                         P 多边形  T 文字  G 油漆桶  I 取色器
                         M 选框  V 移动  H 抓手  Z 缩放""");
        area.setEditable(false);
        area.setPrefRowCount(16);

        javafx.scene.control.Dialog<Void> dialog = new javafx.scene.control.Dialog<>();
        dialog.setTitle("快捷键说明");
        dialog.setHeaderText("快捷键一览");
        dialog.getDialogPane().setContent(area);
        dialog.getDialogPane().getButtonTypes().add(javafx.scene.control.ButtonType.CLOSE);
        if (window() != null) {
            dialog.initOwner(window());
        }
        Style.apply(dialog.getDialogPane());
        dialog.showAndWait();
    }

    // ------------------------------------------------------------------

    /** 窗口关闭请求：有未保存改动时先询问。 */
    public void handleCloseRequest(WindowEvent event) {
        if (!canDiscardChanges()) {
            event.consume();
            return;
        }
        if (document != null && !document.isDirty()) {
            deleteAutosave();
        }
        Log.info("窗口关闭");
    }

    public void selectTool(ToolType type) {
        toolPalette.selectTool(type);
    }

    // ------------------------------------------------------------------
    // ToolContext 实现
    // ------------------------------------------------------------------

    @Override
    public com.pixelforge.model.Layer getActiveLayer() {
        return document == null ? null : document.getActiveLayer();
    }

    @Override
    public com.pixelforge.tool.SelectionManager getSelectionManager() {
        return selectionManager;
    }

    @Override
    public javafx.scene.paint.Color getForegroundColor() {
        return rightPanel.foregroundProperty().get();
    }

    @Override
    public void setForegroundColor(javafx.scene.paint.Color color) {
        rightPanel.foregroundProperty().set(color);
    }

    @Override
    public double getBrushSize() {
        return rightPanel.brushSizeProperty().get();
    }

    @Override
    public double getOpacity() {
        return rightPanel.opacityProperty().get();
    }

    @Override
    public boolean isFilled() {
        return rightPanel.filledProperty().get();
    }

    @Override
    public void setStatusMessage(String message) {
        statusBar.setMessage(message);
    }

    @Override
    public void beginEdit(String actionName) {
        currentEdit = actionName;
        editLayer = getActiveLayer();
        editSnapshot = editLayer == null ? null : editLayer.getPixels().clone();
    }

    @Override
    public void endEdit() {
        if (editSnapshot != null && editLayer != null && undoManager != null) {
            com.pixelforge.command.PixelEditCommand command =
                    com.pixelforge.command.PixelEditCommand.capture(editLayer, editSnapshot,
                            currentEdit == null ? "编辑" : currentEdit);
            undoManager.push(command);
        }
        editSnapshot = null;
        editLayer = null;
        currentEdit = null;
    }

    @Override
    public void requestRender() {
        if (document == null) {
            return;
        }
        document.markDirty();
        document.notifyChanged();
    }

    @Override
    public String askText() {
        javafx.scene.control.TextInputDialog dialog = new javafx.scene.control.TextInputDialog("");
        dialog.setTitle("插入文字");
        dialog.setHeaderText("输入要绘制到画布上的文字");
        dialog.setContentText("文字：");
        if (window() != null) {
            dialog.initOwner(window());
        }
        Style.apply(dialog.getDialogPane());
        return dialog.showAndWait().orElse(null);
    }

    private Window window() {
        return root.getScene() == null ? null : root.getScene().getWindow();
    }

    public Parent getRoot() {
        return root;
    }

    public Document getDocument() {
        return document;
    }

    public StatusBar getStatusBar() {
        return statusBar;
    }

    public CanvasView getCanvasView() {
        return canvasView;
    }

    public ToolPalette getToolPalette() {
        return toolPalette;
    }

    /** 工具管理器（供自检脚本驱动工具）。 */
    public com.pixelforge.tool.ToolManager getToolManager() {
        return toolManager;
    }

    /** 撤销管理器（供自检脚本验证历史）。 */
    public com.pixelforge.command.UndoManager getUndoManager() {
        return undoManager;
    }

    /** 以编程方式触发一个菜单命令（自检用）。 */
    public void performAction(AppAction action) {
        dispatch(action);
    }

    public RightPanel getRightPanel() {
        return rightPanel;
    }
}

