package com.endurance.dev;

import com.endurance.analysis.RealReference;
import com.endurance.data.GameData;
import com.endurance.sim.RaceResult;
import com.endurance.sim.RaceSimulator;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 真实数据对照：把我们的仿真跑出来的比赛，和 FIA WEC 官方结果比"形状"。
 *
 * <p>比的是结构指标，不是绝对圈速——我们的模型是给游戏调出来的，
 * 不是物理引擎。真正要保证的是：比赛该有多长、差距该多大、套圈该从哪儿开始，
 * 这些看起来得像真的耐力赛。</p>
 */
public final class RealDataCheck {

    private static int passed;
    private static int failed;

    private RealDataCheck() {
    }

    public static void main(String[] args) throws Exception {
        com.endurance.util.FxEnv.configure();
        GameData data = RaceCli.loadDataOrExit();
        if (data == null) {
            failed++;
            return;
        }

        List<RealReference.Race> references = RealReference.load(RealReference.defaultFile());
        if (references.isEmpty()) {
            System.out.println("[FAIL] 找不到 data/reference/wec-2026.json");
            System.exit(1);
        }

        System.out.println("=== 真实 WEC 数据（FIA 官方成绩）===");
        System.out.print(RealReference.describe(references));
        System.out.println();

        // 我们的仿真：富士赛道跑满默认圈数，取 3 个种子平均
        List<Double> ratios = new ArrayList<>();
        List<Double> gaps12 = new ArrayList<>();
        List<Double> gapsLast = new ArrayList<>();
        double bestLapSum = 0;
        double avgLapSum = 0;

        for (long seed : new long[]{20260101L, 9001L, 424242L}) {
            var lineup = com.endurance.setup.RaceSetup.build(
                    data, data.defaultTrack(), data.defaultTrack().defaultLaps(), seed, false, false);
            RaceSimulator simulator = new RaceSimulator(lineup.config(), lineup.strategies());
            RaceResult result = simulator.run();
            lineup.close();

            var winner = result.winner();
            if (winner == null) {
                continue;
            }
            double total = winner.totalTime();
            double avg = total / result.totalLaps();
            double best = result.fastestLap() == null ? avg : result.fastestLap().lapTime();
            double gap12 = result.standings().size() > 1 ? result.standings().get(1).gapToWinner() : 0;
            double gapLast = 0;
            for (RaceResult.Entry entry : result.standings()) {
                // 只比 Hypercar：参考数据和本项目都要同组才可比，GT3 是另一个组别
                if (!entry.retired() && entry.car().category()
                        == com.endurance.model.RaceCategory.HYPERCAR) {
                    gapLast = Math.max(gapLast, entry.gapToWinner());
                }
            }

            ratios.add(best / avg);
            gaps12.add(gap12);
            gapsLast.add(gapLast);
            bestLapSum += best;
            avgLapSum += avg;
        }

        RealReference.Race fuji = references.get(0);
        System.out.println("=== 本项目仿真 vs 真实富士 ===");
        System.out.println(String.format(Locale.ROOT, "%-22s %14s %14s %10s",
                "指标", "真实 WEC 富士", "本项目（3 局均值）", "偏差"));
        System.out.println("-".repeat(64));

        double ourRatio = average(ratios);
        double ourGap12 = average(gaps12);
        double ourGapLast = average(gapsLast);

        System.out.println(String.format(Locale.ROOT, "%-22s %14.3f %14.3f %+10.3f",
                "最快圈/平均圈", fuji.bestOverAverage(), ourRatio, ourRatio - fuji.bestOverAverage()));
        System.out.println(String.format(Locale.ROOT, "%-22s %13.1fs %13.1fs %+9.1fs",
                "P1-P2 差距", fuji.gapP1P2(), ourGap12, ourGap12 - fuji.gapP1P2()));
        System.out.println(String.format(Locale.ROOT, "%-22s %13.1fs %13.1fs %+9.1fs",
                "P1-末位差距", fuji.gapP1P10(), ourGapLast, ourGapLast - fuji.gapP1P10()));
        System.out.println(String.format(Locale.ROOT, "%-22s %14s %14s %10s",
                "比赛长度", fuji.winnerLaps() + " 圈", "60 圈（游戏设定）", "—"));
        System.out.println();

        // 只断言"结构合理性"，不断言必须和真值一样——游戏平衡是我们自己定的
        check("最快圈/平均圈落在真实区间 0.80~0.95（本项目 " + String.format(Locale.ROOT, "%.3f", ourRatio) + "）",
                ourRatio >= 0.80 && ourRatio <= 0.95);
        check("前二名有差距但不夸张（" + String.format(Locale.ROOT, "%.1fs", ourGap12) + "）",
                ourGap12 >= 0 && ourGap12 < 120);
        check("全场差距在耐力赛合理范围（" + String.format(Locale.ROOT, "%.0fs", ourGapLast) + "）",
                ourGapLast > 0 && ourGapLast < 900);
        check("真实数据本身可用（富士 " + fuji.winnerLaps() + " 圈 / 最快圈 "
                        + String.format(Locale.ROOT, "%.2fs", fuji.bestLap()) + "）",
                fuji.winnerLaps() > 100 && fuji.bestLap() > 60 && fuji.bestLap() < 120);

        System.out.println();
        System.out.println("说明：我们的圈速绝对值比真车慢约 "
                + String.format(Locale.ROOT, "%.0f%%", (avgLapSum / ratios.size() / fuji.averageLap() - 1) * 100)
                + "，这是刻意为之——游戏的一圈要能在"
                + "几十秒内看完，所以基准圈速按可玩性调过，而不是照搬物理引擎。");
        System.out.println();
        System.out.println("==== 结果: " + passed + " 通过 / " + failed + " 失败 ====");
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static double average(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }

    private static void check(String name, boolean condition) {
        if (condition) {
            passed++;
            System.out.println("[PASS] " + name);
        } else {
            failed++;
            System.out.println("[FAIL] " + name);
        }
    }
}
