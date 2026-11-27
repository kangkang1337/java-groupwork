package com.endurance.analysis;

import com.endurance.model.TireCompound;
import com.endurance.model.Track;
import com.endurance.model.Weather;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 进站方案优化器：把"这场比赛最优的进站方案"算出来，用来给玩家的决策打分。
 *
 * <p>做法是穷举（最多两停）× 逐圈重算，因为单辆车的仿真极快，
 * 几千个方案也就几十毫秒——比蒙特卡洛更适合回答"理论上最好能跑多少"。</p>
 */
public final class PitOptimizer {

    /** 一个候选方案。 */
    public record Plan(List<Integer> pitLaps, List<String> compounds, double totalTime,
                       double pitCost) {

        public String describe() {
            if (pitLaps.isEmpty()) {
                return "零停";
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < pitLaps.size(); i++) {
                if (i > 0) {
                    sb.append("、");
                }
                sb.append("第 ").append(pitLaps.get(i)).append(" 圈换").append(compounds.get(i));
            }
            return sb.toString();
        }
    }

    /**
     * 求解最优方案。
     *
     * @param track      赛道
     * @param weather    每圈天气（按圈号索引，长度 = 总圈数）
     * @param tires      可用轮胎
     * @param startTire  起步轮胎
     * @param startFuel  起步油量
     * @param paceOffset 车辆基础速度偏移
     * @param aggression 驾驶激进度
     * @param pitCost    进站基础耗时（秒）
     * @param fuelPerLap 每圈油耗
     * @param fuelTimePerKg 每公斤油带来的圈速代价
     * @param wearScale  轮胎磨损倍率
     */
    public static Plan optimal(Track track, List<Weather> weather, Map<String, TireCompound> tires,
                               TireCompound startTire, double startFuel, double paceOffset,
                               double aggression, double pitCost, double fuelPerLap,
                               double fuelTimePerKg, double wearScale, double refuelSecondsPerKg,
                               List<Boolean> safetyCarLaps) {
        int laps = weather.size();
        List<TireCompound> options = dryOptions(track, tires);
        Plan best = simulate(tires, track, weather, startTire, startFuel, paceOffset, aggression,
                pitCost, fuelPerLap, fuelTimePerKg, wearScale, refuelSecondsPerKg,
                safetyCarLaps, List.of(), List.of());

        // 一停
        int step = Math.max(1, laps / 40);
        for (int first = 3; first <= laps - 3; first += step) {
            for (TireCompound tire : options) {
                Plan plan = simulate(tires, track, weather, startTire, startFuel, paceOffset, aggression,
                        pitCost, fuelPerLap, fuelTimePerKg, wearScale, refuelSecondsPerKg,
                        safetyCarLaps, List.of(first), List.of(tire.id()));
                if (plan.totalTime() < best.totalTime()) {
                    best = plan;
                }
            }
        }

        // 二停（步长放大，避免组合爆炸）
        int coarse = Math.max(2, laps / 12);
        for (int first = 3; first <= laps - 6; first += coarse) {
            for (int second = first + 4; second <= laps - 3; second += coarse) {
                for (TireCompound a : options) {
                    for (TireCompound b : options) {
                        Plan plan = simulate(tires, track, weather, startTire, startFuel, paceOffset,
                                aggression, pitCost, fuelPerLap, fuelTimePerKg, wearScale,
                                refuelSecondsPerKg, safetyCarLaps,
                                List.of(first, second), List.of(a.id(), b.id()));
                        if (plan.totalTime() < best.totalTime()) {
                            best = plan;
                        }
                    }
                }
            }
        }
        return best;
    }

    // ------------------------------------------------------------------

    private static Plan simulate(Map<String, TireCompound> tires, Track track,
                                 List<Weather> weather, TireCompound startTire,
                                 double startFuel, double paceOffset, double aggression,
                                 double pitCost, double fuelPerLap, double fuelTimePerKg,
                                 double wearScale, double refuelSecondsPerKg,
                                 List<Boolean> safetyCarLaps,
                                 List<Integer> pitLaps, List<String> compounds) {
        int laps = weather.size();
        double total = 0;
        double wear = 0;
        double temp = 40;
        double fuel = startFuel;
        TireCompound tire = startTire;
        double totalPit = 0;
        int stint = 0;
        // 磨损公式用的是"这套胎已经跑了几圈"，不是第几套胎
        int stintLaps = 0;
        int nextPitIndex = 0;

        for (int lap = 1; lap <= laps; lap++) {
            boolean safetyCar = !safetyCarLaps.isEmpty()
                    && safetyCarLaps.get(Math.min(lap - 1, safetyCarLaps.size() - 1));

            if (nextPitIndex < pitLaps.size() && pitLaps.get(nextPitIndex) == lap) {
                int left = laps - lap + 1;
                double needed = fuelPerLap * left * 1.02 + 2;
                // 只补够跑完的差额：油箱里还够就不用加油
                double addFuel = Math.max(0, Math.min(115, needed - fuel));
                double cost = pitCost + addFuel * refuelSecondsPerKg;
                if (safetyCar) {
                    // 安全车期间全场都慢，进站只花三分之一——必须和仿真器一致，
                    // 否则优化器会低估「安全车窗口进站」的价值
                    cost *= 0.35;
                }
                total += cost;
                totalPit += cost;
                fuel = Math.min(130, fuel + addFuel);
                tire = tires.get(compounds.get(nextPitIndex));
                wear = 0;
                temp = 55;
                stint++;
                stintLaps = 0;   // 换新胎
                nextPitIndex++;
            }

            stintLaps++;   // 先累加，再算本圈磨损（与 Car.advanceLap 一致）
            Weather current = weather.get(Math.min(lap - 1, weather.size() - 1));
            if (safetyCar) {
                // 安全车期间全场同速，这一圈与进站策略无关
                total += track.baseLapSeconds() * 1.55;
                continue;
            }
            double lapTime = track.baseLapSeconds()
                    + current.basePenalty()
                    + tire.timeOffset(current)
                    + tire.wearPenalty(wear)
                    + fuel * fuelTimePerKg
                    + Math.max(0, Math.abs(temp - tire.optimalTemp()) - 12) * 0.06
                    + paceOffset
                    + (0.88 - aggression) * 4.0;
            total += Math.max(track.baseLapSeconds() * 0.85, lapTime);

            double target = track.trackTempCelsius() + 45 + aggression * 20;
            temp += (target - temp) * 0.42;
            temp += wear * 4;
            double aggressionFactor = 1 + (aggression - 0.90) * 1.6;
            double overheat = 1 + Math.max(0, (temp - tire.optimalTemp()) / 30.0);
            wear = Math.min(1.25, wear + tire.wearPerLap() * aggressionFactor * overheat
                    * current.wearMultiplier() * wearScale * (1 + stintLaps * 0.004));
            fuel = Math.max(0, fuel - fuelPerLap * (1 + (aggression - 0.90) * 0.6));
        }
        return new Plan(pitLaps, compounds, total, totalPit);
    }

    /** 简单起见，方案里只考虑当前天气下合理的胎。 */
    /**
     * 这一站能用的干胎配方。
     *
     * <p>官方规则：普通分站每队只有两种干胎可选，勒芒三种。
     * 所以"软胎换软胎"往往不是偷懒，而是因为你手上就只有这两种配方。</p>
     */
    private static List<TireCompound> dryOptions(Track track, Map<String, TireCompound> tires) {
        List<TireCompound> options = new ArrayList<>();
        for (TireCompound tire : tires.values()) {
            if (!track.allowsCompound(tire.id())) {
                continue;
            }
            if (tire.timeOffset(Weather.DRY) < 8) {
                options.add(tire);
            }
        }
        if (options.isEmpty()) {
            // 万一配置把配方写空了，别让优化器直接崩掉
            options.addAll(tires.values());
        }
        return options;
    }
}
