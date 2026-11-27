package com.endurance.strategy;

import com.endurance.model.Car;
import com.endurance.sim.PitDecision;
import com.endurance.sim.RaceContext;

/**
 * 玩家策略：把界面上排队的指令交给仿真器。
 *
 * <p>仿真器每圈开始前调用一次 {@link #decide}，如果队列里有指令就执行一次并清空——
 * 所以"点了进站"只会在下一圈生效一次，不会连续进站。</p>
 */
public class PlayerStrategy implements Strategy {

    private PitDecision pending;
    private Runnable onApplied;

    /** 排队一条进站指令（下一圈生效）。 */
    public void queue(PitDecision decision) {
        this.pending = decision;
    }

    /** 撤回排队的指令。 */
    public void clear() {
        this.pending = null;
    }

    public boolean hasPending() {
        return pending != null;
    }

    public PitDecision peek() {
        return pending;
    }

    /** 指令被消费时的回调（界面用来清空选中状态）。 */
    public void setOnApplied(Runnable onApplied) {
        this.onApplied = onApplied;
    }

    @Override
    public PitDecision decide(RaceContext context, Car car) {
        if (pending == null) {
            return null;
        }
        PitDecision decision = pending;
        pending = null;
        if (onApplied != null) {
            onApplied.run();
        }
        return decision;
    }

    @Override
    public String name() {
        return "你的指令";
    }
}
