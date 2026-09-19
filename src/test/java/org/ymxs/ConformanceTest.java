package org.ymxs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.ymxs.YMXS.Tune;

/**
 * The conformance kit under doc/conformance, checked against the reader.
 *
 * <p>Every tune of the kit is read here and recorded as SPEC.md 7 defines,
 * and the record is compared line for line with the file in the tree. A
 * record the tree does not have yet is written, and SOURCES.generated.md
 * beside the kit lists what SOURCES.md then has to say.
 */
class ConformanceTest {

    private static final Path KIT_AT = Path.of("doc/conformance");

    /**
     * One tune of the kit: the file, which tune of it the record is of, the
     * lines of its record - the first line and the frames SPEC.md 7.5
     * defines for an unnamed count - and what the tune reaches.
     */
    private record Source(String name, int tune, int lines, String reaches) {}

    private static final List<Source> KIT = List.of(
            new Source("four-rows", 1, 9,
                    "the example of SPEC.md 7.6: a square on Timer A started, retuned"
                            + " and stopped"),
            new Source("one-row", 1, 3, "one row, and the register columns it sets"),
            new Source("plays-once", 1, 6,
                    "a tune that plays once: the record ends with its -1 line (7.5)"),
            new Source("four-timers", 1, 13,
                    "the four timers, each started, one retuned and one stopped; two run"
                            + " through the wrap"),
            new Source("sources", 1, 11,
                    "three sources: one repeating to row 0, one to a row above it, one"
                            + " that plays once"),
            new Source("registers", 1, 7,
                    "every register column, R7's six bits, and a row that sets a register"
                            + " an effect runs on (6.1)"),
            new Source("wrap", 1, 9, "six rows repeating to row 4, so the record wraps"),
            new Source("several", 1, 8,
                    "targets of two and three registers, and the sources of two and"
                            + " three values a row they run (3.1.1, 3.2.1)"),
            new Source("multi", 2, 6,
                    "a multi of two tunes: the record is of the second, at a rate of its"));

    /** The tune the source names, read out of the kit. */
    private static Tune tune(Source source) throws IOException {
        return Tunes.tune(Text.read(Files.readString(
                KIT_AT.resolve("tunes").resolve(source.name() + ".json"))), source.tune());
    }

    @Test
    void theReaderWritesTheKit() throws IOException {
        List<String> rows = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        Files.createDirectories(KIT_AT.resolve("records"));
        for (Source source : KIT) {
            List<String> record = Record.record(tune(source), source.lines());
            assertEquals(source.lines(), record.size(),
                    source.name() + " records " + source.lines() + " lines (SPEC.md 7.5)");
            Path at = KIT_AT.resolve("records").resolve(source.name() + ".jsonl");
            String said = String.join("\n", record) + "\n";
            if (!Files.exists(at)) {
                Files.writeString(at, said, StandardCharsets.UTF_8);
                missing.add(source.name());
            } else {
                assertEquals(said, Files.readString(at, StandardCharsets.UTF_8),
                        at + " is the record of that tune");
            }
            rows.add("| `%s` | %d | %d | %s |".formatted(
                    source.name(), source.tune(), source.lines(), source.reaches()));
        }
        String said = """
                | tune | of the file | lines | what it reaches |
                |---|---|---|---|
                """ + String.join("\n", rows) + "\n";
        String sources = Files.readString(KIT_AT.resolve("SOURCES.md"), StandardCharsets.UTF_8);
        if (!sources.contains(said)) {
            Files.writeString(KIT_AT.resolve("SOURCES.generated.md"), said,
                    StandardCharsets.UTF_8);
        }
        assertTrue(missing.isEmpty(), "the kit did not have the record of " + missing
                + ", and this run wrote it");
        assertTrue(sources.contains(said), "SOURCES.md has another table than this run"
                + " writes: SOURCES.generated.md beside the kit has the rows");
    }
}
