package com.endurance.ui;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从官方 SVG 赛道图里读出真实赛道走线。
 *
 * <p>为什么非要 SVG：贴一张位图是没用的——车的位置要沿路径算坐标，
 * 位图给不出路径。SVG 的 {@code <path d="...">} 里就是赛道中心线，
 * 解析出来既能画（就是官方原图），又能算出每台车在哪。</p>
 *
 * <p>用法：把赛道图放成 {@code data/tracks/<赛道id>.svg}（id 见 tracks.json，
 * 例如 {@code imola.svg}），游戏会自动改用真实走线；没有文件就退回内置形状。</p>
 */
public final class SvgTracks {

    private static final Map<String, Optional<List<double[]>>> CACHE = new HashMap<>();

    // SVG 里单引号双引号都合法，两种都收
    private static final Pattern PATH_D =
            Pattern.compile("<path[^>]*\\bd\\s*=\\s*(?:\"([^\"]+)\"|'([^']+)')",
                    Pattern.CASE_INSENSITIVE);
    private static final Pattern NUMBER =
            Pattern.compile("-?\\d*\\.?\\d+(?:[eE][-+]?\\d+)?");

    private SvgTracks() {
        throw new AssertionError("工具类不允许实例化");
    }

    /** 赛道 SVG 的存放目录。 */
    public static File directory() {
        return new File(System.getProperty("user.dir"), "data/tracks");
    }

    /**
     * 取某条赛道的真实走线；没有 SVG 文件就返回 {@code null}（调用方退回内置形状）。
     */
    public static List<double[]> polyline(String trackId) {
        return CACHE.computeIfAbsent(trackId, id -> {
            File file = new File(directory(), id + ".svg");
            if (!file.isFile()) {
                return Optional.empty();
            }
            try {
                String text = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                List<double[]> points = parse(text);
                if (points.size() < 8) {
                    System.err.println("赛道 SVG 解析出的点太少，忽略: " + file);
                    return Optional.empty();
                }
                return Optional.of(points);
            } catch (IOException | RuntimeException e) {
                System.err.println("读取赛道 SVG 失败（退回内置形状）: " + file + " — " + e.getMessage());
                return Optional.empty();
            }
        }).orElse(null);
    }

    /** 清缓存（自检用）。 */
    public static void clearCache() {
        CACHE.clear();
    }

    /**
     * 解析一段 SVG 文本，返回归一化到 [0.06, 0.94] 的闭合折线。
     *
     * @throws IllegalArgumentException 找不到路径
     */
    public static List<double[]> parse(String svgText) {
        Matcher matcher = PATH_D.matcher(svgText);
        List<double[]> best = null;
        double bestScore = -1;
        while (matcher.find()) {
            String d = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            List<double[]> points;
            try {
                points = flatten(d);
            } catch (RuntimeException e) {
                continue;
            }
            if (points.size() < 30) {
                // 外框矩形、背景填充这类只有几个点，直接排除
                continue;
            }
            // 赛道外圈的特征：点多（曲线被采样得很密）+ 占画面大。
            // 单看面积会挑中背景矩形，单看点数会挑中图例/标尺，所以两者相乘。
            double score = points.size() * Math.sqrt(boundingArea(points));
            if (score > bestScore) {
                bestScore = score;
                best = points;
            }
        }
        if (best == null) {
            throw new IllegalArgumentException("SVG 里没有可用的 <path d=\"...\">");
        }
        return normalize(best, 0.06, 0.94);
    }

    private static int countNumbers(String d) {
        Matcher m = NUMBER.matcher(d);
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }

    // ---------------------------------------------------------------- 路径展开

    /** 把 SVG 路径命令展开成点序列。 */
    private static List<double[]> flatten(String d) {
        List<double[]> out = new ArrayList<>();
        Matcher tokens = Pattern.compile("[MmLlHhVvCcSsQqTtAaZz]|" + NUMBER.pattern()).matcher(d);

        double x = 0;
        double y = 0;
        double startX = 0;
        double startY = 0;
        double lastCtrlX = x;
        double lastCtrlY = y;
        char command = 0;

        List<Double> args = new ArrayList<>();
        while (tokens.find()) {
            String token = tokens.group();
            char c = token.charAt(0);
            if (Character.isLetter(c)) {
                // 遇到新命令先把上一条命令的参数消费掉
                if (command != 0) {
                    double[] state = run(command, args, out, x, y, startX, startY, lastCtrlX, lastCtrlY);
                    x = state[0];
                    y = state[1];
                    startX = state[2];
                    startY = state[3];
                    lastCtrlX = state[4];
                    lastCtrlY = state[5];
                }
                command = c;
                args = new ArrayList<>();
                if (c == 'Z' || c == 'z') {
                    double[] state = run(c, args, out, x, y, startX, startY, lastCtrlX, lastCtrlY);
                    x = state[0];
                    y = state[1];
                    lastCtrlX = x;
                    lastCtrlY = y;
                    command = 0;
                }
            } else {
                args.add(Double.parseDouble(token));
            }
        }
        if (command != 0) {
            double[] state = run(command, args, out, x, y, startX, startY, lastCtrlX, lastCtrlY);
            x = state[0];
            y = state[1];
        }
        return out;
    }

    /**
     * 执行一条命令（会把重复参数组也处理掉，例如 "L 1,2 3,4"）。
     *
     * @return {x, y, startX, startY, lastCtrlX, lastCtrlY}
     */
    private static double[] run(char command, List<Double> args, List<double[]> out,
                                double x, double y, double startX, double startY,
                                double lastCtrlX, double lastCtrlY) {
        int i = 0;
        switch (command) {
            case 'M', 'm' -> {
                while (i + 1 < args.size()) {
                    double px = args.get(i++);
                    double py = args.get(i++);
                    if (command == 'm') {
                        x += px;
                        y += py;
                    } else {
                        x = px;
                        y = py;
                    }
                    if (i == 2) {
                        startX = x;
                        startY = y;
                    }
                    out.add(new double[]{x, y});
                }
                lastCtrlX = x;
                lastCtrlY = y;
            }
            case 'L', 'l' -> {
                while (i + 1 < args.size()) {
                    double px = args.get(i++);
                    double py = args.get(i++);
                    x = command == 'l' ? x + px : px;
                    y = command == 'l' ? y + py : py;
                    out.add(new double[]{x, y});
                }
                lastCtrlX = x;
                lastCtrlY = y;
            }
            case 'H', 'h' -> {
                while (i < args.size()) {
                    double px = args.get(i++);
                    x = command == 'h' ? x + px : px;
                    out.add(new double[]{x, y});
                }
                lastCtrlX = x;
                lastCtrlY = y;
            }
            case 'V', 'v' -> {
                while (i < args.size()) {
                    double py = args.get(i++);
                    y = command == 'v' ? y + py : py;
                    out.add(new double[]{x, y});
                }
                lastCtrlX = x;
                lastCtrlY = y;
            }
            case 'C', 'c' -> {
                while (i + 5 < args.size()) {
                    double c1x = args.get(i++);
                    double c1y = args.get(i++);
                    double c2x = args.get(i++);
                    double c2y = args.get(i++);
                    double px = args.get(i++);
                    double py = args.get(i++);
                    if (command == 'c') {
                        c1x += x;
                        c1y += y;
                        c2x += x;
                        c2y += y;
                        px += x;
                        py += y;
                    }
                    cubic(out, x, y, c1x, c1y, c2x, c2y, px, py);
                    lastCtrlX = c2x;
                    lastCtrlY = c2y;
                    x = px;
                    y = py;
                }
            }
            case 'S', 's' -> {
                while (i + 3 < args.size()) {
                    double c2x = args.get(i++);
                    double c2y = args.get(i++);
                    double px = args.get(i++);
                    double py = args.get(i++);
                    if (command == 's') {
                        c2x += x;
                        c2y += y;
                        px += x;
                        py += y;
                    }
                    double c1x = 2 * x - lastCtrlX;
                    double c1y = 2 * y - lastCtrlY;
                    cubic(out, x, y, c1x, c1y, c2x, c2y, px, py);
                    lastCtrlX = c2x;
                    lastCtrlY = c2y;
                    x = px;
                    y = py;
                }
            }
            case 'Q', 'q' -> {
                while (i + 3 < args.size()) {
                    double cx = args.get(i++);
                    double cy = args.get(i++);
                    double px = args.get(i++);
                    double py = args.get(i++);
                    if (command == 'q') {
                        cx += x;
                        cy += y;
                        px += x;
                        py += y;
                    }
                    quadratic(out, x, y, cx, cy, px, py);
                    lastCtrlX = cx;
                    lastCtrlY = cy;
                    x = px;
                    y = py;
                }
            }
            case 'T', 't' -> {
                while (i + 1 < args.size()) {
                    double px = args.get(i++);
                    double py = args.get(i++);
                    if (command == 't') {
                        px += x;
                        py += y;
                    }
                    double cx = 2 * x - lastCtrlX;
                    double cy = 2 * y - lastCtrlY;
                    quadratic(out, x, y, cx, cy, px, py);
                    lastCtrlX = cx;
                    lastCtrlY = cy;
                    x = px;
                    y = py;
                }
            }
            case 'A', 'a' -> {
                while (i + 6 < args.size()) {
                    double rx = args.get(i++);
                    double ry = args.get(i++);
                    double rotation = args.get(i++);
                    boolean largeArc = args.get(i++) != 0;
                    boolean sweep = args.get(i++) != 0;
                    double px = args.get(i++);
                    double py = args.get(i++);
                    if (command == 'a') {
                        px += x;
                        py += y;
                    }
                    arc(out, x, y, rx, ry, rotation, largeArc, sweep, px, py);
                    x = px;
                    y = py;
                    lastCtrlX = x;
                    lastCtrlY = y;
                }
            }
            case 'Z', 'z' -> {
                if (!out.isEmpty()) {
                    double[] first = out.get(0);
                    if (Math.abs(first[0] - x) > 1e-6 || Math.abs(first[1] - y) > 1e-6) {
                        out.add(new double[]{first[0], first[1]});
                    }
                }
                x = startX;
                y = startY;
                lastCtrlX = x;
                lastCtrlY = y;
            }
            default -> {
                // 未知命令：忽略
            }
        }
        return new double[]{x, y, startX, startY, lastCtrlX, lastCtrlY};
    }

    private static void cubic(List<double[]> out, double x0, double y0,
                              double c1x, double c1y, double c2x, double c2y,
                              double x1, double y1) {
        int steps = 14;
        for (int s = 1; s <= steps; s++) {
            double t = s / (double) steps;
            double u = 1 - t;
            double x = u * u * u * x0 + 3 * u * u * t * c1x + 3 * u * t * t * c2x + t * t * t * x1;
            double y = u * u * u * y0 + 3 * u * u * t * c1y + 3 * u * t * t * c2y + t * t * t * y1;
            out.add(new double[]{x, y});
        }
    }

    private static void quadratic(List<double[]> out, double x0, double y0,
                                  double cx, double cy, double x1, double y1) {
        int steps = 10;
        for (int s = 1; s <= steps; s++) {
            double t = s / (double) steps;
            double u = 1 - t;
            double x = u * u * x0 + 2 * u * t * cx + t * t * x1;
            double y = u * u * y0 + 2 * u * t * cy + t * t * y1;
            out.add(new double[]{x, y});
        }
    }

    /** SVG 椭圆弧：转成中心参数化再采样。 */
    private static void arc(List<double[]> out, double x0, double y0,
                            double rx, double ry, double rotation,
                            boolean largeArc, boolean sweep, double x1, double y1) {
        if (rx == 0 || ry == 0) {
            out.add(new double[]{x1, y1});
            return;
        }
        double phi = Math.toRadians(rotation);
        double cosPhi = Math.cos(phi);
        double sinPhi = Math.sin(phi);

        double dx2 = (x0 - x1) / 2;
        double dy2 = (y0 - y1) / 2;
        double x1p = cosPhi * dx2 + sinPhi * dy2;
        double y1p = -sinPhi * dx2 + cosPhi * dy2;

        double lambda = (x1p * x1p) / (rx * rx) + (y1p * y1p) / (ry * ry);
        if (lambda > 1) {
            double scale = Math.sqrt(lambda);
            rx *= scale;
            ry *= scale;
        }

        double sign = largeArc == sweep ? -1 : 1;
        double num = rx * rx * ry * ry - rx * rx * y1p * y1p - ry * ry * x1p * x1p;
        double den = rx * rx * y1p * y1p + ry * ry * x1p * x1p;
        double coef = den == 0 ? 0 : sign * Math.sqrt(Math.max(0, num / den));

        double cxp = coef * rx * y1p / ry;
        double cyp = -coef * ry * x1p / rx;
        double cx = cosPhi * cxp - sinPhi * cyp + (x0 + x1) / 2;
        double cy = sinPhi * cxp + cosPhi * cyp + (y0 + y1) / 2;

        double theta1 = angle(1, 0, (x1p - cxp) / rx, (y1p - cyp) / ry);
        double delta = angle((x1p - cxp) / rx, (y1p - cyp) / ry,
                (-x1p - cxp) / rx, (-y1p - cyp) / ry);
        if (!sweep && delta > 0) {
            delta -= 2 * Math.PI;
        } else if (sweep && delta < 0) {
            delta += 2 * Math.PI;
        }

        int steps = Math.max(6, (int) (Math.abs(delta) / 0.25));
        for (int s = 1; s <= steps; s++) {
            double theta = theta1 + delta * s / steps;
            double px = rx * Math.cos(theta);
            double py = ry * Math.sin(theta);
            out.add(new double[]{
                    cosPhi * px - sinPhi * py + cx,
                    sinPhi * px + cosPhi * py + cy});
        }
    }

    private static double angle(double ux, double uy, double vx, double vy) {
        double dot = ux * vx + uy * vy;
        double len = Math.sqrt(ux * ux + uy * uy) * Math.sqrt(vx * vx + vy * vy);
        if (len == 0) {
            return 0;
        }
        double a = Math.acos(Math.max(-1, Math.min(1, dot / len)));
        return ux * vy - uy * vx < 0 ? -a : a;
    }

    // ---------------------------------------------------------------- 归一化

    /** 等比缩放到 [lo, hi] 的正方形区域内并居中。 */
    private static List<double[]> normalize(List<double[]> points, double lo, double hi) {
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
        double width = Math.max(1e-9, maxX - minX);
        double height = Math.max(1e-9, maxY - minY);
        double scale = Math.min((hi - lo) / width, (hi - lo) / height);
        double offsetX = lo + ((hi - lo) - width * scale) / 2;
        double offsetY = lo + ((hi - lo) - height * scale) / 2;

        List<double[]> result = new ArrayList<>(points.size());
        for (double[] p : points) {
            result.add(new double[]{
                    offsetX + (p[0] - minX) * scale,
                    offsetY + (p[1] - minY) * scale});
        }
        return result;
    }
/** 折线的外框面积，用来判断哪条 path 是赛道外圈。 */
    private static double boundingArea(List<double[]> points) {
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
        return (maxX - minX) * (maxY - minY);
    }
}