package org.ymxs;

import java.util.List;

/**
 * A table a tick advances a row at a time, its target writing each row
 * (SPEC.md 3.2). Where a tune's table gives a row a frame, a source gives
 * one a tick, so a source sounds at the rate its effect's timer runs at.
 *
 * <p>The shapes a tune uses are shapes, and this format names no kinds.
 * One row repeating writes its value at the timer's rate, which on R13
 * restarts the envelope: a sync buzzer. Two rows repeating to row 0 on a
 * volume register are a square wave: a SID voice. Many rows played once
 * are a recording: a digidrum. A source holds its own values, so two
 * square waves at two levels are two sources.
 *
 * <p>This version has one kind, {@link Single}: one value a row, which is
 * the row every {@link Target} of this version takes. A source of more
 * values a row would drive a procedure writing more than one register, or
 * one register from a value wider than the register: a tone period is
 * twelve bits over two of them. The interface is sealed for the reason
 * {@link Target}'s is, so a kind added here is a change to this
 * specification.
 */
public sealed interface Source permits Single {

    /** What a writer calls this source, empty where it calls it nothing.
     *  It reaches the tools' reports and nothing a player reads. */
    String name();

    /** The rows, and the row they repeat to. */
    Table<?> table();

    /** The values one row holds. One at this version. */
    int columns();

    /** A source of one value a row that repeats to {@code repeat}. */
    static Source repeating(String name, List<Integer> values, int repeat) {
        return new Single(name, Table.repeating(values, repeat));
    }

    /** A source of one value a row that plays once and stops its timer. */
    static Source once(String name, List<Integer> values) {
        return new Single(name, Table.once(values));
    }
}
