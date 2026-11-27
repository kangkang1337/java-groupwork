package com.endurance.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 一辆参赛车（也是车队策略的最小载体）。
 *
 * <p>持有胎况、油量、累计时间等全部比赛状态。仿真器只调用这里的
 * {@code advanceLap} / {@code applyPitStop}，不直接改字段，
 * 保证状态变化的入口唯一。</p>
 */
public class Car {

    /** 油箱容量上限（kg）。 */
    public static final double MAX_FUEL_KG = 130;

    private final int number;
    private final Team team;
    private final String driverName;

    private TireCompound compound;
    private double tireWear;
    private double tireTemp;
    private double fuelKg;

    /** 驾驶激进度：0.60 保守省胎，1.00 全力push。 */
    private double aggression = 0.90;

    private double totalTime;
    private double lastLapTime;
    private double bestLapTime;
    private int pitStops;
    private int stintLaps;
    /** 当前处于第几段轮胎周期（0 表示第一段）。 */
    private int stint;
    /** 累计被判罚的秒数。 */
    private double penaltySeconds;
    /** 超出赛道限制的次数（黑白旗警告计数）。 */
    private int trackLimitStrikes;
    private boolean retired;
    private String retireReason;

    private final List<Double> lapTimes = new ArrayList<>();

    /** 开赛时的轮胎与油量：比赛结束后复盘要用它当基准，不能用被改过的当前值。 */
    private final TireCompound initialCompound;
    private final double initialFuelKg;

    public Car(int number, Team team, String driverName, TireCompound startCompound, double startFuelKg) {
        this.number = number;
        this.initialCompound = startCompound;
        this.initialFuelKg = startFuelKg;
        this.team = team;
        this.driverName = driverName;
        this.compound = startCompound;
        this.fuelKg = startFuelKg;
        this.tireTemp = 40; // 冷胎起步
        this.bestLapTime = Double.MAX_VALUE;
    }

    /** 起步轮胎（比赛全程不变）。 */
    public TireCompound initialCompound() {
        return initialCompound;
    }

    /** 起步油量（比赛全程不变）。 */
    public double initialFuelKg() {
        return initialFuelKg;
    }

    // ------------------------------------------------------------------
    // 状态推进
    // ------------------------------------------------------------------

    /**
     * 记录一圈的结果并推进轮胎 / 油量状态。
     *
     * @param lapTime    本圈用时（秒）
     * @param trackTemp  赛道温度
     * @param weather    当前天气
     * @param fuelPerLap 该赛道每圈油耗（kg）
     */
    public void advanceLap(double lapTime, double trackTemp, Weather weather, double fuelPerLap, double wearScale) {
        this.lastLapTime = lapTime;
        this.totalTime += lapTime;
        this.lapTimes.add(lapTime);
        this.bestLapTime = Math.min(bestLapTime, lapTime);
        this.stintLaps++;

        updateTireTemperature(trackTemp);
        updateTireWear(weather, wearScale);
        updateFuel(fuelPerLap);
    }

    private void updateTireTemperature(double trackTemp) {
        // 一阶惯性：胎温朝"赛道温度 + 驾驶强度带来的升温"靠拢
        double target = trackTemp + 45 + aggression * 20;
        tireTemp += (target - tireTemp) * 0.42;
        // 磨损后散热变差，温度略高
        tireTemp += tireWear * 4;
    }

    private void updateTireWear(Weather weather, double wearScale) {
        double aggressionFactor = 1 + (aggression - 0.90) * 1.6;
        double overheatFactor = 1 + Math.max(0, (tireTemp - compound.optimalTemp()) / 30.0);
        double delta = compound.wearPerLap() * aggressionFactor * overheatFactor
                * weather.wearMultiplier() * (1 + stintLaps * 0.004);
        tireWear = Math.min(1.25, tireWear + delta * wearScale);
    }

    private void updateFuel(double fuelPerLap) {
        double consumption = fuelPerLap * (1 + (aggression - 0.90) * 0.6);
        fuelKg = Math.max(0, fuelKg - consumption);
    }

    /** 进站：加时间、换胎、加油。 */
    /**
     * 进站。
     *
     * @param newCompound 新配方；{@code null} 表示只加油不换胎——磨损和胎温原样保留
     */
    public void applyPitStop(double timeLost, TireCompound newCompound, double fuelAdded) {
        this.totalTime += timeLost;
        if (newCompound != null) {
            this.compound = newCompound;
            this.tireWear = 0;
            this.tireTemp = 55;
            this.stintLaps = 0;
            this.stint++;
        }
        // 不换胎时 stintLaps 继续累加：还是同一套胎，磨损接着走
        this.fuelKg = Math.min(fuelKg + fuelAdded, MAX_FUEL_KG);
        this.pitStops++;
    }

    /** 直接改写累计时间（安全车压缩差距用）。 */
    public void restoreTotalTime(double time) {
        this.totalTime = Math.max(0, time);
    }

    /** 安全车帮着抹掉的秒数（复盘时要把最优时间也抵扣掉，否则没法比较）。 */
    private double safetyCarCredit;

    public double safetyCarCredit() {
        return safetyCarCredit;
    }

    /** 安全车压缩差距时记账，正数表示"白赚"的秒数。 */
    public void addSafetyCarCredit(double seconds) {
        this.safetyCarCredit += seconds;
    }

    /** 判罚：直接把罚时加进总时间。 */
    public void addPenalty(double seconds) {
        this.totalTime += seconds;
        this.penaltySeconds += seconds;
    }

    /** 累计罚时。 */
    public double penaltySeconds() {
        return penaltySeconds;
    }

    /** 超出赛道限制的次数。 */
    public int trackLimitStrikes() {
        return trackLimitStrikes;
    }

    public void addTrackLimitStrike() {
        this.trackLimitStrikes++;
    }

    /** 从存档恢复状态（读档用）。 */
    public void restore(double totalTime, double lastLapTime, double bestLapTime,
                        TireCompound compound, double tireWear, double tireTemp,
                        double fuelKg, int pitStops, int stint, int stintLaps,
                        boolean retired, String retireReason) {
        this.totalTime = totalTime;
        this.lastLapTime = lastLapTime;
        this.bestLapTime = bestLapTime;
        this.compound = compound;
        this.tireWear = tireWear;
        this.tireTemp = tireTemp;
        this.fuelKg = fuelKg;
        this.pitStops = pitStops;
        this.stint = stint;
        this.stintLaps = stintLaps;
        this.retired = retired;
        this.retireReason = retireReason;
        this.lapTimes.clear();
    }

    /** 退赛。 */
    public void retire(String reason) {
        this.retired = true;
        this.retireReason = reason;
    }

    // ------------------------------------------------------------------

    public int number() {
        return number;
    }

    public Team team() {
        return team;
    }

    public String driverName() {
        return driverName;
    }

    public TireCompound compound() {
        return compound;
    }

    /** 所属组别（跟随车队）。 */
    public RaceCategory category() {
        return team.category();
    }

    public double tireWear() {
        return tireWear;
    }

    public double tireTemp() {
        return tireTemp;
    }

    public double fuelKg() {
        return fuelKg;
    }

    public void setFuelKg(double fuelKg) {
        this.fuelKg = Math.max(0, fuelKg);
    }

    public double aggression() {
        return aggression;
    }

    public void setAggression(double aggression) {
        this.aggression = Math.max(0.60, Math.min(1.0, aggression));
    }

    public double totalTime() {
        return totalTime;
    }

    public double lastLapTime() {
        return lastLapTime;
    }

    public double bestLapTime() {
        return bestLapTime;
    }

    public int pitStops() {
        return pitStops;
    }

    public int stintLaps() {
        return stintLaps;
    }

    /** 当前段号（每次进站 +1）。 */
    public int stint() {
        return stint;
    }

    public boolean isRetired() {
        return retired;
    }

    public String retireReason() {
        return retireReason;
    }

    public List<Double> lapTimes() {
        return java.util.Collections.unmodifiableList(lapTimes);
    }

    public String displayName() {
        return "#" + number + " " + team.shortName() + " " + driverName;
    }

    @Override
    public String toString() {
        return displayName();
    }
}
