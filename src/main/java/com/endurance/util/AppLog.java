package com.endurance.util;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 极简日志：写到 {@code logs/app.log}，同时保留最近一次异常供界面展示。
 *
 * <p>没有引入日志框架——这个项目主打零第三方依赖，而需求也就是
 * "出错了别崩、留个证据"，一个 synchronized 的写文件方法足够。</p>
 */
public final class AppLog {

    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Path LOG_DIR = Path.of(System.getProperty("user.dir"), "logs");
    private static final Path LOG_FILE = LOG_DIR.resolve("app.log");

    private static volatile Throwable lastError;
    private static boolean disabled;

    private AppLog() {
        throw new AssertionError("工具类不允许实例化");
    }

    public static Path logFile() {
        return LOG_FILE;
    }

    /** 最近一次被捕获的异常（界面用来提示用户）。 */
    public static Throwable lastError() {
        return lastError;
    }

    public static void info(String message) {
        write("INFO", message, null);
    }

    public static void warn(String message) {
        write("WARN", message, null);
    }

    public static void error(String message, Throwable error) {
        lastError = error;
        write("ERROR", message, error);
    }

    /** 把异常转成可读的堆栈文本。 */
    public static String stackTraceOf(Throwable error) {
        if (error == null) {
            return "";
        }
        StringWriter writer = new StringWriter();
        error.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }

    // ------------------------------------------------------------------

    private static synchronized void write(String level, String message, Throwable error) {
        String line = "[" + LocalDateTime.now().format(TIME) + "] [" + level + "] " + message;
        System.err.println(line);
        if (error != null) {
            String stack = stackTraceOf(error);
            System.err.println(stack);
            line = line + System.lineSeparator() + stack;
        }
        if (disabled) {
            return;
        }
        try {
            Files.createDirectories(LOG_DIR);
            Files.writeString(LOG_FILE, line + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            // 日志写不进去也不能影响游戏本身
            disabled = true;
            System.err.println("日志写入失败，后续不再尝试: " + e.getMessage());
        }
    }
}
