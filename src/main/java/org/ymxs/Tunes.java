package org.ymxs;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import org.ymxs.YMXS.Effect;
import org.ymxs.YMXS.Multi;
import org.ymxs.YMXS.Prescaler;
import org.ymxs.YMXS.Register;
import org.ymxs.YMXS.Retune;
import org.ymxs.YMXS.Row;
import org.ymxs.YMXS.SetRegister;
import org.ymxs.YMXS.Single;
import org.ymxs.YMXS.Source;
import org.ymxs.YMXS.Start;
import org.ymxs.YMXS.Stop;
import org.ymxs.YMXS.Table;
import org.ymxs.YMXS.Target;
import org.ymxs.YMXS.Timer;
import org.ymxs.YMXS.Tune;

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

    /** An empty row. A player advancing over it writes no register and
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
                if (effect instanceof Start start && !out.contains(start.source())) {
                    out.add(start.source());
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
    public static Target setting(Register register) {
        return new SetRegister(register);
    }

    /** The target numbered {@code number}.
     *
     * @throws IllegalArgumentException where this version defines none
     */
    public static Target target(int number) {
        return new SetRegister(Chip.register(number));
    }

    /** The number of this target, within the 0 to 127 the format
     *  reserves. */
    public static int number(Target target) {
        return switch (target) {
            case SetRegister set -> Chip.number(set.register());
        };
    }

    /** The name of this target: {@code setR0} to {@code setR13}. */
    public static String name(Target target) {
        return switch (target) {
            case SetRegister set -> "set" + set.register();
        };
    }

    /** The values one row of a source has for this target. */
    public static int columns(Target target) {
        return switch (target) {
            case SetRegister ignored -> 1;
        };
    }

    /** The largest value that fits one of those values. A source run by
     *  this target stays within it. */
    public static int most(Target target) {
        return switch (target) {
            case SetRegister set -> Chip.most(set.register());
        };
    }

    // ----------------------------------------------------------- a source

    /** The source name. It appears in the tools' reports, and in no part
     *  of what a player reads. */
    public static String name(Source source) {
        return switch (source) {
            case Single single -> single.name();
        };
    }

    /** The rows, and the row they repeat to. */
    public static Table<Integer> table(Source source) {
        return switch (source) {
            case Single single -> single.table();
        };
    }

    /** The values, one a row. */
    public static List<Integer> values(Source source) {
        return table(source).rows();
    }

    /** The values in one row. */
    public static int columns(Source source) {
        return switch (source) {
            case Single ignored -> 1;
        };
    }

    /** A source of one value a row that repeats to {@code repeat}. */
    public static Source repeating(String name, List<Integer> values, int repeat) {
        return new Single(name, repeating(values, repeat));
    }

    /** A source of one value a row that plays once and stops its timer. */
    public static Source once(String name, List<Integer> values) {
        return new Single(name, once(values));
    }

    // ----------------------------------------------------------- an effect

    /** The timer's reset: the timer stops, loads the count and starts, so
     *  it begins a whole period at that count. */
    public static boolean timerReset(Effect effect) {
        return switch (effect) {
            case Start start -> start.timerReset();
            case Retune retune -> retune.timerReset();
            case Stop ignored -> false;
        };
    }

    /** The place's reset: the next tick reads the source's first row. */
    public static boolean placeReset(Effect effect) {
        return switch (effect) {
            case Start start -> start.placeReset();
            case Retune retune -> retune.placeReset();
            case Stop ignored -> false;
        };
    }

    /** A struck note: the source from its first row, the timer from a
     *  whole period. */
    public static Start struck(Target target, Source source, Prescaler prescaler, int count) {
        return new Start(target, source, prescaler, count, true, true);
    }

    /** A bend: the count changes and both resets stay clear. */
    public static Retune bend(Prescaler prescaler, int count) {
        return new Retune(prescaler, count, false, false);
    }
}
