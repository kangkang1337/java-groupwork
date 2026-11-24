package com.pixelforge.ui;

import com.pixelforge.model.BlendMode;
import com.pixelforge.model.Document;
import com.pixelforge.model.Layer;
import javafx.animation.PauseTransition;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Slider;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

/**
 * 图层面板。
 *
 * <p>列表按"最上面的图层排在最前面"显示，因此界面索引与模型索引是倒序关系。
 * 支持：新建 / 复制 / 删除 / 向下合并、拖拽排序、双击重命名、
 * 显示隐藏、不透明度、混合模式，每一项都带实时缩略图。</p>
 */
public class LayerPanel {

    private static final int THUMB_WIDTH = 46;
    private static final int THUMB_HEIGHT = 34;
    private static final double ROW_HEIGHT = 46;

    private final VBox root = new VBox(8);
    private final ListView<Layer> list = new ListView<>();
    private final Slider opacitySlider = new Slider(0, 100, 100);
    private final Label opacityValue = new Label("100%");
    private final ComboBox<BlendMode> blendBox = new ComboBox<>();

    private Document document;
    private boolean updating;
    private int draggingModelIndex = -1;
    private com.pixelforge.command.UndoManager undoManager;
    private java.util.function.Consumer<com.pixelforge.model.AppAction> actionHandler;
    private com.pixelforge.command.LayerPropertyCommand.State pendingState;

    private final List<Layer> visibleOrder = new ArrayList<>();
    private final PauseTransition thumbnailDebounce = new PauseTransition(Duration.millis(220));
    private final PauseTransition propertyCommit = new PauseTransition(Duration.millis(450));

    public LayerPanel() {
        root.setSpacing(8);

        root.getChildren().addAll(buildButtonRow(), buildList(), buildPropertyRow());
        thumbnailDebounce.setOnFinished(e -> refreshThumbnails());
        propertyCommit.setOnFinished(e -> commitPendingProperty());
    }

    public void setUndoManager(com.pixelforge.command.UndoManager manager) {
        this.undoManager = manager;
    }

    /** 图层按钮统一交给主窗口分发（这样新建/删除也能进撤销栈）。 */
    public void setActionHandler(java.util.function.Consumer<com.pixelforge.model.AppAction> handler) {
        this.actionHandler = handler;
    }

    private void fire(com.pixelforge.model.AppAction action) {
        if (actionHandler != null) {
            actionHandler.accept(action);
        }
    }

    private void capturePropertyState(Layer layer) {
        if (pendingState == null && layer != null) {
            pendingState = com.pixelforge.command.LayerPropertyCommand.snapshot(layer);
        }
        propertyCommit.playFromStart();
    }

    private void commitPendingProperty() {
        if (pendingState == null || document == null || undoManager == null) {
            pendingState = null;
            return;
        }
        Layer layer = document.getActiveLayer();
        var command = com.pixelforge.command.LayerPropertyCommand.capture(
                layer, pendingState, "图层属性");
        undoManager.push(command);
        pendingState = null;
    }

    // ------------------------------------------------------------------
    // 构建
    // ------------------------------------------------------------------

    private Node buildButtonRow() {
        Button add = smallButton("新建", () -> fire(com.pixelforge.model.AppAction.LAYER_NEW));
        Button duplicate = smallButton("复制", () -> fire(com.pixelforge.model.AppAction.LAYER_DUPLICATE));
        Button delete = smallButton("删除", () -> fire(com.pixelforge.model.AppAction.LAYER_DELETE));
        Button merge = smallButton("合并", () -> fire(com.pixelforge.model.AppAction.LAYER_MERGE_DOWN));
        HBox row = new HBox(4, add, duplicate, delete, merge);
        row.setAlignment(Pos.CENTER_LEFT);
        for (Node node : row.getChildren()) {
            HBox.setHgrow(node, Priority.ALWAYS);
        }
        return row;
    }

    private Button smallButton(String text, Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().add("panel-button");
        button.setMaxWidth(Double.MAX_VALUE);
        button.setFocusTraversable(false);
        button.setOnAction(e -> action.run());
        return button;
    }

    private Node buildList() {
        list.setPrefHeight(150);
        list.setMinHeight(110);
        list.setFixedCellSize(ROW_HEIGHT);
        list.getStyleClass().add("layer-list");
        list.setCellFactory(view -> new LayerCell());
        list.getSelectionModel().selectedIndexProperty().addListener((obs, oldIndex, newIndex) -> {
            if (updating || document == null || newIndex == null) {
                return;
            }
            int modelIndex = toModelIndex(newIndex.intValue());
            if (modelIndex >= 0) {
                document.setActiveIndex(modelIndex);
            }
        });

        list.setOnDragDetected(event -> {
            Layer selected = list.getSelectionModel().getSelectedItem();
            if (selected == null || document == null) {
                return;
            }
            draggingModelIndex = document.getLayers().indexOf(selected);
            var board = list.startDragAndDrop(javafx.scene.input.TransferMode.MOVE);
            var content = new javafx.scene.input.ClipboardContent();
            content.putString("layer:" + draggingModelIndex);
            board.setContent(content);
            event.consume();
        });

        list.setOnDragOver(event -> {
            if (event.getDragboard().hasString()) {
                event.acceptTransferModes(javafx.scene.input.TransferMode.MOVE);
            }
            event.consume();
        });

        list.setOnDragDropped(event -> {
            if (document == null || draggingModelIndex < 0) {
                event.setDropCompleted(false);
                return;
            }
            int targetView = (int) (event.getY() / ROW_HEIGHT);
            targetView = Math.max(0, Math.min(list.getItems().size() - 1, targetView));
            int targetModel = toModelIndex(targetView);
            if (targetModel >= 0 && targetModel != draggingModelIndex) {
                int from = draggingModelIndex;
                document.moveLayer(from, targetModel);
                if (undoManager != null) {
                    undoManager.push(new com.pixelforge.command.MoveLayerCommand(from, targetModel));
                }
            }
            draggingModelIndex = -1;
            event.setDropCompleted(true);
            event.consume();
        });

        return list;
    }

    private Node buildPropertyRow() {
        opacitySlider.setPrefWidth(110);
        opacityValue.getStyleClass().add("value-label");
        opacitySlider.valueProperty().addListener((obs, oldValue, newValue) -> {
            opacityValue.setText(Math.round(newValue.doubleValue()) + "%");
            if (updating || document == null) {
                return;
            }
            Layer layer = document.getActiveLayer();
            if (layer != null) {
                layer.setOpacity(newValue.doubleValue() / 100.0);
                document.markDirty();
                document.notifyChanged();
                capturePropertyState(layer);
            }
        });

        HBox opacityRow = new HBox(8, new Label("不透明度"), opacitySlider, opacityValue);
        opacityRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(opacitySlider, Priority.ALWAYS);

        blendBox.getItems().setAll(BlendMode.values());
        blendBox.setMaxWidth(Double.MAX_VALUE);
        blendBox.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (updating || document == null || newValue == null) {
                return;
            }
            Layer layer = document.getActiveLayer();
            if (layer != null) {
                layer.setBlendMode(newValue);
                document.markDirty();
                document.notifyChanged();
                capturePropertyState(layer);
            }
        });

        HBox blendRow = new HBox(8, new Label("混合模式"), blendBox);
        blendRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(blendBox, Priority.ALWAYS);

        VBox box = new VBox(6, opacityRow, blendRow);
        box.setPadding(new Insets(4, 0, 0, 0));
        return box;
    }

    // ------------------------------------------------------------------
    // 与文档同步
    // ------------------------------------------------------------------

    public void setDocument(Document doc) {
        this.document = doc;
        refresh();
    }

    /** 结构变化后重建列表。 */
    public void refresh() {
        if (document == null) {
            list.getItems().clear();
            return;
        }
        updating = true;
        try {
            visibleOrder.clear();
            List<Layer> layers = document.getLayers();
            for (int i = layers.size() - 1; i >= 0; i--) {
                visibleOrder.add(layers.get(i));
            }
            list.getItems().setAll(FXCollections.observableArrayList(visibleOrder));
            int viewIndex = toViewIndex(document.getActiveIndex());
            if (viewIndex >= 0) {
                list.getSelectionModel().select(viewIndex);
            }
            Layer active = document.getActiveLayer();
            if (active != null) {
                opacitySlider.setValue(active.getOpacity() * 100);
                opacityValue.setText(Math.round(active.getOpacity() * 100) + "%");
                blendBox.setValue(active.getBlendMode());
            }
        } finally {
            updating = false;
        }
    }

    /** 文档内容变化：先用防抖延迟刷新缩略图，避免每画一笔就重绘。 */
    public void onDocumentChanged() {
        if (document == null) {
            return;
        }
        thumbnailDebounce.playFromStart();
        if (needsStructureRefresh()) {
            refresh();
        } else {
            updating = true;
            try {
                int viewIndex = toViewIndex(document.getActiveIndex());
                if (viewIndex >= 0 && list.getSelectionModel().getSelectedIndex() != viewIndex) {
                    list.getSelectionModel().select(viewIndex);
                }
                Layer active = document.getActiveLayer();
                if (active != null) {
                    opacitySlider.setValue(active.getOpacity() * 100);
                    blendBox.setValue(active.getBlendMode());
                }
            } finally {
                updating = false;
            }
        }
    }

    private boolean needsStructureRefresh() {
        List<Layer> layers = document.getLayers();
        if (layers.size() != visibleOrder.size()) {
            return true;
        }
        for (int i = 0; i < layers.size(); i++) {
            if (layers.get(layers.size() - 1 - i) != visibleOrder.get(i)) {
                return true;
            }
        }
        return false;
    }

    private void refreshThumbnails() {
        updating = true;
        try {
            list.refresh();
        } finally {
            updating = false;
        }
    }

    private int toModelIndex(int viewIndex) {
        if (document == null) {
            return -1;
        }
        int size = document.getLayerCount();
        int model = size - 1 - viewIndex;
        return (model >= 0 && model < size) ? model : -1;
    }

    private int toViewIndex(int modelIndex) {
        if (document == null) {
            return -1;
        }
        int size = document.getLayerCount();
        if (modelIndex < 0 || modelIndex >= size) {
            return -1;
        }
        return size - 1 - modelIndex;
    }

    public Node getRoot() {
        return root;
    }

    /** 列表当前显示的行数（= 图层数），供自检使用。 */
    public int getRowCount() {
        return list.getItems().size();
    }

    // ------------------------------------------------------------------
    // 列表单元
    // ------------------------------------------------------------------

    private class LayerCell extends ListCell<Layer> {

        private final ImageView thumb = new ImageView();
        private final Label nameLabel = new Label();
        private final ToggleButton eye = new ToggleButton();
        private final HBox box;

        LayerCell() {
            thumb.setFitWidth(THUMB_WIDTH);
            thumb.setFitHeight(THUMB_HEIGHT);
            thumb.setPreserveRatio(false);
            thumb.getStyleClass().add("layer-thumb");

            nameLabel.getStyleClass().add("layer-name");
            nameLabel.setMaxWidth(Double.MAX_VALUE);

            eye.getStyleClass().add("eye-button");
            eye.setFocusTraversable(false);
            eye.setOnAction(e -> {
                Layer layer = getItem();
                if (layer == null || updating) {
                    return;
                }
                var before = com.pixelforge.command.LayerPropertyCommand.snapshot(layer);
                layer.setVisible(eye.isSelected());
                eye.setText(eye.isSelected() ? "◉" : "○");
                document.markDirty();
                document.notifyChanged();
                if (undoManager != null) {
                    undoManager.push(com.pixelforge.command.LayerPropertyCommand.capture(
                            layer, before, "图层显隐"));
                }
            });

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            box = new HBox(8, thumb, nameLabel, spacer, eye);
            box.setAlignment(Pos.CENTER_LEFT);
            box.setPadding(new Insets(3, 4, 3, 4));

            setOnMouseClicked(event -> {
                if (event.getClickCount() == 2) {
                    renameLayer();
                }
            });
        }

        private void renameLayer() {
            Layer layer = getItem();
            if (layer == null || document == null) {
                return;
            }
            TextInputDialog dialog = new TextInputDialog(layer.getName());
            dialog.setTitle("重命名图层");
            dialog.setHeaderText("给图层起个名字");
            dialog.setContentText("名称：");
            Style.apply(dialog.getDialogPane());
            dialog.showAndWait().ifPresent(newName -> {
                String trimmed = newName.trim();
                if (!trimmed.isEmpty()) {
                    var before = com.pixelforge.command.LayerPropertyCommand.snapshot(layer);
                    layer.setName(trimmed);
                    document.markDirty();
                    document.notifyChanged();
                    if (undoManager != null) {
                        undoManager.push(com.pixelforge.command.LayerPropertyCommand.capture(
                                layer, before, "重命名图层"));
                    }
                }
            });
        }

        @Override
        protected void updateItem(Layer layer, boolean empty) {
            super.updateItem(layer, empty);
            if (empty || layer == null) {
                setGraphic(null);
                return;
            }
            nameLabel.setText(layer.getName());
            eye.setSelected(layer.isVisible());
            eye.setText(layer.isVisible() ? "◉" : "○");
            thumb.setImage(thumbnailOf(layer));
            setGraphic(box);
        }
    }

    // ------------------------------------------------------------------
    // 缩略图
    // ------------------------------------------------------------------

    private static WritableImage thumbnailOf(Layer layer) {
        int width = THUMB_WIDTH;
        int height = THUMB_HEIGHT;
        int[] pixels = new int[width * height];
        int sw = layer.getWidth();
        int sh = layer.getHeight();
        for (int y = 0; y < height; y++) {
            int sy = Math.min(sh - 1, y * sh / height);
            for (int x = 0; x < width; x++) {
                int sx = Math.min(sw - 1, x * sw / width);
                int argb = layer.getPixel(sx, sy);
                int alpha = (argb >>> 24) & 0xFF;
                // 缩略图叠在深灰底上，透明区域不至于全黑
                int r = ((argb >> 16) & 0xFF) * alpha / 255 + 32 * (255 - alpha) / 255;
                int g = ((argb >> 8) & 0xFF) * alpha / 255 + 36 * (255 - alpha) / 255;
                int b = (argb & 0xFF) * alpha / 255 + 42 * (255 - alpha) / 255;
                pixels[y * width + x] = 0xFF000000 | (r << 16) | (g << 8) | b;
            }
        }
        WritableImage image = new WritableImage(width, height);
        image.getPixelWriter().setPixels(0, 0, width, height,
                PixelFormat.getIntArgbInstance(), pixels, 0, width);
        return image;
    }

    /** 供状态栏等显示用的颜色表格（占位，保持 API 简洁）。 */
    public static Color defaultSelectionColor() {
        return Color.web("#2D6CDF");
    }
}
