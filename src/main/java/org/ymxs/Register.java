package org.ymxs;

/**
 * One of the fourteen YM2149 registers a row sets and a target writes
 * (SPEC.md 1, 2.1). Two registers of the sixteen are the I/O ports and
 * are no tune's.
 *
 * <p>{@link #most} gives the largest value the register takes, and a row
 * setting it to more is a row no player reads. A voice's tone period and
 * the envelope period each take two registers, one the divider's low bits
 * and one its high, because a row may set one and not the other.
 *
 * <p>A register is what a row sets. What a tick writes is a {@link
 * Target}, and this version's targets are one a register.
 */
public enum Register {

    /** Voice A tone period, the divider's bits 7 to 0. */
    R0(255),
    /** Voice A tone period, the divider's bits 11 to 8. */
    R1(15),
    /** Voice B tone period, the divider's bits 7 to 0. */
    R2(255),
    /** Voice B tone period, the divider's bits 11 to 8. */
    R3(15),
    /** Voice C tone period, the divider's bits 7 to 0. */
    R4(255),
    /** Voice C tone period, the divider's bits 11 to 8. */
    R5(15),
    /** The noise period: how bright the noise is, not how loud. */
    R6(31),
    /** Mixing: which generator signals reach each voice. Bits 7 and 6 of
     *  the register are the host's I/O port directions, which no tune
     *  writes, so this takes six bits. */
    R7(63),
    /** Voice A volume: a level in bits 3 to 0, with bit 4 taking the level
     *  from the envelope generator instead. */
    R8(31),
    /** Voice B volume, as R8. */
    R9(31),
    /** Voice C volume, as R8. */
    R10(31),
    /** The envelope period, the divider's bits 7 to 0. */
    R11(255),
    /** The envelope period, the divider's bits 15 to 8. */
    R12(255),
    /** The envelope shape. Any write to it restarts the envelope. */
    R13(15);

    private final int most;

    Register(int most) {
        this.most = most;
    }

    /** The largest value this register takes. The smallest is 0. */
    public int most() {
        return most;
    }

    /** The register numbered {@code at}, 0 to 13, as the chip numbers
     *  them.
     *
     * @throws IllegalArgumentException where no register has that number
     */
    public static Register at(int at) {
        if (at < 0 || at >= values().length) {
            throw new IllegalArgumentException("no register " + at
                    + ": a tune reaches R0 to R13");
        }
        return values()[at];
    }

    /** This register's number, 0 to 13. */
    public int number() {
        return ordinal();
    }
}
