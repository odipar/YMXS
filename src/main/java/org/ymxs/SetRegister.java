package org.ymxs;

/**
 * The targets this version names: {@code setR0} to {@code setR13}, which
 * write a source's row to one YM2149 register (SPEC.md 3.1).
 *
 * <p>{@code setR7} writes bits 5 to 0 with R7's bits 7 and 6 as the host
 * holds them, which it does not move. Those two are the chip's I/O port
 * directions and are no tune's.
 *
 * @param register the register this target writes
 */
public record SetRegister(Register register) implements Target {

    @Override
    public int columns() {
        return 1;
    }

    @Override
    public int most() {
        return register.most();
    }

    @Override
    public String toString() {
        return "set" + register;
    }
}
