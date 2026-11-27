package com.endurance.analysis;

import com.endurance.data.Json;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 真实赛事数据（FIA WEC 官方结果）与比赛"形状指标"。
 *
 * <p>用来回答一个问题：我们的仿真跑出来的比赛，**长得像不像真的 WEC**？
 * 不追求圈速绝对值一致（那是物理引擎的事），而是比几个结构性指标：
 * 最快圈占平均圈的比例、前二名差距、全场差距幅度、被套圈的车占比。</p>
 */
public final class RealReference {

    /** 一场真实比赛及其形状指标。 */
    public record Race(String event, String track, double lengthKm, int turns,
                       int winnerLaps, double winnerTotal, double bestLap,
                       double gapP1P2, double gapP1P10, int lappedCars, int carCount) {

        /** 平均圈速。 */
        public double averageLap() {
            return winnerTotal / winnerLaps;
        }

        /** 最快圈 / 平均圈：越接近 1 说明全场都很快、波动小。 */
        public double bestOverAverage() {
            return bestLap / averageLap();
        }
    }

    private RealReference() {
        throw new AssertionError("工具类不允许实例化");
    }

    public static File defaultFile() {
        return new File(System.getProperty("user.dir"), "data/reference/wec-2026.json");
    }

    /** 读取所有参考赛事。 */
    public static List<Race> load(File file) throws IOException {
        List<Race> races = new ArrayList<>();
        if (!file.isFile()) {
            return races;
        }
        var root = Json.parseFile(file);
        for (var node : root.get("races").asArray()) {
            var results = node.get("results").asArray();
            if (results.isEmpty()) {
                continue;
            }
            var winner = results.get(0);
            double bestLap = Double.MAX_VALUE;
            double gapP1P2 = 0;
            double gapP1P10 = 0;
            int lapped = 0;
            int winnerLaps = (int) winner.get("laps").asDouble();

            for (var entry : results) {
                int laps = (int) entry.get("laps").asDouble();
                bestLap = Math.min(bestLap, entry.get("bestLap").asDouble());
                if (laps < winnerLaps) {
                    lapped++;
                }
                int position = (int) entry.get("pos").asDouble();
                if (position == 2) {
                    gapP1P2 = entry.get("gapSeconds").asDouble();
                }
                if (position == 10) {
                    gapP1P10 = entry.get("gapSeconds").asDouble();
                }
            }
            races.add(new Race(
                    node.get("event").asString(),
                    node.get("track").asString(),
                    node.opt("lengthKm", new Json.JsonValue.Num(0)).asDouble(),
                    (int) node.opt("turns", new Json.JsonValue.Num(0)).asDouble(),
                    winnerLaps,
                    winner.get("totalSeconds").asDouble(),
                    bestLap,
                    gapP1P2,
                    gapP1P10,
                    lapped,
                    results.size()));
        }
        return races;
    }

    /** 打印参考数据摘要。 */
    public static String describe(List<Race> races) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT,
                "%-34s %5s %9s %9s %7s %8s %8s %5s%n",
                "赛事", "圈数", "冠军用时", "平均圈", "最快圈", "快/均", "P1-P2", "套圈"));
        for (Race race : races) {
            sb.append(String.format(Locale.ROOT,
                    "%-34s %5d %9s %8.2fs %6.2fs %8.3f %7.1fs %4d台%n",
                    race.event(), race.winnerLaps(), lapText(race.winnerTotal()),
                    race.averageLap(), race.bestLap(), race.bestOverAverage(),
                    race.gapP1P2(), race.lappedCars()));
        }
        return sb.toString();
    }

    private static String lapText(double seconds) {
        int total = (int) Math.round(seconds);
        return String.format(Locale.ROOT, "%d:%02d:%02d",
                total / 3600, (total % 3600) / 60, total % 60);
    }
}
