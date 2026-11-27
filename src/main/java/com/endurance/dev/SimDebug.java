package com.endurance.dev;

import com.endurance.data.GameData;
import com.endurance.model.Car;
import com.endurance.model.RaceConfig;
import com.endurance.model.Track;
import com.endurance.sim.LapResult;
import com.endurance.sim.RaceResult;
import com.endurance.sim.RaceSimulator;
import com.endurance.strategy.Strategy;

import java.util.Locale;
import java.util.Map;

/**
 * 仿真内核诊断：逐圈打印每辆车的原始状态，用来定位数值异常。
 */
public final class SimDebug {

    private SimDebug() {
    }

    public static void main(String[] args) {
        GameData data = RaceCli.loadDataOrExit();
        if (data == null) {
            return;
        }
        int laps = args.length > 0 ? Integer.parseInt(args[0]) : 5;
        Track track = data.defaultTrack();
        var lineup = com.endurance.setup.RaceSetup.build(data, track, laps, 20260101L, false, false);
        RaceConfig config = lineup.config();
        Map<Car, Strategy> strategies = lineup.strategies();
        RaceResult result = new RaceSimulator(config, strategies).run();
        lineup.close();

        System.out.printf(Locale.ROOT, "%-24s %8s %8s", "车", "起步油", "侵略度");
        for (int i = 1; i <= laps; i++) {
            System.out.printf(Locale.ROOT, " %9s", "L" + i);
        }
        System.out.println();

        for (Car car : config.cars()) {
            System.out.printf(Locale.ROOT, "%-24s %8.1f %8.2f",
                    car.displayName(), car.fuelKg() + 0, car.aggression());
            for (LapResult lap : result.lapsOf(car.number())) {
                System.out.printf(Locale.ROOT, " %9.3f", lap.lapTime());
            }
            System.out.println();
        }

        System.out.println();
        System.out.println("--- 第 1 圈各车状态 ---");
        for (Car car : config.cars()) {
            var carLaps = result.lapsOf(car.number());
            if (carLaps.isEmpty()) {
                continue;
            }
            LapResult first = carLaps.get(0);
            System.out.printf(Locale.ROOT,
                    "%-24s 圈速 %7.3f  胎温 %5.1f  磨损 %5.3f  油 %5.1f  轮胎 %s%n",
                    car.displayName(), first.lapTime(), first.tireTemp(),
                    first.tireWear(), first.fuelKg(), first.compound().displayName());
        }
    }
}
