package org.ymxs;

import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

/**
 * The tune data structure. These declarations are its specification, and
 * doc/SPEC.md defines what a player or an emulator does with them on an
 * Atari ST's YM2149 and MC68901.
 *
 * <p>The records here have no methods beyond their accessors. A structure
 * is read by a function outside it: {@link Chip} for the figures of the
 * two chips, {@link Tunes} for what is read off a structure, and
 * {@link Check} for the rules a structure must satisfy. Each reads by
 * pattern matching, so a shape added to a sealed interface stops them
 * compiling until they read it.
 *
 * <p>A form writes a tune down. doc/json.md defines one; a player's binary
 * layout is another. No part of this is arranged for a form, and every
 * limit here follows from the two chips.
 */
public interface YMXS {

    /** Several tunes, which a host plays as a tune with subtunes. A multi
     *  of one tune is a single tune. */
    record Multi(List<Tune> tunes) { }

    /** One tune: its title, and its rows, one a frame at the tune's rate.
     *  Its sources are the ones its rows start
     *  ({@link Tunes#sources}). */
    record Tune(String title, String composer, String writer, int rate,
                Table<Row> table) { }

    /** Rows, and the row they repeat to after the last row, or empty for
     *  a table that plays once. A tune's rows are one of these and so are
     *  a source's: the tune's advance one a frame, the source's one a
     *  tick, and that is the whole difference. */
    record Table<T>(List<T> rows, OptionalInt repeat) { }

    /** One row: the registers it sets, and its operation on the effect of
     *  each timer. A register absent from the first map is one the row
     *  does not write; a timer absent from the second is one the row
     *  leaves running. */
    record Row(Map<Register, Integer> registers, Map<Timer, Effect> effects) { }

    /** One row's operation on the effect of one timer. An effect is a
     *  source connected to a target on one timer, at the rate its
     *  prescaler and count come to, and a row performs one of three
     *  operations on it. */
    sealed interface Effect permits Start, Retune, Stop { }

    /** The row runs {@code source} on {@code target} at that rate, from
     *  this row on. Every start records the target and the rate, changed
     *  or not, since a start defines the effect rather than the parts of
     *  it that differ. */
    record Start(Target target, Source source, Prescaler prescaler, int count,
                 boolean timerReset, boolean placeReset) implements Effect { }

    /** The row leaves the effect on its source and target, at the rate in
     *  this record. A bend changes the count; a note struck again at the
     *  rate already running changes the place alone. */
    record Retune(Prescaler prescaler, int count, boolean timerReset,
                  boolean placeReset) implements Effect { }

    /** The row stops the effect: its timer stops, and the effect is idle
     *  until a later row starts a source on it. */
    record Stop() implements Effect { }

    /** What a timer's tick calls with a source's row: a procedure that
     *  reads one row and writes it. A later version defines targets
     *  reaching the MC68901 registers, and targets that read a row of
     *  more than one value. */
    sealed interface Target permits SetRegister { }

    /** The targets of this version, {@code setR0} to {@code setR13},
     *  which write a source's row to one YM2149 register. */
    record SetRegister(Register register) implements Target { }

    /** A table a tick advances a row at a time, its target writing each
     *  row. A later version defines sources of more than one value a
     *  row. */
    sealed interface Source permits Single { }

    /** The sources of this version: one value a row, the row shape every
     *  target of this version reads. */
    record Single(String name, Table<Integer> table) implements Source { }

    /** One of the fourteen YM2149 registers a row sets and a target
     *  writes. Two of the chip's sixteen are its I/O ports, outside a
     *  tune. */
    enum Register {
        R0, R1, R2, R3, R4, R5, R6, R7, R8, R9, R10, R11, R12, R13
    }

    /** One of the MC68901's four timers. A row sets an effect on one, and
     *  the timer is the effect. */
    enum Timer { A, B, C, D }

    /** A timer's first divisor. Its rate is the clock divided by the
     *  prescaler times the count. */
    enum Prescaler { BY_4, BY_10, BY_16, BY_50, BY_64, BY_100, BY_200 }
}
