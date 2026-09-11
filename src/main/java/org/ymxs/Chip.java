package org.ymxs;

import org.ymxs.YMXS.Prescaler;
import org.ymxs.YMXS.Register;

/**
 * The figures of the two chips. Every value here is the YM2149's or the
 * MC68901's, and none of it belongs to a form.
 *
 * <p>Each function reads its argument by pattern matching over every
 * case, so a register or a prescaler added to {@link YMXS} stops this
 * compiling until it is read here.
 */
public final class Chip {

    /** The MC68901's clock, in ticks a second. */
    public static final int CLOCK = 2457600;

    /** The largest value a timer's data register reads. The register is a
     *  byte and every value of it is a count, 0 among them. */
    public static final int MOST_COUNT = 255;

    /** The ticks a count of 0 counts. A timer counts the register's value
     *  down through zero, so 0 counts a whole byte round. */
    public static final int ZERO_COUNTS = 256;

    /** The ticks {@code count} counts: the value itself, and
     *  {@link #ZERO_COUNTS} where it is 0. */
    public static int ticks(int count) {
        return count == 0 ? ZERO_COUNTS : count;
    }

    private Chip() {
    }

    /** The largest value that fits {@code register}. The smallest is 0.
     *
     *  <p>A voice's tone period and the envelope period each run over two
     *  registers, one the divider's low bits and one its high, because a
     *  row may set one and not the other. A volume is five bits: four a
     *  level, and one that reads the level from the envelope generator
     *  instead. In R7, bits 7 and 6 are the host's I/O port directions,
     *  which no tune moves, so a row sets six bits. */
    public static int most(Register register) {
        return switch (register) {
            case R0, R2, R4, R11, R12 -> 255;
            case R1, R3, R5, R13 -> 15;
            case R6, R8, R9, R10 -> 31;
            case R7 -> 63;
        };
    }

    /** What {@code register} reaches, as text. */
    public static String reaches(Register register) {
        return switch (register) {
            case R0, R1 -> "voice A's tone period";
            case R2, R3 -> "voice B's tone period";
            case R4, R5 -> "voice C's tone period";
            case R6 -> "the noise period";
            case R7 -> "mixing";
            case R8 -> "voice A's volume";
            case R9 -> "voice B's volume";
            case R10 -> "voice C's volume";
            case R11, R12 -> "the envelope period";
            case R13 -> "the envelope shape";
        };
    }

    /** The register number, 0 to 13, as the chip numbers them. */
    public static int number(Register register) {
        return register.ordinal();
    }

    /** The register numbered {@code at}.
     *
     * @throws IllegalArgumentException where the chip defines no such
     *     register
     */
    public static Register register(int at) {
        Register[] all = Register.values();
        if (at < 0 || at >= all.length) {
            throw new IllegalArgumentException("no register " + at
                    + ": a tune reaches R0 to R13");
        }
        return all[at];
    }

    /** What {@code prescaler} divides the clock by. */
    public static int divides(Prescaler prescaler) {
        return switch (prescaler) {
            case BY_4 -> 4;
            case BY_10 -> 10;
            case BY_16 -> 16;
            case BY_50 -> 50;
            case BY_64 -> 64;
            case BY_100 -> 100;
            case BY_200 -> 200;
        };
    }

    /** The prescaler that divides by {@code by}.
     *
     * @throws IllegalArgumentException where no prescaler divides by it
     */
    public static Prescaler prescaler(int by) {
        for (Prescaler one : Prescaler.values()) {
            if (divides(one) == by) {
                return one;
            }
        }
        throw new IllegalArgumentException("no prescaler divides by " + by
                + ": a timer's are 4, 10, 16, 50, 64, 100 and 200");
    }

    /** The rate a timer runs at with this prescaler and this count, in
     *  ticks a second. */
    public static int rate(Prescaler prescaler, int count) {
        return CLOCK / (divides(prescaler) * ticks(count));
    }

    /**
     * The frames a source of {@code rows} rows runs for at this rate,
     * where the player is called {@code called} times a second: rounded
     * up, with a sixteenth of a frame for a start that falls inside the
     * frame it begins in.
     *
     * <p>This is a reckoning and not a reading. A tune marks the row a
     * source starts on and no row for its end, so the duration of one that
     * plays once follows from its rate, and any reading resting on that is
     * reported as such.
     */
    public static int frames(int rows, Prescaler prescaler, int count, int called) {
        long divisor = (long) divides(prescaler) * ticks(count);
        long scaled = (long) rows * divisor * called + CLOCK / 16;
        return (int) ((scaled + CLOCK - 1) / CLOCK);
    }
}
