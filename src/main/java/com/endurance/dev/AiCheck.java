package com.endurance.dev;

import com.endurance.ai.AiPersonality;
import com.endurance.ai.AiStrategy;
import com.endurance.ai.MonteCarloPlanner;
import com.endurance.data.GameData;
import com.endurance.model.Car;
import com.endurance.model.Weather;
import com.endurance.sim.LapResult;
import com.endurance.sim.PitDecision;
import com.endurance.sim.RaceContext;
import com.endurance.sim.RaceResult;
import com.endurance.sim.RaceSimulator;
import com.endurance.strategy.Strategy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * AI 自检：连跑多场比赛，检查稳定性、性格差异与耗时。
 */
public final class AiCheck {

    private static int passed;
    private static int failed;

    private AiCheck() {
    }

    public static void main(String[] args) {
        GameData data = RaceCli.loadDataOrExit();
        if (data == null) {
            failed++;
            return;
        }
        testEmergencyFallback(data);
        int races = args.length > 0 ? Integer.parseInt(args[0]) : 20;

        Map<AiPersonality, List<Integer>> pitLaps = new LinkedHashMap<>();
        Map<AiPersonality, List<Integer>> finishPositions = new LinkedHashMap<>();
        Map<AiPersonality, Integer> stops = new LinkedHashMap<>();
        for (AiPersonality personality : AiPersonality.values()) {
            pitLaps.put(personality, new ArrayList<>());
            finishPositions.put(personality, new ArrayList<>());
            stops.put(personality, 0);
        }

        long totalMs = 0;
        int totalPlans = 0;
        int winners = 0;
        int finishers = 0;
        int carCount = 0;
        int crashes = 0;
        int maxPlans = 0;

        for (int i = 1; i <= races; i++) {
            long seed = 9000L + i;
            int plans = 0;
            try {
                var lineup = com.endurance.setup.RaceSetup.build(
                        data, data.defaultTrack(), 60, seed, false, false);
                RaceSimulator simulator = new RaceSimulator(lineup.config(), lineup.strategies());
                long started = System.nanoTime();
                RaceResult result = simulator.run();
                totalMs += (System.nanoTime() - started) / 1_000_000;

                for (Strategy strategy : lineup.strategies().values()) {
                    if (strategy instanceof AiStrategy ai) {
                        plans += ai.planCount();
                    }
                }
                totalPlans += plans;
                maxPlans = Math.max(maxPlans, plans);
                lineup.close();

                if (result.winner() != null) {
                    winners++;
                }
                carCount += result.standings().size();
                finishers += result.standings().stream().filter(e -> !e.retired()).count();

                for (RaceResult.Entry entry : result.standings()) {
                    Car car = entry.car();
                    AiPersonality personality = AiPersonality.parse(car.team().personality());
                    finishPositions.get(personality).add(entry.position());
                    stops.merge(personality, car.pitStops(), Integer::sum);
                    int firstPit = firstPitLap(result.lapsOf(car.number()));
                    if (firstPit > 0) {
                        pitLaps.get(personality).add(firstPit);
                    }
                }
            } catch (Throwable t) {
                crashes++;
                t.printStackTrace();
            }
        }

        System.out.println();
        System.out.println("=== " + races + " 场比赛统计 ===");
        System.out.printf(Locale.ROOT, "总耗时 %d ms（平均 %.0f ms/场）%n",
                totalMs, totalMs / (double) races);
        System.out.printf(Locale.ROOT, "AI 推演次数合计 %d（单场最多 %d）%n", totalPlans, maxPlans);
        System.out.println();

        System.out.printf(Locale.ROOT, "%-8s %10s %12s %10s%n",
                "性格", "平均进站圈", "平均名次", "场均进站");
        for (AiPersonality personality : AiPersonality.values()) {
            List<Integer> laps = pitLaps.get(personality);
            List<Integer> positions = finishPositions.get(personality);
            double avgLap = laps.stream().mapToInt(Integer::intValue).average().orElse(0);
            double avgPos = positions.stream().mapToInt(Integer::intValue).average().orElse(0);
            double avgStops = stops.get(personality) / (double) Math.max(1, positions.size());
            System.out.printf(Locale.ROOT, "%-8s %10.1f %12.2f %10.2f%n",
                    personality.displayName(), avgLap, avgPos, avgStops);
        }
        System.out.println();

        check("没有崩溃", crashes == 0);
        check("每场都有冠军", winners == races);
        check("退赛车很少（" + (carCount - finishers) + " 次 / 共 " + carCount + " 车次）",
                carCount - finishers <= races * 2);
        check("AI 确实做了推演（" + totalPlans + " 次）", totalPlans > 0);
        check("单场推演次数合理（最多 " + maxPlans + "）", maxPlans < races * 12 * 80);
        check("全场仿真耗时可接受（平均 " + (totalMs / races) + " ms）", totalMs / races < 3000);
        check("性格之间有策略差异", personalitiesDiffer(pitLaps));

        System.out.println();
        System.out.println("==== 结果: " + passed + " 通过 / " + failed + " 失败 ====");
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static int firstPitLap(List<LapResult> laps) {
        for (LapResult lap : laps) {
            if (lap.stint() > 0) {
                return lap.lap();
            }
        }
        return -1;
    }

    /** 紧急兜底只处理真的需要补油的情形，并遵守本站配方限制和天气。 */
    private static void testEmergencyFallback(GameData data) {
        com.endurance.model.Track track = data.track("imola");
        var twoLapLineup = com.endurance.setup.RaceSetup.build(
                data, track, 2, 711L, false, false);
        Car twoLapCar = twoLapLineup.config().cars().get(0);
        twoLapCar.setFuelKg(track.fuelPerLap() * 3.0);
        PitDecision enoughFuel = emergencyDecision(
                data, twoLapLineup.config(), twoLapCar, Weather.DRY, 2);
        check("剩余两圈且油量足够时不触发紧急进站", enoughFuel == null);
        twoLapLineup.close();

        var dryLineup = com.endurance.setup.RaceSetup.build(
                data, track, 2, 714L, false, false);
        Car dryCar = dryLineup.config().cars().get(0);
        dryCar.setFuelKg(track.fuelPerLap());
        PitDecision dryDecision = emergencyDecision(
                data, dryLineup.config(), dryCar, Weather.DRY, 2);
        check("干天紧急进站选择本站开放的干胎", dryDecision != null
                && dryDecision.compound() != null
                && track.allowsCompound(dryDecision.compound().id())
                && dryDecision.compound().timeOffset(Weather.DRY) < 8.0);
        dryLineup.close();

        var wetLineup = com.endurance.setup.RaceSetup.build(
                data, track, 2, 712L, false, false);
        Car wetCar = wetLineup.config().cars().get(0);
        wetCar.setFuelKg(track.fuelPerLap());
        PitDecision wetDecision = emergencyDecision(
                data, wetLineup.config(), wetCar, Weather.WET, 2);
        check("紧急进站按湿天选择雨胎", wetDecision != null && wetDecision.compound() != null
                && wetDecision.compound().timeOffset(Weather.DRY) >= 8.0);
        check("紧急进站只选择本站允许配方", wetDecision != null && wetDecision.compound() != null
                && track.allowsCompound(wetDecision.compound().id()));
        wetLineup.close();

        var finalLapLineup = com.endurance.setup.RaceSetup.build(
                data, track, 1, 713L, false, false);
        Car finalLapCar = finalLapLineup.config().cars().get(0);
        finalLapCar.setFuelKg(track.fuelPerLap() * 0.5);
        PitDecision finalLapDecision = emergencyDecision(
                data, finalLapLineup.config(), finalLapCar, Weather.DRY, 1);
        // 末圈也要救：停一次约 20 秒，不动就是退赛 0 分
        check("末圈油量不足时仍然进站", finalLapDecision != null
                && finalLapDecision.fuelToAdd() > 0);
        finalLapLineup.close();
    }

    private static PitDecision emergencyDecision(GameData data,
                                                  com.endurance.model.RaceConfig config,
                                                  Car car, Weather weather, int totalLaps) {
        Map<String, com.endurance.model.TireCompound> tires = new LinkedHashMap<>();
        for (var tire : data.tires()) {
            tires.put(tire.id(), tire);
        }
        MonteCarloPlanner planner = new MonteCarloPlanner(2, 4);
        try {
            AiStrategy strategy = new AiStrategy(car.number(), AiPersonality.BALANCED,
                    planner, tires, config, true);
            RaceContext context = RaceContext.of(1, totalLaps, weather,
                    config.track(), List.of(car));
            return strategy.decide(context, car);
        } finally {
            planner.shutdown();
        }
    }

    /** 至少两种性格的平均进站圈数差 2 圈以上，才算"性格真的有区别"。 */
    private static boolean personalitiesDiffer(Map<AiPersonality, List<Integer>> pitLaps) {
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (List<Integer> laps : pitLaps.values()) {
            if (laps.isEmpty()) {
                continue;
            }
            double avg = laps.stream().mapToInt(Integer::intValue).average().orElse(0);
            min = Math.min(min, avg);
            max = Math.max(max, avg);
        }
        return max - min >= 2.0;
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
