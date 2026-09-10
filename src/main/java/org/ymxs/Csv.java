package org.ymxs;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
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
 * The other form: a {@link Multi} written as a table a structure, for a
 * reader that would rather open a tune in a spreadsheet than in an editor
 * (doc/csv.md).
 *
 * <p>Fields are held apart by {@link #FIELD}, and a line beginning
 * {@link #CLASS} names the structure whose rows come next. The line after
 * that names the fields, and every line after it is one of them, until the
 * next {@link #CLASS} line.
 *
 * <p>It holds what the JSON form holds and writes it the same way round: a
 * register as runs of the rows that set it, and the effects as events at
 * their own row. What a run holds and what a source holds are as many
 * fields as they have values, so the last field a header names takes the
 * rest of the line.
 */
public final class Csv {

    /** What holds two fields apart. */
    public static final String FIELD = "###";

    /** What a line naming a structure begins with. */
    public static final String CLASS = "class;";

    /** What stands where a field has no value. */
    public static final String NONE = "";

    private Csv() {
    }

    // ---------------------------------------------------------------- out

    /** {@code multi} as a table a structure. */
    public static String write(Multi multi) {
        List<Tune> tunes = multi.tunes();
        StringBuilder out = new StringBuilder();
        block(out, "multi", "format", "version", "tunes");
        row(out, Json.FORMAT, Json.VERSION, tunes.size());

        block(out, "tune", "tune", "title", "composer", "writer", "rate", "rows", "repeat");
        for (int at = 0; at < tunes.size(); at++) {
            Tune tune = tunes.get(at);
            row(out, at + 1, tune.title(), tune.composer(), tune.writer(), tune.rate(),
                    Tunes.size(tune.table()), repeat(tune.table()));
        }

        block(out, "source", "tune", "source", "name", "repeat", "values");
        for (int at = 0; at < tunes.size(); at++) {
            List<Source> sources = Tunes.sources(tunes.get(at));
            for (int one = 0; one < sources.size(); one++) {
                Source source = sources.get(one);
                List<Object> said = new ArrayList<>(List.of(at + 1, one + 1,
                        Tunes.name(source), repeat(Tunes.table(source))));
                said.addAll(Tunes.values(source));
                row(out, said.toArray());
            }
        }

        block(out, "run", "tune", "register", "gap", "values");
        for (int at = 0; at < tunes.size(); at++) {
            runs(out, at + 1, tunes.get(at));
        }

        block(out, "start", "tune", "row", "timer", "target", "source", "prescaler", "count",
                "timerReset", "placeReset");
        events(out, tunes, Start.class);
        block(out, "retune", "tune", "row", "timer", "prescaler", "count", "timerReset",
                "placeReset");
        events(out, tunes, Retune.class);
        block(out, "stop", "tune", "row", "timer");
        events(out, tunes, Stop.class);
        return out.toString();
    }

    /** One register's runs, as the JSON form has them. */
    private static void runs(StringBuilder out, int number, Tune tune) {
        List<Row> rows = Tunes.rows(tune);
        for (Register register : Register.values()) {
            int at = 0;
            int end = 0;
            while (at < rows.size()) {
                if (!rows.get(at).registers().containsKey(register)) {
                    at++;
                    continue;
                }
                int from = at;
                List<Object> said = new ArrayList<>();
                while (at < rows.size() && rows.get(at).registers().containsKey(register)) {
                    said.add(rows.get(at).registers().get(register));
                    at++;
                }
                List<Object> whole = new ArrayList<>(List.of(number,
                        Json.name(register), from - end));
                whole.addAll(said);
                end = at;
                row(out, whole.toArray());
            }
        }
    }

    /** Every event of one shape, in row order within each tune. */
    private static void events(StringBuilder out, List<Tune> tunes, Class<?> shape) {
        for (int at = 0; at < tunes.size(); at++) {
            Tune tune = tunes.get(at);
            List<Source> sources = Tunes.sources(tune);
            List<Row> rows = Tunes.rows(tune);
            for (int line = 0; line < rows.size(); line++) {
                for (Map.Entry<Timer, Effect> one : Tunes.effects(rows.get(line)).entrySet()) {
                    if (!shape.isInstance(one.getValue())) {
                        continue;
                    }
                    switch (one.getValue()) {
                        case Start start -> row(out, at + 1, line, one.getKey(),
                                Tunes.name(start.target()),
                                sources.indexOf(start.source()) + 1,
                                Chip.divides(start.prescaler()), start.count(),
                                start.timerReset(), start.placeReset());
                        case Retune retune -> row(out, at + 1, line, one.getKey(),
                                Chip.divides(retune.prescaler()), retune.count(),
                                retune.timerReset(), retune.placeReset());
                        case Stop ignored -> row(out, at + 1, line, one.getKey());
                    }
                }
            }
        }
    }

    private static void block(StringBuilder out, String named, String... fields) {
        if (out.length() > 0) {
            out.append('\n');
        }
        out.append(CLASS).append(named).append('\n');
        row(out, (Object[]) fields);
    }

    private static void row(StringBuilder out, Object... fields) {
        for (int at = 0; at < fields.length; at++) {
            String said = String.valueOf(fields[at]);
            if (said.contains(FIELD) || said.contains("\n")) {
                throw new IllegalArgumentException("a value this form cannot hold, since it"
                        + " holds \"" + FIELD + "\" or a line feed: " + said);
            }
            out.append(at > 0 ? FIELD : "").append(said);
        }
        out.append('\n');
    }

    private static String repeat(Table<?> table) {
        return table.repeat().isPresent() ? String.valueOf(table.repeat().getAsInt()) : NONE;
    }

    // ----------------------------------------------------------------- in

    /** The multi {@code text} holds.
     *
     * @throws IllegalArgumentException where the text is not this form, or
     *     states a structure no player plays
     */
    public static Multi read(String text) {
        Map<String, List<List<String>>> blocks = blocks(text);
        List<List<String>> said = block(blocks, "multi");
        if (said.size() != 1) {
            throw new IllegalArgumentException("the multi block holds " + said.size()
                    + " rows, and it holds one");
        }
        String format = said.get(0).get(0);
        if (!format.equals(Json.FORMAT)) {
            throw new IllegalArgumentException("a text of " + format + ", and this reads "
                    + Json.FORMAT);
        }
        int version = Integer.parseInt(said.get(0).get(1));
        if (version != Json.VERSION) {
            throw new IllegalArgumentException("version " + version + ", and this reads "
                    + Json.VERSION);
        }
        List<List<String>> told = block(blocks, "tune");
        List<List<Source>> sources = new ArrayList<>();
        List<List<Map<Register, Integer>>> registers = new ArrayList<>();
        List<List<Map<Timer, Effect>>> effects = new ArrayList<>();
        for (List<String> one : told) {
            int rows = Integer.parseInt(one.get(5));
            sources.add(new ArrayList<>());
            registers.add(empty(rows, Register.class));
            effects.add(empty(rows, Timer.class));
        }
        for (List<String> one : block(blocks, "source")) {
            List<Integer> values = new ArrayList<>();
            for (int at = 4; at < one.size(); at++) {
                values.add(Integer.parseInt(one.get(at)));
            }
            sources.get(Integer.parseInt(one.get(0)) - 1)
                    .add(new Single(one.get(2), new Table<>(values, maybe(one.get(3)))));
        }
        // A run's first field is the rows since the run before it ended,
        // not the row it starts on, so where each register stands is
        // carried from one run to the next.
        List<Map<Register, Integer>> ends = new ArrayList<>();
        for (int at = 0; at < told.size(); at++) {
            ends.add(new EnumMap<>(Register.class));
        }
        for (List<String> one : block(blocks, "run")) {
            int number = Integer.parseInt(one.get(0)) - 1;
            List<Map<Register, Integer>> rows = registers.get(number);
            Register register = Chip.register(Integer.parseInt(one.get(1).substring(1)));
            int at = ends.get(number).getOrDefault(register, 0) + Integer.parseInt(one.get(2));
            for (int value = 3; value < one.size(); value++) {
                if (at < 0 || at >= rows.size()) {
                    throw new IllegalArgumentException(one.get(1) + " sets row " + at
                            + ", and the tune holds " + rows.size() + " rows");
                }
                rows.get(at++).put(register, Integer.parseInt(one.get(value)));
            }
            ends.get(number).put(register, at);
        }
        for (List<String> one : block(blocks, "start")) {
            List<Source> held = sources.get(Integer.parseInt(one.get(0)) - 1);
            int number = Integer.parseInt(one.get(4));
            if (number < 1 || number > held.size()) {
                throw new IllegalArgumentException("row " + one.get(1) + " starts source "
                        + number + ", and the tune holds " + held.size());
            }
            at(effects, one).put(Timer.valueOf(one.get(2)), new Start(Json.target(one.get(3)),
                    held.get(number - 1), Chip.prescaler(Integer.parseInt(one.get(5))),
                    Integer.parseInt(one.get(6)), Boolean.parseBoolean(one.get(7)),
                    Boolean.parseBoolean(one.get(8))));
        }
        for (List<String> one : block(blocks, "retune")) {
            at(effects, one).put(Timer.valueOf(one.get(2)),
                    new Retune(Chip.prescaler(Integer.parseInt(one.get(3))),
                            Integer.parseInt(one.get(4)), Boolean.parseBoolean(one.get(5)),
                            Boolean.parseBoolean(one.get(6))));
        }
        for (List<String> one : block(blocks, "stop")) {
            at(effects, one).put(Timer.valueOf(one.get(2)), Tunes.STOP);
        }
        List<Tune> tunes = new ArrayList<>();
        for (int at = 0; at < told.size(); at++) {
            List<String> one = told.get(at);
            List<Row> rows = new ArrayList<>();
            for (int line = 0; line < registers.get(at).size(); line++) {
                rows.add(new Row(registers.get(at).get(line), effects.get(at).get(line)));
            }
            tunes.add(new Tune(one.get(1), one.get(2), one.get(3),
                    Integer.parseInt(one.get(4)), new Table<>(rows, maybe(one.get(6)))));
        }
        return Check.must(new Multi(tunes));
    }

    /** The row an event stands on, of the tune it belongs to. */
    private static Map<Timer, Effect> at(List<List<Map<Timer, Effect>>> effects,
                                         List<String> said) {
        List<Map<Timer, Effect>> rows = effects.get(Integer.parseInt(said.get(0)) - 1);
        int at = Integer.parseInt(said.get(1));
        if (at < 0 || at >= rows.size()) {
            throw new IllegalArgumentException("an effect at row " + at + ", and the tune"
                    + " holds " + rows.size() + " rows");
        }
        return rows.get(at);
    }

    private static <K extends Enum<K>, V> List<Map<K, V>> empty(int rows, Class<K> of) {
        List<Map<K, V>> out = new ArrayList<>();
        for (int at = 0; at < rows; at++) {
            out.add(new EnumMap<>(of));
        }
        return out;
    }

    private static OptionalInt maybe(String said) {
        return said.equals(NONE) ? OptionalInt.empty() : OptionalInt.of(Integer.parseInt(said));
    }

    private static List<List<String>> block(Map<String, List<List<String>>> blocks,
                                            String named) {
        List<List<String>> said = blocks.get(named);
        if (said == null) {
            throw new IllegalArgumentException("no \"" + CLASS + named + "\" block");
        }
        return said;
    }

    /** The rows of each block, by the name its class line gives. The line
     *  after a class line names the fields and is not a row. */
    private static Map<String, List<List<String>>> blocks(String text) {
        Map<String, List<List<String>>> out = new LinkedHashMap<>();
        List<List<String>> here = null;
        boolean fields = false;
        for (String line : text.split("\n", -1)) {
            if (line.isBlank()) {
                continue;
            }
            if (line.startsWith(CLASS)) {
                here = new ArrayList<>();
                out.put(line.substring(CLASS.length()).strip(), here);
                fields = true;
                continue;
            }
            if (here == null) {
                throw new IllegalArgumentException("a line before any \"" + CLASS
                        + "\" line: " + line);
            }
            if (fields) {
                fields = false;
                continue;
            }
            here.add(List.of(line.split(java.util.regex.Pattern.quote(FIELD), -1)));
        }
        return out;
    }
}
