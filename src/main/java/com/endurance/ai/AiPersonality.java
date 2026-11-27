package com.endurance.ai;

/**
 * AI 性格：决定它怎么看待"风险和收益"。
 *
 * <p>三种机制拉开差距：</p>
 * <ul>
 *   <li>{@code varianceWeight}：怎么给一组推演结果打分（保守厌恶方差，赌徒偏爱方差）</li>
 *   <li>{@code pitBias}：给"不进站"这个选项额外加多少分（正数＝更倾向于进站）</li>
 *   <li>{@code riskyFuel}：是否愿意为了减重而少加油（赌徒专属）</li>
 * </ul>
 */
public enum AiPersonality {

    /** 保守：宁可早换胎，也不愿在烂胎上冒险。 */
    CONSERVATIVE("保守", 0.12, 0.30, false),

    /** 均衡：只看期望值。 */
    BALANCED("均衡", 0.0, 0.0, false),

    /** 激进：能拖就拖，相信自己的轮胎能撑住。 */
    AGGRESSIVE("激进", -0.12, -0.30, false),

    /** 赌徒：拖得最狠，还愿意少加油换轻车，赌最后几圈不出事。 */
    GAMBLER("赌徒", -0.35, -0.45, true);

    private final String displayName;
    /** 打分的方差权重，负数表示偏好高风险。 */
    private final double varianceWeight;
    /** "不进站"选项的加分，正数表示更保守。 */
    private final double pitBias;
    /** 是否愿意烧掉安全余量、少加油减重。 */
    private final boolean riskyFuel;

    AiPersonality(String displayName, double varianceWeight, double pitBias, boolean riskyFuel) {
        this.displayName = displayName;
        this.varianceWeight = varianceWeight;
        this.pitBias = pitBias;
        this.riskyFuel = riskyFuel;
    }

    public String displayName() {
        return displayName;
    }

    public double pitBias() {
        return pitBias;
    }

    public boolean riskyFuel() {
        return riskyFuel;
    }

    /**
     * 用一组推演样本给候选方案打分（越小越好）。
     *
     * @param samples 每个样本是该方案下的"名次 + 差距惩罚"
     */
    public double score(double[] samples) {
        if (samples.length == 0) {
            return Double.MAX_VALUE;
        }
        double mean = 0;
        for (double value : samples) {
            mean += value;
        }
        mean /= samples.length;

        double variance = 0;
        for (double value : samples) {
            variance += (value - mean) * (value - mean);
        }
        double stdDev = Math.sqrt(variance / samples.length);
        return mean + varianceWeight * stdDev;
    }

    /** 保守派在轮胎还很新的时候不会考虑进站。 */
    public boolean willingToPitEarly(double wear) {
        return this != CONSERVATIVE || wear > 0.45;
    }

    public static AiPersonality parse(String text) {
        if (text == null) {
            return BALANCED;
        }
        return switch (text.toLowerCase()) {
            case "conservative" -> CONSERVATIVE;
            case "aggressive" -> AGGRESSIVE;
            case "gambler" -> GAMBLER;
            default -> BALANCED;
        };
    }
}
