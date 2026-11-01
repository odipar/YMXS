package org.ymxs;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NumericNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import org.jspecify.annotations.Nullable;

/**
 * A JSON number as written in the text. json.md 8.1 defines a value read
 * as JSON text, each number in it as written in the file; the library's
 * nodes write a number as its value: {@code 10.0} for {@code 1e1},
 * {@code 1.5} for {@code 1.50}, {@code 0} for {@code -0}. {@link Reader}
 * reads a number with a fraction or an exponent, and {@code -0}, into one
 * of these, and every other number into the library's node, whose text is
 * its digits.
 *
 * <p>A whole number (json.md 1.4) is an optional {@code -} and digits:
 * {@code -0} is the whole number 0, and a number with a fraction or an
 * exponent is outside the whole numbers.
 */
final class Written extends NumericNode {

    private static final long serialVersionUID = 1L;

    /** The number as written in the text. */
    private final String text;

    /** Whether the text is an optional {@code -} and digits. */
    private final boolean whole;

    private Written(String text, boolean whole) {
        this.text = text;
        this.whole = whole;
    }

    private BigDecimal decimal() {
        return new BigDecimal(text);
    }

    @Override
    public JsonToken asToken() {
        return whole ? JsonToken.VALUE_NUMBER_INT : JsonToken.VALUE_NUMBER_FLOAT;
    }

    @Override
    public JsonParser.NumberType numberType() {
        return whole ? JsonParser.NumberType.INT : JsonParser.NumberType.DOUBLE;
    }

    @Override
    public boolean isIntegralNumber() {
        return whole;
    }

    @Override
    public boolean isFloatingPointNumber() {
        return !whole;
    }

    @Override
    public Number numberValue() {
        return whole ? Integer.valueOf(intValue()) : Double.valueOf(doubleValue());
    }

    @Override
    public int intValue() {
        return decimal().intValue();
    }

    @Override
    public long longValue() {
        return decimal().longValue();
    }

    @Override
    public double doubleValue() {
        return Double.parseDouble(text);
    }

    @Override
    public BigDecimal decimalValue() {
        return decimal();
    }

    @Override
    public BigInteger bigIntegerValue() {
        return decimal().toBigInteger();
    }

    @Override
    public boolean canConvertToInt() {
        return whole;
    }

    @Override
    public boolean canConvertToLong() {
        return whole;
    }

    @Override
    public String asText() {
        return text;
    }

    @Override
    public void serialize(JsonGenerator out, SerializerProvider provider) throws IOException {
        out.writeNumber(text);
    }

    @Override
    public boolean equals(@Nullable Object other) {
        return other instanceof Written written && written.text.equals(text);
    }

    @Override
    public int hashCode() {
        return text.hashCode();
    }

    /**
     * Reads a JSON value into a tree, each number in it as written. The
     * members of an object are in text order, and the last of two members
     * of one name is at the place of the first (json.md 9.1).
     */
    static final class Reader extends StdDeserializer<JsonNode> {

        private static final long serialVersionUID = 1L;

        private static final JsonNodeFactory MAKE = JsonNodeFactory.instance;

        Reader() {
            super(JsonNode.class);
        }

        @Override
        public JsonNode deserialize(JsonParser in, DeserializationContext context)
                throws IOException {
            return value(in, in.currentToken(), context);
        }

        private static JsonNode value(JsonParser in, JsonToken token,
                                      DeserializationContext context) throws IOException {
            switch (token) {
                case START_OBJECT, FIELD_NAME -> {
                    ObjectNode out = MAKE.objectNode();
                    String key = token == JsonToken.FIELD_NAME ? in.currentName()
                            : in.nextFieldName();
                    for (; key != null; key = in.nextFieldName()) {
                        out.set(key, value(in, in.nextToken(), context));
                    }
                    return out;
                }
                case START_ARRAY -> {
                    ArrayNode out = MAKE.arrayNode();
                    for (JsonToken next = in.nextToken(); next != JsonToken.END_ARRAY;
                            next = in.nextToken()) {
                        out.add(value(in, next, context));
                    }
                    return out;
                }
                case VALUE_STRING -> {
                    return MAKE.textNode(in.getText());
                }
                case VALUE_NUMBER_INT -> {
                    if (in.getText().equals("-0")) {
                        return new Written("-0", true);
                    }
                    return switch (in.getNumberType()) {
                        case INT -> MAKE.numberNode(in.getIntValue());
                        case LONG -> MAKE.numberNode(in.getLongValue());
                        default -> MAKE.numberNode(in.getBigIntegerValue());
                    };
                }
                case VALUE_NUMBER_FLOAT -> {
                    return new Written(in.getText(), false);
                }
                case VALUE_TRUE -> {
                    return MAKE.booleanNode(true);
                }
                case VALUE_FALSE -> {
                    return MAKE.booleanNode(false);
                }
                case VALUE_NULL -> {
                    return MAKE.nullNode();
                }
                default -> {
                    return (JsonNode) context.handleUnexpectedToken(JsonNode.class, in);
                }
            }
        }
    }
}
