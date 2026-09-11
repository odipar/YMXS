package org.ymxs;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.ymxs.YMXS.Effect;
import org.ymxs.YMXS.Multi;
import org.ymxs.YMXS.Register;
import org.ymxs.YMXS.Retune;
import org.ymxs.YMXS.Row;
import org.ymxs.YMXS.SetRegister;
import org.ymxs.YMXS.Source;
import org.ymxs.YMXS.Start;
import org.ymxs.YMXS.Stop;
import org.ymxs.YMXS.Table;
import org.ymxs.YMXS.Target;
import org.ymxs.YMXS.Timer;
import org.ymxs.YMXS.Tune;

/**
 * The rules a structure must satisfy for a player to play it, read off the
 * structure rather than stored in it.
 *
 * <p>Every function reports every fault rather than throwing at the first,
 * so one call reports every fault a writer must correct. {@link #must} is
 * the inverse, for a caller that stops at the first.
 *
 * <p>A structure is bound by the two chips and the music: what fits a
 * register, what a timer counts, and that an effect hands a source's row
 * to a target of that same shape. What a form can write belongs to that
 * form, and no limit of one appears here.
 *
 * <p>{@link #writing} is the other half: the rules of SPEC.md 6 read
 * across rows rather than within one, and a tune that breaks one of them
 * plays, but not as written, rather than failing to play.
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
                said.add(one.getKey() + " is 0 to " + most + ", and this row sets it to "
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

    /** What is wrong with one row's operation on one effect, or an empty
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
     *  are read against the target that runs it, so this reads only what
     *  is decidable without one. */
    public static List<String> of(Source source) {
        List<String> said = new ArrayList<>(table(Tunes.table(source), "the source"));
        List<Integer> values = Tunes.values(source);
        for (int at = 0; at < values.size(); at++) {
            if (values.get(at) < 0) {
                said.add("row " + at + " is " + values.get(at)
                        + ", and a register is 0 upward");
            }
        }
        return said;
    }

    /** What a start must satisfy: the target reads the row shape the
     *  source writes, and the source's values fit that register. */
    private static List<String> runs(Start start) {
        List<String> said = new ArrayList<>(of(start.source()));
        if (Tunes.columns(start.target()) != Tunes.columns(start.source())) {
            said.add("a source of " + Tunes.columns(start.source()) + " values a row on "
                    + Tunes.name(start.target()) + ", which reads "
                    + Tunes.columns(start.target()));
            return said;
        }
        int most = Tunes.most(start.target());
        List<Integer> values = Tunes.values(start.source());
        for (int at = 0; at < values.size(); at++) {
            if (values.get(at) > most) {
                said.add("a source on " + Tunes.name(start.target()) + " whose row " + at
                        + " is " + values.get(at) + ", and the target is 0 to " + most);
            }
        }
        return said;
    }

    private static List<String> count(int count) {
        if (count < 0 || count > Chip.MOST_COUNT) {
            return List.of("a count of " + count + ": a timer's data register"
                    + " is a byte, 0 to " + Chip.MOST_COUNT + ", and 0 counts "
                    + Chip.ZERO_COUNTS);
        }
        return List.of();
    }

    private static List<String> table(Table<?> table, String what) {
        List<String> said = new ArrayList<>();
        if (table.rows().isEmpty()) {
            said.add(what + " has no rows: a clock reads one");
        }
        if (table.repeat().isPresent() && (table.repeat().getAsInt() < 0
                || table.repeat().getAsInt() >= table.rows().size())) {
            said.add(what + " has " + table.rows().size() + " rows and repeats to row "
                    + table.repeat().getAsInt());
        }
        return said;
    }

    /**
     * The rules of SPEC.md 6, read across the tune's rows. A tune that
     * breaks one of them plays, but not as written.
     *
     * <p>Three of the five are read here. Rule 2 leaves the order of two
     * timers writing one register to the writer, and is not checked. Rule
     * 4 requires a start to set the timer's reset where the timer is
     * stopped, and a stopped timer begins a whole period with the value or
     * without it, so either value is correct and it is not checked either.
     *
     * <p>A source that plays once has a start in the rows and no end, so
     * its duration is reckoned from its rate ({@link Chip#frames}). Every
     * reading resting on that reckoning is reported as such.
     */
    public static List<String> writing(Tune tune) {
        return new Writing(tune).run();
    }

    /** What one timer runs, and the row its source ends on. */
    private record Running(Target target, Source source, int until) { }

    /** A walk over a tune's rows, with what each timer runs at every
     *  row. */
    private static final class Writing {

        private final Tune tune;
        private final List<String> said = new ArrayList<>();
        private final Map<Timer, Running> running = new EnumMap<>(Timer.class);
        private final Map<Timer, Source> lastSource = new EnumMap<>(Timer.class);
        private final Map<Timer, Target> lastTarget = new EnumMap<>(Timer.class);

        Writing(Tune tune) {
            this.tune = tune;
        }

        List<String> run() {
            List<Row> rows = tune.table().rows();
            for (int at = 0; at < rows.size(); at++) {
                Map<Timer, Effect> here = Tunes.effects(rows.get(at));
                for (Map.Entry<Timer, Effect> one : here.entrySet()) {
                    effect(at, one.getKey(), one.getValue());
                }
                registers(at, rows.get(at));
            }
            return said;
        }

        /** The effects come first, in the order a frame writes them. */
        private void effect(int at, Timer timer, Effect effect) {
            switch (effect) {
                case Start start -> {
                    place(at, timer, start);
                    running.put(timer, new Running(start.target(), start.source(),
                            until(at, start)));
                    lastSource.put(timer, start.source());
                    lastTarget.put(timer, start.target());
                }
                case Retune ignored -> {
                    Running runs = runs(timer, at);
                    if (runs == null) {
                        say(at, timer, "retunes an effect that is idle: a rate written"
                                + " to a timer with no source on it starts that timer"
                                + " with no source to run", reckoned(timer, at));
                    }
                }
                case Stop ignored -> running.remove(timer);
            }
        }

        /** Rule 3: a start sets the place's reset, unless the source it
         *  starts has the row count of the one this effect last ran on the
         *  same target. */
        private void place(int at, Timer timer, Start start) {
            if (start.placeReset()) {
                return;
            }
            Source before = lastSource.get(timer);
            if (before == null) {
                say(at, timer, "starts a source without the place's reset, and this timer"
                        + " has run none: the place is where the player left it", false);
                return;
            }
            Target last = lastTarget.get(timer);
            if (last == null || !start.target().equals(last)) {
                say(at, timer, "starts a source on " + Tunes.name(start.target())
                        + " without the place's reset, and this timer last ran on "
                        + (last == null ? "no target" : Tunes.name(last)), false);
                return;
            }
            int now = Tunes.size(Tunes.table(start.source()));
            int then = Tunes.size(Tunes.table(before));
            if (now != then) {
                say(at, timer, "starts a source of " + now + " rows without the place's"
                        + " reset, and the one before it had " + then, false);
            }
        }

        /** Rule 1: while an effect runs on a register, a row does not set
         *  that register. */
        private void registers(int at, Row row) {
            for (Map.Entry<Timer, Running> one : running.entrySet()) {
                Running runs = one.getValue();
                if (at >= runs.until()) {
                    continue;
                }
                Register register = written(runs.target());
                if (register == Register.R13 || !row.registers().containsKey(register)) {
                    continue;
                }
                say(at, one.getKey(), "runs on " + register + ", and this row sets it",
                        runs.until() != Integer.MAX_VALUE);
            }
        }

        /** What this timer runs at this row, or null where it is idle. */
        private @Nullable Running runs(Timer timer, int at) {
            Running runs = running.get(timer);
            return runs != null && at < runs.until() ? runs : null;
        }

        /** Whether a reading of this timer at this row rests on the
         *  reckoning of a source that plays once. */
        private boolean reckoned(Timer timer, int at) {
            Running runs = running.get(timer);
            return runs != null && runs.until() != Integer.MAX_VALUE && at >= runs.until();
        }

        /** The row a start's source ends on, or no row where it
         *  repeats. */
        private int until(int at, Start start) {
            if (Tunes.table(start.source()).repeat().isPresent()) {
                return Integer.MAX_VALUE;
            }
            return at + Chip.frames(Tunes.size(Tunes.table(start.source())),
                    start.prescaler(), start.count(), tune.rate());
        }

        private Register written(Target target) {
            return switch (target) {
                case SetRegister set -> set.register();
            };
        }

        private void say(int at, Timer timer, String what, boolean reckoned) {
            said.add("row " + at + ": Timer " + timer + " " + what
                    + (reckoned ? ", which rests on how long a source that plays once runs,"
                            + " reckoned from its rate" : ""));
        }
    }

    /** {@code multi} itself, where the check finds no fault.
     *
     * @throws IllegalArgumentException reporting everything that is
     */
    public static Multi must(Multi multi) {
        List<String> said = of(multi);
        if (!said.isEmpty()) {
            throw new IllegalArgumentException(String.join("\n", said));
        }
        return multi;
    }

    /** {@code tune} itself, where the check finds no fault.
     *
     * @throws IllegalArgumentException reporting everything that is
     */
    public static Tune must(Tune tune) {
        List<String> said = of(tune);
        if (!said.isEmpty()) {
            throw new IllegalArgumentException(String.join("\n", said));
        }
        return tune;
    }
}
