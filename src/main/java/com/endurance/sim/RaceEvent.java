package com.endurance.sim;

/**
 * 比赛事件：进站、天气变化、机械故障、冲线等。
 */
public record RaceEvent(int lap, Type type, String message) {

    /** 事件类型。 */
    public enum Type {
        PIT_STOP("进站"),
        WEATHER_CHANGE("天气"),
        MECHANICAL_FAILURE("故障"),
        SAFETY_CAR("安全车"),
        CRASH("撞车"),
        PENALTY("判罚"),
        FLAG("旗语"),
        FUEL_EMPTY("燃油"),
        FINISH("冲线");

        private final String displayName;

        Type(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }
    }

    @Override
    public String toString() {
        return String.format("第 %3d 圈 [%s] %s", lap, type.displayName(), message);
    }
}
