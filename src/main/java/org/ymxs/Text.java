package org.ymxs;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.ymxs.YMXS.Multi;

/**
 * The text form: a {@link Multi} written as JSON, and read back
 * (doc/text.md). It is one way of writing the structure down and not the
 * structure, so nothing here is in {@link YMXS}.
 *
 * <p>{@link Json} maps the structure to a JSON tree and back;
 * {@link Layout} says where the lines break. Escaping, parsing and writing
 * are the JSON library's, and this is the two ends of it.
 *
 * <p>A multi written and read back is the multi it was. The other
 * direction, a text read and written back, is the same text where the text
 * was written by this.
 */
public final class Text {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final ObjectWriter WRITER = MAPPER.writer(new Layout());

    private Text() {
    }

    /** {@code multi} as text. */
    public static String write(Multi multi) {
        try {
            return WRITER.writeValueAsString(Json.of(multi)) + "\n";
        } catch (JsonProcessingException wrong) {
            throw new IllegalStateException("a multi that will not write", wrong);
        }
    }

    /** The multi {@code text} holds.
     *
     * @throws IllegalArgumentException where the text is not JSON, not this
     *     form, or states a structure no player plays
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
