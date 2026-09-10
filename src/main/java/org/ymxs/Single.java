package org.ymxs;

import java.util.List;

/**
 * The sources this version has: one value a row (SPEC.md 3.2). Every
 * {@link Target} of this version takes a row of that shape.
 *
 * <p>What values a row may hold is the target's: a source runs on a
 * target, and the register that target writes takes what it takes.
 * {@link Start} holds the two together and reads one against the other.
 *
 * @param name what a writer calls this source, empty where it calls it
 *     nothing
 * @param table the values, one a row, and the row they repeat to
 */
public record Single(String name, Table<Integer> table) implements Source {

    public Single {
        for (int at = 0; at < table.rows().size(); at++) {
            if (table.rows().get(at) < 0) {
                throw new IllegalArgumentException("row " + at + " is "
                        + table.rows().get(at) + ", and a register takes 0 upward");
            }
        }
    }

    @Override
    public int columns() {
        return 1;
    }

    /** The values, one a row. */
    public List<Integer> values() {
        return table.rows();
    }
}
