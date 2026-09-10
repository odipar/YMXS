package org.ymxs;

import java.util.List;

/**
 * Several tunes, which is what a host plays as a tune with subtunes. A
 * multi of one tune is a tune on its own.
 *
 * <p>Everything a tune has is that tune's: what it is called, who wrote
 * it, and the rate it plays at. What a form does with that is the form's.
 * A form that names one title, one composer and one rate for a whole
 * multi works those out from the tunes it is given, and a multi it cannot
 * write is one it turns away; it is still a multi.
 *
 * @param tunes the tunes, numbered 1 upward in this order, which is the
 *     number a host asks for
 */
public record Multi(List<Tune> tunes) {

    public Multi {
        if (tunes.isEmpty()) {
            throw new IllegalArgumentException("a multi of no tunes: a host plays one");
        }
        tunes = List.copyOf(tunes);
    }

    /** A multi of one tune. */
    public static Multi of(Tune tune) {
        return new Multi(List.of(tune));
    }

    /** Tune {@code number}, 1 upward, which is the number a host asks
     *  for.
     *
     * @throws IllegalArgumentException where the multi holds no such tune
     */
    public Tune tune(int number) {
        if (number < 1 || number > tunes.size()) {
            throw new IllegalArgumentException("no tune " + number + ": the multi holds "
                    + tunes.size());
        }
        return tunes.get(number - 1);
    }
}
