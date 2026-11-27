package com.endurance.ui;

import com.endurance.model.Car;

/**
 * 界面用的格式化工具。
 */
public final class Format {

    private Format() {
        throw new AssertionError("工具类不允许实例化");
    }

    /** 单圈时间：1:43.092。 */
    public static String lap(double seconds) {
        if (seconds <= 0 || seconds == Double.MAX_VALUE) {
            return "—";
        }
        int total = (int) Math.round(seconds * 1000);
        int ms = total % 1000;
        total /= 1000;
        int s = total % 60;
        int m = total / 60;
        return String.format("%d:%02d.%03d", m, s, ms);
    }

    /** 总时间：1:45:13.515。 */
    public static String total(double seconds) {
        if (seconds <= 0) {
            return "—";
        }
        int value = (int) Math.round(seconds * 1000);
        int ms = value % 1000;
        value /= 1000;
        int s = value % 60;
        value /= 60;
        int m = value % 60;
        int h = value / 60;
        return String.format("%d:%02d:%02d.%03d", h, m, s, ms);
    }

    /** 落后差距：+12.7s。 */
    public static String gap(double seconds) {
        if (seconds <= 0.0005) {
            return "—";
        }
        return String.format("+%.1fs", seconds);
    }

    /** 车手短标签：#7 陈奕舟。 */
    public static String carLabel(Car car) {
        return "#" + car.number() + " " + car.driverName();
    }
}
