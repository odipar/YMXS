package org.ymxs;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import org.ymxs.YMXS.Effect;
import org.ymxs.YMXS.Register;
import org.ymxs.YMXS.Retune;
import org.ymxs.YMXS.Row;
import org.ymxs.YMXS.Source;
import org.ymxs.YMXS.Start;
import org.ymxs.YMXS.Stop;
import org.ymxs.YMXS.Timer;
import org.ymxs.YMXS.Tune;

/**
 * The record of SPEC.md 7: the figures of a tune once, then one line a
 * frame. A recorder here reads the structure rather than the chip, since
 * every write of a frame is in the row it reads (4.4).
 *
 * <p>{@link RecordTest} reads the example of 7.6 against it and the
 * conformance kit is written by it, so the clause, the example and the kit
 * move together.
 */
final class Record {

    private Record() {}

    /** The first line of the record (7.3). */
    static String first(Tune tune) {
        StringJoiner timers = new StringJoiner(",", "[", "]");
        for (Timer timer : Tunes.timers(tune)) {
            timers.add('"' + timer.name() + '"');
        }
        StringJoiner sources = new StringJoiner(",", "[", "]");
        for (Source source : Tunes.sources(tune)) {
            // one list, the values of row 0 then those of row 1 and so on,
            // which a source of one value a row reads as a value a row (7.3)
            StringJoiner values = new StringJoiner(",", "[", "]");
            for (List<Integer> row : Tunes.rows(source).rows()) {
                for (int value : row) {
                    values.add(String.valueOf(value));
                }
            }
            var repeat = Tunes.rows(source).repeat();
            sources.add("{\"rows\":" + values + ",\"repeat\":"
                    + (repeat.isPresent() ? String.valueOf(repeat.getAsInt()) : "null")
                    + "}");
        }
        return "{\"rate\":" + tune.rate() + ",\"timers\":" + timers
                + ",\"sources\":" + sources + "}";
    }

    /** The line of a frame that reads {@code row} (7.4). */
    static String frame(Tune tune, Row row) {
        StringJoiner writes = new StringJoiner(",", "{", "}");
        for (Map.Entry<Register, Integer> one : Tunes.registers(row).entrySet()) {
            writes.add("\"" + Chip.number(one.getKey()) + "\":" + one.getValue());
        }
        StringJoiner effects = new StringJoiner(",", "{", "}");
        for (Map.Entry<Timer, Effect> one : Tunes.effects(row).entrySet()) {
            effects.add('"' + one.getKey().name() + "\":" + operation(tune, one.getValue()));
        }
        return "{\"result\":0,\"w\":" + writes + ",\"e\":" + effects + "}";
    }

    /** The object of an operation, as the table of 7.4 lists it. */
    private static String operation(Tune tune, Effect effect) {
        return switch (effect) {
            case Start start -> "{\"start\":{\"target\":\"" + Tunes.name(Tunes.target(start))
                    + "\",\"source\":" + Tunes.number(tune, Tunes.source(start))
                    + ",\"prescaler\":" + Chip.divides(Tunes.prescaler(start))
                    + ",\"count\":" + Tunes.count(start)
                    + ",\"timerReset\":" + Tunes.timerReset(start)
                    + ",\"placeReset\":" + Tunes.placeReset(start) + "}}";
            case Retune retune -> "{\"retune\":{\"prescaler\":"
                    + Chip.divides(retune.timing().prescaler())
                    + ",\"count\":" + retune.timing().count()
                    + ",\"timerReset\":" + retune.timing().timerReset()
                    + ",\"placeReset\":" + retune.timing().placeReset() + "}}";
            case Stop ignored -> "{\"stop\":{}}";
        };
    }

    /** One pass of the tune: the first line, then a frame a row (4.2). */
    static List<String> record(Tune tune) {
        return record(tune, 1 + Tunes.size(tune.table()));
    }

    /**
     * The first line and {@code lines} minus one frames (7.5): the rows in
     * order, then the rows from the repeat row again for a tune that
     * repeats, and the line of the frame after the end for one that plays
     * once, where the record ends.
     */
    static List<String> record(Tune tune, int lines) {
        List<Row> rows = new ArrayList<>();
        for (Row row : Tunes.rows(tune)) {
            rows.add(row);
        }
        var repeat = tune.table().repeat();
        List<String> out = new ArrayList<>();
        out.add(first(tune));
        for (int at = 0; out.size() < lines; at++) {
            if (at < rows.size()) {
                out.add(frame(tune, rows.get(at)));
            } else if (repeat.isEmpty()) {
                out.add("{\"result\":-1}");
                break;
            } else {
                int loop = rows.size() - repeat.getAsInt();
                out.add(frame(tune, rows.get(repeat.getAsInt() + (at - rows.size()) % loop)));
            }
        }
        return out;
    }
}
