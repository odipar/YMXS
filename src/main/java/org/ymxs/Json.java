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
import org.ymxs.YMXS.Mapping;
import org.ymxs.YMXS.Multi;
import org.ymxs.YMXS.Pair;
import org.ymxs.YMXS.Part;
import org.ymxs.YMXS.Register;
import org.ymxs.YMXS.Remap;
import org.ymxs.YMXS.Retune;
import org.ymxs.YMXS.Row;
import org.ymxs.YMXS.Single;
import org.ymxs.YMXS.Source;
import org.ymxs.YMXS.Start;
import org.ymxs.YMXS.Stop;
import org.ymxs.YMXS.Table;
import org.ymxs.YMXS.Target;
import org.ymxs.YMXS.Three;
import org.ymxs.YMXS.Timer;
import org.ymxs.YMXS.Timing;
import org.ymxs.YMXS.Transformation;
import org.ymxs.YMXS.Triple;
import org.ymxs.YMXS.Tune;
import org.ymxs.YMXS.Two;
import org.jspecify.annotations.Nullable;

/**
 * The structure to a JSON tree and back. {@link Text} writes that tree
 * and reads it, and the escaping, the parsing and the layout are the
 * JSON library's; this class performs the mapping alone.
 *
 * <p>A tune is written column by column. A register's column is one
 * value a row, and a timer's columns one value a row of the operation
 * on that effect, {@link #NONE} where a row leaves it alone. Every
 * column is as long as the tune, so a row is one index across every
 * column.
 *
 * <p>Each timer is a separate object, {@code timerA} to {@code timerD},
 * since one row may act on all four. A timer object appears when some
 * row acts on it. Its mapping column uses 0 for mappings with only
 * identity parts and arrays of part numbers when a transformation is
 * used.
 *
 * <p>A source is written by its number, 1 upward into the sources a
 * tune's rows start ({@link Tunes#sources}). The name beside it appears
 * in the tools' reports alone. A part of a mapping is written the same
 * way, 0 for the identity and 1 upward into the transformations the
 * mappings of a tune's rows use ({@link Tunes#transformations}).
 */
public final class Json {

    /** What the tree calls itself. */
    public static final String FORMAT = "ymxs";

    /** The version of the structure this maps, and the version a writer
     *  writes. */
    public static final int VERSION = 7;

    /** The first version with transformations, mappings and a remap: in
     *  a file of a version before it every start reads with the mapping
     *  of no parts, and a shape of 3 is an error of the form (2.4). */
    public static final int MAPPED = 7;

    /** The first version with a start's {@code deferred}: in a file of a
     *  version before it every start reads with it clear (2.4). */
    public static final int DEFERRED = 5;

    /** The first version with sources of several values a row and targets
     *  of several registers: a file of a version before it has one value a
     *  row in every source and a target of 0 to 13 (2.4). */
    public static final int SEVERAL = 4;

    /** The oldest version a reader reads; it reads every version from this
     *  one to {@link #VERSION} (2.4). */
    public static final int OLDEST = 3;

    /** The versions a reader reads, as its report lists them: 3, 4, 5, 6
     *  or 7. */
    static final String READS = reads();

    private static String reads() {
        StringBuilder said = new StringBuilder();
        for (int version = OLDEST; version < VERSION; version++) {
            said.append(version).append(", ");
        }
        said.setLength(said.length() - 2);
        return said + " or " + VERSION;
    }

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

    /** What marks a column where the row left that value alone. It
     *  is free for this, since it fits neither a register nor a part of
     *  an effect. */
    public static final int NONE = -1;

    /** The value each shape is written as. */
    public static final int START = 0;
    public static final int RETUNE = 1;
    public static final int STOP = 2;
    public static final int REMAP = 3;

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
            put(one, "repeat", Tunes.rows(source).repeat());
            // 4.1: a row is a number where the source has one value a row,
            // and an array of its values where it has more
            ArrayNode values = one.putArray("values");
            for (List<Integer> row : Tunes.rows(source).rows()) {
                if (row.size() == 1) {
                    values.add(row.get(0));
                    continue;
                }
                ArrayNode of = values.addArray();
                for (int value : row) {
                    of.add(value);
                }
            }
        }
        // 3.7: a transformation is its name and its entries, in the
        // number order of SPEC.md 1.9
        List<Transformation> transformations = Tunes.transformations(tune);
        ArrayNode mapped = out.putArray("transformations");
        for (Transformation transformation : transformations) {
            ObjectNode one = mapped.addObject();
            one.put("name", transformation.name());
            ArrayNode entries = one.putArray("entries");
            for (int entry : transformation.entries()) {
                entries.add(entry);
            }
        }
        List<Row> rows = Tunes.rows(tune);
        ObjectNode sets = out.putObject("registers");
        for (Register register : Register.values()) {
            column(sets, name(register), rows, register);
        }
        for (Timer timer : Timer.values()) {
            timer(out, timer, rows, sources, transformations);
        }
        return out;
    }

    /**
     * Encodes a mapping as 0 when every part is identity, including an
     * empty mapping. Otherwise, writes an array in part order: 0 for
     * identity and the transformation number for a lookup (json.md
     * 6.7).
     */
    private static JsonNode mapping(Mapping mapping, List<Transformation> transformations) {
        if (Tunes.isIdentity(mapping)) {
            return MAKE.numberNode(0);
        }
        ArrayNode out = MAKE.arrayNode();
        for (Part part : mapping.parts()) {
            out.add(Tunes.number(transformations, part));
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
     *  row acts on has columns, the nine of 6.1. */
    private static void timer(ObjectNode out, Timer timer, List<Row> rows,
                              List<Source> sources, List<Transformation> transformations) {
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
        ArrayNode deferred = into.putArray("deferred");
        ArrayNode mapping = into.putArray("mapping");
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
                deferred.add(NONE);
                mapping.add(NONE);
                continue;
            }
            switch (effect) {
                case Start start -> {
                    shape.add(START);
                    target.add(Tunes.number(Tunes.target(start.connection())));
                    source.add(sources.indexOf(Tunes.source(start.connection())) + 1);
                    prescaler.add(Chip.divides(start.timing().prescaler()));
                    count.add(start.timing().count());
                    timerReset.add(start.timing().timerReset() ? 1 : 0);
                    placeReset.add(start.timing().placeReset() ? 1 : 0);
                    deferred.add(start.deferred() ? 1 : 0);
                    mapping.add(mapping(start.mapping(), transformations));
                }
                case Retune retune -> {
                    shape.add(RETUNE);
                    target.add(NONE);
                    source.add(NONE);
                    prescaler.add(Chip.divides(retune.timing().prescaler()));
                    count.add(retune.timing().count());
                    timerReset.add(retune.timing().timerReset() ? 1 : 0);
                    placeReset.add(retune.timing().placeReset() ? 1 : 0);
                    deferred.add(NONE);
                    mapping.add(NONE);
                }
                case Stop ignored -> {
                    shape.add(STOP);
                    target.add(NONE);
                    source.add(NONE);
                    prescaler.add(NONE);
                    count.add(NONE);
                    timerReset.add(NONE);
                    placeReset.add(NONE);
                    deferred.add(NONE);
                    mapping.add(NONE);
                }
                case Remap remap -> {
                    shape.add(REMAP);
                    target.add(NONE);
                    source.add(NONE);
                    prescaler.add(NONE);
                    count.add(NONE);
                    timerReset.add(NONE);
                    placeReset.add(NONE);
                    deferred.add(NONE);
                    mapping.add(mapping(remap.mapping(), transformations));
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
        if (version < OLDEST || version > VERSION) {
            throw new IllegalArgumentException("version " + version + ", and this reads "
                    + READS);
        }
        List<Tune> tunes = new ArrayList<>();
        for (JsonNode one : array(tree, "tunes")) {
            tunes.add(tune(one, version));
        }
        return Check.must(new Multi(tunes));
    }

    /** One source out of its object: the rows of {@code values}, a number
     *  each where the source has one value a row and an array each where
     *  it has two or three, one shape through the source (4.1, 4.2).
     *  {@code values} is read before {@code name}, as 7.1 step 6 orders
     *  them, and the size of a row before its values. A line of an error
     *  has the name through {@link Check#named} (8.1). */
    private static Source source(JsonNode one, int version) {
        JsonNode written = array(one, "values");
        String name = text(one, "name");
        String reported = Check.named(name);
        List<List<Integer>> rows = new ArrayList<>();
        for (int at = 0; at < written.size(); at++) {
            JsonNode row = written.get(at);
            String named = reported + " at row " + at + " is ";
            if (row.isArray()) {
                if (version < SEVERAL) {
                    throw new IllegalArgumentException("source " + reported + " has a row of"
                            + " several values, and version " + version
                            + " has one value a row");
                }
                if (row.size() < 2 || row.size() > 3) {
                    throw new IllegalArgumentException("a row of " + row.size()
                            + " values in source " + reported + ", and a row is a number"
                            + " or two or three values");
                }
                List<Integer> values = new ArrayList<>();
                for (JsonNode value : row) {
                    values.add(whole(value, named + value));
                }
                rows.add(values);
            } else {
                rows.add(List.of(whole(row, named + row)));
            }
        }
        for (List<Integer> row : rows) {
            if (row.size() != rows.get(0).size()) {
                throw new IllegalArgumentException("source " + reported + " has rows of "
                        + rows.get(0).size() + " and of " + row.size()
                        + " values, and a source has one shape");
            }
        }
        OptionalInt repeat = repeat(one);
        if (rows.isEmpty() || rows.get(0).size() == 1) {
            return new Single(name, new Table<>(
                    rows.stream().map(row -> row.get(0)).toList(), repeat));
        }
        if (rows.get(0).size() == 2) {
            return new Pair(name, new Table<>(rows.stream()
                    .map(row -> new Two(row.get(0), row.get(1))).toList(), repeat));
        }
        return new Triple(name, new Table<>(rows.stream()
                .map(row -> new Three(row.get(0), row.get(1), row.get(2))).toList(),
                repeat));
    }

    /** One tune out of a JSON tree, read as the version this form
     *  writes. */
    public static Tune tune(JsonNode tree) {
        return tune(tree, VERSION);
    }

    /**
     * Reads entries, name, then each entry in key order (json.md 7.1
     * step 7). Parsing checks whole numbers; the structure check later
     * validates entry count and byte range. Error reports escape the
     * name through {@link Check#named}.
     */
    private static Transformation transformation(JsonNode one) {
        JsonNode written = array(one, "entries");
        String name = text(one, "name");
        List<Integer> entries = new ArrayList<>();
        for (int key = 0; key < written.size(); key++) {
            entries.add(whole(written.get(key), Check.named(name) + " at entry " + key
                    + " is " + written.get(key)));
        }
        return new Transformation(name, entries);
    }

    /** One tune out of a JSON tree of {@code version}: a file of version 3
     *  has one value a row and a target of 0 to 13, and another shape in it
     *  is an error of the form; a file of version 4 or 3 has every start
     *  with {@code deferred} clear; a file of version 6 or before has every
     *  start with the mapping of no parts, and its {@code transformations}
     *  and {@code mapping} are skipped (2.4). */
    public static Tune tune(JsonNode tree, int version) {
        int count = number(tree, "rows");
        List<Source> sources = new ArrayList<>();
        for (JsonNode one : array(tree, "sources")) {
            sources.add(source(one, version));
        }
        List<Transformation> transformations = new ArrayList<>();
        if (version >= MAPPED) {
            for (JsonNode one : array(tree, "transformations")) {
                transformations.add(transformation(one));
            }
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
                throw new IllegalArgumentException("registers is " + kind(sets)
                        + ", and this form requires a column a register");
            }
            for (Register register : Register.values()) {
                JsonNode column = sets.get(name(register));
                if (column == null) {
                    continue;
                }
                sized(column, count, name(register));
                for (int at = 0; at < count; at++) {
                    int value = whole(column.get(at), name(register) + " at row " + at
                            + " is " + column.get(at));
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
                        + kind(columns) + ", and this form requires a column a part of an"
                        + " effect");
            }
            for (int at = 0; at < count; at++) {
                Effect effect = effect(columns, at, sources, transformations, timer, count,
                        version);
                if (effect != null) {
                    effects.get(at).put(timer, effect);
                }
            }
        }
        List<Row> built = new ArrayList<>();
        for (int at = 0; at < count; at++) {
            built.add(new Row(registers.get(at), effects.get(at)));
        }
        Tune tune = new Tune(text(tree, "title"), text(tree, "composer"),
                text(tree, "writer"), number(tree, "rate"),
                new Table<>(built, repeat(tree)));
        List<String> wrong = Check.declared(sources, transformations, tune);
        if (!wrong.isEmpty()) {
            throw new IllegalArgumentException(String.join("\n", wrong));
        }
        return tune;
    }

    /** A value of a column, which fits an int: a number past it is no
     *  value of this form, as one that is not whole is no value of it. */
    private static int whole(JsonNode value, String named) {
        if (!value.isIntegralNumber() || !value.canConvertToInt()) {
            throw new IllegalArgumentException(named
                    + ", and this form requires a whole number");
        }
        return value.intValue();
    }

    /** A column is one value a row, so its length is the tune's. */
    private static void sized(JsonNode column, int rows, String named) {
        if (!column.isArray()) {
            throw new IllegalArgumentException(named + " is " + column
                    + ", and this form requires a column");
        }
        if (column.size() != rows) {
            throw new IllegalArgumentException(named + " is " + column.size()
                    + " values long, and the tune has " + rows + " rows");
        }
    }

    /** The target of a number, read against the version: a file of
     *  version 3 reaches 0 to 13 (2.4). The CSV reader calls it too. */
    static Target target(int number, int version) {
        if (version < SEVERAL && number > 13) {
            throw new IllegalArgumentException("target " + number + ", and version "
                    + version + " reaches 0 to 13");
        }
        return Tunes.target(number);
    }

    /** One row's operation on the effect of one timer, or null where the
     *  row leaves it alone. The columns are read in the order 6.2 lists
     *  them, each verified as it is read, and a start's source is verified
     *  to have the values a row its target reads as the start is built
     *  (8.1). */
    private static @Nullable Effect effect(JsonNode columns, int at, List<Source> sources,
                                           List<Transformation> transformations,
                                           Timer timer, int rows, int version) {
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
                yield Tunes.starting(target(column(columns, "target", at, timer, rows),
                                version),
                        sources.get(source - 1),
                        Chip.prescaler(column(columns, "prescaler", at, timer, rows)),
                        column(columns, "count", at, timer, rows),
                        column(columns, "timerReset", at, timer, rows) == 1,
                        column(columns, "placeReset", at, timer, rows) == 1,
                        version >= DEFERRED
                                && column(columns, "deferred", at, timer, rows) == 1,
                        version >= MAPPED
                                ? mapping(columns, at, timer, rows, transformations)
                                : Tunes.NO_PARTS);
            }
            case RETUNE -> new Retune(new Timing(
                    Chip.prescaler(column(columns, "prescaler", at, timer, rows)),
                    column(columns, "count", at, timer, rows),
                    column(columns, "timerReset", at, timer, rows) == 1,
                    column(columns, "placeReset", at, timer, rows) == 1));
            case STOP -> Tunes.STOP;
            case REMAP -> {
                if (version < MAPPED) {
                    throw shape(at, shape, timer, version);
                }
                yield new Remap(mapping(columns, at, timer, rows, transformations));
            }
            default -> throw shape(at, shape, timer, version);
        };
    }

    /** The error of a shape outside the shapes of {@code version}. */
    private static IllegalArgumentException shape(int at, int shape, Timer timer,
                                                  int version) {
        return new IllegalArgumentException("row " + at + " sets shape " + shape
                + " on Timer " + timer + ", and a shape is " + NONE + ", " + START + ", "
                + RETUNE + (version >= MAPPED ? ", " + STOP + " or " + REMAP
                        : " or " + STOP));
    }

    /**
     * Reads the mapping for a start or remap. Verify the column exists
     * and has the tune's row count, then accept scalar 0 or an array of
     * whole numbers. Reject arrays of only identity parts, including an
     * empty array, before resolving each number against the
     * transformation list (json.md 6.7).
     */
    private static Mapping mapping(JsonNode columns, int at, Timer timer, int rows,
                                   List<Transformation> transformations) {
        JsonNode column = columns.get("mapping");
        if (column == null) {
            throw new IllegalArgumentException("Timer " + timer + " has no \"mapping\""
                    + " column");
        }
        sized(column, rows, "Timer " + timer + "'s mapping");
        JsonNode value = column.get(at);
        if (isWhole(value) && value.intValue() == 0) {
            return Tunes.NO_PARTS;
        }
        boolean wholes = value.isArray();
        boolean identity = true;
        for (JsonNode element : value) {
            wholes = wholes && isWhole(element);
            identity = identity && isWhole(element) && element.intValue() == 0;
        }
        if (!wholes) {
            throw new IllegalArgumentException("Timer " + timer + "'s mapping at row " + at
                    + " is " + value + ", and this form requires 0 or an array of whole"
                    + " numbers");
        }
        if (identity) {
            throw new IllegalArgumentException("Timer " + timer + "'s mapping at row " + at
                    + " is " + value + ", and a mapping of the identity in every part is 0");
        }
        List<Part> parts = new ArrayList<>();
        for (JsonNode element : value) {
            parts.add(part(element.intValue(), at, transformations));
        }
        return new Mapping(parts);
    }

    /**
     * Resolves a file part number: 0 is identity, otherwise the number
     * selects a transformation from 1 upward in file order. The CSV
     * reader uses the same resolution.
     *
     * @throws IllegalArgumentException if the number is outside the
     * transformation list
     */
    static Part part(int number, int at, List<Transformation> transformations) {
        if (number < 0 || number > transformations.size()) {
            throw new IllegalArgumentException("row " + at + " maps through transformation "
                    + number + ", and the tune has " + transformations.size());
        }
        return number == 0 ? Tunes.IDENTITY : transformations.get(number - 1);
    }

    /** Whether a node is a whole number of this form (1.4). */
    private static boolean isWhole(JsonNode value) {
        return value.isIntegralNumber() && value.canConvertToInt();
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
        return whole(column.get(at), "Timer " + timer + "'s " + named + " at row " + at
                + " is " + column.get(at));
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
        // a whole number fits 32 bits (1.4), and a larger one is no row number
        if (!value.isIntegralNumber() || !value.canConvertToInt()) {
            throw new IllegalArgumentException("repeat is " + value
                    + ", and this form requires a row number or null");
        }
        return OptionalInt.of(value.intValue());
    }

    private static String text(JsonNode tree, String key) {
        JsonNode value = tree.get(key);
        if (value == null || !value.isTextual()) {
            throw new IllegalArgumentException(key + " is " + value
                    + ", and this form requires a text");
        }
        return value.textValue();
    }

    private static int number(JsonNode tree, String key) {
        JsonNode value = tree.get(key);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()) {
            throw new IllegalArgumentException(key + " is " + value
                    + ", and this form requires a whole number");
        }
        return value.intValue();
    }

    private static boolean flag(JsonNode tree, String key) {
        JsonNode value = tree.get(key);
        if (value == null || !value.isBoolean()) {
            throw new IllegalArgumentException(key + " is " + value
                    + ", and this form requires true or false");
        }
        return value.booleanValue();
    }

    private static JsonNode array(JsonNode tree, String key) {
        JsonNode value = tree.get(key);
        if (value == null || !value.isArray()) {
            throw new IllegalArgumentException(key + " is " + value
                    + ", and this form requires an array");
        }
        return value;
    }
}
