package org.ymxs;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.ymxs.YMXS.Connection;
import org.ymxs.YMXS.Effect;
import org.ymxs.YMXS.Identity;
import org.ymxs.YMXS.Mapping;
import org.ymxs.YMXS.Multi;
import org.ymxs.YMXS.OneConnection;
import org.ymxs.YMXS.OneTarget;
import org.ymxs.YMXS.Pair;
import org.ymxs.YMXS.Part;
import org.ymxs.YMXS.Prescaler;
import org.ymxs.YMXS.Register;
import org.ymxs.YMXS.Remap;
import org.ymxs.YMXS.Retune;
import org.ymxs.YMXS.Row;
import org.ymxs.YMXS.SetBuzzer;
import org.ymxs.YMXS.SetEnvelope;
import org.ymxs.YMXS.SetNoise;
import org.ymxs.YMXS.SetRegister;
import org.ymxs.YMXS.SetTone;
import org.ymxs.YMXS.SetVoice;
import org.ymxs.YMXS.Single;
import org.ymxs.YMXS.Source;
import org.ymxs.YMXS.Start;
import org.ymxs.YMXS.Stop;
import org.ymxs.YMXS.Table;
import org.ymxs.YMXS.Target;
import org.ymxs.YMXS.ThreeConnection;
import org.ymxs.YMXS.ThreeTarget;
import org.ymxs.YMXS.Timer;
import org.ymxs.YMXS.Timing;
import org.ymxs.YMXS.Transformation;
import org.ymxs.YMXS.Triple;
import org.ymxs.YMXS.Tune;
import org.ymxs.YMXS.TwoConnection;
import org.ymxs.YMXS.TwoTarget;
import org.ymxs.YMXS.Voice;

/**
 * What is read off a structure, read rather than stored. Every function
 * here is pure: it reads a structure and returns a value, leaving its
 * argument unchanged.
 *
 * <p>Each reads a sealed interface by pattern matching over every shape,
 * so a shape added to {@link YMXS} stops this compiling until it is read
 * here. An added shape is therefore a change to the specification rather
 * than a value that appears in a file.
 */
public final class Tunes {

    /** The stop, the same for every timer and every row. */
    public static final Stop STOP = new Stop();

    /** The part that writes each value as it is (SPEC.md 3.6.2). */
    public static final Identity IDENTITY = new Identity();

    /** The mapping of no parts, the identity in every part (SPEC.md
     *  3.6.3). */
    public static final Mapping NO_PARTS = new Mapping(List.of());

    /** An empty row. A player advancing over it leaves every register as it
     *  is and
     *  moves no timer. */
    public static final Row EMPTY = new Row(Map.of(), Map.of());

    private Tunes() {
    }

    // ------------------------------------------------------------ a table

    /** How many rows the table has. */
    public static int size(Table<?> table) {
        return table.rows().size();
    }

    /** A table that repeats to {@code repeat}. */
    public static <T> Table<T> repeating(List<T> rows, int repeat) {
        return new Table<>(rows, OptionalInt.of(repeat));
    }

    /** A table that plays once. */
    public static <T> Table<T> once(List<T> rows) {
        return new Table<>(rows, OptionalInt.empty());
    }

    /** A row that sets these registers and leaves every effect alone. */
    public static Row row(Map<Register, Integer> registers) {
        return new Row(registers, Map.of());
    }

    /**
     * The effects this row sets, in the order the timers are declared.
     *
     * <p>A row is built on a plain map, whose iteration order belongs to
     * the caller rather than the row. A form that writes one row twice
     * must write it identically both times, and reads the row through
     * this.
     */
    public static Map<Timer, Effect> effects(Row row) {
        Map<Timer, Effect> out = new LinkedHashMap<>();
        for (Timer timer : Timer.values()) {
            Effect effect = row.effects().get(timer);
            if (effect != null) {
                out.put(timer, effect);
            }
        }
        return out;
    }

    /** The registers this row sets, in the order they are declared. */
    public static Map<Register, Integer> registers(Row row) {
        Map<Register, Integer> out = new LinkedHashMap<>();
        for (Register register : Register.values()) {
            Integer value = row.registers().get(register);
            if (value != null) {
                out.put(register, value);
            }
        }
        return out;
    }

    /** Whether the row sets no register and leaves every effect alone. */
    public static boolean isEmpty(Row row) {
        return row.registers().isEmpty() && row.effects().isEmpty();
    }

    // ------------------------------------------------------------- a tune

    /** The rows, one a frame. */
    public static List<Row> rows(Tune tune) {
        return tune.table().rows();
    }

    /**
     * The sources this tune runs: the ones its rows start, in first-start
     * order. Only a source started by some row belongs to the tune, so
     * this is the complete list.
     */
    public static List<Source> sources(Tune tune) {
        List<Source> out = new ArrayList<>();
        for (Row row : rows(tune)) {
            for (Effect effect : effects(row).values()) {
                if (effect instanceof Start start
                        && !out.contains(source(start.connection()))) {
                    out.add(source(start.connection()));
                }
            }
        }
        return out;
    }

    /** The number a form writes for {@code source}, 1 upward.
     *
     * @throws IllegalArgumentException where no row of the tune starts it
     */
    public static int number(Tune tune, Source source) {
        int at = sources(tune).indexOf(source);
        if (at < 0) {
            throw new IllegalArgumentException("no row of this tune starts that source");
        }
        return at + 1;
    }

    /** Source {@code number}, 1 upward.
     *
     * @throws IllegalArgumentException where the tune runs no such source
     */
    public static Source source(Tune tune, int number) {
        List<Source> all = sources(tune);
        if (number < 1 || number > all.size()) {
            throw new IllegalArgumentException("no source " + number + ": the tune runs "
                    + all.size());
        }
        return all.get(number - 1);
    }

    /**
     * The transformations of this tune, in the order of SPEC.md 1.9: the
     * rows from row 0, within a row the timers A to D, and of a start or a
     * remap each part of its mapping from part 0, a transformation new to
     * the list appended. The identity is outside the list.
     */
    public static List<Transformation> transformations(Tune tune) {
        List<Transformation> out = new ArrayList<>();
        for (Row row : rows(tune)) {
            for (Effect effect : effects(row).values()) {
                Mapping mapping = mapping(effect);
                if (mapping == null) {
                    continue;
                }
                for (Part part : mapping.parts()) {
                    if (part instanceof Transformation transformation
                            && !out.contains(transformation)) {
                        out.add(transformation);
                    }
                }
            }
        }
        return out;
    }

    /** The number a form writes for {@code part}: 0 for the identity, and
     *  n for transformation n of {@code transformations}, the list of
     *  {@link #transformations}.
     *
     * @throws IllegalArgumentException where the list lacks the part
     */
    public static int number(List<Transformation> transformations, Part part) {
        return switch (part) {
            case Identity ignored -> 0;
            case Transformation transformation -> {
                int at = transformations.indexOf(transformation);
                if (at < 0) {
                    throw new IllegalArgumentException("no row of this tune maps through "
                            + transformation.name());
                }
                yield at + 1;
            }
        };
    }

    /** The timers this tune claims: every timer a row starts a source on.
     *  A player claims them before the first row, and a tune that leaves
     *  Timer C alone can be hosted from the operating system's 200 Hz
     *  clock. */
    public static Set<Timer> timers(Tune tune) {
        Set<Timer> claimed = EnumSet.noneOf(Timer.class);
        for (Row row : rows(tune)) {
            for (var one : effects(row).entrySet()) {
                if (one.getValue() instanceof Start) {
                    claimed.add(one.getKey());
                }
            }
        }
        return claimed;
    }

    // ------------------------------------------------------------ a multi

    /** A multi of one tune. */
    public static Multi multi(Tune tune) {
        return new Multi(List.of(tune));
    }

    /** Tune {@code number}, 1 upward, the number a host selects.
     *
     * @throws IllegalArgumentException where the multi has no such tune
     */
    public static Tune tune(Multi multi, int number) {
        if (number < 1 || number > multi.tunes().size()) {
            throw new IllegalArgumentException("no tune " + number + ": the multi has "
                    + multi.tunes().size());
        }
        return multi.tunes().get(number - 1);
    }

    // ----------------------------------------------------------- a target

    /** The target that writes {@code register}. */
    public static OneTarget setting(Register register) {
        return new SetRegister(register);
    }

    /** The registers this target writes, value i of a row to register i.
     *  The list is the target's width (columns). */
    public static List<Register> registers(Target target) {
        return switch (target) {
            case SetRegister set -> List.of(set.register());
            case SetTone tone -> List.of(Chip.fine(tone.voice()), Chip.coarse(tone.voice()));
            case SetNoise noise -> List.of(Register.R6, Chip.volume(noise.voice()));
            case SetEnvelope ignored -> List.of(Register.R11, Register.R12);
            case SetVoice voice -> List.of(Chip.fine(voice.voice()),
                    Chip.coarse(voice.voice()), Chip.volume(voice.voice()));
            case SetBuzzer ignored -> List.of(Register.R11, Register.R12, Register.R13);
        };
    }

    /** The target numbered {@code number}.
     *
     * @throws IllegalArgumentException where this version defines none
     */
    public static Target target(int number) {
        if (number >= 0 && number <= 13) {
            return new SetRegister(Chip.register(number));
        }
        return switch (number) {
            case 14, 15, 16 -> new SetTone(voice(number - 14));
            case 17, 18, 19 -> new SetVoice(voice(number - 17));
            case 20 -> new SetEnvelope();
            case 21 -> new SetBuzzer();
            case 22, 23, 24 -> new SetNoise(voice(number - 22));
            default -> throw new IllegalArgumentException("no target " + number
                    + ": a tune reaches 0 to 24");
        };
    }

    /** The target of one register numbered {@code number}, 0 to 13, which
     *  a form of version 3 reads.
     *
     * @throws IllegalArgumentException where this version defines none of
     *     one register
     */
    public static OneTarget oneTarget(int number) {
        return new SetRegister(Chip.register(number));
    }

    private static Voice voice(int at) {
        return Voice.values()[at];
    }

    /** The number of this target, within the 0 to 127 the format
     *  reserves. */
    public static int number(Target target) {
        return switch (target) {
            case SetRegister set -> Chip.number(set.register());
            case SetTone tone -> 14 + tone.voice().ordinal();
            case SetVoice voice -> 17 + voice.voice().ordinal();
            case SetEnvelope ignored -> 20;
            case SetBuzzer ignored -> 21;
            case SetNoise noise -> 22 + noise.voice().ordinal();
        };
    }

    /** The name of this target: {@code setR0} to {@code setR13}, and
     *  {@code setToneA} to {@code setNoiseC} for the targets of several
     *  registers. */
    public static String name(Target target) {
        return switch (target) {
            case SetRegister set -> "set" + set.register();
            case SetTone tone -> "setTone" + tone.voice();
            case SetVoice voice -> "setVoice" + voice.voice();
            case SetEnvelope ignored -> "setEnvelope";
            case SetBuzzer ignored -> "setBuzzer";
            case SetNoise noise -> "setNoise" + noise.voice();
        };
    }

    /** The values one row of a source has for this target. */
    public static int columns(Target target) {
        return registers(target).size();
    }

    /** The largest value that fits value i of a row, register by
     *  register. A source run by this target stays within them. */
    public static List<Integer> mosts(Target target) {
        return registers(target).stream().map(Chip::most).toList();
    }

    // ----------------------------------------------------------- a source

    /** The source name. It appears in the tools' reports, and in no part
     *  of what a player reads. */
    public static String name(Source source) {
        return switch (source) {
            case Single single -> single.name();
            case Pair pair -> pair.name();
            case Triple triple -> triple.name();
        };
    }

    /** The rows, a value a register of the target that runs it, and the
     *  row they repeat to. A source of one value a row reads as a row of
     *  one. */
    public static Table<List<Integer>> rows(Source source) {
        return switch (source) {
            case Single single -> new Table<>(
                    single.table().rows().stream().map(List::of).toList(),
                    single.table().repeat());
            case Pair pair -> new Table<>(pair.table().rows().stream()
                    .map(row -> List.of(row.first(), row.second())).toList(),
                    pair.table().repeat());
            case Triple triple -> new Table<>(triple.table().rows().stream()
                    .map(row -> List.of(row.first(), row.second(), row.third())).toList(),
                    triple.table().repeat());
        };
    }

    /** The values of a source of one value a row.
     *
     * @throws IllegalArgumentException where the source has more a row
     */
    public static List<Integer> values(Source source) {
        if (source instanceof Single single) {
            return single.table().rows();
        }
        throw new IllegalArgumentException(name(source) + " has "
                + columns(source) + " values a row");
    }

    /** The values in one row. */
    public static int columns(Source source) {
        return switch (source) {
            case Single ignored -> 1;
            case Pair ignored -> 2;
            case Triple ignored -> 3;
        };
    }

    /** A source of one value a row that repeats to {@code repeat}. */
    public static Single repeating(String name, List<Integer> values, int repeat) {
        return new Single(name, repeating(values, repeat));
    }

    /** A source of one value a row that plays once and stops its timer. */
    public static Single once(String name, List<Integer> values) {
        return new Single(name, once(values));
    }

    // ----------------------------------------------------------- an effect

    /** The mapping of a start or a remap, or null for a retune and a
     *  stop, which have none. */
    public static @Nullable Mapping mapping(Effect effect) {
        return switch (effect) {
            case Start start -> start.mapping();
            case Remap remap -> remap.mapping();
            case Retune ignored -> null;
            case Stop ignored -> null;
        };
    }

    // ------------------------------------------------------- a connection

    /** The target of a connection of any width. */
    public static Target target(Connection connection) {
        return switch (connection) {
            case OneConnection one -> one.target();
            case TwoConnection two -> two.target();
            case ThreeConnection three -> three.target();
        };
    }

    /** The source of a connection of any width. */
    public static Source source(Connection connection) {
        return switch (connection) {
            case OneConnection one -> one.source();
            case TwoConnection two -> two.source();
            case ThreeConnection three -> three.source();
        };
    }

    /** The connection of {@code source} to {@code target}, the record of
     *  the width they share.
     *
     * @throws IllegalArgumentException where the source has other values
     *     a row than the target reads
     */
    private static Connection connection(Target target, Source source) {
        if (target instanceof OneTarget one && source instanceof Single single) {
            return new OneConnection(one, single);
        }
        if (target instanceof TwoTarget two && source instanceof Pair pair) {
            return new TwoConnection(two, pair);
        }
        if (target instanceof ThreeTarget three && source instanceof Triple triple) {
            return new ThreeConnection(three, triple);
        }
        throw new IllegalArgumentException("a source of " + columns(source)
                + " values a row on " + name(target) + ", which reads "
                + columns(target));
    }

    // ---------------------------------------------------------- a mapping

    /** A mapping of these parts, part i for value i of a row. */
    public static Mapping mapping(Part... parts) {
        return new Mapping(List.of(parts));
    }

    /** The parts a tick writes the values of a row through, on a target
     *  of {@code width} values a row: part i for value i, and the identity
     *  for each value where the mapping has no parts (SPEC.md 3.6.3). */
    public static List<Part> parts(Mapping mapping, int width) {
        if (!mapping.parts().isEmpty()) {
            return mapping.parts();
        }
        List<Part> out = new ArrayList<>();
        for (int at = 0; at < width; at++) {
            out.add(IDENTITY);
        }
        return out;
    }

    /** Whether every part of the mapping is the identity, the mapping of
     *  no parts included: a form writes 0 for such a mapping. */
    public static boolean isIdentity(Mapping mapping) {
        return mapping.parts().stream().allMatch(part -> part instanceof Identity);
    }

    /** The value written for {@code value} through {@code part}: the value
     *  itself through the identity, and its entry through a
     *  transformation (SPEC.md 3.6.4), whose keys are 0 to the number of
     *  its entries less one, 255 at most (3.6.1).
     *
     * @throws IndexOutOfBoundsException where the value is outside the
     *     keys of the transformation
     */
    public static int written(Part part, int value) {
        return switch (part) {
            case Identity ignored -> value;
            case Transformation transformation -> transformation.entries().get(value);
        };
    }

    /** The start of {@code source} on {@code target} through
     *  {@code mapping}, its connection the record of the width they share.
     *  A reader of a form builds a start this way, where the two come off
     *  text; code that names the shapes builds the record.
     *
     * @throws IllegalArgumentException where the source has other values
     *     a row than the target reads
     */
    public static Start starting(Target target, Source source, Prescaler prescaler,
            int count, boolean timerReset, boolean placeReset, boolean deferred,
            Mapping mapping) {
        return new Start(connection(target, source),
                new Timing(prescaler, count, timerReset, placeReset), deferred, mapping);
    }

    /** A struck note: the source from its first row, the timer from a
     *  whole period, at the row, through the mapping of no parts. */
    public static Start struck(OneTarget target, Single source, Prescaler prescaler,
            int count) {
        return new Start(new OneConnection(target, source),
                new Timing(prescaler, count, true, true), false, NO_PARTS);
    }

    /** A bend: the count changes and both resets stay clear. */
    public static Retune bend(Prescaler prescaler, int count) {
        return new Retune(new Timing(prescaler, count, false, false));
    }
}
