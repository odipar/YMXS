package org.ymxs.tool;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.ymxs.Text;
import org.ymxs.YMXS.Multi;
import org.ymxs.YMXS.Tune;

/**
 * {@code ymxs-merge}: several tunes on standard input, one multi on
 * standard output.
 *
 * <p>Every other tool reads one input and writes one output. A multi
 * contains several tunes, and this tool combines them: JSON puts no count
 * in front of a stream of values, so several such files concatenated read
 * as several multis, and their tunes combine into one in input order.
 *
 * <pre>
 *   cat one.json two.json | ymxs-merge &gt; both.json
 * </pre>
 */
public final class Merge {

    private Merge() {
    }

    public static void main(String[] args) {
        List<String> rest = new ArrayList<>(Arrays.asList(args));
        Tool tool = Tool.of("ymxs-merge", rest);
        List<Multi> read;
        try {
            read = Text.readAll(tool.text());
        } catch (IllegalArgumentException no) {
            throw tool.wrong(Tool.WRONG, String.valueOf(no.getMessage()));
        }
        List<Tune> tunes = new ArrayList<>();
        for (Multi one : read) {
            tunes.addAll(one.tunes());
        }
        if (tunes.isEmpty()) {
            throw tool.wrong(Tool.WRONG, "no tune to merge: a multi is one tune at least");
        }
        tool.write(Text.write(new Multi(tunes)));
        tool.report(read.size() + (read.size() == 1 ? " file with " : " files with ")
                + tunes.size() + (tunes.size() == 1 ? " tune" : " tunes"));
    }
}
