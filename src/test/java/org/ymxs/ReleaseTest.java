package org.ymxs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * The release the documents name against the release the build is.
 *
 * <p>{@code pom.xml} has the version, {@code release/publish.sh} reads
 * it out of there, and three documents list it: what a reader runs
 * {@code go get} with, and the tag a version of the Go module has. They
 * were once a release apart: README.md fetched 0.3.3 of a module the pom
 * had at 0.3.4.
 *
 * <p>RELEASES.md also lists the version of the structure a writer writes
 * and the versions a reader reads, those of the newest release published,
 * which the release commit updates. SPEC.md, json.md and csv.md list the
 * version a writer of this tree writes and the versions it reads, which
 * the Java tree defines in {@link Json}.
 */
final class ReleaseTest {

    private static final Path POM = Path.of("pom.xml");

    private static final Path RELEASES = Path.of("doc/RELEASES.md");

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
                Pattern.MULTILINE).matcher(read(RELEASES));
        assertTrue(listed.find(), "RELEASES.md lists no release");
        assertEquals(version(), listed.group(1),
                "the newest release listed is the version the pom names");
    }

    /** The figures of a list such as {@code 7, 6, 5, 4 and 3}, in order. */
    private static List<Integer> figures(String list) {
        List<Integer> out = new ArrayList<>();
        Matcher figure = Pattern.compile("\\d+").matcher(list);
        while (figure.find()) {
            out.add(Integer.parseInt(figure.group()));
        }
        return out;
    }

    /** The versions from {@code newest} down to {@link Json#OLDEST}. */
    private static List<Integer> down(int newest) {
        List<Integer> out = new ArrayList<>();
        for (int version = newest; version >= Json.OLDEST; version--) {
            out.add(version);
        }
        return out;
    }

    /**
     * The paragraph above Published lists the versions of the newest
     * release published, whose entry has the sentence "The structure's
     * version is N": the structure's version and the version a writer
     * writes are N, at most {@link Json#VERSION}, and a reader reads N
     * down to {@link Json#OLDEST}. A release of a new version updates the
     * paragraph, so between that version's change and its release the
     * paragraph lists the release before it. Each run of white space reads
     * as one space, so a rewrapped paragraph reads as it did.
     */
    @Test
    void theVersionsOfTheStructureListedAreThoseOfTheNewestRelease() throws IOException {
        String releases = read(RELEASES);
        int published = releases.indexOf("\n## Published\n");
        assertTrue(published >= 0, "RELEASES.md has no Published section");
        Matcher said = Pattern.compile("the structure's version is (\\d+), a writer writes"
                + " (\\d+) and a reader reads ((?:\\d+(?:, | and ))*\\d+),")
                .matcher(spaced(releases.substring(0, published)));
        assertTrue(said.find(), "RELEASES.md lists no version a writer writes above"
                + " Published");
        Matcher entry = Pattern.compile("^### \\d+\\.\\d+\\.\\d+, ", Pattern.MULTILINE)
                .matcher(releases);
        assertTrue(entry.find(published), "RELEASES.md lists no release under Published");
        int from = entry.end();
        int to = entry.find() ? entry.start() : releases.length();
        Matcher newest = Pattern.compile("The structure's version is (\\d+)")
                .matcher(spaced(releases.substring(from, to)));
        assertTrue(newest.find(), "the newest release listed has no \"The structure's"
                + " version is N\"");
        int version = Integer.parseInt(newest.group(1));
        assertTrue(version <= Json.VERSION, "the newest release listed has version "
                + version + ", above the " + Json.VERSION + " of Json.VERSION");
        assertEquals(version, Integer.parseInt(said.group(1)), "RELEASES.md lists another"
                + " version of the structure than the newest release listed");
        assertEquals(version, Integer.parseInt(said.group(2)), "RELEASES.md lists another"
                + " version a writer writes than the newest release listed");
        assertEquals(down(version), figures(said.group(3)), "RELEASES.md lists other"
                + " versions a reader reads than the newest release listed down to"
                + " Json.OLDEST");
    }

    /** {@code said} with each run of white space one space. */
    private static String spaced(String said) {
        return said.replaceAll("\\s+", " ");
    }

    /** {@code path} from the clause that opens {@code clause} to the one
     *  that opens {@code next}, each run of white space one space. */
    private static String clause(String path, String clause, String next)
            throws IOException {
        String said = read(Path.of(path));
        int from = said.indexOf(clause);
        assertTrue(from >= 0, path + " has no " + clause);
        int to = said.indexOf(next, from);
        return spaced(said.substring(from, to < 0 ? said.length() : to));
    }

    /** A writer writes N and a reader reads N and the K versions before
     *  it, and the list of them where one follows. */
    private static final Pattern READS = Pattern.compile("(?i)a writer writes"
            + " (?:version )?(\\d+)[.,]? (?:and )?a reader reads (?:version )?(\\d+) and"
            + " the (\\w+) versions before it(?:, ((?:\\d+, )*\\d+ and \\d+))?");

    /**
     * The versions SPEC.md and json.md 2.4 list, against {@link Json}: a
     * writer writes {@link Json#VERSION}, and a reader reads it and the K
     * versions before it, K a word and the versions down to
     * {@link Json#OLDEST}, SPEC.md with the list of them.
     */
    @Test
    void theVersionsTheSpecificationsListAreTheVersionsOfTheJavaTree() throws IOException {
        int before = Json.VERSION - Json.OLDEST;
        List<Integer> older = down(Json.VERSION - 1);
        for (String[] one : new String[][] {{"doc/SPEC.md", "# The YMXS format",
                "**Conventions.**"}, {"doc/json.md", "**2.4**", "**2.5**"}}) {
            Matcher said = READS.matcher(clause(one[0], one[1], one[2]));
            assertTrue(said.find(), one[0] + " lists no version a writer writes and a reader"
                    + " reads");
            assertEquals(Json.VERSION, Integer.parseInt(said.group(1)), one[0]
                    + " lists another version a writer writes than Json.VERSION");
            assertEquals(Json.VERSION, Integer.parseInt(said.group(2)), one[0]
                    + " lists another first version a reader reads than Json.VERSION");
            assertEquals(Words.of(before), said.group(3), one[0] + " has \""
                    + said.group(3) + "\" for the versions before it, and they are "
                    + Words.of(before));
            if (one[0].equals("doc/SPEC.md")) {
                assertTrue(said.group(4) != null, one[0] + " lists no versions before it");
            }
            if (said.group(4) != null) {
                assertEquals(older, figures(said.group(4)), one[0] + " lists other versions"
                        + " before it than Json.VERSION - 1 down to Json.OLDEST");
            }
        }
        Matcher cell = Pattern.compile("the whole number (\\d+), or ((?:\\d+, )*\\d+ or"
                + " \\d+) for a file of a version before it")
                .matcher(clause("doc/csv.md", "**3.3**", "**3.4**"));
        assertTrue(cell.find(), "csv.md 3.3 lists no version of the version cell");
        assertEquals(Json.VERSION, Integer.parseInt(cell.group(1)),
                "csv.md 3.3 lists another version than Json.VERSION");
        assertEquals(older, figures(cell.group(2)), "csv.md 3.3 lists other versions"
                + " before it than Json.VERSION - 1 down to Json.OLDEST");
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
        for (Path at : new Path[] {Path.of("doc/tools.md"), RELEASES}) {
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
