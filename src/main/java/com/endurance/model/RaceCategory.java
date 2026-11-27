package com.endurance.model;

/**
 * 比赛分组（比赛级别）。
 *
 * <p>WEC 的耐力赛同一场里有多个组别同场竞技，各自算自己的名次。
 * 现在只启用了 Hypercar，但分组是本项目从第一天就要留好的接口：
 * LMGT3 和 LMP2 的车队直接写进 teams.json 就能进同一场比赛，
 * 名次、复盘、数据表都会自动按组别分开算。</p>
 *
 * <p>分站层面还有"这一站设不设某组"的差异——比如某些分站只有 Hypercar + LMGT3，
 * 另一些冲刺赛会多一个 LMP2。这部分交给 {@link Track#categories()}。</p>
 */
public enum RaceCategory {

    /** 顶级原型车组。 */
    HYPERCAR("Hypercar", "HYP"),

    /** GT3 组。 */
    LMGT3("LMGT3", "GT3"),

    /** LMP2 组（部分分站参加）。 */
    LMP2("LMP2", "LMP2");

    private final String displayName;
    private final String shortCode;

    RaceCategory(String displayName, String shortCode) {
        this.displayName = displayName;
        this.shortCode = shortCode;
    }

    public String displayName() {
        return displayName;
    }

    /** 界面里省地方用的短标签。 */
    public String shortCode() {
        return shortCode;
    }

    /** 宽松解析：认不出来就归到 Hypercar，不让配置里的手误把程序弄崩。 */
    public static RaceCategory parse(String text) {
        if (text == null || text.isBlank()) {
            return HYPERCAR;
        }
        String key = text.trim().toUpperCase(java.util.Locale.ROOT).replace("-", "").replace("_", "");
        return switch (key) {
            case "LMGT3", "GT3", "LMGTE" -> LMGT3;
            case "LMP2" -> LMP2;
            default -> HYPERCAR;
        };
    }
}
