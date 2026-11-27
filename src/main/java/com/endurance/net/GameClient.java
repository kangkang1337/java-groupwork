package com.endurance.net;

import com.endurance.data.Json;
import com.endurance.data.JsonWriter;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 联机客户端：连服务器、发指令、收状态。
 *
 * <p>收到的消息进一个队列，调用方可以用 {@link #poll} / {@link #awaitType} 取。
 * 真正的界面接入时把它挂到 JavaFX 的 {@code Platform.runLater} 上即可。</p>
 */
public class GameClient implements Closeable {

    private final Socket socket;
    private final BufferedReader reader;
    private final PrintWriter writer;
    private final BlockingQueue<Json.JsonValue> inbox = new LinkedBlockingQueue<>();
    private volatile boolean closed;

    private int seat = -1;
    private String token;

    public GameClient(String host, int port) throws IOException {
        this.socket = new Socket(host, port);
        socket.setTcpNoDelay(true);
        this.reader = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.writer = new PrintWriter(
                new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), false);

        Thread thread = new Thread(this::readLoop, "net-client-reader");
        thread.setDaemon(true);
        thread.start();
    }

    private void readLoop() {
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                Json.JsonValue message = Protocol.parseOrNull(line);
                if (message != null) {
                    inbox.offer(message);
                }
            }
        } catch (IOException ignored) {
            // 断开就是断开
        } finally {
            closed = true;
        }
    }

    // ------------------------------------------------------------------
    // 发送
    // ------------------------------------------------------------------

    public void hello(String name) throws IOException {
        hello(name, null);
    }

    public void hello(String name, String token) {
        JsonWriter.Obj message = JsonWriter.obj()
                .put("type", Protocol.HELLO)
                .put("name", name)
                .put("room", "main");
        if (token != null) {
            message.put("token", token);
        }
        send(Protocol.line(message));
    }

    public void ready() {
        send(Protocol.simple(Protocol.READY));
    }

    public void command(String tireId, double fuel) {
        send(Protocol.line(JsonWriter.obj()
                .put("type", Protocol.COMMAND)
                .put("tire", tireId)
                .put("fuel", fuel)));
    }

    public void ping() {
        send(Protocol.simple(Protocol.PING));
    }

    /** 直接发原始文本（自检里用来测试伪造消息）。 */
    public void sendRaw(String text) {
        send(text.endsWith("\n") ? text : text + "\n");
    }

    private synchronized void send(String payload) {
        if (closed) {
            return;
        }
        writer.print(payload);
        writer.flush();
    }

    // ------------------------------------------------------------------
    // 接收
    // ------------------------------------------------------------------

    /** 取下一条消息，超时返回 null。 */
    public Json.JsonValue poll(long timeoutMillis) {
        try {
            return inbox.poll(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    /** 一直读到指定类型（中间的消息丢弃），超时返回 null。 */
    public Json.JsonValue awaitType(String type, long timeoutMillis) {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            long remaining = deadline - System.currentTimeMillis();
            Json.JsonValue message = poll(Math.max(1, remaining));
            if (message == null) {
                return null;
            }
            if (type.equals(message.opt("type", new Json.JsonValue.Str("")).asString())) {
                return message;
            }
        }
        return null;
    }

    public int seat() {
        return seat;
    }

    public String token() {
        return token;
    }

    public void rememberWelcome(Json.JsonValue welcome) {
        this.seat = (int) welcome.opt("seat", new Json.JsonValue.Num(-1)).asDouble();
        this.token = welcome.opt("token", new Json.JsonValue.Str("")).asString();
    }

    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        closed = true;
        try {
            socket.close();
        } catch (IOException ignored) {
            // 已经断了
        }
    }

    /** 立刻断开底层 socket（模拟拔网线）。 */
    public void kill() {
        close();
    }
}
