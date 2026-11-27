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

    /** 数字：整数值去掉多余的 .0。 */
    public static String number(double value) {
        if (value == Math.rint(value) && Math.abs(value) < 1e15) {
            return String.valueOf((long) value);
        }
        return String.format(java.util.Locale.ROOT, "%.4f", value);
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
