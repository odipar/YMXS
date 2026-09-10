package org.ymxs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import org.ymxs.YMXS.Effect;
import org.ymxs.YMXS.Multi;
import org.ymxs.YMXS.Register;
import org.ymxs.YMXS.Retune;
import org.ymxs.YMXS.Row;
import org.ymxs.YMXS.Single;
import org.ymxs.YMXS.Source;
import org.ymxs.YMXS.Start;
import org.ymxs.YMXS.Stop;
import org.ymxs.YMXS.Table;
import org.ymxs.YMXS.Timer;
import org.ymxs.YMXS.Tune;

/**
 * The structure to a JSON tree and back. What that tree is written as and
 * read from is {@link Text}'s, and escaping, parsing and laying out are
 * the JSON library's; this maps, and nothing else.
 *
 * <p>A tune is written plainly: its sources, then the rows that set a
 * register, then the effects its rows state. Every one of those states
 * where it stands, so nothing is folded into runs or counted in gaps. It
 * is the shape {@link Csv} holds, written as JSON: a reader that has one
 * has the other.
 *
 * <p>A source is written by its number, 1 upward into the sources a tune's
 * rows start ({@link Tunes#sources}). The name beside it is what a writer
 * called it and reaches nothing else.
 */
public final class Json {

    /** What the tree names itself. */
    public static final String FORMAT = "ymxs";

    /** The version of the structure this maps. */
    public static final int VERSION = 1;

    private static final JsonNodeFactory MAKE = JsonNodeFactory.instance;

    private Json() {
    }

    // ---------------------------------------------------------------- out

    /** {@code multi} as a JSON tree. */
    public static JsonNode of(Multi multi) {
        ObjectNode out = MAKE.objectNode();
        out.put("format", FORMAT);
        out.put("version", VERSION);
        ArrayNode tunes = out.putArray("tunes");
        for (Tune tune : multi.tunes()) {
            tunes.add(of(tune));
        }
        return out;
    }

    /** One tune as a JSON tree. */
    public static ObjectNode of(Tune tune) {
        ObjectNode out = MAKE.objectNode();
        out.put("title", tune.title());
        out.put("composer", tune.composer());
        out.put("writer", tune.writer());
        out.put("rate", tune.rate());
        out.put("frames", Tunes.size(tune.table()));
        put(out, "repeat", tune.table().repeat());
        List<Source> sources = Tunes.sources(tune);
        ArrayNode held = out.putArray("sources");
        for (Source source : sources) {
            ObjectNode one = held.addObject();
            one.put("name", Tunes.name(source));
            put(one, "repeat", Tunes.table(source).repeat());
            ArrayNode values = one.putArray("values");
            for (int value : Tunes.values(source)) {
                values.add(value);
            }
        }
        List<Row> rows = Tunes.rows(tune);
        ArrayNode said = out.putArray("rows");
        for (int at = 0; at < rows.size(); at++) {
            Map<Register, Integer> sets = Tunes.registers(rows.get(at));
            if (sets.isEmpty()) {
                continue;
            }
            ObjectNode one = said.addObject();
            one.put("row", at);
            for (Map.Entry<Register, Integer> set : sets.entrySet()) {
                one.put(name(set.getKey()), set.getValue());
            }
        }
        ArrayNode acts = out.putArray("effects");
        for (int at = 0; at < rows.size(); at++) {
            for (Map.Entry<Timer, Effect> one : Tunes.effects(rows.get(at)).entrySet()) {
                acts.add(of(at, one.getKey(), one.getValue(), sources));
            }
        }
        return out;
    }

    /** One effect a row states: where it stands, which timer, which shape,
     *  and that shape's own values. */
    private static ObjectNode of(int at, Timer timer, Effect effect, List<Source> sources) {
        ObjectNode out = MAKE.objectNode();
        out.put("row", at);
        out.put("timer", timer.name());
        switch (effect) {
            case Start start -> {
                out.put("shape", "start");
                out.put("target", Tunes.name(start.target()));
                out.put("source", sources.indexOf(start.source()) + 1);
                out.put("prescaler", Chip.divides(start.prescaler()));
                out.put("count", start.count());
                out.put("timerReset", start.timerReset());
                out.put("placeReset", start.placeReset());
            }
            case Retune retune -> {
                out.put("shape", "retune");
                out.put("prescaler", Chip.divides(retune.prescaler()));
                out.put("count", retune.count());
                out.put("timerReset", retune.timerReset());
                out.put("placeReset", retune.placeReset());
            }
            case Stop ignored -> out.put("shape", "stop");
        }
        return out;
    }

    // ----------------------------------------------------------------- in

    /** The multi {@code tree} holds.
     *
     * @throws IllegalArgumentException where the tree is not this form, or
     *     states a structure no player plays
     */
    public static Multi multi(JsonNode tree) {
        String format = text(tree, "format");
        if (!format.equals(FORMAT)) {
            throw new IllegalArgumentException("a tree of " + format + ", and this reads "
                    + FORMAT);
        }
        int version = number(tree, "version");
        if (version != VERSION) {
            throw new IllegalArgumentException("version " + version + ", and this reads "
                    + VERSION);
        }
        List<Tune> tunes = new ArrayList<>();
        for (JsonNode one : array(tree, "tunes")) {
            tunes.add(tune(one));
        }
        return Check.must(new Multi(tunes));
    }

    /** One tune out of a JSON tree. */
    public static Tune tune(JsonNode tree) {
        int count = number(tree, "frames");
        List<Source> sources = new ArrayList<>();
        for (JsonNode one : array(tree, "sources")) {
            List<Integer> values = new ArrayList<>();
            for (JsonNode value : array(one, "values")) {
                values.add(value.intValue());
            }
            sources.add(new Single(text(one, "name"), new Table<>(values, repeat(one))));
        }
        List<Map<Register, Integer>> registers = new ArrayList<>();
        List<Map<Timer, Effect>> effects = new ArrayList<>();
        for (int at = 0; at < count; at++) {
            registers.add(new EnumMap<>(Register.class));
            effects.add(new EnumMap<>(Timer.class));
        }
        for (JsonNode one : array(tree, "rows")) {
            int at = row(count, number(one, "row"), "a row");
            for (Register register : Register.values()) {
                JsonNode value = one.get(name(register));
                if (value != null) {
                    registers.get(at).put(register, number(one, name(register)));
                }
            }
        }
        for (JsonNode one : array(tree, "effects")) {
            int at = row(count, number(one, "row"), "an effect");
            effects.get(at).put(Timer.valueOf(text(one, "timer")), effect(one, sources, at));
        }
        List<Row> rows = new ArrayList<>();
        for (int at = 0; at < count; at++) {
            rows.add(new Row(registers.get(at), effects.get(at)));
        }
        return new Tune(text(tree, "title"), text(tree, "composer"), text(tree, "writer"),
                number(tree, "rate"), new Table<>(rows, repeat(tree)));
    }

    private static int row(int count, int at, String what) {
        if (at < 0 || at >= count) {
            throw new IllegalArgumentException(what + " at row " + at + ", and the tune holds "
                    + count + " rows");
        }
        return at;
    }

    /** What a row states of one effect, out of the shape it names and that
     *  shape's own fields. */
    private static Effect effect(JsonNode said, List<Source> sources, int at) {
        String shape = text(said, "shape");
        return switch (shape) {
            case "start" -> {
                int number = number(said, "source");
                if (number < 1 || number > sources.size()) {
                    throw new IllegalArgumentException("row " + at + " starts source "
                            + number + ", and the tune holds " + sources.size());
                }
                yield new Start(target(text(said, "target")), sources.get(number - 1),
                        Chip.prescaler(number(said, "prescaler")), number(said, "count"),
                        flag(said, "timerReset"), flag(said, "placeReset"));
            }
            case "retune" -> new Retune(Chip.prescaler(number(said, "prescaler")),
                    number(said, "count"), flag(said, "timerReset"),
                    flag(said, "placeReset"));
            case "stop" -> Tunes.STOP;
            default -> throw new IllegalArgumentException("row " + at + " states \"" + shape
                    + "\" of an effect, and it states one of start, retune and stop");
        };
    }

    // -------------------------------------------------------------- both

    /** A register's name in a form: {@code r0} to {@code r13}. */
    public static String name(Register register) {
        return "r" + Chip.number(register);
    }

    /** A target by the name it is written under, {@code setR0} upward. */
    public static YMXS.Target target(String said) {
        for (Register register : Register.values()) {
            YMXS.Target target = Tunes.setting(register);
            if (Tunes.name(target).equals(said)) {
                return target;
            }
        }
        throw new IllegalArgumentException("no target is called \"" + said + "\"");
    }

    private static void put(List<Map<Register, Integer>> rows, int count, int at,
                            Register register, int value) {
        if (at < 0 || at >= count) {
            throw new IllegalArgumentException(name(register) + " sets row " + at + ", and the"
                    + " tune holds " + count + " rows");
        }
        rows.get(at).put(register, value);
    }

    private static void put(List<Map<Timer, Effect>> rows, int count, int at, Timer timer,
                            Effect effect) {
        if (at < 0 || at >= count) {
            throw new IllegalArgumentException("Timer " + timer + " states something of row "
                    + at + ", and the tune holds " + count + " rows");
        }
        rows.get(at).put(timer, effect);
    }

    private static void put(ObjectNode out, String key, OptionalInt repeat) {
        if (repeat.isPresent()) {
            out.put(key, repeat.getAsInt());
        } else {
            out.putNull(key);
        }
    }

    private static OptionalInt repeat(JsonNode tree) {
        JsonNode value = tree.get("repeat");
        if (value == null || value.isNull()) {
            return OptionalInt.empty();
        }
        if (!value.isIntegralNumber()) {
            throw new IllegalArgumentException("repeat is " + value + ", and a row number or"
                    + " null is asked");
        }
        return OptionalInt.of(value.intValue());
    }

    private static String text(JsonNode tree, String key) {
        JsonNode value = tree.get(key);
        if (value == null || !value.isTextual()) {
            throw new IllegalArgumentException(key + " is " + value + ", and a text is asked");
        }
        return value.textValue();
    }

    private static int number(JsonNode tree, String key) {
        JsonNode value = tree.get(key);
        if (value == null || !value.isIntegralNumber()) {
            throw new IllegalArgumentException(key + " is " + value + ", and a whole number"
                    + " is asked");
        }
        return value.intValue();
    }

    private static boolean flag(JsonNode tree, String key) {
        JsonNode value = tree.get(key);
        if (value == null || !value.isBoolean()) {
            throw new IllegalArgumentException(key + " is " + value + ", and true or false"
                    + " is asked");
        }
        return value.booleanValue();
    }

    private static JsonNode array(JsonNode tree, String key) {
        JsonNode value = tree.get(key);
        if (value == null || !value.isArray()) {
            throw new IllegalArgumentException(key + " is " + value + ", and an array is"
                    + " asked");
        }
        return value;
    }
}
