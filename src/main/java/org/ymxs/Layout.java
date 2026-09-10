package org.ymxs;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.PrettyPrinter;
import com.fasterxml.jackson.core.util.Instantiatable;
import java.io.IOException;

/**
 * Where the JSON form breaks a line. The library writes the JSON; this
 * says only where the whitespace goes.
 *
 * <p>What it is for is reading. A tune's shape stands at the top of the
 * file, a run of one register's rows stands on a line of its own, and an
 * effect's event stands on one, so a reader looks down a stream or across
 * a row without a tool. A long run wraps, so no line runs off the screen.
 *
 * <p>The rule is depth. The outer structures break their lines: the file,
 * a tune, a stream, the list of events. What stands inside those is one
 * thing on one line: a run, an event, a source.
 */
final class Layout implements PrettyPrinter, Instantiatable<Layout> {

    /** Arrays this deep and shallower put each value on a line of its own:
     *  the tunes, a register's runs, the events. */
    private static final int ARRAYS = 4;

    /** Objects this deep and shallower put each field on a line of its
     *  own: the file, a tune. */
    private static final int OBJECTS = 3;

    /** Values on one line of a run before it wraps. */
    private static final int WRAP = 20;

    private static final String INDENT = "  ";

    private int depth;
    private final int[] written = new int[128];

    @Override
    public Layout createInstance() {
        return new Layout();
    }

    @Override
    public void writeRootValueSeparator(JsonGenerator out) throws IOException {
        out.writeRaw('\n');
    }

    @Override
    public void writeStartObject(JsonGenerator out) throws IOException {
        out.writeRaw('{');
        depth++;
    }

    @Override
    public void beforeObjectEntries(JsonGenerator out) throws IOException {
        if (depth <= OBJECTS) {
            newline(out, depth);
        }
    }

    @Override
    public void writeObjectFieldValueSeparator(JsonGenerator out) throws IOException {
        out.writeRaw(": ");
    }

    @Override
    public void writeObjectEntrySeparator(JsonGenerator out) throws IOException {
        out.writeRaw(',');
        if (depth <= OBJECTS) {
            newline(out, depth);
        } else {
            out.writeRaw(' ');
        }
    }

    @Override
    public void writeEndObject(JsonGenerator out, int entries) throws IOException {
        boolean broke = depth <= OBJECTS && entries > 0;
        depth--;
        if (broke) {
            newline(out, depth);
        }
        out.writeRaw('}');
    }

    @Override
    public void writeStartArray(JsonGenerator out) throws IOException {
        out.writeRaw('[');
        depth++;
        written[depth] = 0;
    }

    @Override
    public void beforeArrayValues(JsonGenerator out) throws IOException {
        if (depth <= ARRAYS) {
            newline(out, depth);
        }
    }

    @Override
    public void writeArrayValueSeparator(JsonGenerator out) throws IOException {
        out.writeRaw(',');
        written[depth]++;
        if (depth <= ARRAYS) {
            newline(out, depth);
        } else if (written[depth] % WRAP == 0) {
            newline(out, depth);
        }
    }

    @Override
    public void writeEndArray(JsonGenerator out, int values) throws IOException {
        boolean broke = depth <= ARRAYS && values > 0;
        depth--;
        if (broke) {
            newline(out, depth);
        }
        out.writeRaw(']');
    }

    private static void newline(JsonGenerator out, int at) throws IOException {
        out.writeRaw('\n');
        for (int i = 0; i < at; i++) {
            out.writeRaw(INDENT);
        }
    }
}
