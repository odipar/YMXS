package org.ymxs;

/**
 * The row runs {@code source} on {@code target} at the rate {@code
 * prescaler} and {@code count} give, from this row on (SPEC.md 1.8).
 *
 * <p>The target and the rate are stated whether or not they moved, since
 * this says what the effect is and not which of its parts changed. A form
 * writes the parts that moved.
 *
 * @param target the procedure this effect's ticks call with a source's row
 * @param source the source the effect runs
 * @param prescaler the timer's first divisor
 * @param count the timer's second divisor, 1 to {@link #MOST_COUNT}
 * @param timerReset the timer's reset (SPEC.md 1.9). A row striking a note
 *     on a stopped timer sets it
 * @param placeReset the place's reset. A row starting a source sets it,
 *     unless the source it starts has the row count of the one the effect
 *     last ran, where leaving it clear keeps the wave's phase
 */
public record Start(Target target, Source source, Prescaler prescaler, int count,
                    boolean timerReset, boolean placeReset) implements Effect {

    /** The largest count the MC68901 takes: its data register holds 1 to
     *  255, and 0 counts 256. */
    public static final int MOST_COUNT = 256;

    public Start {
        if (count < 1 || count > MOST_COUNT) {
            throw new IllegalArgumentException("count " + count + ": a timer counts 1 to "
                    + MOST_COUNT);
        }
        if (target.columns() != source.columns()) {
            throw new IllegalArgumentException("a source of " + source.columns()
                    + " values a row on " + target + ", which takes "
                    + target.columns());
        }
        if (source instanceof Single held) {
            for (int at = 0; at < held.values().size(); at++) {
                int value = held.values().get(at);
                if (value > target.most()) {
                    throw new IllegalArgumentException("a source on " + target + " whose row "
                            + at + " is " + value + ", and the target takes 0 to "
                            + target.most());
                }
            }
        }
    }

    /** The row strikes a note: the source from its first row, the timer
     *  from a whole period. */
    public static Start struck(Target target, Source source, Prescaler prescaler, int count) {
        return new Start(target, source, prescaler, count, true, true);
    }
}
