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
                double wet = transitionProbability(Weather.DRY, Weather.WET);
                double damp = transitionProbability(Weather.DRY, Weather.DAMP);
                if (roll < wet) {
                    yield Weather.WET;
                }
                if (roll < wet + damp) {
                    yield Weather.DAMP;
                }
                yield Weather.DRY;
            }
            case DAMP -> {
                double wet = transitionProbability(Weather.DAMP, Weather.WET);
                double dry = transitionProbability(Weather.DAMP, Weather.DRY);
                if (roll < wet) {
                    yield Weather.WET;
                }
                if (roll < wet + dry) {
                    yield Weather.DRY;
                }
                yield Weather.DAMP;
            }
            case WET -> {
                double dry = transitionProbability(Weather.WET, Weather.DRY);
                double damp = transitionProbability(Weather.WET, Weather.DAMP);
                if (roll < dry) {
                    yield Weather.DRY;
                }
                if (roll < dry + damp) {
                    yield Weather.DAMP;
                }
                yield Weather.WET;
            }
        };
    }
    /** 未来 {@code laps} 圈内出现湿天的概率（0~1），按当前天气和赛道转移概率计算。 */
    public double rainChance(int laps) {
        return rainChance(Weather.DRY, laps);
    }

    /** 未来 {@code laps} 圈内至少出现一圈湿天的马尔可夫链概率（0~1）。 */
    public double rainChance(Weather current, int laps) {
        if (!enabled || laps <= 0) {
            return 0;
        }
        // 要在预测区间里始终不下雨，下一圈必须先转干，之后每圈都保持干燥。
        double staysDry = transitionProbability(current, Weather.DRY);
        double dryToDry = transitionProbability(Weather.DRY, Weather.DRY);
        staysDry *= Math.pow(dryToDry, laps - 1);
        return Math.max(0, Math.min(1, 1 - staysDry));
    }

    private double transitionProbability(Weather from, Weather to) {
        return switch (from) {
            case DRY -> switch (to) {
                case DRY -> 1 - wetProbability() - dampProbability();
                case DAMP -> dampProbability();
                case WET -> wetProbability();
            };
            case DAMP -> switch (to) {
                case DRY -> DAMP_TO_DRY;
                case DAMP -> 1 - DAMP_TO_DRY - DAMP_TO_WET;
                case WET -> DAMP_TO_WET;
            };
            case WET -> switch (to) {
                case DRY -> WET_TO_DRY;
                case DAMP -> WET_TO_DAMP;
                case WET -> 1 - WET_TO_DRY - WET_TO_DAMP;
            };
        };
    }

    private double wetProbability() {
        return Math.min(1, DRY_TO_WET * rainBias);
    }

    private double dampProbability() {
        return Math.min(1 - wetProbability(), DRY_TO_DAMP * rainBias);
    }
}
