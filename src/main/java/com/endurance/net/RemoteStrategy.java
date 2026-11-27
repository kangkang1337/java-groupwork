package com.endurance.net;

import com.endurance.model.Car;
import com.endurance.sim.PitDecision;
import com.endurance.sim.RaceContext;
import com.endurance.strategy.Strategy;

/**
 * 服务端替某个联机玩家持有的策略槽。
 *
 * <p>玩家在客户端点"进站"，服务器验完参数后把 {@link PitDecision} 放进这里，
 * 仿真器下一圈取走执行。玩家的输入只影响这一个槽位，碰不到任何比赛状态——
 * 这就是"服务端权威"的核心：客户端只能提交意图，计算结果永远在服务端。</p>
 */
public class RemoteStrategy implements Strategy {

    private final int carNumber;
    private volatile PitDecision pending;
    private volatile boolean connected = true;

    public RemoteStrategy(int carNumber) {
        this.carNumber = carNumber;
    }

    public int carNumber() {
        return carNumber;
    }

    /** 收到一条合法指令。 */
    public void queue(PitDecision decision) {
        this.pending = decision;
    }

    public void clear() {
        this.pending = null;
    }

    public boolean hasPending() {
        return pending != null;
    }

    /** 玩家掉线时清空未执行的指令，避免"人走了车还自己进站"。 */
    public void setConnected(boolean connected) {
        this.connected = connected;
        if (!connected) {
            pending = null;
        }
    }

    public boolean isConnected() {
        return connected;
    }

    @Override
    public PitDecision decide(RaceContext context, Car car) {
        if (!connected) {
            return null;
        }
        PitDecision decision = pending;
        pending = null;
        return decision;
    }

    @Override
    public String name() {
        return "玩家#" + carNumber;
    }
}
