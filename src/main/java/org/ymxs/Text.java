package org.ymxs;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.ymxs.YMXS.Multi;

/**
 * A {@link Multi} written as JSON, and read back (doc/json.md). It is one
 * way of writing the structure down rather than the structure itself, and
 * so stays outside {@link YMXS}.
 *
 * <p>{@link Json} maps the structure to a JSON tree and back;
 * {@link Layout} fixes the line breaks; the escaping, the parsing and the
 * writing are the JSON library's. This class is the two ends of that.
 *
 * <p>Writing a multi and reading it back returns an equal multi. Reading
 * text written by this class and writing it back returns identical text.
 */
public final class Text {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final ObjectWriter WRITER = MAPPER.writer(new Layout());

    private Text() {
    }

    /** {@code multi} as JSON text. */
    public static String write(Multi multi) {
        try {
            return WRITER.writeValueAsString(Json.of(multi)) + "\n";
        } catch (JsonProcessingException wrong) {
            throw new IllegalStateException("a multi that will not write", wrong);
        }
    }

    /**
     * Every multi in {@code text}, one after another. JSON puts no
     * count in front of a stream of values, so this is what a reader gets
     * where several files are handed to it as one.
     *
     * @throws IllegalArgumentException where the text is not JSON, not this
     *     form, or is a structure no player plays
     */
    public static java.util.List<Multi> readAll(String text) {
        java.util.List<Multi> out = new java.util.ArrayList<>();
        try (com.fasterxml.jackson.databind.MappingIterator<com.fasterxml.jackson.databind
                .JsonNode> trees = MAPPER.readerFor(
                        com.fasterxml.jackson.databind.JsonNode.class).readValues(text)) {
            while (trees.hasNext()) {
                out.add(Json.multi(trees.next()));
            }
        } catch (java.io.IOException wrong) {
            throw new IllegalArgumentException("this is not JSON: " + wrong.getMessage(),
                    wrong);
        }
        return out;
    }

    /** The multi in {@code text}.
     *
     * @throws IllegalArgumentException where the text is not JSON, not this
     *     form, or is a structure no player plays
     */
    public static Multi read(String text) {
        try {
            return Json.multi(MAPPER.readTree(text));
        } catch (JsonProcessingException wrong) {
            throw new IllegalArgumentException("this is not JSON: "
                    + wrong.getOriginalMessage(), wrong);
        }
    }
}
