package com.endurance.data;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 历史战绩：每跑完一场就追加一条记录，存在 {@code saves/history.json}。
 *
 * <p>用追加而不是覆盖：JSON 数组读出来、加一条、整体写回。文件只有几十行，
 * 这点开销可以忽略，换来的是"随时能读、随时能看"。</p>
 *
 * <p><b>写入是原子的</b>：先写临时文件再整体替换，写到一半被强制结束也不会留下半个 JSON。
 * 读失败时把损坏文件备份成 {@code .corrupt} 而不是当成空列表——
 * 否则下一次追加会把旧战绩全部覆盖掉。</p>
 */
public final class RaceHistory {

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private RaceHistory() {
        throw new AssertionError("工具类不允许实例化");
    }

    /**
     * 一条战绩。
     *
     * @param playedAt       比赛时间
     * @param trackName      赛道名
     * @param laps           圈数
     * @param position       玩家名次
     * @param retired        是否退赛
     * @param playerTime     玩家总用时
     * @param stops          进站次数
     * @param bestLap        玩家最快圈
     * @param score          决策评分
     * @param winner         冠军
     */
    public record RaceRecord(String playedAt, String trackName, int laps, int position,
                             boolean retired, double playerTime, int stops, double bestLap,
                             int score, String winner) {
    }

    public static Path file() {
        return Path.of(System.getProperty("user.dir"), "saves", "history.json");
    }

    /** 读取全部战绩（没文件返回空列表）。 */
    public static List<RaceRecord> load() {
        File file = file().toFile();
        if (!file.isFile()) {
            return new ArrayList<>();
        }
        try {
            return parse(file);
        } catch (Exception e) {
            // 读坏了不能静默当空表：先把现场保留下来，免得下次追加把旧记录冲掉
            System.err.println("读取历史战绩失败: " + e.getMessage());
            quarantine(file);
            return new ArrayList<>();
        }
    }

    private static List<RaceRecord> parse(File file) throws IOException {
        List<RaceRecord> records = new ArrayList<>();
        var root = Json.parseFile(file);
        var array = root.opt("races", new Json.JsonValue.Arr(new ArrayList<>()));
        for (var node : array.asArray()) {
            records.add(new RaceRecord(
                    node.opt("playedAt", new Json.JsonValue.Str("")).asString(),
                    node.opt("track", new Json.JsonValue.Str("")).asString(),
                    (int) node.opt("laps", new Json.JsonValue.Num(0)).asDouble(),
                    (int) node.opt("position", new Json.JsonValue.Num(0)).asDouble(),
                    node.opt("retired", new Json.JsonValue.Bool(false)).asBool(),
                    node.opt("playerTime", new Json.JsonValue.Num(0)).asDouble(),
                    (int) node.opt("stops", new Json.JsonValue.Num(0)).asDouble(),
                    node.opt("bestLap", new Json.JsonValue.Num(0)).asDouble(),
                    (int) node.opt("score", new Json.JsonValue.Num(0)).asDouble(),
                    node.opt("winner", new Json.JsonValue.Str("")).asString()));
        }
        return records;
    }

    /** 把损坏的历史文件改名保留，避免被下一次写入覆盖。 */
    private static void quarantine(File file) {
        try {
            Path corrupt = file.toPath().resolveSibling("history.json.corrupt");
            Files.move(file.toPath(), corrupt, StandardCopyOption.REPLACE_EXISTING);
            System.err.println("已把损坏的历史战绩备份到 " + corrupt);
        } catch (IOException e) {
            System.err.println("备份损坏文件也失败了: " + e.getMessage());
        }
    }

    /** 追加一条战绩。 */
    public static void append(RaceRecord record) throws IOException {
        List<RaceRecord> records = load();
        records.add(record);

        JsonWriter.Arr array = JsonWriter.arr();
        for (RaceRecord item : records) {
            array.add(JsonWriter.obj()
                    .put("playedAt", item.playedAt())
                    .put("track", item.trackName())
                    .put("laps", item.laps())
                    .put("position", item.position())
                    .put("retired", item.retired())
                    .put("playerTime", item.playerTime())
                    .put("stops", item.stops())
                    .put("bestLap", item.bestLap())
                    .put("score", item.score())
                    .put("winner", item.winner()));
        }

        Path path = file();
        Files.createDirectories(path.getParent());
        JsonWriter.writeAtomically(path, JsonWriter.obj().put("races", array).toJson());
    }

    /** 生成一条当前时间的记录。 */
    public static RaceRecord record(String trackName, int laps, int position, boolean retired,
                                    double playerTime, int stops, double bestLap, int score,
                                    String winner) {
        return new RaceRecord(LocalDateTime.now().format(TIME_FORMAT), trackName, laps, position,
                retired, playerTime, stops, bestLap, score, winner);
    }
}
