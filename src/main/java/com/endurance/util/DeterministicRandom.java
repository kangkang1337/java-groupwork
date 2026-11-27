package com.endurance.util;

import java.util.Random;

/**
 * 可序列化的确定性随机数发生器（SplitMix64）。
 *
 * <p>为什么要自己写：存档要能"从第 20 圈原样接着跑"，就必须能把随机数发生器的
 * 内部状态存下来再恢复。{@link java.util.Random} 的状态藏在私有字段里，
 * 官方也没有导出接口；而 {@code nextGaussian()} 还会缓存一个值，
 * 就算能拿到种子也对不齐。</p>
 *
 * <p>所以换成一个状态只有一个 {@code long} 的实现：
 * 存档时写出 {@link #state()}，读档时 {@link #setState(long)} 即可精确续上。
 * {@code nextGaussian} 也改成无缓存的 Box-Muller，保证不残留隐藏状态。</p>
 */
public final class DeterministicRandom extends Random {

    private static final long GAMMA = 0x9E3779B97F4A7C15L;
    private static final long MIX1 = 0xBF58476D1CE4E5B9L;
    private static final long MIX2 = 0x94D049BB133111EBL;

    private long state;

    public DeterministicRandom(long seed) {
        super(0);
        this.state = seed;
    }

    /** 当前内部状态，用于存档。 */
    public long state() {
        return state;
    }

    /** 从存档恢复内部状态。 */
    public void setState(long state) {
        this.state = state;
    }

    @Override
    public long nextLong() {
        state += GAMMA;
        long z = state;
        z = (z ^ (z >>> 30)) * MIX1;
        z = (z ^ (z >>> 27)) * MIX2;
        return z ^ (z >>> 31);
    }

    @Override
    public int nextInt() {
        return (int) (nextLong() >>> 32);
    }

    @Override
    public int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound must be positive");
        }
        // 无偏取模：拒绝采样，避免低位偏差
        long limit = Long.MAX_VALUE - (Long.MAX_VALUE % bound);
        while (true) {
            long value = nextLong() >>> 1;
            if (value < limit) {
                return (int) (value % bound);
            }
        }
    }

    @Override
    public double nextDouble() {
        return (nextLong() >>> 11) * 0x1.0p-53;
    }

    @Override
    public double nextGaussian() {
        // 无缓存版本：不保留"下一次"的高斯值，状态永远只有 state
        double u1 = Math.max(1e-12, nextDouble());
        double u2 = nextDouble();
        return Math.sqrt(-2.0 * Math.log(u1)) * Math.cos(2.0 * Math.PI * u2);
    }
}
