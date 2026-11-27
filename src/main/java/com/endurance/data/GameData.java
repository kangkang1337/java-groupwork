package com.endurance.data;

import com.endurance.model.Team;
import com.endurance.model.TireCompound;
import com.endurance.model.Track;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 加载完成的一整套游戏数据：赛道、车队、轮胎配方。
 *
 * <p>所有内容都来自 {@code data/} 目录下的 JSON，代码里不再写死任何数值。</p>
 */
public final class GameData {

    private final List<Track> tracks;
    private final List<Team> teams;
    private final Map<String, TireCompound> tires;

    public GameData(List<Track> tracks, List<Team> teams, List<TireCompound> tires) {
        this.tracks = Collections.unmodifiableList(new java.util.ArrayList<>(tracks));
        this.teams = Collections.unmodifiableList(new java.util.ArrayList<>(teams));
        Map<String, TireCompound> map = new LinkedHashMap<>();
        for (TireCompound tire : tires) {
            map.put(tire.id(), tire);
        }
        this.tires = Collections.unmodifiableMap(map);
    }

    public List<Track> tracks() {
        return tracks;
    }

    public List<Team> teams() {
        return teams;
    }

    public List<TireCompound> tires() {
        return List.copyOf(tires.values());
    }

    /** 按 id 取赛道，找不到时抛异常（配置写错要立刻暴露）。 */
    public Track track(String id) {
        for (Track track : tracks) {
            if (track.id().equalsIgnoreCase(id)) {
                return track;
            }
        }
        throw new IllegalArgumentException("没有 id 为 \"" + id + "\" 的赛道");
    }

    /** 默认赛道（列表第一条）。 */
    public Track defaultTrack() {
        if (tracks.isEmpty()) {
            throw new IllegalStateException("配置里没有任何赛道");
        }
        return tracks.get(0);
    }

    /** 按 id 取轮胎配方。 */
    public TireCompound tire(String id) {
        TireCompound tire = tires.get(id);
        if (tire == null) {
            throw new IllegalArgumentException("没有 id 为 \"" + id + "\" 的轮胎配方");
        }
        return tire;
    }

    /** 按 id 查询轮胎配方；外部输入可能未知时使用，不会因找不到而抛异常。 */
    public TireCompound findTire(String id) {
        return tires.get(id);
    }

    /** 常用配方的便捷访问（配置里不存在时返回列表第一条，避免空指针）。 */
    public TireCompound soft() {
        return tires.getOrDefault("soft", tires.values().iterator().next());
    }

    public TireCompound medium() {
        return tires.getOrDefault("medium", tires.values().iterator().next());
    }

    public TireCompound hard() {
        return tires.getOrDefault("hard", tires.values().iterator().next());
    }

    /** 人数统计：车队数 × 每队车手数。 */
    public int carCount() {
        int count = 0;
        for (Team team : teams) {
            count += team.drivers().size();
        }
        return count;
    }
}
