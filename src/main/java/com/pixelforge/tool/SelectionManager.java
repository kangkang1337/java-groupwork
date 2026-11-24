package com.pixelforge.tool;

import com.pixelforge.model.Document;
import com.pixelforge.model.FloatingContent;
import com.pixelforge.model.Layer;
import com.pixelforge.model.Selection;
import com.pixelforge.render.PixelPainter;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.transform.Affine;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

/**
 * 选区与浮动内容管理器。
 *
 * <p>负责三件事：维护当前选区掩膜、把选区内的像素"抠"成浮动内容以便自由变换、
 * 以及在画布上绘制蚂蚁线、变换控制点与浮动内容预览。</p>
 */
public class SelectionManager {

    /** 控制点类型。 */
    public enum Handle {
        NONE, MOVE, TL, TR, BR, BL, T, R, B, L, ROTATE
    }

    private static final double ROTATE_OFFSET = 26;

    private final ToolContext ctx;

    private Selection selection;
    private FloatingContent floating;
    private WritableImage floatPreview;
    private BufferedImage clipboard;
    private boolean editOpen;

    /** 套索最近一次的顶点，仅用于绘制更贴合的形状轮廓。 */
    private double[] lastPolygonX;
    private double[] lastPolygonY;

    public SelectionManager(ToolContext ctx) {
        this.ctx = ctx;
    }

    // ------------------------------------------------------------------
    // 选区
    // ------------------------------------------------------------------

    private void ensureSelection() {
        Document doc = ctx.getDocument();
        if (doc == null) {
            selection = null;
            return;
        }
        if (selection == null || selection.getDocumentWidth() != doc.getWidth()
                || selection.getDocumentHeight() != doc.getHeight()) {
            selection = new Selection(doc.getWidth(), doc.getHeight());
        }
    }

    public Selection getSelection() {
        return selection;
    }

    public boolean hasSelection() {
        return selection != null && !selection.isEmpty();
    }

    public boolean hasFloating() {
        return floating != null;
    }

    /** 矩形选区。 */
    public void selectRect(double x0, double y0, double x1, double y1) {
        commitFloating();
        ensureSelection();
        if (selection == null) {
            return;
        }
        lastPolygonX = null;
        lastPolygonY = null;
        selection.selectRect(x0, y0, x1, y1);
        ctx.requestRender();
    }

    /** 套索选区。 */
    public void selectPolygon(double[] xs, double[] ys) {
        commitFloating();
        ensureSelection();
        if (selection == null) {
            return;
        }
        lastPolygonX = xs.clone();
        lastPolygonY = ys.clone();
        selection.selectPolygon(xs, ys);
        ctx.requestRender();
    }

    public void selectAll() {
        commitFloating();
        ensureSelection();
        if (selection != null) {
            selection.selectAll();
            lastPolygonX = null;
            lastPolygonY = null;
            ctx.requestRender();
        }
    }

    public void clearSelection() {
        if (floating != null) {
            commitFloating();
        }
        if (selection != null) {
            selection.clear();
        }
        lastPolygonX = null;
        lastPolygonY = null;
        ctx.requestRender();
    }

    // ------------------------------------------------------------------
    // 浮动内容
    // ------------------------------------------------------------------

    /**
     * 把选区内的像素抠成浮动内容。
     *
     * @return 是否成功
     */
    public boolean beginFloating() {
        commitFloating();
        Layer layer = ctx.getActiveLayer();
        ensureSelection();
        if (layer == null || selection == null || selection.isEmpty()) {
            return false;
        }
        beginEditIfNeeded("移动内容");

        int ox = selection.getX();
        int oy = selection.getY();
        int w = selection.getWidth();
        int h = selection.getHeight();
        if (w <= 0 || h <= 0) {
            return false;
        }

        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        int[] pixels = layer.getPixels();
        int[] row = new int[w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int dx = ox + x;
                int dy = oy + y;
                if (selection.contains(dx, dy)) {
                    row[x] = pixels[dy * layer.getWidth() + dx];
                    pixels[dy * layer.getWidth() + dx] = 0;
                } else {
                    row[x] = 0;
                }
            }
            image.setRGB(0, y, w, 1, row, 0, w);
        }

        floating = new FloatingContent(image, ox, oy);
        floatPreview = toFxImage(image);
        ctx.requestRender();
        return true;
    }

    public FloatingContent getFloating() {
        return floating;
    }

    public void moveBy(double dx, double dy) {
        if (floating == null) {
            return;
        }
        floating.moveBy(dx, dy);
        ctx.requestRender();
    }

    public void rotateTo(double radians) {
        if (floating == null) {
            return;
        }
        floating.setRotation(radians);
        ctx.requestRender();
    }

    public void scaleTo(double sx, double sy) {
        if (floating == null) {
            return;
        }
        floating.setScale(sx, sy);
        ctx.requestRender();
    }

    public void skewTo(double skewX) {
        if (floating == null) {
            return;
        }
        floating.setSkewX(skewX);
        ctx.requestRender();
    }

    /** 把浮动内容按当前变换栅格化回图层。 */
    public void commitFloating() {
        if (floating == null) {
            return;
        }
        boolean moved = !floating.isIdentity();
        Layer layer = ctx.getActiveLayer();
        if (layer != null) {
            BufferedImage target = new BufferedImage(layer.getWidth(), layer.getHeight(),
                    BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = target.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(floating.getImage(), floating.transform(), null);
            } finally {
                g.dispose();
            }
            int[] rendered = new int[layer.getWidth() * layer.getHeight()];
            target.getRGB(0, 0, layer.getWidth(), layer.getHeight(),
                    rendered, 0, layer.getWidth());
            int[] dst = layer.getPixels();
            for (int i = 0; i < dst.length; i++) {
                if (rendered[i] != 0) {
                    dst[i] = PixelPainter.blendArgb(dst[i], rendered[i]);
                }
            }
        }
        floating = null;
        floatPreview = null;
        if (moved && selection != null) {
            // 内容已经落地到新位置，原来的选区不再有意义
            selection.clear();
        }
        endEditIfNeeded();
        ctx.requestRender();
    }

    private void beginEditIfNeeded(String actionName) {
        if (!editOpen) {
            ctx.beginEdit(actionName);
            editOpen = true;
        }
    }

    private void endEditIfNeeded() {
        if (editOpen) {
            ctx.endEdit();
            editOpen = false;
        }
    }

    /** 放弃变换，把内容放回原处。 */
    public void cancelFloating() {
        if (floating == null) {
            return;
        }
        floating.resetTransform();
        commitFloating();
    }

    // ------------------------------------------------------------------
    // 剪贴板
    // ------------------------------------------------------------------

    /** 复制选区内容（取自合成图，所见即所得）。 */
    public boolean copy() {
        ensureSelection();
        if (selection == null || selection.isEmpty()) {
            return false;
        }
        Document doc = ctx.getDocument();
        if (doc == null) {
            return false;
        }
        int ox = selection.getX();
        int oy = selection.getY();
        int w = selection.getWidth();
        int h = selection.getHeight();
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        int[] composite = new int[doc.getWidth() * doc.getHeight()];
        com.pixelforge.render.Compositor.composite(doc, composite);
        int[] row = new int[w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int dx = ox + x;
                int dy = oy + y;
                row[x] = selection.contains(dx, dy) ? composite[dy * doc.getWidth() + dx] : 0;
            }
            image.setRGB(0, y, w, 1, row, 0, w);
        }
        clipboard = image;
        ctx.setStatusMessage("已复制 " + w + " × " + h + " 区域");
        return true;
    }

    /** 剪切：复制 + 清空选区。 */
    public boolean cut() {
        if (!copy()) {
            return false;
        }
        clearSelectedPixels();
        ctx.setStatusMessage("已剪切");
        return true;
    }

    /** 清空图层中选区内的像素。 */
    public void clearSelectedPixels() {
        Layer layer = ctx.getActiveLayer();
        ensureSelection();
        if (layer == null || selection == null || selection.isEmpty()) {
            return;
        }
        beginEditIfNeeded("删除内容");
        int[] pixels = layer.getPixels();
        for (int y = selection.getY(); y < selection.getY() + selection.getHeight(); y++) {
            for (int x = selection.getX(); x < selection.getX() + selection.getWidth(); x++) {
                if (selection.contains(x, y)) {
                    pixels[y * layer.getWidth() + x] = 0;
                }
            }
        }
        endEditIfNeeded();
        ctx.requestRender();
    }

    /** 删除选区内容（浮动内容直接丢弃）。 */
    public void deleteSelection() {
        if (floating != null) {
            floating = null;
            floatPreview = null;
            ctx.requestRender();
            return;
        }
        clearSelectedPixels();
    }
    public boolean hasClipboard() {
        return clipboard != null;
    }

    /** 粘贴：把剪贴板内容变成可拖动的浮动内容。 */
    public boolean paste() {
        commitFloating();
        if (clipboard == null) {
            return false;
        }
        Document doc = ctx.getDocument();
        if (doc == null) {
            return false;
        }
        ensureSelection();
        BufferedImage copy = new BufferedImage(clipboard.getWidth(), clipboard.getHeight(),
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = copy.createGraphics();
        g.drawImage(clipboard, 0, 0, null);
        g.dispose();

        int ox = Math.max(0, (doc.getWidth() - copy.getWidth()) / 2);
        int oy = Math.max(0, (doc.getHeight() - copy.getHeight()) / 2);
        beginEditIfNeeded("粘贴");
        floating = new FloatingContent(copy, ox, oy);
        floatPreview = toFxImage(copy);

        selection.clear();
        selection.selectRect(ox, oy, ox + copy.getWidth(), oy + copy.getHeight());
        ctx.requestRender();
        ctx.setStatusMessage("已粘贴，拖动可调整位置，回车确认");
        return true;
    }

    // ------------------------------------------------------------------
    // 控制点命中
    // ------------------------------------------------------------------

    private static final double HANDLE_PICK = 9.0;

    /** 判断光标落在哪个控制点上。 */
    public Handle hitTest(double x, double y, double zoom) {
        if (floating == null) {
            return Handle.NONE;
        }
        double tolerance = Math.max(HANDLE_PICK, HANDLE_PICK / Math.max(0.2, zoom));
        double[][] corners = floating.cornersInDocument();
        Handle[] cornerHandles = {Handle.TL, Handle.TR, Handle.BR, Handle.BL};
        for (int i = 0; i < 4; i++) {
            if (distance(x, y, corners[i][0], corners[i][1]) <= tolerance) {
                return cornerHandles[i];
            }
        }
        double[][] mids = floating.edgeMidpointsInDocument();
        Handle[] edgeHandles = {Handle.T, Handle.R, Handle.B, Handle.L};
        for (int i = 0; i < 4; i++) {
            if (distance(x, y, mids[i][0], mids[i][1]) <= tolerance) {
                return edgeHandles[i];
            }
        }
        double[] rotate = rotateHandlePosition(zoom);
        if (distance(x, y, rotate[0], rotate[1]) <= tolerance + 2) {
            return Handle.ROTATE;
        }
        if (containsPoint(corners, x, y)) {
            return Handle.MOVE;
        }
        return Handle.NONE;
    }

    private double[] rotateHandlePosition(double zoom) {
        double[][] corners = floating.cornersInDocument();
        double[][] mids = floating.edgeMidpointsInDocument();
        double[] center = floating.centerInDocument();
        double mx = mids[0][0];
        double my = mids[0][1];
        double dx = mx - center[0];
        double dy = my - center[1];
        double len = Math.hypot(dx, dy);
        if (len < 1e-6) {
            return new double[]{mx, my - ROTATE_OFFSET};
        }
        double offset = ROTATE_OFFSET / Math.max(0.2, zoom);
        return new double[]{mx + dx / len * offset, my + dy / len * offset};
    }

    /** 点是否落在四个角围成的四边形内。 */
    private static boolean containsPoint(double[][] corners, double x, double y) {
        boolean inside = false;
        for (int i = 0, j = 3; i < 4; j = i++) {
            double xi = corners[i][0];
            double yi = corners[i][1];
            double xj = corners[j][0];
            double yj = corners[j][1];
            boolean intersect = ((yi > y) != (yj > y))
                    && (x < (xj - xi) * (y - yi) / (yj - yi + 1e-12) + xi);
            if (intersect) {
                inside = !inside;
            }
        }
        return inside;
    }

    private static double distance(double x0, double y0, double x1, double y1) {
        return Math.hypot(x0 - x1, y0 - y1);
    }

    // ------------------------------------------------------------------
    // 绘制
    // ------------------------------------------------------------------

    /** 在选区画布上绘制蚂蚁线、浮动内容与控制点。 */
    public void render(GraphicsContext gc, double zoom, double canvasWidth, double canvasHeight) {
        gc.clearRect(0, 0, canvasWidth, canvasHeight);

        if (floating != null && floatPreview != null) {
            AffineTransform t = floating.transform();
            double[] m = new double[6];
            t.getMatrix(m);
            gc.save();
            gc.setTransform(new Affine(m[0], m[1], m[2], m[3], m[4], m[5]));
            gc.drawImage(floatPreview, 0, 0);
            gc.restore();
        }

        if (selection != null && !selection.isEmpty() && floating == null) {
            gc.save();
            gc.setLineDashes(5, 4);
            gc.setLineWidth(1.0 / Math.max(0.2, zoom));
            gc.setStroke(Color.web("#000000", 0.75));
            gc.strokeRect(selection.getX() - 0.5, selection.getY() - 0.5,
                    selection.getWidth(), selection.getHeight());
            gc.setLineDashOffset(4);
            gc.setStroke(Color.web("#FFFFFF", 0.95));
            gc.strokeRect(selection.getX() - 0.5, selection.getY() - 0.5,
                    selection.getWidth(), selection.getHeight());
            gc.setLineDashes();
            gc.restore();
        }

        if (floating != null) {
            drawHandles(gc, zoom);
        }
    }

    private void drawHandles(GraphicsContext gc, double zoom) {
        double size = 7.0 / Math.max(0.2, zoom);
        double half = size / 2.0;
        double[][] corners = floating.cornersInDocument();
        double[][] mids = floating.edgeMidpointsInDocument();
        double[] rotate = rotateHandlePosition(zoom);
        double[] center = floating.centerInDocument();

        gc.save();
        gc.setLineWidth(1.0 / Math.max(0.2, zoom));

        // 外框
        gc.setStroke(Color.web("#2D6CDF", 0.95));
        gc.beginPath();
        gc.moveTo(corners[0][0], corners[0][1]);
        for (int i = 1; i < 4; i++) {
            gc.lineTo(corners[i][0], corners[i][1]);
        }
        gc.closePath();
        gc.stroke();

        // 旋转手柄的连接线
        gc.setStroke(Color.web("#2D6CDF", 0.6));
        gc.strokeLine(mids[0][0], mids[0][1], rotate[0], rotate[1]);

        // 角点 + 边中点
        gc.setFill(Color.web("#FFFFFF"));
        gc.setStroke(Color.web("#2D6CDF"));
        for (double[][] points : new double[][][]{corners, mids}) {
            for (double[] point : points) {
                gc.fillRect(point[0] - half, point[1] - half, size, size);
                gc.strokeRect(point[0] - half, point[1] - half, size, size);
            }
        }

        // 旋转手柄
        gc.fillOval(rotate[0] - half, rotate[1] - half, size, size);
        gc.strokeOval(rotate[0] - half, rotate[1] - half, size, size);

        // 中心点
        gc.setFill(Color.web("#2D6CDF", 0.7));
        gc.fillOval(center[0] - 2, center[1] - 2, 4, 4);
        gc.restore();
    }

    // ------------------------------------------------------------------

    private static WritableImage toFxImage(BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        int[] pixels = new int[w * h];
        image.getRGB(0, 0, w, h, pixels, 0, w);
        WritableImage fx = new WritableImage(w, h);
        fx.getPixelWriter().setPixels(0, 0, w, h, PixelFormat.getIntArgbInstance(), pixels, 0, w);
        return fx;
    }
}
