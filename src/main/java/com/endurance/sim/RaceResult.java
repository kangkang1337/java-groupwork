package com.endurance.sim;

import com.endurance.model.Car;
import com.endurance.model.Track;
import com.endurance.model.Weather;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 一场比赛的最终结果：名次表 + 完整事件流 + 逐圈数据。
 *
 * <p>逐圈数据是复盘、圈速曲线和回放的数据源，所以这里完整保留。</p>
 */
public class RaceResult {

    /**
     * 名次表中的一行。
     *
     * @param position    名次（1 开始）
     * @param car         车辆
     * @param totalTime   完赛总时间
     * @param gapToWinner 落后冠军秒数（冠军为 0）
     * @param retired     是否退赛
     */
    public record Entry(int position, Car car, double totalTime, double gapToWinner, boolean retired,
                       int classPosition) {

        /** 组别名，界面用。 */
        public String className() {
            return car.category().displayName();
        }

        public String status() {
            return retired ? "退赛：" + car.retireReason() : "完赛";
        }
    }

    private final Track track;
    private final int totalLaps;
    private final List<Entry> standings;
    private final List<RaceEvent> events;
    private final List<LapResult> lapResults;
    private final Weather finalWeather;
    private final List<PitRecord> pitStops;

    public RaceResult(Track track, int totalLaps, List<Entry> standings,
                      List<RaceEvent> events, List<LapResult> lapResults, Weather finalWeather) {
        this(track, totalLaps, standings, events, lapResults, finalWeather, List.of());
    }

    public RaceResult(Track track, int totalLaps, List<Entry> standings,
                      List<RaceEvent> events, List<LapResult> lapResults, Weather finalWeather,
                      List<PitRecord> pitStops) {
        this.track = track;
        this.totalLaps = totalLaps;
        this.standings = Collections.unmodifiableList(new ArrayList<>(standings));
        this.events = Collections.unmodifiableList(new ArrayList<>(events));
        this.lapResults = Collections.unmodifiableList(new ArrayList<>(lapResults));
        this.finalWeather = finalWeather;
        this.pitStops = Collections.unmodifiableList(new ArrayList<>(pitStops));
    }

    public Track track() {
        return track;
    }

    public int totalLaps() {
        return totalLaps;
    }

    public List<Entry> standings() {
        return standings;
    }

    public List<RaceEvent> events() {
        return events;
    }

    public List<LapResult> lapResults() {
        return lapResults;
    }

    public Weather finalWeather() {
        return finalWeather;
    }

    /** 全部进站记录。 */
    public List<PitRecord> pitStops() {
        return pitStops;
    }

    /** 某辆车的进站记录。 */
    public List<PitRecord> pitStopsOf(int carNumber) {
        return pitStops.stream().filter(p -> p.carNumber() == carNumber).toList();
    }

    /** 冠军，全部退赛时返回 null。 */
    public Entry winner() {
        for (Entry entry : standings) {
            if (!entry.retired()) {
                return entry;
            }
        }
        return null;
    }

    /** 某辆车的全部逐圈记录。 */
    public List<LapResult> lapsOf(int carNumber) {
        List<LapResult> result = new ArrayList<>();
        for (LapResult lap : lapResults) {
            if (lap.carNumber() == carNumber) {
                result.add(lap);
            }
        }
        return result;
    }

    /** 全场最快圈。 */
    public LapResult fastestLap() {
        LapResult fastest = null;
        for (LapResult lap : lapResults) {
            if (fastest == null || lap.lapTime() < fastest.lapTime()) {
                fastest = lap;
            }
        }
        return fastest;
    }
}
