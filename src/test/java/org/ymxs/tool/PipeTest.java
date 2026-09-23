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
 * The tools as invoked in practice: input on standard input, output on
 * standard output, progress and faults on standard error.
 *
 * <p>Each tool composes in a pipe, so this runs them in one and reads the
 * output of the last.
 *
 * <p>They run out of {@code target/classes}, so this runs where they have
 * been built and skips where they have not: a test starts no build inside
 * a build.
 */
@EnabledIf("theToolsAreBuilt")
final class PipeTest {

    /** Whether the tools can run without a further build. */
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
        assertTrue(ran.out().startsWith("### multi\nformat,"), ran.out());
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
        assertTrue(twice.said().contains("every rule of SPEC.md 6 is satisfied"),
                twice.said());
    }

    @Test
    void anErrorStopsThePipeAndLeavesStandardOutputEmpty() throws Exception {
        Ran ran = pipe("{\"format\":\"nope\"}".getBytes(StandardCharsets.UTF_8),
                "ymxs-check");
        assertEquals(1, ran.exit(), ran.said());
        assertEquals("", ran.out(), "standard output stays empty");
        assertTrue(ran.said().contains("a tree of nope"), ran.said());
    }

    @Test
    void aDumpThatIsNeitherStopsTheFirstTool() throws Exception {
        Ran ran = pipe("not a dump".getBytes(StandardCharsets.UTF_8), "ym-to-ymxs");
        assertEquals(1, ran.exit(), ran.said());
        assertEquals("", ran.out());
        assertTrue(ran.said().contains("not a YM3!, YM3b, YM5! or YM6! dump"), ran.said());
    }

    @Test
    void silentCutsWhatAToolSaysDownToWhatIsWrong() throws Exception {
        Ran ran = pipe(packed(), "ym-to-ymxs -silent", "ymxs-check -silent");
        assertEquals(0, ran.exit(), ran.said());
        assertEquals("", ran.said(), "no fault, so no word of one");
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
        assertTrue(ran.said().contains("2 files with 2 tunes"), ran.said());
    }

    @Test
    void aToolTakesNoNamesButItsFlags() throws Exception {
        Ran ran = pipe(packed(), "ymxs-check tune.json");
        assertEquals(2, ran.exit(), ran.said());
        assertTrue(ran.said().contains("standard input"), ran.said());
    }

    /** The multi of tools.md 4.1: four rows repeating to row 4, and a
     *  source of two rows repeating to row 2. */
    private static final String TWO_FAULTS = "{\"format\":\"ymxs\",\"version\":4,"
            + "\"tunes\":[{\"title\":\"Four rows, one square\",\"composer\":\"\","
            + "\"writer\":\"by hand\",\"rate\":50,\"rows\":4,\"repeat\":4,"
            + "\"sources\":[{\"name\":\"square 13\",\"repeat\":2,\"values\":[13,0]}],"
            + "\"registers\":{\"r0\":[163,142,251,89]},"
            + "\"timerA\":{\"shape\":[0,-1,-1,-1],\"target\":[8,-1,-1,-1],"
            + "\"source\":[1,-1,-1,-1],\"prescaler\":[50,-1,-1,-1],"
            + "\"count\":[60,-1,-1,-1],\"timerReset\":[1,-1,-1,-1],"
            + "\"placeReset\":[1,-1,-1,-1]}}]}";

    /** The tool names a quoted line of the document begins with, and the
     *  prefix of the second line of an error of several. */
    private static final java.util.regex.Pattern OF_A_RUN =
            java.util.regex.Pattern.compile("^(ym-to-ymxs|ymxs-check|ymxs-json-to-csv"
                    + "|ymxs-csv-to-json|ymxs-merge|tune \\d+): ");

    /** Every line the document quotes of a run: a line of a fenced block
     *  or of one indented four spaces, written by a tool. A line with a
     *  placeholder in it stands for many lines and is left out: the
     *  document writes those as {@code <name>} and as X, the argument. */
    private static List<String> quoted(String document) {
        List<String> lines = new ArrayList<>();
        boolean fenced = false;
        for (String line : document.split("\n")) {
            if (line.startsWith("```")) {
                fenced = !fenced;
                continue;
            }
            String said = fenced ? line : line.startsWith("    ")
                    ? line.substring(4) : "";
            if (said.isBlank() || said.contains("<") || said.contains("\"X\"")
                    || said.contains(" X ") || !OF_A_RUN.matcher(said).find()) {
                continue;
            }
            lines.add(said);
        }
        return lines;
    }

    /**
     * Every line tools.md quotes of a run against the run that writes it.
     * The figures in them - 2,098 rows and 4 sources, 732 characters in
     * and 469 out, the two warnings of the tune of SPEC.md 6.6 - move
     * with the tunes beside them, and no check read one back.
     */
    @Test
    void everyLineTheDocumentQuotesIsALineARunWrites() throws Exception {
        StringBuilder said = new StringBuilder();
        for (String[] run : new String[][] {
            {"doc/tunes/two-tunes.json", "ymxs-check"},
            {"doc/tunes/warnings.json", "ymxs-check"},
            {"doc/tunes/example.json", "ymxs-json-to-csv"},
            {"doc/tunes/example.csv", "ymxs-csv-to-json"},
        }) {
            said.append(pipe(Files.readAllBytes(Path.of(run[0])), run[1]).said());
        }
        said.append(pipe(TWO_FAULTS.getBytes(StandardCharsets.UTF_8),
                "ymxs-check").said());
        said.append(pipe(packed(), "ym-to-ymxs").said());
        String twice = Files.readString(Path.of("doc/tunes/example.json"));
        said.append(pipe((twice + twice).getBytes(StandardCharsets.UTF_8),
                "ymxs-merge").said());

        List<String> lines = quoted(Files.readString(Path.of("doc/tools.md")));
        assertTrue(lines.size() >= 8, () -> "the document quotes " + lines.size()
                + " lines of a run; the check is asleep");
        List<String> missing = new ArrayList<>();
        for (String line : lines) {
            if (!said.toString().contains(line)) {
                missing.add(line);
            }
        }
        assertTrue(missing.isEmpty(), () -> "tools.md quotes a line no run"
                + " writes:\n" + String.join("\n", missing) + "\nthe runs wrote:\n"
                + said);
    }
}
