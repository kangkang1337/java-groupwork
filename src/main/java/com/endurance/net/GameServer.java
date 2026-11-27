package com.endurance.net;

import com.endurance.data.GameData;
import com.endurance.data.Json;
import com.endurance.data.JsonWriter;
import com.endurance.data.DataLoader;
import com.endurance.model.Track;
import com.endurance.util.AppLog;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * 对战服务器：一个 accept 线程 + 每连接一个读线程 + 每房间一个定时推进器。
 *
 * <p>纯 JDK 的 {@link ServerSocket}，没有任何框架。房间按 id 惰性创建，
 * 第一次有人用某个 id 进房时就建一个。</p>
 */
public class GameServer implements Closeable {

    private final ServerSocket serverSocket;
    private final GameData data;
    private final int laps;
    private final int minPlayers;
    private final long lapMillis;

    private final ExecutorService clients = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "net-client");
        thread.setDaemon(true);
        return thread;
    });
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "net-room");
        thread.setDaemon(true);
        return thread;
    });

    private final Map<String, RaceRoom> rooms = new ConcurrentHashMap<>();
    private final Map<ServerConnection, RaceRoom> roomOf = new ConcurrentHashMap<>();
    private volatile boolean running = true;

    /** 只监听回环地址：这是课程项目的双人原型，不该被当成公网服务。 */
    public GameServer(int port, int laps, int minPlayers, long lapMillis) throws IOException {
        this(port, laps, minPlayers, lapMillis, false);
    }

    /**
     * @param bindAll true 才监听所有网卡（仅在你确认局域网可信时使用）
     */
    public GameServer(int port, int laps, int minPlayers, long lapMillis,
                      boolean bindAll) throws IOException {
        this.data = DataLoader.loadDefault();
        this.laps = laps;
        this.minPlayers = minPlayers;
        this.lapMillis = lapMillis;
        this.serverSocket = new ServerSocket();
        // 端口 0 表示让系统分配，单元测试和临时开局都用得上
        this.serverSocket.setReuseAddress(true);
        // 默认只绑 127.0.0.1：没有认证、没有加密的原型不该直接暴露在公网上
        java.net.InetAddress address = bindAll
                ? null : java.net.InetAddress.getLoopbackAddress();
        this.serverSocket.bind(new InetSocketAddress(address, port));
    }

    public int port() {
        return serverSocket.getLocalPort();
    }

    public GameData data() {
        return data;
    }

    /** 后台开始接受连接。 */
    public void start() {
        Thread acceptor = new Thread(this::acceptLoop, "net-accept");
        acceptor.setDaemon(true);
        acceptor.start();
        AppLog.info("服务器已启动，端口 " + port());
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                clients.submit(() -> serve(socket));
            } catch (IOException e) {
                if (running) {
                    AppLog.warn("接受连接失败: " + e.getMessage());
                }
            }
        }
    }

    private RaceRoom createRoom(String id) {
        Track track = data.defaultTrack();
        AppLog.info("创建房间 " + id + "，赛道 " + track.name() + "，圈数 " + laps);
        return new RaceRoom(id, data, track, laps, minPlayers, lapMillis, scheduler);
    }

    private void serve(Socket socket) {
        ServerConnection connection;
        try {
            connection = new ServerConnection(socket);
        } catch (IOException e) {
            AppLog.warn("建立连接失败: " + e.getMessage());
            return;
        }

        RaceRoom room = null;
        try {
            String line;
            while (running && (line = connection.readLine()) != null) {
                Json.JsonValue message = Protocol.parseOrNull(line);
                if (message == null) {
                    connection.send(Protocol.error("消息不是合法 JSON"));
                    continue;
                }
                String type = message.opt("type", new Json.JsonValue.Str("")).asString();

                if (Protocol.HELLO.equals(type)) {
                    String roomId = message.opt("room", new Json.JsonValue.Str("main")).asString();
                    String name = message.opt("name", new Json.JsonValue.Str("玩家")).asString();
                    String token = message.opt("token", new Json.JsonValue.Str("")).asString();
                    room = rooms.computeIfAbsent(roomId, this::createRoom);
                    roomOf.put(connection, room);
                    room.join(connection, name, token.isBlank() ? null : token);
                } else if (room != null) {
                    room.handle(connection, type, message);
                } else {
                    connection.send(Protocol.error("请先发送 hello 加入房间"));
                }
            }
        } catch (RuntimeException e) {
            AppLog.error("连接处理异常", e);
        } finally {
            if (room != null) {
                room.onDisconnect(connection);
            }
            roomOf.remove(connection);
            connection.close();
        }
    }

    /** 房间数量（自检用）。 */
    public int roomCount() {
        return rooms.size();
    }

    public RaceRoom room(String id) {
        return rooms.get(id);
    }

    @Override
    public void close() {
        running = false;
        try {
            serverSocket.close();
        } catch (IOException ignored) {
            // 关闭失败无所谓
        }
        for (RaceRoom room : rooms.values()) {
            room.shutdown();
        }
        rooms.clear();
        clients.shutdownNow();
        scheduler.shutdownNow();
        AppLog.info("服务器已关闭");
    }
}
