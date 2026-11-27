package com.endurance.sim;

import com.endurance.model.TireCompound;

/**
 * 进站决策：换成什么胎、加多少油。
 *
 * <p>{@code compound} 为 {@code null} 表示**只加油、不换胎**——
 * 这是耐力赛里真实存在的玩法：轮胎还能跑，就没必要为换胎多花那两三秒。
 * 代价是磨损继续累积，胎温也不会重置。</p>
 *
 * @param compound  新轮胎配方；{@code null} 表示不换胎
 * @param fuelToAdd 加油量（kg），0 表示不加油
 */
public record PitDecision(TireCompound compound, double fuelToAdd) {

    public PitDecision {
        if (fuelToAdd < 0) {
            throw new IllegalArgumentException("加油量不能为负");
        }
        if (compound == null && fuelToAdd <= 0) {
            throw new IllegalArgumentException("进站总得干点什么：要么换胎，要么加油");
        }
    }

    /** 只换胎不加油。 */
    public static PitDecision tiresOnly(TireCompound compound) {
        return new PitDecision(compound, 0);
    }

    /** 只加油，不换胎。 */
    public static PitDecision fuelOnly(double fuelToAdd) {
        return new PitDecision(null, fuelToAdd);
    }

    /** 这次进站换不换胎。 */
    public boolean changesTires() {
        return compound != null;
    }

    public String describe() {
        String fuel = fuelToAdd > 0 ? Math.round(fuelToAdd) + "kg" : "";
        if (compound == null) {
            return "不换胎" + (fuel.isEmpty() ? "" : " · 加 " + fuel);
        }
        return compound.displayName() + (fuel.isEmpty() ? "" : " + " + fuel);
    }
}
