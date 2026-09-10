package org.ymxs;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * What every document answers to whatever it says: one wrap width, and a
 * link that leads somewhere.
 *
 * <p>A figure in a document belongs to the test that owns it, and the
 * documents are found rather than listed, so a document that is there is
 * read.
 */
final class ShapeTest {

    /** The width AGENTS.md asks for, past which a line is wide. */
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
        List<String> wide = new ArrayList<>();
        for (Path at : documents) {
            List<String> lines = Files.readAllLines(at);
            boolean fenced = false;
            for (int line = 0; line < lines.size(); line++) {
                String said = lines.get(line);
                if (said.startsWith("```")) {
                    fenced = !fenced;
                    continue;
                }
                // A table's cells, a block of code and a link stand as
                // they are: none of the three rewraps.
                if (fenced || said.startsWith("|") || said.startsWith("    ")
                        || said.contains("](")) {
                    continue;
                }
                if (said.length() > WIDTH) {
                    wide.add(at + ":" + (line + 1) + " runs to " + said.length());
                }
            }
        }
        assertTrue(wide.isEmpty(), () -> String.join("\n", wide)
                + "\nAGENTS.md asks one width, kept.");
    }

    @Test
    void everyLinkLeadsSomewhere() throws IOException {
        List<String> broken = new ArrayList<>();
        for (Path at : documents()) {
            List<String> lines = Files.readAllLines(at);
            for (int line = 0; line < lines.size(); line++) {
                java.util.regex.Matcher links = java.util.regex.Pattern
                        .compile("\\]\\(([^)#:]+)(#[^)]*)?\\)").matcher(lines.get(line));
                while (links.find()) {
                    Path from = at.getParent();
                    Path to = (from == null ? Path.of(".") : from)
                            .resolve(links.group(1)).normalize();
                    if (!Files.exists(to)) {
                        broken.add(at + ":" + (line + 1) + " links to " + links.group(1));
                    }
                }
            }
        }
        assertTrue(broken.isEmpty(), () -> String.join("\n", broken));
    }
}
