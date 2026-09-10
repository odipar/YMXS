package org.ymxs;

/**
 * What a timer's tick calls with a source's row (SPEC.md 3.1). A target
 * is a procedure: it takes one row of a source and writes it.
 *
 * <p>This version names fourteen, one a YM2149 register, and
 * {@link SetRegister} is all of them. A later version names more: a
 * procedure reaching the MC68901's own registers, and one taking a row of
 * more than one value, a tone period being twelve bits over two
 * registers.
 *
 * <p>The interface is sealed, so a version that adds a target adds it
 * here, and every switch over targets that has not read the new one stops
 * compiling. That is what makes an added target a change to this
 * specification.
 */
public sealed interface Target permits SetRegister {

    /** The values one row of a source holds for this target. One at this
     *  version. */
    int columns();

    /** The largest value one of those holds. The smallest is 0. A source
     *  this target runs holds values within it. */
    int most();

    /** The target that writes {@code register}. */
    static Target setting(Register register) {
        return new SetRegister(register);
    }
}
