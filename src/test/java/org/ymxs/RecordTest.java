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
}
