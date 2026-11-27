package com.endurance.dev;

import com.endurance.data.Json;
import com.endurance.net.GameClient;
import com.endurance.net.GameServer;

/**
 * 联机自检：起一个真服务器、连两个真客户端，把"开局、指令同步、断线重连、防作弊"跑一遍。
 */
public final class NetCheck {

    private static int passed;
    private static int failed;

    private NetCheck() {
    }

    public static void main(String[] args) throws Exception {
        com.endurance.util.FxEnv.configure();

        // 端口 0 = 让系统分配，避免和别的程序抢端口
        GameServer server = new GameServer(0, 12, 2, 150);
        server.start();
        int port = server.port();
        System.out.println("测试服务器端口 " + port);

        GameClient a = new GameClient("127.0.0.1", port);
        a.hello("甲");
        Json.JsonValue welcomeA = a.awaitType("welcome", 5000);
        check("甲方拿到欢迎消息", welcomeA != null);
        a.rememberWelcome(welcomeA);
        check("甲方是 0 号座位（" + a.seat() + "）", a.seat() == 0);
        check("甲方拿到重连令牌", a.token() != null && !a.token().isBlank());

        GameClient b = new GameClient("127.0.0.1", port);
        b.hello("乙");
        Json.JsonValue welcomeB = b.awaitType("welcome", 5000);
        check("乙方拿到欢迎消息", welcomeB != null);
        b.rememberWelcome(welcomeB);
        check("乙方是 1 号座位（" + b.seat() + "）", b.seat() == 1);

        a.ready();
        b.ready();

        Json.JsonValue start = a.awaitType("start", 5000);
        check("两人就绪后开局", start != null);
        int seat0Car = start == null ? -1 : (int) start.get("seatCars").get("0").asDouble();
        check("开局消息里带座位车号（座位0 → #" + seat0Car + "）", seat0Car > 0);

        Json.JsonValue state = awaitState(a, s -> s.get("lap").asDouble() >= 1, 5000);
        check("收到比赛状态", state != null);
        check("状态里圈数在推进（第 " + (state == null ? -1
                : (int) state.get("lap").asDouble()) + " 圈）",
                state != null && state.get("lap").asDouble() >= 1);
        check("状态里有 " + (state == null ? 0 : state.get("cars").asArray().size()) + " 台车（双组同场）",
                state != null && state.get("cars").asArray().size() >= 10);

        // ---- 指令同步 ----
        String currentTire = tireOf(state, seat0Car);
        String targetTire = "hard".equals(currentTire) ? "medium" : "hard";
        a.sendRaw("{\"type\":\"command\",\"tire\":\"unknown-tire\",\"fuel\":5}");
        Json.JsonValue unknownTire = a.awaitType("error", 3000);
        check("未知轮胎收到协议错误", unknownTire != null
                && unknownTire.get("message").asString().contains("未知轮胎"));

        a.sendRaw("{\"type\":\"command\",\"tire\":\"soft\",\"fuel\":5}");
        Json.JsonValue restrictedTire = a.awaitType("error", 3000);
        check("本站禁用的软胎被拒绝", restrictedTire != null
                && restrictedTire.get("message").asString().contains("本站不允许"));

        a.sendRaw("{\"type\":\"command\",\"tire\":\"hard\",\"fuel\":\"oops\"}");
        Json.JsonValue invalidFuel = a.awaitType("error", 3000);
        check("非数值油量收到协议错误", invalidFuel != null
                && invalidFuel.get("message").asString().contains("有效数字"));
        a.ping();
        check("无效指令后连接仍然存活", a.awaitType("pong", 3000) != null && !a.isClosed());

        a.command(targetTire, 40);
        Json.JsonValue ack = a.awaitType("ack", 3000);
        check("服务器确认了进站指令", ack != null);

        boolean tireChanged = waitForTire(a, seat0Car, targetTire, 6000);
        check("指令真的改变了比赛（#" + seat0Car + " 换成了 " + targetTire + "）", tireChanged);

        // ---- 防作弊：客户端不许伪造状态 ----
        a.sendRaw("{\"type\":\"state\",\"lap\":9999}");
        Json.JsonValue reject = a.awaitType("error", 3000);
        check("伪造的 state 消息被拒绝", reject != null);
        Json.JsonValue afterFake = a.awaitType("state", 3000);
        int lapAfterFake = afterFake == null ? 9999 : (int) afterFake.get("lap").asDouble();
        check("服务器权威：圈数没有被客户端改掉（第 " + lapAfterFake + " 圈）", lapAfterFake < 100);

        // ---- 断线重连 ----
        String token = a.token();
        a.kill();
        Thread.sleep(300);

        GameClient a2 = new GameClient("127.0.0.1", port);
        a2.hello("甲", token);
        Json.JsonValue welcomeA2 = a2.awaitType("welcome", 5000);
        check("断线后用令牌重连成功", welcomeA2 != null);
        if (welcomeA2 != null) {
            a2.rememberWelcome(welcomeA2);
        }
        check("重连后还是原来的座位（" + a2.seat() + "）", a2.seat() == 0);

        Json.JsonValue stateAfterReconnect = a2.awaitType("state", 5000);
        check("重连后立刻收到当前战况",
                stateAfterReconnect != null && stateAfterReconnect.get("lap").asDouble() >= 1);

        // ---- 跑完全程 ----
        Json.JsonValue result = b.awaitType("result", 15000);
        check("比赛跑完并广播结果", result != null);
        check("结果里有完整名次", result != null && result.get("standings").asArray().size() >= 10);

        Json.JsonValue finalState = awaitState(a2, s -> "FINISHED".equals(s.get("phase").asString()), 5000);
        check("结束后仍在广播收尾状态", finalState != null
                && "FINISHED".equals(finalState.get("phase").asString()));

        a2.close();
        b.close();
        server.close();

        System.out.println();
        System.out.println("==== 结果: " + passed + " 通过 / " + failed + " 失败 ====");
        if (failed > 0) {
            System.exit(1);
        }
    }

    // ------------------------------------------------------------------

    /** 一直读到满足条件的状态消息（跳过之前积压的旧状态）。 */
    private static Json.JsonValue awaitState(GameClient client,
                                             java.util.function.Predicate<Json.JsonValue> match,
                                             long timeout) {
        long deadline = System.currentTimeMillis() + timeout;
        while (System.currentTimeMillis() < deadline) {
            Json.JsonValue message = client.poll(300);
            if (message == null) {
                continue;
            }
            if (!"state".equals(message.opt("type", new Json.JsonValue.Str("")).asString())) {
                continue;
            }
            if (match.test(message)) {
                return message;
            }
        }
        return null;
    }
    private static String tireOf(Json.JsonValue state, int carNumber) {
        if (state == null) {
            return "";
        }
        for (Json.JsonValue car : state.get("cars").asArray()) {
            if ((int) car.get("number").asDouble() == carNumber) {
                return car.get("tire").asString();
            }
        }
        return "";
    }

    private static boolean waitForTire(GameClient client, int carNumber, String tireId, long timeout)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeout;
        while (System.currentTimeMillis() < deadline) {
            Json.JsonValue state = client.poll(500);
            if (state == null) {
                continue;
            }
            String type = state.opt("type", new Json.JsonValue.Str("")).asString();
            if (!"state".equals(type)) {
                continue;
            }
            if (tireId.equals(tireOf(state, carNumber))) {
                return true;
            }
        }
        return false;
    }

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
