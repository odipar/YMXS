package org.ymxs;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * A reader of JSON, enough of it for the text form ({@link Text}). It
 * gives a {@code Map<String, Object>} for an object, a {@code
 * List<Object>} for an array, a {@link String}, a {@link Long}, a {@link
 * Double}, a {@link Boolean}, or {@link #NULL}.
 *
 * <p>JSON's null is a value here rather than a Java null, so nothing this
 * gives back is null and a reader of it needs no test for one.
 *
 * <p>Writing is {@link Text}'s: what a form writes is laid out a run a
 * line and a value column wrapped, which a general writer would not do.
 */
final class Json {

    /** JSON's null. */
    static final Object NULL = new Object() {
        @Override
        public String toString() {
            return "null";
        }
    };

    private final String text;
    private int at;

    private Json(String text) {
        this.text = text;
    }

    /** The value {@code text} holds.
     *
     * @throws IllegalArgumentException where it is not JSON, or holds more
     *     than one value
     */
    static Object read(String text) {
        Json json = new Json(text);
        json.space();
        Object value = json.value();
        json.space();
        if (json.at != text.length()) {
            throw json.wrong("one value, and more text after it");
        }
        return value;
    }

    /** The string at {@code key} of an object. */
    static String text(Map<String, Object> of, String key) {
        Object value = of.get(key);
        if (value instanceof String said) {
            return said;
        }
        throw new IllegalArgumentException(key + " is " + said(value) + ", and a text is asked");
    }

    /** The whole number at {@code key} of an object. */
    static int number(Map<String, Object> of, String key) {
        Object value = of.get(key);
        if (value instanceof Long counted) {
            return Math.toIntExact(counted);
        }
        throw new IllegalArgumentException(key + " is " + said(value)
                + ", and a whole number is asked");
    }

    /** The object at {@code key} of an object. */
    @SuppressWarnings("unchecked")
    static Map<String, Object> object(Map<String, Object> of, String key) {
        Object value = of.get(key);
        if (value instanceof Map<?, ?> held) {
            return (Map<String, Object>) held;
        }
        throw new IllegalArgumentException(key + " is " + said(value)
                + ", and an object is asked");
    }

    /** The array at {@code key} of an object. */
    @SuppressWarnings("unchecked")
    static List<Object> array(Map<String, Object> of, String key) {
        Object value = of.get(key);
        if (value instanceof List<?> held) {
            return (List<Object>) held;
        }
        throw new IllegalArgumentException(key + " is " + said(value)
                + ", and an array is asked");
    }

    /** What a value is, for a complaint. */
    static String said(@Nullable Object value) {
        if (value == null) {
            return "not there";
        }
        if (value == NULL) {
            return "null";
        }
        if (value instanceof Map) {
            return "an object";
        }
        if (value instanceof List) {
            return "an array";
        }
        return String.valueOf(value);
    }

    /** A text of {@code value}, quoted and escaped as JSON has it. */
    static String quote(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char one = value.charAt(i);
            switch (one) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (one < 0x20) {
                        out.append(String.format("\\u%04x", (int) one));
                    } else {
                        out.append(one);
                    }
                }
            }
        }
        return out.append('"').toString();
    }

    private Object value() {
        if (at >= text.length()) {
            throw wrong("a value");
        }
        char one = text.charAt(at);
        return switch (one) {
            case '{' -> object();
            case '[' -> array();
            case '"' -> string();
            case 't' -> word("true", Boolean.TRUE);
            case 'f' -> word("false", Boolean.FALSE);
            case 'n' -> word("null", NULL);
            default -> number();
        };
    }

    private Object object() {
        Map<String, Object> out = new LinkedHashMap<>();
        at++;
        space();
        if (at < text.length() && text.charAt(at) == '}') {
            at++;
            return out;
        }
        while (true) {
            space();
            String key = string();
            space();
            take(':');
            space();
            out.put(key, value());
            space();
            if (at < text.length() && text.charAt(at) == ',') {
                at++;
                continue;
            }
            take('}');
            return out;
        }
    }

    private Object array() {
        List<Object> out = new ArrayList<>();
        at++;
        space();
        if (at < text.length() && text.charAt(at) == ']') {
            at++;
            return out;
        }
        while (true) {
            space();
            out.add(value());
            space();
            if (at < text.length() && text.charAt(at) == ',') {
                at++;
                continue;
            }
            take(']');
            return out;
        }
    }

    private String string() {
        take('"');
        StringBuilder out = new StringBuilder();
        while (at < text.length() && text.charAt(at) != '"') {
            char one = text.charAt(at++);
            if (one != '\\') {
                out.append(one);
                continue;
            }
            if (at >= text.length()) {
                throw wrong("an escape");
            }
            char next = text.charAt(at++);
            switch (next) {
                case '"', '\\', '/' -> out.append(next);
                case 'b' -> out.append('\b');
                case 'f' -> out.append('\f');
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
                case 'u' -> {
                    if (at + 4 > text.length()) {
                        throw wrong("four hex digits");
                    }
                    out.append((char) Integer.parseInt(text.substring(at, at + 4), 16));
                    at += 4;
                }
                default -> throw wrong("an escape");
            }
        }
        take('"');
        return out.toString();
    }

    private Object number() {
        int from = at;
        if (at < text.length() && (text.charAt(at) == '-' || text.charAt(at) == '+')) {
            at++;
        }
        boolean real = false;
        while (at < text.length()) {
            char one = text.charAt(at);
            if (one >= '0' && one <= '9') {
                at++;
            } else if (one == '.' || one == 'e' || one == 'E' || one == '-' || one == '+') {
                real = real || one == '.' || one == 'e' || one == 'E';
                at++;
            } else {
                break;
            }
        }
        if (at == from) {
            throw wrong("a number");
        }
        String said = text.substring(from, at);
        return real ? (Object) Double.valueOf(said) : (Object) Long.valueOf(said);
    }

    private Object word(String said, Object value) {
        if (!text.startsWith(said, at)) {
            throw wrong(said);
        }
        at += said.length();
        return value;
    }

    private void take(char one) {
        if (at >= text.length() || text.charAt(at) != one) {
            throw wrong("'" + one + "'");
        }
        at++;
    }

    private void space() {
        while (at < text.length() && Character.isWhitespace(text.charAt(at))) {
            at++;
        }
    }

    private IllegalArgumentException wrong(String asked) {
        int line = 1;
        for (int i = 0; i < at && i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return new IllegalArgumentException("line " + line + ": " + asked + " is asked, and"
                + " the text holds " + (at < text.length()
                        ? "'" + text.charAt(at) + "'" : "no more"));
    }
}
