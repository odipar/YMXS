package org.ymxs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import org.junit.jupiter.api.Test;
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
 * The record of SPEC.md 7 against the example of 7.6.
 *
 * <p>SPEC.md 7 defines the record a recorder produces: the figures of the
 * tune once, then one line a frame. This is a recorder of one pass, and it
 * reads the five lines of 7.6 out of the document and requires them equal
 * to the record of {@code doc/tunes/example.json}, so the example stays
 * measured where the clause is reworded.
 */
final class RecordTest {

    private static final Path SPEC = Path.of("doc/SPEC.md");
    private static final Path EXAMPLE = Path.of("doc/tunes/example.json");

    /** The indented lines of 7.6: the first line and one pass. */
    private static List<String> quoted() throws IOException {
        List<String> lines = Files.readAllLines(SPEC);
        int from = -1;
        for (int at = 0; at < lines.size(); at++) {
            if (lines.get(at).startsWith("**7.6 ")) {
                from = at;
            }
        }
        assertTrue(from >= 0, SPEC + " has no clause 7.6");
        List<String> out = new ArrayList<>();
        for (int at = from; at < lines.size(); at++) {
            String said = lines.get(at);
            if (said.startsWith("    {")) {
                out.add(said.strip());
            } else if (!out.isEmpty()) {
                break;
            }
        }
        return out;
    }

    /** The first line of the record (7.3). */
    private static String first(Tune tune) {
        StringJoiner timers = new StringJoiner(",", "[", "]");
        for (Timer timer : Tunes.timers(tune)) {
            timers.add('"' + timer.name() + '"');
        }
        StringJoiner sources = new StringJoiner(",", "[", "]");
        for (Source source : Tunes.sources(tune)) {
            StringJoiner values = new StringJoiner(",", "[", "]");
            for (int value : Tunes.values(source)) {
                values.add(String.valueOf(value));
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
    private static String frame(Tune tune, Row row) {
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
    private static List<String> record(Tune tune) {
        List<String> out = new ArrayList<>();
        out.add(first(tune));
        for (Row row : Tunes.rows(tune)) {
            out.add(frame(tune, row));
        }
        return out;
    }

    @Test
    void theExampleOfSevenSixIsTheRecordOfTheExampleTune() throws IOException {
        Tune tune = Tunes.tune(Text.read(Files.readString(EXAMPLE)), 1);
        List<String> quoted = quoted();
        assertEquals(1 + Tunes.size(tune.table()), quoted.size(),
                SPEC + " 7.6 lists the first line and one pass");
        assertEquals(record(tune), quoted, SPEC + " 7.6 is not the record of " + EXAMPLE);
    }
}
