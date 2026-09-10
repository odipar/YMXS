package org.ymxs;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * One row of a tune's table, read once a frame (SPEC.md 1, 4). It holds
 * what it states and says nothing about the rest: a register absent from
 * {@code registers} is one this row does not write, and a timer absent
 * from {@code effects} is one this row leaves running as it runs.
 *
 * <p>The order a player writes a row in is the frame procedure's and not
 * the row's (SPEC.md 4): the effects first, then the tone periods, the
 * noise and envelope periods, the volumes, the mixing and the shape. Both
 * maps here read in the order their enums declare, so a form writes a row
 * the same way twice.
 *
 * @param registers each register this row sets, and the value it sets it
 *     to, 0 to that register's {@link Register#most}
 * @param effects each timer this row states something of, and which of
 *     {@link Start}, {@link Retune} and {@link Stop} it states there. A
 *     timer absent is one the row leaves running as it runs
 */
public record Row(Map<Register, Integer> registers, Map<Timer, Effect> effects) {

    /** A row that states nothing. A player advancing over it writes no
     *  register and moves no timer. */
    public static final Row NOTHING = new Row(Map.of(), Map.of());

    public Row {
        EnumMap<Register, Integer> sets = new EnumMap<>(Register.class);
        for (Map.Entry<Register, Integer> one : registers.entrySet()) {
            int value = one.getValue();
            if (value < 0 || value > one.getKey().most()) {
                throw new IllegalArgumentException(one.getKey() + " takes 0 to "
                        + one.getKey().most() + ", and this row sets it to " + value);
            }
            sets.put(one.getKey(), value);
        }
        registers = Collections.unmodifiableMap(sets);
        EnumMap<Timer, Effect> stated = new EnumMap<>(Timer.class);
        stated.putAll(effects);
        effects = Collections.unmodifiableMap(stated);
    }

    /** The row that sets these registers and states nothing of any
     *  effect. */
    public static Row of(Map<Register, Integer> registers) {
        return new Row(registers, Map.of());
    }

    /** Whether this row sets no register and states nothing of any
     *  effect. */
    public boolean statesNothing() {
        return registers.isEmpty() && effects.isEmpty();
    }
}
