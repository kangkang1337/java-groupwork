package com.endurance.ai;

import com.endurance.model.Team;
import com.endurance.model.TireCompound;
import com.endurance.model.Track;
import com.endurance.model.Weather;

import java.util.List;
import java.util.Map;

/**
 * 比赛快照：把推演需要的状态"冻结"下来。
 *
 * <p>蒙特卡洛推演跑在后台线程，绝对不能在另一个线程里读那些会被仿真器改写的
 * {@code Car} 对象——否则会读到半更新的状态。所以每圈开始时拍一份不可变快照，
 * 后台只认快照。</p>
 */
public record RaceSnapshot(
        int lap,
        int totalLaps,
        Weather weather,
        Track track,
        double tireWearScale,
        double pitLaneLoss,
        double tireChangeCost,
        double refuelSecondsPerKg,
        double fuelTimePerKg,
        Map<String, TireCompound> tires,
        List<CarSnapshot> cars) {

    /** 一辆车在某一刻的状态。 */
    public record CarSnapshot(
            int number,
            double totalTime,
            String tireId,
            double tireWear,
            double tireTemp,
            double fuelKg,
            boolean retired,
            int stint,
            double aggression,
            double paceOffset,
            AiPersonality personality,
            Team team) {

        public TireCompound tire(Map<String, TireCompound> catalog) {
            TireCompound compound = catalog.get(tireId);
            return compound == null ? catalog.values().iterator().next() : compound;
        }
    }

    /** 还剩几圈。 */
    public int remainingLaps() {
        return totalLaps - lap + 1;
    }

    public CarSnapshot car(int number) {
        for (CarSnapshot car : cars) {
            if (car.number() == number) {
                return car;
            }
        }
        return null;
    }

    /** 进站总耗时；不换胎时省掉换胎那几秒。 */
    public double pitCost(double fuel, boolean changeTires) {
        return pitLaneLoss + (changeTires ? tireChangeCost : 0) + fuel * refuelSecondsPerKg;
    }
}
