package org.ymxs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
        throw new AssertionError(at + " lacks a quote under " + clause);
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

    /** The rows and mapping numbers of json.md 10.3, read from its table. */
    @Test
    void theFadeTableMatchesTheTuneFile() throws IOException {
        String document = Files.readString(Path.of("doc/json.md"));
        String section = document.substring(document.indexOf("**10.3"));
        JsonNode tune = new ObjectMapper().readTree(
                Files.readString(Path.of("doc/tunes/fade.json")))
                .get("tunes").get(0);
        JsonNode a = tune.get("timerA");
        JsonNode b = tune.get("timerB");
        java.util.regex.Matcher rows = java.util.regex.Pattern.compile(
                "^\\| (\\d+)(?: to (\\d+))? \\| ([^|]+) \\| ([^|]+) \\|$",
                java.util.regex.Pattern.MULTILINE).matcher(section);
        int read = 0;
        while (rows.find()) {
            int first = Integer.parseInt(rows.group(1));
            int last = rows.group(2) == null ? first : Integer.parseInt(rows.group(2));
            String operation = rows.group(3).strip();
            java.util.regex.Matcher mappings = java.util.regex.Pattern
                    .compile("`(\\[[0-9,]+\\]|-1)`").matcher(rows.group(4));
            assertTrue(mappings.find(), "a mapping in " + rows.group());
            JsonNode mapping = new ObjectMapper().readTree(mappings.group(1));
            if (operation.startsWith("start")) {
                JsonNode timer = operation.contains("Timer B") ? b : a;
                assertEquals(0, timer.get("shape").get(first).intValue());
                assertEquals(mapping, timer.get("mapping").get(first));
            } else if (operation.startsWith("remap")) {
                assertTrue(mappings.find(), "the last mapping in " + rows.group());
                int number = mapping.get(0).intValue();
                int end = new ObjectMapper().readTree(mappings.group(1)).get(0).intValue();
                assertEquals(end - number, last - first);
                for (int row = first; row <= last; row++) {
                    assertEquals(3, a.get("shape").get(row).intValue());
                    assertEquals(number + row - first,
                            a.get("mapping").get(row).get(0).intValue());
                }
            } else {
                assertTrue(operation.startsWith("stop"), operation);
                assertEquals(2, a.get("shape").get(first).intValue());
                assertEquals(mapping, a.get("mapping").get(first));
            }
            read++;
        }
        assertEquals(4, read, "json.md 10.3 lists the start, remaps, stop and chord");
    }
}
