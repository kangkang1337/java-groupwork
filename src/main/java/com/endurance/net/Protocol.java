package com.endurance.net;

import com.endurance.data.Json;
import com.endurance.data.JsonWriter;

/**
 * 联机协议：一行一条 JSON。
 *
 * <p>选行协议而不是二进制包，是因为调试时能用 telnet/nc 直接看明文，
 * 而且现有 {@link Json} / {@link JsonWriter} 直接复用，不用另写序列化。</p>
 *
 * <p>所有消息都带 {@code type} 字段。**客户端只能发 HELLO / READY / COMMAND / PING**，
 * 其余类型（尤其是 STATE 和 RESULT）只允许服务端下发——这就是防作弊的第一道门槛。</p>
 */
public final class Protocol {

    /** 客户端 → 服务端。 */
    public static final String HELLO = "hello";
    public static final String READY = "ready";
    public static final String COMMAND = "command";
    public static final String PING = "ping";

    /** 服务端 → 客户端。 */
    public static final String WELCOME = "welcome";
    public static final String START = "start";
    public static final String STATE = "state";
    public static final String EVENT = "event";
    public static final String RESULT = "result";
    public static final String ACK = "ack";
    public static final String ERROR = "error";
    public static final String PONG = "pong";
    public static final String PEER = "peer";

    /** 房间阶段。 */
    public static final String PHASE_WAITING = "WAITING";
    public static final String PHASE_RUNNING = "RUNNING";
    public static final String PHASE_FINISHED = "FINISHED";

    private Protocol() {
        throw new AssertionError("工具类不允许实例化");
    }

    /** 一条能直接写进 socket 的消息（自带换行）。 */
    public static String line(JsonWriter.Obj message) {
        return message.toJson() + "\n";
    }

    public static String simple(String type) {
        return line(JsonWriter.obj().put("type", type));
    }

    public static String error(String message) {
        return line(JsonWriter.obj().put("type", ERROR).put("message", message));
    }

    public static String ack(String message) {
        return line(JsonWriter.obj().put("type", ACK).put("message", message));
    }

    public static String pong() {
        return simple(PONG);
    }

    /** 安全解析一行；不是合法 JSON 就返回 null（不抛异常，坏数据不能弄死服务器）。 */
    public static Json.JsonValue parseOrNull(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }
        try {
            return Json.parse(line);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** 客户端发来的消息里，只有这几种是合法的。 */
    public static boolean isClientMessage(String type) {
        return HELLO.equals(type) || READY.equals(type)
                || COMMAND.equals(type) || PING.equals(type);
    }
}
