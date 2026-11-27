package com.endurance.ai;

import com.endurance.model.TireCompound;
import com.endurance.model.Weather;
import com.endurance.sim.PitDecision;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 蒙特卡洛策略规划器。
 *
 * <p>每一圈，对"这次进站能选的几种动作"分别做 N 次推演，用统计结果挑最优。
 * 关键在于**并行**：每个候选动作是一个独立任务，扔进线程池同时算；
 * 外部拿到的是一个 {@link CompletableFuture}，不阻塞界面线程。</p>
 *
 * <p>推演的随机源用"圈号 + 车号 + 候选序号 + 样本序号"生成确定性的种子，
 * 所以同一局面下 AI 的选择是可复现的——调试和写测试都靠这个。</p>
 */
public final class MonteCarloPlanner {

    /** 每个候选动作推演多少次。 */
    private static final int SAMPLES = 24;
    /** 候选动作上限，防止极端情况下算太久。 */
    private static final int MAX_CANDIDATES = 12;

    private final ExecutorService pool;
    private final int samples;

    public MonteCarloPlanner(int threads, int samples) {
        this.samples = Math.max(4, samples);
        this.pool = Executors.newFixedThreadPool(Math.max(2, threads), runnable -> {
            Thread thread = new Thread(runnable, "mc-rollout");
            thread.setDaemon(true);
            return thread;
        });
    }

    public static MonteCarloPlanner defaultPlanner() {
        int cores = Runtime.getRuntime().availableProcessors();
        return new MonteCarloPlanner(Math.max(2, cores - 1), SAMPLES);
    }

    public int threads() {
        return ((java.util.concurrent.ThreadPoolExecutor) pool).getCorePoolSize();
    }

    /**
     * 异步算一个决策。
     *
     * @return 完成时给出最优动作；{@code null} 表示"不进站"
     */
    public CompletableFuture<PitDecision> planAsync(RaceSnapshot snapshot, int myNumber,
                                                    AiPersonality personality) {
        List<PitDecision> candidates = candidates(snapshot, myNumber, personality);
        List<CompletableFuture<Scored>> futures = new ArrayList<>(candidates.size());
        for (int i = 0; i < candidates.size(); i++) {
            PitDecision candidate = candidates.get(i);
            int index = i;
            futures.add(CompletableFuture.supplyAsync(
                    () -> evaluate(snapshot, myNumber, candidate, index, personality), pool));
        }
        return CompletableFuture
                .allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(ignored -> {
                    Scored best = null;
                    for (CompletableFuture<Scored> future : futures) {
                        Scored scored = future.getNow(null);
                        if (scored != null && (best == null || scored.score < best.score)) {
                            best = scored;
                        }
                    }
                    return best == null ? null : best.decision;
                });
    }

    /** 同步版本（命令行/测试用）。 */
    public PitDecision plan(RaceSnapshot snapshot, int myNumber, AiPersonality personality) {
        try {
            return planAsync(snapshot, myNumber, personality).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            return null;
        }
    }

    public void shutdown() {
        pool.shutdownNow();
    }

    // ------------------------------------------------------------------

    private record Scored(PitDecision decision, double score) {
    }

    private Scored evaluate(RaceSnapshot snapshot, int myNumber, PitDecision candidate,
                            int candidateIndex, AiPersonality personality) {
        double[] scores = new double[samples];
        for (int i = 0; i < samples; i++) {
            long seed = 1_000_003L * (candidateIndex + 1)
                    + 7919L * snapshot.lap()
                    + 31L * myNumber
                    + i;
            Rollout.Result result = Rollout.run(snapshot, myNumber, candidate, new Random(seed));
            scores[i] = result.position() + result.gapToWinner() * 0.002;
        }
        double score = personality.score(scores);
        if (candidate == null) {
            // 正数＝更倾向于进站（保守），负数＝更倾向留在场上（激进/赌徒）
            score += personality.pitBias();
        }
        return new Scored(candidate, score);
    }

    /**
     * 生成候选动作：不进站 + 若干"换什么胎 × 加多少油"的组合。
     */
    private List<PitDecision> candidates(RaceSnapshot snapshot, int myNumber,
                                         AiPersonality personality) {
        List<PitDecision> list = new ArrayList<>();
        list.add(null); // 不进站

        RaceSnapshot.CarSnapshot me = snapshot.car(myNumber);
        if (me == null) {
            return list;
        }
        if (!personality.willingToPitEarly(me.tireWear()) && me.fuelKg() > 25) {
            // 保守派胎还好、油还够：这圈不考虑进站
            return list;
        }
        if (snapshot.remainingLaps() <= 2) {
            return list;
        }

        Weather weather = snapshot.weather();
        List<TireCompound> options = tireOptions(snapshot, weather);
        double needed = snapshot.track().fuelPerLap() * snapshot.remainingLaps();
        // 至少加够跑 12 圈的油，否则刚出站就得再进站，等于白白多停一次
        double minFuel = Math.min(needed, snapshot.track().fuelPerLap() * 12);

        // 只加油不换胎：轮胎还够用的时候，省掉换胎那几秒是实打实的收益
        for (double factor : new double[]{1.05, 0.80}) {
            double fuel = Math.min(115, Math.max(minFuel, needed * factor));
            if (fuel >= 3) {
                list.add(PitDecision.fuelOnly(Math.round(fuel)));
            }
        }

        Set<String> seen = new LinkedHashSet<>();
        for (TireCompound tire : options) {
            for (double factor : personality.riskyFuel()
                    ? new double[]{1.05, 0.80, 0.62}
                    : new double[]{1.05, 0.80}) {
                double fuel = Math.min(115, Math.max(minFuel, needed * factor));
                fuel = Math.round(fuel);
                String key = tire.id() + "@" + (int) fuel;
                if (!seen.add(key)) {
                    continue;
                }
                list.add(new PitDecision(tire, fuel));
                if (list.size() >= MAX_CANDIDATES) {
                    return list;
                }
            }
        }
        return list;
    }

    /** 只保留当前天气下不会太离谱的胎（干地就别考虑全雨胎了）。 */
    private List<TireCompound> tireOptions(RaceSnapshot snapshot, Weather weather) {
        double best = Double.MAX_VALUE;
        for (TireCompound tire : snapshot.tires().values()) {
            best = Math.min(best, tire.timeOffset(weather));
        }
        List<TireCompound> options = new ArrayList<>();
        for (TireCompound tire : snapshot.tires().values()) {
            // 这一站没给你的配方，AI 也不能凭空换上去
            if (!snapshot.track().allowsCompound(tire.id())) {
                continue;
            }
            if (tire.timeOffset(weather) - best <= 8.0) {
                options.add(tire);
            }
        }
        if (options.isEmpty()) {
            options.addAll(snapshot.tires().values());
        }
        return options;
    }
}
