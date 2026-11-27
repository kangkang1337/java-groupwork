package com.endurance.strategy;

import com.endurance.model.Car;
import com.endurance.sim.PitDecision;
import com.endurance.sim.RaceContext;

import java.util.Map;
import java.util.TreeMap;

/**
 * 预设进站脚本：在第几圈、换什么胎、加多少油。
 *
 * <p>第一步用它验证仿真内核——同一套策略跑两次结果应该一致，
 * 换策略则应该跑出明显差异。用 TreeMap 保证打印时按圈号有序。</p>
 */
public class PlannedStopStrategy implements Strategy {

    private final Map<Integer, PitDecision> stops = new TreeMap<>();
    private final String name;

    public PlannedStopStrategy(String name) {
        this.name = name;
    }

    /** 登记一次进站。 */
    public PlannedStopStrategy at(int lap, PitDecision decision) {
        stops.put(lap, decision);
        return this;
    }

    @Override
    public PitDecision decide(RaceContext context, Car car) {
        return stops.get(context.lap());
    }

    @Override
    public String name() {
        return name;
    }

    public Map<Integer, PitDecision> stops() {
        return stops;
    }
}
