package com.endurance.strategy;

import com.endurance.model.Car;
import com.endurance.sim.RaceContext;

/**
 * 策略接口：每圈开始前被调用一次，返回进站决策或 null（不进站）。
 *
 * <p>玩家、预设脚本、AI 都实现这个接口，仿真器不需要知道对面是人还是机器。</p>
 */
public interface Strategy {

    /**
     * 决定本圈是否进站。
     *
     * @param context 赛场快照
     * @param car     被决策的车
     * @return 进站决策；{@code null} 表示继续跑
     */
    com.endurance.sim.PitDecision decide(RaceContext context, Car car);

    /** 策略名称，用于界面显示。 */
    default String name() {
        return getClass().getSimpleName();
    }
}
