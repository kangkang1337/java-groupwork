package com.endurance.data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 极简 JSON 写出器（和 {@link Json} 解析器配套）。
 *
 * <p>存档和历史战绩都需要把对象写成 JSON，这个类只做"能写、能读回来"这件事，
 * 不做格式化美化，也不依赖任何第三方库。</p>
 */
public final class JsonWriter {

    private JsonWriter() {
        throw new AssertionError("工具类不允许实例化");
    }

    public static Obj obj() {
        return new Obj();
    }

    public static Arr arr() {
        return new Arr();
    }

    /** JSON 字符串转义。 */
    public static String quote(String text) {
        StringBuilder sb = new StringBuilder(text.length() + 2);
        sb.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
        return sb.toString();
    }

    /**
     * 数字：整数值去掉多余的 .0；非整数用能精确往返的最短表示。
     *
     * <p>原来固定保留 4 位小数，写进存档再读回来会损失精度——
     * 存档要的是"写什么读回什么"，不是给人看的对齐格式。</p>
     */
    public static String number(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException("JSON 不支持 NaN / Infinity");
        }
        if (value == Math.rint(value) && Math.abs(value) < 1e15) {
            return String.valueOf((long) value);
        }
        // Double.toString 保证能精确读回同一个 double
        return Double.toString(value);
    }

    /**
     * 原子写文件：先写同名 .tmp，再整体替换。
     *
     * <p>直接往目标文件写，一旦写到一半被强制结束，存档就是半个 JSON。
     * 先写临时文件再 move，要么是旧内容、要么是新内容，不会出现中间态。</p>
     */
    public static void writeAtomically(java.nio.file.Path target, String content)
            throws java.io.IOException {
        java.nio.file.Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        java.nio.file.Files.writeString(tmp, content, java.nio.charset.StandardCharsets.UTF_8);
        try {
            java.nio.file.Files.move(tmp, target,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            // 某些文件系统不支持原子替换，退化成普通覆盖
            java.nio.file.Files.move(tmp, target,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    static String render(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Obj obj) {
            return obj.toJson();
        }
        if (value instanceof Arr arr) {
            return arr.toJson();
        }
        if (value instanceof String text) {
            return quote(text);
        }
        if (value instanceof Boolean flag) {
            return flag.toString();
        }
        if (value instanceof Number n) {
            return number(n.doubleValue());
        }
        if (value instanceof Collection<?> collection) {
            Arr arr = new Arr();
            collection.forEach(arr::add);
            return arr.toJson();
        }
        return quote(String.valueOf(value));
    }

    /** JSON 对象。 */
    public static final class Obj {
        private final Map<String, Object> values = new LinkedHashMap<>();

        public Obj put(String key, Object value) {
            values.put(key, value);
            return this;
        }

        public String toJson() {
            List<String> parts = new ArrayList<>(values.size());
            values.forEach((key, value) -> parts.add(quote(key) + ":" + render(value)));
            return "{" + String.join(",", parts) + "}";
        }

        @Override
        public String toString() {
            return toJson();
        }
    }

    /** JSON 数组。 */
    public static final class Arr {
        private final List<Object> values = new ArrayList<>();

        public Arr add(Object value) {
            values.add(value);
            return this;
        }

        public int size() {
            return values.size();
        }

        public String toJson() {
            List<String> parts = new ArrayList<>(values.size());
            values.forEach(value -> parts.add(render(value)));
            return "[" + String.join(",", parts) + "]";
        }

        @Override
        public String toString() {
            return toJson();
        }
    }
}
