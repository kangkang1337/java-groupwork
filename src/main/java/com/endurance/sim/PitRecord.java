package com.endurance.sim;

/**
 * 一次进站的记录，复盘和评分都要用。
 *
 * @param lap        发生在第几圈
 * @param carNumber  车号
 * @param compound   换上的轮胎
 * @param fuelAdded  加了多少油
 * @param cost       这次进站总共损失多少秒
 * @param tireChanged 这次有没有换胎（只加油时为 false）
 */
public record PitRecord(int lap, int carNumber, String compound, double fuelAdded, double cost,
                      boolean tireChanged) {

    /** 兼容老调用：默认按"换了胎"处理。 */
    public PitRecord(int lap, int carNumber, String compound, double fuelAdded, double cost) {
        this(lap, carNumber, compound, fuelAdded, cost, true);
    }
}
