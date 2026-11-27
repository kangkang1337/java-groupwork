package com.endurance.data;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 赛道的天气参考数据（来自 Open-Meteo 实时抓取）。
 *
 * <p>注意这是**抓取时刻的实况**，不等于比赛当天的天气——真实赛历在春夏季，
 * 而抓取发生在 11 月。所以这份数据只做两件事：
 * ① 天气面板里显示一行"参考实况"；
 * ② 用 daily 降雨概率去调整雨战的触发概率，让"常下雨的赛道"真的更容易下雨。</p>
 */
public final class WeatherProfile {

    /**
     * @param trackId     赛道 id
     * @param city        所在城市
     * @param airTemp     实况气温（°C）
     * @param humidity    实况相对湿度（%）
     * @param rainChance  当日降雨概率（%）
     * @param condition   实况描述
     */
    public record Profile(String trackId, String city, double airTemp, int humidity,
                          int rainChance, String condition) {
    }

    private WeatherProfile() {
        throw new AssertionError("工具类不允许实例化");
    }

    public static File defaultFile() {
        return new File(System.getProperty("user.dir"), "data/weather.json");
    }

    /** 读取全部赛道的参考天气；文件缺失返回空表（不影响游戏）。 */
    public static Map<String, Profile> load() {
        Map<String, Profile> profiles = new LinkedHashMap<>();
        File file = defaultFile();
        if (!file.isFile()) {
            return profiles;
        }
        try {
            var root = Json.parseFile(file);
            var circuits = root.get("circuits");
            for (String trackId : new String[]{"imola", "spa", "lemans", "saopaulo",
                    "cota", "fuji", "barcelona", "monza"}) {
                var node = circuits.opt(trackId, null);
                if (node == null) {
                    continue;
                }
                profiles.put(trackId, new Profile(
                        trackId,
                        node.opt("city", new Json.JsonValue.Str(trackId)).asString(),
                        node.opt("airTemp", new Json.JsonValue.Num(20)).asDouble(),
                        (int) node.opt("humidity", new Json.JsonValue.Num(50)).asDouble(),
                        (int) node.opt("rainChance", new Json.JsonValue.Num(20)).asDouble(),
                        node.opt("condition", new Json.JsonValue.Str("未知")).asString()));
            }
        } catch (IOException | RuntimeException e) {
            System.err.println("读取天气参考数据失败（忽略）: " + e.getMessage());
        }
        return profiles;
    }
}
