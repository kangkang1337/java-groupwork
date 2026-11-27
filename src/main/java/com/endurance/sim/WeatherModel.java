package com.endurance.sim;

import com.endurance.model.Weather;

import java.util.Random;

/**
 * 天气模型：用马尔可夫链逐圈转移。
 *
 * <p>每次 {@link #next(Weather)} 都会以配置好的概率在干燥 / 湿滑 / 大雨之间跳转。
 * 概率取得比较低，保证大多数比赛是干地，但偶尔会来一场"要不要赌雨"的考验。</p>
 */
public class WeatherModel {

    private static final double DRY_TO_DAMP = 0.010;
    private static final double DRY_TO_WET = 0.0015;
    private static final double DAMP_TO_DRY = 0.16;
    private static final double DAMP_TO_WET = 0.07;
    private static final double WET_TO_DAMP = 0.14;
    private static final double WET_TO_DRY = 0.02;

    private final Random random;
    private final boolean enabled;
    /** 赛道的降雨倾向：>1 更容易下雨，<1 更干燥（斯帕 vs COTA）。 */
    private final double rainBias;

    public WeatherModel(Random random, boolean enabled) {
        this(random, enabled, 1.0);
    }

    public WeatherModel(Random random, boolean enabled, double rainBias) {
        this.random = random;
        this.enabled = enabled;
        this.rainBias = Math.max(0.1, rainBias);
    }

    /**
     * 推进一圈天气。
     *
     * @return 下一圈的天气（可能与当前相同）
     */
    public Weather next(Weather current) {
        if (!enabled) {
            return current;
        }
        double roll = random.nextDouble();
        return switch (current) {
            case DRY -> {
                // 变湿的两个方向都按赛道气候缩放，变干不受影响
                if (roll < DRY_TO_WET * rainBias) {
                    yield Weather.WET;
                }
                if (roll < (DRY_TO_WET + DRY_TO_DAMP) * rainBias) {
                    yield Weather.DAMP;
                }
                yield Weather.DRY;
            }
            case DAMP -> {
                if (roll < DAMP_TO_WET) {
                    yield Weather.WET;
                }
                if (roll < DAMP_TO_WET + DAMP_TO_DRY) {
                    yield Weather.DRY;
                }
                yield Weather.DAMP;
            }
            case WET -> {
                if (roll < WET_TO_DRY) {
                    yield Weather.DRY;
                }
                if (roll < WET_TO_DRY + WET_TO_DAMP) {
                    yield Weather.DAMP;
                }
                yield Weather.WET;
            }
        };
    }
/** 未来 {@code laps} 圈内出现降雨的概率（0~1）。 */
    public double rainChance(int laps) {
        if (!enabled || laps <= 0) {
            return 0;
        }
        double perLap = DRY_TO_DAMP + DRY_TO_WET;
        return Math.min(1.0, 1 - Math.pow(1 - perLap, laps));
    }
}