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
 * <p>A form encodes a tune. doc/json.md defines one; a player's binary
 * layout is another. Each type here is independent of a form, and every
 * limit here follows from the two chips.
 */
public interface YMXS {

    /** Several tunes, which a host plays as a tune with subtunes. A multi
     *  of one tune is a single tune. */
    record Multi(List<Tune> tunes) { }

    /** One tune: its title, and its rows, one a frame at the tune's rate.
     *  Its sources are the ones its rows start
     *  ({@link Tunes#sources}), and its transformations the ones the
     *  mappings of its rows use. */
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
     *  source connected to a target on one timer through a mapping, at
     *  the rate its prescaler and count come to, and a row performs one
     *  of four operations on it. */
    sealed interface Effect permits Start, Retune, Stop, Remap { }

    /**
     * Starts the connection with its timing and mapping. A mapping with
     * no parts uses identity for every source value; transformation
     * lookups are optional.
     *
     * <p>With {@code deferred} set on a running effect, this start
     * remains pending until the current source's last row. It keeps its
     * mapping while pending, even if a {@link Remap} changes the
     * running effect (SPEC.md 3.5).
     */
    record Start(Connection connection, Timing timing, boolean deferred,
                 Mapping mapping) implements Effect { }

    /** The target a start runs its source on, and that source. A
     *  connection is a shape a width: each pairs a target with a source of
     *  the values a row that target reads, so a source that fits its
     *  target is a shape here rather than a rule a check reads. A reader
     *  that reads a connection of any width reads this interface; one that
     *  reads a width reads the record. */
    sealed interface Connection permits OneConnection, TwoConnection, ThreeConnection { }

    /** A connection of one value a row. */
    record OneConnection(OneTarget target, Single source) implements Connection { }

    /** A connection of two values a row. */
    record TwoConnection(TwoTarget target, Pair source) implements Connection { }

    /** A connection of three values a row. */
    record ThreeConnection(ThreeTarget target, Triple source) implements Connection { }

    /** The rate a row writes the timer, and the two resets it performs
     *  with it: the prescaler and the count are the rate (3.3), and a
     *  start and a retune each record one of these. */
    record Timing(Prescaler prescaler, int count, boolean timerReset,
                  boolean placeReset) { }

    /**
     * Changes timing and resets while preserving the source, target and
     * mapping. A bend changes the count; a retrigger can reset the
     * source place at the same rate.
     */
    record Retune(Timing timing) implements Effect { }

    /** The row stops the effect: its timer stops, and the effect is idle
     *  until a later row starts a source on it. */
    record Stop() implements Effect { }

    /**
     * Replaces the running effect's mapping for subsequent ticks. Its
     * source, target, timing and place are unchanged. A pending {@link
     * Start} retains its separate mapping; an idle timer stays idle.
     *
     * <p>Use successive volume transformations for a fade without
     * restarting the sample, or an empty mapping to restore identity.
     */
    record Remap(Mapping mapping) implements Effect { }

    /**
     * Writes mapped source values to YM2149 registers in target order.
     * The effect's mapping supplies either each source value unchanged
     * or an optional transformation lookup. Target and source widths
     * must match through their {@link Connection}.
     */
    sealed interface Target permits OneTarget, TwoTarget, ThreeTarget { }

    /** A target of one value a row. */
    sealed interface OneTarget extends Target permits SetRegister { }

    /** A target of two values a row, the first value to the first
     *  register it writes. */
    sealed interface TwoTarget extends Target permits SetTone, SetNoise, SetEnvelope { }

    /** A target of three values a row, in the order of the registers it
     *  writes. */
    sealed interface ThreeTarget extends Target permits SetVoice, SetBuzzer { }

    /**
     * Writes one mapped source value to a YM2149 register: {@code
     * setR0} to {@code setR13}.
     */
    record SetRegister(Register register) implements OneTarget { }

    /** The tone period of one voice, fine then coarse. */
    record SetTone(Voice voice) implements TwoTarget { }

    /** The noise period and the volume of one voice. R6 is one register
     *  for the three voices, so two of these running at once write one
     *  period and the later tick's write remains. */
    record SetNoise(Voice voice) implements TwoTarget { }

    /** The envelope period, fine then coarse. */
    record SetEnvelope() implements TwoTarget { }

    /**
     * Writes tone fine, tone coarse and volume for one voice. Identity
     * parts preserve pitch while an optional transformation on the
     * volume scales volume levels. Volume bit 4 selects the envelope
     * generator.
     */
    record SetVoice(Voice voice) implements ThreeTarget { }

    /** The envelope period and the shape, which every write restarts. */
    record SetBuzzer() implements ThreeTarget { }

    /**
     * Source rows read one per tick. Each value passes through its
     * mapping part before the target writes it: unchanged through
     * identity, or as a lookup key through a transformation. The row
     * width matches the target through its {@link Connection}.
     */
    sealed interface Source permits Single, Pair, Triple { }

    /** One value a row, the row a target of one register reads. */
    record Single(String name, Table<Integer> table) implements Source { }

    /** Two values a row, the row a target of two registers reads. */
    record Pair(String name, Table<Two> table) implements Source { }

    /** Three values a row, the row a target of three registers reads. */
    record Triple(String name, Table<Three> table) implements Source { }

    /** One row of a source of two values, a value a register of the
     *  target that runs it. */
    record Two(int first, int second) { }

    /** One row of a source of three values, a value a register of the
     *  target that runs it. */
    record Three(int first, int second, int third) { }

    /**
     * Parts in the order of source values and target registers. An
     * empty list means identity for every value. Otherwise, supply one
     * part per value: identity for an unchanged value or a
     * transformation for a lookup.
     *
     * <p>For {@link SetVoice}, identity on tone fine and coarse plus a
     * volume transformation changes volume alone (SPEC.md 3.6.3).
     */
    record Mapping(List<Part> parts) { }

    /**
     * Maps one source value: {@link Identity} leaves it unchanged;
     * {@link Transformation} selects a lookup entry.
     */
    sealed interface Part permits Identity, Transformation { }

    /**
     * Leaves a source value unchanged, without a lookup. A
     * transformation whose entries equal their keys remains a separate
     * part with a bounded key range.
     */
    record Identity() implements Part { }

    /**
     * An optional lookup table: source value k selects {@code
     * entries.get(k)} as the value written. The list has 1 to 256
     * entries, each 0 to 255; keys run from 0 to the entry count minus
     * one.
     *
     * <p>A table can scale sample levels without copying the source.
     * The mapping selects which source values use it. Names are for
     * display, reports and equality; equal transformations have equal
     * names and entries in number and order (SPEC.md 3.6).
     */
    record Transformation(String name, List<Integer> entries) implements Part { }

    /** One of the fourteen YM2149 registers a row sets and a target
     *  writes. Two of the chip's sixteen are its I/O ports, outside a
     *  tune. */
    enum Register {
        R0, R1, R2, R3, R4, R5, R6, R7, R8, R9, R10, R11, R12, R13
    }

    /** One of the three voices of the YM2149, which a target names where
     *  it writes the registers of one. */
    enum Voice { A, B, C }

    /** One of the MC68901's four timers. A row sets an effect on one, and
     *  the timer is the effect. */
    enum Timer { A, B, C, D }

    /** A timer's first divisor. Its rate is the clock divided by the
     *  prescaler times the count. */
    enum Prescaler { BY_4, BY_10, BY_16, BY_50, BY_64, BY_100, BY_200 }
}
