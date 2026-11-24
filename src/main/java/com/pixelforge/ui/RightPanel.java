package com.pixelforge.ui;

import com.pixelforge.ui.controls.SectionBox;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.BorderWidths;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

/**
 * 右侧属性面板：颜色、工具选项、图层。
 *
 * <p>所有可调参数都通过 JavaFX 属性对外暴露，画布工具监听这些属性即可，
 * 面板本身不关心谁来消费。</p>
 */
public class RightPanel {

    /** 预设色板。 */
    private static final String[] PRESET_COLORS = {
            "#000000", "#404040", "#808080", "#C0C0C0", "#FFFFFF",
            "#E53935", "#D81B60", "#8E24AA", "#5E35B1", "#3949AB",
            "#1E88E5", "#039BE5", "#00ACC1", "#00897B", "#43A047",
            "#7CB342", "#C0CA33", "#FDD835", "#FFB300", "#FB8C00",
            "#F4511E", "#6D4C41", "#455A64", "#2D6CDF", "#FF6B9D"
    };

    private final ScrollPane root = new ScrollPane();
    private final VBox content = new VBox(14);

    private final ObjectProperty<Color> foreground =
            new SimpleObjectProperty<>(Color.web("#2D6CDF"));
    private final DoubleProperty brushSize = new SimpleDoubleProperty(8);
    private final DoubleProperty opacity = new SimpleDoubleProperty(1.0);
    private final BooleanProperty filled = new SimpleBooleanProperty(false);

    private final ColorPicker colorPicker = new ColorPicker(Color.web("#2D6CDF"));
    private final LayerPanel layerPanel = new LayerPanel();
    private final HistoryPanel historyPanel = new HistoryPanel();
    private final Label hexLabel = new Label();
    private final Label sizeValue = new Label();
    private final Label opacityValue = new Label();
    private HBox fillRow;

    public RightPanel() {
        content.getStyleClass().add("right-panel");
        content.setPadding(new Insets(12, 12, 16, 12));
        content.setFillWidth(true);

        content.getChildren().addAll(
                buildColorSection(),
                buildOptionsSection(),
                buildLayerSection(),
                buildHistorySection());

        root.setContent(content);
        root.setFitToWidth(true);
        root.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        root.getStyleClass().add("right-panel-scroll");
        root.setMinWidth(248);
        root.setPrefWidth(248);
        root.setMaxWidth(280);

        syncColorPicker();
    }

    // ------------------------------------------------------------------
    // 颜色
    // ------------------------------------------------------------------

    private Node buildColorSection() {
        SectionBox section = new SectionBox("颜色");

        colorPicker.getStyleClass().add("color-picker-compact");
        // 只保留颜色方块，隐藏默认文本，避免浅色标签底色破坏深色主题
        colorPicker.setStyle("-fx-color-label-visible: false;");
        colorPicker.setMaxWidth(Double.MAX_VALUE);
        colorPicker.setPrefWidth(96);
        HBox.setHgrow(colorPicker, Priority.ALWAYS);

        hexLabel.getStyleClass().add("hex-label");

        HBox pickerRow = new HBox(8, colorPicker, hexLabel);
        pickerRow.setAlignment(Pos.CENTER_LEFT);

        FlowPane palette = new FlowPane(4, 4);
        for (String hex : PRESET_COLORS) {
            palette.getChildren().add(createSwatch(Color.web(hex)));
        }

        Label paletteLabel = new Label("预设色板");
        paletteLabel.getStyleClass().add("hint-label");

        section.addContent(pickerRow, paletteLabel, palette);
        return section;
    }

    private Node createSwatch(Color color) {
        Region swatch = new Region();
        swatch.setMinSize(24, 24);
        swatch.setPrefSize(24, 24);
        swatch.getStyleClass().add("color-swatch");
        swatch.setBackground(new Background(
                new BackgroundFill(color, new CornerRadii(4), Insets.EMPTY)));
        swatch.setBorder(new Border(new BorderStroke(
                Color.web("#000000", 0.35), BorderStrokeStyle.SOLID,
                new CornerRadii(4), new BorderWidths(1))));
        Tooltip.install(swatch, new Tooltip(toHex(color)));
        swatch.setOnMouseClicked(e -> foreground.set(color));
        return swatch;
    }

    private void syncColorPicker() {
        colorPicker.valueProperty().addListener((obs, oldColor, newColor) -> {
            if (newColor != null) {
                foreground.set(newColor);
            }
        });
        foreground.addListener((obs, oldColor, newColor) -> {
            if (newColor != null) {
                if (!newColor.equals(colorPicker.getValue())) {
                    colorPicker.setValue(newColor);
                }
                hexLabel.setText(toHex(newColor));
            }
        });
        hexLabel.setText(toHex(foreground.get()));
    }

    // ------------------------------------------------------------------
    // 工具选项
    // ------------------------------------------------------------------

    private Node buildOptionsSection() {
        SectionBox section = new SectionBox("工具选项");

        Slider sizeSlider = new Slider(1, 100, brushSize.get());
        sizeSlider.setShowTickMarks(false);
        sizeSlider.setShowTickLabels(false);
        sizeSlider.valueProperty().bindBidirectional(brushSize);
        sizeValue.getStyleClass().add("value-label");
        sizeValue.textProperty().bind(brushSize.asString("%.0f px"));

        HBox sizeRow = new HBox(8, new Label("粗细"), spacer(), sizeValue);
        sizeRow.setAlignment(Pos.CENTER_LEFT);

        Slider opacitySlider = new Slider(0, 100, opacity.get() * 100);
        opacitySlider.valueProperty().addListener((obs, oldValue, newValue) ->
                opacity.set(newValue.doubleValue() / 100.0));
        opacityValue.getStyleClass().add("value-label");
        opacityValue.textProperty().bind(opacity.multiply(100).asString("%.0f%%"));

        HBox opacityRow = new HBox(8, new Label("不透明度"), spacer(), opacityValue);
        opacityRow.setAlignment(Pos.CENTER_LEFT);

        ToggleGroup fillGroup = new ToggleGroup();
        ToggleButton strokeBtn = new ToggleButton("描边");
        ToggleButton fillBtn = new ToggleButton("填充");
        strokeBtn.setToggleGroup(fillGroup);
        fillBtn.setToggleGroup(fillGroup);
        strokeBtn.getStyleClass().add("segment-button");
        fillBtn.getStyleClass().add("segment-button");
        strokeBtn.setSelected(true);
        HBox.setHgrow(strokeBtn, Priority.ALWAYS);
        HBox.setHgrow(fillBtn, Priority.ALWAYS);
        strokeBtn.setMaxWidth(Double.MAX_VALUE);
        fillBtn.setMaxWidth(Double.MAX_VALUE);
        fillBtn.selectedProperty().addListener((obs, oldValue, selected) ->
                filled.set(selected));

        HBox fillRow = new HBox(6, strokeBtn, fillBtn);
        this.fillRow = fillRow;

        section.addContent(sizeRow, sizeSlider, opacityRow, opacitySlider, fillRow);
        return section;
    }

    /**
     * 只有形状类工具需要"描边 / 填充"开关，其它工具时把它隐藏。
     */
    public void setStrokeToolsVisible(boolean visible) {
        if (fillRow != null) {
            fillRow.setVisible(visible);
            fillRow.setManaged(visible);
        }
    }

    // ------------------------------------------------------------------
    // 图层
    // ------------------------------------------------------------------

    private Node buildLayerSection() {
        SectionBox section = new SectionBox("图层");
        section.addContent(layerPanel.getRoot());
        return section;
    }

    /** 图层面板实例。 */
    public LayerPanel getLayerPanel() {
        return layerPanel;
    }

    private Node buildHistorySection() {
        SectionBox section = new SectionBox("历史记录");
        section.addContent(historyPanel.getRoot());
        return section;
    }

    /** 历史面板实例。 */
    public HistoryPanel getHistoryPanel() {
        return historyPanel;
    }

    // ------------------------------------------------------------------

    private static Region spacer() {
        Region region = new Region();
        HBox.setHgrow(region, Priority.ALWAYS);
        return region;
    }

    private static String toHex(Color color) {
        return String.format("#%02X%02X%02X",
                (int) Math.round(color.getRed() * 255),
                (int) Math.round(color.getGreen() * 255),
                (int) Math.round(color.getBlue() * 255));
    }

    public Node getRoot() {
        return root;
    }

    public ObjectProperty<Color> foregroundProperty() {
        return foreground;
    }

    public DoubleProperty brushSizeProperty() {
        return brushSize;
    }

    public DoubleProperty opacityProperty() {
        return opacity;
    }

    public BooleanProperty filledProperty() {
        return filled;
    }
}
