package org.ymxs.ym;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import org.ymxs.Multi;
import org.ymxs.Text;
import org.ymxs.Tune;

/**
 * {@code ym-to-ymxs in.ym [more.ym ...] out.json}: dumps read into one
 * multi, written as the text form. A dump this does not read gets a line
 * on standard error beginning {@code ym-to-ymxs: } and an exit of 1; a
 * file that does not read or write gets the same and an exit of 2, as
 * does a wrong call.
 *
 * <p>{@code -r} makes a tune that plays once, and {@code -rROW} one that
 * repeats to that row; without either, a tune repeats to the frame the
 * dump names.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        List<String> named = new ArrayList<>();
        OptionalInt repeat = OptionalInt.empty();
        boolean stated = false;
        for (String arg : args) {
            if (arg.equals("-r")) {
                stated = true;
            } else if (arg.startsWith("-r")) {
                stated = true;
                try {
                    repeat = OptionalInt.of(Integer.parseInt(arg.substring(2)));
                } catch (NumberFormatException wrong) {
                    System.err.println("ym-to-ymxs: " + arg + " is not a row number");
                    System.exit(2);
                    return;
                }
            } else {
                named.add(arg);
            }
        }
        if (named.size() < 2) {
            System.err.println("ym-to-ymxs in.ym [more.ym ...] out.json [-rROW | -r]");
            System.exit(2);
            return;
        }
        List<Tune> tunes = new ArrayList<>();
        for (int at = 0; at + 1 < named.size(); at++) {
            try {
                Dump.Song song = Dump.read(Files.readAllBytes(Path.of(named.get(at))));
                OptionalInt to = stated ? repeat : OptionalInt.of(row(song));
                Read.Reading reading = Read.of(song, "ym-to-ymxs", to);
                tunes.add(reading.tune());
                said(named.get(at), reading);
            } catch (Dump.Unreadable | IllegalArgumentException wrong) {
                System.err.println("ym-to-ymxs: " + named.get(at) + ": " + wrong.getMessage());
                System.exit(1);
                return;
            } catch (IOException failed) {
                System.err.println("ym-to-ymxs: " + failed);
                System.exit(2);
                return;
            }
        }
        Path out = Path.of(named.get(named.size() - 1));
        try {
            Files.writeString(out, Text.write(new Multi(tunes)));
        } catch (IOException failed) {
            System.err.println("ym-to-ymxs: " + failed);
            System.exit(2);
            return;
        }
        System.out.println(out + ": " + tunes.size() + (tunes.size() == 1 ? " tune" : " tunes"));
    }

    /** The row a dump repeats to, or 0 where it names none this reads. */
    private static int row(Dump.Song song) {
        long loop = song.loopFrame();
        return loop >= 0 && loop < song.frames() ? (int) loop : 0;
    }

    /** What one dump came to, on standard error. */
    private static void said(String name, Read.Reading reading) {
        Tune tune = reading.tune();
        StringBuilder out = new StringBuilder(name + ": " + tune.table().size() + " rows at "
                + tune.rate() + " Hz, " + tune.sources().size() + " sources, timers "
                + tune.timers());
        Read.Said said = reading.said();
        if (said.dropped() > 0) {
            out.append(", ").append(said.dropped()).append(" slots this does not read");
        }
        if (said.preempted() > 0) {
            out.append(", ").append(said.preempted())
                    .append(" frames a recording kept a square wave off its voice");
        }
        if (said.cutAtRepeat() > 0) {
            out.append(", ").append(said.cutAtRepeat())
                    .append(" recordings cut at the row the tune repeats to");
        }
        System.err.println(out);
    }
}
