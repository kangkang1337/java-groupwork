package com.endurance.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * 带速度剖面的赛道路径。
 *
 * <p>原来车的位置直接用"弧长比例"插值，等于整圈匀速——直道和弯道一样快，
 * 看起来像一颗珠子在轨道上匀滑行，很假。</p>
 *
 * <p>这里先按曲率给每段路算一个"耗时"：弯越急耗时越长（速度越慢）。
 * 把每圈的播放时间按耗时比例分配，于是同一段时间里，车在直道跑得远、
 * 在弯道挪得少——视觉上就是直道快、弯道慢。</p>
 */
public final class TrackPath {

    /** 弯道最多能比直道慢多少倍（越大越夸张）。 */
    private static final double CORNER_SLOWDOWN = 2.6;

    private final List<double[]> points;
    /** 每个点的累积时间（最后一个点是总时间）。 */
    private final double[] cumulative;
    private final double total;

    private TrackPath(List<double[]> points, double[] cumulative, double total) {
        this.points = points;
        this.cumulative = cumulative;
        this.total = total;
    }

    /** 由闭合折线构建速度剖面。 */
    public static TrackPath of(List<double[]> polyline) {
        int n = polyline.size();
        if (n < 4) {
            // 点太少就没法定曲率，退化成匀速
            return new TrackPath(polyline, new double[]{0, 1}, 1);
        }

        // 1) 每个点的弯曲程度：转角 / 相邻段长
        double[] curvature = new double[n];
        for (int i = 0; i < n; i++) {
            double[] prev = polyline.get((i - 1 + n) % n);
            double[] cur = polyline.get(i);
            double[] next = polyline.get((i + 1) % n);
            double ax = cur[0] - prev[0];
            double ay = cur[1] - prev[1];
            double bx = next[0] - cur[0];
            double by = next[1] - cur[1];
            double la = Math.hypot(ax, ay);
            double lb = Math.hypot(bx, by);
            if (la < 1e-9 || lb < 1e-9) {
                curvature[i] = 0;
                continue;
            }
            double cos = (ax * bx + ay * by) / (la * lb);
            double angle = Math.acos(Math.max(-1, Math.min(1, cos)));
            curvature[i] = angle / (0.5 * (la + lb));
        }

        // 2) 平滑一下，免得点位稀疏处曲率忽大忽小
        double[] smoothed = new double[n];
        for (int i = 0; i < n; i++) {
            double sum = 0;
            int count = 0;
            for (int k = -2; k <= 2; k++) {
                sum += curvature[(i + k + n) % n];
                count++;
            }
            smoothed[i] = sum / count;
        }

        // 3) 归一化到 [0,1]（用 95 分位数当参考值，避免个别尖角吃掉所有动态范围）
        double[] sorted = smoothed.clone();
        java.util.Arrays.sort(sorted);
        double reference = Math.max(1e-9, sorted[(int) (sorted.length * 0.95)]);
        double[] normalized = new double[n];
        for (int i = 0; i < n; i++) {
            normalized[i] = Math.min(1.0, smoothed[i] / reference);
        }

        // 4) 累积耗时：段长 × (1 + 弯道系数)，也就是弯道段"更花时间"
        double[] cumulative = new double[n + 1];
        for (int i = 0; i < n; i++) {
            double[] a = polyline.get(i);
            double[] b = polyline.get((i + 1) % n);
            double segment = Math.hypot(b[0] - a[0], b[1] - a[1]);
            double corner = normalized[i];
            double cost = segment * (1 + CORNER_SLOWDOWN * corner);
            cumulative[i + 1] = cumulative[i] + cost;
        }
        return new TrackPath(polyline, cumulative, cumulative[n]);
    }

    /**
     * 按"时间进度"取位置。
     *
     * @param fraction 0~1 的圈内时间进度（会取小数部分）
     */
    public double[] pointAt(double fraction) {
        if (points.size() < 4 || total <= 0) {
            return TrackShapes.pointAt(points, fraction);
        }
        double f = fraction - Math.floor(fraction);
        double target = f * total;

        // 二分查找落在哪一段
        int lo = 0;
        int hi = cumulative.length - 1;
        while (lo < hi - 1) {
            int mid = (lo + hi) >>> 1;
            if (cumulative[mid] <= target) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        int index = Math.min(lo, points.size() - 1);
        double segStart = cumulative[index];
        double segEnd = cumulative[index + 1];
        double span = segEnd - segStart;
        double t = span <= 1e-12 ? 0 : (target - segStart) / span;

        double[] a = points.get(index);
        double[] b = points.get((index + 1) % points.size());
        return new double[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t};
    }

    /** 折线点数，测试用。 */
    public int size() {
        return points.size();
    }

    /** 直道与弯道的速度比（>1 表示弯道更慢），测试用。 */
    public double straightToCornerRatio() {
        if (points.size() < 8) {
            return 1;
        }
        double[] speed = new double[points.size()];
        for (int i = 0; i < points.size(); i++) {
            double segment = cumulative[i + 1] - cumulative[i];
            double[] a = points.get(i);
            double[] b = points.get((i + 1) % points.size());
            double length = Math.hypot(b[0] - a[0], b[1] - a[1]);
            speed[i] = segment <= 1e-12 ? 1 : length / segment;
        }
        double[] sorted = speed.clone();
        java.util.Arrays.sort(sorted);
        double slow = sorted[(int) (sorted.length * 0.2)];
        double fast = sorted[(int) (sorted.length * 0.8)];
        return slow <= 1e-9 ? 1 : fast / slow;
    }

    /** 采样整条路径（调试/截图用）。 */
    public List<double[]> sample(int count) {
        List<double[]> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            out.add(pointAt(i / (double) count));
        }
        return out;
    }
}
