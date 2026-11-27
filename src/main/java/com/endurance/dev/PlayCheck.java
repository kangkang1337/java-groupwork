package com.endurance.dev;

import com.endurance.data.GameData;
import com.endurance.model.Car;
import com.endurance.model.Driver;
import com.endurance.model.RaceConfig;
import com.endurance.model.Team;
import com.endurance.model.TireCompound;
import com.endurance.model.Track;
import com.endurance.model.Weather;
import com.endurance.sim.PitDecision;
import com.endurance.sim.RaceSimulator;
import com.endurance.ui.MainWindow;
import javafx.application.Platform;

import java.util.LinkedHashMap;
import java.util.concurrent.CountDownLatch;

/**
 * 可玩性自检：验证"玩家下指令 → 进站生效"、"安全车减速"、"冲线结算"三条链路。
 */
public final class PlayCheck {

    private static int passed;
    private static int failed;

    private PlayCheck() {
    }

    public static void main(String[] args) throws Exception {
        com.endurance.util.FxEnv.configure();
        CountDownLatch latch = new CountDownLatch(1);
        Throwable[] failure = new Throwable[1];

        Platform.startup(() -> {
            try {
                runAll();
            } catch (Throwable t) {
                failure[0] = t;
                t.printStackTrace();
            } finally {
                latch.countDown();
            }
        });
        latch.await();
        Platform.exit();

        System.out.println();
        System.out.println("==== 结果: " + passed + " 通过 / " + failed + " 失败 ====");
        System.exit(failed > 0 || failure[0] != null ? 1 : 0);
    }

    private static void runAll() {
        GameData data = RaceCli.loadDataOrExit();
        if (data == null) {
            failed++;
            return;
        }
        testPlayerPitOrder(data);
        testSafetyCar(data);
        testFinishOverlay(data);
        testCalendarSwitch(data);
        testClassStandings(data);
        testSvgTracks();
        testSpeedProfile();
        testCarSelection(data);
        testSafetyCarCompression(data);
        testReviewWording(data);
        testTireRestrictions(data);
        testFuelOnlyPitStop(data);
        testStartTireLegality(data);
        testSaveResumeConsistency(data);
        testSavePhaseAndFinish(data);
        testSafetyCarTiming(data);
        testRedFlagPause(data);
        testSeedDeterminism(data);
        testWeatherForecast(data);
    }

    /** 玩家排队一条进站指令，下一圈应该真的进站。 */
    private static void testPlayerPitOrder(GameData data) {
        MainWindow window = new MainWindow(data, data.defaultTrack());
        window.getSession().pause();
        window.getSession().advance(10);

        Car player = window.getLineup().playerCar();
        check("配置里标记了玩家车", player != null);
        if (player == null) {
            return;
        }

        var strategy = window.getLineup().playerStrategy();
        int before = player.pitStops();
        double fuelBefore = player.fuelKg();
        TireCompound beforeCompound = player.compound();
        TireCompound newCompound = data.hard().equals(beforeCompound) ? data.soft() : data.hard();

        strategy.queue(new PitDecision(newCompound, 40));
        check("指令已排队", strategy.hasPending());
        check("排队后面板显示已排队", window.getPlayerPanel().hasQueuedCommand());

        window.getSession().stepOnce();

        check("下一圈执行了进站", player.pitStops() == before + 1);
        check("轮胎已更换", player.compound().equals(newCompound));
        check("加了油", player.fuelKg() > fuelBefore);
        check("指令消费后队列清空", !strategy.hasPending());
    }

    /** 安全车期间圈速显著变慢、磨损几乎为零。 */
    private static void testSafetyCar(GameData data) {
        Track track = data.defaultTrack();
        Team team = data.teams().get(0);
        RaceConfig.Builder builder = RaceConfig.builder(track)
                .laps(8)
                .seed(7)
                .weather(Weather.DRY)
                .weatherEnabled(false)
                .safetyCar(true, 1.0, 3);   // 必然触发

        for (Driver driver : team.drivers()) {
            Car car = new Car(driver.number(), team, driver.name(),
                    data.medium(), 70);
            builder.addCar(car);
        }
        RaceConfig config = builder.build();
        RaceSimulator simulator = new RaceSimulator(config, new LinkedHashMap<>());

        simulator.step();
        check("安全车已触发", simulator.isSafetyCarActive());

        double lapOne = simulator.lapResults().stream()
                .filter(lap -> lap.lap() == 1)
                .findFirst().map(lap -> lap.lapTime()).orElse(0.0);
        double base = track.baseLapSeconds();
        check(String.format("安全车圈速明显变慢（%.1fs vs 基准 %.1fs）", lapOne, base),
                lapOne > base * 1.4);

        double wear = simulator.lapResults().stream()
                .filter(lap -> lap.lap() == 1)
                .findFirst().map(lap -> lap.tireWear()).orElse(1.0);
        check(String.format("安全车期间几乎不磨胎（%.3f）", wear), wear < 0.01);
    }

    /** P0-1：所有分站、所有车手，起步轮胎都必须合法。 */
    private static void testStartTireLegality(GameData data) {
        int checkedCars = 0;
        java.util.List<String> offenders = new java.util.ArrayList<>();
        for (var track : data.tracks()) {
            var config = com.endurance.setup.RaceSetup.buildRace(data, track, 10, 1L);
            for (var car : config.cars()) {
                checkedCars++;
                if (!track.allowsCompound(car.compound().id())) {
                    offenders.add(track.id() + "/#" + car.number()
                            + "=" + car.compound().id());
                }
            }
        }
        check("全部 " + checkedCars + " 个车手起步轮胎合法"
                        + (offenders.isEmpty() ? "" : "（违规：" + offenders + "）"),
                offenders.isEmpty());
    }

/**
     * P0-4 验收：跑 N 圈存档 → 读档 → 跑完，必须和一口气跑完的结果逐车一致。
     *
     * <p>这一条同时证明了好几件事：随机数状态、逐圈记录、进站记录、旗语、
     * 安全车、名次快照、AI 冷却都被完整保存了。少恢复任何一样，
     * 读档后的比赛就会从分叉点开始跑出不同的成绩。</p>
     */
    private static void testSaveResumeConsistency(GameData data) {
        java.util.Map<String, com.endurance.model.TireCompound> tireMap = new java.util.LinkedHashMap<>();
        for (var tire : data.tires()) {
            tireMap.put(tire.id(), tire);
        }
        long seed = 20260101L;
        int totalLaps = 14;
        int stopAt = 6;

        // A：一口气跑完
        var lineupA = com.endurance.setup.RaceSetup.build(
                data, data.defaultTrack(), totalLaps, seed, false, false);
        var simA = new com.endurance.sim.RaceSimulator(lineupA.config(), lineupA.strategies());
        var resultA = simA.run();
        lineupA.close();

        // B：跑到 stopAt 圈，存档
        java.io.File saveFile = new java.io.File(".tmp/save-resume-test.json");
        saveFile.getParentFile().mkdirs();
        int lapsAtSave;
        try {
            var lineupB = com.endurance.setup.RaceSetup.build(
                    data, data.defaultTrack(), totalLaps, seed, false, false);
            var simB = new com.endurance.sim.RaceSimulator(lineupB.config(), lineupB.strategies());
            for (int i = 0; i < stopAt; i++) {
                simB.step();
            }
            lapsAtSave = simB.completedLaps();
            com.endurance.data.RaceSave.save(simB, false, saveFile.toPath());
            lineupB.close();
        } catch (Exception e) {
            check("存档写入成功（" + e.getMessage() + "）", false);
            return;
        }
        check("存档写入成功（" + saveFile.length() / 1024 + " KB）", saveFile.length() > 0);

        // C：新装配一场，读档后继续跑完
        var lineupC = com.endurance.setup.RaceSetup.build(
                data, data.defaultTrack(), totalLaps, seed, false, false);
        var simC = new com.endurance.sim.RaceSimulator(lineupC.config(), lineupC.strategies());
        try {
            com.endurance.data.RaceSave.restore(saveFile, simC, tireMap);
        } catch (Exception e) {
            check("读档成功（" + e.getMessage() + "）", false);
            lineupC.close();
            return;
        }
        check("读档恢复到第 " + simC.completedLaps() + " 圈（存档时 " + lapsAtSave + " 圈）",
                simC.completedLaps() == lapsAtSave);
        check("读档恢复了历史逐圈记录（" + simC.lapResults().size() + " 条）",
                simC.lapResults().size() > 0);

        var resultC = simC.run();
        lineupC.close();

        // 逐车比较总成绩
        java.util.Map<Integer, Double> timeA = new java.util.LinkedHashMap<>();
        for (var entry : resultA.standings()) {
            timeA.put(entry.car().number(), entry.totalTime());
        }
        int mismatched = 0;
        double maxDelta = 0;
        StringBuilder detail = new StringBuilder();
        for (var entry : resultC.standings()) {
            Double expected = timeA.get(entry.car().number());
            if (expected == null) {
                mismatched++;
                continue;
            }
            double delta = Math.abs(expected - entry.totalTime());
            maxDelta = Math.max(maxDelta, delta);
            if (delta > 1e-6) {
                mismatched++;
                if (detail.length() < 80) {
                    detail.append("#").append(entry.car().number())
                            .append(" 差 ").append(String.format(java.util.Locale.ROOT, "%.3f", delta))
                            .append("s ");
                }
            }
        }
        check(String.format(java.util.Locale.ROOT,
                        "断点续跑与一口气跑完一致（最大偏差 %.6f 秒%s）",
                        maxDelta, mismatched == 0 ? "" : "，" + detail),
                mismatched == 0);

        // 旧版本存档必须被拒绝，而不是解析出半对半错的状态
        boolean rejected = false;
        try {
            java.nio.file.Files.writeString(saveFile.toPath(),
                    "{\"version\":1,\"trackId\":\"imola\",\"completedLaps\":3}",
                    java.nio.charset.StandardCharsets.UTF_8);
            var lineupD = com.endurance.setup.RaceSetup.build(
                    data, data.defaultTrack(), totalLaps, seed, false, false);
            var simD = new com.endurance.sim.RaceSimulator(lineupD.config(), lineupD.strategies());
            try {
                com.endurance.data.RaceSave.restore(saveFile, simD, tireMap);
            } finally {
                lineupD.close();
            }
        } catch (java.io.IOException expected) {
            rejected = expected.getMessage() != null && expected.getMessage().contains("版本");
        }
        check("旧版本存档被拒绝并提示原因", rejected);
    }

/**
     * 存档要恢复"比赛进行到哪一步"，完赛的比赛则不该再被存档。
     *
     * <p>这两条是读档流程里最容易漏的：
     * ① 存档写了 formationLap，但读档没恢复，中途存档读回来会白跑一遍暖胎圈，
     *    暖胎圈结束时还会重复写一条「绿旗：比赛开始」；
     * ② 完赛后存档没被拦住，读档后模拟器认为比赛未结束，下一次 step() 会多推进一圈。</p>
     */
    private static void testSavePhaseAndFinish(GameData data) {
        java.io.File phaseFile = new java.io.File(".tmp/save-phase-test.json");
        phaseFile.getParentFile().mkdirs();

        // ① 暖胎圈中存档 → peek 应读回 formationLap = true
        var lineupA = com.endurance.setup.RaceSetup.build(
                data, data.defaultTrack(), 10, 55L, false, false);
        var simA = new com.endurance.sim.RaceSimulator(lineupA.config(), lineupA.strategies());
        simA.step();
        boolean phaseRoundTrip = false;
        try {
            com.endurance.data.RaceSave.save(simA, true, phaseFile.toPath());
            phaseRoundTrip = com.endurance.data.RaceSave.peek(phaseFile).formationLap();
        } catch (Exception e) {
            phaseRoundTrip = false;
        }
        lineupA.close();
        check("存档能记录并读回比赛阶段（暖胎圈中）", phaseRoundTrip);

        // 比赛中存档 → formationLap = false
        var lineupB = com.endurance.setup.RaceSetup.build(
                data, data.defaultTrack(), 10, 55L, false, false);
        var simB = new com.endurance.sim.RaceSimulator(lineupB.config(), lineupB.strategies());
        for (int i = 0; i < 3; i++) {
            simB.step();
        }
        boolean runningPhase = true;
        try {
            com.endurance.data.RaceSave.save(simB, false, phaseFile.toPath());
            runningPhase = !com.endurance.data.RaceSave.peek(phaseFile).formationLap();
        } catch (Exception e) {
            runningPhase = false;
        }

        // ② 开赛事件幂等：暖胎圈结束重入时不能写两条
        int beforeStart = simB.events().size();
        simB.markRaceStart();
        simB.markRaceStart();
        boolean idempotent = simB.events().size() == beforeStart + 1;
        check("正常比赛存档读回时阶段为「比赛中」", runningPhase);
        check("开赛事件只记录一次（重复调用 markRaceStart）", idempotent);
        lineupB.close();

        // ③ 完赛的比赛不允许存档
        var lineupC = com.endurance.setup.RaceSetup.build(
                data, data.defaultTrack(), 5, 66L, false, false);
        var simC = new com.endurance.sim.RaceSimulator(lineupC.config(), lineupC.strategies());
        simC.run();
        boolean rejected = false;
        try {
            com.endurance.data.RaceSave.capture(simC, false);
        } catch (IllegalStateException expected) {
            rejected = expected.getMessage() != null && expected.getMessage().contains("结束");
        }
        check("跑完的比赛拒绝存档", rejected);

        // ④ 完赛后继续推进不会多跑一圈（结算面板上点「下一圈」走的就是这里）
        int lapsAtFinish = simC.completedLaps();
        int eventsAtFinish = simC.events().size();
        boolean stepped = simC.step();
        check("完赛后 step() 返回 false 且不多跑一圈",
                !stepped && simC.completedLaps() == lapsAtFinish
                        && simC.events().size() == eventsAtFinish);
        lineupC.close();
    }
    /** P0-5：强制 3 圈安全车，必须恰好 3 圈，且第 4 圈不再处于安全车。 */
    private static void testSafetyCarTiming(GameData data) {
        var lineup = com.endurance.setup.RaceSetup.build(
                data, data.defaultTrack(), 20, 909L, true, false);
        var simulator = new com.endurance.sim.RaceSimulator(lineup.config(), lineup.strategies());
        for (int i = 0; i < 6; i++) {
            simulator.step();
        }
        simulator.forceSafetyCar(3);

        int safetyLaps = 0;
        for (int i = 0; i < 3; i++) {
            simulator.step();
            if (simulator.safetyCarThisLap()) {
                safetyLaps++;
            }
        }
        simulator.step();
        boolean stopped = !simulator.safetyCarThisLap();
        lineup.close();

        check("强制 3 圈安全车正好跑满 3 圈（实际 " + safetyLaps + " 圈）", safetyLaps == 3);
        check("安全车结束后的下一圈不再处于安全车", stopped);
    }

    /** P0-7：红旗期间赛车不前进，完成圈数不能增加。 */
    private static void testRedFlagPause(GameData data) {
        var lineup = com.endurance.setup.RaceSetup.build(
                data, data.defaultTrack(), 20, 4242L, false, false);
        var simulator = new com.endurance.sim.RaceSimulator(lineup.config(), lineup.strategies());
        for (int i = 0; i < 5; i++) {
            simulator.step();
        }
        int lapsBefore = simulator.completedLaps();
        var car = simulator.cars().get(0);
        double timeBefore = car.totalTime();
        double fuelBefore = car.fuelKg();
        double wearBefore = car.tireWear();

        simulator.forceRedFlag(2);
        for (int i = 0; i < 2; i++) {
            simulator.step();
        }
        int lapsAfter = simulator.completedLaps();
        lineup.close();

        check("红旗期间完成圈数不变（" + lapsBefore + " → " + lapsAfter + "）",
                lapsAfter == lapsBefore);
        check("红旗期间赛车时间不变",
                Math.abs(car.totalTime() - timeBefore) < 1e-6);
        check("红旗期间油量和磨损不变",
                Math.abs(car.fuelKg() - fuelBefore) < 1e-6
                        && Math.abs(car.tireWear() - wearBefore) < 1e-6);
    }

    /** P0-8：相同 seed 的两场比赛，结果必须完全一致。 */
    private static void testSeedDeterminism(GameData data) {
        java.util.List<Double> first = runSeedTrace(data, 77L);
        java.util.List<Double> second = runSeedTrace(data, 77L);
        boolean same = first.size() == second.size();
        if (same) {
            for (int i = 0; i < first.size(); i++) {
                if (Math.abs(first.get(i) - second.get(i)) > 1e-9) {
                    same = false;
                    break;
                }
            }
        }
        check("相同 seed 两场比赛结果逐车一致（" + first.size() + " 台车）", same);
    }

    private static java.util.List<Double> runSeedTrace(GameData data, long seed) {
        var lineup = com.endurance.setup.RaceSetup.build(
                data, data.defaultTrack(), 15, seed, false, false);
        var simulator = new com.endurance.sim.RaceSimulator(lineup.config(), lineup.strategies());
        var result = simulator.run();
        lineup.close();
        java.util.List<Double> trace = new java.util.ArrayList<>();
        for (var entry : result.standings()) {
            trace.add(entry.totalTime());
        }
        return trace;
    }

    /** 只加油不换胎：省时间、但磨损继续累积（不换胎 ≠ 换同一套新胎）。 */
    private static void testFuelOnlyPitStop(GameData data) {
        var lineup = com.endurance.setup.RaceSetup.build(
                data, data.defaultTrack(), 20, 606L, false, false);
        var simulator = new com.endurance.sim.RaceSimulator(lineup.config(), lineup.strategies());
        var track = data.defaultTrack();

        var car = simulator.cars().get(0);
        int number = car.number();
        var tire = car.compound();
        car.advanceLap(120, 0.42, com.endurance.model.Weather.DRY, 20, 1.0);
        double wearBefore = car.tireWear();
        String compoundBefore = car.compound().id();

        // 只加油：按不换胎计价
        double costNoTires = simulator.config().pitStopCost(40, false);
        double costWithTires = simulator.config().pitStopCost(40, true);
        car.applyPitStop(costNoTires, null, 40);
        lineup.close();

        check(String.format(java.util.Locale.ROOT,
                        "不换胎比换胎省 %.1f 秒", costWithTires - costNoTires),
                costWithTires - costNoTires > 2.0);
        check("不换胎后磨损继续累积（" + String.format(java.util.Locale.ROOT, "%.2f", wearBefore)
                + " → " + String.format(java.util.Locale.ROOT, "%.2f", car.tireWear()) + "）",
                car.tireWear() >= wearBefore - 1e-9);
        check("不换胎后配方不变（" + compoundBefore + "）",
                compoundBefore.equals(car.compound().id()));
        check("只加油也算进过站（" + car.pitStops() + " 次）", car.pitStops() >= 1);
    }
    /** 分站轮胎限制要真的落到界面上：伊莫拉没软胎，按钮就得是灰的。 */
    private static void testTireRestrictions(GameData data) {
        MainWindow imola = new MainWindow(data, data.track("imola"));
        imola.getSession().pause();
        check("伊莫拉站禁用软胎按钮", !imola.playerPanel().isTireEnabled("soft"));
        check("伊莫拉站中胎可选", imola.playerPanel().isTireEnabled("medium"));
        check("伊莫拉站雨胎可选（不受分站限制）", imola.playerPanel().isTireEnabled("wet"));

        MainWindow lemans = new MainWindow(data, data.track("lemans"));
        lemans.getSession().pause();
        check("勒芒站三种干胎全开", lemans.playerPanel().isTireEnabled("soft")
                && lemans.playerPanel().isTireEnabled("medium")
                && lemans.playerPanel().isTireEnabled("hard"));
    }

    /** 天气面板直接展示仿真器按赛道倾向计算的预测。 */
    private static void testWeatherForecast(GameData data) {
        var lineup = com.endurance.setup.RaceSetup.build(
                data, data.track("spa"), 8, 5150L, false, false);
        RaceSimulator simulator = new RaceSimulator(lineup.config(), lineup.strategies());
        com.endurance.sim.WeatherModel expectedModel = new com.endurance.sim.WeatherModel(
                new java.util.Random(1), true, data.track("spa").rainBias());
        double expectedChance = expectedModel.rainChance(simulator.currentWeather(), 5);
        com.endurance.ui.WeatherPanel panel = new com.endurance.ui.WeatherPanel(java.util.Map.of());
        panel.update(simulator);
        check("天气面板展示包含斯帕降雨倾向的预测",
                panel.rainText().equals(String.format(java.util.Locale.ROOT, "%.0f%%",
                        expectedChance * 100)));
        lineup.close();
    }

    /** 复盘文案不能说反话：比理论最优快的时候不能写"慢了 0.0 秒"。 */
    private static void testReviewWording(GameData data) {
        var lineup = com.endurance.setup.RaceSetup.build(
                data, data.defaultTrack(), 20, 20260101L, false, false);
        var simulator = new com.endurance.sim.RaceSimulator(lineup.config(), lineup.strategies());
        var result = simulator.run();
        lineup.close();

        boolean wordingOk = true;
        boolean scaleOk = true;
        for (var entry : result.standings()) {
            var score = com.endurance.analysis.RaceReport.analyze(
                    result, entry.car().number(), data, simulator.config());
            double delta = score.playerTime() - score.optimalTime();
            String text = String.join(" ", score.notes());
            if (text.contains("慢了 0.0 秒")) {
                wordingOk = false;
            }
            if (delta < -0.05 && text.contains("慢了 ")) {
                // 实际更快却写"慢了 x 秒"
                wordingOk = false;
            }
        }
        check("复盘文案不会说反（不会出现\"慢了 0.0 秒\"）", wordingOk);

        // 安全车圈不该被算进"相对最快圈累计损失"
        var winner = result.winner();
        if (winner != null) {
            var score = com.endurance.analysis.RaceReport.analyze(
                    result, winner.car().number(), data, simulator.config());
            long safetyLaps = result.lapsOf(winner.car().number()).stream()
                    .filter(com.endurance.sim.LapResult::safetyCar).count();
            boolean lossSane = score.lapLoss() < result.lapsOf(winner.car().number()).size() * 60.0;
            check(String.format(java.util.Locale.ROOT,
                            "圈损没被安全车圈带偏（%d 个安全车圈，损失 %.0f 秒）",
                            safetyLaps, score.lapLoss()),
                    lossSane);
        }
    }
    /** 安全车要把差距抹平，而且"安全车回去"只播一条。 */
    private static void testSafetyCarCompression(GameData data) {
        var lineup = com.endurance.setup.RaceSetup.build(
                data, data.defaultTrack(), 20, 777L, true, false);
        var simulator = new com.endurance.sim.RaceSimulator(lineup.config(), lineup.strategies());
        for (int i = 0; i < 8; i++) {
            simulator.step();
        }
        double before = spread(simulator);

        simulator.forceSafetyCar(3);
        int eventsBefore = countSafetyCarEvents(simulator);
        for (int i = 0; i < 3; i++) {
            simulator.step();
        }
        double after = spread(simulator);
        int eventsAfter = countSafetyCarEvents(simulator);
        lineup.close();

        check(String.format(java.util.Locale.ROOT,
                        "安全车抹平差距（%.0f 秒 → %.0f 秒）", before, after),
                after < before * 0.75);
        check("安全车结束播报不超过 2 条（" + (eventsAfter - eventsBefore) + " 条）",
                eventsAfter - eventsBefore <= 2);
    }

    /** 全场第一名到最后一名的差距。 */
    private static double spread(com.endurance.sim.RaceSimulator simulator) {
        var standings = simulator.liveStandings();
        if (standings.size() < 2) {
            return 0;
        }
        return standings.get(standings.size() - 1).totalTime() - standings.get(0).totalTime();
    }

    private static int countSafetyCarEvents(com.endurance.sim.RaceSimulator simulator) {
        int count = 0;
        for (var event : simulator.events()) {
            if (event.type() == com.endurance.sim.RaceEvent.Type.SAFETY_CAR) {
                count++;
            }
        }
        return count;
    }

    /** 点排名表任一行，车况面板要跟着切过去（原来是每帧重建行导致点击失效）。 */
    private static void testCarSelection(GameData data) {
        MainWindow window = new MainWindow(data, data.defaultTrack());
        window.getSession().pause();
        window.getSession().skipFormation();

        int playerNumber = window.getSimulator().cars().get(0).number();
        int otherNumber = -1;
        for (var car : window.getSimulator().cars()) {
            if (car.number() != playerNumber) {
                otherNumber = car.number();
                break;
            }
        }

        boolean clicked = window.standingsPanel().clickRow(otherNumber);
        check("排名表能点到 #" + otherNumber + " 这一行", clicked);
        check("点击后选中的就是 #" + otherNumber + "（实际 #" + window.selectedCar() + "）",
                window.selectedCar() != null && window.selectedCar() == otherNumber);

        window.refreshNow();
        check("车况面板跟着切换到 #" + otherNumber + "（标题 " + window.carStatusTitle() + "）",
                window.carStatusTitle().startsWith("#" + otherNumber + " "));

        // 连续刷新 30 次（模拟动画帧），选中状态不能被重建冲掉
        for (int i = 0; i < 30; i++) {
            window.refreshNow();
        }
        check("刷新 30 帧后仍然选中 #" + otherNumber, window.selectedCar() == otherNumber);
    }
    /** 速度剖面：直道要比弯道明显快，且位置连续不跳变。 */
    private static void testSpeedProfile() {
        var polyline = com.endurance.ui.TrackShapes.sample("imola", 24);
        var path = com.endurance.ui.TrackPath.of(polyline);

        double ratio = path.straightToCornerRatio();
        check(String.format(java.util.Locale.ROOT, "直道比弯道明显快（快 %.1f 倍）", ratio),
                ratio > 1.5);

        // 把一圈切成 400 份，量每一小步的位移：直道那步应该明显比弯道那步长
        int steps = 400;
        double maxStep = 0;
        double minStep = Double.MAX_VALUE;
        double[] previous = path.pointAt(0);
        boolean continuous = true;
        for (int i = 1; i <= steps; i++) {
            double[] current = path.pointAt(i / (double) steps);
            double step = Math.hypot(current[0] - previous[0], current[1] - previous[1]);
            if (i > 1) {
                maxStep = Math.max(maxStep, step);
                minStep = Math.min(minStep, step);
            }
            // 相邻两步的移动距离不应该突然差几十倍（否则就是跳变）
            if (i > 1 && step > 0.02) {
                continuous = false;
            }
            previous = current;
        }
        check("整圈位置连续，没有跨圈跳变", continuous);
        check(String.format(java.util.Locale.ROOT,
                        "单步位移有快慢差异（最快 %.4f / 最慢 %.4f）", maxStep, minStep),
                minStep > 0 && maxStep / minStep > 2.0);
    }

    /** SVG 赛道解析：官方矢量图能变成可用的闭合折线。 */
    private static void testSvgTracks() {
        String svg = "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 100 60'>"
                + "<path fill='none' d='M 10 30 C 10 10, 40 10, 50 30 "
                + "C 60 50, 90 50, 90 30 L 90 45 Q 50 55 10 45 Z'/>"
                + "<path d='M 0 0 L 1 1'/></svg>";
        var points = com.endurance.ui.SvgTracks.parse(svg);
        check("SVG 路径能解析出点（" + points.size() + " 个）", points.size() > 20);

        boolean inRange = true;
        for (double[] p : points) {
            if (p[0] < 0.05 || p[0] > 0.95 || p[1] < 0.05 || p[1] > 0.95) {
                inRange = false;
            }
        }
        check("SVG 点被归一化到画面内", inRange);

        var first = points.get(0);
        var last = points.get(points.size() - 1);
        double closingGap = Math.hypot(first[0] - last[0], first[1] - last[1]);
        check(String.format(java.util.Locale.ROOT, "路径首尾闭合（缺口 %.4f）", closingGap),
                closingGap < 0.02);
    }
    /** 双组同场：组别名次必须按组内时间顺序，1..N 连续。 */
    private static void testClassStandings(GameData data) {
        var lineup = com.endurance.setup.RaceSetup.build(
                data, data.defaultTrack(), 30, 20260101L, false, false);
        var simulator = new com.endurance.sim.RaceSimulator(lineup.config(), lineup.strategies());
        var result = simulator.run();
        lineup.close();

        java.util.Map<com.endurance.model.RaceCategory, java.util.List<Integer>> byClass =
                new java.util.EnumMap<>(com.endurance.model.RaceCategory.class);
        for (var entry : result.standings()) {
            byClass.computeIfAbsent(entry.car().category(), k -> new java.util.ArrayList<>())
                    .add(entry.classPosition());
        }

        boolean sequential = true;
        for (var list : byClass.values()) {
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i) != i + 1) {
                    sequential = false;
                }
            }
        }
        check("每个组别的组别名次是 1..N 连续", sequential);
        check("Hypercar 与 LMGT3 都在场（" + byClass.keySet() + "）", byClass.size() >= 2);

        // 完赛的车里，最快的 GT3 应该比最慢的 Hypercar 还慢（退赛车不参与比较）
        double slowestHypercar = 0;
        double fastestGt3 = Double.MAX_VALUE;
        for (var entry : result.standings()) {
            if (entry.retired()) {
                continue;
            }
            if (entry.car().category() == com.endurance.model.RaceCategory.HYPERCAR) {
                slowestHypercar = Math.max(slowestHypercar, entry.totalTime());
            } else {
                fastestGt3 = Math.min(fastestGt3, entry.totalTime());
            }
        }
        check(String.format(java.util.Locale.ROOT,
                        "GT3 比 Hypercar 慢一个组别（最快 GT3 落后最慢 Hypercar %.0f 秒）",
                        fastestGt3 - slowestHypercar),
                fastestGt3 > slowestHypercar);
    }

    /** 分站日历：切站后比赛要按新赛道重开。 */
    private static void testCalendarSwitch(GameData data) {
        MainWindow window = new MainWindow(data, data.defaultTrack());
        window.getSession().pause();
        check("默认站是 R1（" + window.currentRound() + "）", window.currentRound() == 1);
        check("默认赛道是伊莫拉", "imola".equals(window.getTrack().id()));

        window.selectRound(3);
        check("切到 R3（" + window.currentRound() + "）", window.currentRound() == 3);
        check("赛道换成勒芒", "lemans".equals(window.getTrack().id()));
        check("切到勒芒后软/中/硬胎按钮全部启用",
                window.playerPanel().isTireEnabled("soft")
                        && window.playerPanel().isTireEnabled("medium")
                        && window.playerPanel().isTireEnabled("hard"));
        check("圈数按勒芒折算（" + window.getSimulator().totalLaps() + "）",
                window.getSimulator().totalLaps() == window.getTrack().defaultLaps());
        check("勒芒站有三组", window.getTrack().categories().size() == 3);

        window.selectRound(8);
        check("切到 R8 蒙扎", "monza".equals(window.getTrack().id()));
        check("切到蒙扎后启用软/中胎并禁用硬胎",
                window.playerPanel().isTireEnabled("soft")
                        && window.playerPanel().isTireEnabled("medium")
                        && !window.playerPanel().isTireEnabled("hard"));
        window.selectRound(1);
        check("切回伊莫拉后启用中/硬胎并禁用软胎",
                !window.playerPanel().isTireEnabled("soft")
                        && window.playerPanel().isTireEnabled("medium")
                        && window.playerPanel().isTireEnabled("hard"));
        window.getSession().stepOnce();
        check("切站后比赛能继续推进", window.getSimulator().completedLaps() == 1);
    }
    /** 跑到终点应该弹出结算面板。 */
    private static void testFinishOverlay(GameData data) {
        MainWindow window = new MainWindow(data, data.defaultTrack());
        window.getSession().pause();
        check("比赛开始时没有结算面板", !window.isResultShown());

        window.getSession().runToEnd();
        check("比赛已结束", window.getSimulator().isFinished());
        check("冲线后旗语变为格子旗",
                window.getSimulator().currentFlag() == com.endurance.model.RaceFlag.CHEQUERED);
        check("结束后弹出结算面板", window.isResultShown());
    }

    // ------------------------------------------------------------------

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
