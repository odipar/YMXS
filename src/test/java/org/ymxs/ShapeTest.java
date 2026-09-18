package org.ymxs;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.ymxs.doc.Documents;
import org.junit.jupiter.api.Test;

/**
 * What every document satisfies, independent of its content: one wrap
 * width, a link that resolves, and a clause cited in another document
 * that the document defines.
 *
 * <p>A figure in a document belongs to the test that owns it. The
 * documents are found rather than listed, so every document present is
 * read.
 */
final class ShapeTest {

    /** The width AGENTS.md requires, past which a line is wide. */
    private static final int WIDTH = 78;

    private static List<Path> documents() throws IOException {
        try (Stream<Path> tree = Files.walk(Path.of("."))) {
            return tree.filter(Files::isRegularFile)
                    .filter(at -> at.toString().endsWith(".md"))
                    .filter(at -> !at.toString().contains("/target/"))
                    .filter(at -> !at.toString().contains("/.git"))
                    .sorted()
                    .toList();
        }
    }

    @Test
    void everyDocumentKeepsOneWrapWidth() throws IOException {
        List<Path> documents = documents();
        assertTrue(documents.size() > 2, () -> "only " + documents.size()
                + " documents read; the check is asleep");
        List<String> wide = Documents.wide(documents, 78);
        assertTrue(wide.isEmpty(), () -> String.join("\n", wide)
                + "\nAGENTS.md gives one wrap width, and a document keeps it.");
    }

    /** The clause numbers a document defines: its numbered headings and
     *  the bold number that opens a clause. */
    private static Set<String> clauses(Path at) throws IOException {
        Set<String> out = new TreeSet<>();
        String said = Files.readString(at);
        Matcher heading = Pattern.compile("^#{1,4} (\\d+(?:\\.\\d+)*)\\.?\\s",
                Pattern.MULTILINE).matcher(said);
        while (heading.find()) {
            out.add(heading.group(1));
        }
        Matcher bold = Pattern.compile("^\\*\\*(\\d+(?:\\.\\d+)*)\\b",
                Pattern.MULTILINE).matcher(said);
        while (bold.find()) {
            out.add(bold.group(1));
        }
        return out;
    }

    /**
     * Every clause one document cites in another is a clause that
     * document defines. A citation of a document this repository does not
     * have is another repository's and is left alone, and RELEASES.md
     * records what was true at a release, so a clause renumbered after one
     * leaves its entry as it was.
     */
    @Test
    void everyClauseCitedInAnotherDocumentIsDefined() throws IOException {
        Map<Path, Set<String>> defined = new java.util.LinkedHashMap<>();
        List<String> dangling = new ArrayList<>();
        int read = 0;
        for (Path at : documents()) {
            if (at.getFileName().toString().equals("RELEASES.md")) {
                continue;
            }
            String said = Files.readString(at);
            Matcher cited = Pattern.compile("([A-Za-z_]+)\\.md\\)? (\\d+(?:\\.\\d+)*)")
                    .matcher(said);
            while (cited.find()) {
                Path in = Path.of("doc", cited.group(1) + ".md");
                if (!Files.exists(in)) {
                    in = Path.of(cited.group(1) + ".md");
                }
                if (!Files.exists(in)) {
                    continue;
                }
                if (!defined.containsKey(in)) {
                    defined.put(in, clauses(in));
                }
                read++;
                if (!defined.get(in).contains(cited.group(2))) {
                    dangling.add(at + " cites " + cited.group());
                }
            }
        }
        final int opened = read;
        assertTrue(opened > 40, () -> "only " + opened
                + " citations read; the check is asleep");
        assertTrue(dangling.isEmpty(), () -> String.join("\n", dangling));
    }

    @Test
    void everyLinkLeadsSomewhere() throws IOException {
        List<String> broken = Documents.links(documents());
        assertTrue(broken.isEmpty(), () -> String.join("\n", broken));
    }
}
