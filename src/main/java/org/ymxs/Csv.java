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
 * The other form: a tune as tables, for a reader who would rather open
 * one in a spreadsheet than in an editor (doc/csv.md). It writes what
 * {@link Text} writes, and either reads into the same structure.
 *
 * <p>A line beginning {@link #TABLE} gives a table and its columns. Every
 * line after it is one row of that table, in ordinary comma-separated
 * values, until the next such line.
 *
 * <pre>
 *   ### tune,tune,title,composer,writer,rate,rows,repeat
 *   1,Circus Attractions #2,Mad Max,ym-to-ymxs,50,4,0
 * </pre>
 *
 * <p>The tables are ordinary tables, and they are the tables {@link Json}
 * writes as JSON: a reader that has one form has the other. A row of a
 * tune is a row here, with a column a register, and a cell that is empty
 * is a register the row does not set.
 *
 * <p>A tune opens with its own table and the tables after it are that
 * tune's, until the next tune opens; a source does the same for the values
 * after it. So a table needs no column for its tune or its source: where
 * it stands says that.
 */
public final class Csv {

    /** What a line giving a table and its columns begins with. */
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

    /** One tune: what it is called, and then its own tables. */
    private static void tune(StringBuilder out, Tune tune) {
        table(out, "tune", "title", "composer", "writer", "rate", "frames", "repeat");
        row(out, tune.title(), tune.composer(), tune.writer(), tune.rate(),
                Tunes.size(tune.table()), repeat(tune.table()));

        List<Source> sources = Tunes.sources(tune);
        for (Source source : sources) {
            table(out, "source", "name", "repeat");
            row(out, Tunes.name(source), repeat(Tunes.table(source)));
            table(out, "value", "row", "value");
            List<Integer> values = Tunes.values(source);
            for (int line = 0; line < values.size(); line++) {
                row(out, line, values.get(line));
            }
        }

        List<Object> named = new ArrayList<>(List.of("rows", "row"));
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

    /** One timer's table, where any row acts on it. The values are the
     *  ones {@link Json} writes, and a cell is empty where that form would
     *  say none. */
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
                        Tunes.number(start.target()), sources.indexOf(start.source()) + 1,
                        Chip.divides(start.prescaler()), start.count(),
                        start.timerReset() ? 1 : 0, start.placeReset() ? 1 : 0);
                case Retune retune -> row(out, line, Json.RETUNE, "", "",
                        Chip.divides(retune.prescaler()), retune.count(),
                        retune.timerReset() ? 1 : 0, retune.placeReset() ? 1 : 0);
                case Stop ignored -> row(out, line, Json.STOP, "", "", "", "", "", "");
            }
        }
    }

    /** A line giving a table and its columns, with a blank line before it. */
    private static void table(StringBuilder out, Object... named) {
        if (out.length() > 0) {
            out.append('\n');
        }
        out.append(TABLE.strip()).append(' ');
        row(out, named);
    }

    /** One row, its cells quoted where a cell has a comma, a quote or a
     *  space at either end. */
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

    /** One table: what it is called, what its columns are called, and its
     *  rows. */
    private record Block(String name, List<String> columns, List<List<String>> rows) {

        /** The cell {@code named} of {@code row}, or an empty text where
         *  the table has no such column. */
        String of(List<String> row, String named) {
            int at = columns.indexOf(named);
            return at < 0 || at >= row.size() ? "" : row.get(at);
        }
    }

    /** The multi in {@code text}.
     *
     * @throws IllegalArgumentException where the text is not this form, or
     *     gives a structure no player plays
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
        if (version != Json.VERSION) {
            throw new IllegalArgumentException("version " + version + ", and this reads "
                    + Json.VERSION);
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
            tunes.add(tune(sections.get(from), mine, tunes.size() + 1));
        }
        return Check.must(new Multi(tunes));
    }

    /** One tune, out of the table that opens it and the tables after it.
     *  A source opens its own table, and the values after it are that
     *  source's. */
    private static Tune tune(Block told, List<Block> mine, int number) {
        if (told.rows().size() != 1) {
            throw new IllegalArgumentException("tune " + number + " is opened by "
                    + told.rows().size() + " rows, and one row opens it");
        }
        List<String> one = told.rows().get(0);
        int count = number(told.of(one, "frames"), "frames");
        List<String> names = new ArrayList<>();
        List<OptionalInt> repeats = new ArrayList<>();
        List<List<Integer>> values = new ArrayList<>();
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
                        throw new IllegalArgumentException("tune " + number + " gives values"
                                + " before any source opens");
                    }
                    List<Integer> last = values.get(values.size() - 1);
                    for (List<String> line : block.rows()) {
                        last.add(number(block.of(line, "value"), "value"));
                    }
                }
                case "rows" -> {
                    for (List<String> line : block.rows()) {
                        int at = row(count, block.of(line, "row"), "tune " + number
                                + " gives a row");
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
            sources.add(new Single(names.get(at), new Table<>(values.get(at),
                    repeats.get(at))));
        }
        for (Block block : acts) {
            Timer timer = timer(block.name(), number);
            for (List<String> line : block.rows()) {
                int at = row(count, block.of(line, "row"), "tune " + number
                        + " gives an effect");
                effects.get(at).put(timer, effect(block, line, sources, at));
            }
        }
        List<Row> rows = new ArrayList<>();
        for (int at = 0; at < count; at++) {
            rows.add(new Row(registers.get(at), effects.get(at)));
        }
        return new Tune(told.of(one, "title"), told.of(one, "composer"),
                told.of(one, "writer"), number(told.of(one, "rate"), "rate"),
                new Table<>(rows, maybe(told.of(one, "repeat"))));
    }

    /** The timer a table of that name gives. */
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
                yield new Start(Tunes.target(number(acts.of(one, "target"), "target")),
                        sources.get(number - 1),
                        Chip.prescaler(number(acts.of(one, "prescaler"), "prescaler")),
                        number(acts.of(one, "count"), "count"),
                        flag(acts.of(one, "timerReset")), flag(acts.of(one, "placeReset")));
            }
            case Json.RETUNE -> new Retune(
                    Chip.prescaler(number(acts.of(one, "prescaler"), "prescaler")),
                    number(acts.of(one, "count"), "count"),
                    flag(acts.of(one, "timerReset")), flag(acts.of(one, "placeReset")));
            case Json.STOP -> Tunes.STOP;
            default -> throw new IllegalArgumentException("row " + at + " gives shape "
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
            throw new IllegalArgumentException(what + " is \"" + said + "\", and a whole"
                    + " number is asked");
        }
    }

    /** A cell of 1 or 0, as {@link Json} writes one. */
    private static boolean flag(String said) {
        return number(said, "true or false") == 1;
    }

    private static OptionalInt maybe(String said) {
        return said.isEmpty() ? OptionalInt.empty() : OptionalInt.of(number(said, "repeat"));
    }

    /** The tables in the text, in the order they come. */
    private static List<Block> sections(String text) {
        List<Block> out = new ArrayList<>();
        Block here = null;
        for (String line : text.split("\n", -1)) {
            if (line.isBlank()) {
                continue;
            }
            if (line.startsWith("###")) {
                List<String> heading = cells(line.substring(3).strip());
                if (heading.isEmpty() || heading.get(0).isBlank()) {
                    throw new IllegalArgumentException("a table with no name: " + line);
                }
                here = new Block(heading.get(0), heading.subList(1, heading.size()),
                        new ArrayList<>());
                out.add(here);
                continue;
            }
            if (here == null) {
                throw new IllegalArgumentException("a row before any table gives its"
                        + " columns: " + line);
            }
            here.rows().add(cells(line));
        }
        return out;
    }

    /** One line's cells, a quoted cell giving what it says and two
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
