package org.ymxs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import org.ymxs.YMXS.Effect;
import org.ymxs.YMXS.Multi;
import org.ymxs.YMXS.Pair;
import org.ymxs.YMXS.Register;
import org.ymxs.YMXS.Retune;
import org.ymxs.YMXS.Row;
import org.ymxs.YMXS.Single;
import org.ymxs.YMXS.Source;
import org.ymxs.YMXS.Start;
import org.ymxs.YMXS.StartOne;
import org.ymxs.YMXS.Stop;
import org.ymxs.YMXS.Table;
import org.ymxs.YMXS.Three;
import org.ymxs.YMXS.Timer;
import org.ymxs.YMXS.Timing;
import org.ymxs.YMXS.Triple;
import org.ymxs.YMXS.Tune;
import org.ymxs.YMXS.Two;

/**
 * The other form: a tune as tables, for reading in a spreadsheet
 * (doc/csv.md). It writes what {@link Text} writes, and both read into the
 * same structure.
 *
 * <p>A line beginning {@link #TABLE} opens a table and names it. The line
 * after it names the columns, and every line after that is one row of the
 * table, in ordinary comma-separated values, until the next such line. The
 * column names are over the cells they name.
 *
 * <pre>
 *   ### tune
 *   title,composer,writer,rate,rows,repeat
 *   Circus Attractions #2,Mad Max,ym-to-ymxs,50,4,0
 * </pre>
 *
 * <p>These are ordinary tables, and they are the tables {@link Json}
 * writes as JSON: one form follows from the other. A row of a tune is a
 * row here, with a column a register, and an empty cell is a register the
 * row does not set.
 *
 * <p>A tune opens with a `tune` table, and the tables after it belong to
 * that tune until the next `tune` table; a source opens with a `source`
 * table, and the values after it belong to that source. A table therefore
 * needs no column for its tune or its source: position determines that.
 */
public final class Csv {

    /** What a line opening a table begins with. */
    public static final String TABLE = "### ";

    private Csv() {
    }

    // ---------------------------------------------------------------- out

    /** {@code multi} as tables. */
    public static String write(Multi multi) {
        StringBuilder out = new StringBuilder();
        table(out, "multi", "format", "version", "tunes");
        row(out, Json.FORMAT, Json.VERSION, multi.tunes().size());
        for (Tune tune : multi.tunes()) {
            tune(out, tune);
        }
        return out.toString();
    }

    /** One tune: its `tune` table, then the tables that belong to it. */
    private static void tune(StringBuilder out, Tune tune) {
        table(out, "tune", "title", "composer", "writer", "rate", "rows", "repeat");
        row(out, tune.title(), tune.composer(), tune.writer(), tune.rate(),
                Tunes.size(tune.table()), repeat(tune.table()));

        List<Source> sources = Tunes.sources(tune);
        for (Source source : sources) {
            table(out, "source", "name", "repeat");
            row(out, Tunes.name(source), repeat(Tunes.rows(source)));
            // 3.6: the cells are row and value, and value2 and value3
            // where the source has two or three values a row
            // 3.6: the cell is value where the source has one value a
            // row, and value1 to valueU where it has more
            List<Object> named = new ArrayList<>(List.of("value", "row"));
            if (Tunes.columns(source) == 1) {
                named.add("value");
            } else {
                for (int at = 1; at <= Tunes.columns(source); at++) {
                    named.add("value" + at);
                }
            }
            table(out, named.toArray());
            List<List<Integer>> lines = Tunes.rows(source).rows();
            for (int line = 0; line < lines.size(); line++) {
                List<Object> cells = new ArrayList<>();
                cells.add(line);
                cells.addAll(lines.get(line));
                row(out, cells.toArray());
            }
        }

        List<Object> named = new ArrayList<>(List.of("registers", "row"));
        for (Register register : Register.values()) {
            named.add(Json.name(register));
        }
        table(out, named.toArray());
        List<Row> rows = Tunes.rows(tune);
        for (int line = 0; line < rows.size(); line++) {
            Map<Register, Integer> sets = rows.get(line).registers();
            if (sets.isEmpty()) {
                continue;
            }
            List<Object> said = new ArrayList<>(List.of((Object) line));
            for (Register register : Register.values()) {
                said.add(sets.containsKey(register) ? sets.get(register) : "");
            }
            row(out, said.toArray());
        }

        for (Timer timer : Timer.values()) {
            timer(out, timer, rows, sources);
        }
    }

    /** One timer's table, written where some row acts on that timer. The
     *  values are the ones {@link Json} writes, and a cell is empty where
     *  that form writes {@code -1}. */
    private static void timer(StringBuilder out, Timer timer, List<Row> rows,
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
        table(out, "timer" + timer.name(), "row", "shape", "target", "source",
                "prescaler", "count", "timerReset", "placeReset");
        for (int line = 0; line < rows.size(); line++) {
            Effect effect = rows.get(line).effects().get(timer);
            if (effect == null) {
                continue;
            }
            switch (effect) {
                case Start start -> row(out, line, Json.START,
                        Tunes.number(Tunes.target(start)), sources.indexOf(Tunes.source(start)) + 1,
                        Chip.divides(Tunes.prescaler(start)), Tunes.count(start),
                        Tunes.timerReset(start) ? 1 : 0, Tunes.placeReset(start) ? 1 : 0);
                case Retune retune -> row(out, line, Json.RETUNE, "", "",
                        Chip.divides(retune.timing().prescaler()), retune.timing().count(),
                        retune.timing().timerReset() ? 1 : 0, retune.timing().placeReset() ? 1 : 0);
                case Stop ignored -> row(out, line, Json.STOP, "", "", "", "", "", "");
            }
        }
    }

    /** A line opening a table and naming its columns, after a blank line. */
    private static void table(StringBuilder out, Object... named) {
        if (out.length() > 0) {
            out.append('\n');
        }
        out.append(TABLE.strip()).append(' ').append(named[0]).append('\n');
        row(out, Arrays.copyOfRange(named, 1, named.length));
    }

    /** One row, each cell quoted where it has a comma, a quote or a
     *  leading or trailing space. */
    private static void row(StringBuilder out, Object... cells) {
        for (int at = 0; at < cells.length; at++) {
            out.append(at > 0 ? "," : "").append(cell(String.valueOf(cells[at])));
        }
        out.append('\n');
    }

    private static String cell(String said) {
        if (said.indexOf('\n') >= 0 || said.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("a value with a line feed in it, which this"
                    + " form cannot write: " + said);
        }
        boolean quote = said.indexOf(',') >= 0 || said.indexOf('"') >= 0
                || !said.equals(said.strip()) || said.startsWith("#");
        return quote ? '"' + said.replace("\"", "\"\"") + '"' : said;
    }

    private static String repeat(Table<?> table) {
        return table.repeat().isPresent() ? String.valueOf(table.repeat().getAsInt()) : "";
    }

    // ----------------------------------------------------------------- in

    /** One table: its name, its column names, and its rows. */
    private record Block(String name, List<String> columns, List<List<String>> rows) {

        /** The cell {@code named} of {@code row}, or empty text where the
         *  table defines no such column. */
        String of(List<String> row, String named) {
            int at = columns.indexOf(named);
            return at < 0 || at >= row.size() ? "" : row.get(at);
        }
    }

    /** The multi in {@code text}.
     *
     * @throws IllegalArgumentException where the text is not this form, or
     *     is a structure no player plays
     */
    public static Multi read(String text) {
        List<Block> sections = sections(text);
        if (sections.isEmpty() || !sections.get(0).name().equals("multi")) {
            throw new IllegalArgumentException("the first table is not \"" + TABLE
                    + "multi\"");
        }
        Block multi = sections.get(0);
        if (multi.rows().size() != 1) {
            throw new IllegalArgumentException("the multi table has " + multi.rows().size()
                    + " rows, and one row opens it");
        }
        String format = multi.of(multi.rows().get(0), "format");
        if (!format.equals(Json.FORMAT)) {
            throw new IllegalArgumentException("a text of " + format + ", and this reads "
                    + Json.FORMAT);
        }
        int version = number(multi.of(multi.rows().get(0), "version"), "version");
        if (version != Json.VERSION && version != Json.BEFORE) {
            throw new IllegalArgumentException("version " + version + ", and this reads "
                    + Json.BEFORE + " or " + Json.VERSION);
        }
        List<Tune> tunes = new ArrayList<>();
        int at = 1;
        while (at < sections.size()) {
            if (!sections.get(at).name().equals("tune")) {
                throw new IllegalArgumentException("a \"" + TABLE + sections.get(at).name()
                        + "\" table before any tune opens");
            }
            int from = at++;
            List<Block> mine = new ArrayList<>();
            while (at < sections.size() && !sections.get(at).name().equals("tune")) {
                mine.add(sections.get(at));
                at++;
            }
            tunes.add(tune(sections.get(from), mine, tunes.size() + 1, version));
        }
        return Check.must(new Multi(tunes));
    }

    /** One tune, from the table that opens it and the tables after it. A
     *  source opens a `source` table, and the values after it belong to
     *  that source. */
    /** One source out of its name, its rows and its repeat: a row of one
     *  value where the value block has the `value` cell alone, and of two
     *  or three where it has `value2` and `value3` (3.6). A file of the
     *  version before this one has one value a row (json.md 2.4). */
    private static Source source(String name, List<List<Integer>> rows,
            OptionalInt repeat, int version) {
        for (List<Integer> row : rows) {
            if (row.size() != rows.get(0).size()) {
                throw new IllegalArgumentException("source " + name + " has rows of "
                        + rows.get(0).size() + " and of " + row.size()
                        + " values, and a source has one shape");
            }
            if (row.size() > 1 && version < Json.VERSION) {
                throw new IllegalArgumentException("source " + name + " has a row of"
                        + " several values, and version " + version
                        + " has one value a row");
            }
        }
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

    private static Tune tune(Block told, List<Block> mine, int number, int version) {
        if (told.rows().size() != 1) {
            throw new IllegalArgumentException("tune " + number + " is opened by "
                    + told.rows().size() + " rows, and one row opens it");
        }
        List<String> one = told.rows().get(0);
        int count = number(told.of(one, "rows"), "rows");
        List<String> names = new ArrayList<>();
        List<OptionalInt> repeats = new ArrayList<>();
        List<List<List<Integer>>> values = new ArrayList<>();
        List<Map<Register, Integer>> registers = empty(count, Register.class);
        List<Map<Timer, Effect>> effects = empty(count, Timer.class);
        List<Block> acts = new ArrayList<>();
        for (Block block : mine) {
            switch (block.name()) {
                case "source" -> {
                    if (block.rows().size() != 1) {
                        throw new IllegalArgumentException("tune " + number + " opens a"
                                + " source with " + block.rows().size() + " rows, and"
                                + " one row opens it");
                    }
                    names.add(block.of(block.rows().get(0), "name"));
                    repeats.add(maybe(block.of(block.rows().get(0), "repeat")));
                    values.add(new ArrayList<>());
                }
                case "value" -> {
                    if (values.isEmpty()) {
                        throw new IllegalArgumentException("tune " + number + " opens values"
                                + " before any source");
                    }
                    List<List<Integer>> last = values.get(values.size() - 1);
                    for (List<String> line : block.rows()) {
                        List<Integer> row = new ArrayList<>();
                        // the cell is value where the source has one value a
                        // row, and value1 where it has more (3.6)
                        String first = block.of(line, "value");
                        String named = "value";
                        if (first.isEmpty()) {
                            first = block.of(line, "value1");
                            named = "value1";
                        }
                        if (first.isEmpty()) {
                            throw new IllegalArgumentException("tune " + number + " opens"
                                    + " a value with neither a value cell nor a value1");
                        }
                        row.add(number(first, named));
                        for (int at = 2; at <= 3; at++) {
                            String cell = block.of(line, "value" + at);
                            if (!cell.isEmpty()) {
                                row.add(number(cell, "value" + at));
                            }
                        }
                        last.add(row);
                    }
                }
                case "registers" -> {
                    for (List<String> line : block.rows()) {
                        int at = row(count, block.of(line, "row"), "tune " + number
                                + " sets a row");
                        for (Register register : Register.values()) {
                            String cell = block.of(line, Json.name(register));
                            if (!cell.isEmpty()) {
                                registers.get(at).put(register,
                                        number(cell, Json.name(register)));
                            }
                        }
                    }
                }
                default -> {
                    if (!block.name().startsWith("timer")) {
                        throw new IllegalArgumentException("tune " + number + " opens a \""
                                + TABLE + block.name() + "\" table, which this form does"
                                + " not have");
                    }
                    acts.add(block);
                }
            }
        }
        List<Source> sources = new ArrayList<>();
        for (int at = 0; at < names.size(); at++) {
            sources.add(source(names.get(at), values.get(at), repeats.get(at),
                    version));
        }
        for (Block block : acts) {
            Timer timer = timer(block.name(), number);
            for (List<String> line : block.rows()) {
                int at = row(count, block.of(line, "row"), "tune " + number
                        + " sets an effect");
                effects.get(at).put(timer, effect(block, line, sources, at));
            }
        }
        List<Row> rows = new ArrayList<>();
        for (int at = 0; at < count; at++) {
            rows.add(new Row(registers.get(at), effects.get(at)));
        }
        Tune tune = new Tune(told.of(one, "title"), told.of(one, "composer"),
                told.of(one, "writer"), number(told.of(one, "rate"), "rate"),
                new Table<>(rows, maybe(told.of(one, "repeat"))));
        List<String> wrong = Check.declared(sources, tune);
        if (!wrong.isEmpty()) {
            throw new IllegalArgumentException(String.join("\n", wrong));
        }
        return tune;
    }

    /** The timer a table of that name is for. */
    private static Timer timer(String table, int tune) {
        String said = table.substring("timer".length());
        for (Timer timer : Timer.values()) {
            if (timer.name().equals(said)) {
                return timer;
            }
        }
        throw new IllegalArgumentException("tune " + tune + " opens a \"" + TABLE + table
                + "\" table, and a timer is timerA to timer"
                + Timer.values()[Timer.values().length - 1]);
    }

    private static Effect effect(Block acts, List<String> one, List<Source> sources, int at) {
        int shape = number(acts.of(one, "shape"), "shape");
        return switch (shape) {
            case Json.START -> {
                int number = number(acts.of(one, "source"), "source");
                if (number < 1 || number > sources.size()) {
                    throw new IllegalArgumentException("row " + at + " starts source "
                            + number + ", and the tune runs " + sources.size());
                }
                yield Tunes.starting(Tunes.target(number(acts.of(one, "target"), "target")),
                        sources.get(number - 1),
                        Chip.prescaler(number(acts.of(one, "prescaler"), "prescaler")),
                        number(acts.of(one, "count"), "count"),
                        flag(acts.of(one, "timerReset")), flag(acts.of(one, "placeReset")));
            }
            case Json.RETUNE -> new Retune(new Timing(Chip.prescaler(number(acts.of(one, "prescaler"), "prescaler")), number(acts.of(one, "count"), "count"), flag(acts.of(one, "timerReset")), flag(acts.of(one, "placeReset"))));
            case Json.STOP -> Tunes.STOP;
            default -> throw new IllegalArgumentException("row " + at + " sets shape "
                    + shape + " of an effect, and a shape is " + Json.START + ", "
                    + Json.RETUNE + " or " + Json.STOP);
        };
    }

    private static int row(int rows, String said, String what) {
        int at = number(said, "row");
        if (at < 0 || at >= rows) {
            throw new IllegalArgumentException(what + " at row " + at + ", and the tune runs "
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
            throw new IllegalArgumentException(what + " is \"" + said
                    + "\", and this form requires a whole number");
        }
    }

    /** A cell of 1 or 0, as {@link Json} writes one. */
    private static boolean flag(String said) {
        return number(said, "true or false") == 1;
    }

    private static OptionalInt maybe(String said) {
        return said.isEmpty() ? OptionalInt.empty() : OptionalInt.of(number(said, "repeat"));
    }

    /** The tables in the text, in file order. */
    private static List<Block> sections(String text) {
        List<Block> out = new ArrayList<>();
        Block here = null;
        // The table whose column names the next line is, none where the
        // line is a row of the table open.
        String naming = null;
        for (String line : text.split("\n", -1)) {
            if (line.isBlank()) {
                continue;
            }
            if (line.startsWith("###")) {
                if (naming != null) {
                    throw new IllegalArgumentException(unnamed(naming));
                }
                String name = line.substring(3).strip();
                if (name.isBlank()) {
                    throw new IllegalArgumentException("a table with no name: " + line);
                }
                if (name.indexOf(',') >= 0) {
                    throw new IllegalArgumentException("the table name \"" + name
                            + "\" has a comma in it: a name stands alone on its line, and"
                            + " the column names on the line after it");
                }
                here = new Block(name, new ArrayList<>(), new ArrayList<>());
                out.add(here);
                naming = name;
                continue;
            }
            if (here == null) {
                throw new IllegalArgumentException("a row before any table opens: " + line);
            }
            if (naming != null) {
                here.columns().addAll(cells(line));
                naming = null;
                continue;
            }
            here.rows().add(cells(line));
        }
        if (naming != null) {
            throw new IllegalArgumentException(unnamed(naming));
        }
        return out;
    }

    /** A table whose name is the last line of it. */
    private static String unnamed(String name) {
        return "the \"" + TABLE + name + "\" table names no columns: the line after the"
                + " name is the column names";
    }

    /** One line's cells; a quoted cell is its content, and two quotes
     *  inside one encode a single quote. */
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
