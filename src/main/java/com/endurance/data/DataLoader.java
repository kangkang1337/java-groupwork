package com.endurance.data;

import com.endurance.model.Driver;
import com.endurance.model.Team;
import com.endurance.model.TireCompound;
import com.endurance.model.Track;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 把 {@code data/} 目录下的 JSON 读成 {@link GameData}。
 *
 * <p>三个文件各管一类：{@code tracks.json}（赛道）、{@code teams.json}（车队与车手）、
 * {@code tires.json}（轮胎配方）。改任何一个都不用重新编译代码。</p>
 */
public final class DataLoader {


    public static final String TRACKS_FILE = "tracks.json";
    public static final String TEAMS_FILE = "teams.json";
    public static final String TIRES_FILE = "tires.json";

    private DataLoader() {
        throw new AssertionError("工具类不允许实例化");
    }

    /** 默认数据目录：当前工作目录下的 data/。 */
    public static File defaultDataDir() {
        return new File(System.getProperty("user.dir"), "data");
    }

    /** 从默认目录加载。 */
/** 读配置并套上玩家档案（车手名、车队名以玩家设置为准）。 */
    public static GameData loadDefault() throws IOException {
        return PlayerProfile.load().applyTo(loadRaw());
    }
    /** 读原始配置（不套玩家档案）。 */
    public static GameData loadRaw() throws IOException {
        return load(defaultDataDir());
    }

    /** 从指定目录加载。 */
    public static GameData load(File dir) throws IOException {
        if (!dir.isDirectory()) {
            throw new IOException("找不到数据目录：" + dir.getAbsolutePath());
        }
        List<TireCompound> tires = loadTires(new File(dir, TIRES_FILE));
        List<Track> tracks = loadTracks(new File(dir, TRACKS_FILE));
        List<Team> teams = loadTeams(new File(dir, TEAMS_FILE));
        if (tracks.isEmpty()) {
            throw new IOException(TRACKS_FILE + " 里没有任何赛道");
        }
        if (teams.isEmpty()) {
            throw new IOException(TEAMS_FILE + " 里没有任何车队");
        }
        if (tires.isEmpty()) {
            throw new IOException(TIRES_FILE + " 里没有任何轮胎配方");
        }
        return new GameData(tracks, teams, tires);
    }

    // ------------------------------------------------------------------

    private static List<TireCompound> loadTires(File file) throws IOException {
        Json.JsonValue root = Json.parseFile(file);
        List<TireCompound> tires = new ArrayList<>();
        for (Json.JsonValue item : root.get("tires").asArray()) {
            tires.add(new TireCompound(
                    item.get("id").asString(),
                    item.get("name").asString(),
                    item.get("wearPerLap").asDouble(),
                    item.get("maxWearPenalty").asDouble(),
                    item.get("optimalTemp").asDouble(),
                    item.get("dryOffset").asDouble(),
                    item.get("dampOffset").asDouble(),
                    item.get("wetOffset").asDouble(),
                    item.opt("color", new Json.JsonValue.Str("#9AA4B2")).asString()));
        }
        return tires;
    }

    private static List<Track> loadTracks(File file) throws IOException {
        Json.JsonValue root = Json.parseFile(file);
        List<Track> tracks = new ArrayList<>();
        for (Json.JsonValue item : root.get("tracks").asArray()) {
            tracks.add(new Track(
                    item.get("id").asString(),
                    item.get("name").asString(),
                    item.get("lengthMeters").asInt(),
                    item.get("baseLapSeconds").asDouble(),
                    item.get("corners").asInt(),
                    item.get("trackTemp").asDouble(),
                    item.get("fuelPerLap").asDouble(),
                    item.get("laps").asInt(),
                    item.get("wearScale").asDouble(),
                    parseCategories(item.opt("categories",
                            new Json.JsonValue.Arr(new java.util.ArrayList<>()))),
                    item.opt("round", new Json.JsonValue.Num(0)).asInt(),
                    item.opt("country", new Json.JsonValue.Str("")).asString(),
                    item.opt("event", new Json.JsonValue.Str(item.get("name").asString())).asString(),
                    parseDryCompounds(item.opt("dryCompounds", null)),
                    item.opt("rainBias", new Json.JsonValue.Num(1.0)).asDouble()));
        }
        return tracks;
    }

    private static List<Team> loadTeams(File file) throws IOException {
        Json.JsonValue root = Json.parseFile(file);
        List<Team> teams = new ArrayList<>();
        for (Json.JsonValue item : root.get("teams").asArray()) {
            List<Driver> drivers = new ArrayList<>();
            for (Json.JsonValue d : item.get("drivers").asArray()) {
                drivers.add(new Driver(
                        d.get("number").asInt(),
                        d.get("name").asString(),
                        d.get("aggression").asDouble(),
                        d.get("startTire").asString(),
                        d.opt("player", new Json.JsonValue.Bool(false)).asBool()));
            }
            teams.add(new Team(
                    item.get("id").asString(),
                    item.get("name").asString(),
                    item.get("shortName").asString(),
                    item.get("paceOffset").asDouble(),
                    item.get("reliability").asDouble(),
                    item.opt("color", new Json.JsonValue.Str("#9AA4B2")).asString(),
                    item.opt("aggression", new Json.JsonValue.Num(0.5)).asDouble(),
                    drivers,
                    item.opt("personality", new Json.JsonValue.Str("balanced")).asString(),
                    com.endurance.model.RaceCategory.parse(item.opt("category",
                            new Json.JsonValue.Str("HYPERCAR")).asString())));
        }
        return teams;
    }
/** 解析赛道参与组别；配置里没写就按 Hypercar 一站。 */
    private static java.util.List<com.endurance.model.RaceCategory> parseCategories(
            Json.JsonValue node) {
        java.util.List<com.endurance.model.RaceCategory> list = new java.util.ArrayList<>();
        try {
            for (Json.JsonValue item : node.asArray()) {
                list.add(com.endurance.model.RaceCategory.parse(item.asString()));
            }
        } catch (RuntimeException ignored) {
            // 写错格式就当没写
        }
        return list;
    }
/** 解析"本站允许的干胎配方"，没写就默认三种全开。 */
    private static java.util.List<String> parseDryCompounds(Json.JsonValue node) {
        java.util.List<String> list = new java.util.ArrayList<>();
        if (node == null || node instanceof Json.JsonValue.Null) {
            return java.util.List.of("soft", "medium", "hard");
        }
        try {
            for (Json.JsonValue item : node.asArray()) {
                list.add(item.asString());
            }
        } catch (RuntimeException e) {
            return java.util.List.of("soft", "medium", "hard");
        }
        return list.isEmpty() ? java.util.List.of("soft", "medium", "hard") : list;
    }
}