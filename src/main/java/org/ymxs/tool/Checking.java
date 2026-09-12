package org.ymxs.tool;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.ymxs.Text;
import org.ymxs.Tunes;
import org.ymxs.YMXS.Multi;
import org.ymxs.YMXS.Tune;

/**
 * {@code ymxs-check}: a tune on standard input, the same text on standard
 * output, and the faults on standard error. It stands in a pipe and leaves
 * what passes through it unchanged.
 *
 * <p>It separates two kinds of fault.
 *
 * <p><b>An error</b> is a tune no player plays: text outside this form, or
 * a structure outside the two chips. Standard output stays empty and the
 * exit is 1, so the pipe stops rather than passing broken data on.
 *
 * <p><b>A warning</b> is a tune that plays, but not as written: it breaks
 * a rule of doc/SPEC.md 6. The tune passes through and the exit is 0,
 * since a player plays it and only the writer can judge the result.
 */
public final class Checking {

    private Checking() {
    }

    public static void main(String[] args) {
        List<String> rest = new ArrayList<>(Arrays.asList(args));
        Tool tool = Tool.of("ymxs-check", rest);
        String text = tool.text();
        Multi multi;
        try {
            multi = Text.read(text);
        } catch (IllegalArgumentException no) {
            throw tool.wrong(Tool.WRONG, String.valueOf(no.getMessage()));
        }
        tool.write(text);
        int rows = 0;
        int sources = 0;
        for (Tune tune : multi.tunes()) {
            rows += Tunes.size(tune.table());
            sources += Tunes.sources(tune).size();
        }
        tool.report(multi.tunes().size() + (multi.tunes().size() == 1 ? " tune, " : " tunes, ")
                + rows + " rows, " + sources + (sources == 1 ? " source" : " sources"));
        int warnings = tool.warnings(multi);
        tool.report(warnings == 0 ? "every rule of SPEC.md 6 is satisfied"
                : warnings + (warnings == 1 ? " warning" : " warnings"));
    }
}
