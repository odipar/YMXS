package org.ymxs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * SPEC.md's listing of the records, read back against the records.
 *
 * <p>The records are the specification and the listing is a reader's way
 * in, so the listing is one thing said twice. This keeps the second true:
 * every declaration in it is matched against the type it gives, component
 * for component, and a type in the package that the listing leaves out
 * fails as loudly as one it gets wrong.
 *
 * <p>The listing had gone stale within a day of being written in two
 * places, which is why it is written in one and checked here.
 */
final class StructureTest {

    /** The document that lists the records. */
    private static final Path SPEC = Path.of("doc/SPEC.md");

    /** The interface the structure is nested in. */
    private static final String WRAPPER = "YMXS";

    /** One declaration read out of the listing. */
    private record Said(String kind, String name, List<String> parts) { }

    @Test
    void theListingGivesEveryTypeTheStructureHas() throws IOException {
        Set<String> listed = new LinkedHashSet<>();
        for (Said said : listing()) {
            if (!said.name().equals(WRAPPER)) {
                listed.add(said.name());
            }
        }
        Set<String> declared = new LinkedHashSet<>();
        for (Class<?> one : YMXS.class.getDeclaredClasses()) {
            declared.add(one.getSimpleName());
        }
        List<String> missing = new ArrayList<>(declared);
        missing.removeAll(listed);
        List<String> extra = new ArrayList<>(listed);
        extra.removeAll(declared);
        assertTrue(missing.isEmpty(), () -> SPEC + " does not list " + missing);
        assertTrue(extra.isEmpty(), () -> SPEC + " lists " + extra
                + ", which the package does not have");
    }

    @Test
    void everyDeclarationMatchesItsRecord() throws IOException {
        List<Said> listing = listing();
        assertTrue(listing.size() > 10, () -> "only " + listing.size()
                + " declarations read; the check is asleep");
        List<String> wrong = new ArrayList<>();
        for (Said said : listing) {
            if (said.name().equals(WRAPPER)) {
                continue;
            }
            Class<?> type;
            try {
                type = Class.forName("org.ymxs.YMXS$" + said.name());
            } catch (ClassNotFoundException none) {
                wrong.add(said.name() + " is listed and " + WRAPPER + " has no such type");
                continue;
            }
            List<String> parts = switch (said.kind()) {
                case "record" -> components(type);
                case "sealed" -> permitted(type);
                default -> constants(type, said.parts());
            };
            if (!parts.equals(said.parts())) {
                wrong.add(said.kind() + " " + said.name() + ": the listing gives "
                        + said.parts() + " and the type gives " + parts);
            }
        }
        assertTrue(wrong.isEmpty(), () -> String.join("\n", wrong));
    }

    /** A record's components, each its type and its name as the listing
     *  writes them. */
    private static List<String> components(Class<?> type) {
        List<String> out = new ArrayList<>();
        RecordComponent[] parts = type.getRecordComponents();
        if (parts == null) {
            return List.of("not a record");
        }
        for (RecordComponent one : parts) {
            out.add(plain(one.getGenericType().getTypeName()) + " " + one.getName());
        }
        return out;
    }

    /** A sealed interface's permitted types, in the order it permits
     *  them. */
    private static List<String> permitted(Class<?> type) {
        List<String> out = new ArrayList<>();
        Class<?>[] permits = type.getPermittedSubclasses();
        if (permits == null) {
            return List.of("not sealed");
        }
        for (Class<?> one : permits) {
            out.add(plain(one.getTypeName()));
        }
        return out;
    }

    /** An enum's constants: the first and the last where the listing
     *  writes an ellipsis, and all of them where it does not. */
    private static List<String> constants(Class<?> type, List<String> said) {
        Object[] all = type.getEnumConstants();
        if (all == null) {
            return List.of("not an enum");
        }
        List<String> out = new ArrayList<>();
        for (Object one : all) {
            out.add(String.valueOf(one));
        }
        if (said.size() == 3 && said.get(1).equals("...")) {
            return List.of(out.get(0), "...", out.get(out.size() - 1));
        }
        return out;
    }

    /** A type name as the listing writes it: no package, no space. */
    private static String plain(String name) {
        return name.replace("java.util.", "").replace("java.lang.", "")
                .replace("org.ymxs.YMXS$", "").replace("org.ymxs.YMXS.", "")
                .replace("org.ymxs.", "").replace(" ", "");
    }

    /** The declarations in SPEC.md's first Java block. */
    private static List<Said> listing() throws IOException {
        List<String> lines = Files.readAllLines(SPEC);
        List<String> block = new ArrayList<>();
        boolean inside = false;
        for (String line : lines) {
            if (line.strip().equals("```java")) {
                inside = true;
                continue;
            }
            if (inside && line.strip().equals("```")) {
                break;
            }
            if (inside) {
                block.add(line);
            }
        }
        assertTrue(!block.isEmpty(), () -> SPEC + " has no Java block");
        List<String> whole = new ArrayList<>();
        StringBuilder one = new StringBuilder();
        int depth = 0;
        for (String line : block) {
            String said = line.strip();
            if (said.isEmpty()) {
                continue;
            }
            one.append(one.length() == 0 ? "" : " ").append(said);
            depth += said.chars().filter(c -> c == '(').count()
                    - said.chars().filter(c -> c == ')').count();
            if (depth == 0) {
                whole.add(one.toString());
                one.setLength(0);
            }
        }
        List<Said> out = new ArrayList<>();
        Pattern asRecord = Pattern.compile("^record (\\w+)(?:<[^>]*>)?\\((.*?)\\)");
        Pattern asSealed = Pattern.compile("^sealed interface (\\w+) permits (.+)$");
        Pattern asEnum = Pattern.compile("^enum (\\w+)\\s*\\{(.+)}$");
        for (String said : whole) {
            Matcher record = asRecord.matcher(said);
            Matcher sealed = asSealed.matcher(said);
            Matcher asEnumIs = asEnum.matcher(said);
            if (record.find()) {
                out.add(new Said("record", record.group(1), parts(record.group(2))));
            } else if (sealed.find()) {
                out.add(new Said("sealed", sealed.group(1), parts(sealed.group(2))));
            } else if (asEnumIs.find()) {
                List<String> named = new ArrayList<>();
                for (String word : asEnumIs.group(2).strip().split("[,\\s]+")) {
                    if (!word.isEmpty()) {
                        named.add(word);
                    }
                }
                out.add(new Said("enum", asEnumIs.group(1), named));
            }
        }
        return out;
    }

    /** A comma-separated list, each entry with its spaces closed up as a
     *  type name has them. */
    private static List<String> parts(String said) {
        List<String> out = new ArrayList<>();
        int depth = 0;
        StringBuilder one = new StringBuilder();
        for (char c : said.toCharArray()) {
            if (c == '<') {
                depth++;
            }
            if (c == '>') {
                depth--;
            }
            if (c == ',' && depth == 0) {
                out.add(tidy(one.toString()));
                one.setLength(0);
                continue;
            }
            one.append(c);
        }
        if (!one.toString().isBlank()) {
            out.add(tidy(one.toString()));
        }
        return out;
    }

    /** One entry: its generic part closed up, its name left with one space
     *  before it. */
    private static String tidy(String said) {
        String[] words = said.strip().split("\\s+");
        if (words.length < 2) {
            return said.strip();
        }
        StringBuilder out = new StringBuilder();
        for (int at = 0; at + 1 < words.length; at++) {
            out.append(words[at]);
        }
        return out + " " + words[words.length - 1];
    }

    @Test
    void theListingIsNotInMoreThanOneDocument() throws IOException {
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
}
