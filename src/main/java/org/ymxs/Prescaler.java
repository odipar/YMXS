package org.ymxs;

/**
 * A timer's first divisor (SPEC.md 3.3). Its rate is {@link #CLOCK}
 * divided by the prescaler times the count, so these seven and a count of
 * 1 to 256 give every rate a tune reaches.
 */
public enum Prescaler {

    BY_4(4),
    BY_10(10),
    BY_16(16),
    BY_50(50),
    BY_64(64),
    BY_100(100),
    BY_200(200);

    /** The MC68901's clock, in ticks a second. */
    public static final int CLOCK = 2457600;

    private final int divides;

    Prescaler(int divides) {
        this.divides = divides;
    }

    /** What this prescaler divides the clock by. */
    public int divides() {
        return divides;
    }

    /** The prescaler that divides by {@code by}.
     *
     * @throws IllegalArgumentException where the MFP has no such divisor
     */
    public static Prescaler dividing(int by) {
        for (Prescaler one : values()) {
            if (one.divides == by) {
                return one;
            }
        }
        throw new IllegalArgumentException("no prescaler divides by " + by
                + ": the MFP's are 4, 10, 16, 50, 64, 100 and 200");
    }

    /** The rate a timer runs at with this prescaler and a count, in ticks
     *  a second. */
    public int rate(int count) {
        return CLOCK / (divides * count);
    }
}
