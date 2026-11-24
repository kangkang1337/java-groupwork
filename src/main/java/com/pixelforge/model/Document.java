package com.pixelforge.model;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 文档：一张画布的完整状态，由一组图层自下而上叠成。
 *
 * <p>文档不依赖任何界面类，只维护数据与"变更通知"，界面注册回调后即可刷新。
 * 所有修改入口都会调用 {@link #markDirty()}，用于关窗提示与自动保存。</p>
 */
public class Document {

    /** 当前图层在列表中的索引（0 为最底层）。 */
    public static final int MIN_SIZE = 1;
    public static final int MAX_SIZE = 8000;

    private int width;
    private int height;

    private final List<Layer> layers = new ArrayList<>();
    private final List<Runnable> changeListeners = new CopyOnWriteArrayList<>();

    private int activeIndex;
    private File file;
    private boolean dirty;
    private String title;

    private Document(String title, int width, int height) {
        this.title = title;
        this.width = width;
        this.height = height;
    }

    // ------------------------------------------------------------------
    // 构造
    // ------------------------------------------------------------------

    /** 新建空白文档。 */
    public static Document blank(int width, int height, boolean whiteBackground) {
        checkSize(width, height);
        Document doc = new Document("未命名", width, height);
        Layer base = new Layer("背景", width, height);
        if (whiteBackground) {
            base.fill(0xFFFFFFFF);
        }
        doc.layers.add(base);
        doc.activeIndex = 0;
        return doc;
    }

    /** 由一张位图创建单图层文档。 */
    public static Document fromImage(BufferedImage image, String title) {
        checkSize(image.getWidth(), image.getHeight());
        Document doc = new Document(title, image.getWidth(), image.getHeight());
        Layer layer = new Layer("背景", image.getWidth(), image.getHeight());
        copyFromBufferedImage(image, layer.getPixels());
        doc.layers.add(layer);
        doc.activeIndex = 0;
        return doc;
    }

    /** 创建没有任何图层的空文档（供工程文件恢复时逐层重建）。 */
    public static Document empty(int width, int height) {
        checkSize(width, height);
        return new Document("未命名", width, height);
    }

    private static void copyFromBufferedImage(BufferedImage image, int[] target) {
        int w = image.getWidth();
        int h = image.getHeight();
        int[] row = new int[w];
        for (int y = 0; y < h; y++) {
            image.getRGB(0, y, w, 1, row, 0, w);
            System.arraycopy(row, 0, target, y * w, w);
        }
    }

    private static void checkSize(int width, int height) {
        if (width < MIN_SIZE || height < MIN_SIZE || width > MAX_SIZE || height > MAX_SIZE) {
            throw new IllegalArgumentException(
                    "画布尺寸需在 " + MIN_SIZE + "~" + MAX_SIZE + " 之间，当前: " + width + "x" + height);
        }
    }

    // ------------------------------------------------------------------
    // 图层管理
    // ------------------------------------------------------------------

    public List<Layer> getLayers() {
        return Collections.unmodifiableList(layers);
    }

    public int getLayerCount() {
        return layers.size();
    }

    public Layer getLayer(int index) {
        return layers.get(index);
    }

    public Layer getActiveLayer() {
        if (layers.isEmpty()) {
            return null;
        }
        activeIndex = Math.max(0, Math.min(layers.size() - 1, activeIndex));
        return layers.get(activeIndex);
    }

    public int getActiveIndex() {
        return activeIndex;
    }

    public void setActiveIndex(int index) {
        if (layers.isEmpty()) {
            return;
        }
        int clamped = Math.max(0, Math.min(layers.size() - 1, index));
        if (clamped != activeIndex) {
            activeIndex = clamped;
            notifyChanged();
        }
    }

    /** 在当前图层上方插入一个新图层并选中它。 */
    public Layer addLayer(String name) {
        Layer layer = new Layer(name, width, height);
        int insertAt = Math.min(layers.size(), activeIndex + 1);
        layers.add(insertAt, layer);
        activeIndex = insertAt;
        markDirty();
        notifyChanged();
        return layer;
    }

    public Layer addLayer(Layer layer, int index) {
        int insertAt = Math.max(0, Math.min(layers.size(), index));
        layers.add(insertAt, layer);
        activeIndex = insertAt;
        markDirty();
        notifyChanged();
        return layer;
    }

    /** 删除指定图层；文档至少保留一个图层。 */
    public boolean removeLayer(int index) {
        if (layers.size() <= 1 || index < 0 || index >= layers.size()) {
            return false;
        }
        layers.remove(index);
        activeIndex = Math.max(0, Math.min(layers.size() - 1, index - 1));
        markDirty();
        notifyChanged();
        return true;
    }

    /** 复制图层，副本插在原图层上方。 */
    public Layer duplicateLayer(int index) {
        if (index < 0 || index >= layers.size()) {
            return null;
        }
        Layer copy = layers.get(index).duplicate();
        layers.add(index + 1, copy);
        activeIndex = index + 1;
        markDirty();
        notifyChanged();
        return copy;
    }

    /** 把图层从 from 移到 to（用于面板拖拽排序）。 */
    public void moveLayer(int from, int to) {
        if (from == to || from < 0 || to < 0 || from >= layers.size() || to >= layers.size()) {
            return;
        }
        Layer moving = layers.remove(from);
        layers.add(to, moving);
        activeIndex = to;
        markDirty();
        notifyChanged();
    }

    /**
     * 把 index 图层合并到它下面一层，结果保留在下一层上。
     *
     * @return 是否成功（底层不存在时失败）
     */
    public boolean mergeDown(int index) {
        if (index <= 0 || index >= layers.size()) {
            return false;
        }
        Layer upper = layers.get(index);
        Layer lower = layers.get(index - 1);

        int[] dst = lower.getPixels();
        int[] src = upper.getPixels();
        double alpha = upper.getOpacity();
        BlendMode mode = upper.getBlendMode();

        for (int i = 0; i < dst.length; i++) {
            dst[i] = com.pixelforge.render.Compositor.blendPixel(dst[i], src[i], alpha, mode);
        }
        layers.remove(index);
        activeIndex = index - 1;
        markDirty();
        notifyChanged();
        return true;
    }

    // ------------------------------------------------------------------
    // 变更通知
    // ------------------------------------------------------------------

    public void addChangeListener(Runnable listener) {
        changeListeners.add(listener);
    }

    public void notifyChanged() {
        for (Runnable listener : changeListeners) {
            listener.run();
        }
    }

    public void markDirty() {
        dirty = true;
    }

    // ------------------------------------------------------------------

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public File getFile() {
        return file;
    }

    public void setFile(File file) {
        this.file = file;
        if (file != null) {
            this.title = file.getName();
        }
        notifyChanged();
    }

    public boolean isDirty() {
        return dirty;
    }

    public void clearDirty() {
        dirty = false;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    /** 标题栏显示用，脏数据时带 * 号。 */
    public String getDisplayTitle() {
        return title + (dirty ? " *" : "");
    }

    @Override
    public String toString() {
        return "Document[" + title + " " + width + "x" + height + " layers=" + layers.size() + "]";
    }
}
