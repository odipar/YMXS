package org.ymxs.tool;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.ymxs.Check;
import org.ymxs.Text;
import org.ymxs.Tunes;
import org.ymxs.YMXS.Multi;
import org.ymxs.YMXS.Tune;

/**
 * {@code ymxs-check}: a tune on standard input, the same text on standard
 * output, and what it gets wrong on standard error. It stands in a pipe
 * without changing what runs through it.
 *
 * <p>Two kinds of wrong, and they are not the same fault.
 *
 * <p><b>An error</b> is a tune no player plays: text that is not this
 * form, or a structure the two chips do not take. Nothing goes to standard
 * output and the exit is 1, so a pipe stops rather than carrying something
 * broken further.
 *
 * <p><b>A warning</b> is a tune that plays, and plays as something other
 * than what it states: one of the rules doc/SPEC.md 6 asks of a writer.
 * The tune goes through and the exit is 0, since a player takes it and
 * only the writer can say whether it is what was meant.
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
        tool.say(multi.tunes().size() + (multi.tunes().size() == 1 ? " tune, " : " tunes, ")
                + rows + " rows, " + sources + (sources == 1 ? " source" : " sources"));
        int warnings = 0;
        for (int at = 0; at < multi.tunes().size(); at++) {
            for (String one : Check.writing(multi.tunes().get(at))) {
                System.err.println("ymxs-check: warning: "
                        + (multi.tunes().size() == 1 ? "" : "tune " + (at + 1) + ": ") + one);
                warnings++;
            }
        }
        tool.say(warnings == 0 ? "every rule a writer keeps to is kept"
                : warnings + (warnings == 1 ? " warning" : " warnings"));
    }
}
