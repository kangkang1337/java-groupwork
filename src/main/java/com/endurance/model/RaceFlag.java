package com.endurance.model;

/**
 * 赛道旗语（按 FIA WEC 运动规则）。
 *
 * <p>旗语是耐力赛里赛会跟车手沟通的主要手段，比文字播报更快、更规范。
 * 本项目只实现"会影响策略决策"的那几种，其余留在枚举里备用。</p>
 */
public enum RaceFlag {

    /** 绿旗：赛道畅通。 */
    GREEN("绿旗", "赛道畅通", "#22C55E"),

    /** 黄旗：前方有事故，禁止超车，需减速。 */
    YELLOW("黄旗", "局部黄旗 · 禁止超车", "#FACC15"),

    /** 双黄旗：危险加大，准备停车。 */
    DOUBLE_YELLOW("双黄旗", "严重危险 · 准备停车", "#F59E0B"),

    /** 红旗：比赛中止，全部车辆减速返回维修区。 */
    RED("红旗", "比赛中止 · 等待重启", "#EF4444"),

    /** FCY：全场限速（WEC 特色规则，安全车之外的另一种全场减速方式）。 */
    FCY("FCY", "全场限速 · 禁止超车", "#EAB308"),

    /** 蓝旗：被套圈的车必须让行。 */
    BLUE("蓝旗", "被套圈 · 立即让车", "#3B82F6"),

    /** 白旗：前方有慢车。 */
    WHITE("白旗", "前方慢车", "#E5E7EB"),

    /** 黑橙旗（肉丸旗）：机械故障，必须进站。 */
    BLACK_ORANGE("黑橙旗", "机械故障 · 必须进站", "#F97316"),

    /** 格子旗：比赛结束。 */
    CHEQUERED("格子旗", "比赛结束", "#EAF0F7");

    private final String displayName;
    private final String meaning;
    private final String colorHex;

    RaceFlag(String displayName, String meaning, String colorHex) {
        this.displayName = displayName;
        this.meaning = meaning;
        this.colorHex = colorHex;
    }

    public String displayName() {
        return displayName;
    }

    /** 一句话说明这条旗语对车手意味着什么。 */
    public String meaning() {
        return meaning;
    }

    public String colorHex() {
        return colorHex;
    }

    /** 是否需要全场减速/禁止超车这类限制。 */
    public boolean isRestrictive() {
        return this == YELLOW || this == DOUBLE_YELLOW || this == RED
                || this == FCY || this == BLACK_ORANGE;
    }
}
