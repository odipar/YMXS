package org.ymxs;

/**
 * The row leaves the effect running the source and the target it has, at
 * the rate this states (SPEC.md 1.9). A note that bends states the rate
 * with a count that moved; a note struck again at the rate it has states
 * the rate it has with the place's reset.
 *
 * <p>The rate is stated whole whether or not both parts moved. What a
 * form writes is the parts that moved, and over the 49 tunes measured in
 * {@link Effect} a row states a count the player already holds 12,808
 * times of 25,001.
 *
 * @param prescaler the timer's first divisor
 * @param count the timer's second divisor, 1 to {@link Start#MOST_COUNT}
 * @param timerReset the timer's reset (SPEC.md 1.9)
 * @param placeReset the place's reset
 */
public record Retune(Prescaler prescaler, int count, boolean timerReset,
                     boolean placeReset) implements Effect {

    public Retune {
        if (count < 1 || count > Start.MOST_COUNT) {
            throw new IllegalArgumentException("count " + count + ": a timer counts 1 to "
                    + Start.MOST_COUNT);
        }
    }

    /** The row moves the count and leaves the prescaler and both resets
     *  as they are: a note that bends. */
    public static Retune bend(Prescaler prescaler, int count) {
        return new Retune(prescaler, count, false, false);
    }
}
