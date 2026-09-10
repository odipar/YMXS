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
import org.jspecify.annotations.Nullable;

/**
 * The structure to a JSON tree and back. What that tree is written as and
 * read from is {@link Text}'s, and escaping, parsing and laying out are
 * the JSON library's; this maps, and nothing else.
 *
 * <p>A tune is written column by column. A register's column stands one
 * value a row and a timer's columns stand one value a row of what the row
 * states of the effect there, {@link #NONE} where a row states nothing.
 * Every column is as long as the tune, so a row is what every column holds
 * at that place and nothing has to be counted to find it.
 *
 * <p>A timer is a structure of its own, {@code timer0} to {@code timer3},
 * because a row states an effect on as many of the four as it likes. A
 * column that no row fills is left out.
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

    /** What a column holds where the row it stands on states nothing. No
     *  register takes it and no part of an effect is it, so it names
     *  nothing else. */
    public static final int NONE = -1;

    /** What a shape is written as. */
    public static final int START = 0;
    public static final int RETUNE = 1;
    public static final int STOP = 2;

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
        ObjectNode sets = out.putObject("rows");
        for (Register register : Register.values()) {
            column(sets, name(register), rows, register);
        }
        for (Timer timer : Timer.values()) {
            timer(out, tune, timer, rows, sources);
        }
        return out;
    }

    /** One register's column: its value on every row, and {@link #NONE}
     *  where the row does not set it. A register no row sets has no
     *  column. */
    private static void column(ObjectNode out, String named, List<Row> rows,
                               Register register) {
        ArrayNode values = MAKE.arrayNode();
        boolean any = false;
        for (Row row : rows) {
            Integer value = row.registers().get(register);
            values.add(value == null ? NONE : value.intValue());
            any = any || value != null;
        }
        if (any) {
            out.set(named, values);
        }
    }

    /** One timer's columns: what a row states of the effect there on every
     *  row, and {@link #NONE} where the row states nothing. A timer no row
     *  states has no columns. */
    private static void timer(ObjectNode out, Tune tune, Timer timer, List<Row> rows,
                              List<Source> sources) {
        boolean any = false;
        for (Row row : rows) {
            if (row.effects().containsKey(timer)) {
                any = true;
                break;
            }
        }
        if (!any) {
            return;
        }
        ObjectNode held = out.putObject("timer" + timer.ordinal());
        ArrayNode shape = held.putArray("shape");
        ArrayNode target = held.putArray("target");
        ArrayNode source = held.putArray("source");
        ArrayNode prescaler = held.putArray("prescaler");
        ArrayNode count = held.putArray("count");
        ArrayNode timerReset = held.putArray("timerReset");
        ArrayNode placeReset = held.putArray("placeReset");
        for (Row row : rows) {
            Effect effect = row.effects().get(timer);
            if (effect == null) {
                shape.add(NONE);
                target.add(NONE);
                source.add(NONE);
                prescaler.add(NONE);
                count.add(NONE);
                timerReset.add(NONE);
                placeReset.add(NONE);
                continue;
            }
            switch (effect) {
                case Start start -> {
                    shape.add(START);
                    target.add(Tunes.number(start.target()));
                    source.add(sources.indexOf(start.source()) + 1);
                    prescaler.add(Chip.divides(start.prescaler()));
                    count.add(start.count());
                    timerReset.add(start.timerReset() ? 1 : 0);
                    placeReset.add(start.placeReset() ? 1 : 0);
                }
                case Retune retune -> {
                    shape.add(RETUNE);
                    target.add(NONE);
                    source.add(NONE);
                    prescaler.add(Chip.divides(retune.prescaler()));
                    count.add(retune.count());
                    timerReset.add(retune.timerReset() ? 1 : 0);
                    placeReset.add(retune.placeReset() ? 1 : 0);
                }
                case Stop ignored -> {
                    shape.add(STOP);
                    target.add(NONE);
                    source.add(NONE);
                    prescaler.add(NONE);
                    count.add(NONE);
                    timerReset.add(NONE);
                    placeReset.add(NONE);
                }
            }
        }
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
        JsonNode rows = tree.get("rows");
        if (rows != null) {
            if (!rows.isObject()) {
                throw new IllegalArgumentException("rows is " + kind(rows) + ", and a column"
                        + " a register is asked");
            }
            for (Register register : Register.values()) {
                JsonNode column = rows.get(name(register));
                if (column == null) {
                    continue;
                }
                held(column, count, name(register));
                for (int at = 0; at < count; at++) {
                    if (!column.get(at).isIntegralNumber()) {
                        throw new IllegalArgumentException(name(register) + " holds "
                                + column.get(at) + " at row " + at + ", and a whole number"
                                + " is asked");
                    }
                    int value = column.get(at).intValue();
                    if (value != NONE) {
                        registers.get(at).put(register, value);
                    }
                }
            }
        }
        for (Timer timer : Timer.values()) {
            JsonNode held = tree.get("timer" + timer.ordinal());
            if (held == null) {
                continue;
            }
            if (!held.isObject()) {
                throw new IllegalArgumentException("timer" + timer.ordinal() + " is "
                        + kind(held) + ", and a column a part of an effect is asked");
            }
            for (int at = 0; at < count; at++) {
                Effect effect = effect(held, at, sources, timer, count);
                if (effect != null) {
                    effects.get(at).put(timer, effect);
                }
            }
        }
        List<Row> built = new ArrayList<>();
        for (int at = 0; at < count; at++) {
            built.add(new Row(registers.get(at), effects.get(at)));
        }
        return new Tune(text(tree, "title"), text(tree, "composer"), text(tree, "writer"),
                number(tree, "rate"), new Table<>(built, repeat(tree)));
    }

    /** A column stands one value a row, so it is as long as the tune. */
    private static void held(JsonNode column, int frames, String named) {
        if (!column.isArray()) {
            throw new IllegalArgumentException(named + " is " + column + ", and a column is"
                    + " asked");
        }
        if (column.size() != frames) {
            throw new IllegalArgumentException(named + " holds " + column.size()
                    + " values, and the tune holds " + frames + " frames");
        }
    }

    /** What one row states of the effect on one timer, or null where it
     *  states nothing. */
    private static @Nullable Effect effect(JsonNode held, int at, List<Source> sources,
                                           Timer timer, int frames) {
        int shape = column(held, "shape", at, timer, frames);
        if (shape == NONE) {
            return null;
        }
        return switch (shape) {
            case START -> {
                int source = column(held, "source", at, timer, frames);
                if (source < 1 || source > sources.size()) {
                    throw new IllegalArgumentException("row " + at + " starts source "
                            + source + ", and the tune holds " + sources.size());
                }
                yield new Start(Tunes.target(column(held, "target", at, timer, frames)),
                        sources.get(source - 1),
                        Chip.prescaler(column(held, "prescaler", at, timer, frames)),
                        column(held, "count", at, timer, frames),
                        column(held, "timerReset", at, timer, frames) == 1,
                        column(held, "placeReset", at, timer, frames) == 1);
            }
            case RETUNE -> new Retune(
                    Chip.prescaler(column(held, "prescaler", at, timer, frames)),
                    column(held, "count", at, timer, frames),
                    column(held, "timerReset", at, timer, frames) == 1,
                    column(held, "placeReset", at, timer, frames) == 1);
            case STOP -> Tunes.STOP;
            default -> throw new IllegalArgumentException("row " + at + " states shape "
                    + shape + " on Timer " + timer + ", and a shape is " + START + ", "
                    + RETUNE + " or " + STOP);
        };
    }

    /** What a node is, for a complaint. */
    private static String kind(JsonNode node) {
        if (node.isArray()) {
            return "an array";
        }
        if (node.isObject()) {
            return "an object";
        }
        return String.valueOf(node);
    }

    private static int column(JsonNode held, String named, int at, Timer timer, int frames) {
        JsonNode column = held.get(named);
        if (column == null) {
            throw new IllegalArgumentException("Timer " + timer + " has no \"" + named
                    + "\" column");
        }
        held(column, frames, "Timer " + timer + "'s " + named);
        if (!column.get(at).isIntegralNumber()) {
            throw new IllegalArgumentException("Timer " + timer + "'s " + named + " holds "
                    + column.get(at) + " at row " + at + ", and a whole number is asked");
        }
        return column.get(at).intValue();
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
