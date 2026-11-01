package org.ymxs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.ymxs.YMXS.Multi;
import org.ymxs.YMXS.Transformation;
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
            new Source("several", 1, 9,
                    "targets of two and three registers, and the sources of two and"
                            + " three values a row they run (3.1.1, 3.2.1)"),
            new Source("multi", 2, 6,
                    "a multi of two tunes: the record is of the second, at 60 Hz where the"
                            + " first is at 50"),
            new Source("deferred", 1, 17,
                    "a deferred start, recorded at the frame of its row (3.5, 7.4)"),
            new Source("transformations", 1, 12,
                    "a start through a transformation, a fade of remaps, a remap at the"
                            + " repeat row, a target of three registers through a mapping,"
                            + " transformations of 16 entries and of 256, keys above the most"
                            + " of a register, two transformations of one name, equal entries"
                            + " under two names (3.6.2), and a remap on a timer outside the"
                            + " tune (4.3); the file lists the transformations in another"
                            + " order than 1.9 numbers them (3.6, 7.4)"));

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
        // An implementer receives TASK.md, so a tune the kit has and that
        // table leaves out is a tune a run produces no record for.
        String task = Files.readString(KIT_AT.resolve("TASK.md"), StandardCharsets.UTF_8);
        for (Source source : KIT) {
            assertTrue(task.contains("| `%s.json` | %d | %d |".formatted(
                            source.name(), source.tune(), source.lines())),
                    "TASK.md has no line count for " + source.name());
        }
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

    /** The tunes a check reports a warning on, as SOURCES.md and TASK.md
     *  list them. */
    @Test
    void theKitListsTheTunesWithAWarning() throws IOException {
        List<String> warned = new ArrayList<>();
        for (Source source : KIT) {
            Multi multi = Text.read(Files.readString(
                    KIT_AT.resolve("tunes").resolve(source.name() + ".json")));
            if (multi.tunes().stream().anyMatch(tune -> !Check.writing(tune).isEmpty())) {
                warned.add(source.name());
            }
        }
        String sources = prose("SOURCES.md");
        String listed = listed(warned, "`%s`");
        assertTrue(sources.contains("reports a warning on " + listed + ", and every other"),
                "SOURCES.md has another list than " + listed);
        String task = prose("TASK.md");
        listed = listed(warned, "`%s.json`");
        assertTrue(task.contains(listed + " break a rule"),
                "TASK.md has another list than " + listed);
    }

    /** The lengths of the transformations of the kit, as SOURCES.md and
     *  README.md list them. */
    @Test
    void theKitListsTheLengthsOfItsTransformations() throws IOException {
        TreeSet<Integer> lengths = new TreeSet<>();
        for (Source source : KIT) {
            Multi multi = Text.read(Files.readString(
                    KIT_AT.resolve("tunes").resolve(source.name() + ".json")));
            for (Tune tune : multi.tunes()) {
                for (Transformation each : Tunes.transformations(tune)) {
                    lengths.add(each.entries().size());
                }
            }
        }
        assertEquals(2, lengths.size(), "the transformations of the kit have two lengths: "
                + lengths);
        String listed = "transformations of %d entries and of %d".formatted(
                lengths.first(), lengths.last());
        assertTrue(prose("SOURCES.md").contains(listed),
                "SOURCES.md has other lengths than " + listed);
        assertTrue(prose("README.md").contains(listed),
                "README.md has other lengths than " + listed);
    }

    /** The number of tunes of the kit, a file each under tunes/, against
     *  the two sentences of README.md with it as a word: the kit is that
     *  many tunes, and that many reach the four timers. */
    @Test
    void theReadmeHasTheNumberOfTunesOfTheKit() throws IOException {
        try (Stream<Path> files = Files.list(KIT_AT.resolve("tunes"))) {
            assertEquals(KIT.size(), files.filter(one -> one.toString().endsWith(".json"))
                    .count(), "tunes/ has a file a tune of the kit");
        }
        String word = Words.of(KIT.size());
        String readme = prose("README.md");
        assertTrue(readme.contains("The kit is " + word + " tunes"),
                "README.md has another number of tunes than " + word);
        assertTrue(readme.contains(word + " reach the four timers"),
                "README.md has another number of tunes reaching the four timers than "
                        + word);
    }

    /** A document of the kit, each run of white space one space. */
    private static String prose(String name) throws IOException {
        return Files.readString(KIT_AT.resolve(name), StandardCharsets.UTF_8)
                .replaceAll("\\s+", " ");
    }

    /** The names as a list in prose: a, b and c. */
    private static String listed(List<String> names, String format) {
        List<String> each = names.stream().map(format::formatted).toList();
        return each.size() < 2 ? String.join("", each)
                : String.join(", ", each.subList(0, each.size() - 1))
                        + " and " + each.get(each.size() - 1);
    }
}
