package org.ymxs.ym;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalInt;
import org.ymxs.Text;
import org.ymxs.Tunes;
import org.ymxs.YMXS.Tune;
import org.ymxs.tool.Tool;

/**
 * {@code ym-to-ymxs}: a YM5!/YM6! register dump on standard input, JSON on
 * standard output. A distributed {@code .ym} is usually an archive with
 * the dump inside, and either reads.
 *
 * <p>One dump is one tune, so what comes out is a multi of one.
 * {@code ymxs-merge} puts several together.
 *
 * <p>{@code -r} makes a tune that plays once, and {@code -rROW} one that
 * repeats to that row; without either, a tune repeats to the frame the
 * dump names.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        List<String> rest = new ArrayList<>(Arrays.asList(args));
        Tool tool = Tool.of("ym-to-ymxs", rest, "-r");
        OptionalInt repeat = OptionalInt.empty();
        boolean given = false;
        for (String arg : rest) {
            if (arg.equals("-r")) {
                given = true;
            } else if (arg.startsWith("-r")) {
                given = true;
                try {
                    repeat = OptionalInt.of(Integer.parseInt(arg.substring(2)));
                } catch (NumberFormatException wrong) {
                    throw tool.usage(arg + " is not a row number");
                }
            } else {
                throw tool.usage("ym-to-ymxs reads a dump on standard input and writes"
                        + " JSON on standard output. It takes -rROW, -r and -silent,"
                        + " and \"" + arg + "\" is none of them.");
            }
        }
        Dump.Song song;
        Read.Reading reading;
        try {
            song = Dump.read(tool.bytes());
            reading = Read.of(song, "ym-to-ymxs", given ? repeat : OptionalInt.of(row(song)));
        } catch (Dump.Unreadable | IllegalArgumentException no) {
            throw tool.wrong(Tool.WRONG, String.valueOf(no.getMessage()));
        }
        tool.write(Text.write(Tunes.multi(reading.tune())));
        said(tool, song, reading);
    }

    /** The row a dump repeats to, or 0 where it gives none this reads. */
    private static int row(Dump.Song song) {
        long loop = song.loopFrame();
        return loop >= 0 && loop < song.frames() ? (int) loop : 0;
    }

    /** What the dump came to, on standard error. */
    private static void said(Tool tool, Dump.Song song, Read.Reading reading) {
        if (!tool.says()) {
            return;
        }
        Tune tune = reading.tune();
        StringBuilder out = new StringBuilder(song.format() + " \"" + song.name() + "\" by \""
                + song.author() + "\", " + Tunes.size(tune.table()) + " rows at " + tune.rate()
                + " Hz, " + Tunes.sources(tune).size() + " sources, timers "
                + Tunes.timers(tune));
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
        tool.say(out.toString());
    }
}
