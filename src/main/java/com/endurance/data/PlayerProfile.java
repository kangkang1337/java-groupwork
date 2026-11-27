package com.endurance.data;

import com.endurance.model.Driver;
import com.endurance.model.RaceCategory;
import com.endurance.model.Team;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 玩家自己的车队档案：车队名 + 两位车手的名字。
 *
 * <p>默认就是"1号 / 2号"，谁用谁改——小组作业里每个人换成自己的名字即可。
 * 存在 {@code saves/player.json}，不碰 {@code data/} 下的官方数据，
 * 所以升级配置不会把用户改的名字冲掉。</p>
 */
public record PlayerProfile(String teamName, String shortName, String driverOne, String driverTwo) {

    public static final String DEFAULT_TEAM = "Apex Racing";
    public static final String DEFAULT_SHORT = "Apex";
    public static final String DEFAULT_DRIVER_ONE = "1号";
    public static final String DEFAULT_DRIVER_TWO = "2号";

    public PlayerProfile {
        teamName = blankTo(teamName, DEFAULT_TEAM);
        shortName = blankTo(shortName, DEFAULT_SHORT);
        driverOne = blankTo(driverOne, DEFAULT_DRIVER_ONE);
        driverTwo = blankTo(driverTwo, DEFAULT_DRIVER_TWO);
    }

    public static PlayerProfile defaults() {
        return new PlayerProfile(DEFAULT_TEAM, DEFAULT_SHORT, DEFAULT_DRIVER_ONE, DEFAULT_DRIVER_TWO);
    }

    public static Path file() {
        return Path.of(System.getProperty("user.dir"), "saves", "player.json");
    }

    /** 读档案；没有文件或读坏了都退回默认值，绝不让改名功能把游戏弄打不开。 */
    public static PlayerProfile load() {
        File file = file().toFile();
        if (!file.isFile()) {
            return defaults();
        }
        try {
            var root = Json.parseFile(file);
            return new PlayerProfile(
                    root.opt("teamName", new Json.JsonValue.Str(DEFAULT_TEAM)).asString(),
                    root.opt("shortName", new Json.JsonValue.Str(DEFAULT_SHORT)).asString(),
                    root.opt("driverOne", new Json.JsonValue.Str(DEFAULT_DRIVER_ONE)).asString(),
                    root.opt("driverTwo", new Json.JsonValue.Str(DEFAULT_DRIVER_TWO)).asString());
        } catch (Exception e) {
            System.err.println("读取玩家档案失败，使用默认值: " + e.getMessage());
            return defaults();
        }
    }

    public void save() throws IOException {
        Path path = file();
        Files.createDirectories(path.getParent());
        String json = JsonWriter.obj()
                .put("teamName", teamName)
                .put("shortName", shortName)
                .put("driverOne", driverOne)
                .put("driverTwo", driverTwo)
                .toJson();
        Files.writeString(path, json, StandardCharsets.UTF_8);
    }

    /**
     * 把档案套到一整套数据上，返回替换过玩家车队的新数据。
     *
     * <p>只动"有玩家车手的那个车队"，真实车队原样保留。</p>
     */
    public GameData applyTo(GameData data) {
        if (data == null) {
            return null;
        }
        List<Team> teams = new ArrayList<>(data.teams().size());
        for (Team team : data.teams()) {
            if (!isPlayerTeam(team)) {
                teams.add(team);
                continue;
            }
            List<Driver> drivers = new ArrayList<>(team.drivers().size());
            int index = 0;
            for (Driver driver : team.drivers()) {
                String name = index == 0 ? driverOne : driverTwo;
                drivers.add(new Driver(driver.number(), name, driver.aggression(),
                        driver.startTireId(), driver.player()));
                index++;
            }
            teams.add(new Team(team.id(), teamName, shortName, team.paceOffset(),
                    team.reliability(), team.colorHex(), team.aggressionBias(), drivers,
                    team.personality(), team.category()));
        }
        return new GameData(data.tracks(), teams, data.tires());
    }

    /** 是否是玩家车队（含玩家车手的那一支）。 */
    public static boolean isPlayerTeam(Team team) {
        for (Driver driver : team.drivers()) {
            if (driver.player()) {
                return true;
            }
        }
        return false;
    }

    /** 找到玩家车队，找不到返回 null。 */
    public static Team playerTeam(GameData data) {
        if (data == null) {
            return null;
        }
        for (Team team : data.teams()) {
            if (isPlayerTeam(team)) {
                return team;
            }
        }
        return null;
    }

    /** 是否还是默认值（界面上用来提示"还没改过"）。 */
    public boolean isDefault() {
        return DEFAULT_TEAM.equals(teamName) && DEFAULT_SHORT.equals(shortName)
                && DEFAULT_DRIVER_ONE.equals(driverOne) && DEFAULT_DRIVER_TWO.equals(driverTwo);
    }

    /** 便于调试输出。 */
    @Override
    public String toString() {
        return teamName + "（" + driverOne + " / " + driverTwo + "）";
    }

    private static String blankTo(String text, String fallback) {
        return text == null || text.isBlank() ? fallback : text.trim();
    }

    /** 组别说明，保留给将来的分组界面用。 */
    public static RaceCategory defaultCategory() {
        return RaceCategory.HYPERCAR;
    }
}
