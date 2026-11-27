package com.endurance.dev;

import com.endurance.net.GameServer;
import com.endurance.util.FxEnv;

/**
 * 联机服务器入口。
 *
 * <pre>
 * server.bat                 默认端口 7777、20 圈、每圈 800ms
 * server.bat 7777 40 500     端口 / 圈数 / 每圈毫秒
 * </pre>
 */
public final class ServerMain {

    private ServerMain() {
    }

    public static void main(String[] args) {
        FxEnv.configure();
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 7777;
        int laps = args.length > 1 ? Integer.parseInt(args[1]) : 20;
        long lapMillis = args.length > 2 ? Long.parseLong(args[2]) : 800;

        try (GameServer server = new GameServer(port, laps, 2, lapMillis)) {
            server.start();
            System.out.println("==================================================");
            System.out.println("  Endurance Strategy 服务器");
            System.out.println("  监听端口 : " + server.port());
            System.out.println("  每场圈数 : " + laps);
            System.out.println("  每圈耗时 : " + lapMillis + " ms");
            System.out.println("  房间人数 : 2");
            System.out.println("  按 Ctrl+C 关闭");
            System.out.println("==================================================");
            Thread.currentThread().join();
        } catch (Exception e) {
            System.err.println("服务器启动失败: " + e.getMessage());
            System.exit(1);
        }
    }
}
