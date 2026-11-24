package com.pixelforge.model;

/**
 * 选区：一个与文档等大的二值掩膜，支持矩形与任意多边形（套索）。
 *
 * <p>掩膜用 {@code boolean[]} 存，配合一个缓存的外接矩形，既能精确判断
 * 某个像素是否被选中，也能快速算出去背景画面用的包围盒。</p>
 */
public class Selection {

    private final int width;
    private final int height;
    private final boolean[] mask;

    private boolean empty = true;
    private int minX;
    private int minY;
    private int maxX;
    private int maxY;
    private int count;

    public Selection(int width, int height) {
        this.width = width;
        this.height = height;
        this.mask = new boolean[width * height];
    }

    /** 清空选区。 */
    public void clear() {
        java.util.Arrays.fill(mask, false);
        empty = true;
        count = 0;
        minX = minY = maxX = maxY = 0;
    }

    /** 选中一个矩形（坐标自动归一化并裁剪到画布内）。 */
    public void selectRect(double x0, double y0, double x1, double y1) {
        clear();
        int left = (int) Math.floor(Math.min(x0, x1));
        int top = (int) Math.floor(Math.min(y0, y1));
        int right = (int) Math.ceil(Math.max(x0, x1));
        int bottom = (int) Math.ceil(Math.max(y0, y1));
        left = Math.max(0, Math.min(width - 1, left));
        top = Math.max(0, Math.min(height - 1, top));
        right = Math.max(0, Math.min(width, right));
        bottom = Math.max(0, Math.min(height, bottom));

        for (int y = top; y < bottom; y++) {
            int row = y * width;
            for (int x = left; x < right; x++) {
                mask[row + x] = true;
                count++;
            }
        }
        recomputeBounds();
    }

    /** 选中一个多边形区域（套索）。 */
    public void selectPolygon(double[] xs, double[] ys) {
        clear();
        int n = Math.min(xs.length, ys.length);
        if (n < 3) {
            return;
        }
        double minYd = Double.MAX_VALUE;
        double maxYd = -Double.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            minYd = Math.min(minYd, ys[i]);
            maxYd = Math.max(maxYd, ys[i]);
        }
        int top = Math.max(0, (int) Math.floor(minYd));
        int bottom = Math.min(height - 1, (int) Math.ceil(maxYd));

        double[] nodes = new double[n];
        for (int y = top; y <= bottom; y++) {
            double scanY = y + 0.5;
            int nodeCount = 0;
            for (int i = 0, j = n - 1; i < n; j = i++) {
                if ((ys[i] < scanY && ys[j] >= scanY) || (ys[j] < scanY && ys[i] >= scanY)) {
                    double t = (scanY - ys[i]) / (ys[j] - ys[i]);
                    nodes[nodeCount++] = xs[i] + t * (xs[j] - xs[i]);
                }
            }
            java.util.Arrays.sort(nodes, 0, nodeCount);
            for (int i = 0; i + 1 < nodeCount; i += 2) {
                int left = Math.max(0, (int) Math.ceil(nodes[i]));
                int right = Math.min(width - 1, (int) Math.floor(nodes[i + 1]));
                int row = y * width;
                for (int x = left; x <= right; x++) {
                    if (!mask[row + x]) {
                        mask[row + x] = true;
                        count++;
                    }
                }
            }
        }
        recomputeBounds();
    }

    /** 全选。 */
    public void selectAll() {
        java.util.Arrays.fill(mask, true);
        count = width * height;
        empty = false;
        minX = minY = 0;
        maxX = width - 1;
        maxY = height - 1;
    }

    private void recomputeBounds() {
        if (count == 0) {
            empty = true;
            minX = minY = maxX = maxY = 0;
            return;
        }
        empty = false;
        minX = width;
        minY = height;
        maxX = -1;
        maxY = -1;
        for (int y = 0; y < height; y++) {
            int row = y * width;
            for (int x = 0; x < width; x++) {
                if (mask[row + x]) {
                    if (x < minX) {
                        minX = x;
                    }
                    if (x > maxX) {
                        maxX = x;
                    }
                    if (y < minY) {
                        minY = y;
                    }
                    if (y > maxY) {
                        maxY = y;
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------

    public boolean isEmpty() {
        return empty;
    }

    public boolean contains(int x, int y) {
        if (empty || x < 0 || y < 0 || x >= width || y >= height) {
            return false;
        }
        return mask[y * width + x];
    }

    public boolean[] getMask() {
        return mask;
    }

    public int getX() {
        return minX;
    }

    public int getY() {
        return minY;
    }

    /** 包围盒宽度。 */
    public int getWidth() {
        return empty ? 0 : maxX - minX + 1;
    }

    public int getHeight() {
        return empty ? 0 : maxY - minY + 1;
    }

    public int getCount() {
        return count;
    }

    public int getDocumentWidth() {
        return width;
    }

    public int getDocumentHeight() {
        return height;
    }
}
