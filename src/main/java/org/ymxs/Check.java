package org.ymxs;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.ymxs.YMXS.Effect;
import org.ymxs.YMXS.Identity;
import org.ymxs.YMXS.Mapping;
import org.ymxs.YMXS.Multi;
import org.ymxs.YMXS.Part;
import org.ymxs.YMXS.Prescaler;
import org.ymxs.YMXS.Register;
import org.ymxs.YMXS.Remap;
import org.ymxs.YMXS.Retune;
import org.ymxs.YMXS.Row;
import org.ymxs.YMXS.Source;
import org.ymxs.YMXS.Start;
import org.ymxs.YMXS.Stop;
import org.ymxs.YMXS.Table;
import org.ymxs.YMXS.Target;
import org.ymxs.YMXS.Timer;
import org.ymxs.YMXS.Transformation;
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
 * register and what a timer counts. The type of a start pairs a source
 * with a target of its width (SPEC.md 1.1), and a check leaves the width
 * to the type. What a form can write belongs to that form, and no limit
 * of one appears here.
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

    /** What is wrong with {@code tune}, or an empty list, in the order of
     *  SPEC.md 1.12: the rate, the table, the transformations, then the
     *  rows. The lines of a transformation are prefixed with its number
     *  and its name, the name through {@link #named}. */
    public static List<String> of(Tune tune) {
        List<String> said = new ArrayList<>();
        if (tune.rate() < 1) {
            said.add("a rate of " + tune.rate() + ": a player is called at least once a"
                    + " second");
        }
        said.addAll(table(tune.table(), "the tune"));
        List<Transformation> transformations = Tunes.transformations(tune);
        for (int at = 0; at < transformations.size(); at++) {
            for (String one : of(transformations.get(at))) {
                said.add("transformation " + (at + 1) + ", "
                        + named(transformations.get(at).name()) + ": " + one);
            }
        }
        // a remap is read against every start on its timer, at any row
        List<Row> rows = tune.table().rows();
        Map<Timer, List<Started>> starts = new EnumMap<>(Timer.class);
        for (int at = 0; at < rows.size(); at++) {
            for (Map.Entry<Timer, Effect> one : rows.get(at).effects().entrySet()) {
                if (one.getValue() instanceof Start start) {
                    starts.computeIfAbsent(one.getKey(), timer -> new ArrayList<>())
                            .add(new Started(at, start));
                }
            }
        }
        for (int at = 0; at < rows.size(); at++) {
            for (String one : of(starts, rows.get(at))) {
                said.add("row " + at + ": " + one);
            }
        }
        return said;
    }

    /** A start and the row it is at. */
    private record Started(int row, Start start) { }

    /** What is wrong with one row, or an empty list: its registers, then
     *  its operation on each timer, {@code starts} the starts of the tune
     *  on each timer in row order. */
    private static List<String> of(Map<Timer, List<Started>> starts, Row row) {
        List<String> said = new ArrayList<>();
        for (Map.Entry<Register, Integer> one : Tunes.registers(row).entrySet()) {
            int most = Chip.most(one.getKey());
            if (one.getValue() < 0 || one.getValue() > most) {
                said.add(one.getKey() + " is 0 to " + most + ", and this row sets it to "
                        + one.getValue());
            }
        }
        for (Map.Entry<Timer, Effect> one : Tunes.effects(row).entrySet()) {
            for (String wrong : of(starts.getOrDefault(one.getKey(), List.of()),
                    one.getValue())) {
                said.add("Timer " + one.getKey() + ": " + wrong);
            }
        }
        return said;
    }

    /** What is wrong with one row's operation on the effect of a timer, or
     *  an empty list. A remap is read against {@code starts}, every start
     *  on its timer in row order. */
    private static List<String> of(List<Started> starts, Effect effect) {
        return switch (effect) {
            case Start start -> {
                List<String> said = new ArrayList<>(count(Tunes.count(start)));
                said.addAll(rate(Tunes.prescaler(start), Tunes.count(start)));
                said.addAll(runs(start));
                yield said;
            }
            case Retune retune -> {
                List<String> said = new ArrayList<>(count(retune.timing().count()));
                said.addAll(rate(retune.timing().prescaler(), retune.timing().count()));
                yield said;
            }
            case Stop ignored -> List.of();
            case Remap remap -> remaps(starts, remap.mapping());
        };
    }

    /** What is wrong with a transformation, or an empty list: the number
     *  of its entries where it is outside 1 to 256, then each entry outside
     *  a byte in key order. */
    public static List<String> of(Transformation transformation) {
        List<String> said = new ArrayList<>();
        List<Integer> entries = transformation.entries();
        if (!sized(transformation)) {
            said.add("the transformation has " + entries.size() + " entries, and a"
                    + " transformation has 1 to " + MOST_ENTRIES);
        }
        for (int key = 0; key < entries.size(); key++) {
            if (entries.get(key) < 0 || entries.get(key) > MOST_ENTRY) {
                said.add("entry " + key + " is " + entries.get(key) + ", and an entry is 0 to "
                        + MOST_ENTRY);
            }
        }
        return said;
    }

    /** The most entries of a transformation, so that a key is a byte
     *  (SPEC.md 3.6.1, 3.6.5). */
    private static final int MOST_ENTRIES = 256;

    /** The most an entry of a transformation is, the most of a byte
     *  (SPEC.md 3.6.1). */
    private static final int MOST_ENTRY = 255;

    /** Whether a transformation has 1 to 256 entries, so that it has a
     *  last key, the number of its entries less one (SPEC.md 3.6.1). */
    private static boolean sized(Transformation transformation) {
        int entries = transformation.entries().size();
        return entries >= 1 && entries <= MOST_ENTRIES;
    }

    /** The last key of a transformation of 1 to 256 entries: its keys are
     *  0 to this (SPEC.md 3.6.1). */
    private static int lastKey(Transformation transformation) {
        return transformation.entries().size() - 1;
    }

    /** The line of value {@code value} of row {@code at} of a source on
     *  {@code target}, above the last key of the transformation it is
     *  read through. */
    private static String key(Target target, int at, int value,
                              Transformation transformation) {
        return "a source on " + Tunes.name(target) + " whose row " + at + " is " + value
                + ", and a key is 0 to " + lastKey(transformation);
    }

    /** What is wrong with a source, or an empty list. A source's values
     *  are read against the target that runs it, so this reads only what
     *  is decidable without one. */
    public static List<String> of(Source source) {
        List<String> said = new ArrayList<>(table(Tunes.rows(source), "the source"));
        List<List<Integer>> rows = Tunes.rows(source).rows();
        for (int at = 0; at < rows.size(); at++) {
            for (int value : rows.get(at)) {
                if (value < 0) {
                    said.add("row " + at + " is " + value
                            + ", and a register is 0 upward");
                }
            }
        }
        return said;
    }

    /** What a start must satisfy, in the order of SPEC.md 1.12: each
     *  value is within its register through the identity and a key through
     *  a transformation of 1 to 256 entries, the mapping has a part for
     *  each value or none, and the entry a key reaches is within the
     *  register. A transformation of another number of entries has its
     *  line under its number, and this skips a value through it. The
     *  source has the values a row its target reads, since the record of a
     *  start pairs the two by width (SPEC.md 1.1). */
    private static List<String> runs(Start start) {
        Target target = Tunes.target(start);
        Source source = Tunes.source(start);
        List<String> said = new ArrayList<>(of(source));
        Mapping mapping = Tunes.mapping(start);
        if (!parted(mapping, target)) {
            said.add(parts(mapping, target));
            return said;
        }
        List<Integer> mosts = Tunes.mosts(target);
        List<List<Integer>> rows = Tunes.rows(source).rows();
        List<Part> through = Tunes.parts(mapping, Tunes.columns(target));
        for (int at = 0; at < rows.size(); at++) {
            List<Integer> row = rows.get(at);
            for (int value = 0; value < row.size(); value++) {
                if (through.get(value) instanceof Transformation transformation) {
                    if (sized(transformation) && row.get(value) > lastKey(transformation)) {
                        said.add(key(target, at, row.get(value), transformation));
                    }
                } else if (row.get(value) > mosts.get(value)) {
                    said.add("a source on " + Tunes.name(target) + " whose row " + at
                            + " is " + row.get(value) + ", and the target is 0 to "
                            + mosts.get(value));
                }
            }
        }
        said.addAll(mapped(target, source, mapping, false));
        return said;
    }

    /** Whether a mapping has a part for each value the target reads, or
     *  none (SPEC.md 3.6.3). */
    private static boolean parted(Mapping mapping, Target target) {
        return mapping.parts().isEmpty()
                || mapping.parts().size() == Tunes.columns(target);
    }

    /** The line of a mapping of another number of parts than the values
     *  its target reads. */
    private static String parts(Mapping mapping, Target target) {
        return "a mapping of " + mapping.parts().size() + " parts on " + Tunes.name(target)
                + ", which reads " + Tunes.columns(target);
    }

    /**
     * The values of {@code source} written through {@code mapping} above
     * the most of their register of {@code target}, in row order and
     * within a row in value order. A value through a transformation of 1
     * to 256 entries is read against its last key; where the value is a
     * key, its entry is compared with the register's most where that
     * entry is 0 to 255, and a value through a transformation of another
     * number of entries is skipped (SPEC.md 1.12). Where {@code remap} is
     * set, this reads a value through the identity too, and reports a
     * value above the last key at its place in that order; a start reports
     * both in the pass before the number of parts.
     */
    private static List<String> mapped(Target target, Source source, Mapping mapping,
                                       boolean remap) {
        List<String> said = new ArrayList<>();
        List<Integer> mosts = Tunes.mosts(target);
        List<Part> through = Tunes.parts(mapping, Tunes.columns(target));
        List<List<Integer>> rows = Tunes.rows(source).rows();
        for (int at = 0; at < rows.size(); at++) {
            List<Integer> row = rows.get(at);
            for (int value = 0; value < row.size(); value++) {
                int written;
                if (through.get(value) instanceof Transformation transformation) {
                    if (!sized(transformation) || row.get(value) < 0) {
                        continue;
                    }
                    if (row.get(value) > lastKey(transformation)) {
                        if (remap) {
                            said.add(key(target, at, row.get(value), transformation));
                        }
                        continue;
                    }
                    written = Tunes.written(transformation, row.get(value));
                    if (written < 0 || written > MOST_ENTRY) {
                        continue;
                    }
                } else if (remap) {
                    written = row.get(value);
                } else {
                    continue;
                }
                if (written > mosts.get(value)) {
                    said.add("value " + value + " of row " + at + " maps to " + written
                            + " on " + Tunes.name(target) + ", and the target is 0 to "
                            + mosts.get(value));
                }
            }
        }
        return said;
    }

    /**
     * What a remap must satisfy against {@code starts}, every start on
     * its timer, before it or after it (SPEC.md 1.11, 1.12 item 2.4.2.4,
     * 3.6.5): for each target in the row order of the first start that
     * connects it, the number of parts; then for each target and source
     * in the row order of the first start that connects the two, prefixed
     * {@code start of row S: }, S the row of that start, the values above
     * the last key of a transformation of 1 to 256 entries and the values
     * written above the register's most, in row order.
     */
    private static List<String> remaps(List<Started> starts, Mapping mapping) {
        Set<Target> targets = new LinkedHashSet<>();
        Map<Connection, Integer> connected = new LinkedHashMap<>();
        for (Started one : starts) {
            Target target = Tunes.target(one.start());
            targets.add(target);
            connected.putIfAbsent(new Connection(target, Tunes.source(one.start())), one.row());
        }
        List<String> said = new ArrayList<>();
        for (Target target : targets) {
            if (!parted(mapping, target)) {
                said.add(parts(mapping, target));
            }
        }
        for (Map.Entry<Connection, Integer> two : connected.entrySet()) {
            Target target = two.getKey().target();
            Source source = two.getKey().source();
            if (parted(mapping, target)) {
                for (String one : mapped(target, source, mapping, true)) {
                    said.add("start of row " + two.getValue() + ": " + one);
                }
            }
        }
        return said;
    }

    /** A target and the source a start connects to it. */
    private record Connection(Target target, Source source) { }

    /**
     * What is wrong with the sources and the transformations a form
     * declares beside the tune its rows make, or an empty list: a line for
     * each source no row starts, in the declared order, then one for each
     * transformation no row maps through, each name through
     * {@link #named}.
     *
     * <p>A form numbers its sources and its transformations and names
     * them, where the structure reaches a source through the row that
     * starts it ({@link Tunes#sources}) and a transformation through the
     * mapping that uses it ({@link Tunes#transformations}). So a form may
     * declare one no row uses, which is dropped where the form is read.
     *
     * <p>A name reaches a reader of the form and no more: an effect
     * names its source by the number of the table it is in, in JSON
     * and in CSV alike, so two sources under one name are told apart by
     * those numbers and are no fault, and the same for two
     * transformations.
     */
    public static List<String> declared(List<Source> declared,
                                        List<Transformation> transformations, Tune tune) {
        List<String> said = new ArrayList<>();
        List<Source> run = Tunes.sources(tune);
        for (int at = 0; at < declared.size(); at++) {
            if (!run.contains(declared.get(at))) {
                said.add("source " + (at + 1) + ", " + named(Tunes.name(declared.get(at)))
                        + ", is started by no row, and a source a tune does not run is"
                        + " dropped where this form is read");
            }
        }
        List<Transformation> used = Tunes.transformations(tune);
        for (int at = 0; at < transformations.size(); at++) {
            if (!used.contains(transformations.get(at))) {
                said.add("transformation " + (at + 1) + ", "
                        + named(transformations.get(at).name()) + ", is used by no row, and"
                        + " a transformation a tune does not map through is dropped where"
                        + " this form is read");
            }
        }
        return said;
    }

    /** The name of a source or of a transformation, or the text of the
     *  CSV emitter's line, in a reported line: each line feed as the two
     *  characters {@code \n} and each carriage return as {@code \r}, so a
     *  line with such a text in it is one line (SPEC.md 1.12, json.md 8.1,
     *  csv.md 5.1, 5.4). The check, the readers of both forms and the CSV
     *  emitter write every such text through this. */
    public static String named(String name) {
        return name.replace("\n", "\\n").replace("\r", "\\r");
    }

    /** {@link #declared(List, List, Tune)} of a form that declares no
     *  transformation. */
    public static List<String> declared(List<Source> declared, Tune tune) {
        return declared(declared, List.of(), tune);
    }

    /** What a rate must satisfy: a 68000 services the ticks it comes to.
     *  The count is read first, since a count outside the register makes
     *  no rate. */
    private static List<String> rate(Prescaler prescaler, int count) {
        if (count < 0 || count > Chip.MOST_COUNT) {
            return List.of();
        }
        int ticks = Chip.rate(prescaler, count);
        if (ticks > Chip.MOST_TICKS) {
            return List.of("a rate of " + ticks + " ticks a second: a 68000 at "
                    + Chip.CPU_CLOCK / 1000000 + " MHz enters an interrupt and leaves it"
                    + " in " + Chip.TICK_CYCLES + " cycles, so " + Chip.MOST_TICKS
                    + " a second is every cycle it has");
        }
        return List.of();
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

    /** What one timer runs: the start, the row its effect begins on, the
     *  row its source ends on, and the rows that set the target's register
     *  while it ran. */
    private static final class Running {

        private final Start start;
        private final int from;
        private int until;
        private final boolean begun;
        private int first = -1;
        private int last = -1;
        private int collisions;

        /** {@code begun} is set where the effect begins at a row reckoned
         *  for the end of the one before it, a deferred start's. */
        Running(Start start, int from, int until, boolean begun) {
            this.start = start;
            this.from = from;
            this.until = until;
            this.begun = begun;
        }

        Target target() {
            return Tunes.target(start);
        }

        /** Whether a reading of this run rests on the reckoning: its end is
         *  reckoned, or its first row is (6.3). */
        boolean reckoned() {
            return until != Integer.MAX_VALUE || begun;
        }

        void collides(int at) {
            if (first < 0) {
                first = at;
            }
            last = at;
            collisions++;
        }
    }

    /** What a reading rests on where the source is one that plays once:
     *  its end is reckoned from its rate rather than read off a row. */
    private static final String RECKONED = ", which rests on how long a source that plays"
            + " once runs, reckoned from its rate";

    /** A walk over a tune's rows, with what each timer runs at every
     *  row. */
    private static final class Writing {

        private final Tune tune;
        private final List<String> said = new ArrayList<>();
        private final Map<Timer, Running> running = new EnumMap<>(Timer.class);
        private final Map<Timer, Start> pending = new EnumMap<>(Timer.class);
        private final Set<Timer> started = EnumSet.noneOf(Timer.class);

        Writing(Tune tune) {
            this.tune = tune;
        }

        List<String> run() {
            List<Row> rows = tune.table().rows();
            for (int at = 0; at < rows.size(); at++) {
                begin(at);
                Map<Timer, Effect> here = Tunes.effects(rows.get(at));
                for (Map.Entry<Timer, Effect> one : here.entrySet()) {
                    effect(at, one.getKey(), one.getValue());
                }
                registers(at, rows.get(at));
            }
            for (Map.Entry<Timer, Running> one : running.entrySet()) {
                collided(one.getKey(), one.getValue());
            }
            wrap(rows);
            return said;
        }

        /** A pending start begins at the row the effect before it has run
         *  out by, ahead of the row's operations (6.3 step 1). */
        private void begin(int at) {
            for (Timer timer : Timer.values()) {
                Start start = pending.get(timer);
                if (start == null || runs(timer, at) != null) {
                    continue;
                }
                pending.remove(timer);
                starts(at, timer, start, true);
            }
        }

        /** The effects come first, in the order a frame writes them. */
        private void effect(int at, Timer timer, Effect effect) {
            switch (effect) {
                case Start start -> {
                    place(at, timer, start);
                    Running runs = runs(timer, at);
                    if (Tunes.deferred(start) && runs != null) {
                        // the effect plays its source once from here, and
                        // the start follows it
                        runs.until = Math.min(runs.until, at + frames(runs.start));
                        pending.put(timer, start);
                        return;
                    }
                    pending.remove(timer);
                    starts(at, timer, start, false);
                }
                case Retune ignored -> {
                    Running runs = runs(timer, at);
                    if (runs == null) {
                        say(at, timer, "retunes an effect that is idle: a rate written"
                                + " to a timer with no source on it starts that timer"
                                + " with no source to run", reckoned(timer, at));
                    }
                }
                case Remap ignored -> {
                    // the effect, its run and a pending start are as they
                    // were (6.3 step 2(d))
                    Running runs = runs(timer, at);
                    if (runs == null) {
                        say(at, timer, "remaps an effect that is idle: a mapping is set on"
                                + " a running effect, and this row leaves the timer as it"
                                + " is", reckoned(timer, at));
                    }
                }
                case Stop ignored -> {
                    pending.remove(timer);
                    Running runs = running.remove(timer);
                    // A stop of a source that has run out by the reckoning
                    // is a writer settling rule 4, and the row a tune
                    // repeats to stops every effect so that the wrap
                    // resumes from a known setting. Neither is a slip. A
                    // stop where this timer has run no source is one.
                    if (runs == null && !repeats(at)) {
                        say(at, timer, "stops an effect this timer has not started",
                                false);
                    }
                    if (runs != null) {
                        collided(timer, runs);
                    }
                }
            }
        }

        /** The effect of a start in the model from this row, replacing the
         *  one before it; {@code begun} where a pending start begins at a
         *  reckoned row. */
        private void starts(int at, Timer timer, Start start, boolean begun) {
            shared(at, timer, Tunes.target(start), begun);
            Running before = running.get(timer);
            if (before != null) {
                collided(timer, before);
            }
            running.put(timer, new Running(start, at, until(at, start), begun));
            started.add(timer);
        }

        /** Whether this is the row the tune repeats to, where a writer
         *  stops every effect so that the wrap resumes from a known
         *  setting. */
        private boolean repeats(int at) {
            return tune.table().repeat().isPresent()
                    && tune.table().repeat().getAsInt() == at;
        }

        /** Rule 2: where two timers write one register, the writer fixes
         *  the order. The rule is the writer's to settle, and the row
         *  where the second starts is where it arises. */
        private void shared(int at, Timer timer, Target target, boolean begun) {
            List<Register> registers = written(target);
            for (Map.Entry<Timer, Running> one : running.entrySet()) {
                if (one.getKey() == timer || at >= one.getValue().until) {
                    continue;
                }
                Register register = registers.stream()
                        .filter(written(one.getValue().target())::contains)
                        .findFirst().orElse(null);
                if (register != null) {
                    say(at, timer, "starts on " + register + ", where Timer "
                            + one.getKey() + " runs: rule 2 leaves the order of two"
                            + " timers writing one register to the writer",
                            one.getValue().reckoned() || begun);
                }
            }
        }

        /** Rule 1 at the wrap: an effect running when the last row has
         *  played runs on through the row the tune repeats to, which no
         *  row of the next pass started. */
        private void wrap(List<Row> rows) {
            if (tune.table().repeat().isEmpty()) {
                return;
            }
            int to = tune.table().repeat().getAsInt();
            int last = rows.size() - 1;
            for (Map.Entry<Timer, Running> one : running.entrySet()) {
                // a remap at the repeat row leaves the effect running from
                // the pass before, as a row that leaves the timer alone does
                Effect there = rows.get(to).effects().get(one.getKey());
                if (last >= one.getValue().until
                        || there != null && !(there instanceof Remap)) {
                    continue;
                }
                said.add("the tune repeats to row " + to + ", and Timer " + one.getKey()
                        + " runs on " + written(one.getValue().target()).get(0) + " when its"
                        + " last row has played: the wrap resumes with the timer running from"
                        + " the pass before"
                        + (one.getValue().reckoned() ? RECKONED : ""));
            }
        }

        /** One run of an effect, with the rows that set its register
         *  reported as one line. */
        private void collided(Timer timer, Running runs) {
            if (runs.collisions == 0) {
                return;
            }
            Register register = written(runs.target()).get(0);
            if (runs.collisions == 1) {
                say(runs.first, timer, "runs on " + register + ", and this row sets it",
                        runs.reckoned());
                runs.collisions = 0;
                return;
            }
            said.add("rows " + runs.first + " to " + runs.last + ": Timer " + timer
                    + " runs on " + register + " from row " + runs.from + ", and "
                    + runs.collisions + " of them set it"
                    + (runs.reckoned() ? RECKONED : ""));
            runs.collisions = 0;
        }

        /** Rule 3: a start on a timer that has run none sets the place's
         *  reset. After a start, the place keeps its number modulo the row
         *  count of the source a start connects (3.4.3). */
        private void place(int at, Timer timer, Start start) {
            if (Tunes.placeReset(start) || started.contains(timer)) {
                return;
            }
            say(at, timer, "starts a source without the place's reset, and this timer"
                    + " has run none: the place is where the player left it", false);
        }

        /** Rule 1: while an effect runs on a register, a row does not set
         *  that register. The rows of one run are reported as one line
         *  ({@link #collided}), since a run of a hundred rows is one
         *  fault and not a hundred. */
        private void registers(int at, Row row) {
            for (Map.Entry<Timer, Running> one : running.entrySet()) {
                Running runs = one.getValue();
                if (at >= runs.until) {
                    continue;
                }
                if (written(runs.target()).stream().noneMatch(written ->
                        written != Register.R13 && row.registers().containsKey(written))) {
                    continue;
                }
                runs.collides(at);
            }
        }

        /** What this timer runs at this row, or null where it is idle. */
        private @Nullable Running runs(Timer timer, int at) {
            Running runs = running.get(timer);
            return runs != null && at < runs.until ? runs : null;
        }

        /** Whether a reading of this timer at this row rests on the
         *  reckoning of a source that plays once. */
        private boolean reckoned(Timer timer, int at) {
            Running runs = running.get(timer);
            return runs != null && runs.until != Integer.MAX_VALUE && at >= runs.until;
        }

        /** The row a start's source ends on, or no row where it
         *  repeats. */
        private int until(int at, Start start) {
            if (Tunes.rows(Tunes.source(start)).repeat().isPresent()) {
                return Integer.MAX_VALUE;
            }
            return at + frames(start);
        }

        /** The frames a start's source runs for, played once at its
         *  rate (6.4). */
        private int frames(Start start) {
            return Chip.frames(Tunes.size(Tunes.rows(Tunes.source(start))),
                    Tunes.prescaler(start), Tunes.count(start), tune.rate());
        }

        private List<Register> written(Target target) {
            return Tunes.registers(target);
        }

        private void say(int at, Timer timer, String what, boolean reckoned) {
            said.add("row " + at + ": Timer " + timer + " " + what
                    + (reckoned ? RECKONED : ""));
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
