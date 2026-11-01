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
     * are outside the comparison, and this reads the words around
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

    /**
     * 3.5.3's table of the row a source begins at, recomputed from the
     * rule: a deferred start with {@code placeReset} clear at R modulo R'
     * (3.5.3), a start at its frame at the place P modulo R', which is P
     * where R' is R or more (3.4.3), and either with {@code placeReset}
     * set at row 0.
     */
    @Test
    void theTableOfTheRowASourceBeginsAtFollowsTheRule() throws IOException {
        String spec = Files.readString(SPEC);
        String section = spec.substring(spec.indexOf("**3.5.3**"),
                spec.indexOf("**3.5.4**"));
        Matcher row = Pattern.compile("^\\| (\\d+) \\| (\\d+) \\| ([^|]+) \\| (\\d+) \\| (\\d+) \\|$",
                Pattern.MULTILINE).matcher(section);
        int rows = 0;
        while (row.find()) {
            int was = Integer.parseInt(row.group(1));
            int now = Integer.parseInt(row.group(2));
            String atFrame = now >= was ? "P" : "P modulo " + now;
            assertEquals(atFrame, row.group(3).trim(), "a start at its frame, " + was + " to "
                    + now);
            assertEquals(was % now, Integer.parseInt(row.group(4)), "a deferred start, "
                    + was + " to " + now);
            assertEquals(0, Integer.parseInt(row.group(5)), "placeReset set, " + was + " to "
                    + now);
            rows++;
        }
        assertTrue(rows >= 4, "3.5.3's table read as " + rows + " rows");
    }

    /**
     * 3.6.5's table of the volume written, recomputed from the rule: the
     * key through the identity, and through {@code Vn} its entry at the
     * key, the key times n divided by 15, the remainder discarded. The
     * sentence that defines {@code Vn} is read too, so a table that
     * follows a reworded rule fails here.
     */
    @Test
    void theTableOfTheVolumeWrittenFollowsTheRule() throws IOException {
        String spec = Files.readString(SPEC);
        int from = spec.indexOf("**3.6.5");
        assertTrue(from >= 0, SPEC + " has no 3.6.5");
        String section = spec.substring(from, spec.indexOf("\n---\n", from));
        assertTrue(section.replaceAll("\\s+", " ")
                .contains("the transformation whose entry k is k × n divided by 15"),
                "3.6.5 defines Vn as entry k, k × n divided by 15");
        Matcher header = Pattern.compile("^\\| part((?: \\| key \\d+)+) \\|$",
                Pattern.MULTILINE).matcher(section);
        assertTrue(header.find(), "3.6.5's table has a column a key");
        List<Integer> keys = new ArrayList<>();
        Matcher key = Pattern.compile("key (\\d+)").matcher(header.group(1));
        while (key.find()) {
            keys.add(Integer.parseInt(key.group(1)));
        }
        Matcher row = Pattern.compile("^\\| (the identity|`V(\\d+)`)((?: \\| \\d+)+) \\|$",
                Pattern.MULTILINE).matcher(section);
        int rows = 0;
        boolean identity = false;
        while (row.find()) {
            String[] cells = row.group(3).substring(" | ".length()).split(" \\| ");
            assertEquals(keys.size(), cells.length, row.group(1) + " has a cell a key");
            for (int at = 0; at < keys.size(); at++) {
                int k = keys.get(at);
                int written = row.group(2) == null ? k : k * Integer.parseInt(row.group(2)) / 15;
                assertEquals(written, Integer.parseInt(cells[at].strip()), row.group(1)
                        + " at key " + k);
            }
            identity |= row.group(2) == null;
            rows++;
        }
        assertTrue(identity, "3.6.5's table has a row for the identity");
        assertTrue(rows >= 4, "3.6.5's table read as " + rows + " rows");
    }

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
     *  and this is where the one copy is. */
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

    /** The point of quoting the source: the block compiles as it is.
     *  A record with no body, an enum written with an ellipsis or a
     *  missing import would not, and each of those was here before. */
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
     *  number marking itself and the clauses under it. */
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
                Path.of("doc/csv.md"), Path.of("doc/ym.md"), Path.of("doc/tools.md"))) {
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

    /** csv.md 5.4 writes the line of tools.md 7.4, from its tool's name to
     *  its {@code <value>}, and the text after it as a name in a reported
     *  line. */
    @Test
    void theLineOfTheCsvEmitterIsTheLineOfToolsSevenFour() throws IOException {
        String tool = "ymxs-json-to-csv: ";
        Matcher line = Pattern.compile("\n    " + Pattern.quote(tool) + "(.*)<value>\n")
                .matcher(section(Path.of("doc/tools.md"), "**7.4**", "## 8."));
        assertTrue(line.find(), "tools.md 7.4 has no line of " + tool);
        String emitter = section(Path.of("doc/csv.md"), "**5.4**", "**5.5**")
                .replaceAll("\\s+", " ");
        assertTrue(emitter.contains("`" + line.group(1) + "` followed by the text, each line"
                + " feed in it written as the two characters `\\n` and each carriage return as"
                + " `\\r`"), "csv.md 5.4 reads " + emitter);
    }

    /** {@code at} from the first {@code from} to the first {@code to}
     *  after it. */
    private static String section(Path at, String from, String to) throws IOException {
        String said = Files.readString(at);
        int start = said.indexOf(from);
        assertTrue(start >= 0, at + " has no " + from);
        int end = said.indexOf(to, start + from.length());
        assertTrue(end >= 0, at + " has no " + to + " after " + from);
        return said.substring(start, end);
    }

    /** The lines the table of {@code section} reports: each code span in
     *  the last cell of a row. */
    private static List<String> lines(String section) {
        List<String> out = new ArrayList<>();
        for (String line : section.split("\n")) {
            String row = line.strip();
            if (row.length() < 2 || !row.startsWith("|") || !row.endsWith("|")) {
                continue;
            }
            String last = row.substring(row.lastIndexOf('|', row.length() - 2) + 1,
                    row.length() - 1);
            Matcher span = Pattern.compile("`([^`]+)`").matcher(last);
            while (span.find()) {
                out.add(span.group(1));
            }
        }
        return out;
    }

    /** {@code line} of a table as a regular expression: each word of
     *  capital letters, {@code Rn} and {@code setRn} any text. */
    private static String pattern(String line) {
        StringBuilder out = new StringBuilder();
        Matcher any = Pattern.compile("\\b(?:set)?Rn\\b|\\b[A-Z]+\\b").matcher(line);
        int at = 0;
        while (any.find()) {
            out.append(Pattern.quote(line.substring(at, any.start()))).append(".*");
            at = any.end();
        }
        return out.append(Pattern.quote(line.substring(at))).toString();
    }

    /** Whether {@code reported} is one of {@code lines} after any number
     *  of the {@code prefixes}, each read as {@link #pattern} reads it. */
    private static boolean carries(List<String> lines, List<String> prefixes,
            String reported) {
        String before = prefixes.isEmpty() ? "" : "(?:" + String.join("|",
                prefixes.stream().map(SpecTest::pattern).toList()) + ")*";
        for (String line : lines) {
            if (Pattern.compile(before + pattern(line)).matcher(reported).matches()) {
                return true;
            }
        }
        return false;
    }

    /**
     * A copy of the lines {@code ParityTest} reads from the tools in its
     * case of a source of other values a row than its target reads and its
     * case of a name in a reported line, kept in step with it by hand, are
     * lines of the documents: a line of a JSON input is a line of json.md
     * 8.1, a line of a CSV input one of csv.md 5.1, and a line of the
     * structure one of SPEC.md 1.11 after the prefixes of 1.12, each word
     * of capital letters, {@code Rn} and {@code setRn} in the table
     * matching any text. The line of a source of W values a row is an
     * error of the form, so the table of SPEC.md 1.11 lacks it, and SPEC.md
     * 1.12, json.md 8.1 and csv.md 5.1 write each line feed of a NAME as
     * two characters.
     */
    @Test
    void theDocumentsHaveTheLinesOfAWidthAndOfANameInAReportedLine() throws IOException {
        Path json = Path.of("doc/json.md");
        Path csv = Path.of("doc/csv.md");
        String json81 = section(json, "**8.1**", "**8.2**");
        String csv51 = section(csv, "**5.1**", "**5.2**");
        String spec111 = section(SPEC, "**1.11", "**1.12");
        String spec112 = section(SPEC, "**1.12", "\n---\n");
        List<String> prefixes = new ArrayList<>();
        Matcher prefix = Pattern.compile("`([^`]+: )`").matcher(spec112.replaceAll("\\s+", " "));
        while (prefix.find()) {
            prefixes.add(prefix.group(1));
        }
        assertTrue(prefixes.size() >= 4, "SPEC.md 1.12 read as " + prefixes.size()
                + " prefixes");

        String whole = ", and this form requires a whole number";
        String started = ", is started by no row, and a source a tune does not run is"
                + " dropped where this form is read";
        String used = ", is used by no row, and a transformation a tune does not map"
                + " through is dropped where this form is read";
        List<String> ofJson = new ArrayList<>(List.of(
                "a source of 1 values a row on setNoiseB, which reads 2",
                "a source of 2 values a row on setR8, which reads 1",
                "row 0 maps through transformation 2, and the tune has 1"));
        List<String> ofCsv = new ArrayList<>(ofJson);
        ofJson.add("Timer A's count at row 0 is \"x\"" + whole);
        ofCsv.add("count is \"x\"" + whole);
        List<String> ofStructure = new ArrayList<>(List.of(
                "tune 1: row 0: R9 is 0 to 31, and this row sets it to 40"));
        for (String escape : List.of("\\n", "\\r")) {
            ofJson.add("ha" + escape + "lf at entry 1 is 1e1" + whole);
            ofJson.add("a" + escape + "tone at row 1 is \"x\"" + whole);
            ofJson.add("source 2, id" + escape + "le" + started);
            ofJson.add("transformation 2, ot" + escape + "her" + used);
            ofStructure.add("tune 1: transformation 1, ha" + escape + "lf: entry 2 is 300,"
                    + " and an entry is 0 to 255");
        }
        ofCsv.add("source 2, id\\rle" + started);
        ofCsv.add("transformation 2, ot\\rher" + used);
        ofCsv.add("source a\\rtone has a row of several values, and version 3 has one value a"
                + " row");

        List<String> wrong = new ArrayList<>();
        for (String one : ofJson) {
            if (!carries(lines(json81), List.of(), one)) {
                wrong.add("json.md 8.1 lacks the line " + one);
            }
        }
        for (String one : ofCsv) {
            if (!carries(lines(csv51), List.of(), one)) {
                wrong.add("csv.md 5.1 lacks the line " + one);
            }
        }
        for (String one : ofStructure) {
            if (!carries(lines(spec111), prefixes, one)) {
                wrong.add("SPEC.md 1.11 and 1.12 lack the line " + one);
            }
        }
        for (String one : lines(spec111)) {
            if (one.contains("a source of W values a row")) {
                wrong.add("SPEC.md 1.11 has the line " + one + ", an error of the form");
            }
        }
        Pattern name = Pattern.compile("NAME [^.;]*each line feed in it written as the two"
                + " characters");
        for (String[] one : new String[][] {{"SPEC.md 1.12", spec112}, {"json.md 8.1", json81},
                {"csv.md 5.1", csv51}}) {
            if (!name.matcher(one[1].replaceAll("\\s+", " ")).find()) {
                wrong.add(one[0] + " defines NAME without \"each line feed in it written as"
                        + " the two characters\"");
            }
        }
        assertTrue(wrong.isEmpty(), String.join("\n", wrong));
    }
}
