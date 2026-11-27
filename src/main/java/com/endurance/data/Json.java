package com.endurance.data;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 自写的轻量 JSON 解析器（不依赖任何第三方库）。
 *
 * <p>只支持 JSON 标准里用得到的部分：对象、数组、字符串、数字、布尔、null。
 * 解析结果是不可变的值对象树，配合 {@link JsonValue} 上的取值方法使用。</p>
 */
public final class Json {

    private final String source;
    private int position;

    private Json(String source) {
        this.source = source;
    }

    /** 从字符串解析。 */
    public static JsonValue parse(String text) {
        Json parser = new Json(text);
        parser.skipWhitespace();
        JsonValue value = parser.parseValue();
        parser.skipWhitespace();
        if (parser.position < text.length()) {
            throw parser.error("JSON 结束后还有多余内容");
        }
        return value;
    }

    /** 从文件解析（UTF-8）。 */
    public static JsonValue parseFile(File file) throws IOException {
        String text = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        try {
            return parse(text);
        } catch (JsonException e) {
            throw new JsonException(file.getName() + "：" + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // 解析实现
    // ------------------------------------------------------------------

    private JsonValue parseValue() {
        if (position >= source.length()) {
            throw error("JSON 意外结束");
        }
        char c = source.charAt(position);
        return switch (c) {
            case '{' -> parseObject();
            case '[' -> parseArray();
            case '"' -> new JsonValue.Str(parseString());
            case 't', 'f' -> parseBoolean();
            case 'n' -> parseNull();
            default -> parseNumber();
        };
    }

    private JsonValue parseObject() {
        expect('{');
        Map<String, JsonValue> fields = new LinkedHashMap<>();
        skipWhitespace();
        if (peek() == '}') {
            position++;
            return new JsonValue.Obj(fields);
        }
        while (true) {
            skipWhitespace();
            String key = parseString();
            skipWhitespace();
            expect(':');
            skipWhitespace();
            fields.put(key, parseValue());
            skipWhitespace();
            char c = peek();
            if (c == ',') {
                position++;
                continue;
            }
            if (c == '}') {
                position++;
                return new JsonValue.Obj(fields);
            }
            throw error("对象里期望 ',' 或 '}'");
        }
    }

    private JsonValue parseArray() {
        expect('[');
        List<JsonValue> items = new ArrayList<>();
        skipWhitespace();
        if (peek() == ']') {
            position++;
            return new JsonValue.Arr(items);
        }
        while (true) {
            skipWhitespace();
            items.add(parseValue());
            skipWhitespace();
            char c = peek();
            if (c == ',') {
                position++;
                continue;
            }
            if (c == ']') {
                position++;
                return new JsonValue.Arr(items);
            }
            throw error("数组里期望 ',' 或 ']'");
        }
    }

    private String parseString() {
        expect('"');
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (position >= source.length()) {
                throw error("字符串没有闭合");
            }
            char c = source.charAt(position++);
            if (c == '"') {
                return sb.toString();
            }
            if (c == '\\') {
                if (position >= source.length()) {
                    throw error("转义符不完整");
                }
                char esc = source.charAt(position++);
                switch (esc) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> {
                        if (position + 4 > source.length()) {
                            throw error("\\u 转义不完整");
                        }
                        String hex = source.substring(position, position + 4);
                        position += 4;
                        try {
                            sb.append((char) Integer.parseInt(hex, 16));
                        } catch (NumberFormatException e) {
                            throw error("非法的 \\u 转义：" + hex);
                        }
                    }
                    default -> throw error("未知转义符：\\" + esc);
                }
                continue;
            }
            sb.append(c);
        }
    }

    private JsonValue parseNumber() {
        int start = position;
        while (position < source.length() && "+-0123456789.eE".indexOf(source.charAt(position)) >= 0) {
            position++;
        }
        if (start == position) {
            throw error("无法识别的字符 '" + source.charAt(position) + "'");
        }
        String text = source.substring(start, position);
        try {
            return new JsonValue.Num(Double.parseDouble(text));
        } catch (NumberFormatException e) {
            throw error("非法数字：" + text);
        }
    }

    private JsonValue parseBoolean() {
        if (source.startsWith("true", position)) {
            position += 4;
            return new JsonValue.Bool(true);
        }
        if (source.startsWith("false", position)) {
            position += 5;
            return new JsonValue.Bool(false);
        }
        throw error("期望 true 或 false");
    }

    private JsonValue parseNull() {
        if (source.startsWith("null", position)) {
            position += 4;
            return new JsonValue.Null();
        }
        throw error("期望 null");
    }

    private void skipWhitespace() {
        while (position < source.length()) {
            char c = source.charAt(position);
            if (c == ' ' || c == '\n' || c == '\r' || c == '\t') {
                position++;
            } else {
                break;
            }
        }
    }

    private char peek() {
        if (position >= source.length()) {
            throw error("JSON 意外结束");
        }
        return source.charAt(position);
    }

    private void expect(char expected) {
        if (position >= source.length() || source.charAt(position) != expected) {
            throw error("期望 '" + expected + "'");
        }
        position++;
    }

    private JsonException error(String message) {
        return new JsonException(message + "（位置 " + position + "）");
    }

    /** 解析失败。 */
    public static class JsonException extends RuntimeException {
        public JsonException(String message) {
            super(message);
        }
    }

    // ------------------------------------------------------------------

    /** JSON 值。 */
    public sealed interface JsonValue {

        record Obj(Map<String, JsonValue> fields) implements JsonValue {
        }

        record Arr(List<JsonValue> items) implements JsonValue {
        }

        record Str(String value) implements JsonValue {
        }

        record Num(double value) implements JsonValue {
        }

        record Bool(boolean value) implements JsonValue {
        }

        record Null() implements JsonValue {
        }

        /** 取对象字段；缺失时抛异常。 */
        default JsonValue get(String key) {
            if (this instanceof Obj obj && obj.fields().containsKey(key)) {
                return obj.fields().get(key);
            }
            throw new JsonException("缺少字段 \"" + key + "\"");
        }

        /** 取对象字段；缺失时返回默认值。 */
        default JsonValue opt(String key, JsonValue fallback) {
            if (this instanceof Obj obj && obj.fields().containsKey(key)) {
                return obj.fields().get(key);
            }
            return fallback;
        }

        default boolean has(String key) {
            return this instanceof Obj obj && obj.fields().containsKey(key);
        }

        default String asString() {
            if (this instanceof Str str) {
                return str.value();
            }
            throw new JsonException("期望字符串，实际是 " + typeName());
        }

        default double asDouble() {
            if (this instanceof Num num) {
                return num.value();
            }
            throw new JsonException("期望数字，实际是 " + typeName());
        }

        default int asInt() {
            return (int) Math.round(asDouble());
        }

        default boolean asBool() {
            if (this instanceof Bool bool) {
                return bool.value();
            }
            throw new JsonException("期望布尔值，实际是 " + typeName());
        }

        default List<JsonValue> asArray() {
            if (this instanceof Arr arr) {
                return arr.items();
            }
            throw new JsonException("期望数组，实际是 " + typeName());
        }

        default Map<String, JsonValue> asObject() {
            if (this instanceof Obj obj) {
                return obj.fields();
            }
            throw new JsonException("期望对象，实际是 " + typeName());
        }

        default String typeName() {
            if (this instanceof Obj) {
                return "object";
            }
            if (this instanceof Arr) {
                return "array";
            }
            if (this instanceof Str) {
                return "string";
            }
            if (this instanceof Num) {
                return "number";
            }
            if (this instanceof Bool) {
                return "boolean";
            }
            return "null";
        }
    }
}
