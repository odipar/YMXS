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
 * <p>A tune is written stream by stream. A register's stream is a list of
 * runs, a run being a stretch of rows that all set it, and the number
 * before a run's values is the rows between the end of the run before it
 * and its own first row. The effects are written the other way, as events
 * at their own row, since an event is a thing a reader looks for by row
 * and there are few of them.
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
        out.put("rows", Tunes.size(tune.table()));
        put(out, "repeat", tune.table().repeat());
        List<Source> sources = Tunes.sources(tune);
        ArrayNode held = out.putArray("sources");
        for (Source source : sources) {
            ObjectNode one = held.addObject();
            one.put("name", Tunes.name(source));
            put(one, "repeat", Tunes.table(source).repeat());
            ArrayNode values = one.putArray("rows");
            for (int value : Tunes.values(source)) {
                values.add(value);
            }
        }
        for (Register register : Register.values()) {
            stream(out, tune, register);
        }
        events(out, tune, sources);
        return out;
    }

    /** One register's runs: a stretch of rows that all set it, after a gap
     *  of the rows since the run before it ended. */
    private static void stream(ObjectNode out, Tune tune, Register register) {
        List<Row> rows = Tunes.rows(tune);
        ArrayNode runs = MAKE.arrayNode();
        int at = 0;
        int end = 0;
        while (at < rows.size()) {
            if (!rows.get(at).registers().containsKey(register)) {
                at++;
                continue;
            }
            int from = at;
            List<Integer> run = new ArrayList<>();
            while (at < rows.size() && rows.get(at).registers().containsKey(register)) {
                run.add(rows.get(at).registers().get(register));
                at++;
            }
            ArrayNode one = runs.addArray();
            one.add(from - end);
            end = at;
            if (run.size() == 1) {
                one.add(run.get(0));
            } else {
                ArrayNode values = one.addArray();
                for (int value : run) {
                    values.add(value);
                }
            }
        }
        if (!runs.isEmpty()) {
            out.set(name(register), runs);
        }
    }

    /** The effects as events, each at its own row. */
    private static void events(ObjectNode out, Tune tune, List<Source> sources) {
        List<Row> rows = Tunes.rows(tune);
        ArrayNode events = out.putArray("effects");
        for (int at = 0; at < rows.size(); at++) {
            Map<Timer, Effect> here = Tunes.effects(rows.get(at));
            if (here.isEmpty()) {
                continue;
            }
            ArrayNode event = events.addArray();
            event.add(at);
            ObjectNode timers = event.addObject();
            for (Map.Entry<Timer, Effect> one : here.entrySet()) {
                timers.set(one.getKey().name(), of(one.getValue(), sources));
            }
        }
    }

    /** What a row states of one effect: the shape it names, and what that
     *  shape holds. */
    private static ObjectNode of(Effect effect, List<Source> sources) {
        ObjectNode out = MAKE.objectNode();
        switch (effect) {
            case Start start -> {
                ObjectNode said = out.putObject("start");
                said.put("target", Tunes.name(start.target()));
                said.put("source", sources.indexOf(start.source()) + 1);
                said.put("prescaler", Chip.divides(start.prescaler()));
                said.put("count", start.count());
                said.put("timerReset", start.timerReset());
                said.put("placeReset", start.placeReset());
            }
            case Retune retune -> {
                ObjectNode said = out.putObject("retune");
                said.put("prescaler", Chip.divides(retune.prescaler()));
                said.put("count", retune.count());
                said.put("timerReset", retune.timerReset());
                said.put("placeReset", retune.placeReset());
            }
            case Stop ignored -> out.putObject("stop");
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
        int count = number(tree, "rows");
        List<Source> sources = new ArrayList<>();
        for (JsonNode one : array(tree, "sources")) {
            List<Integer> values = new ArrayList<>();
            for (JsonNode value : array(one, "rows")) {
                values.add(value.intValue());
            }
            sources.add(new Single(text(one, "name"),
                    new Table<>(values, repeat(one))));
        }
        List<Map<Register, Integer>> registers = new ArrayList<>();
        List<Map<Timer, Effect>> effects = new ArrayList<>();
        for (int at = 0; at < count; at++) {
            registers.add(new EnumMap<>(Register.class));
            effects.add(new EnumMap<>(Timer.class));
        }
        for (Register register : Register.values()) {
            JsonNode runs = tree.get(name(register));
            if (runs == null) {
                continue;
            }
            int at = 0;
            for (JsonNode run : runs) {
                at += run.get(0).intValue();
                JsonNode values = run.get(1);
                if (values.isArray()) {
                    for (JsonNode value : values) {
                        put(registers, count, at++, register, value.intValue());
                    }
                } else {
                    put(registers, count, at++, register, values.intValue());
                }
            }
        }
        for (JsonNode event : array(tree, "effects")) {
            int at = event.get(0).intValue();
            if (at < 0 || at >= count) {
                throw new IllegalArgumentException("an effect at row " + at + ", and the tune"
                        + " holds " + count + " rows");
            }
            JsonNode timers = event.get(1);
            for (Map.Entry<String, JsonNode> one : timers.properties()) {
                effects.get(at).put(Timer.valueOf(one.getKey()),
                        effect(one.getValue(), sources, at));
            }
        }
        List<Row> rows = new ArrayList<>();
        for (int at = 0; at < count; at++) {
            rows.add(new Row(registers.get(at), effects.get(at)));
        }
        return new Tune(text(tree, "title"), text(tree, "composer"), text(tree, "writer"),
                number(tree, "rate"), new Table<>(rows, repeat(tree)));
    }

    private static Effect effect(JsonNode tree, List<Source> sources, int at) {
        if (tree.size() != 1) {
            throw new IllegalArgumentException("the effect at row " + at + " states "
                    + tree.size() + " things, and it states one of start, retune and stop");
        }
        String shape = tree.properties().iterator().next().getKey();
        JsonNode of = tree.get(shape);
        return switch (shape) {
            case "start" -> {
                int number = number(of, "source");
                if (number < 1 || number > sources.size()) {
                    throw new IllegalArgumentException("row " + at + " starts source "
                            + number + ", and the tune holds " + sources.size());
                }
                yield new Start(target(text(of, "target")), sources.get(number - 1),
                        Chip.prescaler(number(of, "prescaler")), number(of, "count"),
                        flag(of, "timerReset"), flag(of, "placeReset"));
            }
            case "retune" -> new Retune(Chip.prescaler(number(of, "prescaler")),
                    number(of, "count"), flag(of, "timerReset"), flag(of, "placeReset"));
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
