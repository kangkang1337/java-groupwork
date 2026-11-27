package com.endurance.dev;

import com.endurance.data.GameData;
import com.endurance.model.RaceConfig;
import com.endurance.sim.RaceEvent;
import com.endurance.sim.RaceResult;
import com.endurance.sim.RaceSimulator;

import java.util.Locale;

/**
 * 事故频率统计：连跑多场，数一数平均每场有多少安全车、撞车、赛道限制判罚。
 *
 * <p>用来跟真实赛事对齐——真车手都是职业的，没有这么多事故。</p>
 */
public final class RateCheck {

    private RateCheck() {
    }

    public static void main(String[] args) {
        com.endurance.util.FxEnv.configure();
        GameData data = RaceCli.loadDataOrExit();
        if (data == null) {
            return;
        }
        int races = args.length > 0 ? Integer.parseInt(args[0]) : 20;

        int safetyCars = 0;
        int crashes = 0;
        int warnings = 0;
        int timePenalties = 0;
        int realRaces = 0;

        for (int i = 0; i < races; i++) {
            var lineup = com.endurance.setup.RaceSetup.build(
                    data, data.defaultTrack(), data.defaultTrack().defaultLaps(),
                    5000L + i, false, false);
            RaceSimulator simulator = new RaceSimulator(lineup.config(), lineup.strategies());
            RaceResult result = simulator.run();
            lineup.close();
            realRaces++;

            for (RaceEvent event : result.events()) {
                String message = event.message();
                switch (event.type()) {
                    case SAFETY_CAR -> {
                        if (message.contains("出动")) {
                            safetyCars++;
                        }
                    }
                    case CRASH -> crashes++;
                    case PENALTY -> {
                        if (message.contains("黑白旗")) {
                            warnings++;
                        } else {
                            timePenalties++;
                        }
                    }
                    default -> {
                    }
                }
            }
        }

        System.out.println();
        System.out.println("=== " + realRaces + " 场比赛的事故频率 ===");
        System.out.println(String.format(Locale.ROOT, "平均每场安全车   %.2f 次", safetyCars / (double) realRaces));
        System.out.println(String.format(Locale.ROOT, "平均每场撞车     %.2f 次", crashes / (double) realRaces));
        System.out.println(String.format(Locale.ROOT, "平均每场黑白旗   %.2f 次", warnings / (double) realRaces));
        System.out.println(String.format(Locale.ROOT, "平均每场罚时     %.2f 次", timePenalties / (double) realRaces));
        System.out.println();
        System.out.println("参照：F1 近年平均每场约 0.5 次安全车；WEC 6 小时通常 1~3 次 SC/FCY；");
        System.out.println("      赛道限制正赛里多数只是警告，实际罚时每场 0~2 次。");

        double sc = safetyCars / (double) realRaces;
        double crash = crashes / (double) realRaces;
        double warn = warnings / (double) realRaces;
        double pen = timePenalties / (double) realRaces;

        int failed = 0;
        failed += check(String.format(Locale.ROOT, "安全车频率合理（%.2f 次/场）", sc),
                sc >= 0.2 && sc <= 2.5);
        failed += check(String.format(Locale.ROOT, "撞车频率合理（%.2f 次/场）", crash),
                crash <= 1.0);
        failed += check(String.format(Locale.ROOT, "黑白旗频率合理（%.2f 次/场）", warn),
                warn <= 3.0);
        failed += check(String.format(Locale.ROOT, "罚时频率合理（%.2f 次/场）", pen),
                pen <= 2.0);

        if (failed > 0) {
            System.exit(1);
        }
    }
private static int check(String name, boolean ok) {
        System.out.println((ok ? "[PASS] " : "[FAIL] ") + name);
        return ok ? 0 : 1;
    }
}