package org.ymxs.tool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.ymxs.Check;
import org.ymxs.Text;
import org.ymxs.Tunes;
import org.ymxs.YMXS.Multi;
import org.ymxs.YMXS.Tune;

/**
 * {@code ymxs-check tune.json ...}: what is wrong with each tune, and what
 * SPEC.md 6 asks of a writer that it does not keep to.
 *
 * <p>The two are apart because they are not the same fault. A tune the
 * first names states something no player can play. A tune the second names
 * plays, and plays as something other than what it states.
 *
 * <p>Each file gets a line on standard output. What is wrong goes to
 * standard error, so a run read through a pipe holds the lines and the
 * terminal holds the rest. The exit is 1 where anything is wrong, 2 where
 * a file does not read.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("ymxs-check tune.json [more.json ...]");
            System.exit(2);
            return;
        }
        boolean wrong = false;
        for (String name : args) {
            Multi multi;
            try {
                multi = Text.read(Files.readString(Path.of(name)));
            } catch (IllegalArgumentException no) {
                System.err.println(name + ": " + no.getMessage());
                System.exit(1);
                return;
            } catch (IOException failed) {
                System.err.println("ymxs-check: " + failed);
                System.exit(2);
                return;
            }
            wrong |= said(name, multi);
        }
        System.exit(wrong ? 1 : 0);
    }

    /** What one file came to, and whether anything is wrong with it. */
    private static boolean said(String name, Multi multi) {
        List<String> plays = new ArrayList<>(Check.of(multi));
        List<String> writing = new ArrayList<>();
        for (int at = 0; at < multi.tunes().size(); at++) {
            Tune tune = multi.tunes().get(at);
            for (String one : Check.writing(tune)) {
                writing.add(multi.tunes().size() == 1 ? one : "tune " + (at + 1) + ": " + one);
            }
        }
        int rows = 0;
        int sources = 0;
        for (Tune tune : multi.tunes()) {
            rows += Tunes.size(tune.table());
            sources += Tunes.sources(tune).size();
        }
        String held = plays.isEmpty() && writing.isEmpty() ? "nothing wrong"
                : plays.size() + " no player plays, " + writing.size() + " a writer breaks";
        System.out.println(name + ": " + multi.tunes().size()
                + (multi.tunes().size() == 1 ? " tune, " : " tunes, ") + rows + " rows, "
                + sources + (sources == 1 ? " source; " : " sources; ") + held);
        for (String one : plays) {
            System.err.println(name + ": " + one);
        }
        for (String one : writing) {
            System.err.println(name + ": " + one);
        }
        return !plays.isEmpty() || !writing.isEmpty();
    }
}
