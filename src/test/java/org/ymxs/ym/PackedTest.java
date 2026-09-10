package org.ymxs.ym;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.ymxs.Text;
import org.ymxs.Tunes;
import org.ymxs.YMXS.Multi;

/**
 * {@code bin/packed-ym-to-ymxs} run as a reader runs it.
 *
 * <p>What it unpacks is not read here: an archive cannot be made on every
 * machine, and a test that needs one to be there is a test that is
 * sometimes not run. What is read is everything around the unpacking:
 * which names are tunes and which are flags, a dump handed over as it
 * stands, several of them in one call, and what it says where a name is
 * not a dump at all.
 *
 * <p>The tools run out of {@code target/classes}, so this runs where they
 * have been built and says why where they have not.
 */
@EnabledIf("theToolsAreBuilt")
final class PackedTest {

    private static final Path SCRIPT = Path.of("bin/packed-ym-to-ymxs");

    /** Whether the tools can run without a build of their own, which one
     *  inside a build would be. */
    static boolean theToolsAreBuilt() {
        return Files.exists(Path.of("target/classes/org/ymxs/ym/Main.class"))
                && Files.exists(Path.of("target/classpath.txt"));
    }

    /** What the script said, and what it exited with. */
    private record Ran(int exit, String out, String said) { }

    private static Ran run(String... args) throws IOException, InterruptedException {
        List<String> whole = new ArrayList<>(List.of(SCRIPT.toString()));
        whole.addAll(List.of(args));
        Path out = Files.createTempFile("out", ".txt");
        Path said = Files.createTempFile("said", ".txt");
        Process ran = new ProcessBuilder(whole)
                .redirectOutput(out.toFile()).redirectError(said.toFile()).start();
        int exit = ran.waitFor();
        return new Ran(exit, Files.readString(out), Files.readString(said));
    }

    /** A dump of {@code frames} frames, written where a name says. */
    private static Path dump(String name, int frames) throws IOException {
        Path at = Files.createTempDirectory("ym").resolve(name);
        Files.write(at, new Dumps(frames, 0, List.of()).set(0, 0, 200).bytes());
        return at;
    }

    @Test
    void aDumpHandedOverAsItStandsIsRead() throws Exception {
        Path dump = dump("a plain dump.ym", 4);
        Path json = Files.createTempFile("tune", ".json");
        Ran ran = run(dump.toString(), json.toString());
        assertEquals(0, ran.exit(), ran.said());
        Multi multi = Text.read(Files.readString(json));
        assertEquals(1, multi.tunes().size());
        assertEquals(4, Tunes.size(multi.tunes().get(0).table()));
    }

    @Test
    void severalDumpsBecomeSeveralTunesInOneFile() throws Exception {
        Path one = dump("one.ym", 4);
        Path two = dump("two.ym", 6);
        Path json = Files.createTempFile("tunes", ".json");
        Ran ran = run(one.toString(), two.toString(), json.toString());
        assertEquals(0, ran.exit(), ran.said());
        Multi multi = Text.read(Files.readString(json));
        assertEquals(2, multi.tunes().size());
        assertEquals(4, Tunes.size(multi.tunes().get(0).table()));
        assertEquals(6, Tunes.size(multi.tunes().get(1).table()),
                "each name is a tune of its own, in the order they are named");
    }

    @Test
    void aFlagReachesTheReaderAndIsNotTakenForAName() throws Exception {
        Path dump = dump("once.ym", 4);
        Path json = Files.createTempFile("once", ".json");
        assertEquals(0, run(dump.toString(), json.toString(), "-r").exit());
        assertTrue(Files.readString(json).contains("\"repeat\": null"),
                "-r makes a tune that plays once");
    }

    @Test
    void aNameThatIsNoDumpIsSaidRatherThanRead() throws Exception {
        Ran ran = run("README.md", "/dev/null");
        assertEquals(1, ran.exit(), ran.out());
        assertTrue(ran.said().contains("holds no YM5! or YM6! dump"), ran.said());
    }

    @Test
    void aNameThatIsNotThereIsSaid() throws Exception {
        Ran ran = run("no-such-file.ym", "/dev/null");
        assertEquals(2, ran.exit(), ran.out());
        assertTrue(ran.said().contains("no such file"), ran.said());
    }

    @Test
    void aCallWithNoNamesSaysHowToCallIt() throws Exception {
        assertEquals(2, run().exit());
        assertTrue(run().said().startsWith("packed-ym-to-ymxs in.ym"), run().said());
    }
}
