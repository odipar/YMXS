package org.ymxs.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.ymxs.Csv;
import org.ymxs.Text;
import org.ymxs.Tunes;
import org.ymxs.YMXS.Multi;

/**
 * The tools as a reader runs them: input on standard input, output on
 * standard output, what they did and what is wrong on standard error.
 *
 * <p>Every one of them stands in a pipe, so this runs them in one and
 * reads what comes out of the end.
 *
 * <p>They run out of {@code target/classes}, so this runs where they have
 * been built and skips where they have not: a build inside a build is not
 * something a test starts.
 */
@EnabledIf("theToolsAreBuilt")
final class PipeTest {

    /** Whether the tools can run without a build of their own. */
    static boolean theToolsAreBuilt() {
        return Files.exists(Path.of("target/classes/org/ymxs/tool/Tool.class"))
                && Files.exists(Path.of("target/classpath.txt"));
    }

    /** What one run of a pipeline came to. */
    private record Ran(int exit, String out, String said) { }

    /** {@code tools} run one into the next, the first reading {@code in}. */
    private static Ran pipe(byte[] in, String... tools) throws IOException,
            InterruptedException {
        Path said = Files.createTempFile("said", ".txt");
        Path input = Files.createTempFile("in", ".bin");
        Path out = Files.createTempFile("out", ".bin");
        Files.write(input, in);
        List<ProcessBuilder> built = new ArrayList<>();
        for (String tool : tools) {
            List<String> whole = new ArrayList<>(List.of(tool.split(" ")));
            whole.set(0, "bin/" + whole.get(0));
            built.add(new ProcessBuilder(whole).redirectError(
                    ProcessBuilder.Redirect.appendTo(said.toFile())));
        }
        built.get(0).redirectInput(input.toFile());
        built.get(built.size() - 1).redirectOutput(out.toFile());
        int exit = 0;
        for (Process one : ProcessBuilder.startPipeline(built)) {
            int was = one.waitFor();
            if (was != 0) {
                exit = was;
            }
        }
        return new Ran(exit, Files.readString(out), Files.readString(said));
    }

    private static byte[] packed() throws IOException {
        return Files.readAllBytes(Path.of("src/test/resources/packed.ym"));
    }

    private static Multi circus() throws IOException {
        return Text.read(Files.readString(Path.of("doc/tunes/circus.json")));
    }

    @Test
    void aPackedDumpRunsAllTheWayToTheTableForm() throws Exception {
        Ran ran = pipe(packed(), "ym-to-ymxs", "ymxs-check", "ymxs-json-to-csv");
        assertEquals(0, ran.exit(), ran.said());
        assertTrue(ran.out().startsWith("### multi,"), ran.out());
        assertEquals(circus(), Csv.read(ran.out()),
                "what comes out of the pipe is the tune that went in");
    }

    @Test
    void theTwoFormsCrossBackOverAPipe() throws Exception {
        Ran ran = pipe(packed(), "ym-to-ymxs", "ymxs-json-to-csv", "ymxs-csv-to-json");
        assertEquals(0, ran.exit(), ran.said());
        assertEquals(circus(), Text.read(ran.out()));
    }

    @Test
    void checkPassesWhatItReadsThroughUntouched() throws Exception {
        Ran once = pipe(packed(), "ym-to-ymxs");
        Ran twice = pipe(packed(), "ym-to-ymxs", "ymxs-check");
        assertEquals(once.out(), twice.out(), "the same text, character for character");
        assertTrue(twice.said().contains("every rule a writer keeps to is kept"),
                twice.said());
    }

    @Test
    void anErrorStopsThePipeAndWritesNothing() throws Exception {
        Ran ran = pipe("{\"format\":\"nope\"}".getBytes(StandardCharsets.UTF_8),
                "ymxs-check");
        assertEquals(1, ran.exit(), ran.said());
        assertEquals("", ran.out(), "nothing goes to standard output");
        assertTrue(ran.said().contains("a tree of nope"), ran.said());
    }

    @Test
    void aDumpThatIsNeitherStopsTheFirstTool() throws Exception {
        Ran ran = pipe("not a dump".getBytes(StandardCharsets.UTF_8), "ym-to-ymxs");
        assertEquals(1, ran.exit(), ran.said());
        assertEquals("", ran.out());
        assertTrue(ran.said().contains("not a YM5! or YM6! dump"), ran.said());
    }

    @Test
    void silentLeavesAToolSayingNothingButWhatIsWrong() throws Exception {
        Ran ran = pipe(packed(), "ym-to-ymxs -silent", "ymxs-check -silent");
        assertEquals(0, ran.exit(), ran.said());
        assertEquals("", ran.said(), "nothing was wrong, so nothing was said");
        assertTrue(!ran.out().isEmpty(), "and the tune still came out");
    }

    @Test
    void severalTunesMergeIntoOneMulti() throws Exception {
        Ran one = pipe(packed(), "ym-to-ymxs -silent");
        Ran ran = pipe((one.out() + one.out()).getBytes(StandardCharsets.UTF_8),
                "ymxs-merge");
        assertEquals(0, ran.exit(), ran.said());
        Multi multi = Text.read(ran.out());
        assertEquals(2, multi.tunes().size());
        assertEquals(Tunes.size(multi.tunes().get(0).table()),
                Tunes.size(multi.tunes().get(1).table()));
        assertTrue(ran.said().contains("2 files holding 2 tunes"), ran.said());
    }

    @Test
    void aToolTakesNoNamesButItsFlags() throws Exception {
        Ran ran = pipe(packed(), "ymxs-check tune.json");
        assertEquals(2, ran.exit(), ran.said());
        assertTrue(ran.said().contains("standard input"), ran.said());
    }
}
