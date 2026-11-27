package com.endurance.analysis;

import com.endurance.model.Car;
import com.endurance.sim.LapResult;
import com.endurance.sim.PitRecord;
import com.endurance.sim.RaceResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 一场比赛的完整数据表：每一圈、每一台车发生了什么。
 *
 * <p>这是"我和我的对手这一场到底干了什么"的原始账本：
 * 圈速、轮胎、磨损、油量、名次、差距、这一圈是不是进站了。
 * 可以导出成 CSV 用 Excel 打开，也可以直接在界面里翻。</p>
 */
public final class RaceLog {

    private RaceLog() {
        throw new AssertionError("工具类不允许实例化");
    }

    /**
     * 数据表的一行。
     *
     * @param lap          第几圈
     * @param position     这一圈结束时的名次
     * @param carNumber    车号
     * @param driver       车手
     * @param team         车队
     * @param lapTime      本圈用时（秒）
     * @param tire         本圈用的轮胎
     * @param wear         圈末磨损（0~1）
     * @param fuel         圈末剩余油量
     * @param gapToLeader  落后领先者多少秒
     * @param stint        第几段胎
     * @param pit          这一圈是否进站
     * @param safetyCar    是否安全车期间
     * @param isPlayer     是不是玩家的车
     */
    public record Row(int lap, int position, int carNumber, String driver, String team,
                      double lapTime, String tire, double wear, double fuel,
                      double gapToLeader, int stint, boolean pit, boolean safetyCar,
                      boolean isPlayer, String category, int classPosition) {
    }

    /**
     * 生成整张表。
     *
     * @param playerNumber 玩家车号，没有就传 -1
     */
    public static List<Row> build(RaceResult result, int playerNumber) {
        // 每台车累计时间，用来算"落后领先者多少秒"
        Map<Integer, Double> cumulative = new HashMap<>();
        Map<Integer, Integer> lastStint = new HashMap<>();
        Map<Integer, String> carInfo = new HashMap<>();
        Map<Integer, String> teamInfo = new HashMap<>();
        Map<Integer, String> categoryInfo = new HashMap<>();
        Map<Integer, Integer> classPosInfo = new HashMap<>();

        for (RaceResult.Entry entry : result.standings()) {
            Car car = entry.car();
            carInfo.put(car.number(), car.driverName());
            teamInfo.put(car.number(), car.team().shortName());
            categoryInfo.put(car.number(), car.category().shortCode());
            classPosInfo.put(car.number(), entry.classPosition());
        }

        // 进站圈（按圈号 + 车号索引）
        Map<String, PitRecord> pitOf = new LinkedHashMap<>();
        for (PitRecord pit : result.pitStops()) {
            pitOf.put(pit.lap() + "#" + pit.carNumber(), pit);
        }

        Map<Integer, List<LapResult>> byLap = new LinkedHashMap<>();
        for (LapResult lap : result.lapResults()) {
            byLap.computeIfAbsent(lap.lap(), key -> new ArrayList<>()).add(lap);
        }

        List<Row> rows = new ArrayList<>();
        for (int lap = 1; lap <= result.totalLaps(); lap++) {
            List<LapResult> line = byLap.get(lap);
            if (line == null) {
                continue;
            }
            for (LapResult row : line) {
                cumulative.merge(row.carNumber(), row.lapTime(), Double::sum);
            }
            double leaderTime = Double.MAX_VALUE;
            for (LapResult row : line) {
                leaderTime = Math.min(leaderTime, cumulative.getOrDefault(row.carNumber(), 0.0));
            }

            List<LapResult> ordered = new ArrayList<>(line);
            ordered.sort(Comparator.comparingInt(LapResult::position));

            for (LapResult row : ordered) {
                int previousStint = lastStint.getOrDefault(row.carNumber(), row.stint());
                boolean pit = row.stint() != previousStint
                        || pitOf.containsKey(lap + "#" + row.carNumber());
                lastStint.put(row.carNumber(), row.stint());

                rows.add(new Row(
                        lap,
                        row.position(),
                        row.carNumber(),
                        carInfo.getOrDefault(row.carNumber(), "?"),
                        teamInfo.getOrDefault(row.carNumber(), "?"),
                        row.lapTime(),
                        row.compound().displayName(),
                        row.tireWear(),
                        row.fuelKg(),
                        Math.max(0, cumulative.getOrDefault(row.carNumber(), 0.0) - leaderTime),
                        row.stint(),
                        pit,
                        row.safetyCar(),
                        row.carNumber() == playerNumber,
                        categoryInfo.getOrDefault(row.carNumber(), "—"),
                        classPosInfo.getOrDefault(row.carNumber(), 0)));
            }
        }
        return rows;
    }

    /** 导出成 CSV（Excel 能直接打开，UTF-8 带 BOM）。 */
    public static String toCsv(List<Row> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append('\uFEFF');
        sb.append("圈,名次,组别,组内,车号,车手,车队,圈速(秒),圈速,轮胎,磨损%,油量kg,落后领先(秒),段,进站,安全车,是我\n");
        for (Row row : rows) {
            sb.append(row.lap()).append(',')
                    .append(row.position()).append(',')
                    .append(escape(row.category())).append(',')
                    .append(row.classPosition()).append(',')
                    .append(row.carNumber()).append(',')
                    .append(escape(row.driver())).append(',')
                    .append(escape(row.team())).append(',')
                    .append(String.format(Locale.ROOT, "%.3f", row.lapTime())).append(',')
                    .append(lapText(row.lapTime())).append(',')
                    .append(row.tire()).append(',')
                    .append(Math.round(row.wear() * 100)).append(',')
                    .append(String.format(Locale.ROOT, "%.1f", row.fuel())).append(',')
                    .append(String.format(Locale.ROOT, "%.3f", row.gapToLeader())).append(',')
                    .append(row.stint()).append(',')
                    .append(row.pit() ? "是" : "").append(',')
                    .append(row.safetyCar() ? "是" : "").append(',')
                    .append(row.isPlayer() ? "是" : "").append('\n');
        }
        return sb.toString();
    }

    /** 每台车的决策摘要：进了几次站、每段用多少圈什么胎。 */
    public static List<String> decisionSummary(RaceResult result) {
        List<String> lines = new ArrayList<>();
        for (RaceResult.Entry entry : result.standings()) {
            Car car = entry.car();
            StringBuilder sb = new StringBuilder();
            sb.append(String.format(Locale.ROOT, "P%-2d #%-3d %-10s ", entry.position(),
                    car.number(), car.driverName()));

            List<LapResult> laps = result.lapsOf(car.number());
            int index = 0;
            while (index < laps.size()) {
                String tire = laps.get(index).compound().displayName();
                int start = laps.get(index).lap();
                int count = 0;
                while (index < laps.size() && laps.get(index).compound().displayName().equals(tire)) {
                    index++;
                    count++;
                }
                sb.append(String.format(Locale.ROOT, "%s %d圈", tire, count));
                if (index < laps.size()) {
                    sb.append(" → ");
                }
            }
            sb.append(String.format(Locale.ROOT, "   （%d 次进站）", car.pitStops()));
            lines.add(sb.toString());
        }
        return lines;
    }

    /** 便于直接打印成一张表。 */
    public static String toTextTable(List<Row> rows, int maxRows) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT, "%4s %4s %5s %-10s %10s %-6s %5s %6s %8s %s%n",
                "圈", "名次", "车号", "车手", "圈速", "轮胎", "磨损", "油量", "落后", "标记"));
        int shown = 0;
        for (Row row : rows) {
            if (shown++ >= maxRows) {
                sb.append("... 共 ").append(rows.size()).append(" 行").append(System.lineSeparator());
                break;
            }
            sb.append(String.format(Locale.ROOT, "%4d %4d %5d %-10s %10s %-6s %4d%% %5.1f %7.1fs %s%s%n",
                    row.lap(), row.position(), row.carNumber(), row.driver(),
                    lapText(row.lapTime()), row.tire(),
                    Math.round(row.wear() * 100), row.fuel(), row.gapToLeader(),
                    row.pit() ? "[进站] " : "", row.safetyCar() ? "[FCY]" : ""));
        }
        return sb.toString();
    }

    /** 1:31.159 这种秒数格式。 */
    private static String lapText(double seconds) {
        int total = (int) Math.round(seconds * 1000);
        int ms = total % 1000;
        total /= 1000;
        return String.format(Locale.ROOT, "%d:%02d.%03d", total / 60, total % 60, ms);
    }

    private static String escape(String text) {
        if (text == null) {
            return "";
        }
        return text.contains(",") ? '"' + text.replace("\"", "\"\"") + '"' : text;
    }
}
