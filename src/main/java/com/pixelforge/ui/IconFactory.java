package com.pixelforge.ui;

import com.pixelforge.model.ToolType;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;

/**
 * 矢量图标工厂。
 *
 * <p>工具栏图标全部用 JavaFX {@link GraphicsContext} 现画，不依赖 PNG 资源：
 * 任意尺寸都清晰、可随主题换色，也避免了图标素材的版权问题。
 * 所有图标在 24×24 的虚拟坐标系里绘制，再按目标尺寸整体缩放。</p>
 */
public final class IconFactory {

    /** 图标默认颜色（浅灰）。 */
    public static final Color DEFAULT_COLOR = Color.web("#c9d1d9");

    private static final double BOX = 24.0;

    private IconFactory() {
        throw new AssertionError("工具类不允许实例化");
    }

    /**
     * 绘制某个工具的图标。
     *
     * @param type  工具类型
     * @param size  图标像素尺寸（正方形）
     * @param color 线条 / 填充颜色
     * @return 绘制好的画布节点
     */
    public static Canvas icon(ToolType type, double size, Color color) {
        Canvas canvas = new Canvas(size, size);
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.setStroke(color);
        g.setFill(color);
        g.setLineWidth(1.8);
        g.setLineCap(StrokeLineCap.ROUND);
        g.setLineJoin(StrokeLineJoin.ROUND);
        g.save();
        double scale = size / BOX;
        g.scale(scale, scale);
        draw(g, type);
        g.restore();
        canvas.setMouseTransparent(true);
        return canvas;
    }

    public static Canvas icon(ToolType type, double size) {
        return icon(type, size, DEFAULT_COLOR);
    }

    private static void draw(GraphicsContext g, ToolType type) {
        switch (type) {
            case BRUSH -> {
                g.setLineWidth(2.4);
                g.strokeLine(18.0, 6.0, 11.5, 12.5);
                g.fillPolygon(
                        new double[]{11.5, 4.5, 13.0},
                        new double[]{12.5, 19.5, 16.0}, 3);
            }
            case ERASER -> {
                g.save();
                g.translate(12, 12);
                g.rotate(-35);
                g.strokeRoundRect(-9, -5.5, 18, 11, 3, 3);
                g.strokeLine(0, -5.5, 0, 5.5);
                g.restore();
            }
            case LINE -> {
                g.strokeLine(7, 17, 17, 7);
                g.fillOval(15.2, 4.2, 3.6, 3.6);
                g.fillOval(5.2, 16.2, 3.6, 3.6);
            }
            case RECTANGLE -> g.strokeRect(4.5, 6.5, 15, 11);
            case ELLIPSE -> g.strokeOval(4, 6.5, 16, 11);
            case POLYGON -> g.strokePolygon(
                    new double[]{12, 20.4, 17.2, 6.8, 3.6},
                    new double[]{3.8, 10, 20, 20, 10}, 5);
            case TEXT -> {
                g.strokeLine(6, 6, 18, 6);
                g.strokeLine(12, 6, 12, 18.5);
                g.strokeLine(8.5, 18.5, 15.5, 18.5);
            }
            case FILL -> {
                // 油漆桶：一滴颜料（主体圆 + 尖顶）
                g.fillOval(7.4, 10.4, 9.2, 9.2);
                g.fillPolygon(
                        new double[]{12.0, 7.4, 16.6},
                        new double[]{3.2, 12.0, 12.0}, 3);
            }
            case PICKER -> {
                g.setLineWidth(2.3);
                g.strokeLine(5.5, 18.5, 14.5, 9.5);
                g.fillOval(13.5, 3.5, 6.0, 6.0);
                g.fillPolygon(new double[]{5.5, 3.5, 6.5}, new double[]{18.5, 20.5, 20.5}, 3);
            }
            case SELECT -> {
                g.setLineDashes(2.5, 2.0);
                g.strokeRect(4.5, 5.5, 15, 13);
                g.setLineDashes();
            }
            case LASSO -> {
                g.strokeOval(4.5, 3.5, 13, 11);
                g.strokeLine(17.5, 11.0, 9.0, 20.0);
                g.fillOval(6.5, 17.5, 4.5, 4.5);
            }
            case MOVE -> {
                g.strokeLine(12, 4.5, 12, 19.5);
                g.strokeLine(4.5, 12, 19.5, 12);
                g.fillPolygon(new double[]{12, 9.3, 14.7}, new double[]{3.0, 6.6, 6.6}, 3);
                g.fillPolygon(new double[]{12, 9.3, 14.7}, new double[]{21.0, 17.4, 17.4}, 3);
                g.fillPolygon(new double[]{3.0, 6.6, 6.6}, new double[]{12, 9.3, 14.7}, 3);
                g.fillPolygon(new double[]{21.0, 17.4, 17.4}, new double[]{12, 9.3, 14.7}, 3);
            }
            case HAND -> {
                g.strokeRoundRect(8.0, 10.0, 8.5, 10.0, 4, 4);
                g.strokeLine(8.0, 13.5, 5.5, 8.5);
                g.strokeLine(10.8, 10.0, 10.8, 5.0);
                g.strokeLine(13.2, 10.0, 13.2, 4.2);
                g.strokeLine(15.8, 10.0, 16.6, 5.5);
            }
            case ZOOM -> {
                g.strokeOval(4.5, 4.5, 11, 11);
                g.setLineWidth(2.5);
                g.strokeLine(15.0, 15.0, 20.0, 20.0);
            }
            default -> g.strokeRect(5, 5, 14, 14);
        }
    }
}
