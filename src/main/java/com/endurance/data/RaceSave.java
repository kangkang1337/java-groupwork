package com.endurance.data;

import com.endurance.ai.AiStrategy;
import com.endurance.model.Car;
import com.endurance.model.RaceFlag;
import com.endurance.model.TireCompound;
import com.endurance.model.Weather;
import com.endurance.sim.LapResult;
import com.endurance.sim.PitDecision;
import com.endurance.sim.PitRecord;
import com.endurance.sim.RaceEvent;
import com.endurance.sim.RaceSimulator;
import com.endurance.strategy.PlayerStrategy;
import com.endurance.strategy.Strategy;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 中途存档：把"比赛跑到一半"的完整状态写成 JSON，之后能原样接着跑。
 *
 * <p><b>v2 起不再只存"看起来重要"的字段。</b>第一版只存圈数、车况和天气，
 * 结果读档之后：复盘没有历史圈、评分算不出来、安全车与旗语状态回到初始、
 * 随机数发生器从头开始——比赛从第 21 圈起就和原来那次分叉了。</p>
 *
 * <p>现在存的是完整现场：随机数内部状态、逐圈记录、进站记录、事件流、
 * 名次快照、旗语与安全车状态、判罚计数、AI 冷却、玩家排队指令。
 * 验收标准是「跑 N 圈 → 存档 → 读档 → 跑完」与「一口气跑完」逐车成绩完全一致。</p>
 */
public final class RaceSave {

    /** 当前存档格式版本；读到别的版本直接拒绝，避免解析出半对半错的状态。 */
    public static final int VERSION = 2;

    private RaceSave() {
        throw new AssertionError("工具类不允许实例化");
    }

    public static Path defaultFile() {
        return Path.of(System.getProperty("user.dir"), "saves", "race.json");
    }

    /**
     * 把比赛当前状态序列化成 JSON 文本。
     *
     * @param formationLap 当前是否还在暖胎圈（读档后直接进入比赛，只作记录）
     */
    public static String capture(RaceSimulator simulator, boolean formationLap) {
        JsonWriter.Obj root = JsonWriter.obj()
                .put("version", VERSION)
                .put("trackId", simulator.config().track().id())
                .put("trackName", simulator.config().track().name())
                .put("seed", String.valueOf(simulator.config().seed()))
                .put("randomState", String.valueOf(simulator.randomState()))
                .put("totalLaps", simulator.totalLaps())
                .put("completedLaps", simulator.completedLaps())
                .put("weather", simulator.currentWeather().name())
                .put("previousWeather", simulator.currentWeather().name())
                .put("safetyCarRemaining", simulator.safetyCarRemaining())
                .put("safetyCarThisLap", simulator.safetyCarThisLap())
                .put("redFlagLaps", simulator.redFlagLaps())
                .put("flag", simulator.rawFlag().name())
                .put("flagLaps", simulator.flagLaps())
                .put("lapFlag", simulator.lapFlagValue().name())
                .put("formationLap", formationLap);

        JsonWriter.Arr cars = JsonWriter.arr();
        for (Car car : simulator.cars()) {
            cars.add(JsonWriter.obj()
                    .put("number", car.number())
                    .put("tire", car.compound().id())
                    .put("wear", car.tireWear())
                    .put("temp", car.tireTemp())
                    .put("fuel", car.fuelKg())
                    .put("totalTime", car.totalTime())
                    .put("lastLap", car.lastLapTime())
                    .put("bestLap", car.bestLapTime() == Double.MAX_VALUE ? -1 : car.bestLapTime())
                    .put("pits", car.pitStops())
                    .put("stint", car.stint())
                    .put("stintLaps", car.stintLaps())
                    .put("retired", car.isRetired())
                    .put("retireReason", car.retireReason() == null ? "" : car.retireReason())
                    .put("strikes", car.trackLimitStrikes())
                    .put("penalty", car.penaltySeconds())
                    .put("credit", car.safetyCarCredit()));
        }
        root.put("cars", cars);

        JsonWriter.Arr events = JsonWriter.arr();
        for (RaceEvent event : simulator.events()) {
            events.add(JsonWriter.obj()
                    .put("lap", event.lap())
                    .put("type", event.type().name())
                    .put("message", event.message()));
        }
        root.put("events", events);

        // 逐圈记录：复盘曲线与决策评分全靠它，不能丢
        JsonWriter.Arr laps = JsonWriter.arr();
        for (LapResult lap : simulator.lapResults()) {
            laps.add(JsonWriter.obj()
                    .put("lap", lap.lap())
                    .put("car", lap.carNumber())
                    .put("time", lap.lapTime())
                    .put("pos", lap.position())
                    .put("tire", lap.compound().id())
                    .put("wear", lap.tireWear())
                    .put("temp", lap.tireTemp())
                    .put("fuel", lap.fuelKg())
                    .put("weather", lap.weather().name())
                    .put("stint", lap.stint())
                    .put("sc", lap.safetyCar()));
        }
        root.put("lapResults", laps);

        JsonWriter.Arr pits = JsonWriter.arr();
        for (PitRecord pit : simulator.pitRecords()) {
            pits.add(JsonWriter.obj()
                    .put("lap", pit.lap())
                    .put("car", pit.carNumber())
                    .put("tire", pit.compound())
                    .put("fuel", pit.fuelAdded())
                    .put("cost", pit.cost())
                    .put("changed", pit.tireChanged()));
        }
        root.put("pitRecords", pits);

        JsonWriter.Arr positions = JsonWriter.arr();
        for (var entry : simulator.positionSnapshot().entrySet()) {
            positions.add(JsonWriter.obj()
                    .put("number", entry.getKey())
                    .put("pos", entry.getValue()));
        }
        root.put("positions", positions);

        // 策略层：AI 冷却 + 玩家排队指令
        JsonWriter.Arr cooldowns = JsonWriter.arr();
        JsonWriter.Obj pending = null;
        for (var entry : simulator.strategies().entrySet()) {
            Strategy strategy = entry.getValue();
            if (strategy instanceof AiStrategy ai) {
                cooldowns.add(JsonWriter.obj()
                        .put("number", entry.getKey().number())
                        .put("cooldown", ai.cooldown()));
            } else if (strategy instanceof PlayerStrategy player && player.hasPending()) {
                PitDecision decision = player.peek();
                pending = JsonWriter.obj()
                        .put("number", entry.getKey().number())
                        .put("tire", decision.changesTires() ? decision.compound().id() : "")
                        .put("fuel", decision.fuelToAdd());
            }
        }
        root.put("aiCooldowns", cooldowns);
        if (pending != null) {
            root.put("playerPending", pending);
        }
        return root.toJson();
    }

    /** 保存到文件（自动建目录，原子写入）。 */
    public static void save(RaceSimulator simulator, boolean formationLap, Path file)
            throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        JsonWriter.writeAtomically(file, capture(simulator, formationLap));
    }

    /**
     * 把存档写回一支已经装配好的比赛。
     *
     * @param tires 轮胎配方表（按 id 查 TireCompound）
     */
    public static Meta restore(File file, RaceSimulator simulator,
                               Map<String, TireCompound> tires) throws IOException {
        var root = Json.parseFile(file);
        int version = (int) root.opt("version", new Json.JsonValue.Num(0)).asDouble();
        if (version != VERSION) {
            throw new IOException("存档版本是 " + version + "，当前程序只支持版本 "
                    + VERSION + "，请重新开始一场比赛。");
        }
        String trackId = root.get("trackId").asString();

        Map<Integer, Car> byNumber = new LinkedHashMap<>();
        for (Car car : simulator.cars()) {
            byNumber.put(car.number(), car);
        }
        for (var node : root.get("cars").asArray()) {
            Car car = byNumber.get((int) node.get("number").asDouble());
            if (car == null) {
                continue;
            }
            TireCompound compound = tires.get(node.get("tire").asString());
            double best = node.get("bestLap").asDouble();
            car.restore(
                    node.get("totalTime").asDouble(),
                    node.get("lastLap").asDouble(),
                    best <= 0 ? Double.MAX_VALUE : best,
                    compound == null ? car.compound() : compound,
                    node.get("wear").asDouble(),
                    node.get("temp").asDouble(),
                    node.get("fuel").asDouble(),
                    (int) node.get("pits").asDouble(),
                    (int) node.get("stint").asDouble(),
                    (int) node.get("stintLaps").asDouble(),
                    node.get("retired").asBool(),
                    node.get("retireReason").asString(),
                    (int) node.opt("strikes", new Json.JsonValue.Num(0)).asDouble(),
                    node.opt("penalty", new Json.JsonValue.Num(0)).asDouble(),
                    node.opt("credit", new Json.JsonValue.Num(0)).asDouble());
        }

        List<RaceEvent> events = new ArrayList<>();
        for (var node : root.opt("events", new Json.JsonValue.Arr(new ArrayList<>())).asArray()) {
            events.add(new RaceEvent((int) node.get("lap").asDouble(),
                    RaceEvent.Type.valueOf(node.get("type").asString()),
                    node.get("message").asString()));
        }

        List<LapResult> laps = new ArrayList<>();
        for (var node : root.opt("lapResults",
                new Json.JsonValue.Arr(new ArrayList<>())).asArray()) {
            TireCompound compound = tires.get(node.get("tire").asString());
            laps.add(new LapResult(
                    (int) node.get("lap").asDouble(),
                    (int) node.get("car").asDouble(),
                    node.get("time").asDouble(),
                    (int) node.get("pos").asDouble(),
                    compound == null ? tires.values().iterator().next() : compound,
                    node.get("wear").asDouble(),
                    node.get("temp").asDouble(),
                    node.get("fuel").asDouble(),
                    Weather.valueOf(node.get("weather").asString()),
                    (int) node.get("stint").asDouble(),
                    node.get("sc").asBool()));
        }

        List<PitRecord> pits = new ArrayList<>();
        for (var node : root.opt("pitRecords",
                new Json.JsonValue.Arr(new ArrayList<>())).asArray()) {
            pits.add(new PitRecord(
                    (int) node.get("lap").asDouble(),
                    (int) node.get("car").asDouble(),
                    node.get("tire").asString(),
                    node.get("fuel").asDouble(),
                    node.get("cost").asDouble(),
                    node.opt("changed", new Json.JsonValue.Bool(true)).asBool()));
        }

        Map<Integer, Integer> positions = new LinkedHashMap<>();
        for (var node : root.opt("positions",
                new Json.JsonValue.Arr(new ArrayList<>())).asArray()) {
            positions.put((int) node.get("number").asDouble(),
                    (int) node.get("pos").asDouble());
        }

        simulator.restoreFull(
                (int) root.get("completedLaps").asDouble(),
                Weather.valueOf(root.get("weather").asString()),
                Weather.valueOf(root.opt("previousWeather", root.get("weather")).asString()),
                (int) root.get("safetyCarRemaining").asDouble(),
                root.opt("safetyCarThisLap", new Json.JsonValue.Bool(false)).asBool(),
                (int) root.get("redFlagLaps").asDouble(),
                RaceFlag.valueOf(root.opt("flag", new Json.JsonValue.Str("GREEN")).asString()),
                (int) root.opt("flagLaps", new Json.JsonValue.Num(0)).asDouble(),
                RaceFlag.valueOf(root.opt("lapFlag", new Json.JsonValue.Str("GREEN")).asString()),
                Long.parseLong(root.get("randomState").asString()),
                events, laps, pits, positions);

        // 策略层状态
        Map<Integer, AiStrategy> aiByNumber = new LinkedHashMap<>();
        Map<Integer, PlayerStrategy> playerByNumber = new LinkedHashMap<>();
        for (var entry : simulator.strategies().entrySet()) {
            if (entry.getValue() instanceof AiStrategy ai) {
                aiByNumber.put(entry.getKey().number(), ai);
            } else if (entry.getValue() instanceof PlayerStrategy player) {
                playerByNumber.put(entry.getKey().number(), player);
            }
        }
        for (var node : root.opt("aiCooldowns",
                new Json.JsonValue.Arr(new ArrayList<>())).asArray()) {
            AiStrategy ai = aiByNumber.get((int) node.get("number").asDouble());
            if (ai != null) {
                ai.restoreCooldown((int) node.get("cooldown").asDouble());
            }
        }
        var pendingNode = root.opt("playerPending", null);
        if (pendingNode != null) {
            int number = (int) pendingNode.get("number").asDouble();
            PlayerStrategy player = playerByNumber.get(number);
            if (player != null) {
                String tireId = pendingNode.get("tire").asString();
                double fuel = pendingNode.get("fuel").asDouble();
                TireCompound tire = tireId.isEmpty() ? null : tires.get(tireId);
                if (tire == null && fuel > 0) {
                    player.queue(PitDecision.fuelOnly(fuel));
                } else if (tire != null) {
                    player.queue(new PitDecision(tire, fuel));
                }
            }
        }

        return new Meta(trackId,
                Long.parseLong(root.get("seed").asString()),
                (int) root.get("completedLaps").asDouble());
    }

    /** 只读存档头信息，用来决定"该装配哪一场比赛"。 */
    public static Meta peek(File file) throws IOException {
        var root = Json.parseFile(file);
        int version = (int) root.opt("version", new Json.JsonValue.Num(0)).asDouble();
        if (version != VERSION) {
            throw new IOException("存档版本是 " + version + "，当前程序只支持版本 " + VERSION);
        }
        return new Meta(root.get("trackId").asString(),
                Long.parseLong(root.get("seed").asString()),
                (int) root.get("completedLaps").asDouble());
    }

    /** 存档里记录的"这是哪一场"。 */
    public record Meta(String trackId, long seed, int completedLaps) {
    }
}
