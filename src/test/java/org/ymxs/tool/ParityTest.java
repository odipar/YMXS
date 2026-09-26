package org.ymxs.tool;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
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

    /** The input of {@code wrong} in both trees: exit 1, and its line. */
    private static void wrongInBothTrees(Wrong wrong) throws Exception {
        Ran java = ran(Path.of("bin"), wrong.tool(), wrong.in().getBytes());
        Ran go = ran(built(), wrong.tool(), wrong.in().getBytes());
        assertEquals(1, java.exit(), wrong.tool() + " reads " + wrong.in());
        assertEquals(wrong.tool() + ": " + wrong.line(), java.said().strip(),
                "the line of the form");
        assertEquals(java.exit(), go.exit(), wrong.tool() + " exits the same: " + go.said());
        assertEquals(java.said(), go.said(), wrong.tool() + " reports the same");
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
