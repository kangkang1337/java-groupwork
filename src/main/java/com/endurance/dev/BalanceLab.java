package com.endurance.dev;

import com.endurance.data.GameData;
import com.endurance.model.Car;
import com.endurance.model.RaceConfig;
import com.endurance.model.Team;
import com.endurance.model.TireCompound;
import com.endurance.model.Track;
import com.endurance.model.Weather;
import com.endurance.sim.RaceResult;
import com.endurance.sim.RaceSimulator;
import com.endurance.strategy.PlannedStopStrategy;
import com.endurance.strategy.Strategy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 平衡性实验台：固定其它变量，只让"N 停策略"不同，看哪种策略赢。
 *
 * <p>理想结果是"最优停站次数"随赛道磨损倍率变化——如果某一种永远赢，
 * 说明进站代价和轮胎衰减的比例没调对。</p>
 */
public final class BalanceLab {

    /** 一次进站。 */
    private record Stop(int lap, TireCompound compound, double fuel) {
    }

    /** 一套完整策略。 */
    private record Plan(String name, TireCompound start, List<Stop> stops) {
    }

    private static final int SEEDS = 24;

    private BalanceLab() {
    }

    public static void main(String[] args) {
        GameData data = RaceCli.loadDataOrExit();
        if (data == null) {
            return;
        }
        Track base = data.defaultTrack();
        TireCompound soft = data.soft();
        TireCompound medium = data.medium();
        TireCompound hard = data.hard();

        double[] pitLosses = {18, 20, 22};
        double[] wearScales = {0.68, 0.72, 0.75, 0.78};

        System.out.printf(Locale.ROOT,
                "%-6s %-6s %-13s %-13s %-13s %-13s %-13s %-15s %8s%n",
                "进站代价", "磨损倍率", "一停@25", "一停@30", "一停@35", "二停@20/40", "二停@25/45",
                "三停@15/30/45", "最大差");

        for (double pitLoss : pitLosses) {
            for (double wearScale : wearScales) {
                sweep(data, base, soft, medium, hard, pitLoss, wearScale);
            }
        }
    }

    private static void sweep(GameData data, Track base, TireCompound soft, TireCompound medium,
                              TireCompound hard, double pitLoss, double wearScale) {
        int laps = base.defaultLaps();
        List<Plan> plans = List.of(
                new Plan("一停@25", medium, List.of(new Stop(laps * 25 / 60, hard, 62))),
                new Plan("一停@30", medium, List.of(new Stop(laps * 30 / 60, hard, 55))),
                new Plan("一停@35", medium, List.of(new Stop(laps * 35 / 60, hard, 48))),
                new Plan("二停@20/40", medium, List.of(
                        new Stop(laps * 20 / 60, medium, 42),
                        new Stop(laps * 40 / 60, medium, 42))),
                new Plan("二停@25/45", medium, List.of(
                        new Stop(laps * 25 / 60, hard, 48),
                        new Stop(laps * 45 / 60, hard, 34))),
                new Plan("三停@15/30/45", soft, List.of(
                        new Stop(laps * 15 / 60, soft, 30),
                        new Stop(laps * 30 / 60, soft, 32),
                        new Stop(laps * 45 / 60, soft, 32)))
        );

        int[] wins = new int[plans.size()];
        double[] totalTimes = new double[plans.size()];
        double sumSpread = 0;

        for (int seed = 1; seed <= SEEDS; seed++) {
            RaceConfig config = buildConfig(base, laps, plans, seed, pitLoss, wearScale);
            Map<Car, Strategy> strategies = new LinkedHashMap<>();
            for (Car car : config.cars()) {
                Plan plan = plans.get(car.number() - 1);
                PlannedStopStrategy strategy = new PlannedStopStrategy(plan.name());
                for (Stop stop : plan.stops()) {
                    strategy.at(stop.lap(), new com.endurance.sim.PitDecision(stop.compound(), stop.fuel()));
                }
                strategies.put(car, strategy);
            }
            RaceResult result = new RaceSimulator(config, strategies).run();

            double min = Double.MAX_VALUE;
            double max = 0;
            for (RaceResult.Entry entry : result.standings()) {
                int index = entry.car().number() - 1;
                totalTimes[index] += entry.totalTime();
                min = Math.min(min, entry.totalTime());
                max = Math.max(max, entry.totalTime());
            }
            wins[result.standings().get(0).car().number() - 1]++;
            sumSpread += max - min;
        }

        StringBuilder row = new StringBuilder();
        row.append(String.format(Locale.ROOT, "%-6.0f %-6.2f", pitLoss, wearScale));
        for (int i = 0; i < plans.size(); i++) {
            row.append(String.format(Locale.ROOT, " %d胜/%.0fs", wins[i], totalTimes[i] / SEEDS));
        }
        row.append(String.format(Locale.ROOT, "      %6.1fs", sumSpread / SEEDS));
        System.out.println(row);
    }

    private static RaceConfig buildConfig(Track base, int laps, List<Plan> plans,
                                          int seed, double pitLoss, double wearScale) {
        // 所有车完全相同，只有策略不同
        Team neutral = new Team("TST", "测试车队", "测试", 0.0, 1.0, "#888888", 0.5, List.of(), "balanced", com.endurance.model.RaceCategory.HYPERCAR);

        RaceConfig.Builder builder = RaceConfig.builder(base)
                .laps(laps)
                .seed(seed)
                .weather(Weather.DRY)
                .pitLaneLoss(pitLoss)
                .tireWearScale(wearScale)
                .weatherEnabled(false);

        for (int i = 0; i < plans.size(); i++) {
            Car car = new Car(i + 1, neutral, "车手" + (i + 1), plans.get(i).start(), 80);
            car.setAggression(0.72);
            builder.addCar(car);
        }
        return builder.build();
    }
}
