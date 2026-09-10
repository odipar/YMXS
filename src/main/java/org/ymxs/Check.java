package org.ymxs;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.ymxs.YMXS.Effect;
import org.ymxs.YMXS.Multi;
import org.ymxs.YMXS.Register;
import org.ymxs.YMXS.Retune;
import org.ymxs.YMXS.Row;
import org.ymxs.YMXS.Source;
import org.ymxs.YMXS.Start;
import org.ymxs.YMXS.Stop;
import org.ymxs.YMXS.Table;
import org.ymxs.YMXS.Timer;
import org.ymxs.YMXS.Tune;

/**
 * What a structure has to satisfy for a player to play it, read off the
 * structure rather than held by it.
 *
 * <p>Every function gives what is wrong rather than throwing at the first
 * of it, so one call names everything a writer has to mend. {@link #must}
 * is the other way round, for a caller that would rather stop.
 *
 * <p>What is held to is the two chips and the music: what a register
 * takes, what a timer counts, and that an effect hands a source's row to a
 * target that takes it. What a form can hold is that form's to say, and no
 * limit of one is here.
 *
 * <p>What SPEC.md 6 asks of a writer reads across rows rather than within
 * one, and this does not read it yet.
 */
public final class Check {

    private Check() {
    }

    /** What is wrong with {@code multi}, or an empty list. */
    public static List<String> of(Multi multi) {
        List<String> said = new ArrayList<>();
        if (multi.tunes().isEmpty()) {
            said.add("a multi of no tunes: a host plays one");
        }
        for (int at = 0; at < multi.tunes().size(); at++) {
            for (String one : of(multi.tunes().get(at))) {
                said.add("tune " + (at + 1) + ": " + one);
            }
        }
        return said;
    }

    /** What is wrong with {@code tune}, or an empty list. */
    public static List<String> of(Tune tune) {
        List<String> said = new ArrayList<>();
        if (tune.rate() < 1) {
            said.add("a rate of " + tune.rate() + ": a player is called at least once a"
                    + " second");
        }
        said.addAll(table(tune.table(), "the tune"));
        List<Row> rows = tune.table().rows();
        for (int at = 0; at < rows.size(); at++) {
            for (String one : of(rows.get(at))) {
                said.add("row " + at + ": " + one);
            }
        }
        return said;
    }

    /** What is wrong with {@code row}, or an empty list. */
    public static List<String> of(Row row) {
        List<String> said = new ArrayList<>();
        for (Map.Entry<Register, Integer> one : Tunes.registers(row).entrySet()) {
            int most = Chip.most(one.getKey());
            if (one.getValue() < 0 || one.getValue() > most) {
                said.add(one.getKey() + " takes 0 to " + most + ", and this row sets it to "
                        + one.getValue());
            }
        }
        for (Map.Entry<Timer, Effect> one : Tunes.effects(row).entrySet()) {
            for (String wrong : of(one.getValue())) {
                said.add("Timer " + one.getKey() + ": " + wrong);
            }
        }
        return said;
    }

    /** What is wrong with what a row states of one effect, or an empty
     *  list. */
    public static List<String> of(Effect effect) {
        return switch (effect) {
            case Start start -> {
                List<String> said = new ArrayList<>(count(start.count()));
                said.addAll(runs(start));
                yield said;
            }
            case Retune retune -> count(retune.count());
            case Stop ignored -> List.of();
        };
    }

    /** What is wrong with a source, or an empty list. A source's values
     *  are read against the target that runs it, so this reads what stands
     *  without one. */
    public static List<String> of(Source source) {
        List<String> said = new ArrayList<>(table(Tunes.table(source), "the source"));
        List<Integer> values = Tunes.values(source);
        for (int at = 0; at < values.size(); at++) {
            if (values.get(at) < 0) {
                said.add("row " + at + " is " + values.get(at)
                        + ", and a register takes 0 upward");
            }
        }
        return said;
    }

    /** What a start has to agree on: the target takes the row the source
     *  holds, and the values the source holds are values that register
     *  takes. */
    private static List<String> runs(Start start) {
        List<String> said = new ArrayList<>(of(start.source()));
        if (Tunes.columns(start.target()) != Tunes.columns(start.source())) {
            said.add("a source of " + Tunes.columns(start.source()) + " values a row on "
                    + Tunes.name(start.target()) + ", which takes "
                    + Tunes.columns(start.target()));
            return said;
        }
        int most = Tunes.most(start.target());
        List<Integer> values = Tunes.values(start.source());
        for (int at = 0; at < values.size(); at++) {
            if (values.get(at) > most) {
                said.add("a source on " + Tunes.name(start.target()) + " whose row " + at
                        + " is " + values.get(at) + ", and the target takes 0 to " + most);
            }
        }
        return said;
    }

    private static List<String> count(int count) {
        if (count < 1 || count > Chip.MOST_COUNT) {
            return List.of("a count of " + count + ": a timer counts 1 to "
                    + Chip.MOST_COUNT);
        }
        return List.of();
    }

    private static List<String> table(Table<?> table, String what) {
        List<String> said = new ArrayList<>();
        if (table.rows().isEmpty()) {
            said.add(what + " holds no rows: a clock reads one");
        }
        if (table.repeat().isPresent() && (table.repeat().getAsInt() < 0
                || table.repeat().getAsInt() >= table.rows().size())) {
            said.add(what + " holds " + table.rows().size() + " rows and repeats to row "
                    + table.repeat().getAsInt());
        }
        return said;
    }

    /** {@code multi}, where nothing is wrong with it.
     *
     * @throws IllegalArgumentException naming everything that is
     */
    public static Multi must(Multi multi) {
        List<String> said = of(multi);
        if (!said.isEmpty()) {
            throw new IllegalArgumentException(String.join("\n", said));
        }
        return multi;
    }

    /** {@code tune}, where nothing is wrong with it.
     *
     * @throws IllegalArgumentException naming everything that is
     */
    public static Tune must(Tune tune) {
        List<String> said = of(tune);
        if (!said.isEmpty()) {
            throw new IllegalArgumentException(String.join("\n", said));
        }
        return tune;
    }
}
