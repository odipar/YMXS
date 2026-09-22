package org.ymxs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
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

    /**
     * Every line a table of a document reports reads the same in the two
     * trees: a line reworded in one tree and the document, or in the
     * document alone, fails here. The letters a table writes for a
     * figure, V or N or B, and the figures a tool builds a line from
     * stand outside the comparison, and this reads the words around
     * them.
     *
     * <p>The check came from YMXR, where a release took a descriptor's
     * version to 2 in one clause and left another reading 1.
     */
    @Test
    void everyLineATableReportsReadsTheSameInBothTrees() throws IOException {
        String java = tree(Path.of("src/main/java/org/ymxs"), ".java");
        String go = tree(Path.of("go"), ".go");
        int read = 0;
        for (Path at : List.of(Path.of("doc/SPEC.md"), Path.of("doc/tools.md"),
                Path.of("doc/json.md"), Path.of("doc/csv.md"), Path.of("doc/ym.md"))) {
            for (Reported one : reported(Files.readString(at))) {
                String part = longest(one.said());
                if (part.isEmpty()) {
                    continue;
                }
                boolean inJava = java.contains(part);
                boolean inGo = go.contains(part);
                if (one.tree() == null) {
                    // a line of a table that names no tree: both write it,
                    // or it is a line of the shared tool or of a script
                    if (!inJava) {
                        continue;
                    }
                    read++;
                    assertTrue(inGo, at + " reports \"" + one.said() + "\" and the Go tree"
                            + " lacks \"" + part + "\"");
                    continue;
                }
                // a table of the two trees against one another: the
                // column names which tree writes the line
                if (one.tree().equals("Java") ? inJava : inGo) {
                    read++;
                }
            }
        }
        assertTrue(read >= 20, "the tables report " + read + " lines of the tools");
    }

    /** A line a table reports, and the tree its column names, or null
     *  where the table names none. */
    private record Reported(String said, @Nullable String tree) {
    }

    /** The longest run of words of a line between the figures a tool
     *  writes into it, and the empty text where the line is figures and
     *  short runs. */
    private static String longest(String said) {
        String longest = "";
        for (String part : said.split("\\b[A-Zi]\\b|[0-9][0-9,]*")) {
            String one = part.strip();
            if (one.length() >= 12 && one.length() > longest.length()) {
                longest = one;
            }
        }
        return longest;
    }

    /** The lines the tables of a document report: a code span of three
     *  words or more that opens in lower case, in the last cell of a
     *  row, or in the cell of a tree where the table's header names the
     *  Java tree and the Go tree. */
    private static List<Reported> reported(String document) {
        List<Reported> out = new ArrayList<>();
        String[] header = new String[0];
        for (String line : document.split("\n")) {
            String row = line.strip();
            if (!row.startsWith("|") || !row.endsWith("|")) {
                header = new String[0];
                continue;
            }
            String[] cells = row.substring(1, row.length() - 1).split("\\|");
            if (header.length == 0) {
                header = cells;
                continue;
            }
            if (cells.length > 0 && cells[0].strip().startsWith("---")) {
                continue;
            }
            for (int i = 0; i < cells.length; i++) {
                String tree = null;
                for (int of = 0; of < header.length && of < cells.length; of++) {
                    if (of == i && header[of].contains("Java")) {
                        tree = "Java";
                    } else if (of == i && header[of].contains("Go")) {
                        tree = "Go";
                    }
                }
                if (tree == null && i != cells.length - 1) {
                    continue;
                }
                Matcher said = Pattern.compile("`([^`]+)`").matcher(cells[i]);
                while (said.find()) {
                    String one = said.group(1);
                    if (one.split("\\s+").length >= 3 && Character.isLowerCase(one.charAt(0))) {
                        out.add(new Reported(one, tree));
                    }
                }
            }
        }
        return out;
    }

    /** Every source of a tree, read as one text, a line built from two
     *  strings read as one. */
    private static String tree(Path at, String ending) throws IOException {
        StringBuilder out = new StringBuilder();
        try (Stream<Path> found = Files.walk(at)) {
            for (Path one : found.filter(p -> p.toString().endsWith(ending)).toList()) {
                out.append(Files.readString(one)).append('\n');
            }
        }
        return out.toString().replaceAll("\"\\s*\\+\\s*\"", "");
    }

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

    /** The clauses one document defines: `**N.N**` and `## N.N`, a section
     *  number standing for itself and for the clauses under it. */
    private static Set<String> clausesOf(String said) {
        Set<String> out = new HashSet<>();
        Matcher m = Pattern.compile("(?m)^(?:\\*\\*|#+ )R?(\\d+(?:\\.\\d+)*)").matcher(said);
        while (m.find()) {
            String clause = m.group(1);
            out.add(clause);
            for (int dot = clause.indexOf('.'); dot > 0; dot = clause.indexOf('.', dot + 1)) {
                out.add(clause.substring(0, dot));
            }
        }
        return out;
    }

    /**
     * Every citation of a specification lands on a clause of it.
     *
     * <p>A citation in these documents is the clause in brackets, `(2.2)`,
     * and a reader follows it. 3.1.1 cited a 2.6 no document has until the
     * first reader of the conformance kit found it, with the documents
     * alone; this reads every citation at once, so one that lands nowhere
     * is named where it is written.
     */
    @Test
    void everyCitationLandsOnAClause() throws IOException {
        List<String> wrong = new ArrayList<>();
        for (Path at : List.of(Path.of("doc/SPEC.md"), Path.of("doc/json.md"),
                Path.of("doc/csv.md"), Path.of("doc/ym.md"))) {
            String said = Files.readString(at);
            Set<String> clauses = clausesOf(said);
            Matcher m = Pattern.compile("\\((\\d+(?:\\.\\d+){1,3})\\)").matcher(said);
            while (m.find()) {
                if (!clauses.contains(m.group(1))) {
                    wrong.add(at + " cites (" + m.group(1) + "), which is no clause of it");
                }
            }
        }
        assertTrue(wrong.isEmpty(), String.join("\n", wrong));
    }
}
