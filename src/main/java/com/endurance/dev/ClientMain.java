package com.endurance.dev;

import com.endurance.data.Json;
import com.endurance.net.GameClient;
import com.endurance.util.FxEnv;

import java.util.Locale;
import java.util.Scanner;

/**
 * 联机客户端（命令行版），用来在本机验证联机流程。
 *
 * <pre>
 * client.bat                    连 127.0.0.1:7777，名字自动取
 * client.bat 127.0.0.1 7777 小王
 * </pre>
 *
 * <p>输入命令：{@code ready} / {@code pit 硬胎 40} / {@code ping} / {@code quit}</p>
 */
public final class ClientMain {

    private ClientMain() {
    }

    public static void main(String[] args) throws Exception {
        FxEnv.configure();
        String host = args.length > 0 ? args[0] : "127.0.0.1";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 7777;
        String name = args.length > 2 ? args[2] : "玩家" + (int) (Math.random() * 900 + 100);

        GameClient client = new GameClient(host, port);
        client.hello(name);

        System.out.println("已连接 " + host + ":" + port + "，名字 " + name);

        Thread printer = new Thread(() -> {
            while (!client.isClosed()) {
                Json.JsonValue message = client.poll(500);
                if (message == null) {
                    continue;
                }
                print(message);
            }
        }, "client-print");
        printer.setDaemon(true);
        printer.start();

        boolean auto = java.util.Arrays.asList(args).contains("--auto");
        if (auto) {
            // 自动模式：连上就准备，然后只打印，方便用两个进程做联机验证
            Thread.sleep(400);
            client.ready();
            Thread.sleep(20000);
            client.close();
            return;
        }

        Scanner scanner = new Scanner(System.in, "UTF-8");
        System.out.println("命令：ready | pit <轮胎id> <加油kg> | ping | quit");
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine().trim();
            if (line.isEmpty()) {
                continue;
            }
            if ("quit".equalsIgnoreCase(line)) {
                break;
            }
            if ("ready".equalsIgnoreCase(line)) {
                client.ready();
            } else if ("ping".equalsIgnoreCase(line)) {
                client.ping();
            } else if (line.toLowerCase(Locale.ROOT).startsWith("pit ")) {
                String[] parts = line.split("\\s+");
                if (parts.length >= 3) {
                    client.command(parts[1], Double.parseDouble(parts[2]));
                } else {
                    System.out.println("用法：pit <轮胎id> <加油kg>，例如 pit hard 40");
                }
            } else {
                System.out.println("不认识的命令：" + line);
            }
        }
        client.close();
    }

    private static void print(Json.JsonValue message) {
        String type = message.opt("type", new Json.JsonValue.Str("")).asString();
        switch (type) {
            case "welcome" -> {
                System.out.println("=== 加入房间，你的座位 " + (int) message.get("seat").asDouble()
                        + "，重连令牌 " + message.get("token").asString()
                        + "，赛道 " + message.get("track").asString() + " ===");
            }
            case "start" -> System.out.println("=== 比赛开始，共 "
                    + (int) message.get("totalLaps").asDouble() + " 圈 ===");
            case "state" -> {
                int lap = (int) message.get("lap").asDouble();
                int total = (int) message.get("total").asDouble();
                var cars = message.get("cars").asArray();
                StringBuilder line = new StringBuilder(String.format(Locale.ROOT,
                        "第 %2d/%d 圈  %-4s  ", lap, total, message.get("weather").asString()));
                int shown = 0;
                for (Json.JsonValue car : cars) {
                    if (shown++ >= 3) {
                        break;
                    }
                    line.append(String.format(Locale.ROOT, "%d.#%d %-6s %s ",
                            (int) car.get("pos").asDouble(),
                            (int) car.get("number").asDouble(),
                            car.get("driver").asString(),
                            car.get("tire").asString()));
                }
                System.out.println(line);
            }
            case "event" -> System.out.println("  [事件] " + message.get("message").asString());
            case "result" -> {
                System.out.println("=== 比赛结束 ===");
                for (Json.JsonValue entry : message.get("standings").asArray()) {
                    System.out.printf(Locale.ROOT, "  %d  #%d %-8s %s%n",
                            (int) entry.get("pos").asDouble(),
                            (int) entry.get("number").asDouble(),
                            entry.get("driver").asString(),
                            entry.get("retired").asBool() ? "退赛" : "完赛");
                }
            }
            case "ack", "error" -> System.out.println("<< " + message.get("message").asString());
            case "peer" -> System.out.println("<< " + message.get("message").asString());
            default -> {
                // pong 之类的不打印
            }
        }
    }
}
