package org.ymxs;

import java.util.List;
import java.util.OptionalInt;

/**
 * Rows, and the row they repeat to once the last row is done. A tune's
 * rows are one of these and so are a source's, which is what SPEC.md 3.2
 * means by a source having the shape a tune's table has: the tune's rows
 * advance one a frame and a source's one a tick, and that is the whole
 * difference.
 *
 * @param rows the rows, in order, one at least
 * @param repeat the row the table repeats to, or empty for one that plays
 *     once, after which it yields no row
 */
public record Table<T>(List<T> rows, OptionalInt repeat) {

    public Table {
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("a table of no rows: a clock reads one");
        }
        if (repeat.isPresent()
                && (repeat.getAsInt() < 0 || repeat.getAsInt() >= rows.size())) {
            throw new IllegalArgumentException("a table of " + rows.size()
                    + " rows repeating to row " + repeat.getAsInt());
        }
        rows = List.copyOf(rows);
    }

    /** A table that repeats to {@code repeat}. */
    public static <T> Table<T> repeating(List<T> rows, int repeat) {
        return new Table<>(rows, OptionalInt.of(repeat));
    }

    /** A table that plays once. */
    public static <T> Table<T> once(List<T> rows) {
        return new Table<>(rows, OptionalInt.empty());
    }

    /** How many rows the table holds. */
    public int size() {
        return rows.size();
    }
}
