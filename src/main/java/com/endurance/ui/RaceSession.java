package com.endurance.ui;

import com.endurance.sim.RaceSimulator;
import javafx.animation.AnimationTimer;

/**
 * 比赛节奏控制器：把"一圈一步"的仿真包装成连续动画。
 *
 * <p>每帧推进一个 {@code phase}（0~1 表示当前圈的进度），phase 走满就调用
 * {@link RaceSimulator#step()} 推进一圈。倍速通过放大每帧的 phase 增量实现，
 * 因此 1x / 2x / 4x 只是同一套逻辑跑得快慢不同。</p>
 */
public class RaceSession {

    private final RaceSimulator simulator;
    private final AnimationTimer timer;

    /** 1x 速度下一圈消耗的真实毫秒数。 */
    private double lapDurationMs = 17000;
    private double speed = 1.0;
    private double phase;
    private long lastNanos = -1;
    private boolean running;
    private Runnable onUpdate;

    /** 暖胎圈是否还没结束。 */
    private boolean formationLap = true;

    /** 暖胎圈播放进度 0~1。 */
    private double formationProgress;

    public RaceSession(RaceSimulator simulator) {
        this.simulator = simulator;
        this.timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                tick(now);
            }
        };
    }

    public void setOnUpdate(Runnable onUpdate) {
        this.onUpdate = onUpdate;
    }

    public void start() {
        running = true;
        lastNanos = -1;
        timer.start();
    }

    public void stop() {
        running = false;
        timer.stop();
    }

    /** 暂停：连 AnimationTimer 一起停，别让界面在暂停时还每帧重绘。 */
    public void pause() {
        running = false;
        timer.stop();
    }

    public void resume() {
        if (!simulator.isFinished()) {
            running = true;
            lastNanos = -1;
            timer.start();   // pause() 停了计时器，这里要重新启动
        }
    }

    public boolean isRunning() {
        return running;
    }

    public boolean isFinished() {
        return simulator.isFinished();
    }

    public double getSpeed() {
        return speed;
    }

    public void setSpeed(double speed) {
        this.speed = Math.max(0.25, speed);
    }

    public double phase() {
        return phase;
    }

    /** 是不是还在暖胎圈。 */
    public boolean isFormationLap() {
        return formationLap;
    }

    /** 暖胎圈进度 0~1。 */
    public double formationProgress() {
        return formationProgress;
    }

    /** 跳过暖胎圈，直接发车。 */
    public void skipFormation() {
        if (!formationLap) {
            return;
        }
        formationLap = false;
        formationProgress = 0;
        simulator.markRaceStart();
        if (onUpdate != null) {
            onUpdate.run();
        }
    }

    /** 手动推进一圈（暂停时用）。 */
    public void stepOnce() {
        phase = 0;
        if (formationLap) {
            // 暖胎圈里点「下一圈」= 跳过暖胎圈并发车，这一下照样推进第一圈
            skipFormation();
        }
        if (simulator.isFinished()) {
            return;
        }
        simulator.step();
        if (onUpdate != null) {
            onUpdate.run();
        }
    }

    /** 直接跑到结束（自检/截图用）。 */
    public void runToEnd() {
        if (formationLap) {
            skipFormation();
        }
        while (simulator.step()) {
            // 一直推进
        }
        phase = 0;
        running = false;
        if (onUpdate != null) {
            onUpdate.run();
        }
    }

    /** 推进指定圈数（自检/截图用）。 */
    public void advance(int laps) {
        // laps > 0 才算"要推进比赛"，laps == 0 留着暖胎圈给截图用
        if (formationLap && laps > 0) {
            skipFormation();
        }
        for (int i = 0; i < laps && !simulator.isFinished(); i++) {
            simulator.step();
        }
        if (onUpdate != null) {
            onUpdate.run();
        }
    }

    private void tick(long now) {
        if (lastNanos < 0) {
            lastNanos = now;
            return;
        }
        double deltaMs = (now - lastNanos) / 1_000_000.0;
        lastNanos = now;
        // 窗口最小化/切后台回来时不要一次跳好几圈
        if (deltaMs > 250) {
            deltaMs = 250;
        }
        if (running && formationLap) {
            // 暖胎圈：车辆按队形跑一圈，不计圈也不磨损，让玩家先看清局面
            formationProgress += deltaMs * speed / lapDurationMs;
            if (formationProgress >= 1.0) {
                formationLap = false;
                formationProgress = 0;
                simulator.markRaceStart();
            }
        } else if (running) {
            phase += deltaMs * speed / lapDurationMs;
            while (phase >= 1.0) {
                phase -= 1.0;
                if (!simulator.step()) {
                    running = false;
                    phase = 0;
                    break;
                }
            }
        }
        if (onUpdate != null) {
            onUpdate.run();
        }
    }
}
