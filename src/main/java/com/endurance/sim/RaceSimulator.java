package com.endurance.sim;

import com.endurance.model.Car;
import com.endurance.model.RaceConfig;
import com.endurance.model.Track;
import com.endurance.model.Weather;
import com.endurance.strategy.Strategy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 比赛仿真器：逐圈推进，负责圈速计算、状态更新、事件生成与名次排列。
 *
 * <p>拆成"单步推进"（{@link #step()}）而不是一口气跑完，是因为界面需要每圈
 * 刷新一次画面、玩家需要在两圈之间下指令、联机时服务器也要按圈广播状态。
 * {@link #run()} 只是把 step 循环到底的便捷写法。</p>
 *
 * <p>设计上把"决策"完全外包给 {@link Strategy}，仿真器只做两件事：
 * 按当前赛场状况算出每圈用时，然后把状态推进一圈。</p>
 */
public class RaceSimulator {

    private final RaceConfig config;
    private final Map<Car, Strategy> strategies = new HashMap<>();
    private final Random random;
    private final WeatherModel weatherModel;
    private final List<RaceEvent> events = new ArrayList<>();
    private final List<LapResult> lapResults = new ArrayList<>();
    private final List<PitRecord> pitRecords = new ArrayList<>();
    /** 上一圈结束时各车的位置，用来判黄旗超车。 */
    private final Map<Car, Integer> lastPositions = new HashMap<>();
    /** 本圈进站的车（进站导致的名次变化不算超车）。 */
    private final java.util.Set<Car> pittedThisLap = new java.util.HashSet<>();

    private Weather weather;
    private Weather previousWeather;
    private int completedLaps;
    /** 安全车剩余圈数，0 表示没有安全车。 */
    private int safetyCarRemaining;

    /**
     * 本圈是否处于安全车状态。
     *
     * <p>安全车时长必须按「整圈」计：本圈开始定好，圈速 / 磨损 / 进站折扣 / 差距压缩
     * 全部用它，圈末才递减剩余圈数。原来先减再判，会多压缩一圈、少播一层通知。</p>
     */
    private boolean safetyCarThisLap;

    /** 本圈开始时生效的旗语（圈末事故不影响本圈判定）。 */
    private com.endurance.model.RaceFlag lapFlag = com.endurance.model.RaceFlag.GREEN;
    /** 当前赛道旗语。 */
    private com.endurance.model.RaceFlag flag = com.endurance.model.RaceFlag.GREEN;
    /** 非绿旗还要持续几圈。 */
    private int flagLaps;
    /** 红旗剩余圈数（比赛中止，车辆不动）。 */
    private int redFlagLaps;
    private boolean finished;
    private RaceResult result;

    public RaceSimulator(RaceConfig config, Map<Car, Strategy> strategies) {
        this.config = config;
        this.strategies.putAll(strategies);
        this.random = new Random(config.seed());
        this.weatherModel = new WeatherModel(random, config.weatherEnabled());
        this.weather = config.initialWeather();
        this.previousWeather = this.weather;
    }

    // ------------------------------------------------------------------
    // 推进
    // ------------------------------------------------------------------

    /**
     * 推进一圈。
     *
     * @return 是否真的推进了（比赛已结束时返回 {@code false}）
     */
    public boolean step() {
        if (finished) {
            return false;
        }
        int lap = completedLaps + 1;
        Track track = config.track();

        // 红旗期间比赛暂停：赛车停在赛道上，不推进圈数、不产生逐圈数据
        if (redFlagLaps > 0) {
            redFlagLaps--;
            flag = redFlagLaps > 0 ? com.endurance.model.RaceFlag.RED
                    : com.endurance.model.RaceFlag.GREEN;
            if (redFlagLaps == 0) {
                events.add(new RaceEvent(lap, RaceEvent.Type.FLAG, "红旗解除，比赛重启"));
            }
            // 注意：这里不能写 completedLaps = lap —— 车一圈都没跑，
            // 否则比赛会在实际圈数不足 totalLaps 时提前结束
            return true;
        }

        // 0) 安全车状态 → 再定旗语（顺序不能反，否则安全车那一圈还显示绿旗）
        updateSafetyCar(lap);
        updateFlag(lap);

        // 本圈生效的旗语就此固定：圈末发生的事故属于下一圈，
        // 不能反过来追溯处罚本圈已经完成的超车
        lapFlag = flag;

        // 1) 天气
        Weather next = weatherModel.next(weather);
        if (next != previousWeather) {
            events.add(new RaceEvent(lap, RaceEvent.Type.WEATHER_CHANGE,
                    "天气由「" + previousWeather.displayName() + "」变为「" + next.displayName() + "」"));
            previousWeather = next;
        }
        weather = next;

        pittedThisLap.clear();

        RaceContext context = RaceContext.of(lap, config.totalLaps(), weather, track, config.cars());

        // 2) 策略决策（进站发生在本圈起跑前）
        applyPitStops(context);

        // 3) 逐车跑圈
        Map<Car, Double> lapTimes = new HashMap<>();
        for (Car car : config.cars()) {
            if (car.isRetired()) {
                continue;
            }
            if (car.fuelKg() <= 0.01) {
                car.retire("燃油耗尽");
                events.add(new RaceEvent(lap, RaceEvent.Type.FUEL_EMPTY,
                        car.displayName() + " 燃油耗尽，退出比赛"));
                continue;
            }
            double time = computeLapTime(car, context);
            car.advanceLap(time, track.trackTempCelsius(), weather, track.fuelPerLap(),
                    config.tireWearScale() * (safetyCarThisLap ? 0.15 : 1.0));
            lapTimes.put(car, time);
        }

        // 4) 撞车与机械故障
        rollCrash(lap);
        rollMechanicalFailures(lap, lapTimes);
        rollTrackLimits(lap);

        // 5) 名次与逐圈数据
        List<Car> order = sortedCars();
        checkYellowOvertake(lap, new java.util.HashMap<>(lastPositions),
                new java.util.HashMap<>(lapTimes), pittedThisLap);
        lastPositions.clear();
        for (int i = 0; i < order.size(); i++) {
            lastPositions.put(order.get(i), i + 1);
        }
        for (int i = 0; i < order.size(); i++) {
            Car car = order.get(i);
            Double time = lapTimes.get(car);
            if (time == null) {
                continue;
            }
            lapResults.add(new LapResult(lap, car.number(), time, i + 1,
                    car.compound(), car.tireWear(), car.tireTemp(), car.fuelKg(), weather,
                    car.stint(), safetyCarThisLap));
        }

        // 圈末结算安全车剩余圈数：本圈已经完整跑完，才算消耗掉一圈安全车
        endSafetyCarLap(lap);

        completedLaps = lap;
        if (completedLaps >= config.totalLaps()) {
            finishRace();
        }
        return true;
    }

    /** 一直跑到终点。 */
    public RaceResult run() {
        while (step()) {
            // 交给 step 推进
        }
        return result;
    }

    private void finishRace() {
        finished = true;
        for (Car car : config.cars()) {
            if (!car.isRetired()) {
                events.add(new RaceEvent(config.totalLaps(), RaceEvent.Type.FINISH,
                        car.displayName() + " 冲线"));
            }
        }
        result = buildResult();
    }

    // ------------------------------------------------------------------
    // 各环节
    // ------------------------------------------------------------------

    /** 旗语推进：撞车 → 黄旗；安全车 → FCY；其余时候回绿。 */
    private void updateFlag(int lap) {
        if (flagLaps > 0) {
            flagLaps--;
            return;
        }
        if (safetyCarThisLap) {
            flag = com.endurance.model.RaceFlag.FCY;
            return;
        }
        if (flag != com.endurance.model.RaceFlag.GREEN) {
            // 只有黄旗解除才值得播报；安全车回去已经由 updateSafetyCar 播过了
            boolean fromYellow = flag == com.endurance.model.RaceFlag.YELLOW
                    || flag == com.endurance.model.RaceFlag.DOUBLE_YELLOW;
            flag = com.endurance.model.RaceFlag.GREEN;
            flagLaps = 0;
            if (fromYellow) {
                events.add(new RaceEvent(lap, RaceEvent.Type.FLAG, "黄旗解除，赛道恢复绿旗"));
            }
        }
    }

    /** 撞车：让一台车退赛，并给出黄旗（小概率升级为红旗）。 */
    private void rollCrash(int lap) {
        // 真实比赛里职业车手很少撞车：全场每圈 0.4%，63 圈约 0.25 次/场
        if (config.cars().size() < 3 || random.nextDouble() > 0.004) {
            return;
        }
        java.util.List<Car> running = sortedCars();
        if (running.size() < 3) {
            return;
        }
        Car victim = running.get(random.nextInt(running.size()));
        victim.retire("撞车");
        boolean serious = random.nextDouble() < 0.25;
        if (serious) {
            flag = com.endurance.model.RaceFlag.RED;
            redFlagLaps = 2;
            events.add(new RaceEvent(lap, RaceEvent.Type.CRASH,
                    victim.displayName() + " 严重撞车，红旗中止比赛"));
            events.add(new RaceEvent(lap, RaceEvent.Type.FLAG, "红旗：比赛中止，等待重启"));
        } else {
            flag = com.endurance.model.RaceFlag.YELLOW;
            flagLaps = 2;
            events.add(new RaceEvent(lap, RaceEvent.Type.CRASH,
                    victim.displayName() + " 撞车退赛，赛段黄旗"));
            events.add(new RaceEvent(lap, RaceEvent.Type.FLAG, "黄旗：禁止超车"));
        }
    }

    /** 安全车：随机出动，维持若干圈后收起。 */
    /**
     * 圈开始时确定本圈的安全车状态，并做差距压缩。
     *
     * <p>顺序很关键：先判定、再压缩，剩余圈数留到 {@link #endSafetyCarLap} 才递减。
     * 原来先递减再判，会让安全车结束后的那一圈仍然压缩差距、并且减速少一圈。</p>
     */
    private void updateSafetyCar(int lap) {
        safetyCarThisLap = false;
        if (!config.safetyCarEnabled()) {
            return;
        }
        // 本圈是否延续上一圈的安全车
        if (safetyCarRemaining > 0) {
            safetyCarThisLap = true;
        } else if (lap < config.totalLaps() - 2
                && random.nextDouble() < config.safetyCarChance()) {
            // 本圈新触发：从这一圈就开始减速
            safetyCarRemaining = config.safetyCarLaps();
            safetyCarThisLap = true;
            events.add(new RaceEvent(lap, RaceEvent.Type.SAFETY_CAR,
                    "安全车出动！全场减速，进站代价降低"));
        }
        if (safetyCarThisLap) {
            compressFieldUnderSafetyCar();
        }
    }

    /** 圈末结算：安全车跑完一圈才扣剩余圈数。 */
    private void endSafetyCarLap(int lap) {
        if (!safetyCarThisLap || safetyCarRemaining <= 0) {
            return;
        }
        safetyCarRemaining--;
        if (safetyCarRemaining == 0) {
            events.add(new RaceEvent(lap, RaceEvent.Type.SAFETY_CAR, "安全车回站，比赛恢复"));
        }
    }

    private void applyPitStops(RaceContext context) {        for (Car car : config.cars()) {
            if (car.isRetired()) {
                continue;
            }
            Strategy strategy = strategies.get(car);
            if (strategy == null) {
                continue;
            }
            PitDecision decision = strategy.decide(context, car);
            if (decision == null) {
                continue;
            }
            // 油箱装不下的部分不该计费：按"实际加进去的油"算进站耗时
            double space = Math.max(0, Car.MAX_FUEL_KG - car.fuelKg());
            double acceptedFuel = Math.min(decision.fuelToAdd(), space);
            double lost = config.pitStopCost(acceptedFuel, decision.changesTires());
            if (safetyCarThisLap) {
                // 安全车期间全场都慢，进站相对"便宜"
                lost *= 0.35;
            }
            car.applyPitStop(lost, decision.compound(), acceptedFuel);
            pittedThisLap.add(car);
            pitRecords.add(new PitRecord(context.lap(), car.number(),
                    decision.changesTires() ? decision.compound().id() : car.compound().id(),
                    acceptedFuel, lost, decision.changesTires()));
            String fuelNote = acceptedFuel + 0.5 < decision.fuelToAdd()
                    ? String.format("（油箱只装得下 %.0fkg）", acceptedFuel) : "";
            events.add(new RaceEvent(context.lap(), RaceEvent.Type.PIT_STOP,
                    String.format("%s 进站：%s%s，耗时 %.1fs",
                            car.displayName(), decision.describe(), fuelNote, lost)));
        }
    }

    private void rollMechanicalFailures(int lap, Map<Car, Double> lapTimes) {
        List<Car> failed = new ArrayList<>();
        // 必须按 config.cars() 的固定顺序遍历：
        // lapTimes 是 HashMap，而 Car 没重写 hashCode，遍历顺序依赖对象身份，
        // 同一 seed 的两场比赛可能把随机数分配给不同车，退赛名单就对不上了
        for (Car car : config.cars()) {
            if (!lapTimes.containsKey(car)) {
                continue;
            }
            double chance = (1 - car.team().reliability()) * 0.01;
            if (random.nextDouble() < chance) {
                failed.add(car);
            }
        }
        for (Car car : failed) {
            car.retire("机械故障");
            events.add(new RaceEvent(lap, RaceEvent.Type.MECHANICAL_FAILURE,
                    car.displayName() + " 遭遇机械故障，退赛"));
        }
    }

    /**
     * 单圈用时模型。
     *
     * <p>所有因素都是加在"理想基准圈"上的修正项，便于调参：
     * 天气 + 轮胎配方 + 磨损 + 油重 + 胎温 + 车队实力 + 驾驶强度 + 交通。</p>
     */
    private double computeLapTime(Car car, RaceContext context) {
        Track track = config.track();
        if (safetyCarThisLap) {
            // 安全车带队：全场同速，磨损也几乎为零
            return track.baseLapSeconds() * 1.55 + random.nextGaussian() * 0.4;
        }
        double time = track.baseLapSeconds()
                + weather.basePenalty()
                + car.compound().timeOffset(weather)
                + car.compound().wearPenalty(car.tireWear())
                + car.fuelKg() * config.fuelTimePerKg()
                + temperaturePenalty(car)
                + car.team().paceOffset()
                + (0.88 - car.aggression()) * 4.0
                + trafficPenalty(car, context);

        // 单圈波动：真车一圈的自然起伏（交通、失误、风向）大约在 ±1 秒量级，
        // 原来只给 0.12 秒会让全场圈速过分整齐，和真实比赛的分布差得更远
        time += random.nextGaussian() * 0.9;
        return Math.max(track.baseLapSeconds() * 0.85, time);
    }

    /** 胎温偏离最佳窗口带来的掉速。 */
    private double temperaturePenalty(Car car) {
        double optimal = car.compound().optimalTemp();
        double deviation = Math.abs(car.tireTemp() - optimal);
        return Math.max(0, deviation - 12) * 0.06;
    }

    /** 跟车太近会损失下压力与速度。 */
    private double trafficPenalty(Car car, RaceContext context) {
        double gap = context.gapAhead(car);
        if (!Double.isFinite(gap) || gap >= 1.5) {
            return 0;
        }
        return (1.5 - Math.max(0, gap)) * 0.25;
    }

    /** 在跑的车按累计时间排序。 */
    public List<Car> sortedCars() {
        List<Car> order = new ArrayList<>();
        for (Car car : config.cars()) {
            if (!car.isRetired()) {
                order.add(car);
            }
        }
        order.sort(Comparator.comparingDouble(Car::totalTime));
        return order;
    }

    /** 完整名次（含退赛车，排在最后）。 */
    public List<Car> liveStandings() {
        List<Car> order = sortedCars();
        for (Car car : config.cars()) {
            if (car.isRetired()) {
                order.add(car);
            }
        }
        return order;
    }

    private RaceResult buildResult() {
        List<Car> running = sortedCars();
        List<Car> retired = new ArrayList<>();
        for (Car car : config.cars()) {
            if (car.isRetired()) {
                retired.add(car);
            }
        }

        List<RaceResult.Entry> standings = new ArrayList<>();
        double winnerTime = running.isEmpty() ? 0 : running.get(0).totalTime();
        int position = 1;
        // 组别名次单独数：同一场比赛里 Hypercar 和 LMGT3 各算各的
        java.util.Map<com.endurance.model.RaceCategory, Integer> classCounter =
                new java.util.EnumMap<>(com.endurance.model.RaceCategory.class);
        for (Car car : running) {
            int classPosition = classCounter.merge(car.category(), 1, Integer::sum);
            standings.add(new RaceResult.Entry(position++, car, car.totalTime(),
                    car.totalTime() - winnerTime, false, classPosition));
        }
        for (Car car : retired) {
            standings.add(new RaceResult.Entry(position++, car, car.totalTime(), 0, true,
                    classCounter.merge(car.category(), 1, Integer::sum)));
        }
        return new RaceResult(config.track(), config.totalLaps(), standings,
                events, lapResults, weather, pitRecords);
    }

    // ------------------------------------------------------------------

    public RaceConfig config() {
        return config;
    }

    /**
     * 从存档恢复到某一圈（读档用）。
     *
     * <p>车辆的胎况、油量等由调用方先恢复到 {@code Car} 上，这里只重置
     * 仿真器自己的进度指针。</p>
     */
    public void rewindTo(int completedLaps, Weather weather, int safetyCarRemaining) {
        this.completedLaps = Math.max(0, Math.min(completedLaps, config.totalLaps()));
        this.weather = weather;
        this.previousWeather = weather;
        this.safetyCarRemaining = Math.max(0, safetyCarRemaining);
        this.finished = false;
        this.result = null;
    }

    /** 已完成圈数。 */
    public int completedLaps() {
        return completedLaps;
    }

    public int totalLaps() {
        return config.totalLaps();
    }

    public boolean isFinished() {
        return finished;
    }

    /** 当前是否处于安全车时段。 */
    public boolean isSafetyCarActive() {
        return safetyCarRemaining > 0;
    }

    public int safetyCarRemaining() {
        return safetyCarRemaining;
    }

    /** 本圈是不是安全车圈（自检用）。 */
    public boolean safetyCarThisLap() {
        return safetyCarThisLap;
    }

    /** 强制进入红旗暂停（自检用）。 */
    public void forceRedFlag(int laps) {
        this.redFlagLaps = Math.max(1, laps);
        this.flag = com.endurance.model.RaceFlag.RED;
    }

    /** 强制出动安全车（自检用）。 */
    public void forceSafetyCar(int laps) {
        this.safetyCarRemaining = laps;
        events.add(new RaceEvent(completedLaps + 1, RaceEvent.Type.SAFETY_CAR,
                "安全车出动！全场减速，进站代价降低"));
    }

    /** 发车：暖胎圈结束（或跳过）时喊一声绿旗。 */
    public void markRaceStart() {
        events.add(new RaceEvent(0, RaceEvent.Type.FLAG, "绿旗：比赛开始"));
    }

    /** 当前赛道旗语（比赛结束后统一显示格子旗）。 */
    public com.endurance.model.RaceFlag currentFlag() {
        return finished ? com.endurance.model.RaceFlag.CHEQUERED : flag;
    }

    /** 红旗还要持续几圈（0 表示没有红旗）。 */
    public int redFlagLaps() {
        return redFlagLaps;
    }

    public Weather currentWeather() {
        return weather;
    }

    public List<Car> cars() {
        return config.cars();
    }

    public List<RaceEvent> events() {
        return events;
    }

    public List<LapResult> lapResults() {
        return lapResults;
    }

    /** 比赛结束后的完整结果，未结束时为 null。 */
    public RaceResult result() {
        return result;
    }

    /** 最近一条事件（用于界面提示）。 */
    public RaceEvent latestEvent() {
        return events.isEmpty() ? null : events.get(events.size() - 1);
    }
/** 气温：赛道温度减去沥青与空气的温差。 */
    public double airTemperature() {
        // 沥青路面通常比空气高 10~12°C
        return config.track().trackTempCelsius() - 11;
    }

    /** 相对湿度：天气越湿越高。 */
    public int humidity() {
        return switch (weather) {
            case WET -> 94;
            case DAMP -> 78;
            default -> 46;
        };
    }

    /** 未来 N 圈内下雨的概率（0~1）。 */
    public double rainChance(int laps) {
        return weatherModel.rainChance(laps);
    }
/** A. 超出赛道限制：激进 + 磨损高更容易冲出去，黑白旗警告 → 5 秒 → 更重。 */
    private void rollTrackLimits(int lap) {
        for (Car car : config.cars()) {
            if (car.isRetired()) {
                continue;
            }
            // 正赛里超限绝大多数只是警告，全场每场约 1~2 次，不是每圈都有人冲出去
            double chance = 0.0003 + car.aggression() * 0.0006 + car.tireWear() * 0.0015;
            if (random.nextDouble() > chance) {
                continue;
            }
            car.addTrackLimitStrike();
            int strikes = car.trackLimitStrikes();
            if (strikes == 1) {
                events.add(new RaceEvent(lap, RaceEvent.Type.PENALTY,
                        car.displayName() + " 超出赛道限制：黑白旗警告"));
            } else if (strikes == 2) {
                car.addPenalty(5);
                events.add(new RaceEvent(lap, RaceEvent.Type.PENALTY,
                        car.displayName() + " 第二次超出赛道限制：罚时 5 秒"));
            } else {
                car.addPenalty(10);
                events.add(new RaceEvent(lap, RaceEvent.Type.PENALTY,
                        car.displayName() + " 多次超出赛道限制：判罚 10 秒"));
            }
        }
    }

    /** C. 黄旗期间禁止超车：黄旗圈里名次上升且没进站，判 5 秒。 */
    private void checkYellowOvertake(int lap, java.util.Map<Car, Integer> previousPositions,
                                     java.util.Map<Car, Double> lapTimes,
                                     java.util.Set<Car> pittedThisLap) {
        // 只在局部黄旗下判定：FCY / 安全车期间全场同速，
        // 名次互换是圈速噪声造成的，不是超车，判罚会误伤一大片
        if (lapFlag != com.endurance.model.RaceFlag.YELLOW) {
            return;
        }
        List<Car> order = sortedCars();
        for (int i = 0; i < order.size(); i++) {
            Car car = order.get(i);
            Integer before = previousPositions.get(car);
            if (before == null || pittedThisLap.contains(car)) {
                continue;
            }
            if (i + 1 >= before) {
                continue;
            }
            // 只认"真超车"：本圈必须比被超的那台车快 2 秒以上，
            // 否则就是圈速噪声导致的名次互换（σ=0.9s，2 秒约 2.2σ）
            Car passed = null;
            for (var entry : previousPositions.entrySet()) {
                if (entry.getValue() == before - 1) {
                    passed = entry.getKey();
                }
            }
            Double mine = lapTimes.get(car);
            Double theirs = passed == null ? null : lapTimes.get(passed);
            if (mine == null || theirs == null || mine > theirs - 2.0) {
                continue;
            }
            car.addPenalty(5);
            events.add(new RaceEvent(lap, RaceEvent.Type.PENALTY,
                    car.displayName() + " 黄旗期间超车：罚时 5 秒"));
        }
    }
/**
     * 安全车把车队"压"回一列。
     *
     * <p>真实比赛里安全车一出，前车必须减速等车队集结，后车就能贴上去——
     * 所以差距是被抹平的，这正是安全车最大的战术价值（也是玩家最需要判断的窗口）。
     * 原来只做了"全场圈速 ×1.55"，差距一点没变，等于白出安全车。</p>
     */
    private void compressFieldUnderSafetyCar() {
        List<Car> running = new ArrayList<>();
        for (Car car : config.cars()) {
            if (!car.isRetired()) {
                running.add(car);
            }
        }
        if (running.size() < 2) {
            return;
        }
        running.sort(java.util.Comparator.comparingDouble(Car::totalTime));
        double leader = running.get(0).totalTime();
        for (int i = 1; i < running.size(); i++) {
            Car car = running.get(i);
            double gap = car.totalTime() - leader;
            // 每圈把差距压掉 35%，三圈安全车后只剩约四分之一
            // 同时记账：复盘时要把"白赚的秒数"从理论最优里抵扣掉，否则没法比
            double credit = gap * 0.35;
            car.addSafetyCarCredit(credit);
            car.restoreTotalTime(leader + gap - credit);
        }
    }
}