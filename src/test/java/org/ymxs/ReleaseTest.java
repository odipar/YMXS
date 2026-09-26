package org.ymxs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * The release the documents name against the release the build is.
 *
 * <p>{@code pom.xml} has the version, {@code release/publish.sh} reads
 * it out of there, and three documents write it down: what a reader runs
 * {@code go get} with, and the tag a version of the Go module has. They moved a release apart: README.md fetched 0.3.3 of a module
 * the pom had at 0.3.4.
 */
final class ReleaseTest {

    private static final Path POM = Path.of("pom.xml");

    private static String read(Path at) throws IOException {
        return Files.readString(at);
    }

    /** The version of this repository, the first one the pom names. */
    private static String version() throws IOException {
        Matcher said = Pattern.compile("<version>([^<]+)</version>").matcher(read(POM));
        assertTrue(said.find(), "pom.xml names no version");
        return said.group(1);
    }

    @Test
    void theNewestReleaseListedIsTheVersionOfTheBuild() throws IOException {
        Matcher listed = Pattern.compile("^### (\\d+\\.\\d+\\.\\d+), ",
                Pattern.MULTILINE).matcher(read(Path.of("doc/RELEASES.md")));
        assertTrue(listed.find(), "RELEASES.md lists no release");
        assertEquals(version(), listed.group(1),
                "the newest release listed is the version the pom names");
    }

    @Test
    void theModuleAReaderFetchesIsTheVersionOfTheBuild() throws IOException {
        Matcher said = Pattern.compile("go get github\\.com/odipar/ymxs/go@v(\\S+)")
                .matcher(read(Path.of("README.md")));
        assertTrue(said.find(), "README.md fetches no module");
        assertEquals(version(), said.group(1),
                "README.md fetches another version than the pom names");
    }

    @Test
    void everyTagTheDocumentsShowIsTheVersionOfTheBuild() throws IOException {
        int shown = 0;
        for (Path at : new Path[] {Path.of("doc/tools.md"), Path.of("doc/RELEASES.md")}) {
            Matcher tag = Pattern.compile("`go/v(\\d+\\.\\d+\\.\\d+)` beside `v(\\d+\\.\\d+\\.\\d+)`")
                    .matcher(read(at));
            while (tag.find()) {
                shown++;
                assertEquals(version(), tag.group(1), at + " shows another module tag");
                assertEquals(version(), tag.group(2), at + " shows another release tag");
            }
        }
        final int found = shown;
        assertTrue(found >= 2, () -> "only " + found
                + " tags read; the check is asleep");
    }
}
