package org.ymxs;

/**
 * What a row states of the effect on one timer (SPEC.md 1.8, 1.9). An
 * effect is a source connected to a target on one timer, at the rate a
 * prescaler and a count give, and a row does one of three things to it:
 * {@link Start} it, {@link Retune} it, or {@link Stop} it. A timer a row
 * says nothing of is one absent from {@link Row#effects}.
 *
 * <p>Each case states the whole of what the effect is from that row on,
 * so no part of one is optional. A {@link Start} states its target and
 * its rate even where they are the ones the effect already ran at; a
 * {@link Retune} states the rate even where only the count moves. A
 * player keeps the target, the source and the rate it was last given, so
 * what a form writes is the parts that moved, which is that form's work
 * and not this record's.
 *
 * <p>The three shapes are what rows do, read over 49 tunes converted from
 * register dumps of the scene's own music, 331,376 rows and 27,004
 * statements between them:
 *
 * <ul>
 * <li>Every statement is one of the three.
 * <li>Every stop states nothing else, 2,232 times of 2,232.
 * <li>A row states a value the player already holds all the time: the
 *     prescaler 14,552 times of 17,572, the count 12,808 of 25,001. So a
 *     form writing only what moved has work to do, and this holds the
 *     whole state for it to do it.
 * <li>No start of 20,664 states the running source at the rate it has
 *     with neither reset, which is the one row a {@link Start} could not
 *     be told from silence.
 * </ul>
 */
public sealed interface Effect permits Start, Retune, Stop {

    /** The timer's reset: the timer stops, takes the count and starts, so
     *  it begins a whole period at that count. It moves a running timer
     *  and not a stopped one, which begins a whole period either way. */
    boolean timerReset();

    /** The place's reset: the next tick takes the source's first row.
     *  Without it the place holds the row number the last tick read, and
     *  that number counts into the rows of whatever source runs next. */
    boolean placeReset();
}
