package org.ymxs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The example tune against the documents that quote it.
 *
 * <p>json.md 10.1 quotes {@code doc/tunes/example.json} whole and csv.md
 * 8.1 quotes {@code doc/tunes/example.csv} whole. A quoted file is a
 * second copy of it, and a reader of the form reads that copy, so
 * this requires each block equal to the file it quotes: a tune written
 * again moves both.
 *
 * <p>{@code RecordTest} reads the record of that tune out of SPEC.md 7.6
 * the same way, and {@code ParityTest} crosses every tune of
 * {@code doc/tunes} both ways.
 */
final class ExampleTest {

    /** The first fenced block of the document, its fences off. */
    private static String fenced(Path at) throws IOException {
        List<String> lines = Files.readAllLines(at);
        int from = -1;
        for (int line = 0; line < lines.size() && from < 0; line++) {
            if (lines.get(line).startsWith("```") && lines.get(line).length() > 3) {
                from = line;
            }
        }
        assertTrue(from >= 0, at + " has no fenced block with a language on it");
        StringBuilder said = new StringBuilder();
        for (int line = from + 1; line < lines.size(); line++) {
            if (lines.get(line).startsWith("```")) {
                return said.toString().strip();
            }
            said.append(lines.get(line)).append('\n');
        }
        throw new AssertionError(at + " has a fenced block that does not end");
    }

    /** The block after the clause named, its fences off. */
    private static String quoted(Path at, String clause) throws IOException {
        List<String> lines = Files.readAllLines(at);
        int from = -1;
        for (int line = 0; line < lines.size(); line++) {
            if (lines.get(line).startsWith(clause)) {
                from = line;
            }
        }
        assertTrue(from >= 0, at + " has no clause " + clause);
        StringBuilder said = new StringBuilder();
        boolean inside = false;
        for (int line = from; line < lines.size(); line++) {
            if (lines.get(line).startsWith("```")) {
                if (inside) {
                    return said.toString().strip();
                }
                inside = true;
                continue;
            }
            if (inside) {
                said.append(lines.get(line)).append('\n');
            }
        }
        throw new AssertionError(at + " quotes nothing under " + clause);
    }

    @Test
    void theJsonTheDocumentQuotesIsTheFile() throws IOException {
        assertEquals(Files.readString(Path.of("doc/tunes/example.json")).strip(),
                fenced(Path.of("doc/json.md")),
                "json.md 10.1 quotes another tune than doc/tunes/example.json");
    }

    @Test
    void theCsvTheDocumentQuotesIsTheFile() throws IOException {
        assertEquals(Files.readString(Path.of("doc/tunes/example.csv")).strip(),
                quoted(Path.of("doc/csv.md"), "**8.1**"),
                "csv.md 8.1 quotes another tune than doc/tunes/example.csv");
    }
}
