package com.endurance.dev;

import com.endurance.data.DataLoader;
import com.endurance.data.GameData;
import com.endurance.data.Json;
import com.endurance.model.Team;
import com.endurance.model.TireCompound;
import com.endurance.model.Track;

/**
 * 数据层自检：验证自写 JSON 解析器的正确性，以及配置文件的完整性。
 */
public final class DataCheck {

    private static int passed;
    private static int failed;

    private DataCheck() {
    }

    public static void main(String[] args) {
        testParserBasics();
        testParserEscapes();
        testParserErrors();
        testCatalog();

        System.out.println();
        System.out.println("==== 结果: " + passed + " 通过 / " + failed + " 失败 ====");
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void testParserBasics() {
        Json.JsonValue root = Json.parse("""
                {
                  "name": "测试",
                  "count": 42,
                  "ratio": 0.75,
                  "negative": -3.5,
                  "flag": true,
                  "none": null,
                  "list": [1, 2, 3],
                  "nested": { "a": { "b": "c" } }
                }
                """);
        check("字符串字段", root.get("name").asString().equals("测试"));
        check("整数字段", root.get("count").asInt() == 42);
        check("小数字段", Math.abs(root.get("ratio").asDouble() - 0.75) < 1e-9);
        check("负数", Math.abs(root.get("negative").asDouble() + 3.5) < 1e-9);
        check("布尔", root.get("flag").asBool());
        check("null", root.get("none") instanceof Json.JsonValue.Null);
        check("数组长度", root.get("list").asArray().size() == 3);
        check("嵌套对象", root.get("nested").get("a").get("b").asString().equals("c"));
        check("可选字段默认值", root.opt("missing", new Json.JsonValue.Num(7)).asInt() == 7);
    }

    private static void testParserEscapes() {
        Json.JsonValue root = Json.parse("""
                { "text": "引号\\"反斜杠\\\\换行\\n制表\\t", "unicode": "\\u4e2d\\u6587" }
                """);
        String text = root.get("text").asString();
        check("转义引号", text.contains("\""));
        check("转义反斜杠", text.contains("\\"));
        check("转义换行", text.contains("\n"));
        check("转义制表", text.contains("\t"));
        check("unicode 转义", root.get("unicode").asString().equals("中文"));
    }

    private static void testParserErrors() {
        check("缺少闭合括号会报错", throwsJson("{\"a\": 1"));
        check("非法字符会报错", throwsJson("{\"a\": @}"));
        check("缺少字段会报错", throwsJsonGet("{\"a\": 1}", "b"));
    }

    private static boolean throwsJson(String text) {
        try {
            Json.parse(text);
            return false;
        } catch (Json.JsonException e) {
            return true;
        }
    }

    private static boolean throwsJsonGet(String text, String key) {
        try {
            Json.parse(text).get(key);
            return false;
        } catch (Json.JsonException e) {
            return true;
        }
    }

    private static void testCatalog() {
        GameData data = RaceCli.loadDataOrExit();
        if (data == null) {
            failed++;
            return;
        }
        check("赛道数量 >= 3（实际 " + data.tracks().size() + "）", data.tracks().size() >= 3);
        check("车队数量 >= 4（实际 " + data.teams().size() + "）", data.teams().size() >= 4);

        long hypercar = data.teams().stream()
                .filter(t -> t.category() == com.endurance.model.RaceCategory.HYPERCAR).count();
        long gt3 = data.teams().stream()
                .filter(t -> t.category() == com.endurance.model.RaceCategory.LMGT3).count();
        check("有 Hypercar 组车队（" + hypercar + " 支）", hypercar > 0);
        check("有 LMGT3 组车队（" + gt3 + " 支）", gt3 > 0);

        // 官方轮胎规则：普通分站每队两种干胎，勒芒三种
        check("普通分站只给两种干胎（伊莫拉 " + data.track("imola").dryCompounds() + "）",
                data.track("imola").dryCompounds().size() == 2);
        check("勒芒三种干胎全开（" + data.track("lemans").dryCompounds() + "）",
                data.track("lemans").dryCompounds().size() == 3);
        check("配色符合 WEC 官方标识（白=软、红=硬，和 F1 相反）",
                "#E8EDF3".equalsIgnoreCase(data.tire("soft").colorHex())
                        && "#FF4D4D".equalsIgnoreCase(data.tire("hard").colorHex()));
        check("雨胎不受分站限制",
                data.track("imola").allowsCompound("wet")
                        && data.track("imola").allowsCompound("intermediate"));

        // 真实赛道走线：有 SVG 的必须都能解析出合理折线
        int svgFiles = 0;
        int svgOk = 0;
        for (String id : new String[]{"imola", "spa", "lemans", "saopaulo",
                "cota", "fuji", "barcelona", "monza"}) {
            java.io.File file = new java.io.File("data/tracks/" + id + ".svg");
            if (!file.isFile()) {
                continue;
            }
            svgFiles++;
            var points = com.endurance.ui.SvgTracks.polyline(id);
            if (points != null && points.size() >= 30) {
                svgOk++;
            }
        }
        check("真实赛道 SVG 全部可解析（" + svgOk + "/" + svgFiles + "）",
                svgFiles > 0 && svgOk == svgFiles);
        check("轮胎配方 >= 4（实际 " + data.tires().size() + "）", data.tires().size() >= 4);

        for (Track track : data.tracks()) {
            check("赛道 " + track.id() + " 参数合理",
                    track.baseLapSeconds() > 30 && track.defaultLaps() > 10
                            && track.fuelPerLap() > 0 && track.tireWearScale() > 0);
        }
        for (Team team : data.teams()) {
            check("车队 " + team.id() + " 有车手", !team.drivers().isEmpty());
        }
        for (TireCompound tire : data.tires()) {
            check("轮胎 " + tire.id() + " 参数合理",
                    tire.wearPerLap() > 0 && tire.maxWearPenalty() > 0 && tire.optimalTemp() > 0);
        }

        // 软胎比硬胎快但更不耐用——这是策略博弈的前提
        TireCompound soft = data.soft();
        TireCompound hard = data.hard();
        check("软胎干地更快", soft.timeOffset(com.endurance.model.Weather.DRY)
                < hard.timeOffset(com.endurance.model.Weather.DRY));
        check("软胎磨损更快", soft.wearPerLap() > hard.wearPerLap());
        check("软胎寿命更短", soft.estimatedLifeLaps() < hard.estimatedLifeLaps());
        check("软胎磨穿惩罚更重", soft.maxWearPenalty() > hard.maxWearPenalty());
    }

    // ------------------------------------------------------------------

    private static void check(String name, boolean condition) {
        if (condition) {
            passed++;
            System.out.println("[PASS] " + name);
        } else {
            failed++;
            System.out.println("[FAIL] " + name);
        }
    }
}
