package org.ymxs;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/**
 * One tune: what it is called, its sources, and its rows, one a frame at
 * the rate it states (SPEC.md 3).
 *
 * <p>The three texts are texts: empty and absent are one state for a
 * title or a composer, so none of them is an Optional. What a form does
 * with an empty one is that form's.
 *
 * <p>What a tune states is here. What is read off the rows is not: which
 * timers a player claims before the first row is {@link #timers}; how
 * many rows it has is {@code table.size()}; how many sources it holds is
 * {@code sources.size()}. A form may state each of those, since a player
 * reads one row at a time and cannot find them by reading ahead, but no
 * tune states them.
 *
 * <p>A row's {@link Effect} holds the source itself rather than a number,
 * and a form that numbers them takes this list's order. Two sources of a
 * tune are not equal for that reason: one listed twice would have two
 * numbers.
 *
 * <p>An effect hands a source's row to a target, and {@link Start} holds
 * the two together, so that they agree is read where a row states one.
 * What this reads is the tune's own: that a row starts a source the tune
 * holds.
 *
 * <p>What SPEC.md 6 asks of a writer binds across rows rather than within
 * one, so this record does not hold it: a walk over the rows reads those
 * rules, and names the row that breaks one.
 *
 * @param title what this tune is called, empty where it is not named. A
 *     host shows it, and in a {@link Multi} it names this subtune
 * @param composer who wrote it, empty where none is given
 * @param writer what made this structure, empty where none says
 * @param rate how often the player is called for this tune, in Hz
 * @param sources the sources, numbered 1 upward in this order
 * @param table the rows, one a frame, and the row the tune repeats to
 *     once the last row is done. A tune that plays once repeats to none,
 *     and every frame past its last row writes nothing
 */
public record Tune(String title, String composer, String writer, int rate,
                   List<Source> sources, Table<Row> table) {

    public Tune {
        if (rate < 1) {
            throw new IllegalArgumentException("a rate of " + rate
                    + ": a player is called at least once a second");
        }
        for (int at = 0; at < sources.size(); at++) {
            int again = sources.indexOf(sources.get(at));
            if (again != at) {
                throw new IllegalArgumentException("sources " + (again + 1) + " and "
                        + (at + 1) + " are one source listed twice, which is two numbers"
                        + " for it");
            }
        }
        for (int at = 0; at < table.rows().size(); at++) {
            for (Map.Entry<Timer, Effect> one : table.rows().get(at).effects().entrySet()) {
                if (one.getValue() instanceof Start started
                        && !sources.contains(started.source())) {
                    throw new IllegalArgumentException("row " + at + " starts a source on"
                            + " Timer " + one.getKey() + " that the tune does not hold");
                }
            }
        }
        sources = List.copyOf(sources);
    }

    /** The rows, one a frame. */
    public List<Row> rows() {
        return table.rows();
    }

    /** The timers this tune claims, which is every timer a row starts a
     *  source on. A player claims them before the first row (SPEC.md 3),
     *  and a tune that leaves Timer C alone can be hosted from the
     *  operating system's 200 Hz clock. */
    public EnumSet<Timer> timers() {
        EnumSet<Timer> claimed = EnumSet.noneOf(Timer.class);
        for (Row row : table.rows()) {
            for (Map.Entry<Timer, Effect> one : row.effects().entrySet()) {
                if (one.getValue() instanceof Start) {
                    claimed.add(one.getKey());
                }
            }
        }
        return claimed;
    }

    /** Source {@code number}, 1 upward, which is the number a form
     *  writes.
     *
     * @throws IllegalArgumentException where the tune holds no such source
     */
    public Source source(int number) {
        if (number < 1 || number > sources.size()) {
            throw new IllegalArgumentException("no source " + number + ": the tune holds "
                    + sources.size());
        }
        return sources.get(number - 1);
    }

    /** The number a form writes for {@code source}, 1 upward.
     *
     * @throws IllegalArgumentException where the tune does not hold it
     */
    public int number(Source source) {
        int at = sources.indexOf(source);
        if (at < 0) {
            throw new IllegalArgumentException("the tune holds no such source");
        }
        return at + 1;
    }
}
