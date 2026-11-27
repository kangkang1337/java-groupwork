package com.endurance.model;

import java.util.Collections;
import java.util.List;

/**
 * 车队。
 *
 * <p>速度偏移、可靠性、性格参数共同决定这支队"好不好开"。
 * 车手名单也从配置读取，加一支车队等于往 JSON 里加一段。</p>
 */
public class Team {

    private final String id;
    private final String name;
    private final String shortName;
    /** 基础圈速偏移（负数更快，单位秒）。 */
    private final double paceOffset;
    /** 可靠性 0~1，越高越不容易机械故障。 */
    private final double reliability;
    /** 界面用颜色。 */
    private final String colorHex;
    /** 策略性格：0 保守，1 激进。 */
    private final double aggressionBias;
    /** 车手名单。 */
    private final List<Driver> drivers;
    /** 所属组别（Hypercar / LMGT3 / LMP2）。 */
    private final com.endurance.model.RaceCategory category;
    /** AI 性格标识（conservative / balanced / aggressive / gambler）。 */
    private final String personality;

    public Team(String id, String name, String shortName, double paceOffset,
                double reliability, String colorHex, double aggressionBias, List<Driver> drivers,
                String personality, com.endurance.model.RaceCategory category) {
        this.id = id;
        this.name = name;
        this.shortName = shortName;
        this.paceOffset = paceOffset;
        this.reliability = reliability;
        this.colorHex = colorHex;
        this.aggressionBias = aggressionBias;
        this.drivers = Collections.unmodifiableList(new java.util.ArrayList<>(drivers));
        this.personality = personality == null ? "balanced" : personality;
        this.category = category == null ? com.endurance.model.RaceCategory.HYPERCAR : category;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String shortName() {
        return shortName;
    }

    public double paceOffset() {
        return paceOffset;
    }

    public double reliability() {
        return reliability;
    }

    public String colorHex() {
        return colorHex;
    }

    public double aggressionBias() {
        return aggressionBias;
    }

    /** 所属组别。 */
    public com.endurance.model.RaceCategory category() {
        return category;
    }

    public List<Driver> drivers() {
        return drivers;
    }

    public String personality() {
        return personality;
    }

    @Override
    public String toString() {
        return name;
    }
}
