package com.endurance.ai;

import com.endurance.model.TireCompound;
import com.endurance.model.Weather;
import com.endurance.sim.PitDecision;
import com.endurance.sim.WeatherModel;

import java.util.Map;
import java.util.Random;

/**
 * 单次推演：从当前快照出发，把剩余比赛用极简模型跑一遍。
 *
 * <p>这里刻意不碰 {@code Car} / {@code RaceSimulator}，只用局部数组重算一遍物理，
 * 原因是：
 * <ul>
 *   <li>要能在后台线程反复跑几万次，必须没有对象分配负担；</li>
 *   <li>绝对不能修改真实比赛的状态。</li>
 * </ul>
 * 圈速公式与 {@code RaceSimulator.computeLapTime} 保持一致，改一处要同步改另一处。</p>
 */
public final class Rollout {

    /**
     * 一次推演的结果。
     *
     * @param position    我方的完赛名次
     * @param gapToWinner 落后冠军的秒数
     */
    public record Result(int position, double gapToWinner) {
    }

    private Rollout() {
        throw new AssertionError("工具类不允许实例化");
    }

    /**
     * 跑一次推演。
     *
     * @param snapshot  冻结的赛场状态
     * @param myNumber  我要评估的那辆车
     * @param candidate 本圈让我做的动作（null 表示不进站）
     * @param random    随机源（天气、噪声）
     */
    public static Result run(RaceSnapshot snapshot, int myNumber,
                             PitDecision candidate, Random random) {
        int count = snapshot.cars().size();
        double[] time = new double[count];
        double[] wear = new double[count];
        double[] temp = new double[count];
        double[] fuel = new double[count];
        TireCompound[] tire = new TireCompound[count];
        int[] stint = new int[count];
        // 这套胎已经跑了多少圈——磨损公式用的是它，不是段号
        int[] stintLaps = new int[count];
        boolean[] retired = new boolean[count];
        int[] number = new int[count];
        double[] pace = new double[count];
        double[] aggression = new double[count];
        int myIndex = -1;

        Map<String, TireCompound> catalog = snapshot.tires();
        for (int i = 0; i < count; i++) {
            RaceSnapshot.CarSnapshot car = snapshot.cars().get(i);
            number[i] = car.number();
            time[i] = car.totalTime();
            wear[i] = car.tireWear();
            temp[i] = car.tireTemp();
            fuel[i] = car.fuelKg();
            tire[i] = car.tire(catalog);
            stint[i] = car.stint();
            stintLaps[i] = car.stintLaps();
            retired[i] = car.retired();
            pace[i] = car.paceOffset();
            aggression[i] = car.aggression();
            if (car.number() == myNumber) {
                myIndex = i;
            }
        }
        if (myIndex < 0 || retired[myIndex]) {
            return new Result(count, 0);
        }

        // 立刻执行候选动作（进站发生在本圈起跑前）
        if (candidate != null) {
            time[myIndex] += snapshot.pitCost(candidate.fuelToAdd(), candidate.changesTires());
            if (candidate.changesTires()) {
                tire[myIndex] = candidate.compound();
                wear[myIndex] = 0;
                temp[myIndex] = 55;
                stint[myIndex]++;
                stintLaps[myIndex] = 0;   // 换新胎，段内圈数从 0 开始
            }
            fuel[myIndex] = Math.min(130, fuel[myIndex] + candidate.fuelToAdd());
        }

        WeatherModel weatherModel = new WeatherModel(random, true);
        Weather weather = snapshot.weather();
        int remaining = snapshot.remainingLaps();
        double trackTemp = snapshot.track().trackTempCelsius();
        double fuelPerLap = snapshot.track().fuelPerLap();

        for (int lap = 0; lap < remaining; lap++) {
            weather = weatherModel.next(weather);

            for (int i = 0; i < count; i++) {
                if (retired[i]) {
                    continue;
                }
                if (fuel[i] <= 0.01) {
                    retired[i] = true;
                    continue;
                }
                double lapTime = lapTime(snapshot, tire[i], wear[i], fuel[i], temp[i],
                        pace[i], aggression[i], weather);
                time[i] += lapTime;

                // 对手模型：磨到 65% 或油量不够跑 6 圈就进站（贴近真实策略，
                // 否则推演里所有人都在死撑，AI 会误以为"不进站也不亏"）
                int lapsLeft = remaining - lap;
                if (wear[i] > 0.65 || fuel[i] < fuelPerLap * 6) {
                    if (lapsLeft > 2) {
                        TireCompound next = chooseTire(catalog, weather, lapsLeft);
                        double addFuel = Math.min(120, fuelPerLap * lapsLeft * 1.05 + 2);
                        // 其他车按正常换胎进站估算
                        time[i] += snapshot.pitCost(addFuel, true);
                        tire[i] = next;
                        wear[i] = 0;
                        temp[i] = 55;
                        fuel[i] = Math.min(130, fuel[i] + addFuel);
                        stint[i]++;
                        stintLaps[i] = 0;   // 换新胎：段内圈数归零
                    }
                }

                // 与 Car.advanceLap 完全一致的顺序：先累加段内圈数，再算磨损
                stintLaps[i]++;
                updateTire(snapshot, i, tire[i], wear, temp, aggression, weather, trackTemp,
                        stintLaps);
                fuel[i] = Math.max(0, fuel[i] - fuelPerLap * (1 + (aggression[i] - 0.90) * 0.6));
            }
        }

        // 名次与差距
        int position = 1;
        double myTime = time[myIndex];
        double best = myTime;
        for (int i = 0; i < count; i++) {
            if (i == myIndex || retired[i]) {
                continue;
            }
            if (time[i] < myTime) {
                position++;
            }
            best = Math.min(best, time[i]);
        }
        return new Result(position, myTime - best);
    }

    // ------------------------------------------------------------------

    private static double lapTime(RaceSnapshot snapshot, TireCompound tire, double wear,
                                  double fuel, double temp, double pace, double aggression,
                                  Weather weather) {
        double time = snapshot.track().baseLapSeconds()
                + weather.basePenalty()
                + tire.timeOffset(weather)
                + tire.wearPenalty(wear)
                + fuel * snapshot.fuelTimePerKg()
                + Math.max(0, Math.abs(temp - tire.optimalTemp()) - 12) * 0.06
                + pace
                + (0.88 - aggression) * 4.0;
        return Math.max(snapshot.track().baseLapSeconds() * 0.85, time);
    }

    private static void updateTire(RaceSnapshot snapshot, int index, TireCompound tire,
                                   double[] wear, double[] temp, double[] aggression,
                                   Weather weather, double trackTemp, int[] stintLaps) {
        double target = trackTemp + 45 + aggression[index] * 20;
        temp[index] += (target - temp[index]) * 0.42;
        temp[index] += wear[index] * 4;

        double aggressionFactor = 1 + (aggression[index] - 0.90) * 1.6;
        double overheat = 1 + Math.max(0, (temp[index] - tire.optimalTemp()) / 30.0);
        double delta = tire.wearPerLap() * aggressionFactor * overheat
                * weather.wearMultiplier() * snapshot.tireWearScale()
                * (1 + stintLaps[index] * 0.004);
        wear[index] = Math.min(1.25, wear[index] + delta);
    }

    /** 挑一条适合当前天气、又撑得住剩余里程的轮胎。 */
    private static TireCompound chooseTire(Map<String, TireCompound> catalog,
                                           Weather weather, int lapsLeft) {
        TireCompound best = null;
        double bestScore = Double.MAX_VALUE;
        for (TireCompound compound : catalog.values()) {
            double score = compound.timeOffset(weather);
            int life = compound.estimatedLifeLaps();
            if (life < lapsLeft) {
                score += (lapsLeft - life) * 0.25;
            }
            if (score < bestScore) {
                bestScore = score;
                best = compound;
            }
        }
        return best == null ? catalog.values().iterator().next() : best;
    }
}
