package com.endurance.model;

/**
 * 车手：车号、姓名、驾驶激进度、起步轮胎，以及"是不是玩家操控的车"。
 *
 * <p>激进度 0.60 保守省胎、1.00 全力推进，直接影响圈速、胎温与磨损。</p>
 */
public record Driver(int number, String name, double aggression, String startTireId, boolean player) {
}
