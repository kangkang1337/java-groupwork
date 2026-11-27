package com.endurance.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 一场比赛的完整配置：赛道、参赛车、圈数、随机种子、进站代价等。
 *
 * <p>用建造者模式组装，避免构造器参数爆炸。</p>
 */
public class RaceConfig {

    private final Track track;
    private final List<Car> cars;
    private final int totalLaps;
    private final long seed;
    private final Weather initialWeather;

    /** 进站通道损失（匀速通过维修区的时间）。 */
    private final double pitLaneLoss;
    /** 换胎耗时。 */
    private final double tireChangeCost;
    /** 每加 1kg 油的耗时。 */
    private final double refuelSecondsPerKg;
    /** 燃油影响：每 kg 油带来的圈速影响（秒）。 */
    private final double fuelTimePerKg;
    /** 轮胎磨损倍率（平衡性参数，1.0 为基准）。 */
    private final double tireWearScale;
    /** 是否启用天气变化（平衡性实验时关掉，排除随机干扰）。 */
    private final boolean weatherEnabled;
    /** 是否启用安全车。 */
    private final boolean safetyCarEnabled;
    /** 每圈触发安全车的概率。 */
    private final double safetyCarChance;
    /** 安全车出动后维持的圈数。 */
    private final int safetyCarLaps;

    private RaceConfig(Builder builder) {
        this.track = builder.track;
        this.cars = new ArrayList<>(builder.cars);
        this.totalLaps = builder.totalLaps;
        this.seed = builder.seed;
        this.initialWeather = builder.initialWeather;
        this.pitLaneLoss = builder.pitLaneLoss;
        this.tireChangeCost = builder.tireChangeCost;
        this.refuelSecondsPerKg = builder.refuelSecondsPerKg;
        this.fuelTimePerKg = builder.fuelTimePerKg;
        this.tireWearScale = builder.tireWearScale;
        this.weatherEnabled = builder.weatherEnabled;
        this.safetyCarEnabled = builder.safetyCarEnabled;
        this.safetyCarChance = builder.safetyCarChance;
        this.safetyCarLaps = builder.safetyCarLaps;
    }

    public static Builder builder(Track track) {
        return new Builder(track);
    }

    public Track track() {
        return track;
    }

    public List<Car> cars() {
        return cars;
    }

    public int totalLaps() {
        return totalLaps;
    }

    public long seed() {
        return seed;
    }

    public Weather initialWeather() {
        return initialWeather;
    }

    public double pitLaneLoss() {
        return pitLaneLoss;
    }

    public double tireChangeCost() {
        return tireChangeCost;
    }

    public double refuelSecondsPerKg() {
        return refuelSecondsPerKg;
    }

    public double fuelTimePerKg() {
        return fuelTimePerKg;
    }

    public double tireWearScale() {
        return tireWearScale;
    }

    public boolean weatherEnabled() {
        return weatherEnabled;
    }

    public boolean safetyCarEnabled() {
        return safetyCarEnabled;
    }

    /** 每圈触发安全车的概率。 */
    public double safetyCarChance() {
        return safetyCarChance;
    }

    /** 安全车出动后维持的圈数。 */
    public int safetyCarLaps() {
        return safetyCarLaps;
    }

    /** 一次进站的总耗时。 */
    public double pitStopCost(double fuelAdded) {
        return pitLaneLoss + tireChangeCost + fuelAdded * refuelSecondsPerKg;
    }

    /** 进站耗时：不换胎时省掉换胎那两三秒。 */
    public double pitStopCost(double fuelAdded, boolean changeTires) {
        return pitLaneLoss + (changeTires ? tireChangeCost : 0)
                + fuelAdded * refuelSecondsPerKg;
    }

    // ------------------------------------------------------------------

    public static final class Builder {
        private final Track track;
        private final List<Car> cars = new ArrayList<>();
        private int totalLaps = 50;
        private long seed = 20260101L;
        private Weather initialWeather = Weather.DRY;
        private double pitLaneLoss = 20.0;
        private double tireChangeCost = 2.8;
        private double refuelSecondsPerKg = 0.115;
        private double fuelTimePerKg = 0.035;
        private double tireWearScale = 1.0;
        private boolean weatherEnabled = true;
        private boolean safetyCarEnabled = true;
        // 每圈 1.6%：63 圈约 1 次安全车，对齐真实耐力赛的 1~3 次/6 小时
        private double safetyCarChance = 0.016;
        private int safetyCarLaps = 3;

        private Builder(Track track) {
            this.track = track;
        }

        public Builder laps(int laps) {
            this.totalLaps = laps;
            return this;
        }

        public Builder seed(long seed) {
            this.seed = seed;
            return this;
        }

        public Builder weather(Weather weather) {
            this.initialWeather = weather;
            return this;
        }

        public Builder addCar(Car car) {
            this.cars.add(car);
            return this;
        }

        public Builder pitLaneLoss(double seconds) {
            this.pitLaneLoss = seconds;
            return this;
        }

        public Builder tireWearScale(double scale) {
            this.tireWearScale = scale;
            return this;
        }

        public Builder weatherEnabled(boolean enabled) {
            this.weatherEnabled = enabled;
            return this;
        }

        public Builder safetyCar(boolean enabled, double chance, int laps) {
            this.safetyCarEnabled = enabled;
            this.safetyCarChance = chance;
            this.safetyCarLaps = laps;
            return this;
        }

        public RaceConfig build() {
            if (cars.isEmpty()) {
                throw new IllegalStateException("比赛至少要有一辆车");
            }
            if (totalLaps <= 0) {
                throw new IllegalStateException("圈数必须为正数");
            }
            return new RaceConfig(this);
        }
    }
}
