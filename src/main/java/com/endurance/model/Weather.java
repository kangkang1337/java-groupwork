package com.endurance.model;

/**
 * 天气档位。
 *
 * <p>三个档位足以撑起"赌不赌雨"的博弈，又不会让玩家记不住规则。</p>
 */
public enum Weather {

    DRY("干燥", 0.0, 1.0),
    DAMP("湿滑", 3.5, 1.45),
    WET("大雨", 8.0, 1.9);

    private final String displayName;
    /** 相对干地的基础掉速（秒/圈）。 */
    private final double basePenalty;
    /** 该天气下的轮胎磨损倍率。 */
    private final double wearMultiplier;

    Weather(String displayName, double basePenalty, double wearMultiplier) {
        this.displayName = displayName;
        this.basePenalty = basePenalty;
        this.wearMultiplier = wearMultiplier;
    }

    public String displayName() {
        return displayName;
    }

    public double basePenalty() {
        return basePenalty;
    }

    public double wearMultiplier() {
        return wearMultiplier;
    }

    /** 是否需要用雨胎。 */
    public boolean isWet() {
        return this != DRY;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
