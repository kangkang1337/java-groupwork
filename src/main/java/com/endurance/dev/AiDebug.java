package com.endurance.dev;

import com.endurance.ai.AiStrategy;
import com.endurance.data.GameData;
import com.endurance.model.Car;
import com.endurance.model.TireCompound;
import com.endurance.sim.LapResult;
import com.endurance.sim.RaceResult;
import com.endurance.sim.RaceSimulator;
import com.endurance.strategy.Strategy;

import java.util.Locale;
import java.util.Map;

/**
 * AI 行为诊断：打印每辆车推演了几次、在哪一圈进站、换成了什么胎。
 */
public final class AiDebug {

    private AiDebug() {
    }

    public static void main(String[] args) {
        GameData data = RaceCli.loadDataOrExit();
        if (data == null) {
            return;
        }
        long seed = args.length > 0 ? Long.parseLong(args[0]) : 9001L;
        var lineup = com.endurance.setup.RaceSetup.build(
                data, data.defaultTrack(), 60, seed, false, false);
        RaceSimulator simulator = new RaceSimulator(lineup.config(), lineup.strategies());
        RaceResult result = simulator.run();

        System.out.printf(Locale.ROOT, "%-24s %-8s %6s %6s   %s%n",
                "车", "性格", "推演", "停站", "用胎序列");
        Map<Car, Strategy> strategies = lineup.strategies();
        for (RaceResult.Entry entry : result.standings()) {
            Car car = entry.car();
            Strategy strategy = strategies.get(car);
            int plans = strategy instanceof AiStrategy ai ? ai.planCount() : -1;
            StringBuilder stints = new StringBuilder();
            int last = -1;
            for (LapResult lap : result.lapsOf(car.number())) {
                if (lap.stint() != last) {
                    stints.append(lap.compound().displayName()).append("(L").append(lap.lap()).append(") ");
                    last = lap.stint();
                }
            }
            System.out.printf(Locale.ROOT, "%-24s %-8s %6d %6d   %s%n",
                    car.displayName(), car.team().personality(), plans,
                    car.pitStops(), stints);
        }

        // 每 10 圈打印一次领先者的磨损与油量
        System.out.println();
        for (LapResult lap : result.lapsOf(result.standings().get(0).car().number())) {
            if (lap.lap() % 10 == 0) {
                System.out.printf(Locale.ROOT, "L%-3d 磨损 %4.0f%%  油 %4.0fkg  胎温 %3.0f  圈速 %s%n",
                        lap.lap(), lap.tireWear() * 100, lap.fuelKg(), lap.tireTemp(),
                        String.format(Locale.ROOT, "%.2f", lap.lapTime()));
            }
        }
        lineup.close();
    }
}
