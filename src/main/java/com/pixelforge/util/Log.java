package com.pixelforge.util;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * 全局日志门面。
 *
 * <p>所有异常与关键操作都会写进 {@code logs/app.log}，便于用户反馈问题时排查，
 * 也满足大作业"错误写进日志文件"的要求。日志同时输出到控制台，方便调试。</p>
 */
public final class Log {

    private static final Logger LOGGER = Logger.getLogger("PixelForge");
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private static boolean initialized;
    private static File logDir;

    private Log() {
        throw new AssertionError("工具类不允许实例化");
    }

    /** 初始化日志系统，幂等；重复调用无副作用。 */
    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;

        LOGGER.setUseParentHandlers(false);
        LOGGER.setLevel(Level.ALL);

        Formatter formatter = new Formatter() {
            @Override
            public String format(LogRecord record) {
                StringBuilder sb = new StringBuilder(128);
                sb.append(LocalDateTime.now().format(TS))
                        .append(' ')
                        .append(String.format("%-7s", record.getLevel().getName()))
                        .append(' ')
                        .append(formatMessage(record))
                        .append(System.lineSeparator());
                if (record.getThrown() != null) {
                    StringWriter sw = new StringWriter();
                    record.getThrown().printStackTrace(new PrintWriter(sw));
                    sb.append(sw);
                }
                return sb.toString();
            }
        };

        // 控制台输出
        java.util.logging.ConsoleHandler console = new java.util.logging.ConsoleHandler();
        console.setFormatter(formatter);
        console.setLevel(Level.INFO);
        LOGGER.addHandler(console);

        // 文件输出：logs/app.log（单文件上限 4MB，超限后清空重写）
        try {
            logDir = resolveLogDir();
            FileHandler fileHandler = new FileHandler(
                    new File(logDir, "app.log").getAbsolutePath(), 4 * 1024 * 1024, 1, true);
            fileHandler.setEncoding("UTF-8");
            fileHandler.setFormatter(formatter);
            fileHandler.setLevel(Level.ALL);
            LOGGER.addHandler(fileHandler);
        } catch (IOException | SecurityException e) {
            LOGGER.warning("日志文件不可用，仅输出到控制台：" + e);
        }

        LOGGER.info("日志系统就绪，目录：" + (logDir == null ? "（无）" : logDir.getAbsolutePath()));
    }

    private static File resolveLogDir() {
        File primary = new File(System.getProperty("user.dir"), "logs");
        if (primary.isDirectory() || primary.mkdirs()) {
            return primary;
        }
        File fallback = new File(System.getProperty("java.io.tmpdir"), "PixelForge-logs");
        if (fallback.isDirectory() || fallback.mkdirs()) {
            return fallback;
        }
        throw new IllegalStateException("无法创建日志目录");
    }

    /** 日志目录，未初始化时为 {@code null}。 */
    public static File getLogDir() {
        return logDir;
    }

    public static void info(String message) {
        LOGGER.info(message);
    }

    public static void warn(String message) {
        LOGGER.warning(message);
    }

    public static void error(String message, Throwable throwable) {
        LOGGER.log(Level.SEVERE, message, throwable);
    }

    public static void error(String message) {
        LOGGER.severe(message);
    }
}
