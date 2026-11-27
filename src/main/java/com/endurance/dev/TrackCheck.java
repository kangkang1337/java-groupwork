package com.endurance.dev;

import com.endurance.ui.SvgTracks;

import java.io.File;
import java.util.List;
import java.util.Locale;

/**
 * 校验 data/tracks/*.svg 能不能解析成一条合理的赛道走线。
 *
 * <p>SVG 里往往有好几条 path（背景、文字、装饰），解析器取"数字最多的一条"。
 * 这个工具把每条的规模、外框比例、首尾缺口打出来，方便人工确认挑对了没有。</p>
 */
public final class TrackCheck {

    private TrackCheck() {
    }

    public static void main(String[] args) {
        SvgTracks.clearCache();
        String[] ids = {"imola", "spa", "lemans", "saopaulo", "cota", "fuji", "barcelona", "monza"};
        int ok = 0;
        for (String id : ids) {
            File file = new File(SvgTracks.directory(), id + ".svg");
            if (!file.isFile()) {
                System.out.printf(Locale.ROOT, "%-10s [SKIP] 没有 SVG%n", id);
                continue;
            }
            try {
                List<double[]> points = SvgTracks.polyline(id);
                if (points == null || points.isEmpty()) {
                    System.out.printf(Locale.ROOT, "%-10s [FAIL] 解析失败%n", id);
                    continue;
                }
                double minX = Double.MAX_VALUE;
                double maxX = -Double.MAX_VALUE;
                double minY = Double.MAX_VALUE;
                double maxY = -Double.MAX_VALUE;
                for (double[] p : points) {
                    minX = Math.min(minX, p[0]);
                    maxX = Math.max(maxX, p[0]);
                    minY = Math.min(minY, p[1]);
                    maxY = Math.max(maxY, p[1]);
                }
                double w = maxX - minX;
                double h = maxY - minY;
                double[] first = points.get(0);
                double[] last = points.get(points.size() - 1);
                double gap = Math.hypot(first[0] - last[0], first[1] - last[1]);
                boolean healthy = points.size() >= 40 && w > 0.3 && h > 0.3 && gap < 0.05;
                System.out.printf(Locale.ROOT,
                        "%-10s [%s] 点数 %4d  宽 %.2f  高 %.2f  缺口 %.4f  SVG %.0f KB%n",
                        id, healthy ? "OK" : "!!", points.size(), w, h, gap,
                        file.length() / 1024.0);
                if (healthy) {
                    ok++;
                }
            } catch (RuntimeException e) {
                System.out.printf(Locale.ROOT, "%-10s [FAIL] %s%n", id, e.getMessage());
            }
        }
        System.out.println();
        System.out.println("可用的真实赛道走线: " + ok + " / " + ids.length);
    }
}
