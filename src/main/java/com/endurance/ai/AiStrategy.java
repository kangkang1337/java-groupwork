package com.endurance.ai;

import com.endurance.model.Car;
import com.endurance.model.RaceConfig;
import com.endurance.model.TireCompound;
import com.endurance.model.Weather;
import com.endurance.sim.PitDecision;
import com.endurance.sim.RaceContext;
import com.endurance.strategy.Strategy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * AI 车队的策略：在"值得思考的时刻"向规划器要最优动作。
 *
 * <p>两个工程上的关键点：</p>
 * <ul>
 *   <li><b>不每圈都算</b>：只有轮胎快磨平、油快见底、或者天气对不上轮胎时才推演一次，
 *       否则纯属浪费算力（一圈几百次推演没人看）。</li>
 *   <li><b>界面用异步、脚本用同步</b>：界面每圈有近 1 秒真实时间，推演放后台线程算，
 *       算完下一圈用；命令行一秒跑完一场比赛，等不到后台结果，所以直接同步算。</li>
 * </ul>
 */
public class AiStrategy implements Strategy {

    private final int carNumber;
    private final AiPersonality personality;
    private final MonteCarloPlanner planner;
    private final Map<String, TireCompound> tires;
    private final RaceConfig config;
    private final boolean synchronous;

    /** 刚进过站后的冷却圈数，避免"进站完下一圈又进站"这种蠢事。 */
    private int cooldown;
    /** 过期被丢弃的规划数（自检用）。 */
    private int stalePlans;
    /**
     * 后台算完的决策，带来源圈号。
     *
     * <p>异步推演可能晚好几圈才回来。不带圈号的话，第 20 圈算出的结论
     * 会在第 23 圈才被执行——那时赛道状况早就变了。</p>
     */
    private volatile PendingPlan nextDecision;

    /** 一条待执行的规划：来自哪一圈、结论是什么。 */
    private record PendingPlan(int sourceLap, PitDecision decision) {
    }
    private volatile boolean planning;
    private int planCount;

    public AiStrategy(int carNumber, AiPersonality personality, MonteCarloPlanner planner,
                      Map<String, TireCompound> tires, RaceConfig config, boolean synchronous) {
        this.carNumber = carNumber;
        this.personality = personality;
        this.planner = planner;
        this.tires = tires;
        this.config = config;
        this.synchronous = synchronous;
    }

    public AiPersonality personality() {
        return personality;
    }

    /** 已经做了多少次推演（自检用）。 */
    public int planCount() {
        return planCount;
    }

    @Override
    public PitDecision decide(RaceContext context, Car car) {
        PitDecision decision = null;

        if (cooldown > 0) {
            cooldown--;
        } else if (!car.isRetired() && shouldPlan(context, car)) {
            RaceSnapshot snapshot = snapshotOf(context);
            if (snapshot != null) {
                if (synchronous) {
                    planCount++;
                    decision = planner.plan(snapshot, carNumber, personality);
                } else if (!planning) {
                    planCount++;
                    planning = true;
                    // 规划一启动就设冷却：无论结果是进站还是不进站，
                    // 都算"已经想过一次"，不该每圈重复推演
                    cooldown = 3;
                    planner.planAsync(snapshot, carNumber, personality)
                            .whenComplete((result, error) -> {
                                planning = false;
                                if (error == null) {
                                    nextDecision = new PendingPlan(snapshot.lap(), result);
                                }
                            });
                }
            }
        }

        if (!synchronous) {
            PendingPlan pending = nextDecision;
            nextDecision = null;
            if (pending != null) {
                // 超过一圈就算过期：宁可不进站，也不要拿旧判断坑自己
                if (context.lap() - pending.sourceLap() <= 1) {
                    decision = pending.decision();
                } else {
                    stalePlans++;
                }
            }
        }

        // 兜底：推演没跟上且确实无法靠当前油量跑完时，才紧急进站。
        if (decision == null && !car.isRetired() && needsEmergencyPit(context, car)) {
            decision = emergencyPit(context, car);
        }
        if (decision != null) {
            cooldown = 3;
        }
        return decision;
    }

    /** 冷却剩余圈数（存档要带上，否则读档后 AI 会提前重新规划）。 */
    public int cooldown() {
        return cooldown;
    }

    public void restoreCooldown(int value) {
        this.cooldown = Math.max(0, value);
    }

    /** 被判定过期而丢弃的规划次数（自检用）。 */
    public int stalePlans() {
        return stalePlans;
    }

    @Override
    public String name() {
        return "AI-" + personality.displayName();
    }

    // ------------------------------------------------------------------

    /** 什么时候值得算一次：胎快没了、油不够、或者轮胎和天气对不上。 */
    private boolean shouldPlan(RaceContext context, Car car) {
        if (car.tireWear() > 0.55) {
            return true;
        }
        if (car.fuelKg() < context.track().fuelPerLap() * 12) {
            return true;
        }
        return context.weather().isWet()
                && car.compound().timeOffset(context.weather()) > 4.0;
    }

    private RaceSnapshot snapshotOf(RaceContext context) {
        List<RaceSnapshot.CarSnapshot> cars = new ArrayList<>(context.standings().size());
        for (Car car : context.standings()) {
            if (car.isRetired()) {
                continue;
            }
            cars.add(new RaceSnapshot.CarSnapshot(
                    car.number(),
                    car.totalTime(),
                    car.compound().id(),
                    car.tireWear(),
                    car.tireTemp(),
                    car.fuelKg(),
                    false,
                    car.stint(),
                    car.stintLaps(),
                    car.aggression(),
                    car.team().paceOffset(),
                    AiPersonality.parse(car.team().personality()),
                    car.team()));
        }
        if (cars.isEmpty()) {
            return null;
        }
        return new RaceSnapshot(context.lap(), context.totalLaps(), context.weather(),
                config.weatherEnabled(), context.track(),
                config.tireWearScale(), config.pitLaneLoss(),
                config.tireChangeCost(), config.refuelSecondsPerKg(), config.fuelTimePerKg(),
                tires, cars);
    }

    private boolean needsEmergencyPit(RaceContext context, Car car) {
        int left = context.remainingLaps();
        double needed = context.track().fuelPerLap() * left;
        // 末圈也救：进站多花一次停站时间，但至少能完赛；
        // 不救就是直接退赛，0 分，怎么算都比停一次亏。
        return left > 0
                && car.fuelKg() < context.track().fuelPerLap() * 3.5
                && needed > car.fuelKg();
    }

    private PitDecision emergencyPit(RaceContext context, Car car) {
        int left = context.remainingLaps();
        double needed = context.track().fuelPerLap() * left * 1.05 + 2 - car.fuelKg();
        double fuel = Math.min(115, Math.max(10, needed));
        return new PitDecision(emergencyTire(context), fuel);
    }

    private TireCompound emergencyTire(RaceContext context) {
        boolean wetWeather = context.weather().isWet();
        TireCompound best = null;
        double bestScore = Double.MAX_VALUE;
        for (TireCompound tire : tires.values()) {
            if (!context.track().allowsCompound(tire.id())) {
                continue;
            }
            boolean rainTire = tire.timeOffset(Weather.DRY) >= 8.0;
            if (rainTire != wetWeather) {
                continue;
            }
            double score = tire.timeOffset(context.weather());
            if (score < bestScore) {
                bestScore = score;
                best = tire;
            }
        }
        if (best != null) {
            return best;
        }
        for (TireCompound tire : tires.values()) {
            if (context.track().allowsCompound(tire.id())
                    && tire.timeOffset(context.weather()) < bestScore) {
                bestScore = tire.timeOffset(context.weather());
                best = tire;
            }
        }
        if (best == null) {
            // 走到这里说明 tracks.json 和 tires.json 对不上：本站一个合法配方都没有。
            // 与其偷偷用一条本站不允许的胎跑完全程，不如立刻暴露配置错误。
            throw new IllegalStateException(
                    "本站没有任何可用轮胎：" + context.track().id()
                            + " / " + context.track().dryCompounds());
        }
        return best;
    }
}
