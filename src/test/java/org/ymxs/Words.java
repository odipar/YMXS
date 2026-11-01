package org.ymxs;

import java.util.List;

/** A whole number as the word for it in prose, for a test that reads a
 *  figure written as a word against the figure it counts. */
final class Words {

    private static final List<String> WORDS = List.of("zero", "one", "two", "three",
            "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve",
            "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen",
            "nineteen", "twenty");

    private Words() {
    }

    /** {@code number} as a word, zero to twenty. */
    static String of(int number) {
        if (number < 0 || number >= WORDS.size()) {
            throw new IllegalArgumentException("the words here are zero to twenty, and "
                    + number + " is outside them");
        }
        return WORDS.get(number);
    }
}
