package com.endurance.sim;

import com.endurance.model.Car;
import com.endurance.model.Track;
import com.endurance.model.Weather;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 策略决策时能看到的"赛场快照"。
 *
 * <p>关键点：名次与累计时间都在本圈开始前**冻结**成快照。
 * 因为仿真器是逐车推进的，如果直接读 {@code car.totalTime()}，
 * 先跑的车已经加上了本圈时间，后跑的车就会算出负的"跟车差距"。</p>
 */
public record RaceContext(int lap, int totalLaps, Weather weather, Track track,
                          List<Car> standings, Map<Car, Double> times) {

    /** 按累计时间排好序、并冻结时间快照。 */
    public static RaceContext of(int lap, int totalLaps, Weather weather, Track track, List<Car> cars) {
        List<Car> running = new ArrayList<>();
        Map<Car, Double> snapshot = new HashMap<>();
        for (Car car : cars) {
            if (!car.isRetired()) {
                running.add(car);
                snapshot.put(car, car.totalTime());
            }
        }
        running.sort(Comparator.comparingDouble(snapshot::get));
        return new RaceContext(lap, totalLaps, weather, track, running, snapshot);
    }

    /** 还剩几圈。 */
    public int remainingLaps() {
        return totalLaps - lap + 1;
    }

    /** 某辆车在本圈开始时的累计时间。 */
    public double timeOf(Car car) {
        return times.getOrDefault(car, 0.0);
    }

    /** 当前领跑者本圈开始时的累计时间。 */
    public double leaderTime() {
        return standings.isEmpty() ? 0 : timeOf(standings.get(0));
    }

    /** 某辆车落后领先者多少秒。 */
    public double gapToLeader(Car car) {
        return timeOf(car) - leaderTime();
    }

    /** 某辆车与身前车的差距；没有前车时返回正无穷。 */
    public double gapAhead(Car car) {
        int index = standings.indexOf(car);
        if (index <= 0) {
            return Double.POSITIVE_INFINITY;
        }
        return timeOf(car) - timeOf(standings.get(index - 1));
    }

    /** 某辆车当前的名次（1 开始），未在榜上返回 0。 */
    public int positionOf(Car car) {
        for (int i = 0; i < standings.size(); i++) {
            if (standings.get(i) == car) {
                return i + 1;
            }
        }
        return 0;
    }

    /** 排在 car 前面最近的那辆车，没有则返回 null。 */
    public Car carAhead(Car car) {
        int index = standings.indexOf(car);
        return index > 0 ? standings.get(index - 1) : null;
    }
}
