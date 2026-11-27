package com.endurance.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * 赛道形状。
 *
 * <p>用一组控制点描述赛道的走向，再用 Catmull-Rom 样条插值成平滑的闭合曲线。
 * 控制点都在 0~1 的归一化坐标里，渲染时按画布尺寸缩放，
 * 因此窗口怎么拉，赛道都不会变形。</p>
 */
public final class TrackShapes {

    private TrackShapes() {
        throw new AssertionError("工具类不允许实例化");
    }

    /** 按赛道 id 取控制点；未知 id 使用默认形状。 */
    public static double[][] controlPoints(String trackId) {
        return switch (trackId == null ? "" : trackId) {
            case "imola" -> new double[][]{
                    {0.15, 0.55}, {0.12, 0.30}, {0.22, 0.18}, {0.34, 0.22},
                    {0.40, 0.15}, {0.55, 0.12}, {0.65, 0.20}, {0.72, 0.15},
                    {0.85, 0.22}, {0.90, 0.42}, {0.82, 0.60}, {0.70, 0.70},
                    {0.60, 0.66}, {0.52, 0.78}, {0.38, 0.86}, {0.22, 0.78}
            };
            case "spa" -> new double[][]{
                    {0.10, 0.75}, {0.14, 0.62}, {0.10, 0.45}, {0.18, 0.32},
                    {0.30, 0.28}, {0.42, 0.40}, {0.50, 0.30}, {0.62, 0.18},
                    {0.78, 0.14}, {0.88, 0.26}, {0.86, 0.44}, {0.74, 0.56},
                    {0.66, 0.68}, {0.54, 0.80}, {0.36, 0.86}, {0.20, 0.84}
            };
            case "lemans" -> new double[][]{
                    {0.06, 0.52}, {0.10, 0.42}, {0.22, 0.40}, {0.34, 0.44},
                    {0.46, 0.40}, {0.58, 0.44}, {0.70, 0.38}, {0.82, 0.30},
                    {0.92, 0.34}, {0.95, 0.46}, {0.90, 0.58}, {0.80, 0.62},
                    {0.70, 0.58}, {0.58, 0.62}, {0.44, 0.58}, {0.32, 0.62},
                    {0.20, 0.58}, {0.10, 0.62}
            };
            case "saopaulo" -> new double[][]{
                    {0.14, 0.62}, {0.10, 0.44}, {0.18, 0.30}, {0.32, 0.26},
                    {0.46, 0.30}, {0.58, 0.26}, {0.72, 0.30}, {0.84, 0.40},
                    {0.86, 0.56}, {0.76, 0.68}, {0.62, 0.72}, {0.48, 0.68},
                    {0.34, 0.72}, {0.22, 0.70}
            };
            case "cota" -> new double[][]{
                    {0.10, 0.58}, {0.14, 0.40}, {0.24, 0.34}, {0.30, 0.20},
                    {0.40, 0.26}, {0.46, 0.14}, {0.58, 0.18}, {0.64, 0.32},
                    {0.76, 0.28}, {0.88, 0.38}, {0.86, 0.54}, {0.74, 0.60},
                    {0.62, 0.56}, {0.50, 0.68}, {0.36, 0.66}, {0.24, 0.74},
                    {0.14, 0.72}
            };
            case "barcelona" -> new double[][]{
                    {0.12, 0.56}, {0.12, 0.36}, {0.24, 0.22}, {0.40, 0.18},
                    {0.56, 0.22}, {0.70, 0.18}, {0.84, 0.28}, {0.88, 0.46},
                    {0.80, 0.60}, {0.68, 0.62}, {0.58, 0.72}, {0.44, 0.80},
                    {0.28, 0.76}, {0.16, 0.68}
            };
            case "monza" -> new double[][]{
                    {0.08, 0.40}, {0.14, 0.26}, {0.30, 0.22}, {0.40, 0.28},
                    {0.52, 0.22}, {0.66, 0.26}, {0.80, 0.22}, {0.92, 0.34},
                    {0.94, 0.52}, {0.86, 0.66}, {0.72, 0.72}, {0.58, 0.68},
                    {0.46, 0.74}, {0.32, 0.70}, {0.18, 0.74}, {0.10, 0.62}
            };            default -> new double[][]{
                    {0.12, 0.56}, {0.10, 0.30}, {0.24, 0.14}, {0.44, 0.12},
                    {0.54, 0.26}, {0.64, 0.20}, {0.80, 0.14}, {0.90, 0.30},
                    {0.86, 0.50}, {0.70, 0.56}, {0.58, 0.64}, {0.66, 0.78},
                    {0.54, 0.88}, {0.34, 0.86}, {0.22, 0.72}
            };
        };
    }

    /**
     * 采样成闭合折线。
     *
     * <p>如果 {@code data/tracks/<id>.svg} 存在，直接用官方赛道图的真实走线；
     * 否则退回内置的手工控制点（Catmull-Rom 平滑）。</p>
     */
    public static List<double[]> sample(String trackId, int samplesPerSegment) {
        List<double[]> real = SvgTracks.polyline(trackId);
        if (real != null && real.size() >= 8) {
            return real;
        }
        double[][] points = controlPoints(trackId);
        int n = points.length;
        List<double[]> result = new ArrayList<>();
        int steps = Math.max(4, samplesPerSegment);
        for (int i = 0; i < n; i++) {
            double[] p0 = points[(i - 1 + n) % n];
            double[] p1 = points[i];
            double[] p2 = points[(i + 1) % n];
            double[] p3 = points[(i + 2) % n];
            for (int s = 0; s < steps; s++) {
                double t = s / (double) steps;
                result.add(catmullRom(p0, p1, p2, p3, t));
            }
        }
        return result;
    }

    private static double[] catmullRom(double[] p0, double[] p1, double[] p2, double[] p3, double t) {
        double t2 = t * t;
        double t3 = t2 * t;
        double x = 0.5 * ((2 * p1[0])
                + (-p0[0] + p2[0]) * t
                + (2 * p0[0] - 5 * p1[0] + 4 * p2[0] - p3[0]) * t2
                + (-p0[0] + 3 * p1[0] - 3 * p2[0] + p3[0]) * t3);
        double y = 0.5 * ((2 * p1[1])
                + (-p0[1] + p2[1]) * t
                + (2 * p0[1] - 5 * p1[1] + 4 * p2[1] - p3[1]) * t2
                + (-p0[1] + 3 * p1[1] - 3 * p2[1] + p3[1]) * t3);
        return new double[]{x, y};
    }

    /**
     * 取曲线上某个比例位置的点。
     *
     * @param polyline 采样后的折线
     * @param fraction 0~1
     */
    public static double[] pointAt(List<double[]> polyline, double fraction) {
        if (polyline.isEmpty()) {
            return new double[]{0.5, 0.5};
        }
        double f = fraction - Math.floor(fraction);
        double pos = f * polyline.size();
        int index = (int) Math.floor(pos) % polyline.size();
        int next = (index + 1) % polyline.size();
        double t = pos - Math.floor(pos);
        double[] a = polyline.get(index);
        double[] b = polyline.get(next);
        return new double[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t};
    }
}
