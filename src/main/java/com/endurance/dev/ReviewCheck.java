package com.endurance.dev;

import com.endurance.analysis.RaceReport;
import com.endurance.analysis.RaceLog;
import com.endurance.data.GameData;
import com.endurance.data.Json;
import com.endurance.data.JsonWriter;
import com.endurance.data.RaceHistory;
import com.endurance.data.RaceSave;
import com.endurance.model.Car;
import com.endurance.model.TireCompound;
import com.endurance.sim.RaceResult;
import com.endurance.sim.RaceSimulator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 复盘与存档自检：评分、JSON 读写、断点续跑、历史战绩。
 */
public final class ReviewCheck {

    private static int passed;
    private static int failed;

    private ReviewCheck() {
    }

    public static void main(String[] args) throws Exception {
        com.endurance.util.FxEnv.configure();
        GameData data = RaceCli.loadDataOrExit();
        if (data == null) {
            failed++;
            return;
        }
        testJsonWriter();
        testCsvColumns();
        testLogging();
        testScore(data);
        testSaveLoad(data);
        testHistory();
        testPlayerProfile(data);
        testOptimizerReachability(data);

        System.out.println();
        System.out.println("==== 结果: " + passed + " 通过 / " + failed + " 失败 ====");
        if (failed > 0) {
            System.exit(1);
        }
    }

    /** JSON 写出后能被自己的解析器读回来。 */
    private static void testJsonWriter() {
        String json = JsonWriter.obj()
                .put("name", "示例 \"引号\"")
                .put("value", 12.5)
                .put("count", 7)
                .put("flag", true)
                .put("list", JsonWriter.arr().add(1).add(2).add("x"))
                .toJson();
        var node = Json.parse(json);
        check("JSON 往返：字符串", node.get("name").asString().equals("示例 \"引号\""));
        check("JSON 往返：小数", Math.abs(node.get("value").asDouble() - 12.5) < 1e-9);
        check("JSON 往返：整数", (int) node.get("count").asDouble() == 7);
        check("JSON 往返：布尔", node.get("flag").asBool());
        check("JSON 往返：数组", node.get("list").asArray().size() == 3);
    }

    /** CSV 行必须按表头顺序写入组别与组内名次，并保持列数一致。 */
    private static void testCsvColumns() {
        RaceLog.Row row = new RaceLog.Row(7, 3, 51, "车手甲", "车队甲",
                101.25, "中性胎", 0.4, 32.5, 8.75, 2,
                true, false, true, "Hypercar", 2);
        String[] lines = RaceLog.toCsv(List.of(row)).replace("\uFEFF", "").split("\\R");
        String[] header = lines[0].split(",", -1);
        String[] values = lines[1].split(",", -1);
        check("CSV 数据列数与表头一致（" + values.length + "）", values.length == header.length);
        check("CSV 组别和组内名次位于正确列",
                values.length == 17 && values[2].equals("Hypercar")
                        && values[3].equals("2") && values[4].equals("51"));
    }

    /** 日志必须真的写到文件里（打包后没有控制台，全靠它排错）。 */
    private static void testLogging() {
        String marker = "self-check " + System.nanoTime();
        com.endurance.util.AppLog.info(marker);
        com.endurance.util.AppLog.error("self-check failure record",
                new IllegalStateException("deliberate test error"));

        Path file = com.endurance.util.AppLog.logFile();
        boolean exists = Files.isRegularFile(file);
        check("log file created", exists);

        boolean contains = false;
        try {
            contains = exists && Files.readString(file).contains(marker);
        } catch (Exception ignored) {
            // treat as failure
        }
        check("log content can be read back", contains);
        check("last error captured", com.endurance.util.AppLog.lastError() != null
                && com.endurance.util.AppLog.stackTraceOf(com.endurance.util.AppLog.lastError())
                .contains("deliberate test error"));
    }
    /** 决策评分要落在合理区间，并且给出点评。 */
    private static void testScore(GameData data) {
        var lineup = com.endurance.setup.RaceSetup.build(
                data, data.defaultTrack(), 30, 424242L, false, false);
        RaceSimulator simulator = new RaceSimulator(lineup.config(), lineup.strategies());
        RaceResult result = simulator.run();
        lineup.close();

        int carNumber = result.standings().get(0).car().number();
        RaceReport.Score score = RaceReport.analyze(result, carNumber, data, simulator.config());

        check("进站记录被采集（" + result.pitStops().size() + " 条）", !result.pitStops().isEmpty());
        check("进站记录与车辆状态一致",
                score.stops() == result.standings().get(0).car().pitStops());

        check("评分在 0~100 之间（" + score.score() + "）",
                score.score() >= 0 && score.score() <= 100);
        check("有评级文字（" + score.grade() + "）", score.grade() != null && !score.grade().isBlank());
        check("算出了理论最优时间（" + String.format("%.1f", score.optimalTime()) + "s）",
                score.optimalTime() > 0);
        check("最优方案非空（" + score.optimalPlan() + "）",
                score.optimalPlan() != null && !score.optimalPlan().isBlank());
        check("至少一条复盘意见", !score.notes().isEmpty());
        check("冠军的评分不低于 60（" + score.score() + "）", score.score() >= 60);

        // 评分必须能区分好坏，否则等于没打分
        int max = 0;
        int min = 100;
        System.out.println("  各车评分：");
        for (RaceResult.Entry entry : result.standings()) {
            RaceReport.Score each = RaceReport.analyze(result, entry.car().number(), data,
                    simulator.config());
            System.out.printf("    #%-3d %-10s 评分 %3d  实际 %.1fs  最优 %.1fs%n",
                    entry.car().number(), entry.car().driverName(), each.score(),
                    each.playerTime(), each.optimalTime());
            max = Math.max(max, each.score());
            min = Math.min(min, each.score());
        }
        check("评分能区分好坏（最高 " + max + " / 最低 " + min + "）", max - min >= 5);
    }

    /** 存档再读档，状态要能原样接上。 */
    private static void testSaveLoad(GameData data) throws Exception {
        var lineup = com.endurance.setup.RaceSetup.build(
                data, data.defaultTrack(), 20, 777L, false, false);
        RaceSimulator original = new RaceSimulator(lineup.config(), lineup.strategies());
        for (int i = 0; i < 8; i++) {
            original.step();
        }

        Path file = Files.createTempFile("endurance-save", ".json");
        RaceSave.save(original, false, file);
        check("存档文件已写出", Files.size(file) > 40);

        // 重新装配一场同参数的比赛，再读档覆盖
        var restoredLineup = com.endurance.setup.RaceSetup.build(
                data, data.defaultTrack(), 20, 777L, false, false);
        RaceSimulator restored = new RaceSimulator(
                restoredLineup.config(), restoredLineup.strategies());

        Map<String, TireCompound> tires = new LinkedHashMap<>();
        for (TireCompound tire : data.tires()) {
            tires.put(tire.id(), tire);
        }
        RaceSave.Meta meta = RaceSave.restore(file.toFile(), restored, tires);

        check("读档记录的圈数一致（" + meta.completedLaps() + "）", meta.completedLaps() == 8);
        check("读档后圈数恢复", restored.completedLaps() == 8);
        check("读档后天气一致", restored.currentWeather() == original.currentWeather());

        boolean stateMatches = true;
        for (Car car : restored.cars()) {
            Car source = null;
            for (Car candidate : original.cars()) {
                if (candidate.number() == car.number()) {
                    source = candidate;
                    break;
                }
            }
            if (source == null) {
                continue;
            }
            if (Math.abs(car.totalTime() - source.totalTime()) > 0.001
                    || Math.abs(car.fuelKg() - source.fuelKg()) > 0.001
                    || Math.abs(car.tireWear() - source.tireWear()) > 0.001
                    || !car.compound().id().equals(source.compound().id())) {
                stateMatches = false;
            }
        }
        check("读档后车辆状态一致", stateMatches);

        restored.step();
        check("读档后能继续跑", restored.completedLaps() == 9);

        Files.deleteIfExists(file);
        lineup.close();
        restoredLineup.close();
    }

    /** 历史战绩追加与读取。 */
    private static void testHistory() throws Exception {
        Path file = RaceHistory.file();
        boolean existed = Files.isRegularFile(file);
        byte[] backup = existed ? Files.readAllBytes(file) : null;
        try {
            int before = RaceHistory.load().size();
            RaceHistory.append(RaceHistory.record("测试赛道", 30, 3, false,
                    5400.5, 2, 102.3, 82, "测试车手"));
            List<RaceHistory.RaceRecord> after = RaceHistory.load();
            check("战绩条数 +1", after.size() == before + 1);

            var last = after.get(after.size() - 1);
            check("战绩内容正确", last.trackName().equals("测试赛道")
                    && last.position() == 3 && last.score() == 82);
        } finally {
            if (backup != null) {
                Files.write(file, backup);
            } else {
                Files.deleteIfExists(file);
            }
        }
    }

    /**
     * 优化器的「理论最优」必须真的能跑出来——它比实际还慢，就说明模型低估了某些方案。
     * （历史上就栽过：优化器没算安全车期间的进站折扣，玩家 6 停反而比「最优」快 68 秒。）
     */
    private static void testOptimizerReachability(GameData data) throws Exception {
        var lineup = com.endurance.setup.RaceSetup.build(
                data, data.defaultTrack(), data.defaultTrack().defaultLaps(), 20260101L, false, false);
        var simulator = new com.endurance.sim.RaceSimulator(lineup.config(), lineup.strategies());
        var result = simulator.run();
        lineup.close();

        var winner = result.winner();
        if (winner == null) {
            check("有完赛冠军用于校验优化器", false);
            return;
        }
        var score = RaceReport.analyze(result, winner.car().number(), data, simulator.config());
        check(String.format(java.util.Locale.ROOT,
                        "理论最优不比实际慢（实际 %.1fs / 最优 %.1fs）",
                        score.playerTime(), score.optimalTime()),
                score.optimalTime() <= score.playerTime() + 5);
    }

    /** 玩家改名：存得住、读得回、只动自己的车队。 */
    private static void testPlayerProfile(GameData data) throws Exception {
        var file = com.endurance.data.PlayerProfile.file();
        boolean existed = Files.isRegularFile(file);
        byte[] backup = existed ? Files.readAllBytes(file) : null;
        try {
            var defaults = com.endurance.data.PlayerProfile.defaults();
            check("默认车手叫 1号 / 2号",
                    defaults.driverOne().equals("1号") && defaults.driverTwo().equals("2号"));

            var custom = new com.endurance.data.PlayerProfile("测试车队", "TEST", "张三", "李四");
            custom.save();
            var loaded = com.endurance.data.PlayerProfile.load();
            check("改名能保存并读回",
                    loaded.teamName().equals("测试车队")
                            && loaded.driverOne().equals("张三")
                            && loaded.driverTwo().equals("李四"));

            var raw = com.endurance.data.DataLoader.loadRaw();
            var applied = custom.applyTo(raw);
            var playerTeam = com.endurance.data.PlayerProfile.playerTeam(applied);
            check("套用后玩家车队改名", playerTeam != null && playerTeam.name().equals("测试车队"));
            check("套用后车手改名",
                    playerTeam != null && playerTeam.drivers().get(0).name().equals("张三")
                            && playerTeam.drivers().get(1).name().equals("李四"));

            boolean othersUntouched = true;
            for (int i = 0; i < raw.teams().size(); i++) {
                var before = raw.teams().get(i);
                var after = applied.teams().get(i);
                if (!com.endurance.data.PlayerProfile.isPlayerTeam(before)
                        && !before.name().equals(after.name())) {
                    othersUntouched = false;
                }
            }
            check("真实车队不受影响", othersUntouched);
        } finally {
            if (backup != null) {
                Files.write(file, backup);
            } else {
                Files.deleteIfExists(file);
            }
        }
    }
    private static void check(String name, boolean condition) {
        if (condition) {
            passed++;
            System.out.println("[PASS] " + name);
        } else {
            failed++;
            System.out.println("[FAIL] " + name);
        }
    }
}
