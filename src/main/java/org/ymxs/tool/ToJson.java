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
 * {@code ymxs-csv-to-json}: CSV on standard input, JSON on standard
 * output (doc/json.md).
 */
public final class ToJson {

    private ToJson() {
    }

    public static void main(String[] args) {
        List<String> rest = new ArrayList<>(Arrays.asList(args));
        Tool tool = Tool.of("ymxs-csv-to-json", rest);
        String text = tool.text();
        Multi multi;
        String json;
        try {
            multi = Csv.read(text);
            json = Text.write(multi);
        } catch (IllegalArgumentException no) {
            throw tool.wrong(Tool.WRONG, String.valueOf(no.getMessage()));
        }
        tool.write(json);
        int rows = 0;
        for (Tune tune : multi.tunes()) {
            rows += Tunes.size(tune.table());
        }
        tool.report(multi.tunes().size() + (multi.tunes().size() == 1 ? " tune, " : " tunes, ")
                + rows + " rows, " + text.length() + " characters in and " + json.length()
                + " out");
    }
}
