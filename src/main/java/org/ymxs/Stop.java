package org.ymxs;

/**
 * The row stops the effect: its timer stops, and the effect runs nothing
 * until a later row starts a source on it (SPEC.md 1.8). The place holds
 * the row number the last tick read.
 *
 * <p>It states nothing else, which is what rows do: of 2,232 stops over
 * the tunes measured in {@link Effect}, 2,232 state nothing else.
 */
public record Stop() implements Effect {

    /** The stop, which has no state of its own. */
    public static final Stop STOP = new Stop();

    @Override
    public boolean timerReset() {
        return false;
    }

    @Override
    public boolean placeReset() {
        return false;
    }
}
