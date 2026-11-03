package org.ymxs.tool;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The two trees against each other, byte for byte.
 *
 * <p>One input has one output. Java and Go read the same dumps, write the
 * same JSON, cross to CSV and back the same way and report the same
 * faults, so a caller reading either has the same bytes at every step.
 *
 * <p>The tools are run as a caller runs them: the input on standard input,
 * the output on standard output. What is compared is standard output and
 * the exit; a report is prose and is compared where the two write one.
 *
 * <p>Skipped where Go is not installed, or where the Java tools have not
 * been built.
 */
final class ParityTest {

    /** The five tools, by the name each is called by. */
    private static final List<String> TOOLS = List.of("ym-to-ymxs", "ymxs-check",
            "ymxs-csv-to-json", "ymxs-json-to-csv", "ymxs-merge");

    /** The tools whose input is the JSON form (json.md), which alone report
     *  the conditions of json.md 8.1. */
    private static final List<String> READ_JSON =
            List.of("ymxs-check", "ymxs-json-to-csv", "ymxs-merge");

    /** The line of tools.md 7.4 between the tool's name and the text, read
     *  out of the document; the CSV emitter writes the text as a name in a
     *  reported line (csv.md 5.4). */
    private static final String UNWRITABLE = unwritable();

    /** The indented line after the heading of tools.md 7.4, its tool's name
     *  and its {@code <value>} cut. */
    private static String unwritable() {
        String tools;
        try {
            tools = Files.readString(Path.of("doc/tools.md"));
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        String tool = "ymxs-json-to-csv: ";
        String value = "<value>";
        int at = tools.indexOf("**7.4**");
        int line = tools.indexOf("\n    " + tool, at);
        if (at < 0 || line < 0) {
            throw new IllegalStateException("tools.md 7.4 has no line of " + tool);
        }
        String said = tools.substring(line + 5, tools.indexOf('\n', line + 1));
        if (!said.endsWith(value)) {
            throw new IllegalStateException("tools.md 7.4's line ends other than " + value);
        }
        return said.substring(tool.length(), said.length() - value.length());
    }

    /** Where the Go tools are built, once for every test here. */
    private static @Nullable Path built;

    @BeforeAll
    static void buildTheGoTree() throws IOException, InterruptedException {
        Assumptions.assumeTrue(onThePath("go"), "no go on the path");
        Assumptions.assumeTrue(PipeTest.theToolsAreBuilt(), "the Java tools are not built");
        Path into = Files.createTempDirectory("ymxs-go");
        Process ran = new ProcessBuilder("go", "build", "-o", into + "/", "./cmd/...")
                .directory(Path.of("go").toFile())
                .redirectErrorStream(true)
                .start();
        String said = new String(ran.getInputStream().readAllBytes());
        assertEquals(0, ran.waitFor(), "the Go tree builds: " + said);
        built = into;
    }

    private static boolean onThePath(String tool) {
        try {
            return new ProcessBuilder(tool, "version").redirectErrorStream(true)
                    .start().waitFor() == 0;
        } catch (IOException | InterruptedException no) {
            return false;
        }
    }

    /** What one run came to. */
    private record Ran(int exit, byte[] out, String said) { }

    /** {@code tool} run on {@code in}, out of the tree {@code where} names. */
    private static Ran ran(Path where, String tool, byte[] in, String... flags)
            throws IOException, InterruptedException {
        Path input = Files.createTempFile("in", ".bin");
        Files.write(input, in);
        List<String> command = new ArrayList<>(List.of(where.resolve(tool).toString()));
        command.addAll(List.of(flags));
        Process ran = new ProcessBuilder(command).redirectInput(input.toFile()).start();
        byte[] out = ran.getInputStream().readAllBytes();
        String said = new String(ran.getErrorStream().readAllBytes());
        return new Ran(ran.waitFor(), out, said);
    }

    /** The same call in both trees, which must come to the same bytes. */
    private static byte[] both(String tool, byte[] in, String... flags) throws Exception {
        Ran java = ran(Path.of("bin"), tool, in, flags);
        Ran go = ran(built(), tool, in, flags);
        assertEquals(java.exit(), go.exit(), tool + " exits the same: " + java.said()
                + " | " + go.said());
        assertArrayEquals(java.out(), go.out(), tool + " writes the same bytes");
        assertEquals(java.said(), go.said(), tool + " reports the same");
        return java.out();
    }

    private static Path built() {
        Path where = built;
        if (where == null) {
            throw new IllegalStateException("the Go tools are not built");
        }
        return where;
    }

    private static byte[] file(String named) throws IOException {
        return Files.readAllBytes(Path.of(named));
    }

    @Test
    void aDumpReadsTheSameInBothTrees() throws Exception {
        byte[] dump = file("src/test/resources/packed.ym");
        byte[] json = both("ym-to-ymxs", dump);
        assertTrue(json.length > 0, "a dump converts");
        both("ym-to-ymxs", dump, "-r");
        both("ym-to-ymxs", dump, "-r2");
    }

    /** The flags of ym-to-ymxs read the same in both trees (tools.md 9.2):
     *  the last -rROW sets the repeat row, before or after -r, and a ROW
     *  outside 32 bits is a wrong call. */
    @Test
    void theFlagsOfYmToYmxsReadTheSameInBothTrees() throws Exception {
        byte[] dump = file("src/test/resources/packed.ym");
        for (List<String> flags : List.of(List.of("-r", "-r2"), List.of("-r2", "-r"),
                List.of("-r1", "-r2"), List.of("-r2", "-r", "-r1"),
                List.of("-r2147483647"))) {
            String json = new String(both("ym-to-ymxs", dump, flags.toArray(String[]::new)),
                    StandardCharsets.UTF_8);
            String row = flags.stream().filter(one -> one.length() > 2)
                    .reduce((one, two) -> two).orElseThrow().substring(2);
            assertTrue(json.contains("\"repeat\": " + row + ","), flags + " repeats to row "
                    + row + ": " + json);
        }
        for (String flag : List.of("-r2147483648", "-r99999999999")) {
            Ran java = ran(Path.of("bin"), "ym-to-ymxs", dump, flag);
            Ran go = ran(built(), "ym-to-ymxs", dump, flag);
            assertEquals(2, java.exit(), java.said());
            assertEquals("ym-to-ymxs: " + flag + " is not a row number", java.said().strip());
            assertEquals(java.exit(), go.exit(), "ym-to-ymxs exits the same: " + go.said());
            assertArrayEquals(java.out(), go.out(), "ym-to-ymxs writes the same bytes");
            assertEquals(java.said(), go.said(), "ym-to-ymxs reports the same");
        }
    }

    /** Every JSON tune of the documents, found rather than listed: a
     *  list is a place a tune added later is not. */
    private static List<Path> tunes() throws IOException {
        try (java.util.stream.Stream<Path> at = Files.list(Path.of("doc/tunes"))) {
            return at.filter(one -> one.toString().endsWith(".json")).sorted().toList();
        }
    }

    @Test
    void everyTuneOfTheDocumentsCrossesBothWaysTheSame() throws Exception {
        List<Path> tunes = tunes();
        assertTrue(tunes.size() >= 7, () -> "doc/tunes has " + tunes.size()
                + " tunes as JSON; the check is asleep");
        for (Path named : tunes) {
            byte[] json = file(named.toString());
            byte[] csv = both("ymxs-json-to-csv", json);
            both("ymxs-csv-to-json", csv);
            both("ymxs-check", json);
        }
    }

    /** A count of 1 is followed by the singular noun (tools.md 1.4): a
     *  tune of one row through the three tools that read a tune, and a
     *  YM3! dump of one frame, 14 zero bytes after the format, through
     *  ym-to-ymxs. */
    @Test
    void aCountOfOneIsSingularInBothTrees() throws Exception {
        byte[] json = file("doc/conformance/tunes/one-row.json");
        byte[] csv = both("ymxs-json-to-csv", json);
        both("ymxs-csv-to-json", csv);
        both("ymxs-check", json);
        byte[] dump = new byte[4 + 14];
        System.arraycopy("YM3!".getBytes(StandardCharsets.US_ASCII), 0, dump, 0, 4);
        both("ym-to-ymxs", dump);
        for (Ran run : List.of(ran(Path.of("bin"), "ymxs-json-to-csv", json),
                ran(Path.of("bin"), "ymxs-csv-to-json", csv),
                ran(Path.of("bin"), "ymxs-check", json))) {
            assertTrue(run.said().contains(": 1 tune, 1 row, "), run.said());
        }
        String read = ran(Path.of("bin"), "ym-to-ymxs", dump).said();
        assertTrue(read.contains(", 1 row at 50 Hz, 0 sources, timers []"), read);
    }

    /**
     * Values one tree read and the other reported, now reported by both: a
     * JSON {@code version}, {@code rows}, {@code rate} or {@code repeat}
     * outside 32 bits (json.md 1.4), a JSON {@code repeat} of -1 on a tune
     * or a source (SPEC.md 1.11), and a CSV {@code timerReset} or
     * {@code placeReset} cell that is not a whole number (csv.md 5.2).
     */
    @Test
    void aValueOneTreeReadIsOneLineInBothTrees() throws Exception {
        String json = new String(file("doc/tunes/example.json"), StandardCharsets.UTF_8);
        String csv = new String(file("doc/tunes/example.csv"), StandardCharsets.UTF_8);
        String whole = ", and this form requires a whole number";
        String row = ", and this form requires a row number or null";
        String tuneRepeat = "\"repeat\": 0,\n";
        String sourceRepeat = "\"name\": \"square 13\", \"repeat\": 0,";
        String start = "0,0,8,1,50,60,1,1";
        String retune = "1,1,,,50,61,0,0";
        for (String each : List.of(tuneRepeat, sourceRepeat, "\"version\": 7,",
                "\"rows\": 4,", "\"rate\": 50,")) {
            assertTrue(json.contains(each), "the example has " + each);
        }
        assertTrue(csv.contains(start) && csv.contains(retune), "the example starts and retunes");
        for (Wrong wrong : List.of(
                new Wrong("ymxs-check", json.replace("\"version\": 7,",
                        "\"version\": 4294967300,"), "version is 4294967300" + whole),
                new Wrong("ymxs-check", json.replace("\"rows\": 4,",
                        "\"rows\": 4294967300,"), "rows is 4294967300" + whole),
                new Wrong("ymxs-check", json.replace("\"rate\": 50,",
                        "\"rate\": 4294967346,"), "rate is 4294967346" + whole),
                new Wrong("ymxs-check", json.replace(tuneRepeat,
                        "\"repeat\": 4294967296,\n"), "repeat is 4294967296" + row),
                new Wrong("ymxs-check", json.replace(sourceRepeat,
                        "\"name\": \"square 13\", \"repeat\": 4294967296,"),
                        "repeat is 4294967296" + row),
                new Wrong("ymxs-check", json.replace(tuneRepeat, "\"repeat\": -1,\n"),
                        "tune 1: the tune has 4 rows and repeats to row -1"),
                new Wrong("ymxs-check", json.replace(sourceRepeat,
                        "\"name\": \"square 13\", \"repeat\": -1,"),
                        "tune 1: row 0: Timer A: the source has 2 rows and repeats to row -1"),
                new Wrong("ymxs-csv-to-json", csv.replace(start, "0,0,8,1,50,60,x,1"),
                        "true or false is \"x\"" + whole),
                new Wrong("ymxs-csv-to-json", csv.replace(start, "0,0,8,1,50,60,1,"),
                        "true or false is \"\"" + whole),
                new Wrong("ymxs-csv-to-json", csv.replace(retune, "1,1,,,50,61,yes,0"),
                        "true or false is \"yes\"" + whole))) {
            wrongInBothTrees(wrong);
        }
    }

    @Test
    void severalTunesMergeTheSame() throws Exception {
        byte[] one = file("doc/tunes/circus.json");
        byte[] two = file("doc/tunes/digidrum.json");
        byte[] together = new byte[one.length + two.length];
        System.arraycopy(one, 0, together, 0, one.length);
        System.arraycopy(two, 0, together, one.length, two.length);
        byte[] merged = both("ymxs-merge", together);
        assertTrue(merged.length > one.length, "two tunes in one multi");
    }

    /** A tune of version 3 in CSV: one value a row, and R8 its target. */
    private static final String THREE_CSV = """
            ### multi
            format,version,tunes
            ymxs,3,1

            ### tune
            title,composer,writer,rate,rows,repeat
            ,,t,50,2,0

            ### source
            name,repeat
            a tone,0

            ### value
            row,value
            0,13
            1,0

            ### timerA
            row,shape,source,target,prescaler,count,timerReset,placeReset
            0,0,1,8,50,60,1,1
            """;

    /** The same tune in JSON. */
    private static final String THREE_JSON = """
            {"format":"ymxs","version":3,"tunes":[{"title":"","composer":"",
            "writer":"t","rate":50,"rows":2,"repeat":0,
            "sources":[{"name":"a tone","repeat":0,"values":[13,0]}],
            "timerA":{"shape":[0,-1],"target":[8,-1],"source":[1,-1],
            "prescaler":[50,-1],"count":[60,-1],"timerReset":[1,-1],"placeReset":[1,-1]}}]}
            """;

    /** One input of version 3 in another shape, and the line a tool
     *  reports for it. */
    private record Wrong(String tool, String in, String line) { }

    /**
     * A file of version 3 has one value a row in every source and a target
     * of 0 to 13 (json.md 2.4). Such a file reads the same in both trees, in
     * both forms, and a row of several values or a target above 13 is one
     * error of the form, the same line and exit in both trees (json.md 8.1,
     * csv.md 5.1).
     */
    @Test
    void aFileOfVersion3IsReadTheSameInBothTrees() throws Exception {
        both("ymxs-csv-to-json", THREE_CSV.getBytes());
        both("ymxs-check", THREE_JSON.getBytes());
        both("ymxs-json-to-csv", THREE_JSON.getBytes());
        String several = "source a tone has a row of several values, and version 3 has"
                + " one value a row";
        String target = "target 14, and version 3 reaches 0 to 13";
        for (Wrong wrong : List.of(
                new Wrong("ymxs-csv-to-json", THREE_CSV.replace("row,value\n0,13\n1,0",
                        "row,value1,value2\n0,13,1\n1,0,1"), several),
                new Wrong("ymxs-csv-to-json", THREE_CSV.replace("0,0,1,8,", "0,0,1,14,"),
                        target),
                new Wrong("ymxs-check", THREE_JSON.replace("\"values\":[13,0]",
                        "\"values\":[[13,1],[0,1]]"), several),
                new Wrong("ymxs-check", THREE_JSON.replace("\"target\":[8,-1]",
                        "\"target\":[14,-1]"), target))) {
            wrongInBothTrees(wrong);
        }
    }

    /** A target outside 0 to 24 in a file of version 4 is one error of the
     *  form, the same line in both trees and both forms (json.md 8.1,
     *  csv.md 5.1). */
    @Test
    void aTargetOutside0To24IsOneLineInBothTrees() throws Exception {
        String line = "no target 30: a tune reaches 0 to 24";
        wrongInBothTrees(new Wrong("ymxs-csv-to-json", THREE_CSV
                .replace("ymxs,3,1", "ymxs,4,1").replace("0,0,1,8,", "0,0,1,30,"), line));
        wrongInBothTrees(new Wrong("ymxs-check", THREE_JSON
                .replace("\"version\":3", "\"version\":4")
                .replace("\"target\":[8,-1]", "\"target\":[30,-1]"), line));
    }

    /**
     * A deferred start reads the same in both trees (SPEC.md 3.5): the
     * check of {@code doc/tunes/deferred.json} with R8 set at rows 5 and 6
     * reports the square's run to row 6 by the reckoning and the fall's
     * from there (6.3, 6.4); a reader skips the {@code deferred} column of
     * a file of version 4, and a start of version 7 without it is one
     * error of the form (json.md 2.4, 8.1; csv.md 5.2).
     */
    @Test
    void aDeferredStartReadsTheSameInBothTrees() throws Exception {
        String json = new String(file("doc/tunes/deferred.json"), StandardCharsets.UTF_8);
        String csv = new String(both("ymxs-json-to-csv", json.getBytes()),
                StandardCharsets.UTF_8);
        String r7 = "\"r7\": [62,-1,-1,-1,-1,-1,-1,-1]";
        String deferred = "\"deferred\": [0,-1,-1,-1,1,-1,-1,-1]";
        String cells = ",placeReset,deferred,mapping\n";
        assertTrue(json.contains(r7) && json.contains(deferred) && csv.contains(cells),
                "the example sets R7 alone and defers the start at row 4");
        byte[] sets = json.replace(r7, r7 + ",\n        \"r8\": [-1,-1,-1,-1,-1,12,12,-1]")
                .getBytes();
        both("ymxs-check", sets);
        String said = ran(Path.of("bin"), "ymxs-check", sets).said();
        String reckoned = ", which rests on how long a source that plays once runs,"
                + " reckoned from its rate";
        assertTrue(said.contains("row 5: Timer A runs on R8, and this row sets it" + reckoned)
                && said.contains("row 6: Timer A runs on R8, and this row sets it" + reckoned),
                said);
        both("ymxs-check", json.replace("\"version\": 7", "\"version\": 4").getBytes());
        both("ymxs-csv-to-json", csv.replace("ymxs,7,1", "ymxs,4,1").getBytes());
        wrongInBothTrees(new Wrong("ymxs-check", json.replace(",\n        " + deferred, ""),
                "Timer A has no \"deferred\" column"));
        wrongInBothTrees(new Wrong("ymxs-csv-to-json", csv.replace(cells, ",placeReset\n"),
                "true or false is \"\", and this form requires a whole number"));
    }

    /** The first {@code entries} entries of {@code half}, entry k the key
     *  k divided by 2, separated by commas. */
    private static String halves(int entries) {
        StringBuilder out = new StringBuilder();
        for (int key = 0; key < entries; key++) {
            out.append(key > 0 ? "," : "").append(key / 2);
        }
        return out.toString();
    }

    /** A tune of version 7 in JSON: two rows, a square on R8 started at
     *  row 0 through transformation 1, {@code half} of 256 entries, and
     *  remapped at row 1 through the mapping of no parts. */
    private static final String SEVEN_JSON = """
            {"format":"ymxs","version":7,"tunes":[{"title":"","composer":"",
            "writer":"t","rate":50,"rows":2,"repeat":0,
            "sources":[{"name":"a tone","repeat":0,"values":[13,0]}],
            "transformations":[{"name":"half","entries":[HALVES]}],
            "timerA":{"shape":[0,3],"target":[8,-1],"source":[1,-1],
            "prescaler":[50,-1],"count":[60,-1],"timerReset":[1,-1],"placeReset":[1,-1],
            "deferred":[0,-1],"mapping":[[1],0]}}]}
            """.replace("HALVES", halves(256));

    /**
     * A tune through transformations reads the same in both trees:
     * {@code doc/tunes/fade.json} and the kit's {@code transformations.json}
     * through the check, to CSV and back, and the two through ymxs-merge
     * (SPEC.md 3.6; json.md 3.7, 6.7; csv.md 3.9, 3.11). The kit's file
     * lists its transformations in another order than SPEC.md 1.9 numbers
     * them, and the JSON written back numbers them by first use (json.md
     * 3.8). A transformation of the empty name crosses to CSV as a row
     * line of {@code ""} and back (csv.md 2.5, 6.4).
     */
    @Test
    void aTuneThroughTransformationsReadsTheSameInBothTrees() throws Exception {
        byte[] fade = file("doc/tunes/fade.json");
        byte[] kit = file("doc/conformance/tunes/transformations.json");
        for (byte[] json : List.of(fade, kit)) {
            both("ymxs-check", json);
            byte[] csv = both("ymxs-json-to-csv", json);
            assertTrue(new String(csv, StandardCharsets.UTF_8)
                    .contains("\n### transformation\nname\nV15\n"), "the CSV opens V15");
            byte[] back = both("ymxs-csv-to-json", csv);
            assertArrayEquals(both("ymxs-merge", json), back, "the JSON through CSV is the"
                    + " JSON written again");
        }
        assertArrayEquals(fade, both("ymxs-csv-to-json", both("ymxs-json-to-csv", fade)),
                "fade.json crosses to CSV and back byte for byte");
        String renumbered = new String(both("ymxs-merge", kit), StandardCharsets.UTF_8);
        assertTrue(renumbered.indexOf("\"name\": \"V15\"") < renumbered.indexOf(
                "\"name\": \"V0\""), "V15 is transformation 1 by first use: " + renumbered);
        byte[] together = new byte[fade.length + kit.length];
        System.arraycopy(fade, 0, together, 0, fade.length);
        System.arraycopy(kit, 0, together, fade.length, kit.length);
        both("ymxs-merge", together);

        String nameless = SEVEN_JSON.replace("\"name\":\"half\"", "\"name\":\"\"");
        byte[] csv = both("ymxs-json-to-csv", nameless.getBytes(StandardCharsets.UTF_8));
        assertTrue(new String(csv, StandardCharsets.UTF_8)
                .contains("### transformation\nname\n\"\"\n"), "the empty name is \"\"");
        assertArrayEquals(csv, both("ymxs-json-to-csv", both("ymxs-csv-to-json", csv)),
                "the CSV crosses to JSON and back byte for byte");
    }

    /**
     * Each condition of json.md 8.1 that version 7 adds is one line in
     * both trees, X the value as JSON text, each number in it as written
     * in the file; and a reader skips the transformations and the mapping
     * column of a file of version 6 (json.md 2.4). A transformation name
     * with a line feed, and one with a carriage return, is the one line of
     * tools.md 7.4, the line feed written as the two characters {@code \n}
     * and the carriage return as {@code \r}.
     */
    @Test
    void eachErrorOfTheJsonFormOfVersion7IsOneLineInBothTrees() throws Exception {
        String json = SEVEN_JSON;
        both("ymxs-check", json.getBytes(StandardCharsets.UTF_8));
        String transformations = "\"transformations\":[{\"name\":\"half\",\"entries\":["
                + halves(256) + "]}],";
        String other = "{\"name\":\"other\",\"entries\":[" + halves(256) + "]}";
        String sources = "\"sources\":[{\"name\":\"a tone\",\"repeat\":0,\"values\":[13,0]}";
        String entries = "\"entries\":[0,0,1,";
        String mapping = "\"mapping\":[[1],0]";
        String shape = "\"shape\":[0,3]";
        for (String each : List.of(transformations, sources, entries, mapping, shape)) {
            assertTrue(json.contains(each), "the tune has " + each);
        }
        assertTrue(both("ymxs-check", json.replace("\"version\":7", "\"version\":6")
                .replace(shape, "\"shape\":[0,-1]").replace(entries, "\"entries\":[\"x\",")
                .replace(mapping, "\"mapping\":\"x\"").getBytes(StandardCharsets.UTF_8))
                .length > 0, "a reader skips the transformations and the mapping column of a"
                + " file of version 6");
        String array = ", and this form requires an array";
        String requires = ", and this form requires 0 or an array of whole numbers";
        String identity = ", and a mapping of the identity in every part is 0";
        String unused = "transformation 2, other, is used by no row, and a transformation a"
                + " tune does not map through is dropped where this form is read";
        for (Wrong wrong : List.of(
                new Wrong("ymxs-check", json.replace("\"version\":7", "\"version\":8"),
                        "version 8, and this reads 3, 4, 5, 6 or 7"),
                new Wrong("ymxs-check", json.replace(transformations, ""),
                        "transformations is null" + array),
                new Wrong("ymxs-check", json.replace(transformations,
                        "\"transformations\":{\"a\": 1.50},"),
                        "transformations is {\"a\":1.50}" + array),
                new Wrong("ymxs-check", json.replace(transformations,
                        "\"transformations\":[5],"), "entries is null" + array),
                new Wrong("ymxs-check", json.replace(transformations,
                        "\"transformations\":[{\"name\":\"half\",\"entries\":\"x\"}],"),
                        "entries is \"x\"" + array),
                new Wrong("ymxs-check", json.replace("\"name\":\"half\",", ""),
                        "name is null, and this form requires a text"),
                new Wrong("ymxs-check", json.replace(entries, "\"entries\":[0,1e1,1,"),
                        "half at entry 1 is 1e1, and this form requires a whole number"),
                new Wrong("ymxs-check", json.replace("\"version\":7", "\"version\":6"),
                        "row 1 sets shape 3 on Timer A, and a shape is -1, 0, 1 or 2"),
                new Wrong("ymxs-check", json.replace(shape, "\"shape\":[0,4]"),
                        "row 1 sets shape 4 on Timer A, and a shape is -1, 0, 1, 2 or 3"),
                new Wrong("ymxs-check", json.replace("," + mapping, ""),
                        "Timer A has no \"mapping\" column"),
                new Wrong("ymxs-check", json.replace(mapping, "\"mapping\":0"),
                        "Timer A's mapping is 0, and this form requires a column"),
                new Wrong("ymxs-check", json.replace(mapping, "\"mapping\":[[1]]"),
                        "Timer A's mapping is 1 values long, and the tune has 2 rows"),
                new Wrong("ymxs-check", json.replace(mapping, "\"mapping\":[[1, 1.50],0]"),
                        "Timer A's mapping at row 0 is [1,1.50]" + requires),
                new Wrong("ymxs-check", json.replace(mapping, "\"mapping\":[[1],\"x\"]"),
                        "Timer A's mapping at row 1 is \"x\"" + requires),
                new Wrong("ymxs-check", json.replace(mapping, "\"mapping\":[[],0]"),
                        "Timer A's mapping at row 0 is []" + identity),
                new Wrong("ymxs-check", json.replace(mapping, "\"mapping\":[[1],[-0, 0]]"),
                        "Timer A's mapping at row 1 is [-0,0]" + identity),
                new Wrong("ymxs-check", json.replace(mapping, "\"mapping\":[[2],0]"),
                        "row 0 maps through transformation 2, and the tune has 1"),
                new Wrong("ymxs-check", json.replace(transformations,
                        transformations.replace("]}],", "]}," + other + "],")), unused),
                new Wrong("ymxs-check", json.replace(sources, sources
                        + ",{\"name\":\"idle\",\"values\":[1]}").replace(transformations,
                        transformations.replace("]}],", "]}," + other + "],")),
                        "source 2, idle, is started by no row, and a source a tune does not"
                                + " run is dropped where this form is read\n" + unused),
                new Wrong("ymxs-json-to-csv", json.replace("\"name\":\"half\"",
                        "\"name\":\"ha\\nlf\""), UNWRITABLE + "ha\\nlf"),
                new Wrong("ymxs-json-to-csv", json.replace("\"name\":\"half\"",
                        "\"name\":\"ha\\rlf\""), UNWRITABLE + "ha\\rlf"))) {
            wrongInBothTrees(wrong);
        }
    }

    /** The {@code transformations} of {@code SEVEN_JSON}, one
     *  transformation named {@code name} of the entries {@code entries}. */
    private static String transformation(String name, String entries) {
        return "\"transformations\":[{\"name\":\"" + name + "\",\"entries\":[" + entries
                + "]}],";
    }

    /**
     * Compares structure errors across both trees (SPEC.md 1.11, 1.12):
     * invalid transformation sizes and entries, keys beyond the last
     * entry, incorrect mapping widths, mapped values beyond register
     * limits, and remaps before or after their starts.
     *
     * <p>Dependent key checks are skipped after an invalid entry count.
     * Start key errors precede mapped-value errors; remap values are
     * reported once per connection, in row order, with the start-row
     * prefix. Valid tables include 255 entries and two differently
     * sized tables under one name.
     */
    @Test
    void eachErrorOfTheStructureOfVersion7IsOneLineInBothTrees() throws Exception {
        String json = SEVEN_JSON;
        String entries = "\"entries\":[0,0,1,1,2,";
        String values = "\"values\":[13,0]";
        String mapping = "\"mapping\":[[1],0]";
        String timer = "\"timerA\":{\"shape\":[0,3],\"target\":[8,-1],\"source\":[1,-1],\n"
                + "\"prescaler\":[50,-1],\"count\":[60,-1],\"timerReset\":[1,-1],"
                + "\"placeReset\":[1,-1],\n\"deferred\":[0,-1],\"mapping\":[[1],0]}";
        String half = transformation("half", halves(256));
        for (String each : List.of(entries, values, mapping, timer, half)) {
            assertTrue(json.contains(each), "the tune has " + each);
        }
        String sixteen = json.replace(half, transformation("half", halves(16)));
        String remapped = "\"mapping\":[[1],[1]]";
        String twice = json.replace(values, "\"values\":[300,0]").replace(mapping, remapped);
        String before = "\"timerA\":{\"shape\":[3,0],\"target\":[-1,8],\"source\":[-1,1],"
                + "\"prescaler\":[-1,50],\"count\":[-1,60],\"timerReset\":[-1,1],"
                + "\"placeReset\":[-1,1],\"deferred\":[-1,0],";
        StringBuilder up = new StringBuilder();
        for (int key = 0; key < 16; key++) {
            up.append(key > 0 ? "," : "").append(key + 40);
        }
        String at0 = "tune 1: row 0: Timer A: ";
        String at1 = "tune 1: row 1: Timer A: ";
        String remap0 = at0 + "start of row 1: ";
        String remap1 = at1 + "start of row 0: ";
        String counted = "tune 1: transformation 1, half: the transformation has ";
        String most = ", and the target is 0 to 31";
        String parts = "a mapping of 2 parts on setR8, which reads 1";
        String key15 = "a source on setR8 whose row 0 is 16, and a key is 0 to 15";
        for (Wrong wrong : List.of(
                new Wrong("ymxs-check", json.replace(entries, "\"entries\":[0,1,300,2,"),
                        "tune 1: transformation 1, half: entry 2 is 300, and an entry is 0 to"
                                + " 255"),
                new Wrong("ymxs-check", twice.replace(half,
                        transformation("half", halves(256) + ",300")),
                        counted + "257 entries, and a transformation has 1 to 256\n"
                                + "tune 1: transformation 1, half: entry 256 is 300, and an"
                                + " entry is 0 to 255"),
                new Wrong("ymxs-check", twice.replace(half, transformation("half", "")),
                        counted + "0 entries, and a transformation has 1 to 256"),
                new Wrong("ymxs-check", json.replace(values, "\"values\":[300,0]"),
                        at0 + "a source on setR8 whose row 0 is 300, and a key is 0 to 255\n"
                                + remap1 + "value 0 of row 0 maps to 300 on setR8" + most),
                new Wrong("ymxs-check", sixteen.replace(values, "\"values\":[16,0]"),
                        at0 + key15),
                new Wrong("ymxs-check", sixteen.replace(values, "\"values\":[16,0]")
                        .replace(mapping, "\"mapping\":[0,[1]]"), remap1 + key15),
                new Wrong("ymxs-check", sixteen.replace(values, "\"values\":[16,0]")
                        .replace(timer, before + mapping + "}"), remap0 + key15),
                new Wrong("ymxs-check", json.replace(half, transformation("up", up.toString()))
                        .replace(values, "\"values\":[0,16]")
                        .replace(mapping, "\"mapping\":[0,[1]]"),
                        remap1 + "value 0 of row 0 maps to 40 on setR8" + most + "\n"
                                + remap1 + "a source on setR8 whose row 1 is 16, and a key is 0"
                                + " to 15"),
                new Wrong("ymxs-check", json.replace(half, transformation("one", "40"))
                        .replace(values, "\"values\":[0,13]"),
                        at0 + "a source on setR8 whose row 1 is 13, and a key is 0 to 0\n"
                                + at0 + "value 0 of row 0 maps to 40 on setR8" + most),
                new Wrong("ymxs-check", json.replace(mapping, "\"mapping\":[[1,1],[1,0]]"),
                        at0 + parts + "\n" + at1 + parts),
                new Wrong("ymxs-check", json.replace(values, "\"values\":[100,0]"),
                        at0 + "value 0 of row 0 maps to 50 on setR8" + most + "\n"
                                + remap1 + "value 0 of row 0 maps to 100 on setR8" + most),
                new Wrong("ymxs-check", json.replace(timer, before
                        + "\"mapping\":[[1,1],[1]]}"), at0 + parts))) {
            wrongInBothTrees(wrong);
        }

        assertTrue(both("ymxs-check", json.replace(half, transformation("half", halves(255)))
                .getBytes(StandardCharsets.UTF_8)).length > 0,
                "a transformation of 255 entries, each 0 to 255, reads clean");
        String two = json.replace(half, "\"transformations\":[{\"name\":\"half\",\"entries\":["
                + halves(16) + "]},{\"name\":\"half\",\"entries\":[" + halves(256) + "]}],")
                .replace(mapping, "\"mapping\":[[1],[2]]");
        assertTrue(both("ymxs-check", two.getBytes(StandardCharsets.UTF_8)).length > 0,
                "a table of 16 entries and one of 256, both named half, read clean");
        String merged = new String(both("ymxs-merge", two.getBytes(StandardCharsets.UTF_8)),
                StandardCharsets.UTF_8);
        int first = merged.indexOf("\"name\": \"half\"");
        assertTrue(first >= 0 && merged.indexOf("\"name\": \"half\"", first + 1) > first,
                "a table of 16 entries and one of 256 named half are two transformations: "
                        + merged);
    }

    /** A tune of version 7 in JSON: sources {@code a} and {@code b}, each
     *  of one row of 16, started on R8 at rows 0 and 1, a remap at row 2
     *  through {@code half} of 16 entries, and {@code a} started again at
     *  row 3. */
    private static final String EQUAL_ROWS = """
            {"format":"ymxs","version":7,"tunes":[{"title":"","composer":"",
            "writer":"t","rate":50,"rows":4,"repeat":0,
            "sources":[{"name":"a","repeat":0,"values":[16]},
            {"name":"b","repeat":0,"values":[16]}],
            "transformations":[{"name":"half","entries":[HALVES]}],
            "timerA":{"shape":[0,0,3,0],"target":[8,8,-1,8],"source":[1,2,-1,1],
            "prescaler":[50,50,-1,50],"count":[60,60,-1,60],"timerReset":[1,1,-1,1],
            "placeReset":[1,1,-1,1],"deferred":[0,0,-1,0],"mapping":[0,0,[1],0]}}]}
            """.replace("HALVES", halves(16));

    /**
     * The prefixes of SPEC.md 1.12, and the entries a check reads, are the
     * same in both trees. A line of a transformation is prefixed with its
     * number of SPEC.md 1.9 and its name, in that number order, where the
     * file lists the transformations in the other order. A line of the
     * values of a remap is prefixed with the row of the start it is read
     * against, one line for each source of equal rows under two names, at
     * the first start that connects each. A value through a table of 16
     * entries with entry 1 of 300 is read against the last key and through
     * entry 0, at the start and at the remap.
     */
    @Test
    void thePrefixesAndTheEntriesOfSpec112AreTheSameInBothTrees() throws Exception {
        String json = SEVEN_JSON;
        String half = transformation("half", halves(256));
        String values = "\"values\":[13,0]";
        String mapping = "\"mapping\":[[1],0]";
        for (String each : List.of(half, values, mapping)) {
            assertTrue(json.contains(each), "the tune has " + each);
        }
        String listed = json.replace(values, "\"values\":[1,2]")
                .replace(mapping, "\"mapping\":[[2],[1]]");
        StringBuilder spoilt = new StringBuilder("40,300");
        for (int key = 2; key < 16; key++) {
            spoilt.append(',').append(key - 2);
        }
        String most = ", and the target is 0 to 31";
        String key15 = "a source on setR8 whose row 2 is 20, and a key is 0 to 15";
        String forty = "value 0 of row 0 maps to 40 on setR8" + most;
        String at0 = "tune 1: row 0: Timer A: ";
        String remap = "tune 1: row 1: Timer A: start of row 0: ";
        String equal = "a source on setR8 whose row 0 is 16, and a key is 0 to 15";
        for (Wrong wrong : List.of(
                new Wrong("ymxs-check", listed.replace(half, "\"transformations\":["
                        + "{\"name\":\"bad\",\"entries\":[1,2,300]},"
                        + "{\"name\":\"good\",\"entries\":[1,2,3]}],"),
                        "tune 1: transformation 2, bad: entry 2 is 300, and an entry is 0 to"
                                + " 255"),
                new Wrong("ymxs-check", listed.replace(half, "\"transformations\":["
                        + "{\"name\":\"first\",\"entries\":[1,2,300]},"
                        + "{\"name\":\"second\",\"entries\":[1,2,3,400]}],"),
                        "tune 1: transformation 1, second: entry 3 is 400, and an entry is 0"
                                + " to 255\n"
                                + "tune 1: transformation 2, first: entry 2 is 300, and an"
                                + " entry is 0 to 255"),
                new Wrong("ymxs-check", EQUAL_ROWS,
                        "tune 1: row 2: Timer A: start of row 0: " + equal + "\n"
                                + "tune 1: row 2: Timer A: start of row 1: " + equal),
                new Wrong("ymxs-check", json.replace(half, transformation("v", spoilt.toString()))
                        .replace(values, "\"values\":[0,1,20]")
                        .replace(mapping, "\"mapping\":[[1],[1]]"),
                        "tune 1: transformation 1, v: entry 1 is 300, and an entry is 0 to 255\n"
                                + at0 + key15 + "\n" + at0 + forty + "\n"
                                + remap + forty + "\n" + remap + key15))) {
            wrongInBothTrees(wrong);
        }
    }

    /**
     * A name in a reported line is one line in both trees, each line feed
     * in it written as the two characters {@code \n} and each carriage
     * return as {@code \r} (SPEC.md 1.12; json.md 8.1, csv.md 5.1). In JSON,
     * for each of the two: a transformation with an entry of 300, through
     * ymxs-check and through ymxs-json-to-csv, which reports the structure
     * before the line of tools.md 7.4; a transformation with an entry of
     * {@code 1e1}; a source with a value of {@code "x"}; and an unstarted
     * source and an unused transformation. In CSV, where a line feed ends
     * the line (csv.md 2.6), a carriage return: a transformation with an
     * entry of 300, an unstarted source and an unused transformation, and a
     * source of a row of several values in a file of version 3.
     */
    @Test
    void aNameInAReportedLineIsOneLineInBothTrees() throws Exception {
        String json = SEVEN_JSON;
        String name = "\"name\":\"half\"";
        String tone = "\"name\":\"a tone\"";
        String entries = "\"entries\":[0,0,1,";
        String values = "\"values\":[13,0]";
        String sources = "\"sources\":[{\"name\":\"a tone\",\"repeat\":0,\"values\":[13,0]}";
        String half = transformation("half", halves(256));
        for (String each : List.of(name, tone, entries, values, sources, half)) {
            assertTrue(json.contains(each), "the tune has " + each);
        }
        String csv = new String(both("ymxs-json-to-csv", json.getBytes(
                StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
        String opens = "### transformation\nname\nhalf\n";
        String keys = "### entry\nkey,entry\n0,0\n1,0\n2,1\n";
        String registers = "\n### registers\n";
        for (String each : List.of(opens, keys, registers)) {
            assertTrue(csv.contains(each), "the tune has " + each);
        }
        assertTrue(THREE_CSV.contains("a tone,0\n"), "the tune of version 3 has a tone");
        String high = ": entry 2 is 300, and an entry is 0 to 255";
        String started = ", is started by no row, and a source a tune does not run is"
                + " dropped where this form is read";
        String used = ", is used by no row, and a transformation a tune does not map"
                + " through is dropped where this form is read";
        String whole = ", and this form requires a whole number";
        List<Wrong> wrong = new ArrayList<>();
        for (String escape : List.of("\\n", "\\r")) {
            // the JSON escape of each character is its form in the line
            String ha = "ha" + escape + "lf";
            String idle = "id" + escape + "le";
            String other = "ot" + escape + "her";
            String at = "a" + escape + "tone";
            String named = json.replace(name, "\"name\":\"" + ha + "\"");
            String spoilt = named.replace(entries, "\"entries\":[0,0,300,");
            wrong.add(new Wrong("ymxs-check", spoilt, "tune 1: transformation 1, " + ha + high));
            wrong.add(new Wrong("ymxs-json-to-csv", spoilt,
                    "tune 1: transformation 1, " + ha + high));
            wrong.add(new Wrong("ymxs-check", named.replace(entries, "\"entries\":[0,1e1,1,"),
                    ha + " at entry 1 is 1e1" + whole));
            wrong.add(new Wrong("ymxs-check", json.replace(tone, "\"name\":\"" + at + "\"")
                    .replace(values, "\"values\":[13,\"x\"]"), at + " at row 1 is \"x\"" + whole));
            wrong.add(new Wrong("ymxs-check", json.replace(sources, sources
                    + ",{\"name\":\"" + idle + "\",\"values\":[1]}").replace(half,
                    half.replace("]}],", "]},{\"name\":\"" + other + "\",\"entries\":[0]}],")),
                    "source 2, " + idle + started + "\ntransformation 2, " + other + used));
        }
        wrong.add(new Wrong("ymxs-csv-to-json", csv.replace(opens,
                "### transformation\nname\nha\rlf\n").replace(keys,
                "### entry\nkey,entry\n0,0\n1,0\n2,300\n"),
                "tune 1: transformation 1, ha\\rlf" + high));
        wrong.add(new Wrong("ymxs-csv-to-json", csv.replace(registers,
                "\n### source\nname,repeat\nid\rle,0\n\n### value\nrow,value\n0,1\n\n"
                        + "### transformation\nname\not\rher\n\n### entry\nkey,entry\n0,0\n"
                        + registers),
                "source 2, id\\rle" + started + "\ntransformation 2, ot\\rher" + used));
        wrong.add(new Wrong("ymxs-csv-to-json", THREE_CSV.replace("a tone,0\n", "a\rtone,0\n")
                .replace("row,value\n0,13\n1,0", "row,value1,value2\n0,13,1\n1,0,1"),
                "source a\\rtone has a row of several values, and version 3 has one value a"
                        + " row"));
        for (Wrong one : wrong) {
            wrongInBothTrees(one);
        }
    }

    /**
     * A start whose source has other values a row than its target reads is
     * the one line of json.md 8.1 and csv.md 5.1 in both trees, read after
     * the columns of the start, so the line of a register the same row sets
     * above its most is left unreported (SPEC.md 3.2.1): a source of one
     * value a row on {@code setNoiseB}, and of two on {@code setR8}, in
     * each form. The register alone is its line of SPEC.md 1.11. Where a
     * column of that start read after its target is an error of the form,
     * the line of that column is the one line (json.md 7.1 step 8, csv.md
     * 4.1 step 6): a mapping through transformation 2 of a tune of one
     * transformation on {@code setNoiseB}, and a count of {@code "x"} on
     * {@code setR8}, in each form. A source of no rows on {@code setNoiseB}
     * is the line of a source of 1 values a row (json.md 4.2, csv.md 3.6):
     * in JSON an empty {@code values}, and in CSV a source with no {@code
     * value} block and one whose {@code value} block has no row line.
     */
    @Test
    void aSourceOfOtherValuesARowThanItsTargetReadsIsOneLineInBothTrees() throws Exception {
        String json = SEVEN_JSON;
        String target = "\"target\":[8,-1]";
        String values = "\"values\":[13,0]";
        String count = "\"count\":[60,-1]";
        String mapping = "\"mapping\":[[1],0]";
        String timer = "\"timerA\":";
        for (String each : List.of(target, values, count, mapping, timer)) {
            assertTrue(json.contains(each), "the tune has " + each);
        }
        String csv = new String(both("ymxs-json-to-csv", json.getBytes(
                StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
        String start = "0,0,8,1,50,60,1,1,0,1\n";
        String rows = "row,value\n0,13\n1,0\n";
        String block = "### value\n" + rows + "\n";
        String registers = "### registers\nrow,r0,r1,r2,r3,r4,r5,r6,r7,r8,r9,r10,r11,r12,r13\n";
        for (String each : List.of(start, block, registers)) {
            assertTrue(csv.contains(each), "the tune has " + each);
        }
        String r9 = json.replace(timer, "\"registers\":{\"r9\":[40,-1]},\n" + timer);
        String r8 = json.replace(timer, "\"registers\":{\"r8\":[40,-1]},\n" + timer);
        String one = "a source of 1 values a row on setNoiseB, which reads 2";
        String two = "a source of 2 values a row on setR8, which reads 1";
        String maps = "row 0 maps through transformation 2, and the tune has 1";
        String pairs = "row,value1,value2\n0,13,1\n1,0,1\n";
        for (Wrong wrong : List.of(
                new Wrong("ymxs-check", r9, "tune 1: row 0: R9 is 0 to 31, and this row sets it"
                        + " to 40"),
                new Wrong("ymxs-check", r9.replace(target, "\"target\":[23,-1]"), one),
                new Wrong("ymxs-check", r8.replace(values, "\"values\":[[13,1],[0,1]]"), two),
                new Wrong("ymxs-csv-to-json", csv.replace(start, "0,0,23,1,50,60,1,1,0,1\n")
                        .replace(registers, registers + "0,,,,,,,,,,40,,,,\n"), one),
                new Wrong("ymxs-csv-to-json", csv.replace(rows, pairs)
                        .replace(registers, registers + "0,,,,,,,,,40,,,,,\n"), two),
                new Wrong("ymxs-check", json.replace(target, "\"target\":[23,-1]")
                        .replace(mapping, "\"mapping\":[[2],0]"), maps),
                new Wrong("ymxs-check", json.replace(values, "\"values\":[[13,1],[0,1]]")
                        .replace(count, "\"count\":[\"x\",-1]"),
                        "Timer A's count at row 0 is \"x\", and this form requires a whole"
                                + " number"),
                new Wrong("ymxs-csv-to-json", csv.replace(start, "0,0,23,1,50,60,1,1,0,2\n"),
                        maps),
                new Wrong("ymxs-csv-to-json", csv.replace(rows, pairs)
                        .replace(start, "0,0,8,1,50,x,1,1,0,1\n"),
                        "count is \"x\", and this form requires a whole number"),
                new Wrong("ymxs-check", json.replace(values, "\"values\":[]")
                        .replace(target, "\"target\":[23,-1]"), one),
                new Wrong("ymxs-csv-to-json", csv.replace(block, "")
                        .replace(start, "0,0,23,1,50,60,1,1,0,1\n"), one),
                new Wrong("ymxs-csv-to-json", csv.replace(rows, "row,value\n")
                        .replace(start, "0,0,23,1,50,60,1,1,0,1\n"), one))) {
            wrongInBothTrees(wrong);
        }
    }

    /**
     * Each condition of csv.md 5.1 that version 7 adds is one line in both
     * trees. White space is the Unicode property White_Space, so U+00A0
     * splits a {@code mapping} cell and ends a whole number, and a whole
     * number is of the digits 0 to 9 (csv.md 1.3, 1.7). A reader reads a
     * transformation of any number of entries, an absent {@code entry}
     * block for 0, and a count outside 1 to 256 is a line of the structure
     * (csv.md 3.12; SPEC.md 1.11), as is a value of 16 through a table of
     * 16 entries.
     */
    @Test
    void eachErrorOfTheCsvFormOfVersion7IsOneLineInBothTrees() throws Exception {
        String csv = new String(both("ymxs-json-to-csv", SEVEN_JSON.getBytes(
                StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
        String opens = "### transformation\nname\nhalf\n";
        String keys = "### entry\nkey,entry\n0,0\n1,0\n";
        String values = "row,value\n0,13\n1,0\n";
        String columns = ",placeReset,deferred,mapping\n";
        String start = "0,0,8,1,50,60,1,1,0,1\n";
        String remap = "1,3,,,,,,,,0\n";
        for (String each : List.of(opens, keys, values, columns, start, remap)) {
            assertTrue(csv.contains(each), "the tune has " + each);
        }
        assertTrue(both("ymxs-csv-to-json", csv.replace(values,
                "row,value\n0,13\u00a0\n1,0\n").getBytes(StandardCharsets.UTF_8)).length > 0,
                "a value followed by U+00A0 reads");
        StringBuilder block = new StringBuilder("### entry\nkey,entry\n");
        StringBuilder first = new StringBuilder("### entry\nkey,entry\n");
        for (int key = 0; key < 256; key++) {
            block.append(key).append(',').append(key / 2).append('\n');
            if (key < 16) {
                first.append(key).append(',').append(key / 2).append('\n');
            }
        }
        assertTrue(csv.contains(block + "\n"), "the tune has the 256 entries of half");
        String sixteen = csv.replace(block, first);
        assertTrue(both("ymxs-csv-to-json", sixteen.getBytes(StandardCharsets.UTF_8))
                .length > 0, "a transformation of 16 entries reads");
        String part = ", and this form requires a whole number a part";
        String counted = "tune 1: transformation 1, half: the transformation has ";
        for (Wrong wrong : List.of(
                new Wrong("ymxs-csv-to-json", csv.replace(block + "\n", ""),
                        counted + "0 entries, and a transformation has 1 to 256"),
                new Wrong("ymxs-csv-to-json", csv.replace(block, block + "256,128\n"),
                        counted + "257 entries, and a transformation has 1 to 256"),
                new Wrong("ymxs-csv-to-json", sixteen.replace(values, "row,value\n0,16\n1,0\n"),
                        "tune 1: row 0: Timer A: a source on setR8 whose row 0 is 16, and a key"
                                + " is 0 to 15"),
                new Wrong("ymxs-csv-to-json", csv.replace("ymxs,7,1", "ymxs,8,1"),
                        "version 8, and this reads 3, 4, 5, 6 or 7"),
                new Wrong("ymxs-csv-to-json", csv.replace(opens, opens + "other\n"),
                        "tune 1 opens a transformation with 2 rows, and one row opens it"),
                new Wrong("ymxs-csv-to-json", csv.replace(opens, ""),
                        "tune 1 opens entries before any transformation"),
                new Wrong("ymxs-csv-to-json", csv.replace(keys,
                        "### entry\nkey,entry\n0,0\n1,x\n"),
                        "entry is \"x\", and this form requires a whole number"),
                new Wrong("ymxs-csv-to-json", csv.replace(remap, "1,4,,,,,,,,0\n"),
                        "row 1 sets shape 4 of an effect, and a shape is 0, 1, 2 or 3"),
                new Wrong("ymxs-csv-to-json", csv.replace(start, "0,0,8,1,50,60,1,1,0,1 x\n"),
                        "mapping is \"1 x\"" + part),
                new Wrong("ymxs-csv-to-json", csv.replace(start,
                        "0,0,8,1,50,60,1,1,0,\u0661\n"), "mapping is \"\u0661\"" + part),
                new Wrong("ymxs-csv-to-json", csv.replace(columns, ",placeReset,deferred\n"),
                        "mapping is \"\"" + part),
                new Wrong("ymxs-csv-to-json", csv.replace(remap, "1,3,,,,,,,,0 0\n"),
                        "mapping is \"0 0\", and a mapping of the identity in every part is 0"),
                new Wrong("ymxs-csv-to-json", csv.replace(start, "0,0,8,1,50,60,1,1,0,2\n"),
                        "row 0 maps through transformation 2, and the tune has 1"),
                new Wrong("ymxs-csv-to-json", csv.replace(start, "0,0,8,1,50,60,1,1,0,0\n"),
                        "transformation 1, half, is used by no row, and a transformation a"
                                + " tune does not map through is dropped where this form is"
                                + " read"),
                new Wrong("ymxs-csv-to-json", csv.replace(start,
                        "0,0,8,1,50,60,1,1,0,1\u00a01\n"),
                        "tune 1: row 0: Timer A: a mapping of 2 parts on setR8, which reads 1"),
                new Wrong("ymxs-csv-to-json", csv.replace(values,
                        "row,value\n0,\u0661\u0663\n1,0\n"),
                        "value is \"\u0661\u0663\", and this form requires a whole number"))) {
            wrongInBothTrees(wrong);
        }
    }

    /**
     * In a CSV file of version 6 or before, a reader skips the
     * {@code mapping} column and reads a {@code transformation} or an
     * {@code entry} block as a block of any other name (csv.md 3.10, 4.1
     * step 5): the first such block in file order is the line of the tools
     * of 0.6.0, read before the cells of the block and before the timer
     * blocks. Without the two blocks, a remap there is a shape outside 0
     * to 2.
     */
    @Test
    void aTransformationOrAnEntryBlockOfVersion6IsOneLineInBothTrees() throws Exception {
        String csv = new String(both("ymxs-json-to-csv", SEVEN_JSON.getBytes(
                StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
        String opens = "### transformation\nname\nhalf\n\n";
        String keys = "### entry\nkey,entry\n0,0\n1,0\n";
        String start = "0,0,8,1,50,60,1,1,0,1\n";
        String remap = "1,3,,,,,,,,0\n";
        for (String each : List.of(opens + keys, start, remap, "\n### registers\n")) {
            assertTrue(csv.contains(each), "the tune has " + each);
        }
        String entries = csv.substring(csv.indexOf(keys), csv.indexOf("### registers\n"));
        String six = csv.replace("ymxs,7,1", "ymxs,6,1");
        String bare = six.replace(opens, "").replace(entries, "");
        assertTrue(both("ymxs-csv-to-json", bare.replace(remap, "")
                .replace(start, "0,0,8,1,50,60,1,1,0,x\n").getBytes(StandardCharsets.UTF_8))
                .length > 0, "a reader skips the mapping column of a file of version 6");
        String transformation = "tune 1 opens a \"### transformation\" table, which this form"
                + " does not have";
        String entry = "tune 1 opens a \"### entry\" table, which this form does not have";
        for (Wrong wrong : List.of(
                new Wrong("ymxs-csv-to-json", six, transformation),
                new Wrong("ymxs-csv-to-json", six.replace(entries, ""), transformation),
                new Wrong("ymxs-csv-to-json", six.replace(opens, ""), entry),
                new Wrong("ymxs-csv-to-json", six.replace(opens, "")
                        .replace(keys, "### entry\nkey,entry\n0,x\n1,0\n"), entry),
                new Wrong("ymxs-csv-to-json", six.replace(opens + entries, entries + opens),
                        entry),
                new Wrong("ymxs-csv-to-json", csv.replace("ymxs,7,1", "ymxs,4,1"),
                        transformation),
                new Wrong("ymxs-csv-to-json", bare,
                        "row 1 sets shape 3 of an effect, and a shape is 0, 1 or 2"))) {
            wrongInBothTrees(wrong);
        }
    }

    /**
     * A source value that is not a whole number is one error of the form,
     * the same line in both trees: in JSON for a value and for an element of
     * a row of two (json.md 8.1), in CSV for a {@code value} cell that is a
     * text, empty, or outside 32 bits (csv.md 1.7, 5.2).
     */
    @Test
    void aSourceValueThatIsNotAWholeNumberIsOneLineInBothTrees() throws Exception {
        String json = new String(file("doc/tunes/example.json"), StandardCharsets.UTF_8);
        String csv = new String(file("doc/tunes/example.csv"), StandardCharsets.UTF_8);
        String values = "\"values\": [13,0]";
        String cells = "row,value\n0,13\n1,0\n";
        assertTrue(json.contains(values) && csv.contains(cells),
                "the example has one source of 13 and 0");
        String whole = ", and this form requires a whole number";
        for (Wrong wrong : List.of(
                new Wrong("ymxs-check", json.replace(values, "\"values\": [13, \"x\"]"),
                        "square 13 at row 1 is \"x\"" + whole),
                new Wrong("ymxs-check", json.replace(values, "\"values\": [13, 1.5]"),
                        "square 13 at row 1 is 1.5" + whole),
                new Wrong("ymxs-check", json.replace(values,
                        "\"values\": [[13, \"x\"], [0, 1]]"),
                        "square 13 at row 0 is \"x\"" + whole),
                new Wrong("ymxs-csv-to-json", csv.replace(cells, "row,value\n0,x\n1,0\n"),
                        "value is \"x\"" + whole),
                new Wrong("ymxs-csv-to-json", csv.replace(cells, "row,value\n0,\n1,0\n"),
                        "value1 is \"\"" + whole),
                new Wrong("ymxs-csv-to-json", csv.replace(cells,
                        "row,value\n0,2147483648\n1,0\n"),
                        "value is \"2147483648\"" + whole))) {
            wrongInBothTrees(wrong);
        }
    }

    /** The input of {@code wrong} in both trees: exit 1, and its line. */
    private static void wrongInBothTrees(Wrong wrong) throws Exception {
        wrongInBothTrees(wrong.tool(), wrong.in().getBytes(), 1, wrong.line());
    }

    /** {@code in} through {@code tool} with {@code flags} in both trees:
     *  exit {@code exit}, standard output empty, and {@code line}. */
    private static void wrongInBothTrees(String tool, byte[] in, int exit, String line,
            String... flags) throws Exception {
        Ran java = ran(Path.of("bin"), tool, in, flags);
        Ran go = ran(built(), tool, in, flags);
        assertEquals(exit, java.exit(), tool + " reads " + new String(in,
                StandardCharsets.ISO_8859_1) + ": " + java.said());
        assertEquals(0, java.out().length, tool + " leaves standard output empty");
        assertEquals(tool + ": " + line, java.said().strip(), "the line");
        assertEquals(java.exit(), go.exit(), tool + " exits the same: " + go.said());
        assertArrayEquals(java.out(), go.out(), tool + " writes the same bytes");
        assertEquals(java.said(), go.said(), tool + " reports the same");
    }

    /** A level-0 LHA archive of one stored member, {@code dump}, whose
     *  header declares {@code unpacked} bytes (ym.md 2.5). */
    private static byte[] stored(byte[] dump, int unpacked) {
        java.io.ByteArrayOutputStream header = new java.io.ByteArrayOutputStream();
        header.writeBytes("-lh0-".getBytes(StandardCharsets.US_ASCII));
        for (int size : new int[] {dump.length, unpacked}) {
            for (int at = 0; at < 4; at++) {
                header.write(size >> (8 * at));
            }
        }
        // the time, the attribute, level 0, and a name of four bytes
        header.writeBytes(new byte[] {0, 0, 0, 0, 0x20, 0, 4});
        header.writeBytes("a.ym".getBytes(StandardCharsets.US_ASCII));
        header.writeBytes(new byte[] {0, 0});
        byte[] said = header.toByteArray();
        int sum = 0;
        for (byte one : said) {
            sum += one & 0xFF;
        }
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        out.write(said.length);
        out.write(sum);
        out.writeBytes(said);
        out.writeBytes(dump);
        return out.toByteArray();
    }

    /**
     * Four inputs that ended an invocation with an uncaught exception or a
     * panic end it with a line in both trees: a line feed in a writer
     * through ymxs-json-to-csv (tools.md 7.4), text after a multi through
     * ymxs-merge (8.5), a stored LHA member whose unpacked size is above
     * the bytes after its header (ym.md 2.5, 3.1), and a ROW below 0 (9.3).
     */
    @Test
    void anInputThatCrashedEndsWithALineInBothTrees() throws Exception {
        String json = new String(file("doc/tunes/example.json"), StandardCharsets.UTF_8);
        String writer = "\"writer\": \"by hand\"";
        assertTrue(json.contains(writer), "the example has a writer");
        wrongInBothTrees(new Wrong("ymxs-json-to-csv",
                json.replace(writer, "\"writer\": \"by\\nhand\""), UNWRITABLE + "by\\nhand"));

        // the two trees parse with two parsers, and share the prefix alone
        byte[] trailing = (json + "xyz").getBytes(StandardCharsets.UTF_8);
        for (Ran one : List.of(ran(Path.of("bin"), "ymxs-merge", trailing),
                ran(built(), "ymxs-merge", trailing))) {
            assertEquals(1, one.exit(), one.said());
            assertEquals(0, one.out().length, "ymxs-merge leaves standard output empty");
            assertTrue(one.said().startsWith("ymxs-merge: this is not JSON: "), one.said());
        }

        byte[] dump = new byte[4 + 14];
        System.arraycopy("YM3!".getBytes(StandardCharsets.US_ASCII), 0, dump, 0, 4);
        both("ym-to-ymxs", stored(dump, dump.length));
        wrongInBothTrees("ym-to-ymxs", stored(dump, dump.length + 14), 1,
                "this is an archive with a dump inside, and it does not unpack: LHA member"
                        + " is truncated");
        for (String row : List.of("-r-1", "-r-2147483648")) {
            wrongInBothTrees("ym-to-ymxs", file("src/test/resources/packed.ym"), 2,
                    row + " is not a row number", row);
        }
    }

    /**
     * A row count below 1 is a tune of no rows (json.md 7.1 step 5, csv.md
     * 4.1 step 4): a column or a row cell of the tune is the line of the
     * form with the count as read, and a tune of no columns is the empty
     * table of SPEC.md 1.11, the same line and exit 1 in both trees.
     */
    @Test
    void aRowCountBelow1IsOneLineInBothTrees() throws Exception {
        String json = new String(file("doc/tunes/example.json"), StandardCharsets.UTF_8);
        String csv = new String(file("doc/tunes/example.csv"), StandardCharsets.UTF_8);
        String rows = "\"rows\": 4,";
        String cells = ",50,4,0\n";
        assertTrue(json.contains(rows) && csv.contains(cells), "the example has 4 rows");
        String bare = "{\"format\":\"ymxs\",\"version\":4,\"tunes\":[{\"title\":\"\","
                + "\"composer\":\"\",\"writer\":\"t\",\"rate\":50,\"rows\":-1,"
                + "\"repeat\":null,\"sources\":[]}]}";
        String none = "### multi\nformat,version,tunes\nymxs,4,1\n\n### tune\n"
                + "title,composer,writer,rate,rows,repeat\n,,t,50,-1,\n";
        String empty = "tune 1: the tune has no rows: a clock reads one";
        for (Wrong wrong : List.of(
                new Wrong("ymxs-check", json.replace(rows, "\"rows\": -1,"),
                        "r0 is 4 values long, and the tune has -1 rows"),
                new Wrong("ymxs-check", json.replace(rows, "\"rows\": -2147483648,"),
                        "r0 is 4 values long, and the tune has -2147483648 rows"),
                new Wrong("ymxs-check", bare, empty),
                new Wrong("ymxs-csv-to-json", csv.replace(cells, ",50,-1,0\n"),
                        "tune 1 sets a row at row 0, and the tune runs -1 rows"),
                new Wrong("ymxs-csv-to-json", csv.replace(cells, ",50,-2147483648,0\n"),
                        "tune 1 sets a row at row 0, and the tune runs -2147483648 rows"),
                new Wrong("ymxs-csv-to-json", none, empty))) {
            wrongInBothTrees(wrong);
        }
    }

    /** A YM6! dump of two frames of silence, with a name and an author of
     *  one byte a character (ym.md 2.3). */
    private static byte[] ym6(String name, String author) {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        out.writeBytes("YM6!LeOnArD!".getBytes(StandardCharsets.US_ASCII));
        // 2 frames, attributes 0, 0 samples, a clock of 2,000,000, 50 Hz,
        // loop 0 and extra data 0, each field most significant byte first
        out.writeBytes(new byte[] {0, 0, 0, 2, 0, 0, 0, 0, 0, 0, 0, 0x1E, (byte) 0x84,
            (byte) 0x80, 0, 50, 0, 0, 0, 0, 0, 0});
        for (String text : new String[] {name, author, ""}) {
            out.writeBytes(text.getBytes(StandardCharsets.ISO_8859_1));
            out.write(0);
        }
        for (int frame = 0; frame < 2; frame++) {
            byte[] registers = new byte[16];
            registers[7] = 0x3F;
            registers[13] = (byte) 0xFF;
            out.writeBytes(registers);
        }
        out.writeBytes("End!".getBytes(StandardCharsets.US_ASCII));
        return out.toByteArray();
    }

    /**
     * Inputs on which the Go tree departed from the documents, read the same
     * in both trees: the columns of a JSON start in the order of json.md 7.1
     * step 8; a root, tune or source that is not an object (json.md 8.2); a
     * CSV {@code repeat} cell at its source block (csv.md 4.1 step 5); a
     * cell or a table name as read (csv.md 5.1); a character outside the
     * Basic Multilingual Plane counted as two (tools.md 1.7); and a dump's
     * name, author and opening bytes (tools.md 9.4, ym.md 3).
     */
    @Test
    void theGoTreeReadsAsTheDocumentsDefine() throws Exception {
        String json = new String(file("doc/tunes/example.json"), StandardCharsets.UTF_8);
        String csv = new String(file("doc/tunes/example.csv"), StandardCharsets.UTF_8);
        String target = "\"target\": [8,-1,-1,-1],\n";
        String source = "\"source\": [1,-1,-1,-1]";
        String tunes = "\"tunes\": [";
        String sources = "\"sources\": [";
        String start = "0,0,8,1,50,60,1,1";
        for (String each : List.of(target, source, tunes, sources)) {
            assertTrue(json.contains(each), "the example has " + each);
        }
        assertTrue(csv.contains(start) && csv.contains("square 13,0")
                && csv.contains("### timerA"), "the example starts on timerA");
        String whole = ", and this form requires a whole number";
        List<Wrong> wrong = new ArrayList<>(List.of(
                new Wrong("ymxs-check", json.replace(target, "").replace(source,
                        "\"source\": [5,-1,-1,-1]"), "row 0 starts source 5, and the tune runs 1"),
                new Wrong("ymxs-check", json.replace(tunes, tunes + "5, "),
                        "rows is null" + whole),
                new Wrong("ymxs-check", json.replace(tunes, tunes + "[], "),
                        "rows is null" + whole),
                new Wrong("ymxs-check", json.replace(sources, sources + "null, "),
                        "values is null, and this form requires an array"),
                new Wrong("ymxs-check", json.replace(sources, sources + "[], "),
                        "values is null, and this form requires an array"),
                new Wrong("ymxs-csv-to-json", csv.replace("square 13,0", "square 13,x")
                        .replace("row,value\n0,13\n", "row,value\n0,y\n"),
                        "repeat is \"x\"" + whole),
                new Wrong("ymxs-csv-to-json", csv.replace(start, "0,0,8,1,50,\"6\"\"0\",1,1"),
                        "count is \"6\"0\"" + whole),
                new Wrong("ymxs-csv-to-json", csv.replace(start, "0,0,8,1,50,6\\0,1,1"),
                        "count is \"6\\0\"" + whole),
                new Wrong("ymxs-csv-to-json", csv.replace(start, "0,0,8,1,50,6\u00010,1,1"),
                        "count is \"6\u00010\"" + whole),
                new Wrong("ymxs-csv-to-json", csv.replace("### timerA", "### timerQ\"\\"),
                        "tune 1 opens a \"### timerQ\"\\\" table, and a timer is timerA to"
                                + " timerD"),
                new Wrong("ymxs-csv-to-json", csv.replace("### timerA", "### ti\\m\"er"),
                        "tune 1 opens a \"### ti\\m\"er\" table, which this form does not"
                                + " have")));
        for (String root : List.of("[1,2]", "5", "null", "\"x\"")) {
            wrong.add(new Wrong("ymxs-check", root, "format is null, and this form requires a"
                    + " text"));
        }
        for (Wrong one : wrong) {
            wrongInBothTrees(one);
        }

        String title = "\"title\": \"Four rows, one square\"";
        assertTrue(json.contains(title), "the example has a title");
        byte[] wide = json.replace(title, "\"title\": \"a \uD83C\uDFB5 tune\"")
                .getBytes(StandardCharsets.UTF_8);
        byte[] tables = both("ymxs-json-to-csv", wide);
        both("ymxs-csv-to-json", tables);
        String said = ran(Path.of("bin"), "ymxs-json-to-csv", wide).said();
        int counted = new String(wide, StandardCharsets.UTF_8).length();
        assertTrue(said.contains(counted + " characters in"), "the character outside the"
                + " plane counts two: " + said);

        byte[] dump = ym6("a \"q\" \\ b\u0001", "\u00e9t\u00e9");
        both("ym-to-ymxs", dump);
        String read = ran(Path.of("bin"), "ym-to-ymxs", dump).said();
        assertTrue(read.contains("YM6! \"a \"q\" \\ b\u0001\" by \"\u00e9t\u00e9\", 2 rows"),
                read);
        byte[] opening = "a\"\\\u0001\u00ffxyz0123".getBytes(StandardCharsets.ISO_8859_1);
        wrongInBothTrees("ym-to-ymxs", opening, 1, "not a YM3!, YM3b, YM5! or YM6! dump: it"
                + " opens with \"a\"\\\u0001\"");
    }

    @Test
    void anEmptyInputIsOneFaultInBothTrees() throws Exception {
        for (String tool : TOOLS) {
            Ran java = ran(Path.of("bin"), tool, new byte[0]);
            Ran go = ran(built(), tool, new byte[0]);
            assertEquals(1, java.exit(), tool + " reads an empty input: " + java.said());
            assertEquals(java.exit(), go.exit(), tool + " exits the same: " + go.said());
            assertEquals(java.said(), go.said(), tool + " reports the same");
        }
    }

    /**
     * A text that is no JSON at all. json.md 8.1 has the line as
     * {@code this is not JSON: } and the parser's report after it, and the
     * two trees parse with two parsers, so they share the prefix alone.
     */
    @Test
    void aTextThatIsNotJsonIsOneConditionInBothTrees() throws Exception {
        byte[] junk = "xyz".getBytes();
        for (String tool : READ_JSON) {
            Ran java = ran(Path.of("bin"), tool, junk);
            Ran go = ran(built(), tool, junk);
            assertEquals(1, java.exit(), tool + " reads no such input: " + java.said());
            assertEquals(java.exit(), go.exit(), tool + " exits the same: " + go.said());
            for (Ran one : List.of(java, go)) {
                assertTrue(one.said().contains("this is not JSON: "),
                        tool + " reports \"" + one.said() + '"');
            }
        }
    }

    @Test
    void anUnknownFlagIsOneFaultInBothTrees() throws Exception {
        for (String tool : TOOLS) {
            Ran java = ran(Path.of("bin"), tool, new byte[0], "-zz");
            Ran go = ran(built(), tool, new byte[0], "-zz");
            assertEquals(2, java.exit(), tool + " reads -zz: " + java.said());
            assertEquals(java.exit(), go.exit(), tool + " exits the same: " + go.said());
            assertEquals(java.said(), go.said(), tool + " reports the same");
        }
    }

    @Test
    void aWrongInputIsWrongInBothTrees() throws Exception {
        byte[] nonsense = "not a file of any of these".getBytes();
        for (String tool : TOOLS) {
            Ran java = ran(Path.of("bin"), tool, nonsense);
            Ran go = ran(built(), tool, nonsense);
            assertEquals(1, java.exit(), tool + " reads no such input: " + java.said());
            assertEquals(java.exit(), go.exit(), tool + " exits the same: " + go.said());
            assertArrayEquals(java.out(), go.out(), tool + " writes the same bytes");
        }
    }
}
