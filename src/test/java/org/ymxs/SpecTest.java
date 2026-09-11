package org.ymxs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The Java in doc/SPEC.md against the source it is quoted from.
 *
 * <p>Section 1 is the tune data structure, and the declarations are the
 * specification of it. A block set apart from the source is a second
 * specification, so the block is {@code YMXS.java} with the javadoc off
 * and this reads the two against each other: an added record, a renamed
 * component or a rewrapped line fails here until the document has it.
 *
 * <p>This replaces a check that read the block with a regular expression
 * a declaration at a time and compared each against the type by
 * reflection. That parser had to be taught every turn of Java syntax the
 * source used, and a block it could parse was not therefore a block that
 * compiles. Equality with the source settles both at once.
 */
final class SpecTest {

    private static final Path SPEC = Path.of("doc/SPEC.md");
    private static final Path SOURCE = Path.of("src/main/java/org/ymxs/YMXS.java");

    /** The Java block of the document, its fences off. */
    private static String quoted() throws IOException {
        List<String> lines = Files.readAllLines(SPEC);
        int from = lines.indexOf("```java");
        assertTrue(from >= 0, SPEC + " has no Java block");
        int to = lines.subList(from + 1, lines.size()).indexOf("```") + from + 1;
        assertTrue(to > from, SPEC + " has a Java block that does not end");
        return String.join("\n", lines.subList(from + 1, to)).strip() + "\n";
    }

    /** The source with the javadoc off, and no run of blank lines where a
     *  block came out. */
    private static String declared() throws IOException {
        List<String> out = new ArrayList<>();
        boolean skip = false;
        for (String line : Files.readAllLines(SOURCE)) {
            String said = line.strip();
            if (said.startsWith("/*")) {
                skip = true;
            }
            if (skip) {
                if (said.endsWith("*/")) {
                    skip = false;
                }
                continue;
            }
            if (line.isBlank() && !out.isEmpty() && out.get(out.size() - 1).isBlank()) {
                continue;
            }
            out.add(line);
        }
        return String.join("\n", out).strip() + "\n";
    }

    @Test
    void theBlockInSectionOneIsTheSourceWithTheJavadocOff() throws IOException {
        assertEquals(declared(), quoted(),
                SPEC + " section 1 is not " + SOURCE + " with the javadoc off");
    }

    /** The listing is an entry point for the reader, and the records are
     *  the specification. A second copy of it is a second specification,
     *  and this is where the one copy stands. */
    @Test
    void theRecordsAreListedInOneDocument() throws IOException {
        List<String> also = new ArrayList<>();
        try (Stream<Path> tree = Files.walk(Path.of("."))) {
            for (Path at : tree.filter(Files::isRegularFile)
                    .filter(one -> one.toString().endsWith(".md"))
                    .filter(one -> !one.toString().contains("/target/"))
                    .sorted().toList()) {
                if (at.normalize().equals(SPEC.normalize())) {
                    continue;
                }
                String said = Files.readString(at);
                if (said.contains("record Row(") || said.contains("sealed interface Effect")) {
                    also.add(at.toString());
                }
            }
        }
        assertTrue(also.isEmpty(), () -> "the records are listed in " + also
                + " as well as in " + SPEC + ", which is one thing said twice");
    }

    /** The point of quoting the source: the block compiles as it stands.
     *  A record with no body, an enum written with an ellipsis or a
     *  missing import would not, and each of those stood here before. */
    @Test
    void theBlockIsJavaThatCompiles() throws IOException {
        String java = quoted();
        assertTrue(java.startsWith("package org.ymxs;"), "the block opens with the package");
        for (String needed : List.of("import java.util.List;", "import java.util.Map;",
                "import java.util.OptionalInt;")) {
            assertTrue(java.contains(needed), "the block imports what it names: " + needed);
        }
        assertTrue(!java.contains("..."), "an ellipsis is not Java: write the constants out");
        for (String line : java.lines().toList()) {
            String said = line.strip();
            if (said.startsWith("record ") || said.startsWith("sealed interface ")) {
                assertTrue(said.endsWith("{ }") || said.endsWith("(")
                        || said.endsWith(",") || said.contains("{ }"),
                        "a declaration on one line has a body: " + said);
            }
        }
        // Braces balance, so the interface and every body it has close.
        long open = java.chars().filter(c -> c == '{').count();
        long close = java.chars().filter(c -> c == '}').count();
        assertEquals(open, close, "the braces of the block balance");
    }
}
