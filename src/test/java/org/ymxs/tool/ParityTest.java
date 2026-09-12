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
 * faults, so a caller who takes either has the same bytes at every step.
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

    @Test
    void everyTuneOfTheDocumentsCrossesBothWaysTheSame() throws Exception {
        for (String named : List.of("circus", "digidrum", "retrigger", "turrican-2",
                "two-tunes", "warnings")) {
            byte[] json = file("doc/tunes/" + named + ".json");
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
