package org.ymxs;

import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

/**
 * What a tune holds. These declarations are the specification of that, and
 * doc/SPEC.md states what a player or an emulator does with them on an
 * Atari ST's YM2149 and MC68901.
 *
 * <p>Nothing here holds a method of its own beyond the accessors a record
 * gives. What is read off a structure is read by a function outside it:
 * {@link Chip} for what the two chips give, {@link Tunes} for what a
 * structure holds, and {@link Check} for what a structure has to satisfy.
 * Each reads by pattern matching, so a shape added to a sealed interface
 * stops them compiling until they read it.
 *
 * <p>How a tune is written down is a form, and no form is the format.
 * doc/text.md is one; a player's own is another. Nothing here is arranged
 * for a form's benefit, and no limit here is a form's.
 */
public interface YMXS {

    /** Several tunes, which is what a host plays as a tune with subtunes.
     *  A multi of one tune is a tune on its own. */
    record Multi(List<Tune> tunes) { }

    /** One tune: what it is called, and its rows, one a frame at the rate
     *  it states. Its sources are the ones its rows start
     *  ({@link Tunes#sources}). */
    record Tune(String title, String composer, String writer, int rate,
                Table<Row> table) { }

    /** Rows, and the row they repeat to once the last row is done, or
     *  empty for a table that plays once. A tune's rows are one of these
     *  and so are a source's: the tune's advance one a frame and the
     *  source's one a tick, and that is the whole difference. */
    record Table<T>(List<T> rows, OptionalInt repeat) { }

    /** One row: the registers it sets, and what it states of the effect on
     *  each timer. A register absent from the one map is one the row does
     *  not write, and a timer absent from the other is one it leaves
     *  running as it runs. */
    record Row(Map<Register, Integer> registers, Map<Timer, Effect> effects) { }

    /** What a row states of the effect on one timer. An effect is a source
     *  connected to a target on one timer, at the rate a prescaler and a
     *  count give, and a row does one of three things to it. */
    sealed interface Effect permits Start, Retune, Stop { }

    /** The row runs {@code source} on {@code target} at that rate, from
     *  this row on. The target and the rate are stated whether or not they
     *  moved, since this says what the effect is and not which of its
     *  parts changed. */
    record Start(Target target, Source source, Prescaler prescaler, int count,
                 boolean timerReset, boolean placeReset) implements Effect { }

    /** The row leaves the effect running the source and the target it has,
     *  at the rate this states. A note that bends states a count that
     *  moved; a note struck again at the rate it has states the place's
     *  reset. */
    record Retune(Prescaler prescaler, int count, boolean timerReset,
                  boolean placeReset) implements Effect { }

    /** The row stops the effect: its timer stops, and the effect runs
     *  nothing until a later row starts a source on it. It states nothing
     *  else. */
    record Stop() implements Effect { }

    /** What a timer's tick calls with a source's row: a procedure that
     *  takes one row and writes it. A later version has targets reaching
     *  the MC68901's own registers, and targets taking a row of more than
     *  one value. */
    sealed interface Target permits SetRegister { }

    /** The targets this version has, {@code setR0} to {@code setR13},
     *  which write a source's row to one YM2149 register. */
    record SetRegister(Register register) implements Target { }

    /** A table a tick advances a row at a time, its target writing each
     *  row. A later version has sources of more than one value a row. */
    sealed interface Source permits Single { }

    /** The sources this version has: one value a row, which is the row
     *  every target of this version takes. */
    record Single(String name, Table<Integer> table) implements Source { }

    /** One of the fourteen YM2149 registers a row sets and a target
     *  writes. Two of the chip's sixteen are its I/O ports and are no
     *  tune's. */
    enum Register {
        R0, R1, R2, R3, R4, R5, R6, R7, R8, R9, R10, R11, R12, R13
    }

    /** One of the MC68901's four timers. A row states an effect against
     *  one, and the timer is the effect. */
    enum Timer { A, B, C, D }

    /** A timer's first divisor. Its rate is the clock divided by the
     *  prescaler times the count. */
    enum Prescaler { BY_4, BY_10, BY_16, BY_50, BY_64, BY_100, BY_200 }
}
