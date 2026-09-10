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
 * The other form: a tune as tables, for a reader who would rather open one
 * in a spreadsheet than in an editor (doc/csv.md). It holds what
 * {@link Text} holds, and either reads into the same structure.
 *
 * <p>A line beginning {@link #TABLE} names a table and its columns. Every
 * line after it is one row of that table, in ordinary comma-separated
 * values, until the next such line.
 *
 * <pre>
 *   ### tune,tune,title,composer,writer,rate,rows,repeat
 *   1,Circus Attractions #2,Mad Max,ym-to-ymxs,50,4,0
 * </pre>
 *
 * <p>The tables are ordinary tables. A row of a tune is a row here, with a
 * column a register, and a cell that is empty is a register the row does
 * not set. Nothing is folded into runs or events as the text form folds
 * them: what that form does for a reader looking down a stream, this does
 * by being a table a spreadsheet sorts and filters.
 */
public final class Csv {

    /** What a line naming a table and its columns begins with. */
    public static final String TABLE = "### ";

    private Csv() {
    }

    // ---------------------------------------------------------------- out

    /** {@code multi} as tables. */
    public static String write(Multi multi) {
        List<Tune> tunes = multi.tunes();
        StringBuilder out = new StringBuilder();

        table(out, "multi", "format", "version", "tunes");
        row(out, Json.FORMAT, Json.VERSION, tunes.size());

        table(out, "tune", "tune", "title", "composer", "writer", "rate", "rows", "repeat");
        for (int at = 0; at < tunes.size(); at++) {
            Tune tune = tunes.get(at);
            row(out, at + 1, tune.title(), tune.composer(), tune.writer(), tune.rate(),
                    Tunes.size(tune.table()), repeat(tune.table()));
        }

        table(out, "source", "tune", "source", "name", "repeat");
        for (int at = 0; at < tunes.size(); at++) {
            List<Source> sources = Tunes.sources(tunes.get(at));
            for (int one = 0; one < sources.size(); one++) {
                row(out, at + 1, one + 1, Tunes.name(sources.get(one)),
                        repeat(Tunes.table(sources.get(one))));
            }
        }

        table(out, "value", "tune", "source", "row", "value");
        for (int at = 0; at < tunes.size(); at++) {
            List<Source> sources = Tunes.sources(tunes.get(at));
            for (int one = 0; one < sources.size(); one++) {
                List<Integer> values = Tunes.values(sources.get(one));
                for (int line = 0; line < values.size(); line++) {
                    row(out, at + 1, one + 1, line, values.get(line));
                }
            }
        }

        List<Object> named = new ArrayList<>(List.of("row", "tune", "row"));
        for (Register register : Register.values()) {
            named.add(Json.name(register));
        }
        table(out, named.toArray());
        for (int at = 0; at < tunes.size(); at++) {
            List<Row> rows = Tunes.rows(tunes.get(at));
            for (int line = 0; line < rows.size(); line++) {
                Map<Register, Integer> sets = rows.get(line).registers();
                if (sets.isEmpty()) {
                    continue;
                }
                List<Object> said = new ArrayList<>(List.of(at + 1, line));
                for (Register register : Register.values()) {
                    said.add(sets.containsKey(register) ? sets.get(register) : "");
                }
                row(out, said.toArray());
            }
        }

        table(out, "effect", "tune", "row", "timer", "shape", "target", "source",
                "prescaler", "count", "timerReset", "placeReset");
        for (int at = 0; at < tunes.size(); at++) {
            Tune tune = tunes.get(at);
            List<Source> sources = Tunes.sources(tune);
            List<Row> rows = Tunes.rows(tune);
            for (int line = 0; line < rows.size(); line++) {
                for (Map.Entry<Timer, Effect> one : Tunes.effects(rows.get(line)).entrySet()) {
                    switch (one.getValue()) {
                        case Start start -> row(out, at + 1, line, one.getKey(), "start",
                                Tunes.name(start.target()),
                                sources.indexOf(start.source()) + 1,
                                Chip.divides(start.prescaler()), start.count(),
                                start.timerReset(), start.placeReset());
                        case Retune retune -> row(out, at + 1, line, one.getKey(), "retune",
                                "", "", Chip.divides(retune.prescaler()), retune.count(),
                                retune.timerReset(), retune.placeReset());
                        case Stop ignored -> row(out, at + 1, line, one.getKey(), "stop",
                                "", "", "", "", "", "");
                    }
                }
            }
        }
        return out.toString();
    }

    /** A line naming a table and its columns, with a blank line before it. */
    private static void table(StringBuilder out, Object... named) {
        if (out.length() > 0) {
            out.append('\n');
        }
        out.append(TABLE.strip()).append(' ');
        row(out, named);
    }

    /** One row, its cells quoted where a cell holds a comma, a quote or a
     *  space at either end. */
    private static void row(StringBuilder out, Object... cells) {
        for (int at = 0; at < cells.length; at++) {
            out.append(at > 0 ? "," : "").append(cell(String.valueOf(cells[at])));
        }
        out.append('\n');
    }

    private static String cell(String said) {
        if (said.indexOf('\n') >= 0 || said.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("a value holding a line feed, which this form"
                    + " cannot hold: " + said);
        }
        boolean quote = said.indexOf(',') >= 0 || said.indexOf('"') >= 0
                || !said.equals(said.strip()) || said.startsWith("#");
        return quote ? '"' + said.replace("\"", "\"\"") + '"' : said;
    }

    private static String repeat(Table<?> table) {
        return table.repeat().isPresent() ? String.valueOf(table.repeat().getAsInt()) : "";
    }

    // ----------------------------------------------------------------- in

    /** One table: what its columns are called, and its rows. */
    private record Held(List<String> columns, List<List<String>> rows) {

        /** The cell {@code named} of {@code row}, or an empty text where
         *  the table has no such column. */
        String of(List<String> row, String named) {
            int at = columns.indexOf(named);
            return at < 0 || at >= row.size() ? "" : row.get(at);
        }
    }

    /** The multi {@code text} holds.
     *
     * @throws IllegalArgumentException where the text is not this form, or
     *     states a structure no player plays
     */
    public static Multi read(String text) {
        Map<String, Held> tables = tables(text);
        Held multi = table(tables, "multi");
        if (multi.rows().size() != 1) {
            throw new IllegalArgumentException("the multi table holds " + multi.rows().size()
                    + " rows, and it holds one");
        }
        String format = multi.of(multi.rows().get(0), "format");
        if (!format.equals(Json.FORMAT)) {
            throw new IllegalArgumentException("a text of " + format + ", and this reads "
                    + Json.FORMAT);
        }
        int version = number(multi.of(multi.rows().get(0), "version"), "version");
        if (version != Json.VERSION) {
            throw new IllegalArgumentException("version " + version + ", and this reads "
                    + Json.VERSION);
        }

        Held told = table(tables, "tune");
        List<List<Integer>> counts = new ArrayList<>();
        List<List<String>> named = new ArrayList<>();
        List<List<OptionalInt>> repeats = new ArrayList<>();
        List<List<Map<Register, Integer>>> registers = new ArrayList<>();
        List<List<Map<Timer, Effect>>> effects = new ArrayList<>();
        for (List<String> one : told.rows()) {
            int rows = number(told.of(one, "rows"), "rows");
            counts.add(new ArrayList<>());
            named.add(new ArrayList<>());
            repeats.add(new ArrayList<>());
            registers.add(empty(rows, Register.class));
            effects.add(empty(rows, Timer.class));
        }

        Held sources = table(tables, "source");
        for (List<String> one : sources.rows()) {
            int tune = number(sources.of(one, "tune"), "tune") - 1;
            named.get(tune).add(sources.of(one, "name"));
            repeats.get(tune).add(maybe(sources.of(one, "repeat")));
            counts.get(tune).add(0);
        }
        List<List<List<Integer>>> values = new ArrayList<>();
        for (List<String> tune : named) {
            List<List<Integer>> held = new ArrayList<>();
            for (int at = 0; at < tune.size(); at++) {
                held.add(new ArrayList<>());
            }
            values.add(held);
        }
        Held said = table(tables, "value");
        for (List<String> one : said.rows()) {
            int tune = number(said.of(one, "tune"), "tune") - 1;
            int source = number(said.of(one, "source"), "source") - 1;
            if (source < 0 || source >= values.get(tune).size()) {
                throw new IllegalArgumentException("a value of source "
                        + said.of(one, "source") + ", and tune " + (tune + 1) + " holds "
                        + values.get(tune).size());
            }
            values.get(tune).get(source).add(number(said.of(one, "value"), "value"));
        }
        List<List<Source>> built = new ArrayList<>();
        for (int at = 0; at < named.size(); at++) {
            List<Source> held = new ArrayList<>();
            for (int one = 0; one < named.get(at).size(); one++) {
                held.add(new Single(named.get(at).get(one),
                        new Table<>(values.get(at).get(one), repeats.get(at).get(one))));
            }
            built.add(held);
        }

        Held table = table(tables, "row");
        for (List<String> one : table.rows()) {
            int tune = number(table.of(one, "tune"), "tune") - 1;
            int at = row(registers.get(tune).size(), table.of(one, "row"), "a row");
            for (Register register : Register.values()) {
                String cell = table.of(one, Json.name(register));
                if (!cell.isEmpty()) {
                    registers.get(tune).get(at).put(register,
                            number(cell, Json.name(register)));
                }
            }
        }

        Held acts = table(tables, "effect");
        for (List<String> one : acts.rows()) {
            int tune = number(acts.of(one, "tune"), "tune") - 1;
            int at = row(effects.get(tune).size(), acts.of(one, "row"), "an effect");
            effects.get(tune).get(at).put(Timer.valueOf(acts.of(one, "timer")),
                    effect(acts, one, built.get(tune), at));
        }

        List<Tune> tunes = new ArrayList<>();
        for (int at = 0; at < told.rows().size(); at++) {
            List<String> one = told.rows().get(at);
            List<Row> rows = new ArrayList<>();
            for (int line = 0; line < registers.get(at).size(); line++) {
                rows.add(new Row(registers.get(at).get(line), effects.get(at).get(line)));
            }
            tunes.add(new Tune(told.of(one, "title"), told.of(one, "composer"),
                    told.of(one, "writer"), number(told.of(one, "rate"), "rate"),
                    new Table<>(rows, maybe(told.of(one, "repeat")))));
        }
        return Check.must(new Multi(tunes));
    }

    private static Effect effect(Held acts, List<String> one, List<Source> sources, int at) {
        String shape = acts.of(one, "shape");
        return switch (shape) {
            case "start" -> {
                int number = number(acts.of(one, "source"), "source");
                if (number < 1 || number > sources.size()) {
                    throw new IllegalArgumentException("row " + at + " starts source "
                            + number + ", and the tune holds " + sources.size());
                }
                yield new Start(Json.target(acts.of(one, "target")), sources.get(number - 1),
                        Chip.prescaler(number(acts.of(one, "prescaler"), "prescaler")),
                        number(acts.of(one, "count"), "count"),
                        flag(acts.of(one, "timerReset")), flag(acts.of(one, "placeReset")));
            }
            case "retune" -> new Retune(
                    Chip.prescaler(number(acts.of(one, "prescaler"), "prescaler")),
                    number(acts.of(one, "count"), "count"),
                    flag(acts.of(one, "timerReset")), flag(acts.of(one, "placeReset")));
            case "stop" -> Tunes.STOP;
            default -> throw new IllegalArgumentException("row " + at + " states \"" + shape
                    + "\" of an effect, and it states one of start, retune and stop");
        };
    }

    private static int row(int rows, String said, String what) {
        int at = number(said, "row");
        if (at < 0 || at >= rows) {
            throw new IllegalArgumentException(what + " at row " + at + ", and the tune holds "
                    + rows + " rows");
        }
        return at;
    }

    private static <K extends Enum<K>, V> List<Map<K, V>> empty(int rows, Class<K> of) {
        List<Map<K, V>> out = new ArrayList<>();
        for (int at = 0; at < rows; at++) {
            out.add(new EnumMap<>(of));
        }
        return out;
    }

    private static int number(String said, String what) {
        try {
            return Integer.parseInt(said.strip());
        } catch (NumberFormatException wrong) {
            throw new IllegalArgumentException(what + " is \"" + said + "\", and a whole"
                    + " number is asked");
        }
    }

    private static boolean flag(String said) {
        return Boolean.parseBoolean(said.strip());
    }

    private static OptionalInt maybe(String said) {
        return said.isEmpty() ? OptionalInt.empty() : OptionalInt.of(number(said, "repeat"));
    }

    private static Held table(Map<String, Held> tables, String named) {
        Held held = tables.get(named);
        if (held == null) {
            throw new IllegalArgumentException("no \"" + TABLE + named + "\" table");
        }
        return held;
    }

    /** The tables the text holds, by the name each one's line gives. */
    private static Map<String, Held> tables(String text) {
        Map<String, Held> out = new LinkedHashMap<>();
        Held here = null;
        for (String line : text.split("\n", -1)) {
            if (line.isBlank()) {
                continue;
            }
            if (line.startsWith("###")) {
                List<String> named = cells(line.substring(3).strip());
                if (named.isEmpty()) {
                    throw new IllegalArgumentException("a table with no name: " + line);
                }
                here = new Held(named.subList(1, named.size()), new ArrayList<>());
                out.put(named.get(0), here);
                continue;
            }
            if (here == null) {
                throw new IllegalArgumentException("a row before any table names its"
                        + " columns: " + line);
            }
            here.rows().add(cells(line));
        }
        return out;
    }

    /** One line's cells, a quoted cell holding what it holds and two
     *  quotes standing for one. */
    static List<String> cells(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder one = new StringBuilder();
        boolean quoted = false;
        for (int at = 0; at < line.length(); at++) {
            char c = line.charAt(at);
            if (quoted) {
                if (c == '"') {
                    if (at + 1 < line.length() && line.charAt(at + 1) == '"') {
                        one.append('"');
                        at++;
                    } else {
                        quoted = false;
                    }
                } else {
                    one.append(c);
                }
            } else if (c == '"' && one.length() == 0) {
                quoted = true;
            } else if (c == ',') {
                out.add(one.toString());
                one.setLength(0);
            } else {
                one.append(c);
            }
        }
        out.add(one.toString());
        return out;
    }
}
