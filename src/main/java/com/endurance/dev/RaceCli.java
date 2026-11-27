package com.endurance.dev;

import com.endurance.data.DataLoader;
import com.endurance.data.GameData;
import com.endurance.model.Car;
import com.endurance.model.Driver;
import com.endurance.model.RaceConfig;
import com.endurance.model.Team;
import com.endurance.model.TireCompound;
import com.endurance.model.Track;
import com.endurance.model.Weather;
import com.endurance.sim.LapResult;
import com.endurance.sim.PitDecision;
import com.endurance.sim.RaceEvent;
import com.endurance.sim.RaceResult;
import com.endurance.sim.RaceSimulator;
import com.endurance.strategy.PlannedStopStrategy;
import com.endurance.strategy.Strategy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 命令行比赛跑盘器：不依赖界面，用来验证仿真内核是否跑得通、数值是否合理。
 *
 * <p>用法：
 * <pre>
 *   RaceCli list                 列出配置里的赛道 / 车队 / 轮胎
 *   RaceCli                      用第一条赛道的默认圈数跑一场
 *   RaceCli 60                   指定圈数
 *   RaceCli 60 7                 指定圈数与随机种子
 *   RaceCli 60 7 sahara          指定圈数、种子、赛道 id
 * </pre>
 */
public final class RaceCli {

    private RaceCli() {
    }

    public static void main(String[] args) {
        GameData data = loadDataOrExit();
        if (data == null) {
            return;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("list")) {
            printCatalog(data);
            return;
        }

        int laps = args.length > 0 ? Integer.parseInt(args[0]) : -1;
        long seed = args.length > 1 ? Long.parseLong(args[1]) : 20260101L;
        Track track = args.length > 2 ? data.track(args[2]) : data.defaultTrack();
        if (laps <= 0) {
            laps = track.defaultLaps();
        }

        var lineup = com.endurance.setup.RaceSetup.build(data, track, laps, seed, false, false);
        RaceConfig config = lineup.config();
        Map<Car, Strategy> strategies = lineup.strategies();

        long started = System.nanoTime();
        RaceResult result = new RaceSimulator(config, strategies).run();
        lineup.close();
        long elapsedMs = (System.nanoTime() - started) / 1_000_000;

        printHeader(config);
        printWeather(result);
        printPitSummary(result);
        printEvents(result);
        printStandings(result);
        printLapTrace(result);
        printTireSummary(result);

        System.out.printf(Locale.ROOT, "%n仿真耗时 %d ms（%d 圈 × %d 车）%n",
                elapsedMs, config.totalLaps(), config.cars().size());
    }

    // ------------------------------------------------------------------
    // 配置输出
    // ------------------------------------------------------------------

    static void printCatalog(GameData data) {
        System.out.println("=== 赛道 ===");
        for (Track track : data.tracks()) {
            System.out.printf(Locale.ROOT, "  %-10s %-14s %5.2f km  %2d 圈  磨损×%.2f%n",
                    track.id(), track.name(), track.lengthMeters() / 1000.0,
                    track.defaultLaps(), track.tireWearScale());
        }
        System.out.println("=== 车队 ===");
        for (Team team : data.teams()) {
            System.out.printf(Locale.ROOT, "  %-10s %-18s 速度偏移 %+5.2fs  可靠性 %.2f  车手 ",
                    team.id(), team.name(), team.paceOffset(), team.reliability());
            for (Driver driver : team.drivers()) {
                System.out.printf(Locale.ROOT, "#%d %s(%s)  ", driver.number(), driver.name(),
                        driver.startTireId());
            }
            System.out.println();
        }
        System.out.println("=== 轮胎配方 ===");
        for (TireCompound tire : data.tires()) {
            System.out.printf(Locale.ROOT,
                    "  %-13s %-6s 干地 %+5.2fs  磨损 %.3f/圈  寿命约 %2d 圈  磨穿惩罚 %.1fs%n",
                    tire.id(), tire.displayName(), tire.timeOffset(Weather.DRY),
                    tire.wearPerLap(), tire.estimatedLifeLaps(), tire.maxWearPenalty());
        }
        System.out.println();
        System.out.println("用法：RaceCli [圈数] [随机种子] [赛道id]");
    }

    // ------------------------------------------------------------------
    // 输出
    // ------------------------------------------------------------------

    private static void printHeader(RaceConfig config) {
        System.out.println("==============================================================");
        System.out.println(" Endurance Strategy · 比赛仿真报告");
        System.out.println("==============================================================");
        System.out.println("赛道   " + config.track().summary());
        System.out.printf(Locale.ROOT, "圈数   %d 圈    初始天气  %s    随机种子  %d%n",
                config.totalLaps(), config.initialWeather().displayName(), config.seed());
        System.out.println("车辆   " + config.cars().size() + " 台");
        System.out.println();
    }

    private static void printWeather(RaceResult result) {
        List<RaceEvent> changes = result.events().stream()
                .filter(event -> event.type() == RaceEvent.Type.WEATHER_CHANGE)
                .toList();
        System.out.println("--- 天气变化 ---");
        if (changes.isEmpty()) {
            System.out.println("  （全程 " + result.finalWeather().displayName() + "，没有变化）");
        } else {
            for (RaceEvent event : changes) {
                System.out.println("  " + event);
            }
        }
        System.out.println();
    }

    /** 打印每辆车的实际进站与分段用胎（AI 是临场决定的，没有"计划"可打印）。 */
    private static void printPitSummary(RaceResult result) {
        System.out.println("--- 实际进站与用胎 ---");
        for (RaceResult.Entry entry : result.standings()) {
            Car car = entry.car();
            List<LapResult> laps = result.lapsOf(car.number());
            StringBuilder text = new StringBuilder();
            int lastStint = -1;
            for (LapResult lap : laps) {
                if (lap.stint() != lastStint) {
                    if (lastStint >= 0) {
                        text.append(" ┃ ");
                    }
                    text.append(lap.compound().displayName());
                    lastStint = lap.stint();
                }
            }
            System.out.printf(Locale.ROOT, "  %-22s 停 %d 次   %s%n",
                    car.displayName(), car.pitStops(), text);
        }
        System.out.println();
    }

    private static void printEvents(RaceResult result) {
        List<RaceEvent> events = result.events().stream()
                .filter(event -> event.type() != RaceEvent.Type.WEATHER_CHANGE
                        && event.type() != RaceEvent.Type.PIT_STOP
                        && event.type() != RaceEvent.Type.FINISH)
                .toList();
        System.out.println("--- 意外事件 ---");
        if (events.isEmpty()) {
            System.out.println("  （无）");
        } else {
            events.forEach(event -> System.out.println("  " + event));
        }
        System.out.println();
    }

    private static void printStandings(RaceResult result) {
        System.out.println("--- 最终名次 ---");
        System.out.printf(Locale.ROOT, "%3s %4s %-10s %-12s %12s %10s %4s %10s %s%n",
                "P", "车号", "车队", "车手", "总时间", "差距", "停", "最快圈", "状态");
        for (RaceResult.Entry entry : result.standings()) {
            String gap = entry.position() == 1 || entry.retired()
                    ? "—" : String.format(Locale.ROOT, "+%.3f", entry.gapToWinner());
            String best = entry.car().bestLapTime() == Double.MAX_VALUE
                    ? "—" : formatLap(entry.car().bestLapTime());
            System.out.printf(Locale.ROOT, "%3d %4s %-10s %-12s %12s %10s %4d %10s %s%n",
                    entry.position(),
                    "#" + entry.car().number(),
                    entry.car().team().shortName(),
                    entry.car().driverName(),
                    formatTotal(entry.totalTime()),
                    gap,
                    entry.car().pitStops(),
                    best,
                    entry.status());
        }
        LapResult fastest = result.fastestLap();
        if (fastest != null) {
            System.out.printf(Locale.ROOT, "%n全场最快圈：第 %d 圈 %s %s%n",
                    fastest.lap(), "#" + fastest.carNumber(), formatLap(fastest.lapTime()));
        }
        System.out.println();
    }

    /** 打印两辆策略不同车的圈速轨迹，直观对比策略差异。 */
    private static void printLapTrace(RaceResult result) {
        List<RaceResult.Entry> standings = result.standings();
        if (standings.size() < 2) {
            return;
        }
        int carA = standings.get(0).car().number();
        int carB = standings.get(1).car().number();

        System.out.printf(Locale.ROOT, "--- 圈速轨迹对比：冠军 #%d vs 亚军 #%d ---%n", carA, carB);
        System.out.printf(Locale.ROOT, "%5s %12s %12s %10s %10s%n",
                "圈", "#" + carA, "#" + carB, "胎耗A", "胎耗B");

        List<LapResult> lapsA = result.lapsOf(carA);
        List<LapResult> lapsB = result.lapsOf(carB);
        int count = Math.min(lapsA.size(), lapsB.size());
        boolean everyLap = count <= 45;
        for (int i = 0; i < count; i += everyLap ? 1 : 5) {
            LapResult a = lapsA.get(i);
            LapResult b = lapsB.get(i);
            System.out.printf(Locale.ROOT, "%5d %12s %12s %9.0f%% %9.0f%%%n",
                    a.lap(), formatLap(a.lapTime()), formatLap(b.lapTime()),
                    a.tireWear() * 100, b.tireWear() * 100);
        }
        System.out.println();
    }

    private static void printTireSummary(RaceResult result) {
        System.out.println("--- 轮胎使用（每段） ---");
        for (RaceResult.Entry entry : result.standings()) {
            Car car = entry.car();
            List<LapResult> laps = result.lapsOf(car.number());
            if (laps.isEmpty()) {
                continue;
            }
            StringBuilder stints = new StringBuilder();
            int currentStint = laps.get(0).stint();
            TireCompound current = laps.get(0).compound();
            int length = 0;
            for (LapResult lap : laps) {
                if (lap.stint() != currentStint) {
                    stints.append(current.displayName()).append("×").append(length).append("圈  ");
                    currentStint = lap.stint();
                    current = lap.compound();
                    length = 0;
                }
                length++;
            }
            stints.append(current.displayName()).append("×").append(length).append("圈");
            System.out.printf(Locale.ROOT, "  %-22s %s%n", car.displayName(), stints);
        }
        System.out.println();
    }

    // ------------------------------------------------------------------

    static String formatTotal(double seconds) {
        if (seconds <= 0) {
            return "—";
        }
        int total = (int) Math.round(seconds * 1000);
        int ms = total % 1000;
        total /= 1000;
        int s = total % 60;
        total /= 60;
        int m = total % 60;
        int h = total / 60;
        return String.format(Locale.ROOT, "%d:%02d:%02d.%03d", h, m, s, ms);
    }

    static String formatLap(double seconds) {
        if (seconds <= 0 || seconds == Double.MAX_VALUE) {
            return "—";
        }
        int total = (int) Math.round(seconds * 1000);
        int ms = total % 1000;
        total /= 1000;
        int s = total % 60;
        int m = total / 60;
        return String.format(Locale.ROOT, "%d:%02d.%03d", m, s, ms);
    }

    /** 供其它 dev 工具复用：加载数据，失败时直接退出。 */
    static GameData loadDataOrExit() {
        try {
            return DataLoader.loadDefault();
        } catch (Exception e) {
            System.err.println("加载 data/ 配置失败：" + e.getMessage());
            System.err.println("请确认当前目录下有 data/tracks.json、data/teams.json、data/tires.json");
            System.exit(1);
            return null;
        }
    }
}
