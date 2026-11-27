package com.endurance.model;

/**
 * 赛道。
 *
 * <p>基准圈速定义为"干地、最佳轮胎、空油、零磨损"的理想圈速，
 * 所有其它因素都是在这个基准上的加减法，方便调参和理解。</p>
 *
 * <p>赛道自带两个"难度旋钮"：{@code defaultLaps} 决定比赛长度，
 * {@code tireWearScale} 决定这条赛道有多吃胎。同一条赛道换个磨损倍率，
 * 最优策略就会从"一停到底"变成"三停冲刺"。</p>
 */
public class Track {

    private final String id;
    private final String name;
    private final int lengthMeters;
    private final double baseLapSeconds;
    private final int cornerCount;
    private final double trackTempCelsius;
    private final double fuelPerLap;
    /** 这条赛道的默认比赛圈数。 */
    private final int defaultLaps;
    /** 轮胎磨损倍率。 */
    private final double tireWearScale;
    /** 赛历轮次（1 起）；0 表示不在官方赛历里。 */
    private final int round;
    /** 国家/地区代码（QAT / ITA / BEL …），界面显示用。 */
    private final String country;
    /** 分站正式名称，例如「6 Hours of Fuji」。 */
    private final String event;
    /** 这一站设哪些组别（耐力赛通常 Hypercar + LMGT3，部分分站加 LMP2）。 */
    private final java.util.List<RaceCategory> categories;

    /** 这一站允许用的干胎配方（官方规则：普通分站两种，勒芒三种）。 */
    private final java.util.List<String> dryCompounds;

    /** 赛道的降雨倾向（1.0 为基准；斯帕 1.8，COTA 0.7）。 */
    private final double rainBias;

    public Track(String id, String name, int lengthMeters, double baseLapSeconds,
                 int cornerCount, double trackTempCelsius, double fuelPerLap,
                 int defaultLaps, double tireWearScale) {
        this(id, name, lengthMeters, baseLapSeconds, cornerCount, trackTempCelsius,
                fuelPerLap, defaultLaps, tireWearScale,
                java.util.List.of(RaceCategory.HYPERCAR));
    }

    public Track(String id, String name, int lengthMeters, double baseLapSeconds,
                 int cornerCount, double trackTempCelsius, double fuelPerLap,
                 int defaultLaps, double tireWearScale,
                 java.util.List<RaceCategory> categories) {
        this(id, name, lengthMeters, baseLapSeconds, cornerCount, trackTempCelsius,
                fuelPerLap, defaultLaps, tireWearScale, categories, 0, "", name);
    }

    public Track(String id, String name, int lengthMeters, double baseLapSeconds,
                 int cornerCount, double trackTempCelsius, double fuelPerLap,
                 int defaultLaps, double tireWearScale,
                 java.util.List<RaceCategory> categories,
                 int round, String country, String event) {
        this(id, name, lengthMeters, baseLapSeconds, cornerCount, trackTempCelsius,
                fuelPerLap, defaultLaps, tireWearScale, categories, round, country, event,
                java.util.List.of("soft", "medium", "hard"));
    }

    public Track(String id, String name, int lengthMeters, double baseLapSeconds,
                 int cornerCount, double trackTempCelsius, double fuelPerLap,
                 int defaultLaps, double tireWearScale,
                 java.util.List<RaceCategory> categories,
                 int round, String country, String event,
                 java.util.List<String> dryCompounds) {
        this(id, name, lengthMeters, baseLapSeconds, cornerCount, trackTempCelsius,
                fuelPerLap, defaultLaps, tireWearScale, categories, round, country, event,
                dryCompounds, 1.0);
    }

    public Track(String id, String name, int lengthMeters, double baseLapSeconds,
                 int cornerCount, double trackTempCelsius, double fuelPerLap,
                 int defaultLaps, double tireWearScale,
                 java.util.List<RaceCategory> categories,
                 int round, String country, String event,
                 java.util.List<String> dryCompounds, double rainBias) {
        this.id = id;
        this.name = name;
        this.lengthMeters = lengthMeters;
        this.baseLapSeconds = baseLapSeconds;
        this.cornerCount = cornerCount;
        this.trackTempCelsius = trackTempCelsius;
        this.fuelPerLap = fuelPerLap;
        this.defaultLaps = defaultLaps;
        this.tireWearScale = tireWearScale;
        this.categories = categories == null || categories.isEmpty()
                ? java.util.List.of(RaceCategory.HYPERCAR)
                : java.util.List.copyOf(categories);
        this.round = round;
        this.country = country == null ? "" : country;
        this.event = event == null || event.isBlank() ? name : event;
        this.rainBias = rainBias <= 0 ? 1.0 : rainBias;
        this.dryCompounds = dryCompounds == null || dryCompounds.isEmpty()
                ? java.util.List.of("soft", "medium", "hard")
                : java.util.List.copyOf(dryCompounds);
    }

    /** 这一站允许用的干胎配方 id 列表（官方规则：普通分站两种，勒芒三种）。 */
    public java.util.List<String> dryCompounds() {
        return dryCompounds;
    }

    /** 这一站的降雨倾向（1.0 = 平均值）。 */
    public double rainBias() {
        return rainBias;
    }

    /** 某个配方这一站能不能用；雨胎不受限制。 */
    public boolean allowsCompound(String tireId) {
        return dryCompounds.contains(tireId)
                || "intermediate".equals(tireId)
                || "wet".equals(tireId);
    }

    /** 这一站设的组别。 */
    public java.util.List<RaceCategory> categories() {
        return categories;
    }

    public boolean hasCategory(RaceCategory category) {
        return categories.contains(category);
    }

    /** 赛历轮次（1 起，0 表示非官方分站）。 */
    public int round() {
        return round;
    }

    /** 国家/地区代码。 */
    public String country() {
        return country;
    }

    /** 分站正式名称。 */
    public String event() {
        return event;
    }

    /** 「R3 · 6 Hours of Le Mans」这种日历标签。 */
    public String calendarLabel() {
        return round > 0 ? ("R" + round + " · " + event) : event;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public int lengthMeters() {
        return lengthMeters;
    }

    public double baseLapSeconds() {
        return baseLapSeconds;
    }

    public int cornerCount() {
        return cornerCount;
    }

    public double trackTempCelsius() {
        return trackTempCelsius;
    }

    public double fuelPerLap() {
        return fuelPerLap;
    }

    public int defaultLaps() {
        return defaultLaps;
    }

    public double tireWearScale() {
        return tireWearScale;
    }

    /** 跑完全程大概要多少油（kg）。 */
    public double raceFuelDemand() {
        return fuelPerLap * defaultLaps;
    }

    /** 赛道信息的一行摘要。 */
    public String summary() {
        return String.format("%s · %.2f km · %d 弯 · 基准 %.2fs · 气温 %.0f°C · 油耗 %.2f kg/圈 · 磨损×%.2f",
                name, lengthMeters / 1000.0, cornerCount, baseLapSeconds,
                trackTempCelsius, fuelPerLap, tireWearScale);
    }

    @Override
    public String toString() {
        return name;
    }
}
