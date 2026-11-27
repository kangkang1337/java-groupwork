package com.endurance.analysis;

import com.endurance.data.GameData;
import com.endurance.model.Car;
import com.endurance.model.RaceConfig;
import com.endurance.model.TireCompound;
import com.endurance.model.Track;
import com.endurance.model.Weather;
import com.endurance.setup.RaceSetup;
import com.endurance.sim.LapResult;
import com.endurance.sim.PitRecord;
import com.endurance.sim.RaceResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 赛后复盘：给玩家这场的策略打分。
 *
 * <p>打分逻辑很简单也很实在——用同一个物理模型把这辆车的最优进站方案算出来，
 * 再看你实际跑的时间比理论最优慢了多少。慢 10 秒扣 30 分，慢 30 秒基本归零。</p>
 */
public final class RaceReport {

    /**
     * 复盘结果。
     *
     * @param score       策略分（0~100）
     * @param grade       等级文字
     * @param playerTime  实际完赛时间
     * @param optimalTime 理论最优时间
     * @param optimalPlan 理论最优进站方案
     * @param stops       实际进站次数
     * @param pitCost     实际进站总耗时
     * @param lapLoss     相对本车最快圈的累计损失
     * @param notes       关键决策点评
     */
    public record Score(int score, String grade, double playerTime, double optimalTime,
                        String optimalPlan, int stops, double pitCost, double lapLoss,
                        boolean retired, List<String> notes,
                        String className, int classPosition) {
    }

    /**
     * 不可避免的损耗：交通阻挡、起步混战、每圈的自然波动。
     * 理论最优是"一台车单独跑"的理想值，真实比赛里拿不到，
     * 所以打分前先扣掉这么多秒，否则所有人都会被算成不及格。
     */
    private static final double UNAVOIDABLE_LOSS_SECONDS = 12.0;

    private RaceReport() {
        throw new AssertionError("工具类不允许实例化");
    }

    /** 分析一辆车的比赛表现。 */
    public static Score analyze(RaceResult result, int carNumber, GameData data, RaceConfig config) {
        Track track = result.track();
        List<LapResult> laps = result.lapsOf(carNumber);
        List<String> notes = new ArrayList<>();

        RaceResult.Entry entry = null;
        for (RaceResult.Entry candidate : result.standings()) {
            if (candidate.car().number() == carNumber) {
                entry = candidate;
                break;
            }
        }
        if (entry == null || laps.isEmpty()) {
            return new Score(0, "无数据", 0, 0, "—", 0, 0, 0, true,
                    List.of("这辆车没有完成任何一圈，无法复盘"), "—", 0);
        }

        Car car = entry.car();
        Map<String, TireCompound> tires = new LinkedHashMap<>();
        for (TireCompound tire : data.tires()) {
            tires.put(tire.id(), tire);
        }

        // 逐圈天气与安全车标记（车辆退赛后按最后一圈外推）
        List<Weather> weather = new ArrayList<>(result.totalLaps());
        List<Boolean> safetyCar = new ArrayList<>(result.totalLaps());
        Weather last = Weather.DRY;
        for (int lap = 1; lap <= result.totalLaps(); lap++) {
            boolean underSafetyCar = false;
            for (LapResult row : laps) {
                if (row.lap() == lap) {
                    last = row.weather();
                    underSafetyCar = row.safetyCar();
                    break;
                }
            }
            weather.add(last);
            safetyCar.add(underSafetyCar);
        }

        double startFuel = RaceSetup.startFuelFor(track);
        PitOptimizer.Plan optimal = PitOptimizer.optimal(track, weather, tires,
                // 必须用起步轮胎：此时 car.compound() 已经是比赛中最后一次换上的胎
        car.initialCompound(), startFuel, car.team().paceOffset(), car.aggression(),
                config.pitLaneLoss() + config.tireChangeCost(), track.fuelPerLap(),
                config.fuelTimePerKg(), config.tireWearScale(), config.refuelSecondsPerKg(),
                safetyCar);

        // 安全车帮着抹掉的那部分要加回来，才是这台车真正的比赛用时
        double playerTime = entry.totalTime() + car.safetyCarCredit();
        double optimalTime = optimal.totalTime();
        // 允许为负：实际比"理论最优"还快时，说明这套方案摸到了模型没想到的窗口
        double gap = playerTime - optimalTime;

        List<PitRecord> pits = result.pitStopsOf(carNumber);
        double pitCost = pits.stream().mapToDouble(PitRecord::cost).sum();

        double bestLap = laps.stream().mapToDouble(LapResult::lapTime).min().orElse(0);
        // 安全车圈是全场慢，不能算进"轮胎管理损失"里
        double lapLoss = laps.stream().filter(lap -> !lap.safetyCar())
                .mapToDouble(lap -> lap.lapTime() - bestLap).sum();

        double adjustedGap = Math.max(0, gap - UNAVOIDABLE_LOSS_SECONDS);
        int score = entry.retired() ? 0
                : (int) Math.round(Math.max(0, Math.min(100, 100 - adjustedGap * 2.5)));
        String grade = entry.retired() ? "退赛"
                : score >= 90 ? "优秀"
                : score >= 75 ? "良好"
                : score >= 60 ? "及格" : "待改进";

        // ---------------- 点评 ----------------
        if (entry.retired()) {
            notes.add("比赛退赛（" + car.retireReason() + "），成绩不计分");
        } else if (gap <= 0.05) {
            notes.add(String.format(java.util.Locale.ROOT,
                    "理论最优 %s，你跑了 %s —— 比模型还快 %.1f 秒，这套方案赢了优化器",
                    format(optimalTime), format(playerTime), -gap));
        } else {
            notes.add(String.format(java.util.Locale.ROOT,
                    "理论最优 %s，你跑了 %s，慢了 %.1f 秒",
                    format(optimalTime), format(playerTime), gap));
        }
        notes.add("最优进站方案：" + optimal.describe());

        if (!pits.isEmpty()) {
            StringBuilder sb = new StringBuilder("你的进站：");
            for (int i = 0; i < pits.size(); i++) {
                if (i > 0) {
                    sb.append("、");
                }
                sb.append("第 ").append(pits.get(i).lap()).append(" 圈");
            }
            sb.append(String.format(java.util.Locale.ROOT, "（累计损失 %.1f 秒）", pitCost));
            notes.add(sb.toString());
        } else {
            notes.add("你一次都没进站");
        }

        if (!entry.retired() && pits.size() > optimal.pitLaps().size() + 1) {
            notes.add("进站次数比最优方案多了 " + (pits.size() - optimal.pitLaps().size())
                    + " 次，在耐力赛里代价很大");
        }
        if (lapLoss > 60) {
            notes.add(String.format(java.util.Locale.ROOT,
                    "相对本车最快圈累计损失 %.0f 秒，轮胎管理还有空间", lapLoss));
        }
        notes.add(String.format(java.util.Locale.ROOT, "本车最快圈 %s", format(bestLap)));

        return new Score(score, grade, playerTime, optimalTime, optimal.describe(),
                pits.size(), pitCost, lapLoss, entry.retired(), notes,
                car.category().displayName(), entry.classPosition());
    }

    private static String format(double seconds) {
        if (seconds <= 0) {
            return "—";
        }
        int total = (int) Math.round(seconds * 1000);
        int ms = total % 1000;
        total /= 1000;
        return String.format(java.util.Locale.ROOT, "%d:%02d.%03d",
                total / 60, total % 60, ms);
    }
}
