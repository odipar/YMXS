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
 * The structure to a JSON tree and back. {@link Text} writes that tree and
 * reads it, and the escaping, the parsing and the layout are the JSON
 * library's; this class performs the mapping alone.
 *
 * <p>A tune is written column by column. A register's column is one value
 * a row, and a timer's columns one value a row of the operation on that
 * effect, {@link #NONE} where a row leaves it alone. Every column is as
 * long as the tune, so a row is one index across every column.
 *
 * <p>Each timer is a separate object, {@code timerA} to {@code timerD},
 * since one row may act on all four. A column appears only where some row
 * fills it.
 *
 * <p>A source is written by its number, 1 upward into the sources a tune's
 * rows start ({@link Tunes#sources}). The name beside it appears in the
 * tools' reports alone.
 */
public final class Json {

    /** What the tree calls itself. */
    public static final String FORMAT = "ymxs";

    /** The version of the structure this maps. */
    public static final int VERSION = 2;

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

    /** What stands in a column where the row left that value alone. It
     *  is free for this, since it fits neither a register nor a part of
     *  an effect. */
    public static final int NONE = -1;

    /** The value each shape is written as. */
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
        out.put("rows", Tunes.size(tune.table()));
        put(out, "repeat", tune.table().repeat());
        List<Source> sources = Tunes.sources(tune);
        ArrayNode written = out.putArray("sources");
        for (Source source : sources) {
            ObjectNode one = written.addObject();
            one.put("name", Tunes.name(source));
            put(one, "repeat", Tunes.table(source).repeat());
            ArrayNode values = one.putArray("values");
            for (int value : Tunes.values(source)) {
                values.add(value);
            }
        }
        List<Row> rows = Tunes.rows(tune);
        ObjectNode sets = out.putObject("registers");
        for (Register register : Register.values()) {
            column(sets, name(register), rows, register);
        }
        for (Timer timer : Timer.values()) {
            timer(out, tune, timer, rows, sources);
        }
        return out;
    }

    /** One register's column: its value on every row, and {@link #NONE}
     *  where the row does not set it. Only a register some row sets has a
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

    /** One timer's columns: the operation on that effect at every row,
     *  and {@link #NONE} where the row leaves it alone. Only a timer some
     *  row acts on has columns. */
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
        ObjectNode into = out.putObject("timer" + timer.name());
        ArrayNode shape = into.putArray("shape");
        ArrayNode target = into.putArray("target");
        ArrayNode source = into.putArray("source");
        ArrayNode prescaler = into.putArray("prescaler");
        ArrayNode count = into.putArray("count");
        ArrayNode timerReset = into.putArray("timerReset");
        ArrayNode placeReset = into.putArray("placeReset");
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

    /** The multi in {@code tree}.
     *
     * @throws IllegalArgumentException where the tree is not this form, or
     *     is a structure no player plays
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
        JsonNode sets = tree.get("registers");
        if (sets != null) {
            if (!sets.isObject()) {
                throw new IllegalArgumentException("registers is " + kind(sets) + ", and a"
                        + " column a register is asked");
            }
            for (Register register : Register.values()) {
                JsonNode column = sets.get(name(register));
                if (column == null) {
                    continue;
                }
                sized(column, count, name(register));
                for (int at = 0; at < count; at++) {
                    if (!column.get(at).isIntegralNumber()) {
                        throw new IllegalArgumentException(name(register) + " is "
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
            JsonNode columns = tree.get("timer" + timer.name());
            if (columns == null) {
                continue;
            }
            if (!columns.isObject()) {
                throw new IllegalArgumentException("timer" + timer.name() + " is "
                        + kind(columns) + ", and a column a part of an effect is asked");
            }
            for (int at = 0; at < count; at++) {
                Effect effect = effect(columns, at, sources, timer, count);
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

    /** A column is one value a row, so its length is the tune's. */
    private static void sized(JsonNode column, int rows, String named) {
        if (!column.isArray()) {
            throw new IllegalArgumentException(named + " is " + column + ", and a column is"
                    + " asked");
        }
        if (column.size() != rows) {
            throw new IllegalArgumentException(named + " is " + column.size()
                    + " values long, and the tune has " + rows + " rows");
        }
    }

    /** One row's operation on the effect of one timer, or null where the
     *  row leaves it alone. */
    private static @Nullable Effect effect(JsonNode columns, int at, List<Source> sources,
                                           Timer timer, int rows) {
        int shape = column(columns, "shape", at, timer, rows);
        if (shape == NONE) {
            return null;
        }
        return switch (shape) {
            case START -> {
                int source = column(columns, "source", at, timer, rows);
                if (source < 1 || source > sources.size()) {
                    throw new IllegalArgumentException("row " + at + " starts source "
                            + source + ", and the tune runs " + sources.size());
                }
                yield new Start(Tunes.target(column(columns, "target", at, timer, rows)),
                        sources.get(source - 1),
                        Chip.prescaler(column(columns, "prescaler", at, timer, rows)),
                        column(columns, "count", at, timer, rows),
                        column(columns, "timerReset", at, timer, rows) == 1,
                        column(columns, "placeReset", at, timer, rows) == 1);
            }
            case RETUNE -> new Retune(
                    Chip.prescaler(column(columns, "prescaler", at, timer, rows)),
                    column(columns, "count", at, timer, rows),
                    column(columns, "timerReset", at, timer, rows) == 1,
                    column(columns, "placeReset", at, timer, rows) == 1);
            case STOP -> Tunes.STOP;
            default -> throw new IllegalArgumentException("row " + at + " sets shape "
                    + shape + " on Timer " + timer + ", and a shape is " + START + ", "
                    + RETUNE + " or " + STOP);
        };
    }

    /** The kind of a node, for a fault message. */
    private static String kind(JsonNode node) {
        if (node.isArray()) {
            return "an array";
        }
        if (node.isObject()) {
            return "an object";
        }
        return String.valueOf(node);
    }

    private static int column(JsonNode columns, String named, int at, Timer timer, int rows) {
        JsonNode column = columns.get(named);
        if (column == null) {
            throw new IllegalArgumentException("Timer " + timer + " has no \"" + named
                    + "\" column");
        }
        sized(column, rows, "Timer " + timer + "'s " + named);
        if (!column.get(at).isIntegralNumber()) {
            throw new IllegalArgumentException("Timer " + timer + "'s " + named + " is "
                    + column.get(at) + " at row " + at + ", and a whole number is asked");
        }
        return column.get(at).intValue();
    }

    // -------------------------------------------------------------- both

    /** The name of a register in a form: {@code r0} to {@code r13}. */
    public static String name(Register register) {
        return "r" + Chip.number(register);
    }

    /** The target of a written name, {@code setR0} upward. */
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
                    + " tune runs " + count + " rows");
        }
        rows.get(at).put(register, value);
    }

    private static void put(List<Map<Timer, Effect>> rows, int count, int at, Timer timer,
                            Effect effect) {
        if (at < 0 || at >= count) {
            throw new IllegalArgumentException("Timer " + timer + " acts on row "
                    + at + ", and the tune runs " + count + " rows");
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
