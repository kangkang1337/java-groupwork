package com.endurance.sim;

import com.endurance.model.TireCompound;
import com.endurance.model.Weather;

/**
 * 某一圈某一辆车的比赛过程记录，用于复盘、圈速曲线与回放。
 *
 * @param lap       圈号（从 1 开始）
 * @param carNumber 车号
 * @param lapTime   本圈用时（秒）
 * @param position  本圈结束时的名次
 * @param compound  本圈使用的轮胎
 * @param tireWear  本圈结束时的磨损（0~1）
 * @param tireTemp  本圈结束时的胎温
 * @param fuelKg    本圈结束时的剩余油量
 * @param weather   本圈天气
 * @param stint     段号（第几套胎，0 起）
 * @param safetyCar 本圈是否在安全车期间
 */
public record LapResult(
        int lap,
        int carNumber,
        double lapTime,
        int position,
        TireCompound compound,
        double tireWear,
        double tireTemp,
        double fuelKg,
        Weather weather,
        int stint,
        boolean safetyCar) {
}
