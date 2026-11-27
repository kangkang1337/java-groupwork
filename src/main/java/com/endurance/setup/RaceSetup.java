package com.endurance.setup;

import com.endurance.ai.AiPersonality;
import com.endurance.ai.AiStrategy;
import com.endurance.ai.MonteCarloPlanner;
import com.endurance.data.GameData;
import com.endurance.model.Car;
import com.endurance.model.Driver;
import com.endurance.model.RaceConfig;
import com.endurance.model.Team;
import com.endurance.model.TireCompound;
import com.endurance.model.Track;
import com.endurance.model.Weather;
import com.endurance.strategy.PlayerStrategy;
import com.endurance.strategy.Strategy;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 赛事装配：把配置数据组装成一场可运行的比赛。
 *
 * <p>界面和命令行都走这里，保证"电脑上看到的"和"脚本跑出来的"是同一套规则。
 * 玩家车挂 {@link PlayerStrategy}（等界面上排队指令），其余车挂
 * {@link AiStrategy}（蒙特卡洛推演），所以每场都有一支会思考的对手车队。</p>
 */
public final class RaceSetup {

    private RaceSetup() {
        throw new AssertionError("工具类不允许实例化");
    }

    /**
     * 一场比赛的全部要素。
     *
     * @param config         比赛配置
     * @param strategies     每辆车对应的策略
     * @param playerCar      玩家操控的车（配置里没标注时为 null）
     * @param playerStrategy 玩家的指令通道
     * @param planner        AI 共用的蒙特卡洛线程池，换场次时要 shutdown
     */
    public record RaceLineup(RaceConfig config, Map<Car, Strategy> strategies,
                             Car playerCar, PlayerStrategy playerStrategy,
                             MonteCarloPlanner planner) {

        /** 结束这场比赛时释放 AI 线程。 */
        public void close() {
            planner.shutdown();
        }
    }

    /** 组装一场比赛（界面用：玩家车等指令，AI 后台推演）。 */
    public static RaceLineup build(GameData data, Track track, int laps, long seed) {
        return build(data, track, laps, seed, true, true);
    }

    /**
     * 组装一场比赛。
     *
     * @param asyncAi          true 表示 AI 推演放后台线程（界面用）；false 表示同步算（命令行用）
     * @param playerControlled true 表示玩家车等人下指令；false 表示全场都是 AI（命令行/回归测试用）
     */
    public static RaceLineup build(GameData data, Track track, int laps, long seed,
                                   boolean asyncAi, boolean playerControlled) {
        RaceConfig config = buildRace(data, track, laps, seed);
        MonteCarloPlanner planner = MonteCarloPlanner.defaultPlanner();
        Map<String, TireCompound> tires = new LinkedHashMap<>();
        for (TireCompound tire : data.tires()) {
            tires.put(tire.id(), tire);
        }

        PlayerStrategy playerStrategy = new PlayerStrategy();
        Map<Car, Strategy> strategies = new LinkedHashMap<>();
        Car playerCar = null;

        for (Car car : config.cars()) {
            if (playerControlled && isPlayerCar(data, car)) {
                strategies.put(car, playerStrategy);
                playerCar = car;
            } else {
                AiPersonality personality = AiPersonality.parse(car.team().personality());
                strategies.put(car, new AiStrategy(car.number(), personality,
                        planner, tires, config, !asyncAi));
            }
        }
        return new RaceLineup(config, strategies, playerCar, playerStrategy, planner);
    }

    /** 按赛道配置组装比赛。 */
    public static RaceConfig buildRace(GameData data, Track track, int laps, long seed) {
        int totalLaps = laps > 0 ? laps : track.defaultLaps();
        RaceConfig.Builder builder = RaceConfig.builder(track)
                .laps(totalLaps)
                .seed(seed)
                .weather(Weather.DRY)
                .tireWearScale(track.tireWearScale());

        double startFuel = startFuelFor(track);
        for (Team team : data.teams()) {
            for (Driver driver : team.drivers()) {
                Car car = new Car(driver.number(), team, driver.name(),
                        data.tire(driver.startTireId()), startFuel);
                car.setAggression(driver.aggression());
                builder.addCar(car);
            }
        }
        return builder.build();
    }

    /** 起步油量：大约够跑 65% 的里程，逼出至少一次进站。 */
    public static double startFuelFor(Track track) {
        double demand = track.raceFuelDemand();
        return Math.min(110, Math.max(45, demand * 0.65));
    }

    /** 进站加油量：够跑到下一次进站，留一点余量（油箱上限 130kg）。 */
    public static double fuelFor(Track track, int laps) {
        return Math.min(115, track.fuelPerLap() * laps * 1.10 + 2);
    }

    // ------------------------------------------------------------------

    private static boolean isPlayerCar(GameData data, Car car) {
        for (Team team : data.teams()) {
            for (Driver driver : team.drivers()) {
                if (driver.number() == car.number()) {
                    return driver.player();
                }
            }
        }
        return false;
    }
}
