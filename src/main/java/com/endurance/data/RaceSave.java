package com.endurance.data;

import com.endurance.model.Car;
import com.endurance.model.TireCompound;
import com.endurance.model.Weather;
import com.endurance.sim.RaceSimulator;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * 中途存档：把"比赛跑到一半"的状态写成 JSON，之后能原样接着跑。
 *
 * <p>只存**可变状态**（跑到第几圈、每辆车的胎况油量、天气、安全车），
 * 赛道和车队这些静态配置不存——重开时从 data/ 重新读即可。
 * 这样存档文件很小，也不会因为改了配置就失效。</p>
 */
public final class RaceSave {

    private RaceSave() {
        throw new AssertionError("工具类不允许实例化");
    }

    public static Path defaultFile() {
        return Path.of(System.getProperty("user.dir"), "saves", "race.json");
    }

    /** 把比赛当前状态序列化成 JSON 文本。 */
    public static String capture(RaceSimulator simulator) {
        JsonWriter.Obj root = JsonWriter.obj()
                .put("version", 1)
                .put("trackId", simulator.config().track().id())
                .put("trackName", simulator.config().track().name())
                .put("totalLaps", simulator.totalLaps())
                .put("completedLaps", simulator.completedLaps())
                .put("weather", simulator.currentWeather().name())
                .put("safetyCar", simulator.safetyCarRemaining())
                .put("seed", simulator.config().seed());

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
                    .put("retireReason", car.retireReason() == null ? "" : car.retireReason()));
        }
        root.put("cars", cars);
        return root.toJson();
    }

    /** 保存到文件（自动建目录）。 */
    public static void save(RaceSimulator simulator, Path file) throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(file, capture(simulator), StandardCharsets.UTF_8);
    }

    /**
     * 把存档写回一支已经装配好的比赛。
     *
     * @param tires 轮胎配方表（按 id 查 TireCompound）
     * @return 存档里记录的赛道 id 与随机种子
     */
    public static Meta restore(File file, RaceSimulator simulator,
                               Map<String, TireCompound> tires) throws IOException {
        var root = Json.parseFile(file);
        String trackId = root.get("trackId").asString();

        for (var node : root.get("cars").asArray()) {
            int number = (int) node.get("number").asDouble();
            Car car = find(simulator, number);
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
                    node.get("retireReason").asString());
        }

        simulator.rewindTo(
                (int) root.get("completedLaps").asDouble(),
                Weather.valueOf(root.get("weather").asString()),
                (int) root.get("safetyCar").asDouble());
        return new Meta(trackId, (long) root.get("seed").asDouble(),
                (int) root.get("completedLaps").asDouble());
    }

    /** 只读存档头信息，用来决定"该装配哪一场比赛"。 */
    public static Meta peek(File file) throws IOException {
        var root = Json.parseFile(file);
        return new Meta(root.get("trackId").asString(),
                (long) root.get("seed").asDouble(),
                (int) root.get("completedLaps").asDouble());
    }

    /** 存档里记录的"这是哪一场"。 */
    public record Meta(String trackId, long seed, int completedLaps) {
    }

    private static Car find(RaceSimulator simulator, int number) {
        for (Car car : simulator.cars()) {
            if (car.number() == number) {
                return car;
            }
        }
        return null;
    }
}
