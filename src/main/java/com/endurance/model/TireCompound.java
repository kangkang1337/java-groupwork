package com.endurance.model;

/**
 * 轮胎配方。
 *
 * <p>从 JSON 加载，不再写死在代码里：新增一种配方（比如"超软胎"）只要在
 * {@code data/tires.json} 里加一段，不用改任何 Java 代码。</p>
 *
 * <p>每种配方用一组参数描述它的"性格"：干地速度、耐磨程度、工作温度窗口，
 * 以及在非理想天气下的掉速。策略博弈的本质就是在这几项之间取舍。</p>
 */
public final class TireCompound {

    private final String id;
    private final String displayName;
    /** 每圈磨损增量（1.0 表示磨穿）。 */
    private final double wearPerLap;
    /** 磨穿时相对新胎的掉速（秒）。 */
    private final double maxWearPenalty;
    /** 最佳工作温度（摄氏度）。 */
    private final double optimalTemp;
    /** 干地相对基准的圈速偏移（负值更快）。 */
    private final double dryOffset;
    /** 湿地相对该天气最佳配方的偏移。 */
    private final double dampOffset;
    /** 雨天相对该天气最佳配方的偏移。 */
    private final double wetOffset;
    /** 界面显示色。 */
    private final String colorHex;

    public TireCompound(String id, String displayName, double wearPerLap, double maxWearPenalty,
                        double optimalTemp, double dryOffset, double dampOffset, double wetOffset,
                        String colorHex) {
        this.id = id;
        this.displayName = displayName;
        this.wearPerLap = wearPerLap;
        this.maxWearPenalty = maxWearPenalty;
        this.optimalTemp = optimalTemp;
        this.dryOffset = dryOffset;
        this.dampOffset = dampOffset;
        this.wetOffset = wetOffset;
        this.colorHex = colorHex;
    }

    /** 该配方在指定天气下相对"最适合天气的配方"的圈速偏移。 */
    public double timeOffset(Weather weather) {
        return switch (weather) {
            case DRY -> dryOffset;
            case DAMP -> dampOffset;
            case WET -> wetOffset;
        };
    }

    /** 磨损带来的掉速（超线性：越到后面掉得越快）。 */
    public double wearPenalty(double wear) {
        double clamped = Math.max(0, Math.min(1.2, wear));
        return maxWearPenalty * Math.pow(clamped, 1.5);
    }

    /** 大概能跑多少圈磨穿（估算值，用于界面提示）。 */
    public int estimatedLifeLaps() {
        return (int) Math.round(1.0 / Math.max(0.001, wearPerLap));
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public double wearPerLap() {
        return wearPerLap;
    }

    public double maxWearPenalty() {
        return maxWearPenalty;
    }

    public double optimalTemp() {
        return optimalTemp;
    }

    public String colorHex() {
        return colorHex;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof TireCompound compound && compound.id.equals(id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return displayName;
    }
}
