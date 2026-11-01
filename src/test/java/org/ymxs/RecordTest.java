package org.ymxs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
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

    @Test
    void theExampleOfSevenSixIsTheRecordOfTheExampleTune() throws IOException {
        Tune tune = Tunes.tune(Text.read(Files.readString(EXAMPLE)), 1);
        List<String> quoted = quoted();
        assertEquals(1 + Tunes.size(tune.table()), quoted.size(),
                SPEC + " 7.6 lists the first line and one pass");
        assertEquals(Record.record(tune), quoted, SPEC + " 7.6 is not the record of " + EXAMPLE);
    }

    /** The record of the start that maps the volume of setVoiceA alone:
     *  the first line lists V5 by its entries, and the start records its
     *  mapping as an array of a number a part (SPEC.md 7.3, 7.4). */
    @Test
    void aStartRecordsItsMapping() {
        List<String> entries = new ArrayList<>();
        for (int entry : Fade.volumes(5).entries()) {
            entries.add(String.valueOf(entry));
        }
        assertEquals(List.of(
                "{\"rate\":50,\"timers\":[\"A\"],\"sources\":[{\"rows\":[28,1,15,214,0,12],"
                        + "\"repeat\":0}],\"transformations\":[[" + String.join(",", entries)
                        + "]]}",
                "{\"result\":0,\"w\":{\"7\":62},\"e\":{\"A\":{\"start\":{\"target\":"
                        + "\"setVoiceA\",\"source\":1,\"prescaler\":200,\"count\":192,"
                        + "\"timerReset\":true,\"placeReset\":true,\"deferred\":false,"
                        + "\"mapping\":[0,0,1]}}}}",
                "{\"result\":0,\"w\":{},\"e\":{}}"), Record.record(Fade.wide()));
    }

    /** The record of the fade: a start through the mapping of no parts
     *  records 0, and each remap its mapping, V15 to V0 numbered 1 to 16
     *  in the order the rows map through them (SPEC.md 1.9, 7.4). */
    @Test
    void aFadeRecordsARemapAFrame() {
        Tune fade = Fade.tune();
        List<String> record = Record.record(fade, Fade.ROWS + 5);
        assertEquals(1 + Fade.ROWS + 1, record.size(), "the record ends at the -1 line");
        assertTrue(record.get(0).startsWith("{\"rate\":50,\"timers\":[\"A\"],\"sources\":"
                + "[{\"rows\":[28,1,15],\"repeat\":0}],\"transformations\":[[0,1,2,3,"),
                record.get(0));
        assertTrue(record.get(0).contains(",13,14,15],[0,0,1,2,3,"),
                "V14 follows V15, 16 entries each: " + record.get(0));
        assertEquals(16, record.get(0).split("\\],\\[").length, "sixteen transformations");
        assertTrue(record.get(0).endsWith(",0,0,0]]}"), "V0 is last: " + record.get(0));
        assertTrue(record.get(1).endsWith(",\"deferred\":false,\"mapping\":0}}}}"),
                record.get(1));
        for (int n = 1; n <= 16; n++) {
            assertEquals("{\"result\":0,\"w\":{},\"e\":{\"A\":{\"remap\":{\"mapping\":[0,0,"
                    + n + "]}}}}", record.get(1 + n));
        }
        assertEquals("{\"result\":0,\"w\":{\"8\":0},\"e\":{\"A\":{\"stop\":{}}}}",
                record.get(Fade.ROWS));
        assertEquals("{\"result\":-1}", record.get(Fade.ROWS + 1));
    }

    /** A remap on a timer outside the timers of the tune is recorded as
     *  the row has it (SPEC.md 7.4), and leaves the timers as they are
     *  (1.10). */
    @Test
    void aRemapOnATimerOfNoStartIsRecorded() {
        Tune tune = new Tune("", "", "", 50, Tunes.once(List.of(
                new YMXS.Row(java.util.Map.of(), java.util.Map.of(YMXS.Timer.C,
                        new YMXS.Remap(Tunes.mapping(Fade.volumes(5))))))));
        List<String> record = Record.record(tune);
        assertTrue(record.get(0).startsWith("{\"rate\":50,\"timers\":[],\"sources\":[],"
                + "\"transformations\":[[0,0,0,1,"), record.get(0));
        assertEquals("{\"result\":0,\"w\":{},\"e\":{\"C\":{\"remap\":{\"mapping\":[1]}}}}",
                record.get(1));
    }
}
