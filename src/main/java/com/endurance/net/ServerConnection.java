package com.endurance.net;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 一条客户端连接。
 *
 * <p>写操作加了锁（多个线程可能同时广播），读操作由调用方在自己线程里循环。
 * 任何 IOException 都只标记关闭，不往外抛——一个客户端掉线不能影响别人。</p>
 */
public class ServerConnection {

    private final Socket socket;
    private final BufferedReader reader;
    private final PrintWriter writer;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private volatile String playerName = "玩家";
    private volatile int seat = -1;
    private volatile String token;

    public ServerConnection(Socket socket) throws IOException {
        this.socket = socket;
        this.reader = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.writer = new PrintWriter(
                new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), false);
        socket.setTcpNoDelay(true);
    }

    /** 读一行，连接断开返回 null。 */
    public String readLine() {
        try {
            return reader.readLine();
        } catch (IOException e) {
            return null;
        }
    }

    /** 发一条消息（自带换行）。 */
    public void send(String payload) {
        if (closed.get()) {
            return;
        }
        synchronized (writer) {
            writer.print(payload);
            writer.flush();
            if (writer.checkError()) {
                close();
            }
        }
    }

    public void close() {
        if (closed.compareAndSet(false, true)) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // 已经断了就算了
            }
        }
    }

    public boolean isClosed() {
        return closed.get();
    }

    public String playerName() {
        return playerName;
    }

    public void setPlayerName(String playerName) {
        this.playerName = playerName == null || playerName.isBlank() ? "玩家" : playerName;
    }

    public int seat() {
        return seat;
    }

    public void setSeat(int seat) {
        this.seat = seat;
    }

    public String token() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String remote() {
        return socket.getRemoteSocketAddress() + "";
    }
}
