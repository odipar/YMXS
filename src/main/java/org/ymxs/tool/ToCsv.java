package org.ymxs.tool;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.ymxs.Csv;
import org.ymxs.Text;
import org.ymxs.Tunes;
import org.ymxs.YMXS.Multi;
import org.ymxs.YMXS.Tune;

/**
 * {@code ymxs-json-to-csv}: JSON on standard input, the table
 * form on standard output (doc/csv.md).
 */
public final class ToCsv {

    private ToCsv() {
    }

    public static void main(String[] args) {
        List<String> rest = new ArrayList<>(Arrays.asList(args));
        Tool tool = Tool.of("ymxs-json-to-csv", rest);
        String text = tool.text();
        Multi multi;
        String csv;
        try {
            multi = Text.read(text);
            csv = Csv.write(multi);
        } catch (IllegalArgumentException no) {
            throw tool.wrong(Tool.WRONG, String.valueOf(no.getMessage()));
        }
        tool.write(csv);
        int rows = 0;
        for (Tune tune : multi.tunes()) {
            rows += Tunes.size(tune.table());
        }
        tool.say(multi.tunes().size() + (multi.tunes().size() == 1 ? " tune, " : " tunes, ")
                + rows + " rows, " + text.length() + " characters in and " + csv.length()
                + " out");
    }
}
